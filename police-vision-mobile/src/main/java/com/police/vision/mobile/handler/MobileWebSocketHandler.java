package com.police.vision.mobile.handler;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.entity.GpsLocation;
import com.police.vision.common.entity.LoginUser;
import com.police.vision.common.enums.PoliceStatusEnum;
import com.police.vision.common.util.MqUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.mobile.service.DispatchMobileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MobileWebSocketHandler implements WebSocketHandler {

    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<Long, String> USER_SESSION_MAP = new ConcurrentHashMap<>();

    private final RedisUtil redisUtil;
    private final MqUtil mqUtil;
    private final DispatchMobileService dispatchMobileService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LoginUser loginUser = (LoginUser) session.getAttributes().get("loginUser");
        Long userId = (Long) session.getAttributes().get("userId");
        String token = (String) session.getAttributes().get("token");

        if (loginUser != null && userId != null) {
            String sessionId = session.getId();
            SESSIONS.put(sessionId, session);
            USER_SESSION_MAP.put(userId, sessionId);

            redisUtil.set(RedisConstant.WEBSOCKET_SESSION_PREFIX + sessionId,
                    userId.toString(), 1, TimeUnit.HOURS);
            redisUtil.set(RedisConstant.WEBSOCKET_USER_PREFIX + userId,
                    sessionId, 1, TimeUnit.HOURS);

            redisUtil.set(RedisConstant.POLICE_STATUS_PREFIX + userId,
                    PoliceStatusEnum.ONLINE.getCode(), 24, TimeUnit.HOURS);

            mqUtil.sendGpsLocation(buildLocationUpdate(userId, loginUser));

            log.info("移动警务端WebSocket连接建立：userId={}, name={}, sessionId={}",
                    userId, loginUser.getName(), sessionId);

            sendMessage(session, mqUtil.buildWebSocketMessage("connected",
                    Map.of("sessionId", sessionId, "userId", userId, "name", loginUser.getName())));

            dispatchMobileService.sendPendingDispatches(userId);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        LoginUser loginUser = (LoginUser) session.getAttributes().get("loginUser");
        Long userId = (Long) session.getAttributes().get("userId");
        String payload = message.getPayload().toString();

        try {
            Map<String, Object> msgMap = JSON.parseObject(payload, Map.class);
            String type = (String) msgMap.get("type");

            switch (type) {
                case "heartbeat" -> handleHeartbeat(session, userId);
                case "gps_report" -> handleGpsReport(userId, msgMap);
                case "dispatch_status" -> handleDispatchStatus(userId, msgMap);
                case "dispatch_accept" -> handleDispatchAccept(userId, msgMap);
                case "dispatch_arrive" -> handleDispatchArrive(userId, msgMap);
                case "dispatch_complete" -> handleDispatchComplete(userId, msgMap);
                default -> log.warn("未知的移动端消息类型：{}", type);
            }
        } catch (Exception e) {
            log.error("处理移动端WebSocket消息失败：{}", e.getMessage(), e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("移动端WebSocket传输错误：sessionId={}, error={}", session.getId(), exception.getMessage());
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        Long userId = (Long) session.getAttributes().get("userId");
        log.info("移动端WebSocket连接关闭：userId={}, sessionId={}, status={}",
                userId, session.getId(), closeStatus);
        removeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void handleHeartbeat(WebSocketSession session, Long userId) {
        sendMessage(session, mqUtil.buildWebSocketMessage("heartbeat_ack",
                Map.of("timestamp", System.currentTimeMillis())));

        if (userId != null) {
            redisUtil.expire(RedisConstant.POLICE_STATUS_PREFIX + userId, 24, TimeUnit.HOURS);
        }
    }

    private void handleGpsReport(Long userId, Map<String, Object> msgMap) {
        if (userId == null) return;

        try {
            BigDecimal longitude = new BigDecimal(msgMap.get("longitude").toString());
            BigDecimal latitude = new BigDecimal(msgMap.get("latitude").toString());
            Double speed = msgMap.get("speed") != null ?
                    Double.parseDouble(msgMap.get("speed").toString()) : null;
            Double direction = msgMap.get("direction") != null ?
                    Double.parseDouble(msgMap.get("direction").toString()) : null;

            GpsLocation location = new GpsLocation();
            location.setUserId(userId);
            location.setLongitude(longitude);
            location.setLatitude(latitude);
            location.setSpeed(speed);
            location.setDirection(direction);
            location.setReportTime(LocalDateTime.now());

            redisUtil.set(RedisConstant.POLICE_LOCATION_PREFIX + userId,
                    JSON.toJSONString(location), 24, TimeUnit.HOURS);

            mqUtil.sendGpsLocation(location);
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("police_location", location));

            log.debug("警员GPS位置上报：userId={}, lng={}, lat={}", userId, longitude, latitude);
        } catch (Exception e) {
            log.error("处理GPS上报失败：userId={}", userId, e);
        }
    }

    private void handleDispatchStatus(Long userId, Map<String, Object> msgMap) {
        Object dispatchId = msgMap.get("dispatchId");
        Object status = msgMap.get("status");
        log.info("警员更新派单状态：userId={}, dispatchId={}, status={}", userId, dispatchId, status);
    }

    private void handleDispatchAccept(Long userId, Map<String, Object> msgMap) {
        try {
            Long dispatchId = Long.parseLong(msgMap.get("dispatchId").toString());
            dispatchMobileService.acceptDispatch(userId, dispatchId);
        } catch (Exception e) {
            log.error("接受派单失败：userId={}", userId, e);
        }
    }

    private void handleDispatchArrive(Long userId, Map<String, Object> msgMap) {
        try {
            Long dispatchId = Long.parseLong(msgMap.get("dispatchId").toString());
            BigDecimal longitude = new BigDecimal(msgMap.get("longitude").toString());
            BigDecimal latitude = new BigDecimal(msgMap.get("latitude").toString());
            dispatchMobileService.arriveDispatch(userId, dispatchId, longitude, latitude);
        } catch (Exception e) {
            log.error("到达现场失败：userId={}", userId, e);
        }
    }

    private void handleDispatchComplete(Long userId, Map<String, Object> msgMap) {
        try {
            Long dispatchId = Long.parseLong(msgMap.get("dispatchId").toString());
            String result = (String) msgMap.get("result");
            String remark = (String) msgMap.get("remark");
            dispatchMobileService.completeDispatch(userId, dispatchId, result, remark);
        } catch (Exception e) {
            log.error("完成派单失败：userId={}", userId, e);
        }
    }

    private void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        SESSIONS.remove(sessionId);

        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            USER_SESSION_MAP.remove(userId);
            redisUtil.delete(RedisConstant.WEBSOCKET_SESSION_PREFIX + sessionId);
            redisUtil.delete(RedisConstant.WEBSOCKET_USER_PREFIX + userId);

            redisUtil.set(RedisConstant.POLICE_STATUS_PREFIX + userId,
                    PoliceStatusEnum.OFFLINE.getCode(), 24, TimeUnit.HOURS);

            GpsLocation location = buildLocationUpdate(userId, null);
            mqUtil.sendGpsLocation(location);
            mqUtil.sendWebsocketScreenPush(mqUtil.buildWebSocketMessage("police_location", location));
        }
    }

    private GpsLocation buildLocationUpdate(Long userId, LoginUser loginUser) {
        GpsLocation location = new GpsLocation();
        location.setUserId(userId);
        location.setReportTime(LocalDateTime.now());
        if (loginUser != null) {
            location.setUserName(loginUser.getName());
        }
        return location;
    }

    public void sendMessage(WebSocketSession session, Object message) {
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = JSON.toJSONString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("发送移动端WebSocket消息失败：sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
    }

    public void sendToPolice(Long policeId, Object message) {
        String sessionId = USER_SESSION_MAP.get(policeId);
        if (sessionId != null) {
            WebSocketSession session = SESSIONS.get(sessionId);
            sendMessage(session, message);
        }
    }

    public void pushNewDispatch(Long policeId, Object dispatchInfo) {
        sendToPolice(policeId, mqUtil.buildWebSocketMessage("new_dispatch", dispatchInfo));
    }

    public void pushDispatchStatusUpdate(Long policeId, Object statusInfo) {
        sendToPolice(policeId, mqUtil.buildWebSocketMessage("dispatch_status", statusInfo));
    }

    public int getOnlinePoliceCount() {
        return USER_SESSION_MAP.size();
    }

    public boolean isPoliceOnline(Long policeId) {
        return USER_SESSION_MAP.containsKey(policeId);
    }
}
