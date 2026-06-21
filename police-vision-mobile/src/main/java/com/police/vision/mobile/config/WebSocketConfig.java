package com.police.vision.mobile.config;

import com.police.vision.mobile.handler.MobileWebSocketHandler;
import com.police.vision.mobile.interceptor.MobileWebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MobileWebSocketHandler mobileWebSocketHandler;
    private final MobileWebSocketAuthInterceptor mobileWebSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mobileWebSocketHandler, "/ws/mobile")
                .addInterceptors(mobileWebSocketAuthInterceptor)
                .setAllowedOrigins("*");
    }
}
