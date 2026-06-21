package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("target_person")
public class TargetPerson extends BaseEntity {

    private String personId;

    private String personName;

    private String idCardNo;

    private String faceFeature;

    private Integer controlLevel;

    private Integer status;

    private String remark;

    private String personType;

    private String personTypeName;

    private String gender;

    private Integer age;

    private String phone;

    private String avatarUrl;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String residentAddress;

    private String workAddress;

    private String policeStationCode;

    private String policeStationName;

    private LocalDate registerDate;

    private Integer caseCount;

    private Integer visitCount;

    private Integer alertCount;

    private Double riskScore;

    private String criminalTags;

    private String mentalLevel;

    private String appealCategory;

    @TableField(exist = false)
    private Object activityPattern;

    @TableField(exist = false)
    private Object abnormalStats;
}
