package com.police.vision.flink.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapPoint implements Serializable {

    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer weight;
    private String gridCode;
    private Long timestamp;
    private Integer dataType;

    public HeatmapPoint(BigDecimal longitude, BigDecimal latitude, Integer weight) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.weight = weight;
    }
}
