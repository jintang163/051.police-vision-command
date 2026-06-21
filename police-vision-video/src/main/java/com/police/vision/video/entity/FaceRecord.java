package com.police.vision.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("face_record")
public class FaceRecord extends BaseEntity {

    private String recordId;

    private String cameraId;

    private String personId;

    private String personName;

    private BigDecimal similarity;

    private String snapshotUrl;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime detectTime;
}
