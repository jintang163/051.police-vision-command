package com.police.vision.mobile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mobile_dispatch_record")
public class MobileDispatchRecord extends BaseEntity {

    private Long dispatchId;

    private Long alarmId;

    private Long policeId;

    private String dispatchNo;

    private String alarmNo;

    private Integer priority;

    private String alarmContent;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String dispatchRemark;

    private Integer dispatchStatus;

    private LocalDateTime dispatchTime;

    private LocalDateTime responseTime;

    private LocalDateTime arriveTime;

    private LocalDateTime finishTime;

    private BigDecimal arriveLongitude;

    private BigDecimal arriveLatitude;

    private String handleResult;

    private String handleRemark;
}
