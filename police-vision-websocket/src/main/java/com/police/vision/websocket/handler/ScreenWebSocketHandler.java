package com.police.vision.websocket.handler;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.entity.LoginUser;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScreenWebSocketHandler implements WebSocketHandler {

    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> USER_SESSION_MAP = new ConcurrentHashMap<>();

    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser loginUser = (LoginUser) session.getAttributes().get("loginUser");
        String token = (String) session.getAttributes().get("token");

        if (loginUser != null) {
            String sessionId = session.getId();
            SESSIONS.put(sessionId, session);
            USER_SESSION_MAP.put(loginUser.getUserId().toString(), sessionId);

            redisUtil.set(RedisConstant.WEBSOCKET_SESSION_PREFIX + sessionId,
                    loginUser.getUserId().toString(), 1, TimeUnit.HOURS);
            redisUtil.set(RedisConstant.WEBSOCKET_USER_PREFIX + loginUser.getUserId(),
                    sessionId, 1, TimeUnit.HOURS);

            log.info("WebSocket连接建立成功：userId={}, name={}, sessionId={}",
                    loginUser.getUserId(), loginUser.getName(), sessionId);

            sendMessage(session, mqUtil.buildWebSocketMessage("connected",
                    Map.of("sessionId", sessionId, "userId", loginUser.getUserId())));
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        LoginUser loginUser = (LoginUser) session.getAttributes().get("loginUser");
        String payload = message.getPayload().toString();

        try {
            Map<String, Object> msgMap = JSON.parseObject(payload, Map.class);
            String type = (String) msgMap.get("type");

            if ("heartbeat".equals(type)) {
                sendMessage(session, mqUtil.buildWebSocketMessage("heartbeat_ack", null));
            } else if ("subscribe".equals(type)) {
                Object channels = msgMap.get("channels");
                if (loginUser != null) {
                    log.info("用户 {} 订阅频道: {}", loginUser.getUserId(), channels);
                }
            } else if ("unsubscribe".equals(type)) {
                Object channels = msgMap.get("channels");
                if (loginUser != null) {
                    log.info("用户 {} 取消订阅频道: {}", loginUser.getUserId(), channels);
                }
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败：{}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket传输错误：sessionId={}, error={}", session.getId(), exception.getMessage());
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.info("WebSocket连接关闭：sessionId={}, status={}", session.getId(), closeStatus);
        removeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        SESSIONS.remove(sessionId);

        LoginUser loginUser = (LoginUser) session.getAttributes().get("loginUser");
        if (loginUser != null) {
            USER_SESSION_MAP.remove(loginUser.getUserId().toString());
            redisUtil.delete(RedisConstant.WEBSOCKET_SESSION_PREFIX + sessionId);
            redisUtil.delete(RedisConstant.WEBSOCKET_USER_PREFIX + loginUser.getUserId());
        }
    }

    public void sendMessage(WebSocketSession session, Object message) {
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = JSON.toJSONString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("发送WebSocket消息失败：sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
    }

    public void sendToUser(Long userId, Object message) {
        String sessionId = USER_SESSION_MAP.get(userId.toString());
        if (sessionId != null) {
            WebSocketSession session = SESSIONS.get(sessionId);
            sendMessage(session, message);
        }
    }

    public void broadcast(Object message) {
        String jsonMessage = JSON.toJSONString(message);
        SESSIONS.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (IOException e) {
                    log.error("广播消息失败：sessionId={}", session.getId());
                }
            }
        });
        log.debug("WebSocket广播消息完成，在线人数：{}", SESSIONS.size());
    }

    public int getOnlineCount() {
        return SESSIONS.size();
    }

    public void pushPoliceLocation(Object data) {
        broadcast(mqUtil.buildWebSocketMessage("police_location", data));
        broadcast(mqUtil.buildWebSocketMessage("police", data));
    }

    public void pushNewAlarm(Object data) {
        broadcast(mqUtil.buildWebSocketMessage("new_alarm", data));
        broadcast(mqUtil.buildWebSocketMessage("alarm", data));
    }

    public void pushAlarmStatusUpdate(Object data) {
        broadcast(mqUtil.buildWebSocketMessage("alarm_status_update", data));
        broadcast(mqUtil.buildWebSocketMessage("alarm", data));
    }

    public void pushVideoAlert(Object data) {
        broadcast(mqUtil.buildWebSocketMessage("video_alert", data));
        broadcast(mqUtil.buildWebSocketMessage("alert", data));
    }

    public void pushRealTimeStats(Object data) {
        broadcast(mqUtil.buildWebSocketMessage("real_time_stats", data));
        broadcast(mqUtil.buildWebSocketMessage("stats", data));
    }

    public void pushDispatchOrder(Object data) {
        broadcast(mqUtil.buildWebSocketMessage("dispatch_order", data));
    }

    public void pushDispatchStatus(Object data) {
        broadcast(mqUtil.buildWebSocketMessage("dispatch_status", data));
    }

    public void pushToPolice(Long policeId, Object data) {
        sendToUser(policeId, data);
    }

    public void pushDispatchToPolice(Long policeId, Object data) {
        sendToUser(policeId, mqUtil.buildWebSocketMessage("new_dispatch", data));
    }
}
