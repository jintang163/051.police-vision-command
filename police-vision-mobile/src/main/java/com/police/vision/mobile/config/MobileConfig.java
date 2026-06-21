package com.police.vision.mobile.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mobile")
public class MobileConfig {

    private WebSocketConfig websocket = new WebSocketConfig();
    private GpsConfig gps = new GpsConfig();
    private NavigationConfig navigation = new NavigationConfig();

    @Data
    public static class WebSocketConfig {
        private String endpoint = "/ws/mobile";
        private int heartbeatInterval = 30000;
    }

    @Data
    public static class GpsConfig {
        private int reportInterval = 5000;
        private int retainHours = 24;
    }

    @Data
    public static class NavigationConfig {
        private String amapKey = "";
        private int defaultSpeed = 60;
    }
}
