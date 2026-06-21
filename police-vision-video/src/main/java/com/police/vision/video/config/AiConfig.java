package com.police.vision.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private ArcFaceConfig arcface = new ArcFaceConfig();
    private Yolov8Config yolov8 = new Yolov8Config();
    private BehaviorConfig behavior = new BehaviorConfig();

    @Data
    public static class ArcFaceConfig {
        private boolean enabled = true;
        private String baseUrl = "http://127.0.0.1:8000";
        private String apiKey = "arcface-api-key-2024";
        private String extractEndpoint = "/api/v1/face/extract";
        private String compareEndpoint = "/api/v1/face/compare";
        private String searchEndpoint = "/api/v1/face/search";
        private int timeout = 10000;
    }

    @Data
    public static class Yolov8Config {
        private boolean enabled = true;
        private String baseUrl = "http://127.0.0.1:8001";
        private String apiKey = "yolov8-api-key-2024";
        private String plateEndpoint = "/api/v1/plate/detect";
        private String behaviorEndpoint = "/api/v1/behavior/analyze";
        private String detectEndpoint = "/api/v1/object/detect";
        private int timeout = 15000;
    }

    @Data
    public static class BehaviorConfig {
        private boolean enabled = true;
        private String baseUrl = "http://127.0.0.1:8002";
        private String apiKey = "behavior-api-key-2024";
        private String analyzeEndpoint = "/api/v1/behavior/analyze";
        private int timeout = 30000;
    }
}
