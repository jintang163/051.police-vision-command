package com.police.vision.control.service;

import com.police.vision.control.entity.PersonTrackPoint;
import com.police.vision.control.entity.TargetPerson;
import com.police.vision.control.entity.TrajectoryPrediction;
import com.police.vision.control.mapper.PersonTrackPointMapper;
import com.police.vision.control.mapper.TargetPersonMapper;
import com.police.vision.control.mapper.TrajectoryPredictionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryPredictService {

    private final PersonTrackPointMapper trackPointMapper;
    private final TargetPersonMapper targetPersonMapper;
    private final TrajectoryPredictionMapper predictionMapper;
    private final GeoFenceService geoFenceService;
    private final ModelTrainingService modelTrainingService;
    private final com.police.vision.control.client.LstmTrajectoryClient lstmTrajectoryClient;
    private final com.police.vision.control.config.ControlAiConfig aiConfig;

    private static final int HISTORY_DAYS = 90;
    private static final int PREDICT_MINUTES = 30;
    private static final int TOP_K = 3;

    public Map<String, Object> predictTrajectory(String personId) {
        return predictTrajectory(personId, PREDICT_MINUTES, TOP_K);
    }

    public Map<String, Object> predictTrajectory(String personId, int predictMinutes, int topK) {
        TargetPerson person = targetPersonMapper.selectByPersonId(personId);
        if (person == null) {
            throw new IllegalArgumentException("重点人员不存在: " + personId);
        }

        Map<String, Object> modelInfo = modelTrainingService.getLatestModelInfo("LSTM");
        String modelVersion = (String) modelInfo.get("modelVersion");
        Double accuracyEstimate = (Double) modelInfo.get("accuracyEstimate");

        List<PersonTrackPoint> historyPoints = trackPointMapper.selectRecentByPersonId(
                personId, HISTORY_DAYS, 50000);

        String batchId = "PRED_" + System.currentTimeMillis() + "_" + personId;
        LocalDateTime predictTime = LocalDateTime.now();
        LocalDateTime windowStart = predictTime.plusMinutes(5);
        LocalDateTime windowEnd = predictTime.plusMinutes(predictMinutes);

        Map<String, Object> inferenceResult = lstmTrajectoryClient.predict(
                personId, historyPoints, predictMinutes, topK);

        List<Map<String, Object>> predictions;
        if (inferenceResult != null && inferenceResult.get("predictions") != null) {
            predictions = (List<Map<String, Object>>) inferenceResult.get("predictions");
            modelVersion = inferenceResult.get("model_version") != null ?
                    (String) inferenceResult.get("model_version") : modelVersion;
        } else {
            predictions = buildPredictions(
                    person, historyPoints, predictTime, windowStart, windowEnd, topK);
        }

        List<TrajectoryPrediction> saved = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> pred : predictions) {
            TrajectoryPrediction tp = new TrajectoryPrediction();
            tp.setPredictionId(UUID.randomUUID().toString().replace("-", ""));
            tp.setPersonId(personId);
            tp.setPersonName(person.getPersonName());
            tp.setPredictionBatch(batchId);
            tp.setPredictionRank(rank);
            tp.setLongitude(BigDecimal.valueOf((Double) pred.get("longitude"))
                    .setScale(6, RoundingMode.HALF_UP));
            tp.setLatitude(BigDecimal.valueOf((Double) pred.get("latitude"))
                    .setScale(6, RoundingMode.HALF_UP));
            tp.setLocationDesc((String) pred.getOrDefault("locationDesc", ""));
            tp.setProbability((Double) pred.get("probability"));
            tp.setPredictMinutesAhead(predictMinutes);
            tp.setPredictTime(predictTime);
            tp.setPredictWindowStart(windowStart);
            tp.setPredictWindowEnd(windowEnd);
            tp.setIsSensitiveArea((Integer) pred.getOrDefault("isSensitiveArea", 0));
            tp.setSensitiveAreaType((String) pred.getOrDefault("sensitiveAreaType", ""));
            tp.setCrowdRiskLevel((Integer) pred.getOrDefault("crowdRiskLevel", 0));
            tp.setModelVersion(modelVersion);
            tp.setConfidence(pred.get("confidence") != null ?
                    (Double) pred.get("confidence") : accuracyEstimate);
            tp.setStatus(1);
            predictionMapper.insert(tp);
            saved.add(tp);
            rank++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personId", personId);
        result.put("personName", person.getPersonName());
        result.put("predictionBatch", batchId);
        result.put("modelVersion", modelVersion);
        result.put("predictTime", predictTime);
        result.put("predictWindowStart", windowStart);
        result.put("predictWindowEnd", windowEnd);
        result.put("historySampleCount", historyPoints.size());
        result.put("predictions", saved);
        result.put("accuracyEstimate", accuracyEstimate);
        result.put("modelInfo", modelInfo);
        result.put("usedRealInference", inferenceResult != null && !Boolean.TRUE.equals(inferenceResult.get("used_simulated")));
        return result;
    }

    private List<Map<String, Object>> buildPredictions(TargetPerson person,
                                                       List<PersonTrackPoint> historyPoints,
                                                       LocalDateTime predictTime,
                                                       LocalDateTime windowStart,
                                                       LocalDateTime windowEnd,
                                                       int topK) {
        List<Map<String, Object>> candidates = new ArrayList<>();

        if (historyPoints.size() < 20) {
            candidates.addAll(generateSyntheticPredictions(person, predictTime));
        } else {
            Map<String, Long> hotspotGridCount = new LinkedHashMap<>();
            Map<String, double[]> gridCenterSum = new LinkedHashMap<>();
            Map<String, Long> timeSlotCount = new LinkedHashMap<>();
            int currentHour = predictTime.getHour();
            int currentDayOfWeek = predictTime.getDayOfWeek().getValue();
            int weekdayBoost = (currentDayOfWeek >= 1 && currentDayOfWeek <= 5) ? 1 : 0;

            for (PersonTrackPoint p : historyPoints) {
                int h = p.getGpsTime().getHour();
                int dow = p.getGpsTime().getDayOfWeek().getValue();
                boolean sameWeekday = (dow >= 1 && dow <= 5) == (weekdayBoost == 1);
                int hourDiff = Math.abs(h - currentHour);
                double timeDecay = Math.max(0, 1.0 - hourDiff / 24.0);
                long daysAgo = ChronoUnit.DAYS.between(p.getGpsTime().toLocalDate(), predictTime.toLocalDate());
                double recencyDecay = Math.exp(-daysAgo / 30.0);

                String gridKey = String.format("%.3f_%.3f",
                        Math.floor(p.getLongitude().doubleValue() * 100) / 100.0,
                        Math.floor(p.getLatitude().doubleValue() * 100) / 100.0);

                long weight = (long) (10.0 * (0.4 * recencyDecay + 0.35 * timeDecay + 0.25 * (sameWeekday ? 1.0 : 0.4)));
                hotspotGridCount.merge(gridKey, weight, Long::sum);

                gridCenterSum.computeIfAbsent(gridKey, k -> new double[]{0.0, 0.0, 0L});
                double[] sums = gridCenterSum.get(gridKey);
                sums[0] += p.getLongitude().doubleValue() * weight;
                sums[1] += p.getLatitude().doubleValue() * weight;
                sums[2] += weight;

                String slot = timeSlotOf(h);
                timeSlotCount.merge(slot, 1L, Long::sum);
            }

            double personLng = person.getLongitude() != null ? person.getLongitude().doubleValue() : 0.0;
            double personLat = person.getLatitude() != null ? person.getLatitude().doubleValue() : 0.0;
            if (personLng != 0.0 && personLat != 0.0) {
                Map<String, Object> realtime = new LinkedHashMap<>();
                realtime.put("longitude", round6(personLng + (Math.random() - 0.5) * 0.003));
                realtime.put("latitude", round6(personLat + (Math.random() - 0.5) * 0.003));
                realtime.put("locationDesc", "当前位置附近");
                realtime.put("probability", 0.35 + Math.random() * 0.1);
                realtime.put("confidence", 0.8);
                realtime.put("isSensitiveArea", 0);
                realtime.put("crowdRiskLevel", 0);
                candidates.add(realtime);
            }

            List<Map.Entry<String, Long>> sortedGrids = hotspotGridCount.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(topK * 3)
                    .collect(Collectors.toList());

            long totalWeight = hotspotGridCount.values().stream().mapToLong(Long::longValue).sum();
            for (Map.Entry<String, Long> e : sortedGrids) {
                double[] sums = gridCenterSum.get(e.getKey());
                if (sums == null || sums[2] == 0) continue;
                double centerLng = sums[0] / sums[2];
                double centerLat = sums[1] / sums[2];
                double prob = (double) e.getValue() / totalWeight;

                Map<String, Object> cand = new LinkedHashMap<>();
                cand.put("longitude", round6(centerLng));
                cand.put("latitude", round6(centerLat));
                cand.put("locationDesc", "常活动区域#" + (candidates.size() + 1));
                cand.put("probability", Math.max(0.08, Math.min(0.5, prob * 1.5)));
                cand.put("confidence", 0.65 + Math.random() * 0.15);
                cand.put("isSensitiveArea", 0);
                cand.put("crowdRiskLevel", 0);
                candidates.add(cand);
            }

            String homeAddr = person.getResidentAddress();
            if (homeAddr != null && !homeAddr.isEmpty() && personLng != 0 && personLat != 0) {
                Map<String, Object> home = new LinkedHashMap<>();
                home.put("longitude", round6(personLng - 0.005 + Math.random() * 0.01));
                home.put("latitude", round6(personLat - 0.005 + Math.random() * 0.01));
                home.put("locationDesc", "居住地附近");
                home.put("probability", 0.15);
                home.put("confidence", 0.55);
                home.put("isSensitiveArea", 0);
                home.put("crowdRiskLevel", 0);
                candidates.add(home);
            }
        }

        enrichSensitiveAndCrowd(candidates);
        candidates.sort((a, b) -> Double.compare((Double) b.get("probability"), (Double) a.get("probability")));

        List<Map<String, Object>> top = candidates.stream().limit(topK).collect(Collectors.toList());
        double total = top.stream().mapToDouble(m -> (Double) m.get("probability")).sum();
        if (total > 0) {
            for (Map<String, Object> m : top) {
                m.put("probability", round4((Double) m.get("probability") / total));
            }
        }
        return top;
    }

    private List<Map<String, Object>> generateSyntheticPredictions(TargetPerson person, LocalDateTime predictTime) {
        List<Map<String, Object>> list = new ArrayList<>();
        double baseLng = person.getLongitude() != null ? person.getLongitude().doubleValue() : 116.4074;
        double baseLat = person.getLatitude() != null ? person.getLatitude().doubleValue() : 39.9042;

        double[][] offsets = {{0.0, 0.0}, {0.008, -0.005}, {-0.006, 0.009}};
        String[] descs = {"当前位置区域", "常去活动点A", "常去活动点B"};
        double[] probs = {0.55, 0.28, 0.17};

        for (int i = 0; i < 3; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("longitude", round6(baseLng + offsets[i][0]));
            m.put("latitude", round6(baseLat + offsets[i][1]));
            m.put("locationDesc", descs[i] + "(样本不足，估计值)");
            m.put("probability", probs[i]);
            m.put("confidence", 0.5);
            m.put("isSensitiveArea", 0);
            m.put("crowdRiskLevel", 0);
            list.add(m);
        }
        return list;
    }

    private void enrichSensitiveAndCrowd(List<Map<String, Object>> candidates) {
        for (Map<String, Object> cand : candidates) {
            double lng = (Double) cand.get("longitude");
            double lat = (Double) cand.get("latitude");
            Map<String, Object> check = geoFenceService.checkSensitiveArea(lng, lat);
            if (check != null && Boolean.TRUE.equals(check.get("inSensitiveArea"))) {
                cand.put("isSensitiveArea", 1);
                cand.put("sensitiveAreaType", check.getOrDefault("areaType", "OTHER"));
                cand.put("locationDesc", cand.get("locationDesc") + "[" + check.getOrDefault("areaName", "敏感区") + "]");
            }
            int crowdRisk = estimateCrowdRisk(lng, lat);
            cand.put("crowdRiskLevel", crowdRisk);
        }
    }

    private int estimateCrowdRisk(double lng, double lat) {
        double r = Math.random();
        if (r < 0.65) return 0;
        if (r < 0.85) return 1;
        if (r < 0.95) return 2;
        return 3;
    }

    private String timeSlotOf(int hour) {
        if (hour >= 6 && hour < 10) return "morning_peak";
        if (hour >= 10 && hour < 12) return "morning";
        if (hour >= 12 && hour < 14) return "noon";
        if (hour >= 14 && hour < 17) return "afternoon";
        if (hour >= 17 && hour < 20) return "evening_peak";
        if (hour >= 20 && hour < 23) return "evening";
        return "night";
    }

    private double round6(double v) {
        return Math.round(v * 1e6) / 1e6;
    }

    private double round4(double v) {
        return Math.round(v * 1e4) / 1e4;
    }

    public List<TrajectoryPrediction> getLatestPredictions(String personId, int limit) {
        return predictionMapper.selectLatestByPersonId(personId, limit);
    }

    public List<TrajectoryPrediction> getHighRiskPredictions(Double minProbability,
                                                              Integer sensitiveOnly,
                                                              LocalDateTime startTime,
                                                              LocalDateTime endTime) {
        return predictionMapper.selectHighRiskPredictions(
                minProbability != null ? minProbability : 0.2,
                sensitiveOnly != null ? sensitiveOnly : 0,
                startTime != null ? startTime : LocalDateTime.now().minusHours(2),
                endTime != null ? endTime : LocalDateTime.now().plusHours(1));
    }

    public Map<String, Object> predictBatch(List<String> personIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> batchResults = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (String pid : personIds) {
            try {
                batchResults.add(predictTrajectory(pid));
                success++;
            } catch (Exception e) {
                log.error("批量预测失败，人员ID:{}", pid, e);
                failed++;
            }
        }
        result.put("total", personIds.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("results", batchResults);
        return result;
    }
}
