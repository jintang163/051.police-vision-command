package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;

@Data
@Validated
public class PassVerifyDTO implements Serializable {

    @NotBlank(message = "JWT令牌不能为空")
    private String jwtToken;

    private String qrCode;
}
