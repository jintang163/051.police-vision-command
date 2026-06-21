package com.police.vision.video.controller;

import com.police.vision.common.result.Result;
import com.police.vision.video.entity.PlateRecord;
import com.police.vision.video.entity.TargetVehicle;
import com.police.vision.video.service.PlateRecognitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "车牌识别", description = "车牌识别、重点车辆布控")
@RestController
@RequestMapping("/plate")
@RequiredArgsConstructor
public class PlateController {

    private final PlateRecognitionService plateRecognitionService;

    @Operation(summary = "车牌识别")
    @PostMapping("/recognize")
    public Result<Map<String, Object>> recognizePlate(@RequestParam("image") MultipartFile image) throws IOException {
        Map<String, Object> result = plateRecognitionService.recognizePlate(image.getBytes());
        return Result.success(result);
    }

    @Operation(summary = "添加重点车辆布控")
    @PostMapping("/target")
    public Result<Void> addTargetVehicle(@RequestBody TargetVehicle vehicle) {
        plateRecognitionService.addVehicleToTarget(vehicle);
        return Result.success();
    }

    @Operation(summary = "更新重点车辆布控")
    @PutMapping("/target")
    public Result<Void> updateTargetVehicle(@RequestBody TargetVehicle vehicle) {
        plateRecognitionService.addVehicleToTarget(vehicle);
        return Result.success();
    }

    @Operation(summary = "获取重点车辆详情")
    @GetMapping("/target/{vehicleId}")
    public Result<TargetVehicle> getTargetVehicle(@PathVariable String vehicleId) {
        return Result.success(plateRecognitionService.getTargetVehicle(vehicleId));
    }

    @Operation(summary = "根据车牌号查询重点车辆")
    @GetMapping("/target/plate/{plateNo}")
    public Result<TargetVehicle> getTargetVehicleByPlateNo(@PathVariable String plateNo) {
        return Result.success(plateRecognitionService.getTargetVehicleByPlateNo(plateNo));
    }

    @Operation(summary = "获取重点车辆列表")
    @GetMapping("/target/list")
    public Result<List<TargetVehicle>> getTargetVehicleList(
            @Parameter(description = "状态：0停用 1启用") @RequestParam(required = false, defaultValue = "1") Integer status) {
        return Result.success(plateRecognitionService.getTargetVehicleByStatus(status));
    }

    @Operation(summary = "处理车牌匹配记录")
    @PostMapping("/match")
    public Result<Void> handlePlateMatch(@RequestBody PlateRecord record) {
        plateRecognitionService.handlePlateMatch(record);
        return Result.success();
    }

    @Operation(summary = "保存车牌识别记录")
    @PostMapping("/record")
    public Result<Void> savePlateRecord(@RequestBody PlateRecord record) {
        plateRecognitionService.savePlateRecord(record);
        return Result.success();
    }

    @Operation(summary = "验证车牌号格式")
    @GetMapping("/validate/{plateNo}")
    public Result<Boolean> validatePlate(@PathVariable String plateNo) {
        return Result.success(plateRecognitionService.validatePlate(plateNo));
    }
}
