package com.police.vision.control.service.intelligence;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.control.dto.intelligence.ClusterAnalyzeDTO;
import com.police.vision.control.dto.intelligence.PredictionDTO;
import com.police.vision.control.dto.intelligence.ReportGenerateDTO;
import com.police.vision.control.entity.intelligence.AnalysisModel;
import com.police.vision.control.entity.intelligence.CrawlerTask;
import com.police.vision.control.mapper.intelligence.AnalysisModelMapper;
import com.police.vision.control.mapper.intelligence.CrawlerTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisModelService {

    private final AnalysisModelMapper analysisModelMapper;
    private final CrawlerTaskMapper crawlerTaskMapper;

    @Autowired
    @Lazy
    private IntelligenceProductService intelligenceProductService;

    @Autowired
    @Lazy
    private CaseClusterService caseClusterService;

    @Autowired
    @Lazy
    private HotspotPredictionService hotspotPredictionService;

    @Autowired
    @Lazy
    private CrawlerService crawlerService;

    public IPage<AnalysisModel> listModels(String modelType, String category, Integer enabled, int pageNum, int pageSize) {
        LambdaQueryWrapper<AnalysisModel> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(modelType)) {
            wrapper.eq(AnalysisModel::getModelType, modelType);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(AnalysisModel::getCategory, category);
        }
        if (enabled != null) {
            wrapper.eq(AnalysisModel::getEnabled, enabled);
        }
        wrapper.orderByDesc(AnalysisModel::getCreateTime);
        Page<AnalysisModel> page = new Page<>(pageNum, pageSize);
        return analysisModelMapper.selectPage(page, wrapper);
    }

    public AnalysisModel getModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            throw new BusinessException("模型ID不能为空");
        }
        LambdaQueryWrapper<AnalysisModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisModel::getModelId, modelId);
        AnalysisModel model = analysisModelMapper.selectOne(wrapper);
        if (model == null) {
            throw new BusinessException("研判模型不存在: " + modelId);
        }
        return model;
    }

    @Transactional(rollbackFor = Exception.class)
    public AnalysisModel createModel(AnalysisModel model) {
        if (!StringUtils.hasText(model.getModelName())) {
            throw new BusinessException("模型名称不能为空");
        }
        if (!StringUtils.hasText(model.getModelType())) {
            throw new BusinessException("模型类型不能为空");
        }

        String snowflakeId = SnowflakeIdUtil.nextIdStr();
        String suffix = snowflakeId.length() > 8
                ? snowflakeId.substring(snowflakeId.length() - 8)
                : snowflakeId;
        String modelId = "AM" + suffix;
        String modelNo = snowflakeId;

        model.setModelId(modelId);
        model.setModelNo(modelNo);
        if (model.getVersion() == null) {
            model.setVersion(1);
        }
        if (model.getEnabled() == null) {
            model.setEnabled(0);
        }
        fillModelTypeName(model);
        if (model.getId() == null) {
            model.setId(SnowflakeIdUtil.nextId());
        }
        analysisModelMapper.insert(model);
        log.info("创建研判模型: modelId={}, modelName={}, modelType={}",
                model.getModelId(), model.getModelName(), model.getModelType());
        return model;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateModel(AnalysisModel model) {
        if (!StringUtils.hasText(model.getModelId())) {
            throw new BusinessException("模型ID不能为空");
        }
        LambdaQueryWrapper<AnalysisModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisModel::getModelId, model.getModelId());
        AnalysisModel exist = analysisModelMapper.selectOne(wrapper);
        if (exist == null) {
            throw new BusinessException("研判模型不存在: " + model.getModelId());
        }
        fillModelTypeName(model);
        if (model.getVersion() == null) {
            model.setVersion(exist.getVersion());
        }
        model.setVersion(model.getVersion() + 1);
        analysisModelMapper.updateById(model);
        log.info("更新研判模型: modelId={}, version={}", model.getModelId(), model.getVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            throw new BusinessException("模型ID不能为空");
        }
        LambdaQueryWrapper<AnalysisModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisModel::getModelId, modelId);
        analysisModelMapper.delete(wrapper);
        log.info("删除研判模型: modelId={}", modelId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeModel(String modelId, Map<String, Object> params) {
        AnalysisModel model = getModel(modelId);
        LocalDateTime startTime = LocalDateTime.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelId", modelId);
        result.put("modelName", model.getModelName());
        result.put("modelType", model.getModelType());
        result.put("startTime", startTime);

        try {
            Object executeResult = dispatchExecute(model, params);
            result.put("success", true);
            result.put("result", executeResult);
            model.setExecuteStatus(1);
            model.setExecuteStatusName("执行成功");
            model.setLastExecuteResult("success");
        } catch (Exception e) {
            log.error("执行研判模型失败: modelId={}, error={}", modelId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            model.setExecuteStatus(2);
            model.setExecuteStatusName("执行失败");
            model.setLastExecuteResult("failed: " + e.getMessage());
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException("模型执行失败: " + e.getMessage());
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            model.setLastExecuteTime(endTime);
            analysisModelMapper.updateById(model);
            result.put("endTime", endTime);
            result.put("durationSeconds", java.time.Duration.between(startTime, endTime).getSeconds());
        }

        return result;
    }

    private Object dispatchExecute(AnalysisModel model, Map<String, Object> params) {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        String modelType = model.getModelType();
        switch (modelType) {
            case "REPORT": {
                ReportGenerateDTO dto = buildReportGenerateDTO(model, params);
                return intelligenceProductService.generateReport(dto);
            }
            case "CLUSTER": {
                ClusterAnalyzeDTO dto = buildClusterAnalyzeDTO(model, params);
                return caseClusterService.analyzeClusters(dto);
            }
            case "PREDICTION": {
                PredictionDTO dto = buildPredictionDTO(model, params);
                return hotspotPredictionService.runPrediction(dto);
            }
            case "CRAWLER": {
                LambdaQueryWrapper<CrawlerTask> taskWrapper = new LambdaQueryWrapper<>();
                taskWrapper.eq(CrawlerTask::getAnalysisModelId, model.getModelId())
                        .or()
                        .eq(CrawlerTask::getEnabled, 1);
                List<CrawlerTask> tasks = crawlerTaskMapper.selectList(taskWrapper);
                if (tasks == null || tasks.isEmpty()) {
                    throw new BusinessException("未找到关联的爬虫任务: modelId=" + model.getModelId());
                }
                Map<String, Object> crawlResults = new LinkedHashMap<>();
                for (CrawlerTask task : tasks) {
                    try {
                        Map<String, Object> taskResult = crawlerService.executeTask(task);
                        crawlResults.put(task.getTaskId(), taskResult);
                    } catch (Exception e) {
                        log.error("爬虫任务执行失败: taskId={}, error={}", task.getTaskId(), e.getMessage());
                        crawlResults.put(task.getTaskId(), Collections.singletonMap("error", e.getMessage()));
                    }
                }
                return crawlResults;
            }
            case "SENTIMENT": {
                int batchSize = params != null && params.get("batchSize") != null
                        ? Integer.parseInt(params.get("batchSize").toString())
                        : 100;
                return crawlerService.batchSentimentAnalysis(batchSize);
            }
            default:
                throw new BusinessException("不支持的模型类型: " + modelType);
        }
    }

    private ReportGenerateDTO buildReportGenerateDTO(AnalysisModel model, Map<String, Object> params) {
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setModelId(model.getModelId());
        dto.setProductType(model.getModelType());
        if (params != null) {
            if (params.get("reportStartDate") != null) {
                dto.setReportStartDate(parseDate(params.get("reportStartDate")));
            }
            if (params.get("reportEndDate") != null) {
                dto.setReportEndDate(parseDate(params.get("reportEndDate")));
            }
            if (params.get("areaCode") != null) {
                dto.setAreaCode(params.get("areaCode").toString());
            }
            if (params.get("includeAlarm") != null) {
                dto.setIncludeAlarm(Boolean.parseBoolean(params.get("includeAlarm").toString()));
            }
            if (params.get("includeCase") != null) {
                dto.setIncludeCase(Boolean.parseBoolean(params.get("includeCase").toString()));
            }
            if (params.get("includePerson") != null) {
                dto.setIncludePerson(Boolean.parseBoolean(params.get("includePerson").toString()));
            }
            if (params.get("includeVehicle") != null) {
                dto.setIncludeVehicle(Boolean.parseBoolean(params.get("includeVehicle").toString()));
            }
            if (params.get("includeOpinion") != null) {
                dto.setIncludeOpinion(Boolean.parseBoolean(params.get("includeOpinion").toString()));
            }
        }
        return dto;
    }

    private ClusterAnalyzeDTO buildClusterAnalyzeDTO(AnalysisModel model, Map<String, Object> params) {
        ClusterAnalyzeDTO dto = new ClusterAnalyzeDTO();
        if (params != null) {
            if (params.get("timeWindowHours") != null) {
                dto.setTimeWindowHours(Integer.parseInt(params.get("timeWindowHours").toString()));
            }
            if (params.get("similarityThreshold") != null) {
                dto.setSimilarityThreshold(new BigDecimal(params.get("similarityThreshold").toString()));
            }
            if (params.get("minClusterSize") != null) {
                dto.setMinClusterSize(Integer.parseInt(params.get("minClusterSize").toString()));
            }
            if (params.get("caseType") != null) {
                dto.setCaseType(params.get("caseType").toString());
            }
            if (params.get("areaCode") != null) {
                dto.setAreaCode(params.get("areaCode").toString());
            }
        }
        return dto;
    }

    private PredictionDTO buildPredictionDTO(AnalysisModel model, Map<String, Object> params) {
        PredictionDTO dto = new PredictionDTO();
        if (params != null) {
            if (params.get("predictHours") != null) {
                dto.setPredictHours(Integer.parseInt(params.get("predictHours").toString()));
            }
            if (params.get("historyDays") != null) {
                dto.setHistoryDays(Integer.parseInt(params.get("historyDays").toString()));
            }
            if (params.get("gridSizeMeters") != null) {
                dto.setGridSizeMeters(Integer.parseInt(params.get("gridSizeMeters").toString()));
            }
            if (params.get("caseType") != null) {
                dto.setCaseType(params.get("caseType").toString());
            }
            if (params.get("areaCode") != null) {
                dto.setAreaCode(params.get("areaCode").toString());
            }
        }
        return dto;
    }

    private java.time.LocalDate parseDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof java.time.LocalDate) return (java.time.LocalDate) obj;
        try {
            return java.time.LocalDate.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleEnabled(String modelId, boolean enabled) {
        AnalysisModel model = getModel(modelId);
        model.setEnabled(enabled ? 1 : 0);
        analysisModelMapper.updateById(model);
        log.info("切换研判模型启用状态: modelId={}, enabled={}", modelId, enabled);
    }

    @PostConstruct
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultModels() {
        try {
            log.info("开始初始化预置研判模型...");
            initModel("每日治安态势报告", "REPORT", "deepseek_report",
                    "0 0 6 * * ?", "reportGenerateHandler", "治安态势", "统计报告", "每日自动生成治安态势分析报告");
            initModel("每周治安态势报告", "REPORT", "deepseek_report_weekly",
                    "0 0 7 ? * MON", "reportGenerateHandler", "治安态势", "统计报告", "每周一自动生成治安态势分析周报");
            initModel("每月治安态势报告", "REPORT", "deepseek_report_monthly",
                    "0 0 8 1 * ?", "reportGenerateHandler", "治安态势", "统计报告", "每月1号自动生成治安态势分析月报");
            initModel("案件串并分析", "CLUSTER", "es_case_cluster",
                    "0 0 */4 * * ?", "caseClusterHandler", "案件分析", "串并聚类", "基于ES相似度聚类分析，自动发现串并案件");
            initModel("24小时热点预测", "PREDICTION", "sarima_hotspot_24h",
                    "0 0 5 * * ?", "hotspotPredictionHandler", "预测预警", "时空预测", "基于SARIMA模型预测未来24小时治安热点区域");
            initModel("本地社交媒体舆情爬取", "CRAWLER", "webmagic_local_forum",
                    "0 30 * * * ?", "crawlerHandler", "舆情情报", "网络爬虫", "定时爬取本地论坛、社交媒体舆情数据");
            log.info("预置研判模型初始化完成");
        } catch (Exception e) {
            log.error("预置研判模型初始化失败", e);
        }
    }

    private void initModel(String modelName, String modelType, String modelCode,
                           String cronExpression, String jobHandler,
                           String category, String algorithm, String description) {
        LambdaQueryWrapper<AnalysisModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisModel::getModelCode, modelCode);
        Long count = analysisModelMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            log.debug("预置模型已存在，跳过: modelCode={}, modelName={}", modelCode, modelName);
            return;
        }
        AnalysisModel model = new AnalysisModel();
        model.setModelName(modelName);
        model.setModelCode(modelCode);
        model.setModelType(modelType);
        model.setCategory(category);
        model.setAlgorithm(algorithm);
        model.setCronExpression(cronExpression);
        model.setJobHandler(jobHandler);
        model.setDescription(description);
        model.setEnabled(0);
        model.setExecuteStatus(0);
        model.setExecuteStatusName("未执行");
        model.setVersion(1);
        createModel(model);
        log.info("初始化预置研判模型: modelCode={}, modelName={}, modelType={}",
                modelCode, modelName, modelType);
    }

    private void fillModelTypeName(AnalysisModel model) {
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("REPORT", "情报报告");
        typeMap.put("CLUSTER", "串并聚类");
        typeMap.put("PREDICTION", "预测预警");
        typeMap.put("CRAWLER", "数据采集");
        typeMap.put("SENTIMENT", "情感分析");
        if (model.getModelType() != null) {
            model.setModelTypeName(typeMap.getOrDefault(model.getModelType(), "其他"));
        }
    }
}
