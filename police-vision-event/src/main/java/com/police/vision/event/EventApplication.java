package com.police.vision.event;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.police.vision.event", "com.police.vision.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("com.police.vision.event.mapper")
public class EventApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventApplication.class, args);
        System.out.println("""
                ================================================
                 公安视图智能综合实战指挥平台 - 安保事件管理服务启动成功
                 端口: 8088
                 文档: http://localhost:8088/doc.html
                ================================================
                """);
    }
}
