package com.police.vision.control.service.intelligence;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TermsAggregation;
import co.elastic.clients.json.JsonData;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.config.intelligence.IntelligenceConfig;
import com.police.vision.control.dto.intelligence.ReportGenerateDTO;
import com.police.vision.control.entity.AggregationAlert;
import com.police.vision.control.entity.TargetPerson;
import com.police.vision.control.entity.intelligence.CaseCluster;
import com.police.vision.control.entity.intelligence.PoliceCaseInfo;
import com.police.vision.control.entity.intelligence.HotspotPrediction;
import com.police.vision.control.entity.intelligence.IntelligenceProduct;
import com.police.vision.control.entity.intelligence.PublicOpinion;
import com.police.vision.control.mapper.AggregationAlertMapper;
import com.police.vision.control.mapper.TargetPersonMapper;
import com.police.vision.control.mapper.intelligence.CaseClusterMapper;
import com.police.vision.control.mapper.intelligence.PoliceCaseInfoMapper;
import com.police.vision.control.mapper.intelligence.HotspotPredictionMapper;
import com.police.vision.control.mapper.intelligence.IntelligenceProductMapper;
import com.police.vision.control.mapper.intelligence.PublicOpinionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligenceProductService {

    private final IntelligenceProductMapper intelligenceProductMapper;
    private final DeepSeekService deepSeekService;
    private final HotspotPredictionService hotspotPredictionService;
    private final CaseClusterService caseClusterService;
    private final IntelligenceConfig intelligenceConfig;

    @Autowired(required = false)
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    @Lazy
    private AnalysisModelService analysisModelService;

    @Autowired
    private MqUtil mqUtil;

    private final AggregationAlertMapper aggregationAlertMapper;
    private final TargetPersonMapper targetPersonMapper;
    private final PublicOpinionMapper publicOpinionMapper;
    private final HotspotPredictionMapper hotspotPredictionMapper;
    private final CaseClusterMapper caseClusterMapper;
    private final PoliceCaseInfoMapper policeCaseInfoMapper;

    private static final Map<String, String> PRODUCT_TYPE_NAME_MAP = new LinkedHashMap<>();
    private static final int PRODUCT_NO_SEQUENCE_BOUND = 10000;
    private static final AtomicInteger PRODUCT_NO_SEQUENCE = new AtomicInteger(0);

    static {
        PRODUCT_TYPE_NAME_MAP.put("REPORT", "情报报告");
        PRODUCT_TYPE_NAME_MAP.put("DAILY_REPORT", "每日治安态势报告");
        PRODUCT_TYPE_NAME_MAP.put("WEEKLY_REPORT", "每周治安态势报告");
        PRODUCT_TYPE_NAME_MAP.put("MONTHLY_REPORT", "每月治安态势报告");
        PRODUCT_TYPE_NAME_MAP.put("CLUSTER", "串并案分析报告");
        PRODUCT_TYPE_NAME_MAP.put("PREDICTION", "热点预测报告");
        PRODUCT_TYPE_NAME_MAP.put("CRAWLER", "舆情采集报告");
    }

    @Transactional(rollbackFor = Exception.class)
    public IntelligenceProduct generateReport(ReportGenerateDTO dto) {
        long startMs = System.currentTimeMillis();
        log.info("开始生成情报产品报告: productType={}, startDate={}, endDate={}",
                dto.getProductType(), dto.getReportStartDate(), dto.getReportEndDate());

        if (dto.getReportStartDate() == null) {
            dto.setReportStartDate(LocalDate.now().minusDays(1));
        }
        if (dto.getReportEndDate() == null) {
            dto.setReportEndDate(LocalDate.now());
        }

        Map<String, Object> multiSourceData = collectMultiSourceData(dto);

        Integer alarmCount = extractCount(multiSourceData, "alarmCount");
        Integer caseCount = extractCount(multiSourceData, "caseCount");
        Integer personCount = extractCount(multiSourceData, "personCount");
        Integer vehicleCount = extractCount(multiSourceData, "vehicleCount");
        Integer opinionCount = extractCount(multiSourceData, "opinionCount");

        String markdownReport = null;
        try {
            markdownReport = deepSeekService.generateReport(dto, multiSourceData);
        } catch (Exception e) {
            log.warn("DeepSeek生成报告异常，使用降级报告模板", e);
        }
        if (!StringUtils.hasText(markdownReport)) {
            markdownReport = buildFallbackReport(dto, multiSourceData);
        }

        String title = extractTitle(markdownReport, dto);
        String summary = extractSummary(markdownReport);
        JSONObject structuredMetrics = extractStructuredMetrics(markdownReport);
        String hotspots = structuredMetrics != null && structuredMetrics.containsKey("hotspots")
                ? JSON.toJSONString(structuredMetrics.get("hotspots")) : null;
        String trends = structuredMetrics != null && structuredMetrics.containsKey("trends")
                ? JSON.toJSONString(structuredMetrics.get("trends")) : null;
        String suggestions = structuredMetrics != null && structuredMetrics.containsKey("suggestions")
                ? JSON.toJSONString(structuredMetrics.get("suggestions")) : null;

        IntelligenceProduct product = new IntelligenceProduct();
        fillProductIdAndNo(product);

        product.setProductType(dto.getProductType());
        product.setProductTypeName(PRODUCT_TYPE_NAME_MAP.getOrDefault(dto.getProductType(), "情报产品"));
        product.setTitle(title);
        product.setSummary(summary);
        product.setContent(summary);
        product.setMarkdownContent(markdownReport);
        product.setReportDate(LocalDate.now());
        product.setReportStartDate(dto.getReportStartDate());
        product.setReportEndDate(dto.getReportEndDate());
        product.setAlarmCount(alarmCount);
        product.setCaseCount(caseCount);
        product.setPersonCount(personCount);
        product.setVehicleCount(vehicleCount);
        product.setOpinionCount(opinionCount);
        product.setHotspots(hotspots);
        product.setTrends(trends);
        product.setSuggestions(suggestions);
        product.setModelId(dto.getModelId());
        if (StringUtils.hasText(dto.getModelId())) {
            try {
                product.setModelName(analysisModelService.getModel(dto.getModelId()).getModelName());
            } catch (Exception e) {
                log.warn("获取模型名称失败: modelId={}", dto.getModelId());
            }
        }
        product.setGenerateParams(JSON.toJSONString(dto));
        product.setStatus(1);
        product.setStatusName("已生成");
        product.setGenerateTime(LocalDateTime.now());
        long seconds = (System.currentTimeMillis() - startMs) / 1000L;
        product.setGenerateSeconds(seconds);
        if (product.getId() == null) {
            product.setId(SnowflakeIdUtil.nextId());
        }

        intelligenceProductMapper.insert(product);
        log.info("情报产品生成完成: productId={}, productNo={}, type={}, 耗时={}s",
                product.getProductId(), product.getProductNo(), product.getProductType(), seconds);

        sendProductGenerateNotification(product);

        return product;
    }

    public IntelligenceProduct getProduct(String productId) {
        if (!StringUtils.hasText(productId)) {
            throw new BusinessException("产品ID不能为空");
        }
        LambdaQueryWrapper<IntelligenceProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntelligenceProduct::getProductId, productId);
        IntelligenceProduct product = intelligenceProductMapper.selectOne(wrapper);
        if (product == null) {
            throw new BusinessException("情报产品不存在: " + productId);
        }
        return product;
    }

    public IPage<IntelligenceProduct> listProducts(String productType, LocalDate startDate,
                                                   LocalDate endDate, String status,
                                                   int pageNum, int pageSize) {
        LambdaQueryWrapper<IntelligenceProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(productType)) {
            wrapper.eq(IntelligenceProduct::getProductType, productType);
        }
        if (startDate != null) {
            wrapper.ge(IntelligenceProduct::getReportDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(IntelligenceProduct::getReportDate, endDate);
        }
        if (StringUtils.hasText(status)) {
            try {
                wrapper.eq(IntelligenceProduct::getStatus, Integer.parseInt(status));
            } catch (NumberFormatException e) {
                wrapper.eq(IntelligenceProduct::getStatusName, status);
            }
        }
        wrapper.orderByDesc(IntelligenceProduct::getGenerateTime);
        Page<IntelligenceProduct> page = new Page<>(pageNum, pageSize);
        return intelligenceProductMapper.selectPage(page, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public IntelligenceProduct regenerateReport(String productId) {
        IntelligenceProduct oldProduct = getProduct(productId);
        log.info("重新生成情报产品报告: productId={}, oldProductNo={}",
                productId, oldProduct.getProductNo());

        ReportGenerateDTO dto;
        try {
            dto = JSON.parseObject(oldProduct.getGenerateParams(), ReportGenerateDTO.class);
        } catch (Exception e) {
            dto = new ReportGenerateDTO();
            dto.setProductType(oldProduct.getProductType());
            dto.setReportStartDate(oldProduct.getReportStartDate());
            dto.setReportEndDate(oldProduct.getReportEndDate());
            dto.setModelId(oldProduct.getModelId());
        }

        long startMs = System.currentTimeMillis();
        Map<String, Object> multiSourceData = collectMultiSourceData(dto);

        Integer alarmCount = extractCount(multiSourceData, "alarmCount");
        Integer caseCount = extractCount(multiSourceData, "caseCount");
        Integer personCount = extractCount(multiSourceData, "personCount");
        Integer vehicleCount = extractCount(multiSourceData, "vehicleCount");
        Integer opinionCount = extractCount(multiSourceData, "opinionCount");

        String markdownReport = null;
        try {
            markdownReport = deepSeekService.generateReport(dto, multiSourceData);
        } catch (Exception e) {
            log.warn("DeepSeek重新生成报告异常，使用降级报告模板", e);
        }
        if (!StringUtils.hasText(markdownReport)) {
            markdownReport = buildFallbackReport(dto, multiSourceData);
        }

        String title = extractTitle(markdownReport, dto);
        String summary = extractSummary(markdownReport);
        JSONObject structuredMetrics = extractStructuredMetrics(markdownReport);
        String hotspots = structuredMetrics != null && structuredMetrics.containsKey("hotspots")
                ? JSON.toJSONString(structuredMetrics.get("hotspots")) : null;
        String trends = structuredMetrics != null && structuredMetrics.containsKey("trends")
                ? JSON.toJSONString(structuredMetrics.get("trends")) : null;
        String suggestions = structuredMetrics != null && structuredMetrics.containsKey("suggestions")
                ? JSON.toJSONString(structuredMetrics.get("suggestions")) : null;

        oldProduct.setTitle(title);
        oldProduct.setSummary(summary);
        oldProduct.setContent(summary);
        oldProduct.setMarkdownContent(markdownReport);
        oldProduct.setReportDate(LocalDate.now());
        oldProduct.setReportStartDate(dto.getReportStartDate());
        oldProduct.setReportEndDate(dto.getReportEndDate());
        oldProduct.setAlarmCount(alarmCount);
        oldProduct.setCaseCount(caseCount);
        oldProduct.setPersonCount(personCount);
        oldProduct.setVehicleCount(vehicleCount);
        oldProduct.setOpinionCount(opinionCount);
        oldProduct.setHotspots(hotspots);
        oldProduct.setTrends(trends);
        oldProduct.setSuggestions(suggestions);
        oldProduct.setGenerateParams(JSON.toJSONString(dto));
        oldProduct.setStatus(1);
        oldProduct.setStatusName("已重新生成");
        oldProduct.setGenerateTime(LocalDateTime.now());
        long seconds = (System.currentTimeMillis() - startMs) / 1000L;
        oldProduct.setGenerateSeconds(seconds);

        intelligenceProductMapper.updateById(oldProduct);
        log.info("情报产品重新生成完成: productId={}, 耗时={}s", productId, seconds);

        sendProductGenerateNotification(oldProduct);

        return oldProduct;
    }

    public Map<String, Object> getReportStats(LocalDate from, LocalDate to) {
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate startDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate endDate = to != null ? to : LocalDate.now();

        LambdaQueryWrapper<IntelligenceProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(IntelligenceProduct::getReportDate, startDate, endDate);
        List<IntelligenceProduct> products = intelligenceProductMapper.selectList(wrapper);

        int totalCount = products != null ? products.size() : 0;
        result.put("totalCount", totalCount);
        result.put("startDate", startDate);
        result.put("endDate", endDate);

        long totalSeconds = 0;
        Map<String, AtomicInteger> typeDistribution = new LinkedHashMap<>();
        Map<LocalDate, AtomicInteger> dailyDistribution = new TreeMap<>();
        Map<String, AtomicInteger> statusDistribution = new LinkedHashMap<>();

        if (products != null) {
            for (IntelligenceProduct p : products) {
                if (p.getGenerateSeconds() != null) {
                    totalSeconds += p.getGenerateSeconds();
                }
                String type = p.getProductTypeName() != null ? p.getProductTypeName() :
                        (p.getProductType() != null ? p.getProductType() : "未知");
                typeDistribution.computeIfAbsent(type, k -> new AtomicInteger()).incrementAndGet();
                if (p.getReportDate() != null) {
                    dailyDistribution.computeIfAbsent(p.getReportDate(), k -> new AtomicInteger()).incrementAndGet();
                }
                String status = p.getStatusName() != null ? p.getStatusName() :
                        (p.getStatus() != null ? String.valueOf(p.getStatus()) : "未知");
                statusDistribution.computeIfAbsent(status, k -> new AtomicInteger()).incrementAndGet();
            }
        }

        double avgSeconds = totalCount > 0
                ? BigDecimal.valueOf(totalSeconds).divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;
        result.put("avgGenerateSeconds", avgSeconds);
        result.put("totalGenerateSeconds", totalSeconds);

        Map<String, Integer> typeDistResult = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicInteger> e : typeDistribution.entrySet()) {
            typeDistResult.put(e.getKey(), e.getValue().get());
        }
        result.put("typeDistribution", typeDistResult);

        Map<String, Integer> dailyDistResult = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, AtomicInteger> e : dailyDistribution.entrySet()) {
            dailyDistResult.put(e.getKey().toString(), e.getValue().get());
        }
        result.put("dailyDistribution", dailyDistResult);

        Map<String, Integer> statusDistResult = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicInteger> e : statusDistribution.entrySet()) {
            statusDistResult.put(e.getKey(), e.getValue().get());
        }
        result.put("statusDistribution", statusDistResult);

        return result;
    }

    private Map<String, Object> collectMultiSourceData(ReportGenerateDTO dto) {
        Map<String, Object> data = new LinkedHashMap<>();
        LocalDate startDate = dto.getReportStartDate() != null ? dto.getReportStartDate() : LocalDate.now().minusDays(1);
        LocalDate endDate = dto.getReportEndDate() != null ? dto.getReportEndDate() : LocalDate.now();
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(23, 59, 59);
        String areaCode = dto.getAreaCode();

        boolean includeAlarm = dto.getIncludeAlarm() == null || Boolean.TRUE.equals(dto.getIncludeAlarm());
        boolean includeCase = dto.getIncludeCase() == null || Boolean.TRUE.equals(dto.getIncludeCase());
        boolean includePerson = dto.getIncludePerson() == null || Boolean.TRUE.equals(dto.getIncludePerson());
        boolean includeVehicle = dto.getIncludeVehicle() == null || Boolean.TRUE.equals(dto.getIncludeVehicle());
        boolean includeOpinion = dto.getIncludeOpinion() == null || Boolean.TRUE.equals(dto.getIncludeOpinion());

        if (includeAlarm) {
            collectAlarmData(data, startTime, endTime, areaCode);
        } else {
            data.put("alarmCount", 0);
        }

        if (includeCase) {
            collectCaseData(data, startTime, endTime, areaCode);
        } else {
            data.put("caseCount", 0);
        }

        if (includePerson) {
            collectPersonData(data, startTime, endTime, areaCode);
        } else {
            data.put("personCount", 0);
        }

        if (includeVehicle) {
            collectVehicleData(data, startTime, endTime, areaCode);
        } else {
            data.put("vehicleCount", 0);
        }

        if (includeOpinion) {
            collectOpinionData(data, startTime, endTime, areaCode);
        } else {
            data.put("opinionCount", 0);
        }

        collectHotspotPredictionData(data, areaCode);
        collectCaseClusterData(data, startTime, endTime, areaCode);

        data.put("reportPeriod", new LinkedHashMap<String, Object>() {{
            put("startDate", startDate.toString());
            put("endDate", endDate.toString());
            put("days", ChronoUnit.DAYS.between(startDate, endDate) + 1);
        }});
        if (areaCode != null) {
            data.put("areaCode", areaCode);
        }

        return data;
    }

    private void collectAlarmData(Map<String, Object> data, LocalDateTime startTime,
                                  LocalDateTime endTime, String areaCode) {
        int count = 0;
        Map<String, Integer> typeDistribution = new LinkedHashMap<>();
        double momRate = 0.0;
        try {
            LambdaQueryWrapper<AggregationAlert> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(AggregationAlert::getStartTime, startTime, endTime);
            if (StringUtils.hasText(areaCode)) {
                wrapper.eq(AggregationAlert::getAreaCode, areaCode);
            }
            List<AggregationAlert> alerts = aggregationAlertMapper.selectList(wrapper);
            count = alerts != null ? alerts.size() : 0;
            if (alerts != null) {
                for (AggregationAlert a : alerts) {
                    String level = a.getAlertLevel() != null ? "L" + a.getAlertLevel() : "未知";
                    typeDistribution.merge(level, 1, Integer::sum);
                }
            }

            long days = ChronoUnit.DAYS.between(startTime.toLocalDate(), endTime.toLocalDate()) + 1;
            LocalDateTime lastStart = startTime.minusDays(days);
            LocalDateTime lastEnd = endTime.minusDays(days);
            LambdaQueryWrapper<AggregationAlert> lastWrapper = new LambdaQueryWrapper<>();
            lastWrapper.between(AggregationAlert::getStartTime, lastStart, lastEnd);
            if (StringUtils.hasText(areaCode)) {
                lastWrapper.eq(AggregationAlert::getAreaCode, areaCode);
            }
            Long lastCount = aggregationAlertMapper.selectCount(lastWrapper);
            int last = lastCount != null ? lastCount.intValue() : 0;
            if (last > 0) {
                momRate = BigDecimal.valueOf(count - last)
                        .divide(BigDecimal.valueOf(last), 4, RoundingMode.HALF_UP).doubleValue() * 100;
            }
        } catch (Exception e) {
            log.warn("采集警情数据失败，使用空数据", e);
        }
        data.put("alarmCount", count);
        data.put("alarmTypeDistribution", typeDistribution);
        data.put("alarmMomRate", momRate);
    }

    private void collectCaseData(Map<String, Object> data, LocalDateTime startTime,
                                 LocalDateTime endTime, String areaCode) {
        int count = 0;
        Map<String, Integer> typeDistribution = new LinkedHashMap<>();
        double solveRate = 0.0;
        try {
            count = policeCaseInfoMapper.countByTimeRange(startTime, endTime, null, areaCode);
            List<Map<String, Object>> dist = policeCaseInfoMapper.selectCaseTypeDistribution(startTime, endTime, null, areaCode);
            for (Map<String, Object> row : dist) {
                typeDistribution.put(String.valueOf(row.get("case_type")), ((Number) row.get("cnt")).intValue());
            }
            int totalSolved = policeCaseInfoMapper.countSolvedByTimeRange(startTime, endTime, null, areaCode);
            if (totalSolved > 0 && count > 0) {
                solveRate = (double) totalSolved / count;
            }
            if (elasticsearchClient != null) {
                try {
                    Query rangeQuery = Query.of(q -> q.range(r -> r
                            .field("create_time")
                            .gte(JsonData.of(startTime.toString()))
                            .lte(JsonData.of(endTime.toString()))
                    ));
                    Query areaQuery = null;
                    if (StringUtils.hasText(areaCode)) {
                        areaQuery = Query.of(q -> q.term(t -> t.field("area_code").value(areaCode)));
                    }
                    Query finalAreaQuery = areaQuery;
                    CountRequest countReq = CountRequest.of(cr -> cr
                            .index("police_case")
                            .query(q -> q.bool(b -> {
                                b.must(rangeQuery);
                                if (finalAreaQuery != null) b.must(finalAreaQuery);
                                return b;
                            }))
                    );
                    CountResponse countResp = elasticsearchClient.count(countReq);
                    int esCount = (int) countResp.count();
                    if (esCount > 0) {
                        count = esCount;
                        SearchRequest aggReq = SearchRequest.of(sr -> sr
                                .index("police_case")
                                .size(0)
                                .query(q -> q.bool(b -> {
                                    b.must(rangeQuery);
                                    if (finalAreaQuery != null) b.must(finalAreaQuery);
                                    return b;
                                }))
                                .aggregations("caseTypeAgg", a -> a.terms(TermsAggregation.of(t -> t.field("case_type").size(20))))
                        );
                        SearchResponse<Void> aggResp = elasticsearchClient.search(aggReq, Void.class);
                        if (aggResp.aggregations() != null && aggResp.aggregations().get("caseTypeAgg") != null) {
                            var buckets = aggResp.aggregations().get("caseTypeAgg").sterms().buckets().array();
                            Map<String, Integer> esDist = new LinkedHashMap<>();
                            for (var bucket : buckets) {
                                esDist.put(bucket.key().stringValue(), (int) bucket.docCount());
                            }
                            if (!esDist.isEmpty()) {
                                typeDistribution = esDist;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("ES查询案件数据失败，已使用DB数据: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("采集案件数据失败", e);
        }
        data.put("caseCount", count);
        data.put("caseTypeDistribution", typeDistribution);
        data.put("caseSolveRate", solveRate);
    }

    private Map<String, Object> queryCaseFromDbFallback(LocalDateTime startTime, LocalDateTime endTime, String areaCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        int count = policeCaseInfoMapper.countByTimeRange(startTime, endTime, null, areaCode);
        List<Map<String, Object>> dist = policeCaseInfoMapper.selectCaseTypeDistribution(startTime, endTime, null, areaCode);
        Map<String, Integer> typeDistribution = new LinkedHashMap<>();
        for (Map<String, Object> row : dist) {
            typeDistribution.put(String.valueOf(row.get("case_type")), ((Number) row.get("cnt")).intValue());
        }
        result.put("count", count);
        result.put("typeDistribution", typeDistribution);
        return result;
    }

    private void collectPersonData(Map<String, Object> data, LocalDateTime startTime,
                                   LocalDateTime endTime, String areaCode) {
        int count = 0;
        int alertTotal = 0;
        List<Map<String, Object>> topPersons = new ArrayList<>();
        try {
            LambdaQueryWrapper<TargetPerson> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(areaCode)) {
                wrapper.eq(TargetPerson::getPoliceStationCode, areaCode);
            }
            List<TargetPerson> persons = targetPersonMapper.selectList(wrapper);
            count = persons != null ? persons.size() : 0;
            if (persons != null) {
                for (TargetPerson p : persons) {
                    if (p.getAlertCount() != null) alertTotal += p.getAlertCount();
                }
                List<TargetPerson> sorted = persons.stream()
                        .sorted((a, b) -> Integer.compare(
                                b.getAlertCount() != null ? b.getAlertCount() : 0,
                                a.getAlertCount() != null ? a.getAlertCount() : 0))
                        .limit(10)
                        .collect(Collectors.toList());
                for (TargetPerson p : sorted) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("personId", p.getPersonId());
                    item.put("personName", p.getPersonName());
                    item.put("personType", p.getPersonTypeName() != null ? p.getPersonTypeName() : p.getPersonType());
                    item.put("controlLevel", p.getControlLevel());
                    item.put("alertCount", p.getAlertCount() != null ? p.getAlertCount() : 0);
                    item.put("caseCount", p.getCaseCount() != null ? p.getCaseCount() : 0);
                    topPersons.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("采集重点人员数据失败", e);
        }
        data.put("personCount", count);
        data.put("personAlertTotal", alertTotal);
        data.put("topAlertPersons", topPersons);
    }

    private void collectVehicleData(Map<String, Object> data, LocalDateTime startTime,
                                    LocalDateTime endTime, String areaCode) {
        int monitorCount = 0;
        int alertCount = 0;
        Map<String, Integer> alertDistribution = new LinkedHashMap<>();
        try {
            LambdaQueryWrapper<AggregationAlert> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(AggregationAlert::getStartTime, startTime, endTime);
            if (StringUtils.hasText(areaCode)) {
                wrapper.eq(AggregationAlert::getAreaCode, areaCode);
            }
            List<AggregationAlert> alerts = aggregationAlertMapper.selectList(wrapper);
            monitorCount = alerts != null ? alerts.size() : 0;
            if (alerts != null) {
                for (AggregationAlert a : alerts) {
                    if (a.getAlertLevel() != null && a.getAlertLevel() >= 3) {
                        alertCount++;
                    }
                    String level = a.getAlertLevel() != null ? "L" + a.getAlertLevel() : "未知";
                    alertDistribution.merge(level, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.warn("采集车辆布控数据失败", e);
        }
        data.put("vehicleCount", monitorCount);
        data.put("vehicleAlertCount", alertCount);
        data.put("vehicleAlertDistribution", alertDistribution);
    }

    private void collectOpinionData(Map<String, Object> data, LocalDateTime startTime,
                                    LocalDateTime endTime, String areaCode) {
        int count = 0;
        Map<String, Integer> sentimentDistribution = new LinkedHashMap<>();
        sentimentDistribution.put("正面", 0);
        sentimentDistribution.put("中性", 0);
        sentimentDistribution.put("负面", 0);
        List<String> topKeywords = new ArrayList<>();
        try {
            LambdaQueryWrapper<PublicOpinion> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(PublicOpinion::getPublishTime, startTime, endTime);
            List<PublicOpinion> opinions = publicOpinionMapper.selectList(wrapper);
            count = opinions != null ? opinions.size() : 0;
            if (opinions != null) {
                Map<String, AtomicInteger> keywordCounter = new LinkedHashMap<>();
                for (PublicOpinion o : opinions) {
                    if (o.getSentimentLabel() != null) {
                        if (o.getSentimentLabel() == 1) {
                            sentimentDistribution.merge("正面", 1, Integer::sum);
                        } else if (o.getSentimentLabel() == -1) {
                            sentimentDistribution.merge("负面", 1, Integer::sum);
                        } else {
                            sentimentDistribution.merge("中性", 1, Integer::sum);
                        }
                    } else {
                        sentimentDistribution.merge("中性", 1, Integer::sum);
                    }
                    if (StringUtils.hasText(o.getKeywords())) {
                        String[] kws = o.getKeywords().split("[,，、;；\\s]+");
                        for (String kw : kws) {
                            if (kw.length() >= 2) {
                                keywordCounter.computeIfAbsent(kw, k -> new AtomicInteger()).incrementAndGet();
                            }
                        }
                    }
                }
                topKeywords = keywordCounter.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                        .limit(15)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("采集舆情数据失败", e);
        }
        data.put("opinionCount", count);
        data.put("sentimentDistribution", sentimentDistribution);
        data.put("topOpinionKeywords", topKeywords);
    }

    private void collectHotspotPredictionData(Map<String, Object> data, String areaCode) {
        List<Map<String, Object>> highRiskHotspots = new ArrayList<>();
        try {
            LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
            wrapper.ge(HotspotPrediction::getRiskLevel, 3);
            if (StringUtils.hasText(areaCode)) {
                wrapper.eq(HotspotPrediction::getAreaCode, areaCode);
            }
            wrapper.orderByDesc(HotspotPrediction::getModelRunTime);
            wrapper.last("LIMIT 20");
            List<HotspotPrediction> predictions = hotspotPredictionMapper.selectList(wrapper);
            if (predictions != null) {
                String lastBatch = null;
                for (HotspotPrediction p : predictions) {
                    if (lastBatch == null) {
                        lastBatch = p.getPredictionBatch();
                    }
                    if (!Objects.equals(lastBatch, p.getPredictionBatch())) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("predictionId", p.getPredictionId());
                    item.put("areaName", p.getAreaName());
                    item.put("gridCode", p.getGridCode());
                    item.put("caseType", p.getCaseTypeName() != null ? p.getCaseTypeName() : p.getCaseType());
                    item.put("riskLevel", p.getRiskLevel());
                    item.put("riskLevelName", p.getRiskLevelName());
                    item.put("riskScore", p.getRiskScore() != null ? p.getRiskScore().doubleValue() : null);
                    item.put("predictedCount", p.getPredictedCount());
                    item.put("centerLng", p.getGridCenterLng() != null ? p.getGridCenterLng().doubleValue() : null);
                    item.put("centerLat", p.getGridCenterLat() != null ? p.getGridCenterLat().doubleValue() : null);
                    item.put("suggestion", p.getPreventionSuggestion());
                    highRiskHotspots.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("采集热点预测数据失败", e);
        }
        data.put("hotspotPredictions", highRiskHotspots);
    }

    private void collectCaseClusterData(Map<String, Object> data, LocalDateTime startTime,
                                        LocalDateTime endTime, String areaCode) {
        List<Map<String, Object>> newClusters = new ArrayList<>();
        try {
            LambdaQueryWrapper<CaseCluster> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(CaseCluster::getCreateTime, startTime, endTime);
            if (StringUtils.hasText(areaCode)) {
                wrapper.eq(CaseCluster::getAreaCode, areaCode);
            }
            wrapper.orderByDesc(CaseCluster::getCreateTime);
            wrapper.last("LIMIT 10");
            List<CaseCluster> clusters = caseClusterMapper.selectList(wrapper);
            if (clusters != null) {
                for (CaseCluster c : clusters) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("clusterId", c.getClusterId());
                    item.put("clusterName", c.getClusterName());
                    item.put("caseType", c.getCaseTypeName() != null ? c.getCaseTypeName() : c.getCaseType());
                    item.put("caseCount", c.getCaseCount());
                    item.put("modusOperandi", c.getModusOperandi());
                    item.put("modusKeywords", c.getModusKeywords());
                    item.put("alertLevel", c.getAlertLevel());
                    item.put("alertLevelName", c.getAlertLevelName());
                    item.put("areaName", c.getAreaName());
                    item.put("startTime", c.getStartTime());
                    item.put("endTime", c.getEndTime());
                    item.put("similarity", c.getSimilarityScore() != null ? c.getSimilarityScore().doubleValue() : null);
                    item.put("suggestion", c.getInvestigationSuggestion());
                    newClusters.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("采集串并案数据失败", e);
        }
        data.put("caseClusters", newClusters);
    }

    private Integer extractCount(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildFallbackReport(ReportGenerateDTO dto, Map<String, Object> multiSourceData) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 治安态势分析报告\n\n");
        sb.append("## 一、情报摘要\n\n");
        sb.append(String.format("本报告统计周期为 **%s** 至 **%s**，期间共采集警情 **%d** 起，案件 **%d** 起，").append(
                dto.getReportStartDate(), dto.getReportEndDate(),
                extractCount(multiSourceData, "alarmCount"),
                extractCount(multiSourceData, "caseCount"));
        sb.append(String.format("重点人员 **%d** 名，舆情数据 **%d** 条。\n\n",
                extractCount(multiSourceData, "personCount"),
                extractCount(multiSourceData, "opinionCount")));

        sb.append("## 二、警情统计\n\n");
        sb.append("（数据不足或AI生成失败时使用的降级模板，建议检查DeepSeek服务状态）\n\n");

        sb.append("## 三、案件分析\n\n");
        sb.append("（降级模板，请稍后重试以获取完整报告）\n\n");

        sb.append("## 八、防范建议\n\n");
        sb.append("1. 加强重点区域巡逻防控\n");
        sb.append("2. 完善重点人员动态管控机制\n");
        sb.append("3. 及时响应网络舆情引导\n");
        return sb.toString();
    }

    private String extractTitle(String markdownReport, ReportGenerateDTO dto) {
        if (!StringUtils.hasText(markdownReport)) {
            return PRODUCT_TYPE_NAME_MAP.getOrDefault(dto.getProductType(), "治安态势分析报告");
        }
        String[] lines = markdownReport.split("\\n");
        for (String line : lines) {
            String trim = line.trim();
            if (trim.startsWith("# ")) {
                return trim.substring(2).trim();
            }
        }
        return PRODUCT_TYPE_NAME_MAP.getOrDefault(dto.getProductType(), "治安态势分析报告");
    }

    private String extractSummary(String markdownReport) {
        if (!StringUtils.hasText(markdownReport)) {
            return "";
        }
        try {
            int idx = markdownReport.indexOf("##");
            if (idx > 0) {
                String firstPart = markdownReport.substring(0, idx).trim();
                firstPart = firstPart.replaceAll("#", "").trim();
                String[] parts = firstPart.split("\\n+");
                StringBuilder sb = new StringBuilder();
                int count = 0;
                for (String p : parts) {
                    String trim = p.trim();
                    if (trim.length() > 0) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(trim);
                        count++;
                        if (count >= 3) break;
                    }
                }
                String result = sb.toString();
                return result.length() > 500 ? result.substring(0, 500) : result;
            }
        } catch (Exception e) {
            log.warn("提取报告摘要失败", e);
        }
        String plain = markdownReport.replaceAll("#", "").replaceAll("\\*", "").trim();
        return plain.length() > 500 ? plain.substring(0, 500) : plain;
    }

    private JSONObject extractStructuredMetrics(String markdownReport) {
        if (!StringUtils.hasText(markdownReport)) {
            return null;
        }
        try {
            String jsonBlockPattern = "```json";
            int startIdx = markdownReport.indexOf(jsonBlockPattern);
            if (startIdx >= 0) {
                int contentStart = startIdx + jsonBlockPattern.length();
                int endIdx = markdownReport.indexOf("```", contentStart);
                if (endIdx > contentStart) {
                    String jsonStr = markdownReport.substring(contentStart, endIdx).trim();
                    return JSON.parseObject(jsonStr);
                }
            }
            int jsonStart = markdownReport.indexOf("{");
            int jsonEnd = markdownReport.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String candidate = markdownReport.substring(jsonStart, jsonEnd + 1);
                if (candidate.length() < 10000) {
                    try {
                        return JSON.parseObject(candidate);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取AI结构化指标失败", e);
        }
        return null;
    }

    private void fillProductIdAndNo(IntelligenceProduct product) {
        String snowflakeId = SnowflakeIdUtil.nextIdStr();
        String suffix = snowflakeId.length() > 10
                ? snowflakeId.substring(snowflakeId.length() - 10)
                : snowflakeId;
        String productId = "IP" + suffix;

        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = PRODUCT_NO_SEQUENCE.incrementAndGet() % PRODUCT_NO_SEQUENCE_BOUND;
        String productNo = "IP" + dateStr + String.format("%04d", seq);

        product.setProductId(productId);
        product.setProductNo(productNo);
    }

    private void sendProductGenerateNotification(IntelligenceProduct product) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("productId", product.getProductId());
            msg.put("productNo", product.getProductNo());
            msg.put("productType", product.getProductType());
            msg.put("productTypeName", product.getProductTypeName());
            msg.put("title", product.getTitle());
            msg.put("generateTime", product.getGenerateTime());
            msg.put("status", product.getStatus());
            msg.put("statusName", product.getStatusName());
            msg.put("modelId", product.getModelId());
            msg.put("modelName", product.getModelName());

            mqUtil.sendAsync(MqConstant.REAL_TIME_STAT_TOPIC + ":intelligence_product", msg);
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("intelligence_product", msg));
            log.debug("情报产品通知发送成功: productId={}", product.getProductId());
        } catch (Exception e) {
            log.warn("情报产品通知发送失败: productId={}", product.getProductId(), e);
        }
    }
}
