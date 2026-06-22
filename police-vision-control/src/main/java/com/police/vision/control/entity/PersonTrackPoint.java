package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("person_track_point")
public class PersonTrackPoint extends BaseEntity {

    private String trackId;

    private String personId;

    private String personName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal altitude;

    private BigDecimal speed;

    private BigDecimal direction;

    private Integer accuracy;

    private String sourceType;

    private String deviceId;

    private LocalDateTime gpsTime;

    private String locationType;

    private String locationDesc;

    private String extraData;
}
