package com.police.vision.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.util.List;

@Data
@Validated
public class SecurityPlanCreateDTO implements Serializable {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    @NotBlank(message = "方案名称不能为空")
    private String planName;

    private String planType;

    @Valid
    private List<TaskGroupDTO> taskGroups;
}
