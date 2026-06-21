package com.police.vision.video.controller;

import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.service.CameraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "摄像头管理", description = "摄像头设备增删改查、状态管理、批量导入")
@RestController
@RequestMapping("/camera")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @Operation(summary = "分页查询摄像头列表")
    @GetMapping("/list")
    public Result<PageResult<CameraDevice>> getCameraList(
            PageParam param,
            @Parameter(description = "关键词（设备ID/名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：0离线 1在线") @RequestParam(required = false) Integer status,
            @Parameter(description = "所属区域") @RequestParam(required = false) String region) {
        return Result.success(cameraService.getPage(param, keyword, status, region));
    }

    @Operation(summary = "获取摄像头详情")
    @GetMapping("/{id}")
    public Result<CameraDevice> getCameraById(@PathVariable Long id) {
        return Result.success(cameraService.getById(id));
    }

    @Operation(summary = "根据设备ID查询")
    @GetMapping("/device/{deviceId}")
    public Result<CameraDevice> getCameraByDeviceId(@PathVariable String deviceId) {
        return Result.success(cameraService.getByDeviceId(deviceId));
    }

    @Operation(summary = "获取所有摄像头")
    @GetMapping("/all")
    public Result<List<CameraDevice>> getAllCameras() {
        return Result.success(cameraService.getAll());
    }

    @Operation(summary = "按状态查询摄像头")
    @GetMapping("/status/{status}")
    public Result<List<CameraDevice>> getCamerasByStatus(@PathVariable Integer status) {
        return Result.success(cameraService.getByStatus(status));
    }

    @Operation(summary = "按区域查询摄像头")
    @GetMapping("/region/{region}")
    public Result<List<CameraDevice>> getCamerasByRegion(@PathVariable String region) {
        return Result.success(cameraService.getByRegion(region));
    }

    @Operation(summary = "添加摄像头")
    @PostMapping
    public Result<Void> addCamera(@RequestBody CameraDevice camera) {
        cameraService.add(camera);
        return Result.success();
    }

    @Operation(summary = "更新摄像头")
    @PutMapping
    public Result<Void> updateCamera(@RequestBody CameraDevice camera) {
        cameraService.update(camera);
        return Result.success();
    }

    @Operation(summary = "更新摄像头状态")
    @PutMapping("/{id}/status")
    public Result<Void> updateCameraStatus(
            @PathVariable Long id,
            @Parameter(description = "状态：0离线 1在线") @RequestParam Integer status) {
        cameraService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除摄像头")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCamera(@PathVariable Long id) {
        cameraService.delete(id);
        return Result.success();
    }

    @Operation(summary = "批量导入摄像头")
    @PostMapping("/import")
    public Result<Void> batchImportCameras(@RequestParam("file") MultipartFile file) {
        cameraService.batchImport(file);
        return Result.success();
    }
}
