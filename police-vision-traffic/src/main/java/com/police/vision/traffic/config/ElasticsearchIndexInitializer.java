package com.police.vision.traffic.config;

import com.police.vision.traffic.service.VehicleTrackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private final VehicleTrackService vehicleTrackService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始初始化Elasticsearch车辆轨迹索引...");
        try {
            vehicleTrackService.createTrackIndex();
            log.info("Elasticsearch车辆轨迹索引初始化完成");
        } catch (Exception e) {
            log.error("Elasticsearch车辆轨迹索引初始化失败：{}", e.getMessage(), e);
        }
    }
}
