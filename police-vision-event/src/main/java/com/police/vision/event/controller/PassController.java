package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.PassCreateDTO;
import com.police.vision.event.dto.PassVerifyDTO;
import com.police.vision.event.entity.SecPass;
import com.police.vision.event.service.PassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "通行证管理", description = "通行证生成、验证、吊销及查询接口")
@RestController
@RequestMapping("/event/pass")
@RequiredArgsConstructor
@Slf4j
public class PassController {

    private final PassService passService;

    @Operation(summary = "生成通行证")
    @PostMapping("/generate")
    public Result<Map<String, Object>> generatePass(@RequestBody @Valid PassCreateDTO dto) {
        return passService.generatePass(dto);
    }

    @Operation(summary = "批量生成通行证")
    @PostMapping("/batch-generate")
    public Result<List<Map<String, Object>>> batchGeneratePass(@RequestBody @Valid List<PassCreateDTO> dtoList) {
        return passService.batchGeneratePass(dtoList);
    }

    @Operation(summary = "验证通行证（JWT令牌方式）")
    @PostMapping("/verify")
    public Result<Map<String, Object>> verifyPass(@RequestBody @Valid PassVerifyDTO dto) {
        return passService.verifyPass(dto);
    }

    @Operation(summary = "验证通行证（二维码方式）")
    @PostMapping("/verify/qrcode")
    public Result<Map<String, Object>> verifyPassByQrCode(@RequestParam String qrCode) {
        return passService.verifyPassByQrCode(qrCode);
    }

    @Operation(summary = "分页查询通行证列表")
    @GetMapping("/list")
    public Result<PageResult<SecPass>> getPassList(
            @RequestParam(required = false) Long eventId,
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

    @Operation(summary = "下载通行证二维码图片")
    @GetMapping("/qrcode/download/{passId}")
    public ResponseEntity<byte[]> downloadQrCode(@PathVariable Long passId) {
        Result<byte[]> result = passService.downloadQrCode(passId);
        byte[] qrCodeBytes = result.getData();

        SecPass passDetail = passService.getPassDetail(passId).getData();
        String fileName = (passDetail != null && passDetail.getPassNo() != null ? passDetail.getPassNo() : "qrcode") + ".png";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(qrCodeBytes.length);
        headers.setContentDispositionFormData("attachment", encodedFileName);
        headers.add("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

        return ResponseEntity.ok()
                .headers(headers)
                .body(qrCodeBytes);
    }
}
