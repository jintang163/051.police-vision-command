package com.police.vision.alarm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alarm_handle_log")
public class AlarmHandleLog extends BaseEntity {

    private Long alarmId;

    private Integer operateType;

    private Long operatorId;

    private String operatorName;

    private String content;

    private LocalDateTime operateTime;
}
