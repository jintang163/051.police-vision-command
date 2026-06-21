package com.police.vision.flink.entity;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VehicleFollowResult implements Serializable {

    private String alertId;

    private String alertNo;

    private String crossingId;

    private String crossingName;

    private String roadDirection;

    private Integer vehicleCount;

    private List<String> plateNos;

    private List<String> vehicleTypes;

    private BigDecimal averageSpeed;

    private LocalDateTime windowStart;

    private LocalDateTime windowEnd;

    private Integer alertLevel;

    private String description;

    private Long timestamp;

    private BigDecimal longitude;

    private BigDecimal latitude;
}
