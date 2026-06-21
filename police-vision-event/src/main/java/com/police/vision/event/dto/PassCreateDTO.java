package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;

@Data
@Validated
public class PassCreateDTO implements Serializable {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    @NotBlank(message = "持证人姓名不能为空")
    private String holderName;

    private String holderIdcard;

    private String holderPhone;

    private String passType;

    private String photoUrl;

    private Integer validDays;
}
