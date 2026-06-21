package com.police.vision.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.Result;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.MinioUtil;
import com.police.vision.common.util.RedisUtil;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.event.dto.ReportGenerateDTO;
import com.police.vision.event.entity.*;
import com.police.vision.event.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityReportService {

    private final SecEventReportMapper secEventReportMapper;
    private final SecEventMapper secEventMapper;
    private final SecTrafficAlertMapper secTrafficAlertMapper;
    private final SecEventResourceMapper secEventResourceMapper;
    private final SecPostMapper secPostMapper;
    private final SecSecurityPlanMapper secSecurityPlanMapper;
    private final RedisUtil redisUtil;
    private final MinioUtil minioUtil;

    private static final String JASPER_TEMPLATE_PATH = "jasper/event_report_template.jrxml";
    private static final String REPORT_BUCKET = "event-reports";
    private static final String REPORT_CONTENT_TYPE = "application/pdf";
    private static final String RESOURCE_TYPE_POLICE = "police";
    private static final String RESOURCE_TYPE_CAMERA = "camera";
    private static final String PEDESTRIAN_TOTAL_KEY = "event:%s:pedestrian:total";
    private static final String VEHICLE_TOTAL_KEY = "event:%s:vehicle:total";

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> generateReport(ReportGenerateDTO dto) {
        log.info("生成安保报告开始，eventId：{}，reportName：{}", dto.getEventId(), dto.getReportName());
        try {
            SecEvent event = secEventMapper.selectById(dto.getEventId());
            if (event == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "活动不存在");
            }

            int policeCount = countResources(dto.getEventId(), RESOURCE_TYPE_POLICE);
            int cameraCount = countResources(dto.getEventId(), RESOURCE_TYPE_CAMERA);
            int postCount = countPosts(dto.getEventId());
            int alertCount = countAlerts(dto.getEventId());

            long pedestrianCount = getTotalCount(dto.getEventId(), PEDESTRIAN_TOTAL_KEY, "pedestrian");
            long vehicleCount = getTotalCount(dto.getEventId(), VEHICLE_TOTAL_KEY, "vehicle");

            byte[] pdfBytes = generatePdfBytes(event, policeCount, cameraCount, postCount,
                    alertCount, pedestrianCount, vehicleCount);

            String fileName = generateReportFileName(dto.getEventId(), dto.getReportName());
            ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
            minioUtil.uploadFile(inputStream, fileName, REPORT_CONTENT_TYPE, REPORT_BUCKET);
            String reportUrl = minioUtil.getPresignedUrl(fileName, REPORT_BUCKET);

            String summary = buildSummary(event, policeCount, cameraCount, postCount,
                    alertCount, pedestrianCount, vehicleCount);

            SecEventReport report = new SecEventReport();
            report.setId(SnowflakeIdUtil.nextId());
            report.setEventId(dto.getEventId());
            report.setReportName(dto.getReportName());
            report.setReportUrl(reportUrl);
            report.setGenerateTime(LocalDateTime.now());
            report.setSummary(summary);
            report.setPedestrianCount(pedestrianCount);
            report.setVehicleCount(vehicleCount);
            report.setAlertCount(alertCount);
            report.setPoliceCount(policeCount);
            report.setCameraCount(cameraCount);
            report.setPostCount(postCount);
            secEventReportMapper.insert(report);

            Map<String, Object> result = new HashMap<>();
            result.put("reportId", report.getId());
            result.put("reportUrl", reportUrl);

            log.info("生成安保报告成功，reportId：{}", report.getId());
            return Result.success(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成安保报告失败", e);
            throw new BusinessException(ResultCode.FAIL, "生成安保报告失败：" + e.getMessage());
        }
    }

    public Result<PageResult<SecEventReport>> getReportList(Long eventId, int page, int size) {
        log.debug("分页查询报告列表，eventId：{}", eventId);
        try {
            LambdaQueryWrapper<SecEventReport> wrapper = new LambdaQueryWrapper<>();
            if (eventId != null) {
                wrapper.eq(SecEventReport::getEventId, eventId);
            }
            wrapper.orderByDesc(SecEventReport::getGenerateTime);

            Page<SecEventReport> pageParam = new Page<>(page, size);
            IPage<SecEventReport> result = secEventReportMapper.selectPage(pageParam, wrapper);
            PageResult<SecEventReport> pageResult = PageResult.of(result.getTotal(), result.getRecords(), page, size);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("分页查询报告列表失败", e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "分页查询报告列表失败：" + e.getMessage());
        }
    }

    public Result<SecEventReport> getReportDetail(Long reportId) {
        log.debug("查询报告详情，reportId：{}", reportId);
        try {
            SecEventReport report = secEventReportMapper.selectById(reportId);
            if (report == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "报告不存在");
            }
            return Result.success(report);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询报告详情失败，reportId：{}", reportId, e);
            throw new BusinessException(ResultCode.DATABASE_ERROR, "查询报告详情失败：" + e.getMessage());
        }
    }

    public Result<Map<String, Object>> downloadReport(Long reportId) {
        log.info("下载报告开始，reportId：{}", reportId);
        try {
            SecEventReport report = secEventReportMapper.selectById(reportId);
            if (report == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "报告不存在");
            }

            String fileName = extractFileNameFromUrl(report.getReportUrl());
            InputStream inputStream = minioUtil.downloadFile(fileName, REPORT_BUCKET);
            byte[] pdfBytes = inputStream.readAllBytes();

            Map<String, Object> result = new HashMap<>();
            result.put("reportName", report.getReportName());
            result.put("fileName", fileName);
            result.put("contentType", REPORT_CONTENT_TYPE);
            result.put("fileSize", pdfBytes.length);
            result.put("fileBytes", pdfBytes);

            log.info("下载报告成功，reportId：{}", reportId);
            return Result.success(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载报告失败，reportId：{}", reportId, e);
            throw new BusinessException(ResultCode.MINIO_ERROR, "下载报告失败：" + e.getMessage());
        }
    }

    private int countResources(Long eventId, String resourceType) {
        LambdaQueryWrapper<SecEventResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecEventResource::getEventId, eventId)
                .eq(SecEventResource::getResourceType, resourceType);
        return Math.toIntExact(secEventResourceMapper.selectCount(wrapper));
    }

    private int countPosts(Long eventId) {
        LambdaQueryWrapper<SecSecurityPlan> planWrapper = new LambdaQueryWrapper<>();
        planWrapper.eq(SecSecurityPlan::getEventId, eventId);
        List<SecSecurityPlan> plans = secSecurityPlanMapper.selectList(planWrapper);

        if (CollectionUtils.isEmpty(plans)) {
            return 0;
        }

        List<Long> planIds = plans.stream().map(SecSecurityPlan::getId).toList();
        LambdaQueryWrapper<SecPost> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.in(SecPost::getPlanId, planIds);
        return Math.toIntExact(secPostMapper.selectCount(postWrapper));
    }

    private int countAlerts(Long eventId) {
        LambdaQueryWrapper<SecTrafficAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecTrafficAlert::getEventId, eventId);
        return Math.toIntExact(secTrafficAlertMapper.selectCount(wrapper));
    }

    private long getTotalCount(Long eventId, String redisKeyTemplate, String alertTypeKeyword) {
        String redisKey = String.format(redisKeyTemplate, eventId);
        try {
            String value = redisUtil.get(redisKey);
            if (value != null && !value.isEmpty()) {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            log.warn("从Redis读取统计数据失败，key：{}，将从数据库统计", redisKey, e);
        }

        return sumAlertCountValue(eventId, alertTypeKeyword);
    }

    private long sumAlertCountValue(Long eventId, String alertTypeKeyword) {
        LambdaQueryWrapper<SecTrafficAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SecTrafficAlert::getEventId, eventId);
        if (alertTypeKeyword != null && !alertTypeKeyword.isEmpty()) {
            wrapper.like(SecTrafficAlert::getAlertType, alertTypeKeyword);
        }
        List<SecTrafficAlert> alerts = secTrafficAlertMapper.selectList(wrapper);
        return alerts.stream()
                .map(alert -> alert.getCountValue() != null ? alert.getCountValue() : 0L)
                .reduce(0L, Long::sum);
    }

    private byte[] generatePdfBytes(SecEvent event, int policeCount, int cameraCount, int postCount,
                                     int alertCount, long pedestrianCount, long vehicleCount) throws Exception {
        ClassPathResource resource = new ClassPathResource(JASPER_TEMPLATE_PATH);
        InputStream templateStream;
        try {
            templateStream = resource.getInputStream();
        } catch (Exception e) {
            log.warn("未找到JasperReport模板文件：{}，使用内置模板生成", JASPER_TEMPLATE_PATH);
            return generateSimplePdf(event, policeCount, cameraCount, postCount,
                    alertCount, pedestrianCount, vehicleCount);
        }

        JasperReport jasperReport = JasperCompileManager.compileReport(templateStream);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("eventId", event.getId());
        parameters.put("eventName", event.getEventName());
        parameters.put("eventType", event.getEventType());
        parameters.put("eventLevel", event.getEventLevel());
        parameters.put("startTime", event.getStartTime() != null ?
                event.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        parameters.put("endTime", event.getEndTime() != null ?
                event.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
        parameters.put("organizer", event.getOrganizer());
        parameters.put("description", event.getDescription());
        parameters.put("policeCount", policeCount);
        parameters.put("cameraCount", cameraCount);
        parameters.put("postCount", postCount);
        parameters.put("alertCount", alertCount);
        parameters.put("pedestrianCount", pedestrianCount);
        parameters.put("vehicleCount", vehicleCount);
        parameters.put("generateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        parameters.put("status", event.getStatus());

        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
        return outputStream.toByteArray();
    }

    private byte[] generateSimplePdf(SecEvent event, int policeCount, int cameraCount, int postCount,
                                      int alertCount, long pedestrianCount, long vehicleCount) {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<!DOCTYPE html>")
                .append("<html><head><meta charset='UTF-8'>")
                .append("<title>").append(event.getEventName()).append(" - 安保报告</title>")
                .append("<style>body{font-family:'SimSun',sans-serif;padding:40px;}")
                .append("h1{text-align:center;color:#1a1a1a;}")
                .append(".section{margin:20px 0;padding:15px;background:#f5f5f5;border-radius:8px;}")
                .append(".section h2{color:#2c3e50;border-bottom:2px solid #3498db;padding-bottom:8px;}")
                .append(".info-row{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px dashed #ddd;}")
                .append(".info-label{font-weight:bold;color:#555;}")
                .append(".info-value{color:#222;}")
                .append(".stats-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:15px;margin-top:15px;}")
                .append(".stat-card{background:#fff;padding:20px;text-align:center;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);}")
                .append(".stat-value{font-size:28px;font-weight:bold;color:#3498db;}")
                .append(".stat-label{font-size:14px;color:#777;margin-top:5px;}")
                .append(".footer{text-align:center;margin-top:40px;color:#888;font-size:12px;}")
                .append("</style></head><body>");

        htmlBuilder.append("<h1>安保工作报告</h1>");

        htmlBuilder.append("<div class='section'><h2>活动基本信息</h2>");
        htmlBuilder.append("<div class='info-row'><span class='info-label'>活动名称：</span><span class='info-value'>")
                .append(event.getEventName()).append("</span></div>");
        htmlBuilder.append("<div class='info-row'><span class='info-label'>活动类型：</span><span class='info-value'>")
                .append(nullSafe(event.getEventType())).append("</span></div>");
        htmlBuilder.append("<div class='info-row'><span class='info-label'>活动级别：</span><span class='info-value'>")
                .append(nullSafe(event.getEventLevel())).append("</span></div>");
        htmlBuilder.append("<div class='info-row'><span class='info-label'>开始时间：</span><span class='info-value'>")
                .append(event.getStartTime() != null ? event.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-").append("</span></div>");
        htmlBuilder.append("<div class='info-row'><span class='info-label'>结束时间：</span><span class='info-value'>")
                .append(event.getEndTime() != null ? event.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "-").append("</span></div>");
        htmlBuilder.append("<div class='info-row'><span class='info-label'>主办方：</span><span class='info-value'>")
                .append(nullSafe(event.getOrganizer())).append("</span></div>");
        htmlBuilder.append("<div class='info-row'><span class='info-label'>活动描述：</span><span class='info-value'>")
                .append(nullSafe(event.getDescription())).append("</span></div>");
        htmlBuilder.append("</div>");

        htmlBuilder.append("<div class='section'><h2>资源统计</h2><div class='stats-grid'>");
        htmlBuilder.append("<div class='stat-card'><div class='stat-value'>").append(policeCount)
                .append("</div><div class='stat-label'>投入警力</div></div>");
        htmlBuilder.append("<div class='stat-card'><div class='stat-value'>").append(cameraCount)
                .append("</div><div class='stat-label'>监控摄像头</div></div>");
        htmlBuilder.append("<div class='stat-card'><div class='stat-value'>").append(postCount)
                .append("</div><div class='stat-label'>设置岗点数</div></div>");
        htmlBuilder.append("</div></div>");

        htmlBuilder.append("<div class='section'><h2>流量与预警统计</h2><div class='stats-grid'>");
        htmlBuilder.append("<div class='stat-card'><div class='stat-value'>").append(pedestrianCount)
                .append("</div><div class='stat-label'>累计人流量</div></div>");
        htmlBuilder.append("<div class='stat-card'><div class='stat-value'>").append(vehicleCount)
                .append("</div><div class='stat-label'>累计车流量</div></div>");
        htmlBuilder.append("<div class='stat-card'><div class='stat-value'>").append(alertCount)
                .append("</div><div class='stat-label'>预警数量</div></div>");
        htmlBuilder.append("</div></div>");

        htmlBuilder.append("<div class='footer'>报告生成时间：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("</div>");
        htmlBuilder.append("</body></html>");

        return htmlBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String nullSafe(String value) {
        return value != null ? value : "-";
    }

    private String buildSummary(SecEvent event, int policeCount, int cameraCount, int postCount,
                                 int alertCount, long pedestrianCount, long vehicleCount) {
        return String.format("活动【%s】安保报告：投入警力%d人，摄像头%d个，设置岗点%d个；累计人流量%d人次，" +
                        "车流量%d辆次，产生预警%d条。",
                event.getEventName(), policeCount, cameraCount, postCount,
                pedestrianCount, vehicleCount, alertCount);
    }

    private String generateReportFileName(Long eventId, String reportName) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String safeName = reportName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_");
        return "event-reports/" + dateStr + "/" + eventId + "_" + safeName + "_" +
                System.currentTimeMillis() + ".pdf";
    }

    private String extractFileNameFromUrl(String reportUrl) {
        try {
            int startIndex = reportUrl.indexOf("/event-reports/");
            if (startIndex >= 0) {
                String path = reportUrl.substring(startIndex + 1);
                int queryIndex = path.indexOf('?');
                return queryIndex > 0 ? path.substring(0, queryIndex) : path;
            }
            int lastSlash = reportUrl.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < reportUrl.length() - 1) {
                String path = reportUrl.substring(lastSlash + 1);
                int queryIndex = path.indexOf('?');
                return queryIndex > 0 ? path.substring(0, queryIndex) : path;
            }
        } catch (Exception e) {
            log.warn("解析报告文件名失败，url：{}", reportUrl, e);
        }
        return reportUrl;
    }
}
