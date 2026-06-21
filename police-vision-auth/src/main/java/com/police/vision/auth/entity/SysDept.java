package com.police.vision.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private String deptCode;
    private String deptName;
    private Long parentId;
    private Integer deptType;
    private Integer sortOrder;
    private String leader;
    private String phone;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer status;
    private String remark;
}
