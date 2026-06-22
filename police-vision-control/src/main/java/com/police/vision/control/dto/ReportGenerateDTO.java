package com.police.vision.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;

@Data
@Validated
public class ReportGenerateDTO implements Serializable {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    @NotBlank(message = "报告名称不能为空")
    private String reportName;
}
