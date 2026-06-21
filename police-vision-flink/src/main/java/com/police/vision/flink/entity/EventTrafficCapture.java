package com.police.vision.flink.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventTrafficCapture implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cameraId;

    private Long captureTime;

    private String type;

    private Long count;

    private Double lng;

    private Double lat;
}
