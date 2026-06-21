package com.police.vision.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("公安视图智能综合实战指挥平台API")
                        .version("1.0.0")
                        .description("基于Spring Cloud微服务架构的公安实战指挥平台，融合GIS地图、视频AI分析、大数据研判，实现警情智能接报派单、警力实时调度等功能。")
                        .contact(new Contact()
                                .name("技术支持")
                                .email("support@police-vision.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
