package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_training_job")
public class ModelTrainingJob extends BaseEntity {

    private String jobId;

    private String modelType;

    private String modelVersion;

    private String status;

    private String statusName;

    private LocalDate trainStartDate;

    private LocalDate trainEndDate;

    private Long trainSampleCount;

    private Long evalSampleCount;

    private String trainParams;

    private Double trainLoss;

    private Double evalMae;

    private Double evalRmse;

    private Double evalAccuracyTop1;

    private Double evalAccuracyTop3;

    private Double evalAccuracy30m;

    private Double evalAccuracy50m;

    private Double evalAccuracy100m;

    private String evalReport;

    private String modelPath;

    private Integer deployed;

    private LocalDateTime deployTime;

    private Double accuracyEstimate;

    private String triggerMode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationSeconds;

    private String errorMessage;

    private String createdBy;
}
