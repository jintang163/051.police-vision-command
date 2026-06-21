package com.police.vision.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class VideoAnalyzeDTO implements Serializable {

    @NotBlank(message = "摄像头ID不能为空")
    private String cameraId;

    @NotBlank(message = "视频流地址不能为空")
    private String streamUrl;

    @NotNull(message = "分析类型不能为空")
    private Integer analyzeType;

    private LocalDateTime analyzeTime = LocalDateTime.now();
    private String taskId;
    private String frameUrl;
}
