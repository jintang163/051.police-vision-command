package com.police.vision.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_police_user")
public class PoliceUser extends BaseEntity {

    private String policeNo;
    private String password;
    private String name;
    private String gender;
    private LocalDate birthDate;
    private String idCard;
    private String phone;
    private String email;
    private Long deptId;
    private String deptName;
    private String rank;
    private String position;
    private String avatar;
    private Integer status;
    private Integer policeType;
    private String deviceId;
    private String remark;
    private BigDecimal longitude;
    private BigDecimal latitude;
}
