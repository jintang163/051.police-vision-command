package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.PassCreateDTO;
import com.police.vision.event.dto.PassVerifyDTO;
import com.police.vision.event.entity.SecPass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "通行证管理", description = "通行证生成、验证、吊销及查询接口")
@RestController
@RequestMapping("/pass")
@RequiredArgsConstructor
@Slf4j
public class PassController {

    private final com.police.vision.event.service.PassService passService;

    @Operation(summary = "生成通行证")
    @PostMapping("/generate")
    public Result<SecPass> generatePass(@RequestBody @Valid PassCreateDTO dto) {
        return passService.generatePass(dto);
    }

    @Operation(summary = "验证通行证（JWT令牌方式）")
    @PostMapping("/verify")
    public Result<SecPass> verifyPass(@RequestBody @Valid PassVerifyDTO dto) {
        return passService.verifyPass(dto);
    }

    @Operation(summary = "验证通行证（二维码方式）")
    @PostMapping("/verify/qrcode")
    public Result<SecPass> verifyPassByQrCode(@RequestParam String qrCode) {
        return passService.verifyPassByQrCode(qrCode);
    }

    @Operation(summary = "分页查询通行证列表")
    @GetMapping("/list")
    public Result<PageResult<SecPass>> getPassList(
            @RequestParam Long eventId,
            @RequestParam(required = false) String holderName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return passService.getPassList(eventId, holderName, status, page, size);
    }

    @Operation(summary = "吊销通行证")
    @PostMapping("/revoke/{passId}")
    public Result<Void> revokePass(@PathVariable Long passId) {
        return passService.revokePass(passId);
    }

    @Operation(summary = "获取通行证详情")
    @GetMapping("/{passId}")
    public Result<SecPass> getPassDetail(@PathVariable Long passId) {
        return passService.getPassDetail(passId);
    }
}
