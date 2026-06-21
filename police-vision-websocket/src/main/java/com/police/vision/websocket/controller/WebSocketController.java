package com.police.vision.websocket.controller;

import com.police.vision.common.result.Result;
import com.police.vision.websocket.handler.ScreenWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "WebSocket管理", description = "WebSocket连接管理、消息推送")
@RestController
@RequestMapping("/ws")
@RequiredArgsConstructor
public class WebSocketController {

    private final ScreenWebSocketHandler screenWebSocketHandler;

    @Operation(summary = "获取在线连接数")
    @GetMapping("/online/count")
    public Result<Map<String, Object>> getOnlineCount() {
        Map<String, Object> result = new HashMap<>();
        result.put("onlineCount", screenWebSocketHandler.getOnlineCount());
        return Result.success(result);
    }

    @Operation(summary = "推送消息到指定用户")
    @PostMapping("/push/user/{userId}")
    public Result<Void> pushToUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> message) {
        String type = (String) message.get("type");
        Object data = message.get("data");
        if (type == null) {
            return Result.fail("消息类型不能为空");
        }
        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", type);
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());
        screenWebSocketHandler.sendToUser(userId, wsMessage);
        return Result.success();
    }

    @Operation(summary = "广播消息到所有在线用户")
    @PostMapping("/push/broadcast")
    public Result<Void> broadcast(@RequestBody Map<String, Object> message) {
        String type = (String) message.get("type");
        Object data = message.get("data");
        if (type == null) {
            return Result.fail("消息类型不能为空");
        }
        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", type);
        wsMessage.put("data", data);
        wsMessage.put("timestamp", System.currentTimeMillis());
        screenWebSocketHandler.broadcast(wsMessage);
        return Result.success();
    }

    @Operation(summary = "模拟推送警力位置")
    @PostMapping("/test/police-location")
    public Result<Void> testPushPoliceLocation(@RequestBody Map<String, Object> data) {
        screenWebSocketHandler.pushPoliceLocation(data);
        return Result.success();
    }

    @Operation(summary = "模拟推送新警情")
    @PostMapping("/test/new-alarm")
    public Result<Void> testPushNewAlarm(@RequestBody Map<String, Object> data) {
        screenWebSocketHandler.pushNewAlarm(data);
        return Result.success();
    }

    @Operation(summary = "模拟推送视频告警")
    @PostMapping("/test/video-alert")
    public Result<Void> testPushVideoAlert(@RequestBody Map<String, Object> data) {
        screenWebSocketHandler.pushVideoAlert(data);
        return Result.success();
    }
}
