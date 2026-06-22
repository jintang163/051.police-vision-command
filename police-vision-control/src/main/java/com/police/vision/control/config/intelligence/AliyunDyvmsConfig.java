package com.police.vision.control.config.intelligence;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.dyvms")
public class AliyunDyvmsConfig {

    private String accessKeyId = "LTAIxxxxxxxxxx";

    private String accessKeySecret = "xxxxxxxxxx";

    private String endpoint = "dyvmsapi.aliyuncs.com";

    private String regionId = "cn-hangzhou";

    private String calledShowNumber = "010xxxxxx";

    private String ttsCode = "xiaoyun";

    private Integer speed = 0;

    private Integer volume = 0;

    private Integer pitchRate = 0;

    private Integer connectTimeout = 10;

    private Integer readTimeout = 60;

    private Integer pollingInterval = 30;

    private Integer maxPollingTimes = 60;

    private Integer defaultAutoCallbackDelayHours = 24;

    private String autoCallbackCron = "0 0/5 8-20 * * ?";

    private Boolean enabled = true;
}
