package com.police.vision.websocket.interceptor;

import com.alibaba.fastjson2.JSON;
import com.police.vision.common.constant.RedisConstant;
import com.police.vision.common.entity.LoginUser;
import com.police.vision.common.util.JwtUtil;
import com.police.vision.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final RedisUtil redisUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token == null || token.isEmpty()) {
                String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && JwtUtil.validateToken(token)) {
                String userJson = redisUtil.get(RedisConstant.USER_PREFIX + token);
                if (userJson != null) {
                    LoginUser loginUser = JSON.parseObject(userJson, LoginUser.class);
                    attributes.put("loginUser", loginUser);
                    attributes.put("token", token);
                    log.info("WebSocket连接认证成功：userId={}, name={}", loginUser.getUserId(), loginUser.getName());
                    return true;
                }
            }
        }
        log.warn("WebSocket连接认证失败");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
