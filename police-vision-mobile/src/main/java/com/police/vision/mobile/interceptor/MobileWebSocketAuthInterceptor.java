package com.police.vision.mobile.interceptor;

import com.police.vision.common.entity.LoginUser;
import com.police.vision.common.util.JwtUtil;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MobileWebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            String token = extractToken(request);
            if (token == null) {
                log.warn("WebSocket连接缺少token");
                return false;
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                log.warn("WebSocket连接token无效");
                return false;
            }

            LoginUser loginUser = jwtUtil.getLoginUser(token);
            if (loginUser == null) {
                log.warn("WebSocket连接获取用户信息失败");
                return false;
            }

            attributes.put("loginUser", loginUser);
            attributes.put("token", token);
            attributes.put("userId", userId);

            log.info("移动警务端WebSocket连接认证成功：userId={}, name={}",
                    userId, loginUser.getName());
            return true;

        } catch (Exception e) {
            log.error("WebSocket握手认证失败：{}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }

        return request.getHeaders().getFirst("Authorization");
    }
}
