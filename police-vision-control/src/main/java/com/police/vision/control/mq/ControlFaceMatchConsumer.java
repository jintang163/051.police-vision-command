package com.police.vision.control.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.control.service.TargetPersonAlertService;
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
        consumerGroup = "police-control-face-consumer-group",
        selectorExpression = MqConstant.TAG_FACE_MATCH + "||*"
)
public class ControlFaceMatchConsumer implements RocketMQListener<String> {

    private final TargetPersonAlertService targetPersonAlertService;

    @Override
    public void onMessage(String message) {
        try {
            Map<String, Object> msgMap = JSON.parseObject(message, new TypeReference<Map<String, Object>>() {});

            String personId = (String) msgMap.get("personId");
            String personName = (String) msgMap.get("personName");
            String cameraId = (String) msgMap.get("cameraId");
            String cameraName = (String) msgMap.get("cameraName");
            BigDecimal longitude = getBigDecimal(msgMap, "longitude");
            BigDecimal latitude = getBigDecimal(msgMap, "latitude");
            String snapshotUrl = (String) msgMap.get("snapshotUrl");
            String videoClipUrl = (String) msgMap.get("videoClipUrl");
            Float similarity = msgMap.get("similarity") != null ?
                    Float.parseFloat(msgMap.get("similarity").toString()) : null;

            targetPersonAlertService.processFaceMatchInternal(personId, personName, cameraId, cameraName,
                    longitude, latitude, snapshotUrl, videoClipUrl, similarity);

            log.debug("管控模块消费人脸匹配消息：personId={}, cameraId={}", personId, cameraId);

        } catch (Exception e) {
            log.error("管控模块消费人脸匹配消息失败：{}", message, e);
        }
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Double) return BigDecimal.valueOf((Double) v);
        if (v instanceof Integer) return BigDecimal.valueOf((Integer) v);
        if (v instanceof Long) return BigDecimal.valueOf((Long) v);
        if (v instanceof String) {
            try {
                return new BigDecimal((String) v);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
