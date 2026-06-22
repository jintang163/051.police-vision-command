package com.police.vision.control.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.control")
public class ControlAiConfig {

    private LstmTrajectoryConfig lstm = new LstmTrajectoryConfig();

    @Data
    public static class LstmTrajectoryConfig {
        private boolean enabled = true;
        private String baseUrl = "http://127.0.0.1:8003";
        private String apiKey = "lstm-trajectory-api-key-2024";
        private String trainEndpoint = "/api/v1/trajectory/train";
        private String predictEndpoint = "/api/v1/trajectory/predict";
        private String evaluateEndpoint = "/api/v1/trajectory/evaluate";
        private String historyDataEndpoint = "/api/v1/trajectory/export-history";
        private int timeout = 30000;
        private int predictMinutes = 30;
        private int topK = 3;
        private int historyDays = 90;
        private String defaultModelVersion = "lstm-v2.1-90d";
        private int autoTrainDays = 7;
    }
}
