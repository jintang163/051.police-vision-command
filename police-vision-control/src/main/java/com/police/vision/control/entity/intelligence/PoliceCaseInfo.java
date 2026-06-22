package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("police_case_info")
public class PoliceCaseInfo extends BaseEntity {

    private String caseId;

    private String caseNo;

    private String caseType;

    private String caseTypeName;

    private String caseSubType;

    private String caseLevel;

    private String caseLevelName;

    private String modusOperandi;

    private String caseKeywords;

    private String areaCode;

    private String areaName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String address;

    private LocalDateTime caseTime;

    private String weaponType;

    private String targetType;

    private String suspectIds;

    private String suspectNames;

    private String vehicleIds;

    private String gridCode;

    private Integer isSolved;

    private LocalDateTime solveTime;

    private String handlerId;

    private String handlerName;

    private String policeStationCode;

    private String policeStationName;

    private String description;

    private String reporterName;

    private String reporterPhone;

    private String reporterIdCard;

    private LocalDateTime reportTime;
}
