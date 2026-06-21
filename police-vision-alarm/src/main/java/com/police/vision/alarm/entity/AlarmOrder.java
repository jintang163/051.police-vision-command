package com.police.vision.alarm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alarm_order")
public class AlarmOrder extends BaseEntity {

    private String alarmNo;

    private Integer alarmType;

    private Integer alarmStatus;

    private Integer priority;

    private String content;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String callerName;

    private String callerPhone;

    private String source;

    private LocalDateTime dispatchTime;

    private LocalDateTime arriveTime;

    private LocalDateTime finishTime;

    private String handleResult;

    private String remark;
}
