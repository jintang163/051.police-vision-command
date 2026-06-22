package com.police.vision.control.dto.intelligence;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CrawlTaskExecuteDTO implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    private List<String> entryUrls;

    private Integer threadCount;

    private Integer crawlDepth;

    private Integer sleepMillis;
}
