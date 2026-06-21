package com.police.vision.auth.controller;

import com.police.vision.auth.entity.PoliceUser;
import com.police.vision.auth.service.PoliceUserService;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "警员管理", description = "警员增删改查、状态管理")
@RestController
@RequestMapping("/police")
@RequiredArgsConstructor
public class PoliceUserController {

    private final PoliceUserService policeUserService;

    @Operation(summary = "分页查询警员列表")
    @GetMapping("/list")
    public Result<PageResult<PoliceUser>> getPoliceList(
            PageParam param,
            @Parameter(description = "部门ID") @RequestParam(required = false) Long deptId,
            @Parameter(description = "关键词（警号/姓名）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        return Result.success(policeUserService.getPoliceList(param, deptId, keyword, status));
    }

    @Operation(summary = "获取警员详情")
    @GetMapping("/{id}")
    public Result<PoliceUser> getPoliceById(@PathVariable Long id) {
        return Result.success(policeUserService.getPoliceById(id));
    }

    @Operation(summary = "根据警号查询警员")
    @GetMapping("/no/{policeNo}")
    public Result<PoliceUser> getPoliceByNo(@PathVariable String policeNo) {
        return Result.success(policeUserService.getPoliceByNo(policeNo));
    }

    @Operation(summary = "创建警员")
    @PostMapping
    public Result<PoliceUser> createPolice(@RequestBody PoliceUser user) {
        return Result.success(policeUserService.createPolice(user));
    }

    @Operation(summary = "更新警员信息")
    @PutMapping
    public Result<PoliceUser> updatePolice(@RequestBody PoliceUser user) {
        return Result.success(policeUserService.updatePolice(user));
    }

    @Operation(summary = "删除警员")
    @DeleteMapping("/{id}")
    public Result<Void> deletePolice(@PathVariable Long id) {
        policeUserService.deletePolice(id);
        return Result.success();
    }

    @Operation(summary = "更新警员状态")
    @PutMapping("/{id}/status")
    public Result<Void> updatePoliceStatus(
            @PathVariable Long id,
            @Parameter(description = "状态：0禁用 1启用") @RequestParam Integer status) {
        policeUserService.updatePoliceStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "更新警员位置")
    @PutMapping("/{id}/location")
    public Result<Void> updatePoliceLocation(
            @PathVariable Long id,
            @Parameter(description = "经度") @RequestParam BigDecimal longitude,
            @Parameter(description = "纬度") @RequestParam BigDecimal latitude) {
        policeUserService.updatePoliceLocation(id, longitude, latitude);
        return Result.success();
    }

    @Operation(summary = "获取部门下的警员列表")
    @GetMapping("/dept/{deptId}")
    public Result<List<PoliceUser>> getPoliceByDeptId(@PathVariable Long deptId) {
        return Result.success(policeUserService.getPoliceByDeptId(deptId));
    }

    @Operation(summary = "获取所有可用警力（在线且有位置）")
    @GetMapping("/available")
    public Result<List<PoliceUser>> getAvailablePolice() {
        return Result.success(policeUserService.getAvailablePolice());
    }
}
