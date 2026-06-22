package com.police.vision.control.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.result.Result;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.dto.intelligence.*;
import com.police.vision.control.entity.intelligence.*;
import com.police.vision.control.mapper.intelligence.*;
import com.police.vision.control.service.intelligence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
public class IntelligenceController {

    private final IntelligenceProductService productService;
    private final AnalysisModelService modelService;
    private final CaseClusterService clusterService;
    private final HotspotPredictionService predictionService;
    private final CrawlerService crawlerService;
    private final DeepSeekService deepSeekService;

    @Lazy
    private final AnalysisModelMapper analysisModelMapper;
    @Lazy
    private final CrawlerTaskMapper crawlerTaskMapper;
    @Lazy
    private final PublicOpinionMapper publicOpinionMapper;
    @Lazy
    private final CaseClusterMapper caseClusterMapper;
    @Lazy
    private final HotspotPredictionMapper hotspotPredictionMapper;
    @Lazy
    private final IntelligenceProductMapper productMapper;

    // ========== 1. 情报产品（治安态势报告） ==========

    @PostMapping("/report/generate")
    public Result<IntelligenceProduct> generateReport(@RequestBody ReportGenerateDTO dto) {
        return Result.success(productService.generateReport(dto));
    }

    @GetMapping("/report/{productId}")
    public Result<IntelligenceProduct> getReport(@PathVariable String productId) {
        return Result.success(productService.getProduct(productId));
    }

    @GetMapping("/report/page")
    public Result<PageResult<IntelligenceProduct>> pageReports(
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        IPage<IntelligenceProduct> page = productService.listProducts(productType, startDate, endDate, status, pageNum, pageSize);
        return Result.success(PageResult.of(page.getTotal(), page.getRecords(), pageNum, pageSize));
    }

    @PostMapping("/report/{productId}/regenerate")
    public Result<IntelligenceProduct> regenerateReport(@PathVariable String productId) {
        return Result.success(productService.regenerateReport(productId));
    }

    @GetMapping("/report/stats")
    public Result<Map<String, Object>> getReportStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(productService.getReportStats(startDate, endDate));
    }

    @GetMapping("/report/content/{productId}")
    public Result<Map<String, Object>> getReportContent(@PathVariable String productId) {
        IntelligenceProduct product = productService.getProduct(productId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", product.getContent());
        result.put("markdownContent", product.getMarkdownContent());
        result.put("summary", product.getSummary());
        result.put("title", product.getTitle());
        return Result.success(result);
    }

    // ========== 2. 研判模型管理 ==========

    @PostMapping("/model/save")
    public Result<AnalysisModel> saveModel(@RequestBody AnalysisModel model) {
        if (model.getId() == null) {
            return Result.success(modelService.createModel(model));
        } else {
            modelService.updateModel(model);
            return Result.success(modelService.getModel(model.getModelId()));
        }
    }

    @GetMapping("/model/{modelId}")
    public Result<AnalysisModel> getModel(@PathVariable String modelId) {
        return Result.success(modelService.getModel(modelId));
    }

    @GetMapping("/model/page")
    public Result<PageResult<AnalysisModel>> pageModels(
            @RequestParam(required = false) String modelType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        IPage<AnalysisModel> page = modelService.listModels(modelType, category, enabled, pageNum, pageSize);
        return Result.success(PageResult.of(page.getTotal(), page.getRecords(), pageNum, pageSize));
    }

    @DeleteMapping("/model/{modelId}")
    public Result<Void> deleteModel(@PathVariable String modelId) {
        modelService.deleteModel(modelId);
        return Result.success();
    }

    @PostMapping("/model/{modelId}/toggle")
    public Result<Void> toggleModel(
            @PathVariable String modelId,
            @RequestParam boolean enabled) {
        modelService.toggleEnabled(modelId, enabled);
        return Result.success();
    }

    @PostMapping("/model/{modelId}/execute")
    public Result<Map<String, Object>> executeModel(
            @PathVariable String modelId,
            @RequestBody(required = false) Map<String, Object> params) {
        return Result.success(modelService.executeModel(modelId, params));
    }

    @PostMapping("/model/init-defaults")
    public Result<Map<String, Object>> initDefaultModels() {
        modelService.initDefaultModels();
        LambdaQueryWrapper<AnalysisModel> wrapper = new LambdaQueryWrapper<>();
        Long count = analysisModelMapper.selectCount(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        return Result.success(result);
    }

    // ========== 3. 串并案分析 ==========

    @PostMapping("/cluster/analyze")
    public Result<Map<String, Object>> analyzeClusters(@RequestBody ClusterAnalyzeDTO dto) {
        return Result.success(clusterService.analyzeClusters(dto));
    }

    @GetMapping("/cluster/{clusterId}")
    public Result<Map<String, Object>> getClusterDetail(@PathVariable String clusterId) {
        return Result.success(clusterService.getClusterDetail(clusterId));
    }

    @GetMapping("/cluster/page")
    public Result<Map<String, Object>> pageClusters(
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<CaseCluster> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(caseType)) wrapper.eq(CaseCluster::getCaseType, caseType);
        if (StringUtils.hasText(areaCode)) wrapper.eq(CaseCluster::getAreaCode, areaCode);
        if (startTime != null) wrapper.ge(CaseCluster::getCreateTime, startTime);
        if (endTime != null) wrapper.le(CaseCluster::getCreateTime, endTime);
        if (alertLevel != null) wrapper.eq(CaseCluster::getAlertLevel, alertLevel);
        if (StringUtils.hasText(status)) {
            try {
                wrapper.eq(CaseCluster::getStatus, Integer.parseInt(status));
            } catch (NumberFormatException e) {
                wrapper.eq(CaseCluster::getStatusName, status);
            }
        }
        wrapper.orderByDesc(CaseCluster::getCreateTime);
        Page<CaseCluster> page = new Page<>(pageNum, pageSize);
        IPage<CaseCluster> result = caseClusterMapper.selectPage(page, wrapper);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("list", result.getRecords());
        map.put("total", result.getTotal());
        map.put("pageNum", pageNum);
        map.put("pageSize", pageSize);
        return Result.success(map);
    }

    @GetMapping("/cluster/similar/{caseId}")
    public Result<List<Map<String, Object>>> getSimilarCases(
            @PathVariable String caseId,
            @RequestParam(defaultValue = "0.75") double threshold,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(clusterService.searchSimilarCases(caseId, threshold, limit));
    }

    @PostMapping("/cluster/{clusterId}/handle")
    public Result<Void> handleCluster(
            @PathVariable String clusterId,
            @RequestParam(required = false) String statusName,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long officerId) {
        clusterService.markClusterHandled(clusterId, statusName, remark, officerId);
        return Result.success();
    }

    // ========== 4. 热点预测 ==========

    @PostMapping("/prediction/run")
    public Result<Map<String, Object>> runPrediction(@RequestBody PredictionDTO dto) {
        return Result.success(predictionService.runPrediction(dto));
    }

    @GetMapping("/prediction/{predictionId}")
    public Result<HotspotPrediction> getPrediction(@PathVariable String predictionId) {
        LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotspotPrediction::getPredictionId, predictionId);
        return Result.success(hotspotPredictionMapper.selectOne(wrapper));
    }

    @GetMapping("/prediction/high-risk")
    public Result<List<HotspotPrediction>> getHighRiskPredictions(
            @RequestParam(required = false) String batch,
            @RequestParam(defaultValue = "2") Integer minRiskLevel,
            @RequestParam(required = false) String areaCode) {
        return Result.success(predictionService.getHighRiskPredictions(batch, minRiskLevel, areaCode));
    }

    @GetMapping("/prediction/page")
    public Result<Map<String, Object>> pagePredictions(
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(areaCode)) wrapper.eq(HotspotPrediction::getAreaCode, areaCode);
        if (StringUtils.hasText(caseType)) wrapper.eq(HotspotPrediction::getCaseType, caseType);
        if (startTime != null && endTime != null) {
            wrapper.and(w -> w.between(HotspotPrediction::getPredictStartTime, startTime, endTime)
                    .or().between(HotspotPrediction::getCreateTime, startTime, endTime));
        }
        if (riskLevel != null) wrapper.eq(HotspotPrediction::getRiskLevel, riskLevel);
        wrapper.orderByDesc(HotspotPrediction::getCreateTime, HotspotPrediction::getRiskScore);
        Page<HotspotPrediction> page = new Page<>(pageNum, pageSize);
        IPage<HotspotPrediction> result = hotspotPredictionMapper.selectPage(page, wrapper);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("list", result.getRecords());
        map.put("total", result.getTotal());
        map.put("pageNum", pageNum);
        map.put("pageSize", pageSize);
        return Result.success(map);
    }

    @PostMapping("/prediction/{batch}/evaluate")
    public Result<Map<String, Object>> evaluatePrediction(@PathVariable String batch) {
        return Result.success(predictionService.evaluatePredictionAccuracy(batch));
    }

    @GetMapping("/prediction/latest-batch")
    public Result<Map<String, Object>> getLatestBatch(
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String areaCode) {
        LambdaQueryWrapper<HotspotPrediction> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(caseType)) wrapper.eq(HotspotPrediction::getCaseType, caseType);
        if (StringUtils.hasText(areaCode)) wrapper.eq(HotspotPrediction::getAreaCode, areaCode);
        wrapper.orderByDesc(HotspotPrediction::getCreateTime);
        wrapper.last("LIMIT 1");
        HotspotPrediction latest = hotspotPredictionMapper.selectOne(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        if (latest != null) {
            result.put("batch", latest.getPredictionBatch());
            result.put("caseType", latest.getCaseType());
            result.put("caseTypeName", latest.getCaseTypeName());
            result.put("areaCode", latest.getAreaCode());
            result.put("createTime", latest.getCreateTime());
            result.put("predictStartTime", latest.getPredictStartTime());
            result.put("predictEndTime", latest.getPredictEndTime());
            result.put("predictHours", latest.getPredictHours());
        }
        return Result.success(result);
    }

    // ========== 5. 舆情监测 ==========

    @PostMapping("/crawler/task/save")
    public Result<CrawlerTask> saveCrawlerTask(@RequestBody CrawlerTask task) {
        if (task.getId() == null) {
            String taskId = "CT" + SnowflakeIdUtil.nextIdStr();
            task.setTaskId(taskId);
            task.setTaskNo(SnowflakeIdUtil.nextIdStr());
            if (task.getId() == null) {
                task.setId(SnowflakeIdUtil.nextId());
            }
            crawlerTaskMapper.insert(task);
        } else {
            crawlerTaskMapper.updateById(task);
        }
        return Result.success(task);
    }

    @GetMapping("/crawler/task/{taskId}")
    public Result<CrawlerTask> getCrawlerTask(@PathVariable String taskId) {
        LambdaQueryWrapper<CrawlerTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CrawlerTask::getTaskId, taskId);
        return Result.success(crawlerTaskMapper.selectOne(wrapper));
    }

    @GetMapping("/crawler/task/page")
    public Result<Map<String, Object>> pageCrawlerTasks(
            @RequestParam(required = false) String siteName,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(required = false) Integer taskStatus,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<CrawlerTask> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(siteName)) wrapper.like(CrawlerTask::getSiteName, siteName);
        if (enabled != null) wrapper.eq(CrawlerTask::getEnabled, enabled);
        if (taskStatus != null) wrapper.eq(CrawlerTask::getTaskStatus, taskStatus);
        wrapper.orderByDesc(CrawlerTask::getCreateTime);
        Page<CrawlerTask> page = new Page<>(pageNum, pageSize);
        IPage<CrawlerTask> result = crawlerTaskMapper.selectPage(page, wrapper);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("list", result.getRecords());
        map.put("total", result.getTotal());
        map.put("pageNum", pageNum);
        map.put("pageSize", pageSize);
        return Result.success(map);
    }

    @DeleteMapping("/crawler/task/{taskId}")
    public Result<Void> deleteCrawlerTask(@PathVariable String taskId) {
        LambdaQueryWrapper<CrawlerTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CrawlerTask::getTaskId, taskId);
        crawlerTaskMapper.delete(wrapper);
        return Result.success();
    }

    @PostMapping("/crawler/task/{taskId}/execute")
    public Result<Map<String, Object>> executeCrawlerTask(
            @PathVariable String taskId,
            @RequestBody(required = false) CrawlTaskExecuteDTO overrides) {
        LambdaQueryWrapper<CrawlerTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CrawlerTask::getTaskId, taskId);
        CrawlerTask task = crawlerTaskMapper.selectOne(wrapper);
        if (task == null) {
            return Result.fail("爬虫任务不存在: " + taskId);
        }
        if (overrides != null) {
            if (overrides.getEntryUrls() != null) {
                task.setEntryUrls(String.join(",", overrides.getEntryUrls()));
            }
            if (overrides.getThreadCount() != null) {
                task.setThreadCount(overrides.getThreadCount());
            }
            if (overrides.getCrawlDepth() != null) {
                task.setCrawlDepth(overrides.getCrawlDepth());
            }
            if (overrides.getSleepMillis() != null) {
                task.setSleepMillis(overrides.getSleepMillis());
            }
        }
        return Result.success(crawlerService.executeTask(task));
    }

    @PostMapping("/crawler/url/test")
    public Result<Map<String, Object>> testCrawlUrl(
            @RequestParam String url,
            @RequestBody(required = false) CrawlerTask defaults) {
        if (defaults == null) {
            defaults = new CrawlerTask();
        }
        return Result.success(crawlerService.crawlSingleUrl(url, defaults));
    }

    @PostMapping("/crawler/sentiment/batch")
    public Result<Map<String, Object>> batchSentimentAnalysis(
            @RequestParam(defaultValue = "50") int batchSize) {
        return Result.success(crawlerService.batchSentimentAnalysis(batchSize));
    }

    @GetMapping("/opinion/page")
    public Result<Map<String, Object>> pageOpinions(
            @RequestParam(required = false) String sourceSite,
            @RequestParam(required = false) Integer sentimentLabel,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String areaCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<PublicOpinion> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(sourceSite)) wrapper.eq(PublicOpinion::getSourceSite, sourceSite);
        if (sentimentLabel != null) wrapper.eq(PublicOpinion::getSentimentLabel, sentimentLabel);
        if (alertLevel != null) wrapper.eq(PublicOpinion::getAlertLevel, alertLevel);
        if (startTime != null) wrapper.ge(PublicOpinion::getPublishTime, startTime);
        if (endTime != null) wrapper.le(PublicOpinion::getPublishTime, endTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PublicOpinion::getTitle, keyword)
                    .or().like(PublicOpinion::getContent, keyword)
                    .or().like(PublicOpinion::getKeywords, keyword));
        }
        if (StringUtils.hasText(areaCode)) wrapper.eq(PublicOpinion::getAreaCode, areaCode);
        wrapper.orderByDesc(PublicOpinion::getPublishTime);
        Page<PublicOpinion> page = new Page<>(pageNum, pageSize);
        IPage<PublicOpinion> result = publicOpinionMapper.selectPage(page, wrapper);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("list", result.getRecords());
        map.put("total", result.getTotal());
        map.put("pageNum", pageNum);
        map.put("pageSize", pageSize);
        return Result.success(map);
    }

    @GetMapping("/opinion/{opinionId}")
    public Result<PublicOpinion> getOpinion(@PathVariable String opinionId) {
        LambdaQueryWrapper<PublicOpinion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PublicOpinion::getOpinionId, opinionId);
        return Result.success(publicOpinionMapper.selectOne(wrapper));
    }

    @PostMapping("/opinion/{opinionId}/handle")
    public Result<Void> handleOpinion(
            @PathVariable String opinionId,
            @RequestParam(required = false) String statusName,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long officerId) {
        LambdaQueryWrapper<PublicOpinion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PublicOpinion::getOpinionId, opinionId);
        PublicOpinion opinion = publicOpinionMapper.selectOne(wrapper);
        if (opinion != null) {
            opinion.setStatus(2);
            opinion.setStatusName(StringUtils.hasText(statusName) ? statusName : "已处理");
            opinion.setHandleRemark(remark);
            opinion.setHandleOfficerId(officerId);
            opinion.setHandleTime(LocalDateTime.now());
            publicOpinionMapper.updateById(opinion);
        }
        return Result.success();
    }

    @DeleteMapping("/opinion/clean")
    public Result<Map<String, Object>> cleanOpinions(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit) {
        LambdaQueryWrapper<PublicOpinion> wrapper = new LambdaQueryWrapper<>();
        if (days != null) {
            wrapper.lt(PublicOpinion::getPublishTime, LocalDateTime.now().minusDays(days));
        }
        wrapper.eq(PublicOpinion::getStatus, 2);
        if (limit != null) {
            wrapper.last("LIMIT " + limit);
        }
        List<PublicOpinion> toDelete = publicOpinionMapper.selectList(wrapper);
        int cleaned = 0;
        if (toDelete != null && !toDelete.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (PublicOpinion o : toDelete) {
                if (o.getId() != null) ids.add(o.getId());
            }
            if (!ids.isEmpty()) {
                cleaned = publicOpinionMapper.deleteBatchIds(ids);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cleaned", cleaned);
        return Result.success(result);
    }

    // ========== 6. DeepSeek 直接调试接口 ==========

    @PostMapping("/llm/chat")
    public Result<Map<String, Object>> llmChat(@RequestBody Map<String, Object> body) {
        String systemPrompt = body.get("systemPrompt") != null ? body.get("systemPrompt").toString() : "";
        String userPrompt = body.get("userPrompt") != null ? body.get("userPrompt").toString() : "";
        String text = deepSeekService.chat(systemPrompt, userPrompt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        return Result.success(result);
    }

    @PostMapping("/llm/sentiment")
    public Result<SentimentResultDTO> llmSentiment(@RequestBody SentimentAnalysisDTO dto) {
        return Result.success(deepSeekService.analyzeSentiment(dto));
    }

    @PostMapping("/llm/insights")
    public Result<Map<String, Object>> llmInsights(@RequestBody Map<String, Object> body) {
        String text = body.get("text") != null ? body.get("text").toString() : "";
        String category = body.get("category") != null ? body.get("category").toString() : "general";
        return Result.success(deepSeekService.extractInsights(text, category));
    }
}
