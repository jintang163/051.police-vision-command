package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crawler_task")
public class CrawlerTask extends BaseEntity {

    private String taskId;

    private String taskNo;

    private String taskName;

    private String siteName;

    private String siteUrl;

    private String entryUrls;

    private String allowDomains;

    private String urlPattern;

    private String contentSelector;

    private String titleSelector;

    private String authorSelector;

    private String publishTimeSelector;

    private String keywords;

    private String areaFilter;

    private Integer crawlDepth;

    private Integer threadCount;

    private Integer sleepMillis;

    private Integer enabled;

    private Integer taskStatus;

    private String taskStatusName;

    private LocalDateTime lastStartTime;

    private LocalDateTime lastEndTime;

    private Long lastDurationSeconds;

    private Integer lastCrawlCount;

    private Integer lastNewCount;

    private Integer totalCrawlCount;

    private String cronExpression;

    private String policeStationCode;

    private String policeStationName;
}
