package com.police.vision.common.util;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.dto.AlertMessageDTO;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqUtil {

    private final RocketMQTemplate rocketMQTemplate;

    public void send(String destination, Object message) {
        try {
            String jsonMessage = JSON.toJSONString(message);
            rocketMQTemplate.convertAndSend(destination, jsonMessage);
            log.info("发送MQ消息成功：destination={}, message={}", destination, jsonMessage);
        } catch (Exception e) {
            log.error("发送MQ消息失败：destination={}, message={}", destination, message, e);
            throw new BusinessException(ResultCode.MQ_ERROR);
        }
    }

    public void sendAsync(String destination, Object message) {
        try {
            String jsonMessage = JSON.toJSONString(message);
            rocketMQTemplate.asyncSend(destination, jsonMessage, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    log.info("异步发送MQ消息成功：destination={}, msgId={}", destination, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("异步发送MQ消息失败：destination={}", destination, throwable);
                }
            });
        } catch (Exception e) {
            log.error("异步发送MQ消息异常：destination={}", destination, e);
        }
    }

    public void sendDelay(String destination, Object message, int delayLevel) {
        try {
            String jsonMessage = JSON.toJSONString(message);
            rocketMQTemplate.syncSend(destination,
                    MessageBuilder.withPayload(jsonMessage).build(),
                    3000, delayLevel);
            log.info("发送延迟MQ消息成功：destination={}, delayLevel={}", destination, delayLevel);
        } catch (Exception e) {
            log.error("发送延迟MQ消息失败：destination={}", destination, e);
            throw new BusinessException(ResultCode.MQ_ERROR);
        }
    }

    public void sendOneway(String destination, Object message) {
        try {
            String jsonMessage = JSON.toJSONString(message);
            rocketMQTemplate.sendOneWay(destination, jsonMessage);
        } catch (Exception e) {
            log.error("发送单向MQ消息失败：destination={}", destination, e);
        }
    }

    public void sendAlarmDispatch(Object message) {
        sendAsync(RocketMQConfig.alarmDispatchDestination(), message);
    }

    public void sendAlarmNotify(Object message) {
        sendAsync(RocketMQConfig.alarmNotifyDestination(), message);
    }

    public void sendVideoAlert(AlertMessageDTO alert) {
        sendAsync(RocketMQConfig.videoAlertDestination(), alert);
    }

    public void sendWebsocketScreenPush(Object message) {
        sendOneway(RocketMQConfig.websocketScreenDestination(), message);
    }

    public void sendGpsLocation(Object message) {
        sendOneway(RocketMQConfig.buildDestination(MqConstant.GIS_LOCATION_TOPIC, "location"), message);
    }

    public void sendFaceRecognition(Object message) {
        sendAsync(RocketMQConfig.buildDestination(MqConstant.FACE_RECOGNITION_TOPIC, MqConstant.TAG_FACE), message);
    }

    public void sendPlateRecognition(Object message) {
        sendAsync(RocketMQConfig.buildDestination(MqConstant.PLATE_RECOGNITION_TOPIC, MqConstant.TAG_PLATE), message);
    }

    public void sendBehaviorAnalysis(Object message) {
        sendAsync(RocketMQConfig.buildDestination(MqConstant.BEHAVIOR_ANALYSIS_TOPIC, MqConstant.TAG_BEHAVIOR), message);
    }

    public void sendRealTimeStat(Object message) {
        sendOneway(RocketMQConfig.buildDestination(MqConstant.REAL_TIME_STAT_TOPIC, "stat"), message);
    }

    public Map<String, Object> buildWebSocketMessage(String type, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());
        return message;
    }
}
