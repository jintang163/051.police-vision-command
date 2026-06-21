package com.police.vision.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.police.vision.websocket", "com.police.vision.common"})
@EnableDiscoveryClient
public class WebSocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketApplication.class, args);
        System.out.println("""
                ================================================
                 公安视图智能综合实战指挥平台 - WebSocket推送服务启动成功
                 端口: 8085
                 WebSocket: ws://localhost:8085/ws/screen
                ================================================
                """);
    }
}
