package com.police.vision.control.job.intelligence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.police.vision.control.service.intelligence.CallbackDispatchService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackJobHandler {

    private final CallbackDispatchService callbackDispatchService;

    @XxlJob("callbackCaseScanHandler")
    public void callbackCaseScanHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("callbackCaseScanHandler 开始执行，param={}", param);

            int batchSize = 100;
            if (org.springframework.util.StringUtils.hasText(param)) {
                try {
                    String trimmed = param.trim();
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(trimmed);
                        if (json.containsKey("batchSize")) {
                            batchSize = json.getInteger("batchSize");
                        }
                    } else {
                        batchSize = Integer.parseInt(trimmed);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            int createdCount = callbackDispatchService.scanClosedCasesAndCreateTasks(batchSize);
            long costMs = System.currentTimeMillis() - startMs;

            XxlJobHelper.log("callbackCaseScanHandler 执行完成，自动创建{}个回访任务，耗时{}ms", createdCount, costMs);
            XxlJobHelper.handleSuccess(String.format("执行完成，创建%d个回访任务", createdCount));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("callbackCaseScanHandler 执行异常", e);
            XxlJobHelper.log("callbackCaseScanHandler 执行异常: {}, 耗时{}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    @XxlJob("callbackScanHandler")
    public void callbackScanHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("callbackScanHandler 开始执行，param={}", param);

            int batchSize = 50;
            if (StringUtils.hasText(param)) {
                try {
                    String trimmed = param.trim();
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        JSONObject json = JSON.parseObject(trimmed);
                        if (json.containsKey("batchSize")) {
                            batchSize = json.getInteger("batchSize");
                        }
                    } else {
                        batchSize = Integer.parseInt(trimmed);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            int successCount = callbackDispatchService.scanAndDispatchTasks(batchSize);
            long costMs = System.currentTimeMillis() - startMs;

            XxlJobHelper.log("callbackScanHandler 执行完成，成功发起{}个回访任务，耗时{}ms", successCount, costMs);
            XxlJobHelper.handleSuccess(String.format("执行完成，成功发起%d个回访任务", successCount));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("callbackScanHandler 执行异常", e);
            XxlJobHelper.log("callbackScanHandler 执行异常: {}, 耗时{}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    @XxlJob("callbackPollStatusHandler")
    public void callbackPollStatusHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("callbackPollStatusHandler 开始执行，param={}", param);

            int handled = callbackDispatchService.pollAndUpdateCallStatus();
            long costMs = System.currentTimeMillis() - startMs;

            XxlJobHelper.log("callbackPollStatusHandler 执行完成，处理{}个呼叫状态，耗时{}ms", handled, costMs);
            XxlJobHelper.handleSuccess(String.format("执行完成，更新%d个呼叫状态", handled));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("callbackPollStatusHandler 执行异常", e);
            XxlJobHelper.log("callbackPollStatusHandler 执行异常: {}, 耗时{}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    @XxlJob("callbackRetryHandler")
    public void callbackRetryHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("callbackRetryHandler 开始执行，param={}", param);

            int limit = 30;
            if (StringUtils.hasText(param)) {
                try {
                    String trimmed = param.trim();
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        JSONObject json = JSON.parseObject(trimmed);
                        if (json.containsKey("limit")) {
                            limit = json.getInteger("limit");
                        }
                    } else {
                        limit = Integer.parseInt(trimmed);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            int successCount = callbackDispatchService.retryFailedTasks(limit);
            long costMs = System.currentTimeMillis() - startMs;

            XxlJobHelper.log("callbackRetryHandler 执行完成，成功重试{}个失败任务，耗时{}ms", successCount, costMs);
            XxlJobHelper.handleSuccess(String.format("执行完成，重试成功%d个任务", successCount));
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("callbackRetryHandler 执行异常", e);
            XxlJobHelper.log("callbackRetryHandler 执行异常: {}, 耗时{}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("执行失败: " + e.getMessage());
        }
    }

    @XxlJob("callbackDailyStatHandler")
    public void callbackDailyStatHandler() {
        long startMs = System.currentTimeMillis();
        try {
            String param = XxlJobHelper.getJobParam();
            XxlJobHelper.log("callbackDailyStatHandler 开始执行，param={}", param);

            int days = 1;
            if (StringUtils.hasText(param)) {
                try {
                    days = Integer.parseInt(param.trim());
                } catch (NumberFormatException ignored) {
                }
            }

            com.police.vision.control.dto.intelligence.CallbackStatisticsDTO dto =
                    new com.police.vision.control.dto.intelligence.CallbackStatisticsDTO();
            dto.setDays(days);
            Map<String, Object> statistics = callbackDispatchService.getStatistics(dto);

            long costMs = System.currentTimeMillis() - startMs;
            XxlJobHelper.log("callbackDailyStatHandler 执行完成，昨日回访统计: {}, 耗时{}ms",
                    JSON.toJSONString(statistics), costMs);
            log.info("警情回访日报统计完成，统计{}天数据，耗时{}ms", days, costMs);
            XxlJobHelper.handleSuccess("日报统计完成");
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.error("callbackDailyStatHandler 执行异常", e);
            XxlJobHelper.log("callbackDailyStatHandler 执行异常: {}, 耗时{}ms", e.getMessage(), costMs);
            XxlJobHelper.handleFail("日报统计失败: " + e.getMessage());
        }
    }
}
