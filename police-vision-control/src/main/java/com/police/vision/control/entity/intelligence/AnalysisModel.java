package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("analysis_model")
public class AnalysisModel extends BaseEntity {

    private String modelId;

    private String modelNo;

    private String modelName;

    private String modelCode;

    private String modelType;

    private String modelTypeName;

    private String description;

    private String category;

    private String algorithm;

    private String paramConfig;

    private String dataSource;

    private String cronExpression;

    private String jobHandler;

    private Integer enabled;

    private Integer executeStatus;

    private String executeStatusName;

    private LocalDateTime lastExecuteTime;

    private String lastExecuteResult;

    private Integer version;

    private String policeStationCode;

    private String policeStationName;
}
