package com.police.vision.mobile.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.mobile.service.PoliceMobilePushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.CONTROL_TOPIC,
        consumerGroup = "police-mobile-prediction-alert-consumer-group",
        selectorExpression = MqConstant.TAG_PREDICTION_ALERT
)
public class PredictionAlertMobileConsumer implements RocketMQListener<String> {

    private final PoliceMobilePushService policeMobilePushService;

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> msgMap = JSON.parseObject(message, new TypeReference<Map<String, Object>>() {});
            String type = (String) msgMap.get("type");

            if ("PREDICTION_ALERT".equals(type)) {
                Object data = msgMap.get("data");
                if (data instanceof Map) {
                    Map<String, Object> alertData = (Map<String, Object>) data;
                    processPredictionAlert(alertData);
                }
            }
        } catch (Exception e) {
            log.error("消费预测预警消息失败：{}", message, e);
        }
    }

    private void processPredictionAlert(Map<String, Object> alertData) {
        try {
            String alertId = (String) alertData.get("alertId");
            String alertNo = (String) alertData.get("alertNo");
            String alertType = (String) alertData.get("alertType");
            String alertTypeName = (String) alertData.get("alertTypeName");
            Integer alertLevel = getIntegerValue(alertData, "alertLevel");
            String personId = (String) alertData.get("personId");
            String personName = (String) alertData.get("personName");
            BigDecimal longitude = getBigDecimalValue(alertData, "longitude");
            BigDecimal latitude = getBigDecimalValue(alertData, "latitude");
            String locationDesc = (String) alertData.get("locationDesc");
            Double probability = getDoubleValue(alertData, "probability");
            String predictTime = (String) alertData.get("predictTime");
            String triggerReason = (String) alertData.get("triggerReason");
            String policeStationCode = (String) alertData.get("policeStationCode");

            String title = buildAlertTitle(alertTypeName, alertLevel);
            String content = buildAlertContent(personName, probability, triggerReason, locationDesc, predictTime);

            boolean pushed = policeMobilePushService.pushPredictionAlertToPolice(
                    alertId, alertNo, alertType, alertLevel, title, content,
                    personId, personName, longitude, latitude, locationDesc,
                    probability, predictTime, triggerReason, policeStationCode
            );

            if (pushed) {
                log.info("预测预警已推送至移动端：alertId={}, alertType={}, person={}",
                        alertId, alertType, personName);
            } else {
                log.warn("预测预警移动端推送失败或无在线警员：alertId={}", alertId);
            }
        } catch (Exception e) {
            log.error("处理预测预警推送失败：{}", JSON.toJSONString(alertData), e);
        }
    }

    private String buildAlertTitle(String alertTypeName, Integer alertLevel) {
        String levelText = "低";
        if (alertLevel != null) {
            if (alertLevel >= 4) levelText = "极高";
            else if (alertLevel == 3) levelText = "高";
            else if (alertLevel == 2) levelText = "中";
        }
        return "【" + levelText + "风险】" + alertTypeName;
    }

    private String buildAlertContent(String personName, Double probability,
                                     String triggerReason, String locationDesc, String predictTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("人员：").append(personName != null ? personName : "未知");
        if (probability != null) {
            sb.append(" · 预测概率：").append(String.format("%.0f%%", probability * 100));
        }
        sb.append("\n位置：").append(locationDesc != null ? locationDesc : "未知位置");
        if (triggerReason != null) {
            sb.append("\n原因：").append(triggerReason);
        }
        if (predictTime != null) {
            sb.append("\n时间：未来30分钟内");
        }
        sb.append("\n请辖区民警关注并提前部署");
        return sb.toString();
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

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Double) return BigDecimal.valueOf((Double) value);
        if (value instanceof Long) return BigDecimal.valueOf((Long) value);
        if (value instanceof Integer) return BigDecimal.valueOf((Integer) value);
        if (value instanceof String) {
            try { return new BigDecimal((String) value); } catch (Exception e) { return null; }
        }
        return null;
    }
}
