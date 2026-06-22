package com.police.vision.control.dto.intelligence;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ClusterAnalyzeDTO implements Serializable {

    private Integer timeWindowHours;

    private BigDecimal similarityThreshold;

    private Integer minClusterSize;

    private String caseType;

    private String areaCode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
