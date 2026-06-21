package com.police.vision.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class LoginDTO implements Serializable {

    @NotBlank(message = "警号不能为空")
    private String policeNo;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String captcha;
    private String captchaKey;
}
