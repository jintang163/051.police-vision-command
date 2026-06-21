package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;

@Data
@Validated
public class PostDTO implements Serializable {

    @NotBlank(message = "岗位名称不能为空")
    private String postName;

    private String postCode;

    private Double lng;

    private Double lat;

    private Long policeId;

    private String policeName;

    private String policeNo;

    private String dutyContent;
}
