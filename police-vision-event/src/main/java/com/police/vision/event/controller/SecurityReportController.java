package com.police.vision.event.controller;

import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.event.dto.ReportGenerateDTO;
import com.police.vision.event.entity.SecEventReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "报告生成管理", description = "安保报告生成、查询、详情及下载接口")
@RestController
@RequestMapping("/security-report")
@RequiredArgsConstructor
@Slf4j
public class SecurityReportController {

    private final com.police.vision.event.service.SecurityReportService securityReportService;

    @Operation(summary = "生成安保报告")
    @PostMapping("/generate")
    public Result<SecEventReport> generateReport(@RequestBody @Valid ReportGenerateDTO dto) {
        return securityReportService.generateReport(dto);
    }

    @Operation(summary = "分页查询报告列表")
    @GetMapping("/list")
    public Result<PageResult<SecEventReport>> getReportList(
            @RequestParam Long eventId,
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
    public void downloadReport(@PathVariable Long reportId, HttpServletResponse response) throws IOException {
        SecEventReport report = securityReportService.getReportDetail(reportId).getData();
        String fileName = (report != null && report.getReportName() != null ? report.getReportName() : "安保报告") + ".pdf";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

        byte[] pdfData = securityReportService.getReportPdfData(reportId);
        try (OutputStream out = response.getOutputStream()) {
            out.write(pdfData);
            out.flush();
        }
    }
}
