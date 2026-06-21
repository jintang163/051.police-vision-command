package com.police.vision.event.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RouteCameraDTO implements Serializable {

    private Long cameraId;

    private String cameraName;

    private String cameraUrl;

    private Integer cameraIndex;

    private Integer playDuration;
}
