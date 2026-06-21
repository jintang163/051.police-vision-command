package com.police.vision.common.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long userId;
    private String policeNo;
    private String name;
    private String phone;
    private Long deptId;
    private String deptName;
    private List<String> roles;
    private List<String> permissions;
    private String token;
    private Long expireTime;
}
