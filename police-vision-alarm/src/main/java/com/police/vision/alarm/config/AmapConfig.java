package com.police.vision.alarm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "amap")
public class AmapConfig {

    private boolean enabled = true;

    private String key = "";

    private String secret = "";

    private String baseUrl = "https://restapi.amap.com";

    private String drivingRoutePath = "/v3/direction/driving";

    private String trafficStatusPath = "/v3/traffic/status/rectangle";

    private String trafficCirclePath = "/v3/traffic/status/circle";

    private int connectTimeout = 5000;

    private int socketTimeout = 10000;

    private int routeCacheMinutes = 1;

    private int trafficRefreshMinutes = 5;

    private int maxRetries = 3;

    private boolean mockEnabled = false;

    private String strategy = "10";

    private boolean requireExtension = true;

    private String extensions = "all";
}
