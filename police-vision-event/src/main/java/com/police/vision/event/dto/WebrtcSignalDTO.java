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

    private String toUserId;

    private Map<String, Object> data;

    private Long eventId;
}
