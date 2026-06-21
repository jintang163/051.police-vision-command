package com.police.vision.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;

@Data
@Validated
public class AreaQueryDTO implements Serializable {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    private Integer radius = 500;
}
