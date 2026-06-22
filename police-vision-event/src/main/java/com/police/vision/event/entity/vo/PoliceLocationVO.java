package com.police.vision.event.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PoliceLocationVO {

    private Long policeId;

    private String policeNumber;

    private String policeNo;

    private String name;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer status;

    private String deviceId;

    private String deptName;

    private String deptId;

    private String phone;

    private String rankLevel;
}
