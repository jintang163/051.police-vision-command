package com.police.vision.control.job.intelligence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.control.config.intelligence.IntelligenceConfig;
import com.police.vision.control.dto.intelligence.ClusterAnalyzeDTO;
import com.police.vision.control.dto.intelligence.PredictionDTO;
import com.police.vision.control.dto.intelligence.ReportGenerateDTO;
import com.police.vision.control.entity.intelligence.CrawlerTask;
import com.police.vision.control.entity.intelligence.HotspotPrediction;
import com.police.vision.control.mapper.intelligence.CrawlerTaskMapper;
import com.police.vision.control.mapper.intelligence.HotspotPredictionMapper;
import com.police.vision.control.mapper.intelligence.PublicOpinionMapper;
import com.police.vision.control.service.intelligence.AnalysisModelService;
import com.police.vision.control.service.intelligence.CaseClusterService;
import com.police.vision.control.service.intelligence.CrawlerService;
import com.police.vision.control.service.intelligence.HotspotPredictionService;
import com.police.vision.control.service.intelligence.IntelligenceProductService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntelligenceJobHandler {

    private final IntelligenceProductService productService;
    private final CaseClusterService clusterService;
    private final HotspotPredictionService predictionService;
    private final CrawlerService crawlerService;
    private final AnalysisModelService modelService;
    private final IntelligenceConfig intelligenceConfig;
    private final CrawlerTaskMapper crawlerTaskMapper;
    private final PublicOpinionMapper publicOpinionMapper;
    private final HotspotPredictionMapper hotspotPredictionMapper;

    @XxlJob("reportGenerateHandler")
    public void reportGenerateHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("reportGenerateHandler 开始执行，param={}", param);

            String productType = null;
            String areaCode = null;
            LocalDate reportStartDate = null;
            LocalDate reportEndDate = null;

            if (StringUtils.hasText(param)) {
                String trimmed = param.trim();
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    try {
                        JSONObject json = JSON.parseObject(trimmed);
                        productType = json.getString("productType");
                        areaCode = json.getString("areaCode");
                        if (json.containsKey("reportStartDate")) {
                            reportStartDate = parseLocalDate(json.getString("reportStartDate"));
                        }
                        if (json.containsKey("reportEndDate")) {
                            reportEndDate = parseLocalDate(json.getString("reportEndDate"));
                        }
                    } catch (Exception e) {
                        XxlJobHelper.log("解析JSON参数失败，尝试按简单文本处理: {}", e.getMessage());
                        productType = trimmed;
                    }
                } else {
                    productType = trimmed;
                }
            }

            if (!StringUtils.hasText(productType)) {
                productType = "DAILY";
            }

            if (reportStartDate == null || reportEndDate == null) {
                LocalDate[] dates = calculateReportDates(productType);
                reportStartDate = dates[0];
                reportEndDate = dates[1];
            }

            ReportGenerateDTO dto = new ReportGenerateDTO();
            dto.setProductType(normalizeProductType(productType));
            dto.setReportStartDate(reportStartDate);
            dto.setReportEndDate(reportEndDate);
            dto.setAreaCode(areaCode);

            Integer historyDays = intelligenceConfig.getReport().getHistoryDays();

            productService.generateReport(dto);

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("reportGenerateHandler 执行成功，productType={}, areaCode={}, startDate={}, endDate={}, 耗时={}ms",
                    productType, areaCode, reportStartDate, reportEndDate, costMs);
            XxlJobHelper.handleSuccess("报告生成成功");
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("reportGenerateHandler 执行异常", e);
            XxlJobHelper.log("reportGenerateHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("报告生成失败: " + e.getMessage());
        }
    }

    @XxlJob("dailyReportHandler")
    public void dailyReportHandler() {
        long startMs = System.currentTimeMillis();
        try {
            XxlJobHelper.log("dailyReportHandler 开始执行");
            LocalDate[] dates = calculateReportDates("DAILY");
            ReportGenerateDTO dto = new ReportGenerateDTO();
            dto.setProductType("DAILY_REPORT");
            dto.setReportStartDate(dates[0]);
            dto.setReportEndDate(dates[1]);
            productService.generateReport(dto);
            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("dailyReportHandler 执行成功，startDate={}, endDate={}, 耗时={}ms",
                    dates[0], dates[1], costMs);
            XxlJobHelper.handleSuccess("日报生成成功");
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("dailyReportHandler 执行异常", e);
            XxlJobHelper.log("dailyReportHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("日报生成失败: " + e.getMessage());
        }
    }

    @XxlJob("weeklyReportHandler")
    public void weeklyReportHandler() {
        long startMs = System.currentTimeMillis();
        try {
            XxlJobHelper.log("weeklyReportHandler 开始执行");
            LocalDate[] dates = calculateReportDates("WEEKLY");
            ReportGenerateDTO dto = new ReportGenerateDTO();
            dto.setProductType("WEEKLY_REPORT");
            dto.setReportStartDate(dates[0]);
            dto.setReportEndDate(dates[1]);
            productService.generateReport(dto);
            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("weeklyReportHandler 执行成功，startDate={}, endDate={}, 耗时={}ms",
                    dates[0], dates[1], costMs);
            XxlJobHelper.handleSuccess("周报生成成功");
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("weeklyReportHandler 执行异常", e);
            XxlJobHelper.log("weeklyReportHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("周报生成失败: " + e.getMessage());
        }
    }

    @XxlJob("monthlyReportHandler")
    public void monthlyReportHandler() {
        long startMs = System.currentTimeMillis();
        try {
            XxlJobHelper.log("monthlyReportHandler 开始执行");
            LocalDate[] dates = calculateReportDates("MONTHLY");
            ReportGenerateDTO dto = new ReportGenerateDTO();
            dto.setProductType("MONTHLY_REPORT");
            dto.setReportStartDate(dates[0]);
            dto.setReportEndDate(dates[1]);
            productService.generateReport(dto);
            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("monthlyReportHandler 执行成功，startDate={}, endDate={}, 耗时={}ms",
                    dates[0], dates[1], costMs);
            XxlJobHelper.handleSuccess("月报生成成功");
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("monthlyReportHandler 执行异常", e);
            XxlJobHelper.log("monthlyReportHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("月报生成失败: " + e.getMessage());
        }
    }

    @XxlJob("caseClusterHandler")
    public void caseClusterHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("caseClusterHandler 开始执行，param={}", param);

            Integer timeWindowHours = 72;
            BigDecimal similarityThreshold = new BigDecimal("0.75");
            Integer minClusterSize = 2;
            String caseType = null;
            String areaCode = null;

            if (StringUtils.hasText(param)) {
                try {
                    JSONObject json = JSON.parseObject(param.trim());
                    if (json.containsKey("timeWindowHours")) {
                        timeWindowHours = json.getInteger("timeWindowHours");
                    }
                    if (json.containsKey("similarityThreshold")) {
                        similarityThreshold = json.getBigDecimal("similarityThreshold");
                    }
                    if (json.containsKey("minClusterSize")) {
                        minClusterSize = json.getInteger("minClusterSize");
                    }
                    caseType = json.getString("caseType");
                    areaCode = json.getString("areaCode");
                } catch (Exception ignored) {
                }
            }

            ClusterAnalyzeDTO dto = new ClusterAnalyzeDTO();
            dto.setTimeWindowHours(timeWindowHours);
            dto.setSimilarityThreshold(similarityThreshold);
            dto.setMinClusterSize(minClusterSize);
            dto.setCaseType(caseType);
            dto.setAreaCode(areaCode);

            Map<String, Object> result = clusterService.analyzeClusters(dto);

            int clusterCount = result != null && result.get("clusters") != null
                    ? ((List<?>) result.get("clusters")).size() : 0;
            int totalCases = result != null && result.get("totalCases") != null
                    ? (Integer) result.get("totalCases") : 0;

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("caseClusterHandler 执行成功，聚类数={}, 处理案件数={}, 耗时={}ms",
                    clusterCount, totalCases, costMs);
            XxlJobHelper.handleSuccess(String.format("串并案分析完成，聚类%d个，案件%d个", clusterCount, totalCases));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("caseClusterHandler 执行异常", e);
            XxlJobHelper.log("caseClusterHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("串并案分析失败: " + e.getMessage());
        }
    }

    @XxlJob("hotspotPredictionHandler")
    public void hotspotPredictionHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("hotspotPredictionHandler 开始执行，param={}", param);

            Integer predictHours = 24;
            Integer historyDays = 90;
            Integer gridSizeMeters = 500;
            String caseType = null;
            String areaCode = null;

            if (StringUtils.hasText(param)) {
                try {
                    JSONObject json = JSON.parseObject(param.trim());
                    if (json.containsKey("predictHours")) {
                        predictHours = json.getInteger("predictHours");
                    }
                    if (json.containsKey("historyDays")) {
                        historyDays = json.getInteger("historyDays");
                    }
                    if (json.containsKey("gridSizeMeters")) {
                        gridSizeMeters = json.getInteger("gridSizeMeters");
                    }
                    caseType = json.getString("caseType");
                    areaCode = json.getString("areaCode");
                } catch (Exception ignored) {
                }
            }

            PredictionDTO dto = new PredictionDTO();
            dto.setPredictHours(predictHours);
            dto.setHistoryDays(historyDays);
            dto.setGridSizeMeters(gridSizeMeters);
            dto.setCaseType(caseType);
            dto.setAreaCode(areaCode);

            Map<String, Object> result = predictionService.runPrediction(dto);

            String batch = result != null ? String.valueOf(result.get("predictionBatch")) : null;
            int totalGrids = result != null && result.get("totalGrids") != null
                    ? (Integer) result.get("totalGrids") : 0;
            int highRiskCount = result != null && result.get("highRiskCount") != null
                    ? (Integer) result.get("highRiskCount") : 0;

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("hotspotPredictionHandler 执行成功，batch={}, totalGrids={}, highRisk={}, 耗时={}ms",
                    batch, totalGrids, highRiskCount, costMs);
            XxlJobHelper.handleSuccess(String.format("热点预测完成，batch=%s，网格%d个，高风险%d个", batch, totalGrids, highRiskCount));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("hotspotPredictionHandler 执行异常", e);
            XxlJobHelper.log("hotspotPredictionHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("热点预测失败: " + e.getMessage());
        }
    }

    @XxlJob("crawlerHandler")
    public void crawlerHandler() {
        long startMs = System.currentTimeMillis();
        int totalCrawlCount = 0;
        int totalNewCount = 0;
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("crawlerHandler 开始执行，param={}", param);

            if (StringUtils.hasText(param) && !"ALL".equalsIgnoreCase(param.trim())) {
                String taskId = param.trim();
                CrawlerTask task = crawlerTaskMapper.selectById(taskId);
                if (task == null) {
                    LambdaQueryWrapper<CrawlerTask> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(CrawlerTask::getTaskId, taskId);
                    task = crawlerTaskMapper.selectOne(wrapper);
                }
                if (task == null) {
                    XxlJobHelper.log("未找到爬虫任务: taskId={}", taskId);
                    XxlJobHelper.handleFail("未找到爬虫任务: " + taskId);
                    return;
                }
                Map<String, Object> result = crawlerService.executeTask(task);
                totalCrawlCount = result.get("crawlCount") != null ? (Integer) result.get("crawlCount") : 0;
                totalNewCount = result.get("newCount") != null ? (Integer) result.get("newCount") : 0;
            } else {
                List<CrawlerTask> tasks = crawlerTaskMapper.selectEnabledTasks();
                if (tasks == null || tasks.isEmpty()) {
                    XxlJobHelper.log("没有启用的爬虫任务");
                    XxlJobHelper.handleSuccess("没有启用的爬虫任务");
                    return;
                }
                XxlJobHelper.log("共{}个启用的爬虫待执行", tasks.size());
                for (CrawlerTask task : tasks) {
                    try {
                        Map<String, Object> result = crawlerService.executeTask(task);
                        int crawlCount = result.get("crawlCount") != null ? (Integer) result.get("crawlCount") : 0;
                        int newCount = result.get("newCount") != null ? (Integer) result.get("newCount") : 0;
                        totalCrawlCount += crawlCount;
                        totalNewCount += newCount;
                        XxlJobHelper.log("任务执行完成: taskId={}, taskName={}, crawlCount={}, newCount={}",
                                task.getTaskId(), task.getTaskName(), crawlCount, newCount);
                    } catch (Exception e) {
                        XxlJobHelper.log("爬虫任务执行异常: taskId={}, error={}", task.getTaskId(), e.getMessage());
                    }
                }
            }

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("crawlerHandler 执行成功，crawlCount={}, newCount={}, 耗时={}ms",
                    totalCrawlCount, totalNewCount, costMs);
            XxlJobHelper.handleSuccess(String.format("爬虫执行完成，采集{}条，新增{}条", totalCrawlCount, totalNewCount));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("crawlerHandler 执行异常", e);
            XxlJobHelper.log("crawlerHandler 执行异常: {}, crawlCount={}, newCount={}, 耗时={}ms",
                    e.getMessage(), totalCrawlCount, totalNewCount, costMs);
            XxlJobHelper.handleFail("爬虫执行失败: " + e.getMessage());
        }
    }

    @XxlJob("sentimentAnalysisHandler")
    public void sentimentAnalysisHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("sentimentAnalysisHandler 开始执行，param={}", param);

            int batchSize = 50;
            if (StringUtils.hasText(param)) {
                try {
                    batchSize = Integer.parseInt(param.trim());
                } catch (NumberFormatException ignored) {
                    try {
                        JSONObject json = JSON.parseObject(param.trim());
                        if (json.containsKey("batchSize")) {
                            batchSize = json.getInteger("batchSize");
                        }
                    } catch (Exception ignored2) {
                    }
                }
            }

            Map<String, Object> result = crawlerService.batchSentimentAnalysis(batchSize);
            int success = result.get("success") != null ? (Integer) result.get("success") : 0;
            int failed = result.get("failed") != null ? (Integer) result.get("failed") : 0;
            int processed = result.get("processed") != null ? (Integer) result.get("processed") : 0;

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("sentimentAnalysisHandler 执行成功，processed={}, success={}, failed={}, 耗时={}ms",
                    processed, success, failed, costMs);
            XxlJobHelper.handleSuccess(String.format("情感分析完成，处理{}条，成功{}条，失败{}条", processed, success, failed));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("sentimentAnalysisHandler 执行异常", e);
            XxlJobHelper.log("sentimentAnalysisHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("情感分析失败: " + e.getMessage());
        }
    }

    @XxlJob("cleanOldOpinionHandler")
    public void cleanOldOpinionHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("cleanOldOpinionHandler 开始执行，param={}", param);

            int days = 90;
            int limit = 10000;

            if (StringUtils.hasText(param)) {
                try {
                    JSONObject json = JSON.parseObject(param.trim());
                    if (json.containsKey("days")) {
                        days = json.getInteger("days");
                    }
                    if (json.containsKey("limit")) {
                        limit = json.getInteger("limit");
                    }
                } catch (Exception ignored) {
                }
            }

            int cleaned = publicOpinionMapper.cleanOldOpinions(days, limit);

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("cleanOldOpinionHandler 执行成功，清理{}天前舆情{}条，耗时={}ms",
                    days, cleaned, costMs);
            XxlJobHelper.handleSuccess(String.format("清理完成，删除%d天前舆情%d条", days, cleaned));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("cleanOldOpinionHandler 执行异常", e);
            XxlJobHelper.log("cleanOldOpinionHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("清理舆情失败: " + e.getMessage());
        }
    }

    @XxlJob("modelDispatchHandler")
    public void modelDispatchHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("modelDispatchHandler 开始执行，param={}", param);

            if (!StringUtils.hasText(param)) {
                XxlJobHelper.log("modelDispatchHandler 参数为空，modelId 必填");
                XxlJobHelper.handleFail("modelId 必填");
                return;
            }

            String modelId = null;
            Map<String, Object> extraParams = null;

            String trimmed = param.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    JSONObject json = JSON.parseObject(trimmed);
                    modelId = json.getString("modelId");
                    JSONObject extraJson = json.getJSONObject("extraParams");
                    if (extraJson != null) {
                        extraParams = JSON.parseObject(extraJson.toJSONString());
                    }
                } catch (Exception e) {
                    XxlJobHelper.log("解析JSON参数失败: {}", e.getMessage());
                }
            } else {
                modelId = trimmed;
            }

            if (!StringUtils.hasText(modelId)) {
                XxlJobHelper.log("modelId 必填");
                XxlJobHelper.handleFail("modelId 必填");
                return;
            }

            Map<String, Object> result = modelService.executeModel(modelId, extraParams);

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("modelDispatchHandler 执行成功，modelId={}, 耗时={}ms", modelId, costMs);
            XxlJobHelper.handleSuccess("模型执行成功: modelId=" + modelId);
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("modelDispatchHandler 执行异常", e);
            XxlJobHelper.log("modelDispatchHandler 执行异常: {}, 耗时={}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("模型调度失败: " + e.getMessage());
        }
    }

    @XxlJob("evaluatePredictionAccuracyHandler")
    public void evaluatePredictionAccuracyHandler() {
        long startMs = System.currentTimeMillis();
        int evaluatedBatches = 0;
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("evaluatePredictionAccuracyHandler 开始执行，param={}", param);

            int startHoursAgo = 25;
            int endHoursAgo = 49;

            if (StringUtils.hasText(param)) {
                try {
                    JSONObject json = JSON.parseObject(param.trim());
                    if (json.containsKey("startHoursAgo")) {
                        startHoursAgo = json.getInteger("startHoursAgo");
                    }
                    if (json.containsKey("endHoursAgo")) {
                        endHoursAgo = json.getInteger("endHoursAgo");
                    }
                } catch (Exception ignored) {
                }
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.minusHours(endHoursAgo);
            LocalDateTime windowEnd = now.minusHours(startHoursAgo);

            LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(HotspotPrediction::getModelRunTime, windowStart, windowEnd);
            wrapper.isNull(HotspotPrediction::getPredictionAccuracy);
            wrapper.select(HotspotPrediction::getPredictionBatch);
            wrapper.groupBy(HotspotPrediction::getPredictionBatch);

            List<HotspotPrediction> candidates = hotspotPredictionMapper.selectList(wrapper);

            if (candidates == null || candidates.isEmpty()) {
                long costMs = System.currentTimeMillis() - startMs;
                XxlJobHelper.log("evaluatePredictionAccuracyHandler 无待评估批次，耗时={}ms", costMs);
                XxlJobHelper.handleSuccess("无待评估的预测批次");
                return;
            }

            XxlJobHelper.log("找到{}个待评估批次，时间窗口: {} ~ {}",
                    candidates.size(), windowStart, windowEnd);

            for (HotspotPrediction candidate : candidates) {
                try {
                    String batch = candidate.getPredictionBatch();
                    predictionService.evaluatePredictionAccuracy(batch);
                    evaluatedBatches++;
                    XxlJobHelper.log("批次评估完成: batch={}", batch);
                } catch (Exception e) {
                    XxlJobHelper.log("批次评估异常: batch={}, error={}",
                            candidate.getPredictionBatch(), e.getMessage());
                }
            }

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("evaluatePredictionAccuracyHandler 执行成功，评估批次={}, 耗时={}ms",
                    evaluatedBatches, costMs);
            XxlJobHelper.handleSuccess(String.format("评估完成，处理%d个批次", evaluatedBatches));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("evaluatePredictionAccuracyHandler 执行异常", e);
            XxlJobHelper.log("evaluatePredictionAccuracyHandler 执行异常: {}, 已评估批次={}, 耗时={}ms",
                    e.getMessage(), evaluatedBatches, costMs);
            XxlJobHelper.handleFail("预测准确性评估失败: " + e.getMessage());
        }
    }

    private LocalDate[] calculateReportDates(String productType) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        switch (productType.toUpperCase()) {
            case "WEEKLY":
                startDate = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
                endDate = startDate.plusDays(6);
                break;
            case "MONTHLY":
                startDate = today.with(TemporalAdjusters.firstDayOfPreviousMonth());
                endDate = today.with(TemporalAdjusters.lastDayOfPreviousMonth());
                break;
            case "DAILY":
            default:
                startDate = today.minusDays(1);
                endDate = startDate;
                break;
        }
        return new LocalDate[]{startDate, endDate};
    }

    private String normalizeProductType(String productType) {
        if (productType == null) {
            return "REPORT";
        }
        switch (productType.toUpperCase()) {
            case "DAILY":
                return "DAILY_REPORT";
            case "WEEKLY":
                return "WEEKLY_REPORT";
            case "MONTHLY":
                return "MONTHLY_REPORT";
            default:
                return productType;
        }
    }

    private LocalDate parseLocalDate(String str) {
        if (!StringUtils.hasText(str)) {
            return null;
        }
        try {
            return LocalDate.parse(str.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
