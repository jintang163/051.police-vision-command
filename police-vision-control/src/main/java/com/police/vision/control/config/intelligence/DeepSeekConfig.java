package com.police.vision.control.config.intelligence;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {

    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

    private String apiKey = "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    private String model = "deepseek-chat";

    private int maxTokens = 4096;

    private double temperature = 0.7;

    private double topP = 0.9;

    private int connectTimeout = 30;

    private int readTimeout = 120;

    private int retryCount = 3;

    private boolean enabled = true;
}
