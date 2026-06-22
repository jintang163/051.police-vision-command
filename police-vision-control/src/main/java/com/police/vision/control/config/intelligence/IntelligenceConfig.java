package com.police.vision.control.config.intelligence;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "intelligence")
public class IntelligenceConfig {

    private ReportConfig report = new ReportConfig();

    private CrawlerConfig crawler = new CrawlerConfig();

    private ClusterConfig cluster = new ClusterConfig();

    private PredictionConfig prediction = new PredictionConfig();

    @Data
    public static class ReportConfig {
        private String dailyCron = "0 0 6 * * ?";
        private String weeklyCron = "0 0 7 ? * MON";
        private String monthlyCron = "0 0 8 1 * ?";
        private int historyDays = 30;
        private boolean autoGenerate = true;
    }

    @Data
    public static class CrawlerConfig {
        private int defaultThreadCount = 5;
        private int defaultCrawlDepth = 3;
        private int defaultSleepMillis = 1000;
        private int defaultTimeoutSeconds = 30;
        private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        private boolean autoSentiment = true;
    }

    @Data
    public static class ClusterConfig {
        private double similarityThreshold = 0.75;
        private int timeWindowHours = 72;
        private int minClusterSize = 2;
        private int maxClusters = 100;
        private String defaultCron = "0 0 */4 * * ?";
    }

    @Data
    public static class PredictionConfig {
        private int predictHours = 24;
        private int historyDays = 90;
        private int gridSizeMeters = 500;
        private double riskThreshold = 0.6;
        private String defaultCron = "0 0 5 * * ?";
        private boolean sarimaAutoTune = true;
    }
}
