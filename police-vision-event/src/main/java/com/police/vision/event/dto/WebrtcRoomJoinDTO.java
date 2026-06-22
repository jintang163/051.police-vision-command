package com.police.vision.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WebrtcRoomJoinDTO {

    @NotNull(message = "事件ID不能为空")
    private Long eventId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String userName;

    private String userRole;

    private Boolean enableVideo;

    private Boolean enableAudio;
}
