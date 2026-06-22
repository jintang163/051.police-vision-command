package com.police.vision.event.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.fastjson2.JSON;
import com.police.vision.common.config.RocketMQConfig;
import com.police.vision.common.constant.MqConstant;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.event.config.EventNacosConfig;
import com.police.vision.event.config.SentinelConfig;
import com.police.vision.event.dto.WebrtcRoomJoinDTO;
import com.police.vision.event.dto.WebrtcSignalDTO;
import com.police.vision.event.entity.SecEvent;
import com.police.vision.event.mapper.SecEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebrtcSignalService {

    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;
    private final SecEventMapper eventMapper;
    private final EventNacosConfig nacosConfig;

    private static final String ROOM_PREFIX = "emergency:webrtc:room:";
    private static final String PARTICIPANT_PREFIX = "emergency:webrtc:participants:";

    @Value("${emergency.webrtc.redis-expire-minutes:480}")
    private int redisExpireMinutes;

    @SentinelResource(value = "emergency_webrtc_signal")
    public Map<String, Object> createOrJoinRoom(WebrtcRoomJoinDTO dto) {
        log.info("创建/加入视频会商房间，事件ID：{}，用户ID：{}", dto.getEventId(), dto.getUserId());

        SecEvent event = eventMapper.selectById(dto.getEventId());
        if (event == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "事件不存在");
        }

        String roomId = getOrCreateRoomId(dto.getEventId());
        String roomKey = ROOM_PREFIX + roomId;
        String participantsKey = PARTICIPANT_PREFIX + roomId;

        Map<String, Object> roomInfo = redisUtil.getObject(roomKey, Map.class);
        if (roomInfo == null) {
            roomInfo = new HashMap<>();
            roomInfo.put("roomId", roomId);
            roomInfo.put("eventId", dto.getEventId());
            roomInfo.put("eventName", event.getEventName());
            roomInfo.put("createTime", System.currentTimeMillis());
            roomInfo.put("creatorId", dto.getUserId());
            roomInfo.put("creatorName", dto.getUserName());
            roomInfo.put("maxParticipants", nacosConfig.getWebrtcMaxParticipants());
            roomInfo.put("status", "active");
        }

        Map<String, Object> participant = new HashMap<>();
        participant.put("userId", dto.getUserId());
        participant.put("userName", dto.getUserName());
        participant.put("userRole", dto.getUserRole() != null ? dto.getUserRole() : "participant");
        participant.put("enableVideo", dto.getEnableVideo() != null ? dto.getEnableVideo() : true);
        participant.put("enableAudio", dto.getEnableAudio() != null ? dto.getEnableAudio() : true);
        participant.put("joinTime", System.currentTimeMillis());
        participant.put("isMuted", false);
        participant.put("isVideoOff", false);
        participant.put("isHandRaised", false);

        Map<String, Map<String, Object>> participants = getParticipantsMap(participantsKey);
        int maxParticipants = nacosConfig.getWebrtcMaxParticipants();
        if (participants.size() >= maxParticipants && !participants.containsKey(String.valueOf(dto.getUserId()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "会议室人数已达上限（" + maxParticipants + "人），请稍后再试");
        }
        participants.put(String.valueOf(dto.getUserId()), participant);

        redisUtil.setObject(roomKey, roomInfo, redisExpireMinutes, TimeUnit.MINUTES);
        saveParticipantsMap(participantsKey, participants);

        Map<String, Object> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("roomInfo", roomInfo);
        result.put("participants", new ArrayList<>(participants.values()));
        result.put("selfInfo", participant);
        result.put("token", generateToken(roomId, dto.getUserId()));
        result.put("signalServerUrl", getSignalServerUrl());

        sendRoomUpdateMq(roomId, "JOIN", participant);
        log.info("用户加入视频会商成功，房间ID：{}，用户：{}，当前人数：{}", roomId, dto.getUserName(), participants.size());
        return result;
    }

    @SentinelResource(value = "emergency_webrtc_signal")
    public void leaveRoom(String roomId, Long userId, String userName) {
        log.info("用户离开视频会商，房间ID：{}，用户ID：{}", roomId, userId);

        String participantsKey = PARTICIPANT_PREFIX + roomId;
        Map<String, Map<String, Object>> participants = getParticipantsMap(participantsKey);

        String userKey = String.valueOf(userId);
        Map<String, Object> leaver = participants.remove(userKey);
        saveParticipantsMap(participantsKey, participants);

        if (leaver != null) {
            sendRoomUpdateMq(roomId, "LEAVE", leaver);
        }

        if (participants.isEmpty()) {
            String roomKey = ROOM_PREFIX + roomId;
            redisUtil.delete(roomKey);
            log.info("视频会商房间已清空，已销毁房间：{}", roomId);
        }

        log.info("用户离开视频会商成功，房间ID：{}，用户：{}，剩余人数：{}", roomId, userName, participants.size());
    }

    @SentinelResource(value = "emergency_webrtc_signal")
    public Map<String, Object> getRoomInfo(String roomId) {
        String roomKey = ROOM_PREFIX + roomId;
        Map<String, Object> roomInfo = redisUtil.getObject(roomKey, Map.class);
        if (roomInfo == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "视频会商房间不存在或已过期");
        }

        String participantsKey = PARTICIPANT_PREFIX + roomId;
        Map<String, Map<String, Object>> participants = getParticipantsMap(participantsKey);

        Map<String, Object> result = new HashMap<>();
        result.put("roomInfo", roomInfo);
        result.put("participants", new ArrayList<>(participants.values()));
        result.put("participantCount", participants.size());
        return result;
    }

    @SentinelResource(value = "emergency_webrtc_signal")
    public void sendSignal(WebrtcSignalDTO dto) {
        log.debug("转发WebRTC信令，房间：{}，类型：{}，发送者：{} -> 接收者：{}",
                dto.getRoomId(), dto.getSignalType(), dto.getFromUserId(), dto.getToUserId());

        String roomKey = ROOM_PREFIX + dto.getRoomId();
        if (!redisUtil.hasKey(roomKey)) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "视频会商房间不存在或已过期");
        }

        Map<String, Object> signalMessage = new HashMap<>();
        signalMessage.put("roomId", dto.getRoomId());
        signalMessage.put("eventId", dto.getEventId());
        signalMessage.put("userId", dto.getUserId());
        signalMessage.put("userName", dto.getUserName());
        signalMessage.put("signalType", dto.getSignalType());
        signalMessage.put("fromUserId", dto.getFromUserId());
        signalMessage.put("toUserId", dto.getToUserId());
        signalMessage.put("data", dto.getData());
        signalMessage.put("timestamp", System.currentTimeMillis());

        mqUtil.sendAsync(
                RocketMQConfig.buildDestination(MqConstant.WEBRTC_SIGNAL_TOPIC, MqConstant.TAG_WEBRTC_SIGNAL),
                signalMessage
        );

        mqUtil.sendBroadcast(
                RocketMQConfig.buildDestination(MqConstant.WEBRTC_SIGNAL_TOPIC, MqConstant.TAG_WEBRTC_SIGNAL),
                signalMessage
        );
    }

    @SentinelResource(value = "emergency_webrtc_signal")
    public Map<String, Object> updateParticipantStatus(String roomId, Long userId,
                                                        Boolean enableAudio, Boolean enableVideo,
                                                        Boolean isHandRaised) {
        String participantsKey = PARTICIPANT_PREFIX + roomId;
        Map<String, Map<String, Object>> participants = getParticipantsMap(participantsKey);

        String userKey = String.valueOf(userId);
        Map<String, Object> participant = participants.get(userKey);
        if (participant == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "用户不在该视频会商中");
        }

        if (enableAudio != null) {
            participant.put("enableAudio", enableAudio);
            participant.put("isMuted", !enableAudio);
        }
        if (enableVideo != null) {
            participant.put("enableVideo", enableVideo);
            participant.put("isVideoOff", !enableVideo);
        }
        if (isHandRaised != null) {
            participant.put("isHandRaised", isHandRaised);
        }
        participants.put(userKey, participant);
        saveParticipantsMap(participantsKey, participants);

        sendRoomUpdateMq(roomId, "STATUS_UPDATE", participant);
        return participant;
    }

    public List<Map<String, Object>> listActiveRooms() {
        Set<String> roomKeys = redisUtil.keys(ROOM_PREFIX + "*");
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (String key : roomKeys) {
            Map<String, Object> roomInfo = redisUtil.getObject(key, Map.class);
            if (roomInfo != null && "active".equals(roomInfo.get("status"))) {
                String roomId = (String) roomInfo.get("roomId");
                String participantsKey = PARTICIPANT_PREFIX + roomId;
                Map<String, Map<String, Object>> participants = getParticipantsMap(participantsKey);
                roomInfo.put("participantCount", participants.size());
                rooms.add(roomInfo);
            }
        }
        rooms.sort((a, b) -> Long.compare(
                (Long) b.get("createTime"),
                (Long) a.get("createTime")
        ));
        return rooms;
    }

    private String getOrCreateRoomId(Long eventId) {
        String roomCacheKey = ROOM_PREFIX + "event:" + eventId;
        String existingRoomId = redisUtil.getString(roomCacheKey);
        if (existingRoomId != null && redisUtil.hasKey(ROOM_PREFIX + existingRoomId)) {
            return existingRoomId;
        }
        String newRoomId = "EMERGENCY_" + eventId + "_" +
                Long.toHexString(System.currentTimeMillis()).toUpperCase();
        redisUtil.setString(roomCacheKey, newRoomId, redisExpireMinutes, TimeUnit.MINUTES);
        return newRoomId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getParticipantsMap(String key) {
        Map<String, Object> raw = redisUtil.getObject(key, Map.class);
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            result.put(entry.getKey(), (Map<String, Object>) entry.getValue());
        }
        return result;
    }

    private void saveParticipantsMap(String key, Map<String, Map<String, Object>> participants) {
        redisUtil.setObject(key, participants, redisExpireMinutes, TimeUnit.MINUTES);
    }

    private String generateToken(String roomId, Long userId) {
        return Base64.getEncoder().encodeToString(
                (roomId + ":" + userId + ":" + System.currentTimeMillis()).getBytes()
        );
    }

    private String getSignalServerUrl() {
        return "/ws/webrtc/signal";
    }

    private void sendRoomUpdateMq(String roomId, String action, Map<String, Object> userInfo) {
        Map<String, Object> message = new HashMap<>();
        message.put("roomId", roomId);
        message.put("action", action);
        message.put("userInfo", userInfo);
        message.put("timestamp", System.currentTimeMillis());

        mqUtil.sendBroadcast(
                RocketMQConfig.buildDestination(MqConstant.WEBRTC_SIGNAL_TOPIC, MqConstant.TAG_WEBRTC_SIGNAL),
                message
        );
    }
}
