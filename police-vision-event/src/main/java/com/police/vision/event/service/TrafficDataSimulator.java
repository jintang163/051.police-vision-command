package com.police.vision.event.service;

import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.dto.EventTrafficDataDTO;
import com.police.vision.common.util.MqUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event.traffic.simulator-enabled", havingValue = "true", matchIfMissing = false)
public class TrafficDataSimulator {

    private final MqUtil mqUtil;

    @Value("${event.traffic.simulator-event-id:1}")
    private Long simulatorEventId;

    @Value("${event.traffic.simulator-camera-count:5}")
    private int cameraCount;

    @Value("${event.traffic.simulator-base-pedestrian:50}")
    private int basePedestrian;

    @Value("${event.traffic.simulator-base-vehicle:30}")
    private int baseVehicle;

    @Value("${event.traffic.simulator-fluctuation:0.3}")
    private double fluctuation;

    private final Random random = new Random();

    @Scheduled(fixedRate = 2000)
    public void simulateTrafficData() {
        try {
            List<EventTrafficDataDTO> dataList = generateTrafficData();

            for (EventTrafficDataDTO data : dataList) {
                String destination = RocketMQConfig.buildDestination(
                        MqConstant.EVENT_TRAFFIC_DATA_TOPIC,
                        MqConstant.TAG_EVENT_TRAFFIC_DATA
                );
                mqUtil.sendOneway(destination, data);
            }

            log.debug("模拟生成交通数据：{}条, eventId={}", dataList.size(), simulatorEventId);
        } catch (Exception e) {
            log.error("模拟交通数据生成失败", e);
        }
    }

    private List<EventTrafficDataDTO> generateTrafficData() {
        List<EventTrafficDataDTO> list = new ArrayList<>();

        for (int i = 0; i < cameraCount; i++) {
            String cameraId = "CAM_" + String.format("%03d", i + 1);

            double lng = 116.397 + (random.nextDouble() - 0.5) * 0.02;
            double lat = 39.908 + (random.nextDouble() - 0.5) * 0.02;

            EventTrafficDataDTO pedestrianData = new EventTrafficDataDTO();
            pedestrianData.setCameraId(cameraId);
            pedestrianData.setCaptureTime(System.currentTimeMillis());
            pedestrianData.setType("pedestrian");
            pedestrianData.setCount(generateRandomCount(basePedestrian));
            pedestrianData.setLng(lng);
            pedestrianData.setLat(lat);
            pedestrianData.setEventId(simulatorEventId);
            list.add(pedestrianData);

            EventTrafficDataDTO vehicleData = new EventTrafficDataDTO();
            vehicleData.setCameraId(cameraId);
            vehicleData.setCaptureTime(System.currentTimeMillis());
            vehicleData.setType("vehicle");
            vehicleData.setCount(generateRandomCount(baseVehicle));
            vehicleData.setLng(lng);
            vehicleData.setLat(lat);
            vehicleData.setEventId(simulatorEventId);
            list.add(vehicleData);
        }

        return list;
    }

    private Long generateRandomCount(int base) {
        double factor = 1 + (random.nextDouble() - 0.5) * 2 * fluctuation;
        long count = Math.round(base * factor);
        return Math.max(1, count);
    }
}
