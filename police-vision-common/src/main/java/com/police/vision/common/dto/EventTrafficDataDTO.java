package com.police.vision.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventTrafficDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cameraId;

    private Long captureTime;

    private String type;

    private Long count;

    private Double lng;

    private Double lat;

    private Long eventId;
}
