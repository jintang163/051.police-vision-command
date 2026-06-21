package com.police.vision.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AlarmStatusUpdateDTO implements Serializable {

    @NotNull(message = "警情ID不能为空")
    private Long alarmId;

    @NotNull(message = "状态不能为空")
    private Integer status;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private String remark;
    private String handleResult;
    private String[] imageUrls;
}
