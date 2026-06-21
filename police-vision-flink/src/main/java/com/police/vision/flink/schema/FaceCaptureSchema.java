package com.police.vision.flink.schema;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.police.vision.flink.entity.FaceCaptureEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

public class FaceCaptureSchema implements DeserializationSchema<FaceCaptureEvent> {

    private static final long serialVersionUID = 1L;

    @Override
    public FaceCaptureEvent deserialize(byte[] message) throws IOException {
        try {
            String json = new String(message, StandardCharsets.UTF_8);
            Map<String, Object> map = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
            FaceCaptureEvent event = new FaceCaptureEvent();
            event.setCaptureId(getStr(map, "captureId"));
            event.setPersonId(getStr(map, "personId"));
            event.setPersonName(getStr(map, "personName"));
            event.setPersonType(getStr(map, "personType"));
            event.setControlLevel(getInt(map, "controlLevel"));
            event.setIsTargetPerson(getBool(map, "isTargetPerson"));
            event.setCameraId(getStr(map, "cameraId"));
            event.setCameraName(getStr(map, "cameraName"));
            event.setLongitude(getBigDecimal(map, "longitude"));
            event.setLatitude(getBigDecimal(map, "latitude"));
            event.setGridCode(getStr(map, "gridCode"));
            event.setAreaCode(getStr(map, "areaCode"));
            event.setSimilarity(getFloat(map, "similarity"));

            Object timeObj = map.get("eventTime");
            if (timeObj instanceof String) {
                event.setEventTime(LocalDateTime.parse((String) timeObj));
            } else {
                event.setEventTime(LocalDateTime.now());
            }
            Object tsObj = map.get("timestamp");
            if (tsObj != null) {
                event.setTimestamp(Long.parseLong(tsObj.toString()));
            } else {
                event.setTimestamp(System.currentTimeMillis());
            }
            return event;
        } catch (Exception e) {
            System.err.println("解析FaceCaptureEvent失败: " + e.getMessage());
            FaceCaptureEvent fallback = new FaceCaptureEvent();
            fallback.setCaptureId("ERR-" + System.currentTimeMillis());
            fallback.setEventTime(LocalDateTime.now());
            fallback.setTimestamp(System.currentTimeMillis());
            fallback.setPersonId("UNKNOWN");
            fallback.setPersonName("UNKNOWN");
            fallback.setIsTargetPerson(false);
            fallback.setCameraId("UNKNOWN");
            fallback.setGridCode("UNKNOWN");
            return fallback;
        }
    }

    @Override
    public boolean isEndOfStream(FaceCaptureEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<FaceCaptureEvent> getProducedType() {
        return TypeInformation.of(FaceCaptureEvent.class);
    }

    private String getStr(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    private Integer getInt(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private Float getFloat(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof Float) return (Float) v;
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(v.toString()); } catch (Exception e) { return null; }
    }

    private Boolean getBool(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }

    private BigDecimal getBigDecimal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return BigDecimal.valueOf(((Number) v).doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }
}
