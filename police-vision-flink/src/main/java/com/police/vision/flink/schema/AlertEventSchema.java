package com.police.vision.flink.schema;

import com.alibaba.fastjson2.JSON;
import com.police.vision.flink.entity.FlinkAlertEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AlertEventSchema implements DeserializationSchema<FlinkAlertEvent>, SerializationSchema<FlinkAlertEvent> {

    @Override
    public FlinkAlertEvent deserialize(byte[] message) throws IOException {
        try {
            String json = new String(message, StandardCharsets.UTF_8);
            return JSON.parseObject(json, FlinkAlertEvent.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public byte[] serialize(FlinkAlertEvent element) {
        return JSON.toJSONString(element).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean isEndOfStream(FlinkAlertEvent nextElement) {
        return false;
    }

    @Override
    public TypeInformation<FlinkAlertEvent> getProducedType() {
        return TypeInformation.of(FlinkAlertEvent.class);
    }
}
