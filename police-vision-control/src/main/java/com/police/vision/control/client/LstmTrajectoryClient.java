package com.police.vision.control.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.control.config.ControlAiConfig;
import com.police.vision.control.entity.PersonTrackPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LstmTrajectoryClient {

    private final ControlAiConfig aiConfig;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> predict(String personId, List<PersonTrackPoint> historyPoints, int predictMinutes, int topK) {
        ControlAiConfig.LstmTrajectoryConfig cfg = aiConfig.getLstm();
        if (!cfg.isEnabled() || historyPoints == null || historyPoints.size() < 20) {
            return simulatePredict(personId, historyPoints, predictMinutes, topK);
        }
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(cfg.getBaseUrl() + cfg.getPredictEndpoint());
            post.addHeader("X-API-Key", cfg.getApiKey());
            post.addHeader("Content-Type", "application/json");

            Map<String, Object> request = buildPredictRequest(personId, historyPoints, predictMinutes, topK);
            post.setEntity(new StringEntity(JSON.toJSONString(request), ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> resp = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(resp.get("success")) && resp.get("data") != null) {
                    return (Map<String, Object>) resp.get("data");
                }
                log.warn("LSTM预测服务返回异常，使用模拟数据: {}", resp.get("message"));
                return simulatePredict(personId, historyPoints, predictMinutes, topK);
            });
        } catch (Exception e) {
            log.error("调用LSTM预测服务失败，使用模拟数据: {}", e.getMessage());
            return simulatePredict(personId, historyPoints, predictMinutes, topK);
        }
    }

    public Map<String, Object> train(String personId, List<PersonTrackPoint> trainPoints, String modelVersion) {
        ControlAiConfig.LstmTrajectoryConfig cfg = aiConfig.getLstm();
        if (!cfg.isEnabled()) {
            return simulateTrain(personId, trainPoints, modelVersion);
        }
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(cfg.getBaseUrl() + cfg.getTrainEndpoint());
            post.addHeader("X-API-Key", cfg.getApiKey());
            post.addHeader("Content-Type", "application/json");

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("person_id", personId);
            request.put("model_version", modelVersion);
            request.put("train_data", convertTrackPointsToMap(trainPoints));
            request.put("params", Map.of(
                    "seq_len", 50,
                    "pred_len", 3,
                    "hidden_size", 64,
                    "num_layers", 2,
                    "batch_size", 64,
                    "epochs", 50,
                    "learning_rate", 0.001,
                    "validation_split", 0.2
            ));
            post.setEntity(new StringEntity(JSON.toJSONString(request), ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> resp = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(resp.get("success")) && resp.get("data") != null) {
                    return (Map<String, Object>) resp.get("data");
                }
                log.warn("LSTM训练服务返回异常: {}", resp.get("message"));
                return simulateTrain(personId, trainPoints, modelVersion);
            });
        } catch (Exception e) {
            log.error("调用LSTM训练服务失败，使用模拟训练结果: {}", e.getMessage());
            return simulateTrain(personId, trainPoints, modelVersion);
        }
    }

    public Map<String, Object> evaluate(String personId, List<PersonTrackPoint> evalPoints, String modelVersion) {
        ControlAiConfig.LstmTrajectoryConfig cfg = aiConfig.getLstm();
        if (!cfg.isEnabled()) {
            return simulateEvaluate(personId, evalPoints, modelVersion);
        }
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(cfg.getBaseUrl() + cfg.getEvaluateEndpoint());
            post.addHeader("X-API-Key", cfg.getApiKey());
            post.addHeader("Content-Type", "application/json");

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("person_id", personId);
            request.put("model_version", modelVersion);
            request.put("eval_data", convertTrackPointsToMap(evalPoints));
            request.put("distance_thresholds", Arrays.asList(30, 50, 100));
            post.setEntity(new StringEntity(JSON.toJSONString(request), ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> resp = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(resp.get("success")) && resp.get("data") != null) {
                    return (Map<String, Object>) resp.get("data");
                }
                log.warn("LSTM评估服务返回异常: {}", resp.get("message"));
                return simulateEvaluate(personId, evalPoints, modelVersion);
            });
        } catch (Exception e) {
            log.error("调用LSTM评估服务失败，使用模拟评估结果: {}", e.getMessage());
            return simulateEvaluate(personId, evalPoints, modelVersion);
        }
    }

    private Map<String, Object> buildPredictRequest(String personId, List<PersonTrackPoint> historyPoints,
                                                     int predictMinutes, int topK) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("person_id", personId);
        request.put("predict_minutes", predictMinutes);
        request.put("top_k", topK);
        request.put("history_data", convertTrackPointsToMap(historyPoints));
        request.put("current_time", LocalDateTime.now().format(FORMATTER));
        return request;
    }

    private List<Map<String, Object>> convertTrackPointsToMap(List<PersonTrackPoint> points) {
        if (points == null) return Collections.emptyList();
        return points.stream()
                .sorted(Comparator.comparing(PersonTrackPoint::getGpsTime))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("t", p.getGpsTime() != null ? p.getGpsTime().format(FORMATTER) : null);
                    m.put("lng", p.getLongitude() != null ? p.getLongitude().doubleValue() : null);
                    m.put("lat", p.getLatitude() != null ? p.getLatitude().doubleValue() : null);
                    m.put("speed", p.getSpeed() != null ? p.getSpeed().doubleValue() : null);
                    m.put("dir", p.getDirection() != null ? p.getDirection().doubleValue() : null);
                    m.put("src", p.getSourceType());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> simulatePredict(String personId, List<PersonTrackPoint> historyPoints,
                                                 int predictMinutes, int topK) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model_version", aiConfig.getLstm().getDefaultModelVersion());
        result.put("predict_minutes", predictMinutes);
        result.put("top_k", topK);
        result.put("history_sample_count", historyPoints != null ? historyPoints.size() : 0);
        result.put("used_simulated", true);

        List<Map<String, Object>> predictions = new ArrayList<>();
        Random random = new Random();

        double baseLng = 116.4074;
        double baseLat = 39.9042;
        if (historyPoints != null && !historyPoints.isEmpty()) {
            PersonTrackPoint last = historyPoints.get(0);
            baseLng = last.getLongitude() != null ? last.getLongitude().doubleValue() : baseLng;
            baseLat = last.getLatitude() != null ? last.getLatitude().doubleValue() : baseLat;
        }

        double[][] offsets = {{0.0, 0.0}, {0.008, -0.005}, {-0.006, 0.009}};
        String[] descs = {"当前位置区域", "常去活动点A", "常去活动点B"};
        double[] probs = {0.55, 0.28, 0.17};

        for (int i = 0; i < Math.min(topK, 3); i++) {
            Map<String, Object> pred = new LinkedHashMap<>();
            pred.put("rank", i + 1);
            pred.put("longitude", Math.round((baseLng + offsets[i][0] + (random.nextDouble() - 0.5) * 0.002) * 1e6) / 1e6);
            pred.put("latitude", Math.round((baseLat + offsets[i][1] + (random.nextDouble() - 0.5) * 0.002) * 1e6) / 1e6);
            pred.put("probability", probs[i]);
            pred.put("location_desc", descs[i] + "(模拟预测)");
            pred.put("predict_time", LocalDateTime.now().format(FORMATTER));
            predictions.add(pred);
        }

        result.put("predictions", predictions);
        result.put("predict_window_start", LocalDateTime.now().plusMinutes(5).format(FORMATTER));
        result.put("predict_window_end", LocalDateTime.now().plusMinutes(predictMinutes).format(FORMATTER));
        return result;
    }

    private Map<String, Object> simulateTrain(String personId, List<PersonTrackPoint> trainPoints, String modelVersion) {
        Random random = new Random();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model_version", modelVersion);
        result.put("person_id", personId);
        result.put("train_sample_count", trainPoints != null ? trainPoints.size() : 50000);
        result.put("train_loss", 0.00123 + random.nextDouble() * 0.0005);
        result.put("val_loss", 0.00145 + random.nextDouble() * 0.0005);
        result.put("epochs", 50);
        result.put("duration_seconds", 128 + random.nextInt(60));
        result.put("model_path", "oss://police-vision-models/lstm/" + modelVersion + ".pt");
        result.put("used_simulated", true);
        return result;
    }

    private Map<String, Object> simulateEvaluate(String personId, List<PersonTrackPoint> evalPoints, String modelVersion) {
        Random random = new Random();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model_version", modelVersion);
        result.put("person_id", personId);
        result.put("eval_sample_count", evalPoints != null ? evalPoints.size() : 10000);

        double baseAcc = 0.70 + random.nextDouble() * 0.12;
        result.put("eval_mae", 35.2 + random.nextDouble() * 15.0);
        result.put("eval_rmse", 52.8 + random.nextDouble() * 20.0);
        result.put("eval_accuracy_top1", baseAcc);
        result.put("eval_accuracy_top3", baseAcc + 0.10 + random.nextDouble() * 0.05);
        result.put("eval_accuracy_30m", baseAcc - 0.08 - random.nextDouble() * 0.04);
        result.put("eval_accuracy_50m", baseAcc);
        result.put("eval_accuracy_100m", baseAcc + 0.12 + random.nextDouble() * 0.06);
        result.put("accuracy_estimate", baseAcc);
        result.put("used_simulated", true);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("hourly_accuracy", Map.of(
                "morning", baseAcc + 0.03,
                "afternoon", baseAcc - 0.01,
                "evening", baseAcc + 0.02,
                "night", baseAcc - 0.06
        ));
        report.put("weekday_accuracy", Map.of(
                "weekday", baseAcc + 0.02,
                "weekend", baseAcc - 0.03
        ));
        report.put("distance_distribution", Map.of(
                "within_30m", baseAcc - 0.08,
                "within_50m", baseAcc,
                "within_100m", baseAcc + 0.12,
                "within_200m", baseAcc + 0.18
        ));
        result.put("eval_report", report);
        return result;
    }
}
