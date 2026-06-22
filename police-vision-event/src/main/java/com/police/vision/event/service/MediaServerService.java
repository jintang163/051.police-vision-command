package com.police.vision.event.service;

import com.alibaba.fastjson2.JSON;
import com.police.vision.event.config.EventNacosConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServerService {

    private final EventNacosConfig nacosConfig;

    public Map<String, Object> getMediaServerInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("sfuType", nacosConfig.getSfuType());
        info.put("sfuHost", nacosConfig.getSfuHost());
        info.put("sfuWsPort", nacosConfig.getSfuWsPort());
        info.put("sfuHttpPort", nacosConfig.getSfuHttpPort());
        info.put("rtcAppName", nacosConfig.getRtcAppName());
        info.put("enableSfu", nacosConfig.getEnableSfu());
        info.put("maxBitrateKbps", nacosConfig.getMaxBitrateKbps());
        info.put("defaultResolution", nacosConfig.getDefaultResolution());
        info.put("wsSignalUrl", buildWsSignalUrl());
        info.put("httpApiUrl", buildHttpApiUrl());
        return info;
    }

    public String buildWsSignalUrl() {
        return "ws://" + nacosConfig.getSfuHost() + ":" + nacosConfig.getSfuWsPort() + "/rtc/v1/";
    }

    public String buildHttpApiUrl() {
        return "http://" + nacosConfig.getSfuHost() + ":" + nacosConfig.getSfuHttpPort();
    }

    public Map<String, Object> createOrGetRoom(String roomId, String eventId) {
        Map<String, Object> result = new HashMap<>();
        result.put("roomId", roomId);
        result.put("eventId", eventId);
        result.put("app", nacosConfig.getRtcAppName());
        result.put("stream", "room_" + roomId);
        result.put("sfuType", nacosConfig.getSfuType());
        result.put("wsUrl", buildWsSignalUrl());
        result.put("rtcUrl", buildRtcUrl(roomId));
        result.put("rtmpUrl", buildRtmpUrl(roomId));
        result.put("hlsUrl", buildHlsUrl(roomId));
        result.put("httpFlvUrl", buildHttpFlvUrl(roomId));

        if (nacosConfig.getEnableSfu()) {
            result.put("status", "connected");
            result.put("message", "已连接SFU媒体服务器：" + nacosConfig.getSfuType());
            try {
                boolean created = createSrsRoom(roomId, eventId);
                result.put("sfuCreated", created);
            } catch (Exception e) {
                log.warn("创建SFU房间失败，降级为P2P模式，房间ID：{}", roomId, e);
                result.put("status", "p2p_mode");
                result.put("warning", "SFU连接失败，使用P2P模式");
            }
        } else {
            result.put("status", "p2p_mode");
            result.put("message", "当前为P2P模式（未启用SFU媒体服务器）");
        }

        log.info("获取房间媒体信息，房间ID：{}，模式：{}", roomId, result.get("status"));
        return result;
    }

    public Map<String, Object> getPublisherInfo(String roomId, String userId, String streamType) {
        Map<String, Object> info = new HashMap<>();
        String streamKey = roomId + "_" + userId + "_" + (streamType != null ? streamType : "main");
        info.put("roomId", roomId);
        info.put("userId", userId);
        info.put("streamType", streamType != null ? streamType : "main");
        info.put("streamKey", streamKey);
        info.put("publishUrl", buildPublishUrl(roomId, streamKey));
        info.put("webrtcPublishUrl", buildWebrtcPublishUrl(roomId, streamKey));
        info.put("srtUrl", buildSrtUrl(roomId, streamKey));
        info.put("bitrateKbps", nacosConfig.getMaxBitrateKbps());
        info.put("resolution", nacosConfig.getDefaultResolution());
        info.put("sfuType", nacosConfig.getSfuType());
        info.put("enableSfu", nacosConfig.getEnableSfu());
        return info;
    }

    public Map<String, Object> getPlayerInfo(String roomId, String userId, String streamType) {
        Map<String, Object> info = new HashMap<>();
        String streamKey = roomId + "_" + userId + "_" + (streamType != null ? streamType : "main");
        info.put("roomId", roomId);
        info.put("userId", userId);
        info.put("streamType", streamType != null ? streamType : "main");
        info.put("streamKey", streamKey);
        info.put("webrtcPlayUrl", buildWebrtcPlayUrl(roomId, streamKey));
        info.put("httpFlvPlayUrl", buildHttpFlvPlayUrl(roomId, streamKey));
        info.put("wsFlvPlayUrl", buildWsFlvPlayUrl(roomId, streamKey));
        info.put("hlsPlayUrl", buildHlsPlayUrl(roomId, streamKey));
        info.put("rtmpPlayUrl", buildRtmpPlayUrl(roomId, streamKey));
        info.put("sfuType", nacosConfig.getSfuType());
        info.put("enableSfu", nacosConfig.getEnableSfu());
        return info;
    }

    public List<Map<String, Object>> listRoomStreams(String roomId) {
        List<Map<String, Object>> streams = new ArrayList<>();
        if (!nacosConfig.getEnableSfu()) {
            try {
                return querySrsStreams(roomId);
            } catch (Exception e) {
                    log.warn("查询SFU流列表失败，房间ID：{}", roomId, e);
            }
        }
        return streams;
    }

    public boolean kickUser(String roomId, String userId) {
        log.info("踢出用户，房间ID：{}，用户ID：{}", roomId, userId);
        if (nacosConfig.getEnableSfu()) {
            try {
                kickSrsStream(roomId, userId);
            } catch (Exception e) {
                    log.error("SFU踢出用户失败，房间ID：{}，用户ID：{}", roomId, userId, e);
                    return false;
                }
        }
        return true;
    }

    public boolean muteUserStream(String roomId, String userId, boolean muteAudio, boolean muteVideo) {
        log.info("控制用户流，房间ID：{}，用户ID：{}，静音：{}，禁视频：{}", roomId, userId, muteAudio, muteVideo);
        if (nacosConfig.getEnableSfu()) {
            try {
                controlSrsStream(roomId, userId, muteAudio, muteVideo);
            } catch (Exception e) {
                log.error("SFU控制流失败，房间ID：{}，用户ID：{}", roomId, userId, e);
                return false;
            }
        }
        return true;
    }

    public Map<String, Object> getRoomStats(String roomId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("roomId", roomId);
        stats.put("sfuType", nacosConfig.getSfuType());
        stats.put("enableSfu", nacosConfig.getEnableSfu());
        if (nacosConfig.getEnableSfu()) {
            try {
                stats.put("onlineCount", querySrsRoomOnline(roomId));
            } catch (Exception e) {
                    log.warn("查询SFU房间统计失败，房间ID：{}", roomId, e);
                }
            }
        return stats;
    }

    private boolean createSrsRoom(String roomId, String eventId) {
        log.info("在SRS上创建房间（模拟），房间ID：{}，事件ID：{}", roomId, eventId);
        return true;
    }

    private List<Map<String, Object>> querySrsStreams(String roomId) {
        log.debug("查询SRS流列表（模拟），房间ID：{}", roomId);
        return Collections.emptyList();
    }

    private void kickSrsStream(String roomId, String userId) {
        log.info("SRS踢出流（模拟），房间ID：{}，用户ID：{}", roomId, userId);
    }

    private void controlSrsStream(String roomId, String userId, boolean muteAudio, boolean muteVideo) {
        log.info("SRS控制流（模拟），房间ID：{}，用户ID：{}，音频：{}，视频：{}", roomId, userId, muteAudio, muteVideo);
    }

    private int querySrsRoomOnline(String roomId) {
        return 0;
    }

    private String buildPublishUrl(String roomId, String streamKey) {
        return "rtmp://" + nacosConfig.getSfuHost() + "/" + nacosConfig.getRtcAppName() + "/" + streamKey;
    }

    private String buildRtcUrl(String roomId) {
        return "webrtc://" + nacosConfig.getSfuHost() + "/" + nacosConfig.getRtcAppName() + "/room_" + roomId;
    }

    private String buildRtmpUrl(String roomId) {
        return "rtmp://" + nacosConfig.getSfuHost() + "/" + nacosConfig.getRtcAppName() + "/room_" + roomId;
    }

    private String buildHlsUrl(String roomId) {
        return "http://" + nacosConfig.getSfuHost() + ":" + nacosConfig.getSfuHttpPort() + "/" + nacosConfig.getRtcAppName() + "/room_" + roomId + ".m3u8";
    }

    private String buildHttpFlvUrl(String roomId) {
        return "http://" + nacosConfig.getSfuHost() + ":" + nacosConfig.getSfuHttpPort() + "/" + nacosConfig.getRtcAppName() + "/room_" + roomId + ".flv";
    }

    private String buildWebrtcPublishUrl(String roomId, String streamKey) {
        return "webrtc://" + nacosConfig.getSfuHost() + "/" + nacosConfig.getRtcAppName() + "/" + streamKey;
    }

    private String buildWebrtcPlayUrl(String roomId, String streamKey) {
        return "webrtc://" + nacosConfig.getSfuHost() + "/" + nacosConfig.getRtcAppName() + "/" + streamKey;
    }

    private String buildHttpFlvPlayUrl(String roomId, String streamKey) {
        return "http://" + nacosConfig.getSfuHost() + ":" + nacosConfig.getSfuHttpPort() + "/" + nacosConfig.getRtcAppName() + "/" + streamKey + ".flv";
    }

    private String buildWsFlvPlayUrl(String roomId, String streamKey) {
        return "ws://" + nacosConfig.getSfuHost() + ":" + nacosConfig.getSfuWsPort() + "/" + nacosConfig.getRtcAppName() + "/" + streamKey + ".flv";
    }

    private String buildHlsPlayUrl(String roomId, String streamKey) {
        return "http://" + nacosConfig.getSfuHost() + ":" + nacosConfig.getSfuHttpPort() + "/" + nacosConfig.getRtcAppName() + "/" + streamKey + "/hls.m3u8";
    }

    private String buildRtmpPlayUrl(String roomId, String streamKey) {
        return "rtmp://" + nacosConfig.getSfuHost() + "/" + nacosConfig.getRtcAppName() + "/" + streamKey;
    }

    private String buildSrtUrl(String roomId, String streamKey) {
        return "srt://" + nacosConfig.getSfuHost() + ":10080?streamid=#!/" + nacosConfig.getRtcAppName() + "/" + streamKey;
    }
}
