package com.police.vision.event.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sec_pass")
public class SecPass extends BaseEntity {

    @TableField("event_id")
    private Long eventId;

    @TableField("pass_no")
    private String passNo;

    @TableField("holder_name")
    private String holderName;

    @TableField("holder_idcard")
    private String holderIdcard;

    @TableField("holder_phone")
    private String holderPhone;

    @TableField("pass_type")
    private String passType;

    @TableField("photo_url")
    private String photoUrl;

    @TableField("qr_code")
    private String qrCode;

    @TableField("jwt_token")
    private String jwtToken;

    @TableField("issue_time")
    private LocalDateTime issueTime;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("status")
    private Integer status;

    @TableField("verify_count")
    private Integer verifyCount;
}
