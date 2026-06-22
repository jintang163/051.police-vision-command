package com.police.vision.control.dto.intelligence;

import lombok.Data;

import java.io.Serializable;

@Data
public class CallbackStatisticsDTO implements Serializable {

    private Integer days = 7;

    private String areaCode;

    private String deptCode;
}
