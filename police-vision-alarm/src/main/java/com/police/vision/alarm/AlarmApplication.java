package com.police.vision.alarm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.police.vision.alarm", "com.police.vision.common"})
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.police.vision.alarm.mapper")
public class AlarmApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlarmApplication.class, args);
        System.out.println("""
                ================================================
                 公安视图智能综合实战指挥平台 - 警情接报派单服务启动成功
                 端口: 8083
                 文档: http://localhost:8083/doc.html
                ================================================
                """);
    }
}
