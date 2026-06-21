package com.police.vision.gis.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.GpsLocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.GIS_LOCATION_TOPIC,
        consumerGroup = MqConstant.GIS_LOCATION_GROUP,
        selectorExpression = "*"
)
public class LocationConsumer implements RocketMQListener<String> {

    private final GisService gisService;

    @Override
    public void onMessage(String message) {
        try {
            log.info("接收到GPS位置更新消息：{}", message);
            GpsLocation gpsLocation = JSON.parseObject(message, GpsLocation.class);
            gisService.updatePoliceLocationFromGps(gpsLocation);
        } catch (Exception e) {
            log.error("处理GPS位置更新消息失败：message={}", message, e);
        }
    }
}
