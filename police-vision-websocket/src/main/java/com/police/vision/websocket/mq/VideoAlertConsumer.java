package com.police.vision.websocket.mq;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.websocket.handler.ScreenWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.VIDEO_ANALYSIS_TOPIC,
        consumerGroup = "police-websocket-video-alert-group",
        selectorExpression = MqConstant.TAG_ALARM
)
public class VideoAlertConsumer implements RocketMQListener<String> {

    private final ScreenWebSocketHandler screenWebSocketHandler;

    @Override
    public void onMessage(String message) {
        try {
            AlertMessageDTO alert = JSON.parseObject(message, AlertMessageDTO.class);
            if (alert != null) {
                screenWebSocketHandler.pushVideoAlert(alert);
                log.info("推送视频AI告警：type={}, level={}, cameraId={}",
                        alert.getAlertName(), alert.getAlertLevel(), alert.getCameraId());
            }
        } catch (Exception e) {
            log.error("处理视频告警消息失败：{}", e.getMessage());
        }
    }
}
