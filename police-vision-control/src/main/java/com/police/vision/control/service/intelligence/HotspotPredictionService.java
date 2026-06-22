package com.police.vision.control.service.intelligence;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.digest.DigestUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.config.intelligence.ElasticsearchConfig;
import com.police.vision.control.config.intelligence.IntelligenceConfig;
import com.police.vision.control.dto.intelligence.PredictionDTO;
import com.police.vision.control.entity.intelligence.HotspotPrediction;
import com.police.vision.control.mapper.intelligence.HotspotPredictionMapper;
import com.police.vision.control.util.SarimaMathUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotspotPredictionService {

    private final HotspotPredictionMapper hotspotPredictionMapper;
    private final IntelligenceConfig intelligenceConfig;
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchConfig elasticsearchConfig;

    private static final int S = 24;
    private static final double[] HOUR_WEIGHTS = buildHourWeights();
    private static final String[] RISK_LEVEL_NAMES = {"低风险", "较低风险", "较高风险", "高风险"};

    private static double[] buildHourWeights() {
        double[] weights = new double[24];
        for (int h = 0; h < 24; h++) {
            if (h >= 0 && h < 6) {
                weights[h] = 1.8;
            } else if (h >= 22) {
                weights[h] = 1.6;
            } else if (h >= 18 && h < 22) {
                weights[h] = 1.4;
            } else if (h >= 12 && h < 14) {
                weights[h] = 1.2;
            } else if (h >= 7 && h < 9) {
                weights[h] = 1.3;
            } else {
                weights[h] = 1.0;
            }
        }
        return weights;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runPrediction(PredictionDTO dto) {
        long startTimeMs = System.currentTimeMillis();

        IntelligenceConfig.PredictionConfig config = intelligenceConfig.getPrediction();
        int predictHours = dto.getPredictHours() != null ? dto.getPredictHours() : config.getPredictHours();
        int historyDays = dto.getHistoryDays() != null ? dto.getHistoryDays() : config.getHistoryDays();
        int gridSizeMeters = dto.getGridSizeMeters() != null ? dto.getGridSizeMeters() : config.getGridSizeMeters();
        String caseType = dto.getCaseType();
        String areaCode = dto.getAreaCode();
        double riskThreshold = config.getRiskThreshold();

        String predictionBatch = generatePredictionBatch();
        LocalDateTime predictStartTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime predictEndTime = predictStartTime.plusHours(predictHours);

        Map<String, double[]> gridTimeSeries;

        try {
            gridTimeSeries = queryGridTimeSeriesFromEs(historyDays, predictHours, caseType, areaCode, gridSizeMeters);
        } catch (Exception e) {
            log.warn("从ES查询警情数据失败，尝试DB降级，batch={}：{}", predictionBatch, e.getMessage());
        }

        if (gridTimeSeries == null || gridTimeSeries.isEmpty()) {
            try {
                gridTimeSeries = queryGridTimeSeriesFromDb(historyDays, predictHours, caseType, areaCode, gridSizeMeters);
            } catch (Exception e2) {
                log.error("从DB查询也失败，batch={}：{}", predictionBatch, e2.getMessage());
            }
        }
        if (gridTimeSeries == null || gridTimeSeries.isEmpty()) {
            log.warn("无历史数据可用于预测，跳过本次预测，batch={}", predictionBatch);
            Map<String, Object> emptyResult = new LinkedHashMap<>();
            emptyResult.put("predictionBatch", predictionBatch);
            emptyResult.put("totalGrids", 0);
            emptyResult.put("highRiskCount", 0);
            emptyResult.put("durationMs", System.currentTimeMillis() - startTimeMs);
            emptyResult.put("message", "无历史数据");
            return emptyResult;
        }

        List<HotspotPrediction> predictions = new ArrayList<>();
        int totalGrids = gridTimeSeries.size();
        int highRiskCount = 0;

        for (Map.Entry<String, double[]> entry : gridTimeSeries.entrySet()) {
            String gridCode = entry.getKey();
            double[] history = entry.getValue();

            double[] forecasts;
            String sarimaParamsJson;
            boolean useEwma = false;

            try {
                double[] params = SarimaMathUtils.selectSarimaParams(history, S);
                int p = (int) params[0];
                int d = (int) params[1];
                int q = (int) params[2];
                int P = (int) params[3];
                int D = (int) params[4];
                int Q = (int) params[5];
                double aicVal = params[7];

                forecasts = SarimaMathUtils.sarimaForecast(history, p, d, q, P, D, Q, S, predictHours);

                Map<String, Object> paramMap = new LinkedHashMap<>();
                paramMap.put("p", p);
                paramMap.put("d", d);
                paramMap.put("q", q);
                paramMap.put("P", P);
                paramMap.put("D", D);
                paramMap.put("Q", Q);
                paramMap.put("s", S);
                paramMap.put("AIC", round4(aicVal));
                paramMap.put("model", "SARIMA");
                sarimaParamsJson = JSON.toJSONString(paramMap);
            } catch (Exception e) {
                log.debug("SARIMA参数选择/预测失败，回退到EWMA，grid={}：{}", gridCode, e.getMessage());
                forecasts = SarimaMathUtils.ewmaForecast(history, predictHours, 0.3);
                useEwma = true;
                Map<String, Object> paramMap = new LinkedHashMap<>();
                paramMap.put("model", "EWMA");
                paramMap.put("alpha", 0.3);
                sarimaParamsJson = JSON.toJSONString(paramMap);
            }

            double historicalMax = 0;
            double historicalSum = 0;
            for (double v : history) {
                historicalMax = Math.max(historicalMax, v);
                historicalSum += v;
            }
            double historicalMean = historicalSum / history.length;
            int historicalCount = (int) Math.round(historicalSum);

            double[] probabilities = new double[predictHours];
            double predictedSum = 0;
            for (int i = 0; i < predictHours; i++) {
                if (historicalMax > 0) {
                    probabilities[i] = Math.min(1.0, Math.max(0.0, forecasts[i] / historicalMax));
                } else {
                    probabilities[i] = 0.0;
                }
                predictedSum += forecasts[i];
            }
            int predictedCount = (int) Math.round(predictedSum);

            double weightedNumerator = 0;
            double weightedDenominator = 0;
            for (int i = 0; i < predictHours; i++) {
                int hour = (predictStartTime.getHour() + i) % 24;
                double w = HOUR_WEIGHTS[hour];
                weightedNumerator += forecasts[i] * w;
                weightedDenominator += w;
            }
            double riskScoreRaw = weightedDenominator > 0 ? weightedNumerator / weightedDenominator : 0;
            double riskScore;
            if (historicalMax > 0) {
                riskScore = Math.min(1.0, riskScoreRaw / historicalMax);
            } else {
                riskScore = Math.min(1.0, riskScoreRaw / 2.0);
            }

            int riskLevel = determineRiskLevel(riskScore, riskThreshold);
            if (riskLevel >= 3) {
                highRiskCount++;
            }
            String riskLevelName = RISK_LEVEL_NAMES[Math.min(riskLevel, RISK_LEVEL_NAMES.length - 1)];

            double trendRate = historicalMean > 0
                    ? (predictedSum / predictHours - historicalMean) / historicalMean * 100.0
                    : 0.0;
            if (Double.isInfinite(trendRate) || Double.isNaN(trendRate)) {
                trendRate = 0.0;
            }

            double averageProbability = 0;
            for (double prob : probabilities) {
                averageProbability += prob;
            }
            averageProbability = predictHours > 0 ? averageProbability / predictHours : 0.0;

            BigDecimal probability = round(averageProbability);
            BigDecimal riskScoreBd = round(riskScore);
            BigDecimal trendRateBd = round(trendRate);

            double[] gridCenter = gridCodeToLngLat(gridCode, gridSizeMeters);
            BigDecimal gridCenterLng = round(gridCenter[0]);
            BigDecimal gridCenterLat = round(gridCenter[1]);

            String suggestion = generatePreventionSuggestion(riskLevel, caseType);

            HotspotPrediction prediction = new HotspotPrediction();
            prediction.setPredictionId(generatePredictionId(predictionBatch, gridCode, caseType));
            prediction.setPredictionNo(SnowflakeIdUtil.nextIdStr());
            prediction.setPredictionBatch(predictionBatch);
            prediction.setPredictStartTime(predictStartTime);
            prediction.setPredictEndTime(predictEndTime);
            prediction.setPredictHours(predictHours);
            prediction.setAreaCode(areaCode);
            prediction.setGridCode(gridCode);
            prediction.setGridCenterLng(gridCenterLng);
            prediction.setGridCenterLat(gridCenterLat);
            prediction.setCaseType(caseType);
            prediction.setPredictedCount(predictedCount);
            prediction.setProbability(probability);
            prediction.setRiskScore(riskScoreBd);
            prediction.setRiskLevel(riskLevel);
            prediction.setRiskLevelName(riskLevelName);
            prediction.setHistoricalCount(historicalCount);
            prediction.setTrendRate(trendRateBd);
            prediction.setPreventionSuggestion(suggestion);
            prediction.setModelRunTime(LocalDateTime.now());
            prediction.setModelVersion("1.0.0");
            prediction.setSarimaParams(sarimaParamsJson);
            prediction.setStatus(1);
            prediction.setStatusName("正常");

            if (useEwma) {
                prediction.setSeasonalPattern("EWMA_FALLBACK");
            }

            predictions.add(prediction);
        }

        int batchSize = 500;
        for (int i = 0; i < predictions.size(); i += batchSize) {
            int end = Math.min(i + batchSize, predictions.size());
            List<HotspotPrediction> batch = predictions.subList(i, end);
            for (HotspotPrediction hp : batch) {
                hotspotPredictionMapper.insert(hp);
            }
        }

        long durationMs = System.currentTimeMillis() - startTimeMs;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("predictionBatch", predictionBatch);
        result.put("totalGrids", totalGrids);
        result.put("highRiskCount", highRiskCount);
        result.put("durationMs", durationMs);
        result.put("predictStartTime", predictStartTime);
        result.put("predictEndTime", predictEndTime);
        result.put("caseType", caseType);
        result.put("areaCode", areaCode);

        log.info("热点预测任务完成：batch={}, totalGrids={}, highRisk={}, duration={}ms",
                predictionBatch, totalGrids, highRiskCount, durationMs);

        return result;
    }

    public List<HotspotPrediction> getHighRiskPredictions(String batch, Integer minRiskLevel, String areaCode) {
        LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(batch != null && !batch.isEmpty(), HotspotPrediction::getPredictionBatch, batch);
        wrapper.ge(minRiskLevel != null, HotspotPrediction::getRiskLevel,
                minRiskLevel != null ? minRiskLevel : 2);
        wrapper.eq(areaCode != null && !areaCode.isEmpty(), HotspotPrediction::getAreaCode, areaCode);
        wrapper.orderByDesc(HotspotPrediction::getRiskScore, HotspotPrediction::getProbability);
        return hotspotPredictionMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> evaluatePredictionAccuracy(String predictionBatch) {
        LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotspotPrediction::getPredictionBatch, predictionBatch);
        List<HotspotPrediction> predictions = hotspotPredictionMapper.selectList(wrapper);

        if (predictions == null || predictions.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("accuracy", 0.0);
            empty.put("mae", 0.0);
            empty.put("rmse", 0.0);
            empty.put("totalGrids", 0);
            empty.put("evaluatedGrids", 0);
            return empty;
        }

        HotspotPrediction sample = predictions.get(0);
        LocalDateTime evalStartTime = sample.getPredictStartTime();
        LocalDateTime evalEndTime = sample.getPredictEndTime();
        int predictHours = sample.getPredictHours() != null ? sample.getPredictHours() : 24;

        Map<String, Integer> actualCounts;
        try {
            actualCounts = queryActualCountsFromEs(evalStartTime, evalEndTime, sample.getCaseType(), sample.getAreaCode(),
                    predictions.stream().map(HotspotPrediction::getGridCode).collect(Collectors.toList()));
        } catch (Exception e) {
            log.warn("从ES查询实际数据失败，尝试DB：{}", e.getMessage());
            try {
                actualCounts = queryActualCountsFromDb(evalStartTime, evalEndTime, sample.getCaseType(), sample.getAreaCode());
            } catch (Exception e2) {
                log.warn("查询实际数据失败，无法评估：{}", e2.getMessage());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("accuracy", 0.0);
                result.put("mae", 0.0);
                result.put("rmse", 0.0);
                result.put("totalGrids", predictions.size());
                result.put("evaluatedGrids", 0);
                return result;
            }
        }

        double sumAbsError = 0.0;
        double sumSqError = 0.0;
        double sumAbsPercentError = 0.0;
        int evaluatedGrids = 0;

        for (HotspotPrediction pred : predictions) {
            Integer actual = actualCounts.getOrDefault(pred.getGridCode(), 0);
            int predicted = pred.getPredictedCount() != null ? pred.getPredictedCount() : 0;

            pred.setActualCount(actual);

            double absError = Math.abs(predicted - actual);
            sumAbsError += absError;
            sumSqError += absError * absError;

            if (actual > 0 || predicted > 0) {
                double denom = Math.max(actual, predicted);
                sumAbsPercentError += absError / denom;
                evaluatedGrids++;
            }

            hotspotPredictionMapper.updateById(pred);
        }

        double mae = evaluatedGrids > 0 ? sumAbsError / evaluatedGrids : 0.0;
        double rmse = evaluatedGrids > 0 ? Math.sqrt(sumSqError / evaluatedGrids) : 0.0;
        double mape = evaluatedGrids > 0 ? sumAbsPercentError / evaluatedGrids : 1.0;
        double accuracy = Math.max(0.0, 1.0 - mape);

        for (HotspotPrediction pred : predictions) {
            pred.setPredictionAccuracy(round(accuracy));
            hotspotPredictionMapper.updateById(pred);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accuracy", round4(accuracy));
        result.put("mae", round4(mae));
        result.put("rmse", round4(rmse));
        result.put("totalGrids", predictions.size());
        result.put("evaluatedGrids", evaluatedGrids);

        log.info("预测批次评估完成：batch={}, accuracy={}, mae={}, rmse={}",
                predictionBatch, round4(accuracy), round4(mae), round4(rmse));

        return result;
    }

    public IPage<HotspotPrediction> listPredictions(String areaCode, String caseType,
                                                     LocalDateTime startTime, LocalDateTime endTime,
                                                     int pageNum, int pageSize) {
        LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(areaCode != null && !areaCode.isEmpty(), HotspotPrediction::getAreaCode, areaCode);
        wrapper.eq(caseType != null && !caseType.isEmpty(), HotspotPrediction::getCaseType, caseType);
        if (startTime != null && endTime != null) {
            wrapper.and(w -> w.between(HotspotPrediction::getPredictStartTime, startTime, endTime)
                    .or().between(HotspotPrediction::getCreateTime, startTime, endTime));
        }
        wrapper.orderByDesc(HotspotPrediction::getCreateTime, HotspotPrediction::getRiskScore);

        Page<HotspotPrediction> page = new Page<>(pageNum, pageSize);
        return hotspotPredictionMapper.selectPage(page, wrapper);
    }

    private Map<String, double[]> queryGridTimeSeriesFromEs(int historyDays, int predictHours,
                                                             String caseType, String areaCode, int gridSizeMeters) {
        Map<String, double[]> result = new LinkedHashMap<>();
        if (elasticsearchClient == null) {
            throw new IllegalStateException("ElasticsearchClient not available");
        }

        String caseIndex = elasticsearchConfig.getCaseIndex();
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime startTime = endTime.minusDays(historyDays);
        int totalHours = historyDays * 24;

        Query timeQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                .range(r -> r
                        .field("case_time")
                        .gte(JsonData.of(startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                        .lte(JsonData.of(endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                )
        );

        Query typeQuery = null;
        if (caseType != null && !caseType.isEmpty()) {
            typeQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                    .term(t -> t.field("case_type").value(caseType))
            );
        }

        Query areaQuery = null;
        if (areaCode != null && !areaCode.isEmpty()) {
            areaQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                    .term(t -> t.field("area_code").value(areaCode))
            );
        }

        List<Query> queries = new ArrayList<>();
        queries.add(timeQuery);
        if (typeQuery != null) queries.add(typeQuery);
        if (areaQuery != null) queries.add(areaQuery);

        Query finalQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                .bool(b -> {
                    b.must(queries);
                    return b;
                })
        );

        Map<String, Aggregation> hourAggs = new LinkedHashMap<>();
        Aggregation gridTermsAgg = Aggregation.of(a -> a
                .terms(t -> t.field("grid_code").size(5000))
                .aggregations("hourly_histogram", Aggregation.of(ha -> ha
                        .dateHistogram(dh -> dh
                                .field("case_time")
                                .calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.Hourly)
                                .minDocCount(0L)
                                .extendedBounds(eb -> eb
                                        .min(JsonData.of(startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                                        .max(JsonData.of(endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                                )
                        )
                ))
        );

        try {
            SearchResponse<Void> response = elasticsearchClient.search(s -> s
                            .index(caseIndex)
                            .query(finalQuery)
                            .size(0)
                            .aggregations("grid_terms", gridTermsAgg),
                    Void.class
            );

            Buckets<StringTermsBucket> gridBuckets = response.aggregations()
                    .get("grid_terms").sterms().buckets();

            long startEpochHour = startTime.truncatedTo(ChronoUnit.HOURS)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 3600000L;

            for (StringTermsBucket gridBucket : gridBuckets.array()) {
                String gridCode = gridBucket.key().stringValue();
                double[] series = new double[totalHours];
                Arrays.fill(series, 0.0);

                Buckets<DateHistogramBucket> hourBuckets = gridBucket.aggregations()
                        .get("hourly_histogram").dateHistogram().buckets();

                for (DateHistogramBucket hourBucket : hourBuckets.array()) {
                    long epochHour = hourBucket.key() / 3600000L;
                    int idx = (int) (epochHour - startEpochHour);
                    if (idx >= 0 && idx < totalHours) {
                        series[idx] = hourBucket.docCount();
                    }
                }

                double sum = 0;
                for (double v : series) sum += v;
                if (sum > 0) {
                    result.put(gridCode, series);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("ES查询失败：" + e.getMessage(), e);
        }

        return result;
    }

    private Map<String, double[]> queryGridTimeSeriesFromDb(int historyDays, int predictHours,
                                                             String caseType, String areaCode, int gridSizeMeters) {
        Map<String, double[]> result = new LinkedHashMap<>();
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime startTime = endTime.minusDays(historyDays);
        int totalHours = historyDays * 24;
        long startEpochHour = startTime.truncatedTo(ChronoUnit.HOURS)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 3600000L;

        try {
            List<Map<String, Object>> rawData = hotspotPredictionMapper.selectHistoryCaseData(
                    startTime, endTime, caseType, areaCode);
            if (rawData == null || rawData.isEmpty()) {
                return result;
            }

            Map<String, double[]> tempMap = new LinkedHashMap<>();
            for (Map<String, Object> row : rawData) {
                String gridCode = row.get("grid_code") != null ? row.get("grid_code").toString() : null;
                Object caseTimeObj = row.get("case_time");
                if (gridCode == null || caseTimeObj == null) continue;

                long epochHour;
                if (caseTimeObj instanceof java.util.Date) {
                    epochHour = ((java.util.Date) caseTimeObj).toInstant().toEpochMilli() / 3600000L;
                } else if (caseTimeObj instanceof LocalDateTime) {
                    epochHour = ((LocalDateTime) caseTimeObj)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 3600000L;
                } else {
                    continue;
                }

                int idx = (int) (epochHour - startEpochHour);
                if (idx < 0 || idx >= totalHours) continue;

                tempMap.computeIfAbsent(gridCode, k -> {
                    double[] arr = new double[totalHours];
                    Arrays.fill(arr, 0.0);
                    return arr;
                });
                tempMap.get(gridCode)[idx] += 1.0;
            }

            for (Map.Entry<String, double[]> entry : tempMap.entrySet()) {
                double sum = 0;
                for (double v : entry.getValue()) sum += v;
                if (sum > 0) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("DB查询失败：" + e.getMessage(), e);
        }
        return result;
    }



    private Map<String, Integer> queryActualCountsFromEs(LocalDateTime startTime, LocalDateTime endTime,
                                                          String caseType, String areaCode, List<String> gridCodes) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (elasticsearchClient == null || gridCodes == null || gridCodes.isEmpty()) {
            return result;
        }

        String caseIndex = elasticsearchConfig.getCaseIndex();

        List<Query> queries = new ArrayList<>();
        queries.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                .range(r -> r
                        .field("case_time")
                        .gte(JsonData.of(startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                        .lte(JsonData.of(endTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                )
        ));
        if (caseType != null && !caseType.isEmpty()) {
            queries.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                    .term(t -> t.field("case_type").value(caseType))
            ));
        }
        if (areaCode != null && !areaCode.isEmpty()) {
            queries.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                    .term(t -> t.field("area_code").value(areaCode))
            ));
        }
        queries.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                .terms(t -> t.field("grid_code")
                        .terms(tv -> tv.value(gridCodes.stream().map(JsonData::of).collect(Collectors.toList()))))
        ));

        Query finalQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                .bool(b -> b.must(queries))
        );

        try {
            SearchResponse<Void> response = elasticsearchClient.search(s -> s
                            .index(caseIndex)
                            .query(finalQuery)
                            .size(0)
                            .aggregations("grid_counts", Aggregation.of(a -> a
                                    .terms(t -> t.field("grid_code").size(gridCodes.size()))
                            )),
                    Void.class
            );

            Buckets<StringTermsBucket> buckets = response.aggregations()
                    .get("grid_counts").sterms().buckets();
            for (StringTermsBucket bucket : buckets.array()) {
                result.put(bucket.key().stringValue(), (int) bucket.docCount());
            }
        } catch (Exception e) {
            throw new RuntimeException("ES实际数据查询失败：" + e.getMessage(), e);
        }
        return result;
    }

    private Map<String, Integer> queryActualCountsFromDb(LocalDateTime startTime, LocalDateTime endTime,
                                                          String caseType, String areaCode) {
        Map<String, Integer> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rawData = hotspotPredictionMapper.selectActualCaseData(
                    startTime, endTime, caseType, areaCode);
            if (rawData == null) return result;
            for (Map<String, Object> row : rawData) {
                String gridCode = row.get("grid_code") != null ? row.get("grid_code").toString() : null;
                Object cntObj = row.get("case_count");
                if (gridCode == null || cntObj == null) continue;
                int cnt = cntObj instanceof Number ? ((Number) cntObj).intValue() : Integer.parseInt(cntObj.toString());
                result.put(gridCode, cnt);
            }
        } catch (Exception e) {
            throw new RuntimeException("DB实际数据查询失败：" + e.getMessage(), e);
        }
        return result;
    }

    private String generatePredictionBatch() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = String.format("%06d", System.currentTimeMillis() % 1000000);
        return date + "-" + rand;
    }

    private String generatePredictionId(String batch, String gridCode, String caseType) {
        String raw = batch + "_" + (gridCode != null ? gridCode : "") + "_" + (caseType != null ? caseType : "");
        String md5 = HexUtil.encodeHexStr(DigestUtil.md5(raw.getBytes(StandardCharsets.UTF_8)));
        return "HP" + md5.substring(0, Math.min(12, md5.length()));
    }

    private int determineRiskLevel(double riskScore, double threshold) {
        double t1 = threshold;
        double t2 = Math.min(1.0, threshold + 0.25);
        double t3 = Math.min(1.0, threshold + 0.50);
        if (riskScore >= t3) return 4;
        if (riskScore >= t2) return 3;
        if (riskScore >= t1) return 2;
        return 1;
    }

    private String generatePreventionSuggestion(int riskLevel, String caseType) {
        String typeDesc = caseType != null ? caseType : "各类";
        switch (riskLevel) {
            case 4:
                return "【高风险预警】强烈建议立即增派巡逻警力，增设临时卡点，重点防控" + typeDesc + "案件高发区域，同步启动视频追踪和便衣布控，24小时专人盯防。";
            case 3:
                return "【较高风险】建议增派巡逻警力，加密巡逻频次，重点时段安排定点值守，加强" + typeDesc + "案件高发区域排查，提前部署防控力量。";
            case 2:
                return "【较低风险】建议加强日常巡逻，关注该区域" + typeDesc + "警情动态，可适当调整巡逻路线覆盖该网格。";
            case 1:
            default:
                return "【低风险】保持常规巡逻即可，持续关注警情变化趋势。";
        }
    }

    private String lngLatToGridCode(double lng, double lat, int gridSizeMeters) {
        double metersPerDegLat = 111320.0;
        double metersPerDegLng = 111320.0 * Math.cos(Math.toRadians(lat));
        double lngStep = gridSizeMeters / Math.max(1.0, metersPerDegLng);
        double latStep = gridSizeMeters / metersPerDegLat;
        long gridX = Math.round(lng / lngStep);
        long gridY = Math.round(lat / latStep);
        return "G" + gridSizeMeters + "_" + gridX + "_" + gridY;
    }

    private double[] gridCodeToLngLat(String gridCode, int gridSizeMeters) {
        double lng = 116.404;
        double lat = 39.915;
        try {
            if (gridCode != null && gridCode.startsWith("G")) {
                String[] parts = gridCode.split("_");
                if (parts.length >= 3) {
                    int size = Integer.parseInt(parts[0].substring(1));
                    long gridX = Long.parseLong(parts[1]);
                    long gridY = Long.parseLong(parts[2]);
                    double metersPerDegLat = 111320.0;
                    double latCenter = gridY * (size / metersPerDegLat);
                    double metersPerDegLng = 111320.0 * Math.cos(Math.toRadians(latCenter));
                    double lngCenter = gridX * (size / Math.max(1.0, metersPerDegLng));
                    return new double[]{lngCenter, latCenter};
                }
            }
        } catch (Exception ignored) {
        }
        return new double[]{lng, lat};
    }

    private BigDecimal round(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(val).setScale(6, RoundingMode.HALF_UP);
    }

    private double round4(double val) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 0.0;
        }
        return Math.round(val * 10000.0) / 10000.0;
    }
}
