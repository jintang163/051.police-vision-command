package com.police.vision.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlarmCreateDTO implements Serializable {

    @NotNull(message = "警情类型不能为空")
    private Integer alarmType;

    @NotBlank(message = "报警内容不能为空")
    private String content;

    @NotBlank(message = "报警地址不能为空")
    private String address;

    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;

    private String callerName;
    private String callerPhone;
    private String idCard;
    private LocalDateTime alarmTime = LocalDateTime.now();
    private String source = "110";
    private String remark;
}
