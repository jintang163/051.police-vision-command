package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("geo_fence")
public class GeoFence extends BaseEntity {

    private String fenceId;

    private String fenceName;

    private String fenceType;

    private String fenceTypeName;

    private Integer fenceLevel;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    private BigDecimal radius;

    private String polygonPoints;

    private String policeStationCode;

    private String policeStationName;

    private String description;

    private Boolean enabled;

    private String fenceTags;
}
