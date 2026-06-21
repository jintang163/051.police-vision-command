package com.police.vision.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
        System.out.println("""
                ================================================
                 公安视图智能综合实战指挥平台 - 网关服务启动成功
                 端口: 8080
                 文档: http://localhost:8080/doc.html
                ================================================
                """);
    }
}
