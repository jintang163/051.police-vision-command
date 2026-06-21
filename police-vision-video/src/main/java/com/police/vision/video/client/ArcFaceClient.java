package com.police.vision.video.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.video.config.AiConfig;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArcFaceClient {

    private final AiConfig aiConfig;

    public float[] extractFeature(byte[] image) {
        if (!aiConfig.getArcface().isEnabled()) {
            return simulateFeature(image);
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(aiConfig.getArcface().getBaseUrl() + aiConfig.getArcface().getExtractEndpoint());
            post.addHeader("X-API-Key", aiConfig.getArcface().getApiKey());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("image", new ByteArrayInputStream(image), ContentType.IMAGE_JPEG, "face.jpg");
            post.setEntity(builder.build());

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> map = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(map.get("success"))) {
                    List<Float> featureList = (List<Float>) map.get("feature");
                    float[] feature = new float[featureList.size()];
                    for (int i = 0; i < featureList.size(); i++) {
                        feature[i] = featureList.get(i);
                    }
                    return normalizeFeature(feature);
                }
                log.error("ArcFace特征提取失败：{}", map.get("message"));
                return simulateFeature(image);
            });
        } catch (Exception e) {
            log.error("调用ArcFace特征提取接口失败，使用模拟数据：{}", e.getMessage());
            return simulateFeature(image);
        }
    }

    public Map<String, Object> compareFaces(byte[] image1, byte[] image2) {
        if (!aiConfig.getArcface().isEnabled()) {
            return simulateCompare();
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(aiConfig.getArcface().getBaseUrl() + aiConfig.getArcface().getCompareEndpoint());
            post.addHeader("X-API-Key", aiConfig.getArcface().getApiKey());
            post.addHeader("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<>();
            body.put("image1", Base64.getEncoder().encodeToString(image1));
            body.put("image2", Base64.getEncoder().encodeToString(image2));
            post.setEntity(new StringEntity(JSON.toJSONString(body), ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> map = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(map.get("success"))) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("similarity", map.get("similarity"));
                    data.put("isMatch", ((Number) map.get("similarity")).doubleValue() > 0.8);
                    return data;
                }
                return simulateCompare();
            });
        } catch (Exception e) {
            log.error("调用ArcFace人脸比对接口失败，使用模拟数据：{}", e.getMessage());
            return simulateCompare();
        }
    }

    public List<Map<String, Object>> searchFace(byte[] image, float threshold, int topK) {
        if (!aiConfig.getArcface().isEnabled()) {
            return List.of();
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(aiConfig.getArcface().getBaseUrl() + aiConfig.getArcface().getSearchEndpoint());
            post.addHeader("X-API-Key", aiConfig.getArcface().getApiKey());
            post.addHeader("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<>();
            body.put("image", Base64.getEncoder().encodeToString(image));
            body.put("threshold", threshold);
            body.put("topK", topK);
            post.setEntity(new StringEntity(JSON.toJSONString(body), ContentType.APPLICATION_JSON));

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> map = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(map.get("success"))) {
                    return (List<Map<String, Object>>) map.get("results");
                }
                return List.of();
            });
        } catch (Exception e) {
            log.error("调用ArcFace人脸搜索接口失败：{}", e.getMessage());
            return List.of();
        }
    }

    private float[] simulateFeature(byte[] image) {
        float[] feature = new float[512];
        java.util.Random random = new java.util.Random(image.length);
        for (int i = 0; i < 512; i++) {
            feature[i] = random.nextFloat() * 2 - 1;
        }
        return normalizeFeature(feature);
    }

    private float[] normalizeFeature(float[] feature) {
        float norm = 0;
        for (float v : feature) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < feature.length; i++) {
            feature[i] /= norm;
        }
        return feature;
    }

    private Map<String, Object> simulateCompare() {
        Map<String, Object> result = new HashMap<>();
        result.put("similarity", 0.7 + Math.random() * 0.3);
        result.put("isMatch", Math.random() > 0.3);
        return result;
    }
}
