package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_post")
public class SecPost extends BaseEntity {

    @TableField("plan_id")
    private Long planId;

    @TableField("group_id")
    private Long groupId;

    @TableField("post_name")
    private String postName;

    @TableField("post_code")
    private String postCode;

    @TableField("lng")
    private Double lng;

    @TableField("lat")
    private Double lat;

    @TableField("police_id")
    private Long policeId;

    @TableField("police_name")
    private String policeName;

    @TableField("police_no")
    private String policeNo;

    @TableField("duty_content")
    private String dutyContent;
}
