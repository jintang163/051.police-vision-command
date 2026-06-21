package com.police.vision.alarm.entity;

import com.police.vision.common.dto.OfficerEtaResultDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PoliceOfficer {

    private Long id;

    private String name;

    private String badgeNo;

    private Integer status;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String deptName;

    private String vehicleType;

    private Double distance;

    private OfficerEtaResultDTO etaResult;

    private Integer etaSeconds;

    private BigDecimal roadDistance;

    private Integer trafficLevel;

    private String routePolyline;

    private Integer dispatchRank;

    private Double dispatchScore;

    private String vehicleNo;

    private String equipmentIds;

    private LocalDateTime lastUpdateTime;
}
