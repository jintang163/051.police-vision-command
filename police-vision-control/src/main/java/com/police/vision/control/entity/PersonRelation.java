package com.police.vision.control.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("person_relation")
public class PersonRelation extends BaseEntity {

    private String relationId;
    private String personId1;
    private String personId2;
    private String relationType;
    private String relationName;
    private String caseId;
    private String caseName;
    private String description;
    private Integer contactCount;
    private Integer strength;
    private LocalDate firstContactDate;
    private LocalDate lastContactDate;
    private Boolean syncedToNeo4j;
}
