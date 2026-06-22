package com.police.vision.control.dto.intelligence;

import lombok.Data;

import java.io.Serializable;

@Data
public class PredictionDTO implements Serializable {

    private Integer predictHours;

    private Integer historyDays;

    private Integer gridSizeMeters;

    private String caseType;

    private String areaCode;
}
