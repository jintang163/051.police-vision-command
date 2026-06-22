package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("public_opinion")
public class PublicOpinion extends BaseEntity {

    private String opinionId;

    private String opinionNo;

    private String sourceSite;

    private String sourceSiteName;

    private String sourceUrl;

    private String title;

    private String content;

    private String author;

    private String authorId;

    private LocalDateTime publishTime;

    private Integer viewCount;

    private Integer likeCount;

    private Integer commentCount;

    private Integer shareCount;

    private Integer sentimentLabel;

    private String sentimentLabelName;

    private BigDecimal sentimentScore;

    private String sentimentKeywords;

    private String sentimentAnalysis;

    private String keywords;

    private String topics;

    private String areaCode;

    private String areaName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer alertLevel;

    private String alertLevelName;

    private Integer status;

    private String statusName;

    private String handleRemark;

    private Long handleOfficerId;

    private LocalDateTime handleTime;

    private String crawlerTaskId;
}
