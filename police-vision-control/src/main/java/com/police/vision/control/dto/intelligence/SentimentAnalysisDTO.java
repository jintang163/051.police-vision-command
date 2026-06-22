package com.police.vision.control.dto.intelligence;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class SentimentAnalysisDTO implements Serializable {

    @NotBlank(message = "分析文本不能为空")
    private String text;

    private String language;

    private Boolean extractKeywords = true;
}
