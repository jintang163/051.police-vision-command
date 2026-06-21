package com.police.vision.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alert_record")
public class AlertRecord extends BaseEntity {

    private String alertId;

    private Integer alertType;

    private Integer alertLevel;

    private String cameraId;

    private String description;

    private String snapshotUrl;

    private String videoClipUrl;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime detectTime;

    private Integer processed;

    private Long processorId;

    private LocalDateTime processTime;

    private String processResult;
}
