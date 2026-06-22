package com.police.vision.control.config.intelligence;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobConfig {

    private String adminAddresses = "http://127.0.0.1:8080/xxl-job-admin";

    private String accessToken = "default_token";

    private String executorAppname = "police-vision-control-executor";

    private String executorAddress;

    private String executorIp;

    private int executorPort = 9999;

    private String executorLogPath = "./logs/xxl-job/jobhandler";

    private int executorLogRetentionDays = 30;

    private boolean enabled = true;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        if (!enabled) {
            log.info(">>>>>>>>>>> xxl-job executor disabled");
            return null;
        }
        log.info(">>>>>>>>>>> xxl-job config init, appname={}, port={}", executorAppname, executorPort);
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(executorAppname);
        xxlJobSpringExecutor.setAddress(executorAddress);
        xxlJobSpringExecutor.setIp(executorIp);
        xxlJobSpringExecutor.setPort(executorPort);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(executorLogPath);
        xxlJobSpringExecutor.setLogRetentionDays(executorLogRetentionDays);
        return xxlJobSpringExecutor;
    }
}
