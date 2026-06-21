package com.police.vision.alarm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dispatch_record")
public class DispatchRecord extends BaseEntity {

    private String dispatchNo;

    private Long alarmId;

    @TableField(exist = false)
    private List<Long> policeIds;

    private String policeIdsStr;

    private Long commanderId;

    private LocalDateTime dispatchTime;

    private LocalDateTime responseTime;

    private Integer dispatchStatus;

    private String dispatchRemark;

    private Integer priority;
}
