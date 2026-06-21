package com.police.vision.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;
    private String roleName;
    private String roleDesc;
    private Integer roleType;
    private Integer sortOrder;
    private Integer status;
    private String dataScope;
}
