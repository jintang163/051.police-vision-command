package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("case_cluster")
public class CaseCluster extends BaseEntity {

    private String clusterId;

    private String clusterNo;

    private String clusterName;

    private String modusOperandi;

    private String modusKeywords;

    private String caseType;

    private String caseTypeName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer caseCount;

    private String caseIds;

    private String caseNos;

    private String areaCode;

    private String areaName;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    private BigDecimal radiusMeters;

    private String suspectIds;

    private String vehicleIds;

    private BigDecimal similarityScore;

    private String clusterFeatures;

    private Integer alertLevel;

    private String alertLevelName;

    private Integer status;

    private String statusName;

    private String investigationSuggestion;

    private String handleRemark;

    private Long handleOfficerId;

    private LocalDateTime handleTime;

    private String analysisModelId;

    private String analysisModelName;
}
