package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.ReportGenerateDTO;
import com.police.vision.event.entity.SecEventReport;
import com.police.vision.event.service.SecurityReportService;
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
import java.util.Map;

@Tag(name = "报告生成管理", description = "安保报告生成、查询、详情及下载接口")
@RestController
@RequestMapping("/event/report")
@RequiredArgsConstructor
@Slf4j
public class SecurityReportController {

    private final SecurityReportService securityReportService;

    @Operation(summary = "生成安保报告")
    @PostMapping("/generate")
    public Result<Map<String, Object>> generateReport(@RequestBody @Valid ReportGenerateDTO dto) {
        return securityReportService.generateReport(dto);
    }

    @Operation(summary = "分页查询报告列表")
    @GetMapping("/list")
    public Result<PageResult<SecEventReport>> getReportList(
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return securityReportService.getReportList(eventId, page, size);
    }

    @Operation(summary = "获取报告详情")
    @GetMapping("/{reportId}")
    public Result<SecEventReport> getReportDetail(@PathVariable Long reportId) {
        return securityReportService.getReportDetail(reportId);
    }

    @Operation(summary = "下载报告PDF")
    @GetMapping("/download/{reportId}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long reportId) {
        Result<Map<String, Object>> result = securityReportService.downloadReport(reportId);
        Map<String, Object> data = result.getData();

        byte[] fileBytes = (byte[]) data.get("fileBytes");
        String fileName = (String) data.get("fileName");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(fileBytes.length);
        headers.add("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }
}
