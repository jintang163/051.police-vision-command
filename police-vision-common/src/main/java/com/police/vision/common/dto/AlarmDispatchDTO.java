package com.police.vision.common.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class AlarmDispatchDTO implements Serializable {

    @NotNull(message = "警情ID不能为空")
    private Long alarmId;

    @NotEmpty(message = "派单警力不能为空")
    private List<Long> policeIds;

    private Long commanderId;
    private String dispatchRemark;
    private Integer priority;

    private Boolean useSmartEta;

    private String recalcReason;
}
