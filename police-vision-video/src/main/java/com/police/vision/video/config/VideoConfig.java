package com.police.vision.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "video")
public class VideoConfig {

    private FfmpegConfig ffmpeg = new FfmpegConfig();
    private RtspConfig rtsp = new RtspConfig();

    @Data
    public static class FfmpegConfig {
        private String path = "ffmpeg";
        private int defaultDuration = 20;
        private int clipBeforeSeconds = 10;
        private int clipAfterSeconds = 10;
    }

    @Data
    public static class RtspConfig {
        private String transport = "tcp";
        private int timeout = 10000;
        private int bufferSize = 1048576;
    }
}
