package com.police.vision.gis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.police.vision.gis", "com.police.vision.common"})
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.police.vision.gis.mapper")
public class GisApplication {

    public static void main(String[] args) {
        SpringApplication.run(GisApplication.class, args);
        System.out.println("""
                ================================================
                 公安视图智能综合实战指挥平台 - GIS地图服务启动成功
                 端口: 8082
                 文档: http://localhost:8082/doc.html
                ================================================
                """);
    }
}
