package com.police.vision.event.mq;

import com.police.vision.common.constant.MqConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstant.WEBRTC_SIGNAL_TOPIC,
        consumerGroup = MqConstant.WEBRTC_SIGNAL_GROUP,
        messageModel = MessageModel.BROADCASTING,
        selectorExpression = MqConstant.TAG_WEBRTC_SIGNAL
)
public class WebrtcSignalConsumer implements RocketMQListener<Map<String, Object>> {

    @Override
    public void onMessage(Map<String, Object> message) {
        String signalType = (String) message.get("signalType");
        String roomId = (String) message.get("roomId");
        Object fromUserId = message.get("fromUserId");
        Object toUserId = message.get("toUserId");

        if (signalType == null) {
            String action = (String) message.get("action");
            if (action != null) {
                handleRoomEvent(message, roomId, action);
                return;
            }
            return;
        }

        switch (signalType) {
            case "offer":
                log.debug("【WebRTC信令】Offer SDP 转发：房间={}, {} -> {}", roomId, fromUserId, toUserId);
                break;
            case "answer":
                log.debug("【WebRTC信令】Answer SDP 转发：房间={}, {} -> {}", roomId, fromUserId, toUserId);
                break;
            case "ice_candidate":
                log.debug("【WebRTC信令】ICE Candidate 转发：房间={}, {} -> {}", roomId, fromUserId, toUserId);
                break;
            case "bye":
                log.info("【WebRTC信令】挂断：房间={}, 用户={}", roomId, fromUserId);
                break;
            default:
                log.debug("【WebRTC信令】其他类型={}，房间={}", signalType, roomId);
        }

        dispatchToClientWebSocket(message);
    }

    private void handleRoomEvent(Map<String, Object> message, String roomId, String action) {
        Map<String, Object> userInfo = (Map<String, Object>) message.get("userInfo");
        String userName = userInfo != null ? (String) userInfo.get("userName") : "未知用户";

        switch (action) {
            case "JOIN":
                log.info("【WebRTC-房间事件】用户加入：房间={}, 用户={}", roomId, userName);
                break;
            case "LEAVE":
                log.info("【WebRTC-房间事件】用户离开：房间={}, 用户={}", roomId, userName);
                break;
            case "STATUS_UPDATE":
                Boolean isMuted = (Boolean) userInfo.get("isMuted");
                Boolean isVideoOff = (Boolean) userInfo.get("isVideoOff");
                Boolean isHandRaised = (Boolean) userInfo.get("isHandRaised");
                log.debug("【WebRTC-状态变更】房间={}, 用户={}, 静音={}, 视频关闭={}, 举手={}",
                        roomId, userName, isMuted, isVideoOff, isHandRaised);
                break;
            default:
                break;
        }

        dispatchToClientWebSocket(message);
    }

    private void dispatchToClientWebSocket(Map<String, Object> message) {
        log.debug("通过WebSocket推送到前端视频会商组件");
    }
}
