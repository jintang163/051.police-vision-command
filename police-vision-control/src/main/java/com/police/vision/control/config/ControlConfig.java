package com.police.vision.control.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "control")
public class ControlConfig {

    private FenceConfig fence = new FenceConfig();
    private VisitorConfig visitor = new VisitorConfig();
    private AggregationConfig aggregation = new AggregationConfig();
    private PersonnelConfig personnel = new PersonnelConfig();

    @Data
    public static class FenceConfig {
        private boolean enabled = true;
        private int checkInterval = 5000;
    }

    @Data
    public static class VisitorConfig {
        private boolean enabled = true;
    }

    @Data
    public static class AggregationConfig {
        private int areaSize = 3;
        private int windowMinutes = 10;
    }

    @Data
    public static class PersonnelConfig {
        private int portraitDays = 30;
    }
}
