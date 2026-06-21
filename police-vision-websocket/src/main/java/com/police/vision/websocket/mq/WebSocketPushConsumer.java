package com.police.vision.websocket.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.websocket.handler.ScreenWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.WEBSOCKET_PUSH_TOPIC,
        consumerGroup = MqConstant.WEBSOCKET_PUSH_GROUP,
        selectorExpression = "*"
)
public class WebSocketPushConsumer implements RocketMQListener<String> {

    private final ScreenWebSocketHandler screenWebSocketHandler;

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> msgMap = JSON.parseObject(message, new TypeReference<Map<String, Object>>() {});
            String type = (String) msgMap.get("type");
            Object data = msgMap.get("data");

            if (type == null) {
                log.warn("WebSocket推送消息类型为空：{}", message);
                return;
            }

            switch (type) {
                case "police_location" -> screenWebSocketHandler.pushPoliceLocation(data);
                case "new_alarm" -> screenWebSocketHandler.pushNewAlarm(data);
                case "alarm_status_update" -> screenWebSocketHandler.pushAlarmStatusUpdate(data);
                case "video_alert" -> screenWebSocketHandler.pushVideoAlert(data);
                case "real_time_stats" -> screenWebSocketHandler.pushRealTimeStats(data);
                case "dispatch_order" -> screenWebSocketHandler.pushDispatchOrder(data);
                case "dispatch_status" -> screenWebSocketHandler.pushDispatchStatus(data);
                case "vehicle_follow" -> screenWebSocketHandler.pushVehicleFollowAlert(data);
                case "vehicle_night_active" -> screenWebSocketHandler.pushVehicleNightActiveAlert(data);
                case "vehicle_control" -> screenWebSocketHandler.pushVehicleControlAlert(data);
                case "new_dispatch" -> {
                    Object policeIdObj = msgMap.get("policeId");
                    if (policeIdObj != null) {
                        Long policeId = Long.valueOf(policeIdObj.toString());
                        screenWebSocketHandler.pushDispatchToPolice(policeId, data);
                    }
                }
                default -> log.debug("未知的WebSocket消息类型：{}", type);
            }
            log.debug("WebSocket消息推送完成：type={}", type);
        } catch (Exception e) {
            log.error("处理WebSocket推送消息失败：{}", e.getMessage(), e);
        }
    }
}
