package com.police.vision.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.police.vision.auth", "com.police.vision.common"})
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.police.vision.auth.mapper")
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
        System.out.println("""
                ================================================
                 公安视图智能综合实战指挥平台 - 认证授权服务启动成功
                 端口: 8081
                 文档: http://localhost:8081/doc.html
                ================================================
                """);
    }
}
