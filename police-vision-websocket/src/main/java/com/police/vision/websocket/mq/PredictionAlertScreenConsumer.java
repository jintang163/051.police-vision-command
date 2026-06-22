package com.police.vision.websocket.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.websocket.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.CONTROL_TOPIC,
        consumerGroup = "police-websocket-prediction-alert-consumer-group",
        selectorExpression = MqConstant.TAG_PREDICTION_ALERT
)
public class PredictionAlertScreenConsumer implements RocketMQListener<String> {

    private final WebSocketService webSocketService;

    private static final String SCREEN_PUSH_TYPE = "prediction_alert";
    private static final String ALERT_LIST_UPDATE = "prediction_alert_list_update";
    private static final String STATS_UPDATE = "prediction_alert_stats_update";

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> msgMap = JSON.parseObject(message, new TypeReference<Map<String, Object>>() {});
            String type = (String) msgMap.get("type");

            if ("PREDICTION_ALERT".equals(type)) {
                Object data = msgMap.get("data");
                if (data instanceof Map) {
                    Map<String, Object> alertData = (Map<String, Object>) data;
                    pushToScreen(alertData);
                }
            }
        } catch (Exception e) {
            log.error("消费大屏预测预警消息失败：{}", message, e);
        }
    }

    private void pushToScreen(Map<String, Object> alertData) {
        try {
            String alertId = (String) alertData.get("alertId");
            String alertNo = (String) alertData.get("alertNo");
            String alertType = (String) alertData.get("alertType");
            String alertTypeName = (String) alertData.get("alertTypeName");
            Integer alertLevel = getIntegerValue(alertData, "alertLevel");
            String personId = (String) alertData.get("personId");
            String personName = (String) alertData.get("personName");
            String personType = (String) alertData.get("personType");
            Integer controlLevel = getIntegerValue(alertData, "controlLevel");
            Double longitude = getDoubleValue(alertData, "longitude");
            Double latitude = getDoubleValue(alertData, "latitude");
            String locationDesc = (String) alertData.get("locationDesc");
            Double probability = getDoubleValue(alertData, "probability");
            String predictTime = (String) alertData.get("predictTime");
            String triggerReason = (String) alertData.get("triggerReason");
            String sensitiveAreaName = (String) alertData.get("sensitiveAreaName");
            Integer crowdCount = getIntegerValue(alertData, "crowdCount");
            Integer targetPersonCount = getIntegerValue(alertData, "targetPersonCount");
            String policeStationCode = (String) alertData.get("policeStationCode");
            String policeStationName = (String) alertData.get("policeStationName");

            Map<String, Object> screenAlert = new HashMap<>();
            screenAlert.put("alertId", alertId);
            screenAlert.put("alertNo", alertNo);
            screenAlert.put("alertType", alertType);
            screenAlert.put("alertTypeName", alertTypeName);
            screenAlert.put("alertLevel", alertLevel);
            screenAlert.put("personId", personId);
            screenAlert.put("personName", personName);
            screenAlert.put("personType", personType);
            screenAlert.put("controlLevel", controlLevel);
            screenAlert.put("longitude", longitude);
            screenAlert.put("latitude", latitude);
            screenAlert.put("locationDesc", locationDesc);
            screenAlert.put("probability", probability);
            screenAlert.put("predictTime", predictTime);
            screenAlert.put("triggerReason", triggerReason);
            screenAlert.put("sensitiveAreaName", sensitiveAreaName);
            screenAlert.put("crowdCount", crowdCount);
            screenAlert.put("targetPersonCount", targetPersonCount);
            screenAlert.put("policeStationCode", policeStationCode);
            screenAlert.put("policeStationName", policeStationName);
            screenAlert.put("status", 0);
            screenAlert.put("statusName", "待处置");
            screenAlert.put("createTime", LocalDateTime.now().toString());
            screenAlert.put("timestamp", System.currentTimeMillis());

            Map<String, Object> pushMsg = new HashMap<>();
            pushMsg.put("type", SCREEN_PUSH_TYPE);
            pushMsg.put("data", screenAlert);
            pushMsg.put("timestamp", System.currentTimeMillis());

            webSocketService.broadcastToScreen(JSON.toJSONString(pushMsg));
            webSocketService.broadcastToMap(JSON.toJSONString(pushMsg));

            Map<String, Object> updateMsg = new HashMap<>();
            updateMsg.put("type", ALERT_LIST_UPDATE);
            updateMsg.put("data", Map.of("action", "ADD", "alert", screenAlert));
            webSocketService.broadcastToScreen(JSON.toJSONString(updateMsg));

            Map<String, Object> statsMsg = new HashMap<>();
            statsMsg.put("type", STATS_UPDATE);
            statsMsg.put("data", Map.of("increment", 1, "alertLevel", alertLevel));
            webSocketService.broadcastToScreen(JSON.toJSONString(statsMsg));

            log.info("预测预警已推送至大屏：alertId={}, alertType={}, level={}, person={}",
                    alertId, alertType, alertLevel, personName);

        } catch (Exception e) {
            log.error("推送预测预警到大屏失败：{}", JSON.toJSONString(alertData), e);
        }
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (Exception e) { return null; }
        }
        return null;
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble((String) value); } catch (Exception e) { return null; }
        }
        return null;
    }
}
