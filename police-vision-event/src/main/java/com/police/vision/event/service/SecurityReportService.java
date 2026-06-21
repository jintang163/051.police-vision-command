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
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

            long pedestrianCount = getTotalCount(dto.getEventId(), PEDESTRIAN_TOTAL_KEY);
            long vehicleCount = getTotalCount(dto.getEventId(), VEHICLE_TOTAL_KEY);

            String summary = buildSummary(event, policeCount, cameraCount, postCount,
                    alertCount, pedestrianCount, vehicleCount);

            byte[] pdfBytes = generatePdfBytes(event, policeCount, cameraCount, postCount,
                    alertCount, pedestrianCount, vehicleCount, summary);

            String fileName = generateReportFileName(dto.getEventId(), dto.getReportName());
            ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
            minioUtil.uploadFile(inputStream, fileName, REPORT_CONTENT_TYPE, REPORT_BUCKET);
            String reportUrl = minioUtil.getPresignedUrl(fileName, REPORT_BUCKET);

            LocalDateTime generateTime = LocalDateTime.now();
            SecEventReport report = new SecEventReport();
            report.setId(SnowflakeIdUtil.nextId());
            report.setEventId(dto.getEventId());
            report.setReportName(dto.getReportName());
            report.setReportUrl(reportUrl);
            report.setGenerateTime(generateTime);
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
            result.put("reportName", dto.getReportName());
            result.put("reportUrl", reportUrl);
            result.put("generateTime", generateTime);

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
            byte[] fileBytes = inputStream.readAllBytes();

            Map<String, Object> result = new HashMap<>();
            result.put("fileName", report.getReportName() + ".pdf");
            result.put("contentType", REPORT_CONTENT_TYPE);
            result.put("fileBytes", fileBytes);
            result.put("fileSize", fileBytes.length);

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

    private long getTotalCount(Long eventId, String redisKeyTemplate) {
        String redisKey = String.format(redisKeyTemplate, eventId);
        try {
            String value = redisUtil.get(redisKey);
            if (StringUtils.hasText(value)) {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            log.warn("从Redis读取统计数据失败，key：{}，返回0", redisKey, e);
        }
        return 0L;
    }

    private byte[] generatePdfBytes(SecEvent event, int policeCount, int cameraCount, int postCount,
                                     int alertCount, long pedestrianCount, long vehicleCount, String summary) throws Exception {
        ClassPathResource resource = new ClassPathResource(JASPER_TEMPLATE_PATH);
        InputStream templateStream;
        try {
            templateStream = resource.getInputStream();
        } catch (Exception e) {
            log.warn("未找到JasperReport模板文件：{}，使用降级方案生成", JASPER_TEMPLATE_PATH, e);
            return generateSimplePdf(event, policeCount, cameraCount, postCount,
                    alertCount, pedestrianCount, vehicleCount, summary);
        }

        try {
            JasperDesign jasperDesign = JRXmlLoader.load(templateStream);
            JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("eventId", event.getId());
            parameters.put("eventName", event.getEventName());
            parameters.put("eventType", event.getEventType());
            parameters.put("eventLevel", event.getEventLevel());
            parameters.put("startTime", event.getStartTime() != null ?
                    event.getStartTime().format(DATE_TIME_FORMATTER) : "");
            parameters.put("endTime", event.getEndTime() != null ?
                    event.getEndTime().format(DATE_TIME_FORMATTER) : "");
            parameters.put("organizer", event.getOrganizer());
            parameters.put("description", event.getDescription());
            parameters.put("policeCount", policeCount);
            parameters.put("cameraCount", cameraCount);
            parameters.put("postCount", postCount);
            parameters.put("alertCount", alertCount);
            parameters.put("pedestrianCount", pedestrianCount);
            parameters.put("vehicleCount", vehicleCount);
            parameters.put("generateTime", LocalDateTime.now().format(DATE_TIME_FORMATTER));
            parameters.put("summary", summary);

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.warn("使用JasperReport模板生成PDF失败，使用降级方案生成", e);
            return generateSimplePdf(event, policeCount, cameraCount, postCount,
                    alertCount, pedestrianCount, vehicleCount, summary);
        }
    }

    private byte[] generateSimplePdf(SecEvent event, int policeCount, int cameraCount, int postCount,
                                      int alertCount, long pedestrianCount, long vehicleCount, String summary) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\"\n");
            sb.append("              name=\"SimpleReport\" pageWidth=\"595\" pageHeight=\"842\" whenNoDataType=\"AllSectionsNoDetail\"\n");
            sb.append("              columnWidth=\"555\" leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\">\n");
            sb.append("    <parameter name=\"eventName\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"eventType\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"eventLevel\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"startTime\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"endTime\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"organizer\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"description\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"generateTime\" class=\"java.lang.String\"/>\n");
            sb.append("    <parameter name=\"policeCount\" class=\"java.lang.Integer\"/>\n");
            sb.append("    <parameter name=\"cameraCount\" class=\"java.lang.Integer\"/>\n");
            sb.append("    <parameter name=\"postCount\" class=\"java.lang.Integer\"/>\n");
            sb.append("    <parameter name=\"alertCount\" class=\"java.lang.Integer\"/>\n");
            sb.append("    <parameter name=\"pedestrianCount\" class=\"java.lang.Long\"/>\n");
            sb.append("    <parameter name=\"vehicleCount\" class=\"java.lang.Long\"/>\n");
            sb.append("    <parameter name=\"summary\" class=\"java.lang.String\"/>\n");
            sb.append("    <title>\n");
            sb.append("        <band height=\"80\">\n");
            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"0\" y=\"20\" width=\"555\" height=\"30\"/>\n");
            sb.append("                <textElement textAlignment=\"Center\" fontSize=\"22\" isBold=\"true\"/>\n");
            sb.append("                <text><![CDATA[重大活动安保总结报告]]></text>\n");
            sb.append("            </staticText>\n");
            sb.append("            <textField>\n");
            sb.append("                <reportElement x=\"380\" y=\"55\" width=\"175\" height=\"20\"/>\n");
            sb.append("                <textElement textAlignment=\"Right\" fontSize=\"9\"/>\n");
            sb.append("                <textFieldExpression><![CDATA[\"报告生成时间: \" + $P{generateTime}]]></textFieldExpression>\n");
            sb.append("            </textField>\n");
            sb.append("        </band>\n");
            sb.append("    </title>\n");
            sb.append("    <detail>\n");
            sb.append("        <band height=\"500\">\n");
            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"0\" y=\"0\" width=\"555\" height=\"20\" backcolor=\"#1a5490\"/>\n");
            sb.append("                <textElement textAlignment=\"Left\" fontSize=\"12\" isBold=\"true\" forecolor=\"#ffffff\"/>\n");
            sb.append("                <text><![CDATA[  一、活动基本信息]]></text>\n");
            sb.append("            </staticText>\n");
            int yPos = 30;
            yPos = addTextField(sb, yPos, "活动名称:", "$P{eventName}", 200);
            yPos = addTextField(sb, yPos, "活动类型:", "$P{eventType}", 150);
            yPos = addTextField(sb, yPos, "安保级别:", "$P{eventLevel}", 200);
            yPos = addTextField(sb, yPos, "主办单位:", "$P{organizer}", 150);
            yPos = addTextField(sb, yPos, "开始时间:", "$P{startTime}", 200);
            yPos = addTextField(sb, yPos, "结束时间:", "$P{endTime}", 150);
            yPos += 5;
            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"20\" y=\"").append(yPos).append("\" width=\"80\" height=\"20\"/>\n");
            sb.append("                <textElement fontSize=\"11\" isBold=\"true\"/>\n");
            sb.append("                <text><![CDATA[活动描述:]]></text>\n");
            sb.append("            </staticText>\n");
            sb.append("            <textField isStretchWithOverflow=\"true\">\n");
            sb.append("                <reportElement x=\"100\" y=\"").append(yPos).append("\" width=\"435\" height=\"40\"/>\n");
            sb.append("                <textElement fontSize=\"11\"/>\n");
            sb.append("                <textFieldExpression><![CDATA[$P{description}]]></textFieldExpression>\n");
            sb.append("            </textField>\n");
            yPos += 50;

            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"0\" y=\"").append(yPos).append("\" width=\"555\" height=\"20\" backcolor=\"#1a5490\"/>\n");
            sb.append("                <textElement textAlignment=\"Left\" fontSize=\"12\" isBold=\"true\" forecolor=\"#ffffff\"/>\n");
            sb.append("                <text><![CDATA[  二、资源投入统计]]></text>\n");
            sb.append("            </staticText>\n");
            yPos += 30;

            String[] statLabels = {"投入警力", "监控摄像头", "执勤岗位", "预警处置"};
            String[] statExpressions = {"$P{policeCount} + \" 人\"", "$P{cameraCount} + \" 路\"", "$P{postCount} + \" 个\"", "$P{alertCount} + \" 起\""};
            int[] statX = {20, 160, 300, 440};
            for (int i = 0; i < 4; i++) {
                sb.append("            <rectangle>\n");
                sb.append("                <reportElement x=\"").append(statX[i]).append("\" y=\"").append(yPos).append("\" width=\"115\" height=\"60\" backcolor=\"#e8f0fe\"/>\n");
                sb.append("            </rectangle>\n");
                sb.append("            <staticText>\n");
                sb.append("                <reportElement x=\"").append(statX[i]).append("\" y=\"").append(yPos + 5).append("\" width=\"115\" height=\"20\"/>\n");
                sb.append("                <textElement textAlignment=\"Center\" fontSize=\"11\" isBold=\"true\"/>\n");
                sb.append("                <text><![CDATA[").append(statLabels[i]).append("]]></text>\n");
                sb.append("            </staticText>\n");
                sb.append("            <textField>\n");
                sb.append("                <reportElement x=\"").append(statX[i]).append("\" y=\"").append(yPos + 25).append("\" width=\"115\" height=\"30\"/>\n");
                sb.append("                <textElement textAlignment=\"Center\" fontSize=\"18\" isBold=\"true\" forecolor=\"#1a5490\"/>\n");
                sb.append("                <textFieldExpression><![CDATA[").append(statExpressions[i]).append("]]></textFieldExpression>\n");
                sb.append("            </textField>\n");
            }
            yPos += 95;

            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"0\" y=\"").append(yPos).append("\" width=\"555\" height=\"20\" backcolor=\"#1a5490\"/>\n");
            sb.append("                <textElement textAlignment=\"Left\" fontSize=\"12\" isBold=\"true\" forecolor=\"#ffffff\"/>\n");
            sb.append("                <text><![CDATA[  三、流量监测统计]]></text>\n");
            sb.append("            </staticText>\n");
            yPos += 30;

            sb.append("            <rectangle>\n");
            sb.append("                <reportElement x=\"20\" y=\"").append(yPos).append("\" width=\"250\" height=\"70\" backcolor=\"#f0f5fc\"/>\n");
            sb.append("            </rectangle>\n");
            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"20\" y=\"").append(yPos + 10).append("\" width=\"250\" height=\"20\"/>\n");
            sb.append("                <textElement textAlignment=\"Center\" fontSize=\"12\" isBold=\"true\"/>\n");
            sb.append("                <text><![CDATA[活动期间累计人流量]]></text>\n");
            sb.append("            </staticText>\n");
            sb.append("            <textField>\n");
            sb.append("                <reportElement x=\"20\" y=\"").append(yPos + 30).append("\" width=\"250\" height=\"30\"/>\n");
            sb.append("                <textElement textAlignment=\"Center\" fontSize=\"24\" isBold=\"true\" forecolor=\"#1a5490\"/>\n");
            sb.append("                <textFieldExpression><![CDATA[$P{pedestrianCount}]]></textFieldExpression>\n");
            sb.append("            </textField>\n");

            sb.append("            <rectangle>\n");
            sb.append("                <reportElement x=\"290\" y=\"").append(yPos).append("\" width=\"265\" height=\"70\" backcolor=\"#f0fcf0\"/>\n");
            sb.append("            </rectangle>\n");
            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"290\" y=\"").append(yPos + 10).append("\" width=\"265\" height=\"20\"/>\n");
            sb.append("                <textElement textAlignment=\"Center\" fontSize=\"12\" isBold=\"true\"/>\n");
            sb.append("                <text><![CDATA[活动期间累计车流量]]></text>\n");
            sb.append("            </staticText>\n");
            sb.append("            <textField>\n");
            sb.append("                <reportElement x=\"290\" y=\"").append(yPos + 30).append("\" width=\"265\" height=\"30\"/>\n");
            sb.append("                <textElement textAlignment=\"Center\" fontSize=\"24\" isBold=\"true\" forecolor=\"#1a7a3d\"/>\n");
            sb.append("                <textFieldExpression><![CDATA[$P{vehicleCount}]]></textFieldExpression>\n");
            sb.append("            </textField>\n");
            yPos += 90;

            sb.append("            <staticText>\n");
            sb.append("                <reportElement x=\"0\" y=\"").append(yPos).append("\" width=\"555\" height=\"20\" backcolor=\"#1a5490\"/>\n");
            sb.append("                <textElement textAlignment=\"Left\" fontSize=\"12\" isBold=\"true\" forecolor=\"#ffffff\"/>\n");
            sb.append("                <text><![CDATA[  四、活动总结]]></text>\n");
            sb.append("            </staticText>\n");
            yPos += 30;

            sb.append("            <textField isStretchWithOverflow=\"true\">\n");
            sb.append("                <reportElement x=\"20\" y=\"").append(yPos).append("\" width=\"515\" height=\"100\"/>\n");
            sb.append("                <textElement textAlignment=\"Justified\" lineSpacing=\"1.5\" fontSize=\"11\"/>\n");
            sb.append("                <textFieldExpression><![CDATA[$P{summary}]]></textFieldExpression>\n");
            sb.append("            </textField>\n");

            sb.append("        </band>\n");
            sb.append("    </detail>\n");
            sb.append("    <pageFooter>\n");
            sb.append("        <band height=\"30\">\n");
            sb.append("            <textField>\n");
            sb.append("                <reportElement x=\"0\" y=\"10\" width=\"200\" height=\"20\"/>\n");
            sb.append("                <textElement textAlignment=\"Left\" fontSize=\"9\" forecolor=\"#999999\"/>\n");
            sb.append("                <textFieldExpression><![CDATA[\"公安视图智能综合实战指挥平台\"]]></textFieldExpression>\n");
            sb.append("            </textField>\n");
            sb.append("            <textField>\n");
            sb.append("                <reportElement x=\"355\" y=\"10\" width=\"200\" height=\"20\"/>\n");
            sb.append("                <textElement textAlignment=\"Right\" fontSize=\"9\" forecolor=\"#999999\"/>\n");
            sb.append("                <textFieldExpression><![CDATA[\"第 \" + $V{PAGE_NUMBER} + \" 页\"]]></textFieldExpression>\n");
            sb.append("            </textField>\n");
            sb.append("        </band>\n");
            sb.append("    </pageFooter>\n");
            sb.append("</jasperReport>");

            InputStream templateStream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
            JasperDesign jasperDesign = JRXmlLoader.load(templateStream);
            JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("eventName", event.getEventName() != null ? event.getEventName() : "");
            parameters.put("eventType", event.getEventType() != null ? event.getEventType() : "");
            parameters.put("eventLevel", event.getEventLevel() != null ? event.getEventLevel() : "");
            parameters.put("startTime", event.getStartTime() != null ? event.getStartTime().format(DATE_TIME_FORMATTER) : "");
            parameters.put("endTime", event.getEndTime() != null ? event.getEndTime().format(DATE_TIME_FORMATTER) : "");
            parameters.put("organizer", event.getOrganizer() != null ? event.getOrganizer() : "");
            parameters.put("description", event.getDescription() != null ? event.getDescription() : "");
            parameters.put("policeCount", policeCount);
            parameters.put("cameraCount", cameraCount);
            parameters.put("postCount", postCount);
            parameters.put("alertCount", alertCount);
            parameters.put("pedestrianCount", pedestrianCount);
            parameters.put("vehicleCount", vehicleCount);
            parameters.put("generateTime", LocalDateTime.now().format(DATE_TIME_FORMATTER));
            parameters.put("summary", summary != null ? summary : "");

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(jasperPrint, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("降级生成PDF失败", e);
            throw new BusinessException(ResultCode.FAIL, "生成PDF失败：" + e.getMessage());
        }
    }

    private int addTextField(StringBuilder sb, int yPos, String label, String expression, int valueWidth) {
        sb.append("            <staticText>\n");
        sb.append("                <reportElement x=\"20\" y=\"").append(yPos).append("\" width=\"80\" height=\"20\"/>\n");
        sb.append("                <textElement fontSize=\"11\" isBold=\"true\"/>\n");
        sb.append("                <text><![CDATA[").append(label).append("]]></text>\n");
        sb.append("            </staticText>\n");
        sb.append("            <textField>\n");
        sb.append("                <reportElement x=\"100\" y=\"").append(yPos).append("\" width=\"").append(valueWidth).append("\" height=\"20\"/>\n");
        sb.append("                <textElement fontSize=\"11\"/>\n");
        sb.append("                <textFieldExpression><![CDATA[").append(expression).append("]]></textFieldExpression>\n");
        sb.append("            </textField>\n");
        return yPos + 22;
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
