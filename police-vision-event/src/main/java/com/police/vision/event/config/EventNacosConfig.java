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

    @Value("${event.resources.default-radius:1000}")
    private Double defaultResourceRadius;

    @Value("${event.gis.service-name:police-vision-gis}")
    private String gisServiceName;

    @Value("${emergency.plan.default-resource-radius:500}")
    private Double emergencyDefaultResourceRadius;

    @Value("${emergency.plan.default-template:terrorism}")
    private String emergencyDefaultTemplate;

    @Value("${emergency.command.default-deadline-minutes:60}")
    private Integer emergencyCommandDefaultDeadlineMinutes;

    @Value("${emergency.command.retry-times:3}")
    private Integer emergencyCommandRetryTimes;

    @Value("${emergency.webrtc.max-participants:16}")
    private Integer webrtcMaxParticipants;

    @Value("${emergency.webrtc.room-expire-minutes:480}")
    private Integer webrtcRoomExpireMinutes;

    @Value("${emergency.sentinel.plan-start-qps:10}")
    private Integer sentinelPlanStartQps;

    @Value("${emergency.sentinel.command-dispatch-qps:50}")
    private Integer sentinelCommandDispatchQps;
}
