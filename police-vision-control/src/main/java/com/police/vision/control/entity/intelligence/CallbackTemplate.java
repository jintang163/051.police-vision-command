package com.police.vision.control.entity.intelligence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("callback_template")
public class CallbackTemplate extends BaseEntity {

    private String templateId;

    private String templateNo;

    private String templateName;

    private Integer templateType;

    private String templateTypeName;

    private String ttsCode;

    private String ttsName;

    private String welcomeText;

    private String question1Timeliness;

    private String question2Attitude;

    private String question3Solving;

    private String extraQuestions;

    private String endThankText;

    private String transferHumanText;

    private String dissatisfactionFollowup;

    private String keywordsMap;

    private Integer priority;

    private Integer status;

    private String statusName;

    private Integer defaultFlag;

    private Integer interactionMode;

    private String interactionModeName;

    private String dialogFlow;
}
