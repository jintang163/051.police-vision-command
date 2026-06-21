package com.police.vision.mobile.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.mobile.service.DispatchMobileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.WEBSOCKET_PUSH_TOPIC,
        consumerGroup = "police-mobile-dispatch-consumer-group",
        selectorExpression = "*"
)
public class DispatchMobileConsumer implements RocketMQListener<String> {

    private final DispatchMobileService dispatchMobileService;

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> msgMap = JSON.parseObject(message, new TypeReference<Map<String, Object>>() {});
            String type = (String) msgMap.get("type");

            if ("new_dispatch".equals(type)) {
                Object data = msgMap.get("data");
                if (data instanceof Map) {
                    Map<String, Object> dispatchData = (Map<String, Object>) data;

                    Long dispatchId = getLongValue(dispatchData, "dispatchId");
                    Long alarmId = getLongValue(dispatchData, "alarmId");
                    Long policeId = getLongValue(dispatchData, "policeId");
                    String dispatchNo = (String) dispatchData.get("dispatchNo");
                    String alarmNo = (String) dispatchData.get("alarmNo");
                    Integer priority = getIntegerValue(dispatchData, "priority");
                    String alarmContent = (String) dispatchData.get("alarmContent");
                    String address = (String) dispatchData.get("address");
                    BigDecimal longitude = getBigDecimalValue(dispatchData, "longitude");
                    BigDecimal latitude = getBigDecimalValue(dispatchData, "latitude");
                    String dispatchRemark = (String) dispatchData.get("dispatchRemark");

                    if (dispatchId != null && policeId != null) {
                        dispatchMobileService.createMobileDispatch(
                                dispatchId, alarmId, policeId,
                                dispatchNo, alarmNo, priority,
                                alarmContent, address,
                                longitude, latitude,
                                dispatchRemark
                        );
                        log.info("移动端消费派单消息成功：dispatchId={}, policeId={}", dispatchId, policeId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("消费派单消息失败：{}", message, e);
        }
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Double) return BigDecimal.valueOf((Double) value);
        if (value instanceof Integer) return BigDecimal.valueOf((Integer) value);
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
