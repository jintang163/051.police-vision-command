package com.police.vision.control.dto.intelligence;

import lombok.Data;

import java.io.Serializable;

@Data
public class CallbackManualExecuteDTO implements Serializable {

    private String taskId;

    private Long operatorId;

    private String operatorName;
}
