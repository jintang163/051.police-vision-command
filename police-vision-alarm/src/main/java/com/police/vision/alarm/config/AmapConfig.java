package com.police.vision.alarm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "amap")
public class AmapConfig {

    private boolean enabled = true;

    private String apiKey = "";

    private String secretKey = "";

    private String baseUrl = "https://restapi.amap.com";

    private String drivingRoutePath = "/v3/direction/driving";

    private String trafficCirclePath = "/v3/traffic/status/circle";

    private int connectTimeout = 5000;

    private int readTimeout = 8000;

    private int trafficRefreshMinutes = 5;

    private int routeEtaCacheSeconds = 60;

    private boolean useMock = true;

    private String strategy = "10";

    private String extensions = "all";

    public String getKey() {
        return apiKey;
    }

    public String getSecret() {
        return secretKey;
    }

    public boolean isMockEnabled() {
        return useMock;
    }
}
