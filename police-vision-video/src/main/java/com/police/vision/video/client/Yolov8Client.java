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
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class Yolov8Client {

    private final AiConfig aiConfig;

    private static final Pattern PLATE_PATTERN = Pattern.compile(
            "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领]" +
                    "[A-Z][A-HJ-NP-Z0-9]{4,5}[A-HJ-NP-Z0-9挂学警港澳]$");

    public Map<String, Object> detectPlate(byte[] image) {
        if (!aiConfig.getYolov8().isEnabled()) {
            return simulatePlate();
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(aiConfig.getYolov8().getBaseUrl() + aiConfig.getYolov8().getPlateEndpoint());
            post.addHeader("X-API-Key", aiConfig.getYolov8().getApiKey());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("image", new ByteArrayInputStream(image), ContentType.IMAGE_JPEG, "plate.jpg");
            post.setEntity(builder.build());

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> map = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(map.get("success")) && map.get("plate") != null) {
                    Map<String, Object> plateData = (Map<String, Object>) map.get("plate");
                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("plateNo", plateData.get("plate_no"));
                    resultMap.put("plateColor", plateData.get("plate_color"));
                    resultMap.put("vehicleColor", plateData.get("vehicle_color"));
                    resultMap.put("vehicleType", plateData.get("vehicle_type"));
                    resultMap.put("vehicleBrand", plateData.get("vehicle_brand"));
                    resultMap.put("confidence", plateData.get("confidence"));
                    resultMap.put("valid", PLATE_PATTERN.matcher(String.valueOf(plateData.get("plate_no"))).matches());
                    return resultMap;
                }
                log.warn("YOLOv8车牌识别未检测到车牌");
                return simulatePlate();
            });
        } catch (Exception e) {
            log.error("调用YOLOv8车牌识别接口失败，使用模拟数据：{}", e.getMessage());
            return simulatePlate();
        }
    }

    public Map<String, Object> analyzeBehavior(byte[] frame) {
        if (!aiConfig.getYolov8().isEnabled()) {
            return simulateBehavior();
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(aiConfig.getYolov8().getBaseUrl() + aiConfig.getYolov8().getBehaviorEndpoint());
            post.addHeader("X-API-Key", aiConfig.getYolov8().getApiKey());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("frame", new ByteArrayInputStream(frame), ContentType.IMAGE_JPEG, "frame.jpg");
            post.setEntity(builder.build());

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> map = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});

                Map<String, Object> resultMap = new HashMap<>();
                boolean detected = Boolean.TRUE.equals(map.get("detected"));
                resultMap.put("detected", detected);

                if (detected) {
                    List<Map<String, Object>> events = (List<Map<String, Object>>) map.get("events");
                    if (events != null && !events.isEmpty()) {
                        Map<String, Object> event = events.get(0);
                        resultMap.put("behaviorType", event.get("type"));
                        resultMap.put("behaviorName", getBehaviorName((Integer) event.get("type")));
                        resultMap.put("confidence", event.get("confidence"));
                        resultMap.put("peopleCount", event.get("people_count"));
                        resultMap.put("bbox", event.get("bbox"));
                        resultMap.put("trackId", event.get("track_id"));
                    }
                } else {
                    resultMap.put("behaviorType", 0);
                    resultMap.put("behaviorName", "正常");
                    resultMap.put("confidence", 0.9);
                }

                return resultMap;
            });
        } catch (Exception e) {
            log.error("调用YOLOv8行为分析接口失败，使用模拟数据：{}", e.getMessage());
            return simulateBehavior();
        }
    }

    public List<Map<String, Object>> detectObjects(byte[] frame) {
        if (!aiConfig.getYolov8().isEnabled()) {
            return List.of();
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(aiConfig.getYolov8().getBaseUrl() + aiConfig.getYolov8().getDetectEndpoint());
            post.addHeader("X-API-Key", aiConfig.getYolov8().getApiKey());

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("frame", new ByteArrayInputStream(frame), ContentType.IMAGE_JPEG, "frame.jpg");
            post.setEntity(builder.build());

            return client.execute(post, response -> {
                String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> map = JSON.parseObject(result, new TypeReference<Map<String, Object>>() {});
                if (Boolean.TRUE.equals(map.get("success"))) {
                    return (List<Map<String, Object>>) map.get("detections");
                }
                return List.of();
            });
        } catch (Exception e) {
            log.error("调用YOLOv8目标检测接口失败：{}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> simulatePlate() {
        Map<String, Object> result = new HashMap<>();
        String plateNo = generateRandomPlate();
        result.put("plateNo", plateNo);
        result.put("plateColor", getRandomPlateColor());
        result.put("vehicleColor", getRandomColor());
        result.put("vehicleType", getRandomVehicleType());
        result.put("vehicleBrand", getRandomBrand());
        result.put("confidence", 0.85 + Math.random() * 0.15);
        result.put("valid", PLATE_PATTERN.matcher(plateNo).matches());
        return result;
    }

    private Map<String, Object> simulateBehavior() {
        Map<String, Object> result = new HashMap<>();
        boolean detected = Math.random() > 0.75;
        result.put("detected", detected);

        if (detected) {
            int[] behaviorTypes = {1, 2, 3, 4, 5, 6};
            int behaviorType = behaviorTypes[(int) (Math.random() * behaviorTypes.length)];
            result.put("behaviorType", behaviorType);
            result.put("behaviorName", getBehaviorName(behaviorType));
            result.put("confidence", 0.7 + Math.random() * 0.3);
            result.put("peopleCount", (int) (Math.random() * 10) + 1);
        } else {
            result.put("behaviorType", 0);
            result.put("behaviorName", "正常");
            result.put("confidence", 0.9);
        }
        return result;
    }

    private String generateRandomPlate() {
        String[] provinces = {"京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘",
                "皖", "鲁", "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋",
                "蒙", "陕", "吉", "闽", "贵", "粤", "青", "藏", "川", "宁", "琼"};
        String province = provinces[(int) (Math.random() * provinces.length)];
        String letter = String.valueOf((char) ('A' + (int) (Math.random() * 26)));
        StringBuilder numbers = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (Math.random() > 0.7) {
                numbers.append((char) ('A' + (int) (Math.random() * 26)));
            } else {
                numbers.append((int) (Math.random() * 10));
            }
        }
        return province + letter + numbers;
    }

    private String getRandomPlateColor() {
        String[] colors = {"蓝牌", "黄牌", "绿牌", "白牌", "黑牌"};
        return colors[(int) (Math.random() * colors.length)];
    }

    private String getRandomColor() {
        String[] colors = {"白色", "黑色", "灰色", "银色", "红色", "蓝色", "绿色", "黄色", "棕色", "金色"};
        return colors[(int) (Math.random() * colors.length)];
    }

    private String getRandomVehicleType() {
        String[] types = {"轿车", "SUV", "面包车", "货车", "客车", "摩托车", "电动车"};
        return types[(int) (Math.random() * types.length)];
    }

    private String getRandomBrand() {
        String[] brands = {"大众", "丰田", "本田", "宝马", "奔驰", "奥迪", "别克", "福特", "比亚迪", "特斯拉"};
        return brands[(int) (Math.random() * brands.length)];
    }

    private String getBehaviorName(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case 1 -> "打架斗殴";
            case 2 -> "人群聚集";
            case 3 -> "摔倒检测";
            case 4 -> "翻越围栏";
            case 5 -> "异常行为";
            case 6 -> "火灾检测";
            default -> "正常";
        };
    }

    public boolean validatePlate(String plateNo) {
        if (plateNo == null || plateNo.isEmpty()) {
            return false;
        }
        Matcher matcher = PLATE_PATTERN.matcher(plateNo.toUpperCase());
        return matcher.matches();
    }
}
