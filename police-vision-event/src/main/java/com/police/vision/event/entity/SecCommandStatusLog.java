package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_command_status_log")
public class SecCommandStatusLog extends BaseEntity {

    @TableField("command_id")
    private Long commandId;

    @TableField("from_status")
    private Integer fromStatus;

    @TableField("to_status")
    private Integer toStatus;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("operator_dept")
    private String operatorDept;

    @TableField("operate_time")
    private LocalDateTime operateTime;

    @TableField("operate_remark")
    private String operateRemark;

    @TableField("extra_data")
    private String extraData;
}
