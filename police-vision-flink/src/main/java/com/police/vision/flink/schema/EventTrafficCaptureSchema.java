package com.police.vision.flink.schema;

import com.alibaba.fastjson2.JSON;
import com.police.vision.flink.entity.EventTrafficCapture;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class EventTrafficCaptureSchema implements DeserializationSchema<EventTrafficCapture> {

    private static final long serialVersionUID = 1L;

    @Override
    public EventTrafficCapture deserialize(byte[] message) throws IOException {
        if (message == null || message.length == 0) {
            return null;
        }
        try {
            String json = new String(message, StandardCharsets.UTF_8);
            return JSON.parseObject(json, EventTrafficCapture.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(EventTrafficCapture nextElement) {
        return false;
    }

    @Override
    public TypeInformation<EventTrafficCapture> getProducedType() {
        return TypeInformation.of(EventTrafficCapture.class);
    }
}
