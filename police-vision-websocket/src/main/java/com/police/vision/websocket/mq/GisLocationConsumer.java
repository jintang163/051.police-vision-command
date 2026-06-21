package com.police.vision.websocket.mq;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.websocket.handler.ScreenWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.GIS_LOCATION_TOPIC,
        consumerGroup = MqConstant.GIS_LOCATION_GROUP,
        selectorExpression = "location"
)
public class GisLocationConsumer implements RocketMQListener<String> {

    private final ScreenWebSocketHandler screenWebSocketHandler;

    @Override
    public void onMessage(String message) {
        try {
            GpsLocation location = JSON.parseObject(message, GpsLocation.class);
            if (location != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("deviceId", location.getDeviceId());
                data.put("longitude", location.getLongitude());
                data.put("latitude", location.getLatitude());
                data.put("speed", location.getSpeed());
                data.put("direction", location.getDirection());
                data.put("timestamp", location.getTimestamp());

                screenWebSocketHandler.pushPoliceLocation(data);
                log.debug("推送警力位置更新：deviceId={}, lng={}, lat={}",
                        location.getDeviceId(), location.getLongitude(), location.getLatitude());
            }
        } catch (Exception e) {
            log.error("处理GIS位置消息失败：{}", e.getMessage());
        }
    }
}
