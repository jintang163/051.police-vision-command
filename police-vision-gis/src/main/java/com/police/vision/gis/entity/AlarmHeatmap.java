package com.police.vision.gis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.police.vision.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gis_alarm_heatmap")
public class AlarmHeatmap extends BaseEntity {

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer weight;

    private Integer alarmType;

    private LocalDateTime alarmTime;
}
