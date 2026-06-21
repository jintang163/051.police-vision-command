package com.police.vision.flink.sink;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.util.MqUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WebSocketPushSink<T> extends RichSinkFunction<T> {

    private final String messageType;
    private final String nameServer;

    public WebSocketPushSink(String nameServer, String messageType) {
        this.nameServer = nameServer;
        this.messageType = messageType;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        MqUtil.initProducer(nameServer, "police-flink-ws-producer");
    }

    @Override
    public void invoke(T value, Context context) throws Exception {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", messageType);
            message.put("data", value);
            message.put("timestamp", System.currentTimeMillis());

            String jsonMessage = JSON.toJSONString(message);

            MqUtil.sendOneway(MqConstant.WEBSOCKET_PUSH_TOPIC, jsonMessage);

            log.debug("推送到WebSocket队列成功：type={}", messageType);
        } catch (Exception e) {
            log.error("推送到WebSocket队列失败：{}", e.getMessage(), e);
        }
    }

    @Override
    public void close() throws Exception {
        MqUtil.shutdownProducer();
    }
}
