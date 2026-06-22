package com.police.vision.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class WebrtcSignalDTO {

    @NotBlank(message = "房间ID不能为空")
    private String roomId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String userName;

    @NotBlank(message = "信令类型不能为空")
    private String signalType;

    private String fromUserId;

    private String fromUserName;

    private String toUserId;

    private String toUserName;

    private Map<String, Object> data;

    private Long eventId;

    private String sdp;

    private String sdpType;

    private String candidate;

    private String sdpMid;

    private Integer sdpMLineIndex;

    private String transId;

    private Boolean enableAudio;

    private Boolean enableVideo;

    private Boolean isHandRaised;

    private String streamType;

    private String action;
}
