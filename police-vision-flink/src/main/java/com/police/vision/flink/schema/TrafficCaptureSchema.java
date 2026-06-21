package com.police.vision.flink.schema;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.entity.TrafficCaptureData;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TrafficCaptureSchema implements DeserializationSchema<TrafficCaptureData> {

    @Override
    public TrafficCaptureData deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }
        try {
            String json = new String(message, StandardCharsets.UTF_8);
            return JSON.parseObject(json, TrafficCaptureData.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(TrafficCaptureData nextElement) {
        return false;
    }

    @Override
    public TypeInformation<TrafficCaptureData> getProducedType() {
        return TypeInformation.of(TrafficCaptureData.class);
    }
}
