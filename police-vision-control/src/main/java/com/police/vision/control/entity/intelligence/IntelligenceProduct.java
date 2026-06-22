package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("intelligence_product")
public class IntelligenceProduct extends BaseEntity {

    private String productId;

    private String productNo;

    private String productType;

    private String productTypeName;

    private String title;

    private String summary;

    private String content;

    private String markdownContent;

    private LocalDate reportDate;

    private LocalDate reportStartDate;

    private LocalDate reportEndDate;

    private Integer alarmCount;

    private Integer caseCount;

    private Integer personCount;

    private Integer vehicleCount;

    private Integer opinionCount;

    private String hotspots;

    private String trends;

    private String suggestions;

    private String modelId;

    private String modelName;

    private String generateParams;

    private Integer status;

    private String statusName;

    private LocalDateTime generateTime;

    private Long generateSeconds;

    private String policeStationCode;

    private String policeStationName;
}
