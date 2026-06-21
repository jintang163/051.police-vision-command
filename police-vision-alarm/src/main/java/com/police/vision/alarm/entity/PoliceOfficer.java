package com.police.vision.alarm.entity;

import lombok.Data;

import java.math.BigDecimal;

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
}
