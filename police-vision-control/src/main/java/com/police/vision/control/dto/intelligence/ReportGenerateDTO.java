package com.police.vision.control.dto.intelligence;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
public class ReportGenerateDTO implements Serializable {

    @NotBlank(message = "产品类型不能为空")
    private String productType;

    private LocalDate reportStartDate;

    private LocalDate reportEndDate;

    private String modelId;

    private String areaCode;

    private Boolean includeAlarm;

    private Boolean includeCase;

    private Boolean includePerson;

    private Boolean includeVehicle;

    private Boolean includeOpinion;
}
