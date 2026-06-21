package com.police.vision.event.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@RefreshScope
public class EventNacosConfig {

    @Value("${event.traffic.default-pedestrian-threshold:5000}")
    private Long defaultPedestrianThreshold;

    @Value("${event.traffic.default-vehicle-threshold:2000}")
    private Long defaultVehicleThreshold;

    @Value("${event.traffic.default-window-seconds:300}")
    private Integer defaultWindowSeconds;

    @Value("${event.pass.default-valid-days:7}")
    private Integer defaultPassValidDays;

    @Value("${event.report.output-path:/data/reports/}")
    private String reportOutputPath;
}
