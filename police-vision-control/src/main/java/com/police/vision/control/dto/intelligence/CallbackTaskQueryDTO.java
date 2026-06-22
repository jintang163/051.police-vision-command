package com.police.vision.control.dto.intelligence;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CallbackTaskQueryDTO implements Serializable {

    private Integer taskStatus;

    private Integer sourceType;

    private Integer transferHumanFlag;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String areaCode;

    private String keyword;

    private String alertDeptCode;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
