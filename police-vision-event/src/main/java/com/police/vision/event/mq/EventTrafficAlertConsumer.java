package com.police.vision.event.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.util.MqUtil;
import com.police.vision.event.service.TrafficAlertService;
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
        topic = MqConstant.EVENT_TRAFFIC_ALERT_TOPIC,
        consumerGroup = MqConstant.EVENT_TRAFFIC_ALERT_GROUP,
        selectorExpression = MqConstant.TAG_EVENT_TRAFFIC_ALERT
)
public class EventTrafficAlertConsumer implements RocketMQListener<String> {

    private final TrafficAlertService trafficAlertService;
    private final MqUtil mqUtil;

    @Override
    public void onMessage(String message) {
        try {
            log.info("收到交通告警消息：{}", message);
            Map<String, Object> msgMap = JSON.parseObject(message, new TypeReference<Map<String, Object>>() {});

            Object dataObj = msgMap.get("data");
            if (dataObj == null) {
                log.warn("告警消息data为空");
                return;
            }

            Map<String, Object> data = JSON.parseObject(JSON.toJSONString(dataObj), new TypeReference<Map<String, Object>>() {});

            Long eventId = getLong(data, "eventId");
            String alertType = getStr(data, "alertType");
            Integer alertLevel = getInt(data, "alertLevel");
            Long countValue = getLong(data, "countValue");
            Long thresholdValue = getLong(data, "thresholdValue");
            Double lng = getDouble(data, "lng");
            Double lat = getDouble(data, "lat");

            if (eventId == null || alertType == null || alertLevel == null) {
                log.warn("告警消息参数不完整：eventId={}, alertType={}, alertLevel={}", eventId, alertType, alertLevel);
                return;
            }

            trafficAlertService.createAlert(eventId, alertType, alertLevel, null, lng, lat, countValue, thresholdValue);

            log.info("交通告警处理完成：eventId={}, alertType={}, alertLevel={}", eventId, alertType, alertLevel);

        } catch (Exception e) {
            log.error("交通告警消费失败：{}", message, e);
        }
    }

    private String getStr(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private Long getLong(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Integer getInt(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private Double getDouble(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }
}
