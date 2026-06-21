package com.police.vision.control.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.entity.AggregationAlert;
import com.police.vision.control.mapper.AggregationAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.CONTROL_TOPIC,
        consumerGroup = "police-control-aggregation-consumer-group",
        selectorExpression = MqConstant.TAG_AGGREGATION_ALERT
)
public class AggregationAlertConsumer implements RocketMQListener<String> {

    private final AggregationAlertMapper aggregationAlertMapper;
    private final MqUtil mqUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(String message) {
        try {
            Map<String, Object> msgMap = JSON.parseObject(message, new TypeReference<Map<String, Object>>() {});

            AggregationAlert alert = new AggregationAlert();
            alert.setId(SnowflakeIdUtil.nextId());
            alert.setAlertId(getStr(msgMap, "alertId", "AG" + SnowflakeIdUtil.nextId()));
            alert.setAlertNo(getStr(msgMap, "alertNo", "AG" + System.currentTimeMillis()));
            alert.setAreaCode(getStr(msgMap, "areaCode", ""));
            alert.setAreaName(getStr(msgMap, "areaName", alert.getAreaCode()));
            alert.setCenterLongitude(getBigDecimal(msgMap, "centerLongitude"));
            alert.setCenterLatitude(getBigDecimal(msgMap, "centerLatitude"));
            alert.setTotalPersonCount(getInt(msgMap, "totalPersonCount", 0));
            alert.setPersonIds(toListStr(msgMap.get("personIds")));
            alert.setPersonNames(toListStr(msgMap.get("personNames")));
            alert.setTargetPersonCount(getInt(msgMap, "targetPersonCount", 0));
            alert.setTargetPersonIds(toListStr(msgMap.get("targetPersonIds")));
            alert.setTargetPersonNames(toListStr(msgMap.get("targetPersonNames")));
            alert.setAlertLevel(getInt(msgMap, "alertLevel", 2));

            Object start = msgMap.get("startTime");
            if (start instanceof String) {
                try { alert.setStartTime(LocalDateTime.parse((String) start)); }
                catch (Exception e) { alert.setStartTime(LocalDateTime.now()); }
            } else { alert.setStartTime(LocalDateTime.now()); }

            Object end = msgMap.get("endTime");
            if (end instanceof String) {
                try { alert.setEndTime(LocalDateTime.parse((String) end)); }
                catch (Exception e) { alert.setEndTime(LocalDateTime.now()); }
            } else { alert.setEndTime(LocalDateTime.now()); }

            alert.setDurationSeconds(getInt(msgMap, "durationSeconds", 0));
            alert.setCameraIds(toListStr(msgMap.get("cameraIds")));
            alert.setStatus(0);
            alert.setStatusName("待处理");
            alert.setDescription(getStr(msgMap, "description", ""));
            alert.setPoliceStationCode(getStr(msgMap, "policeStationCode", null));
            alert.setPoliceStationName(getStr(msgMap, "policeStationName", null));

            aggregationAlertMapper.insert(alert);

            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("aggregation_alert", alert));
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("alert",
                    Map.of(
                            "alertId", alert.getAlertId(),
                            "alertType", 100,
                            "alertName", "异常聚集告警",
                            "alertLevel", alert.getAlertLevel(),
                            "description", alert.getDescription(),
                            "longitude", alert.getCenterLongitude(),
                            "latitude", alert.getCenterLatitude(),
                            "totalPersonCount", alert.getTotalPersonCount(),
                            "targetPersonCount", alert.getTargetPersonCount(),
                            "personNames", alert.getPersonNames(),
                            "targetPersonNames", alert.getTargetPersonNames(),
                            "areaCode", alert.getAreaCode(),
                            "areaName", alert.getAreaName(),
                            "alertTime", LocalDateTime.now().toString(),
                            "cameraIds", alert.getCameraIds()
                    )));

            log.warn("【聚集告警】入库完成：area={}, persons={}, targets={}, level={}",
                    alert.getAreaCode(), alert.getTotalPersonCount(),
                    alert.getTargetPersonCount(), alert.getAlertLevel());

        } catch (Exception e) {
            log.error("聚集告警入库失败：{}", message, e);
        }
    }

    private String getStr(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v == null ? def : v.toString();
    }
    private Integer getInt(Map<String, Object> m, String k, Integer def) {
        Object v = m.get(k);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }
    private BigDecimal getBigDecimal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }
    @SuppressWarnings("unchecked")
    private String toListStr(Object o) {
        if (o == null) return "";
        if (o instanceof List) {
            return ((List<Object>) o).stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return o.toString();
    }
}
