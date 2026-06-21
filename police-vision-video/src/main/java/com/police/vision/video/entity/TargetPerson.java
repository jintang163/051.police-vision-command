package com.police.vision.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("target_person")
public class TargetPerson extends BaseEntity {

    private String personId;

    private String personName;

    private String idCardNo;

    private String faceFeature;

    private Integer controlLevel;

    private Integer status;

    private String remark;
}
