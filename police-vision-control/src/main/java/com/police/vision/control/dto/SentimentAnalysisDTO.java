package com.police.vision.control.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;

@Data
@Validated
public class SentimentAnalysisDTO implements Serializable {

    @NotBlank(message = "文本内容不能为空")
    private String text;

    private String language;

    private String domain;
}
