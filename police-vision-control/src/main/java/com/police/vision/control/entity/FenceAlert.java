package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fence_alert")
public class FenceAlert extends BaseEntity {

    private String alertId;

    private String alertNo;

    private String personId;

    private String personName;

    private String personType;

    private Integer alertLevel;

    private String fenceId;

    private String fenceName;

    private String fenceType;

    private BigDecimal alertLongitude;

    private BigDecimal alertLatitude;

    private String cameraId;

    private String cameraName;

    private Integer alertType;

    private String alertTypeName;

    private LocalDateTime alertTime;

    private LocalDateTime leaveTime;

    private Integer status;

    private String statusName;

    private String snapshotUrl;

    private String videoClipUrl;

    private String description;

    private String handleRemark;

    private Long handleOfficerId;

    private LocalDateTime handleTime;

    private String policeStationCode;

    private String policeStationName;

    private Boolean visitorPushed;

    private LocalDateTime visitorPushTime;
}
