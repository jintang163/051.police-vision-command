package com.police.vision.mobile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("police_status_log")
public class PoliceStatusLog extends BaseEntity {

    private Long policeId;

    private Long dispatchId;

    private Integer status;

    private String statusName;

    private String remark;

    private LocalDateTime operateTime;
}
