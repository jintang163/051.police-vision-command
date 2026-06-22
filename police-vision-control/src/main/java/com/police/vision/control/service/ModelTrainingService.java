package com.police.vision.control.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.police.vision.control.client.LstmTrajectoryClient;
import com.police.vision.control.config.ControlAiConfig;
import com.police.vision.control.entity.ModelTrainingJob;
import com.police.vision.control.entity.PersonTrackPoint;
import com.police.vision.control.mapper.ModelTrainingJobMapper;
import com.police.vision.control.mapper.PersonTrackPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTrainingService {

    private final ModelTrainingJobMapper trainingJobMapper;
    private final PersonTrackPointMapper trackPointMapper;
    private final LstmTrajectoryClient lstmClient;
    private final ControlAiConfig aiConfig;

    private static final String DEFAULT_MODEL_TYPE = "LSTM";

    public Map<String, Object> getLatestModelInfo(String modelType) {
        ModelTrainingJob job = trainingJobMapper.selectLatestDeployedModel(
                modelType != null ? modelType : DEFAULT_MODEL_TYPE);
        Map<String, Object> result = new LinkedHashMap<>();
        if (job == null) {
            result.put("modelVersion", aiConfig.getLstm().getDefaultModelVersion());
            result.put("accuracyEstimate", 0.75);
            result.put("deployed", false);
            result.put("fallback", true);
            result.put("message", "未找到已部署的模型，使用默认配置");
            return result;
        }
        result.put("modelVersion", job.getModelVersion());
        result.put("accuracyEstimate", job.getAccuracyEstimate() != null ? job.getAccuracyEstimate() : 0.75);
        result.put("evalMae", job.getEvalMae());
        result.put("evalRmse", job.getEvalRmse());
        result.put("evalAccuracyTop1", job.getEvalAccuracyTop1());
        result.put("evalAccuracyTop3", job.getEvalAccuracyTop3());
        result.put("evalAccuracy30m", job.getEvalAccuracy30m());
        result.put("evalAccuracy50m", job.getEvalAccuracy50m());
        result.put("evalAccuracy100m", job.getEvalAccuracy100m());
        result.put("evalReport", job.getEvalReport() != null ? JSON.parse(job.getEvalReport()) : null);
        result.put("deployed", true);
        result.put("deployTime", job.getDeployTime());
        result.put("trainSampleCount", job.getTrainSampleCount());
        result.put("evalSampleCount", job.getEvalSampleCount());
        result.put("trainStartDate", job.getTrainStartDate());
        result.put("trainEndDate", job.getTrainEndDate());
        result.put("fallback", false);
        return result;
    }

    public Double getLatestAccuracyEstimate(String modelType) {
        Double accuracy = trainingJobMapper.getLatestAccuracyEstimate(
                modelType != null ? modelType : DEFAULT_MODEL_TYPE);
        if (accuracy == null || accuracy <= 0 || accuracy > 1) {
            return 0.75;
        }
        return accuracy;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createTrainingJob(String modelType, int historyDays, String triggerMode) {
        ControlAiConfig.LstmTrajectoryConfig cfg = aiConfig.getLstm();
        String modelTypeFinal = modelType != null ? modelType : DEFAULT_MODEL_TYPE;
        int days = historyDays > 0 ? historyDays : cfg.getHistoryDays();

        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(days);

        ModelTrainingJob job = new ModelTrainingJob();
        job.setJobId("TRAIN-" + System.currentTimeMillis());
        job.setModelType(modelTypeFinal);
        job.setModelVersion(generateModelVersion(modelTypeFinal));
        job.setStatus("PENDING");
        job.setStatusName("待训练");
        job.setTrainStartDate(startDate);
        job.setTrainEndDate(endDate);
        job.setTrainParams(JSON.toJSONString(Map.of(
                "historyDays", days,
                "seq_len", 50,
                "pred_len", 3,
                "hidden_size", 64,
                "num_layers", 2,
                "batch_size", 64,
                "epochs", 50,
                "learning_rate", 0.001
        )));
        job.setTriggerMode(triggerMode != null ? triggerMode : "MANUAL");
        job.setCreatedBy("system");
        trainingJobMapper.insert(job);

        asyncTrainAndEvaluate(job);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getJobId());
        result.put("modelVersion", job.getModelVersion());
        result.put("trainStartDate", startDate);
        result.put("trainEndDate", endDate);
        result.put("historyDays", days);
        result.put("status", "PENDING");
        result.put("message", "训练任务已创建，正在后台异步执行");
        return result;
    }

    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void asyncTrainAndEvaluate(ModelTrainingJob job) {
        try {
            updateJobStatus(job.getJobId(), "TRAINING", "训练中", null, null);
            log.info("开始模型训练：jobId={}, modelVersion={}", job.getJobId(), job.getModelVersion());

            List<PersonTrackPoint> trainPoints = trackPointMapper.selectRecentByPersonId(
                    "ALL", (int) ChronoUnit.DAYS.between(job.getTrainStartDate(), job.getTrainEndDate()) + 1, 100000);
            job.setTrainSampleCount((long) trainPoints.size());

            Map<String, Object> trainResult = lstmClient.train("ALL", trainPoints, job.getModelVersion());
            job.setTrainLoss(getDouble(trainResult, "train_loss"));

            updateJobStatus(job.getJobId(), "EVALUATING", "评估中", null, null);
            log.info("开始模型评估：jobId={}", job.getJobId());

            List<PersonTrackPoint> evalPoints = trackPointMapper.selectRecentByPersonId(
                    "ALL", 7, 20000);
            job.setEvalSampleCount((long) evalPoints.size());

            Map<String, Object> evalResult = lstmClient.evaluate("ALL", evalPoints, job.getModelVersion());
            job.setEvalMae(getDouble(evalResult, "eval_mae"));
            job.setEvalRmse(getDouble(evalResult, "eval_rmse"));
            job.setEvalAccuracyTop1(getDouble(evalResult, "eval_accuracy_top1"));
            job.setEvalAccuracyTop3(getDouble(evalResult, "eval_accuracy_top3"));
            job.setEvalAccuracy30m(getDouble(evalResult, "eval_accuracy_30m"));
            job.setEvalAccuracy50m(getDouble(evalResult, "eval_accuracy_50m"));
            job.setEvalAccuracy100m(getDouble(evalResult, "eval_accuracy_100m"));
            job.setAccuracyEstimate(getDouble(evalResult, "accuracy_estimate"));
            job.setEvalReport(evalResult.get("eval_report") != null ?
                    JSON.toJSONString(evalResult.get("eval_report")) : null);
            job.setModelPath((String) trainResult.get("model_path"));
            job.setEndTime(LocalDateTime.now());
            job.setDurationSeconds(ChronoUnit.SECONDS.between(job.getStartTime(), job.getEndTime()));

            if (job.getAccuracyEstimate() != null && job.getAccuracyEstimate() >= 0.65) {
                trainingJobMapper.updateDeployedStatus(job.getModelVersion(), 0);
                job.setDeployed(1);
                job.setDeployTime(LocalDateTime.now());
                updateJobStatus(job.getJobId(), "SUCCESS", "训练成功且已部署",
                        "模型准确率 " + String.format("%.1f%%", job.getAccuracyEstimate() * 100) +
                                " 超过阈值65%，已自动部署为生产版本", null);
                log.info("模型训练成功并部署：jobId={}, accuracy={}%",
                        job.getJobId(), String.format("%.1f", job.getAccuracyEstimate() * 100));
            } else {
                updateJobStatus(job.getJobId(), "SUCCESS", "训练成功但未部署",
                        "模型准确率 " + (job.getAccuracyEstimate() != null ?
                                String.format("%.1f%%", job.getAccuracyEstimate() * 100) : "未知") +
                                " 未达部署阈值65%", null);
                log.info("模型训练成功但未部署：jobId={}, accuracy={}%",
                        job.getJobId(), job.getAccuracyEstimate() != null ?
                                String.format("%.1f", job.getAccuracyEstimate() * 100) : "未知");
            }
            trainingJobMapper.updateById(job);

        } catch (Exception e) {
            log.error("模型训练失败：jobId={}", job.getJobId(), e);
            updateJobStatus(job.getJobId(), "FAILED", "训练失败", e.getMessage(), null);
        }
    }

    private void updateJobStatus(String jobId, String status, String statusName, String errorMessage, String remark) {
        ModelTrainingJob update = new ModelTrainingJob();
        LambdaQueryWrapper<ModelTrainingJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelTrainingJob::getJobId, jobId);

        if ("TRAINING".equals(status)) {
            update.setStartTime(LocalDateTime.now());
        }
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            update.setEndTime(LocalDateTime.now());
        }
        update.setStatus(status);
        update.setStatusName(statusName);
        update.setErrorMessage(errorMessage);
        trainingJobMapper.update(update, wrapper);
    }

    private String generateModelVersion(String modelType) {
        String dateStr = LocalDate.now().toString().replace("-", "");
        LambdaQueryWrapper<ModelTrainingJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(ModelTrainingJob::getModelVersion,
                modelType.toLowerCase() + "-v2." + dateStr + "-");
        long count = trainingJobMapper.selectCount(wrapper);
        return modelType.toLowerCase() + "-v2." + dateStr + "-" + (count + 1);
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Double) return (Double) v;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    public List<ModelTrainingJob> listTrainingJobs(String modelType, int pageNum, int pageSize) {
        LambdaQueryWrapper<ModelTrainingJob> wrapper = new LambdaQueryWrapper<>();
        if (modelType != null && !modelType.isEmpty()) {
            wrapper.eq(ModelTrainingJob::getModelType, modelType);
        }
        wrapper.orderByDesc(ModelTrainingJob::getCreatedAt);
        wrapper.last("limit " + (pageNum - 1) * pageSize + "," + pageSize);
        return trainingJobMapper.selectList(wrapper);
    }

    public long countTrainingJobs(String modelType) {
        LambdaQueryWrapper<ModelTrainingJob> wrapper = new LambdaQueryWrapper<>();
        if (modelType != null && !modelType.isEmpty()) {
            wrapper.eq(ModelTrainingJob::getModelType, modelType);
        }
        return trainingJobMapper.selectCount(wrapper);
    }

    public ModelTrainingJob getTrainingJob(String jobId) {
        return trainingJobMapper.selectByJobId(jobId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deployModel(String modelVersion) {
        Map<String, Object> result = new LinkedHashMap<>();
        trainingJobMapper.updateDeployedStatus(modelVersion, 0);
        int updated = trainingJobMapper.updateDeployedStatus(modelVersion, 1);
        if (updated > 0) {
            result.put("deployed", true);
            result.put("modelVersion", modelVersion);
            result.put("message", "模型已部署为生产版本");
            log.info("模型手动部署成功：modelVersion={}", modelVersion);
        } else {
            result.put("deployed", false);
            result.put("modelVersion", modelVersion);
            result.put("message", "未找到对应模型版本");
        }
        return result;
    }
}
