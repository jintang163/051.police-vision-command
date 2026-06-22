package com.police.vision.control.dto.intelligence;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CallbackTaskCreateDTO implements Serializable {

    private Integer sourceType;

    private String sourceId;

    private String caseType;

    private String briefDescription;

    private String reporterName;

    private String reporterPhone;

    private LocalDateTime closeTime;

    private Long alertOfficerId;

    private String alertOfficerName;

    private String alertDeptCode;

    private String alertDeptName;

    private String areaCode;

    private String areaName;

    private String templateId;
}
