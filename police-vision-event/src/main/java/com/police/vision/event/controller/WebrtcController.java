package com.police.vision.event.controller;

import com.police.vision.common.result.Result;
import com.police.vision.event.dto.WebrtcRoomJoinDTO;
import com.police.vision.event.dto.WebrtcSignalDTO;
import com.police.vision.event.service.WebrtcSignalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "WebRTC视频会商", description = "多方视频会商、信令转发、参会人管理")
@RestController
@RequestMapping("/emergency/webrtc")
@RequiredArgsConstructor
public class WebrtcController {

    private final WebrtcSignalService webrtcSignalService;

    @Operation(summary = "创建或加入视频会商房间", description = "事件级别的视频会商，Sentinel限流保护")
    @PostMapping("/room/join")
    public Result<Map<String, Object>> createOrJoinRoom(@RequestBody @Valid WebrtcRoomJoinDTO dto) {
        return Result.success(webrtcSignalService.createOrJoinRoom(dto));
    }

    @Operation(summary = "离开视频会商房间")
    @PostMapping("/room/leave")
    public Result<Void> leaveRoom(
            @RequestParam String roomId,
            @RequestParam Long userId,
            @RequestParam(required = false) String userName) {
        webrtcSignalService.leaveRoom(roomId, userId, userName);
        return Result.success();
    }

    @Operation(summary = "获取房间信息（含参会人列表）")
    @GetMapping("/room/{roomId}")
    public Result<Map<String, Object>> getRoomInfo(@PathVariable String roomId) {
        return Result.success(webrtcSignalService.getRoomInfo(roomId));
    }

    @Operation(summary = "获取所有进行中的视频会商")
    @GetMapping("/rooms/active")
    public Result<List<Map<String, Object>>> listActiveRooms() {
        return Result.success(webrtcSignalService.listActiveRooms());
    }

    @Operation(summary = "发送WebRTC信令", description = "通过RocketMQ广播转发SDP/ICE信令")
    @PostMapping("/signal/send")
    public Result<Void> sendSignal(@RequestBody @Valid WebrtcSignalDTO dto) {
        webrtcSignalService.sendSignal(dto);
        return Result.success();
    }

    @Operation(summary = "更新参会人状态（静音/视频/举手）")
    @PostMapping("/room/participant/status")
    public Result<Map<String, Object>> updateParticipantStatus(
            @RequestParam String roomId,
            @RequestParam Long userId,
            @RequestParam(required = false) Boolean enableAudio,
            @RequestParam(required = false) Boolean enableVideo,
            @RequestParam(required = false) Boolean isHandRaised) {
        return Result.success(webrtcSignalService.updateParticipantStatus(
                roomId, userId, enableAudio, enableVideo, isHandRaised));
    }
}
