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

    @Operation(summary = "发送WebRTC SDP Offer", description = "发起方发送SDP Offer建立连接")
    @PostMapping("/signal/offer")
    public Result<Map<String, Object>> sendOffer(@RequestBody @Valid WebrtcSignalDTO dto) {
        return Result.success(webrtcSignalService.sendOffer(dto));
    }

    @Operation(summary = "发送WebRTC SDP Answer", description = "接收方回应SDP Answer")
    @PostMapping("/signal/answer")
    public Result<Map<String, Object>> sendAnswer(@RequestBody @Valid WebrtcSignalDTO dto) {
        return Result.success(webrtcSignalService.sendAnswer(dto));
    }

    @Operation(summary = "发送WebRTC ICE候选", description = "交换ICE候选人信息完成NAT穿透")
    @PostMapping("/signal/ice")
    public Result<Map<String, Object>> sendIceCandidate(@RequestBody @Valid WebrtcSignalDTO dto) {
        return Result.success(webrtcSignalService.sendIceCandidate(dto));
    }

    @Operation(summary = "挂断WebRTC连接", description = "结束P2P连接或离开SFU房间")
    @PostMapping("/signal/hangup")
    public Result<Map<String, Object>> hangUp(@RequestBody @Valid WebrtcSignalDTO dto) {
        return Result.success(webrtcSignalService.hangUp(dto));
    }

    @Operation(summary = "获取推流地址信息", description = "获取当前用户的推流地址（SRS SFU模式）")
    @GetMapping("/stream/publisher/{roomId}/{userId}")
    public Result<Map<String, Object>> getPublisherInfo(
            @PathVariable String roomId,
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "main") String streamType) {
        return Result.success(webrtcSignalService.getPublisherInfo(roomId, userId, streamType));
    }

    @Operation(summary = "获取拉流地址信息", description = "获取指定参会人的拉流地址（SRS SFU模式）")
    @GetMapping("/stream/player/{roomId}/{targetUserId}")
    public Result<Map<String, Object>> getPlayerInfo(
            @PathVariable String roomId,
            @PathVariable String targetUserId,
            @RequestParam(required = false, defaultValue = "main") String streamType) {
        return Result.success(webrtcSignalService.getPlayerInfo(roomId, targetUserId, streamType));
    }

    @Operation(summary = "获取房间媒体服务信息", description = "获取SFU服务配置、在线人数、流列表等")
    @GetMapping("/room/{roomId}/media")
    public Result<Map<String, Object>> getRoomMediaInfo(@PathVariable String roomId) {
        return Result.success(webrtcSignalService.getRoomMediaInfo(roomId));
    }
}
