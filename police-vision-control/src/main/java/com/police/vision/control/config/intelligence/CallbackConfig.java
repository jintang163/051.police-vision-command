package com.police.vision.control.config.intelligence;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "callback")
public class CallbackConfig {

    private String defaultTemplateId = "TPL001";

    private Integer maxRetryTimes = 3;

    private Integer retryIntervalMinutes = 30;

    private Integer workingHoursStart = 8;

    private Integer workingHoursEnd = 20;

    private Integer autoTransferSatisfactionThreshold = 3;

    private Integer autoTransferSentimentThreshold = 0;

    private Integer batchSize = 50;

    private Integer maxConcurrentCalls = 20;

    private Boolean saveRecording = true;

    private Boolean asrEnabled = true;
}
