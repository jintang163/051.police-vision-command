package com.police.vision.event.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CameraPointVO {

    private Long id;

    private String deviceId;

    private String deviceNo;

    private String name;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer status;

    private String direction;

    private String brand;

    private String streamUrl;

    private String rtspUrl;

    private String address;

    private String groupId;

    private String groupName;
}
