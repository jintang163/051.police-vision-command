package com.police.vision.mobile.controller;

import com.police.vision.common.entity.LoginUser;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.common.util.UserContext;
import com.police.vision.mobile.entity.MobileDispatchRecord;
import com.police.vision.mobile.service.DispatchMobileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/dispatch")
@RequiredArgsConstructor
public class DispatchMobileController {

    private final DispatchMobileService dispatchMobileService;

    @GetMapping("/list")
    public Result<List<MobileDispatchRecord>> getMyDispatches(
            @RequestParam(required = false) Integer status) {
        LoginUser user = UserContext.getCurrentUser();
        List<MobileDispatchRecord> list = dispatchMobileService.getMyDispatches(user.getUserId(), status);
        return Result.success(list);
    }

    @GetMapping("/{dispatchId}")
    public Result<MobileDispatchRecord> getDispatchDetail(@PathVariable Long dispatchId) {
        LoginUser user = UserContext.getCurrentUser();
        MobileDispatchRecord detail = dispatchMobileService.getDispatchDetail(user.getUserId(), dispatchId);
        return Result.success(detail);
    }

    @PostMapping("/{dispatchId}/accept")
    public Result<Void> acceptDispatch(@PathVariable Long dispatchId) {
        LoginUser user = UserContext.getCurrentUser();
        dispatchMobileService.acceptDispatch(user.getUserId(), dispatchId);
        return Result.success();
    }

    @PostMapping("/{dispatchId}/arrive")
    public Result<Void> arriveDispatch(
            @PathVariable Long dispatchId,
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude) {
        LoginUser user = UserContext.getCurrentUser();
        dispatchMobileService.arriveDispatch(user.getUserId(), dispatchId, longitude, latitude);
        return Result.success();
    }

    @PostMapping("/{dispatchId}/complete")
    public Result<Void> completeDispatch(
            @PathVariable Long dispatchId,
            @RequestParam String result,
            @RequestParam(required = false) String remark) {
        LoginUser user = UserContext.getCurrentUser();
        dispatchMobileService.completeDispatch(user.getUserId(), dispatchId, result, remark);
        return Result.success();
    }

    @GetMapping("/{dispatchId}/route")
    public Result<Map<String, Object>> getRoute(@PathVariable Long dispatchId) {
        LoginUser user = UserContext.getCurrentUser();
        Map<String, Object> route = dispatchMobileService.calculateRoute(user.getUserId(), dispatchId);
        return Result.success(route);
    }
}
