package com.police.vision.common.constant;

import java.util.HashMap;
import java.util.Map;

public class IntelligenceConstant {

    public static final String PRODUCT_TYPE_DAILY = "DAILY";
    public static final String PRODUCT_TYPE_WEEKLY = "WEEKLY";
    public static final String PRODUCT_TYPE_MONTHLY = "MONTHLY";

    public static final Map<String, String> PRODUCT_TYPE_NAME_MAP = new HashMap<>();

    static {
        PRODUCT_TYPE_NAME_MAP.put(PRODUCT_TYPE_DAILY, "日报");
        PRODUCT_TYPE_NAME_MAP.put(PRODUCT_TYPE_WEEKLY, "周报");
        PRODUCT_TYPE_NAME_MAP.put(PRODUCT_TYPE_MONTHLY, "月报");
    }

    public static final Integer SENTIMENT_NEGATIVE = 0;
    public static final Integer SENTIMENT_NEUTRAL = 1;
    public static final Integer SENTIMENT_POSITIVE = 2;

    public static final Map<Integer, String> SENTIMENT_NAME_MAP = new HashMap<>();

    static {
        SENTIMENT_NAME_MAP.put(SENTIMENT_NEGATIVE, "负面");
        SENTIMENT_NAME_MAP.put(SENTIMENT_NEUTRAL, "中性");
        SENTIMENT_NAME_MAP.put(SENTIMENT_POSITIVE, "正面");
    }

    public static final Integer RISK_LOW = 1;
    public static final Integer RISK_MEDIUM = 2;
    public static final Integer RISK_HIGH = 3;
    public static final Integer RISK_CRITICAL = 4;

    public static final Map<Integer, String> RISK_LEVEL_NAME_MAP = new HashMap<>();

    static {
        RISK_LEVEL_NAME_MAP.put(RISK_LOW, "低风险");
        RISK_LEVEL_NAME_MAP.put(RISK_MEDIUM, "中风险");
        RISK_LEVEL_NAME_MAP.put(RISK_HIGH, "高风险");
        RISK_LEVEL_NAME_MAP.put(RISK_CRITICAL, "严重风险");
    }

    public static final String MODEL_TYPE_REPORT = "REPORT";
    public static final String MODEL_TYPE_CLUSTER = "CLUSTER";
    public static final String MODEL_TYPE_PREDICTION = "PREDICTION";
    public static final String MODEL_TYPE_CRAWLER = "CRAWLER";
    public static final String MODEL_TYPE_SENTIMENT = "SENTIMENT";
    public static final String MODEL_TYPE_HEATMAP = "HEATMAP";

    public static final Map<String, String> MODEL_TYPE_NAME_MAP = new HashMap<>();

    static {
        MODEL_TYPE_NAME_MAP.put(MODEL_TYPE_REPORT, "情报报告");
        MODEL_TYPE_NAME_MAP.put(MODEL_TYPE_CLUSTER, "案件聚类");
        MODEL_TYPE_NAME_MAP.put(MODEL_TYPE_PREDICTION, "热点预测");
        MODEL_TYPE_NAME_MAP.put(MODEL_TYPE_CRAWLER, "舆情爬虫");
        MODEL_TYPE_NAME_MAP.put(MODEL_TYPE_SENTIMENT, "情感分析");
        MODEL_TYPE_NAME_MAP.put(MODEL_TYPE_HEATMAP, "热力图分析");
    }

    public static final Integer TASK_STATUS_PENDING = 0;
    public static final Integer TASK_STATUS_RUNNING = 1;
    public static final Integer TASK_STATUS_SUCCESS = 2;
    public static final Integer TASK_STATUS_FAILED = 3;
    public static final Integer TASK_STATUS_CANCELLED = 4;
    public static final Integer TASK_STATUS_PAUSED = 5;

    public static final Map<Integer, String> TASK_STATUS_NAME_MAP = new HashMap<>();

    static {
        TASK_STATUS_NAME_MAP.put(TASK_STATUS_PENDING, "待执行");
        TASK_STATUS_NAME_MAP.put(TASK_STATUS_RUNNING, "执行中");
        TASK_STATUS_NAME_MAP.put(TASK_STATUS_SUCCESS, "成功");
        TASK_STATUS_NAME_MAP.put(TASK_STATUS_FAILED, "失败");
        TASK_STATUS_NAME_MAP.put(TASK_STATUS_CANCELLED, "已取消");
        TASK_STATUS_NAME_MAP.put(TASK_STATUS_PAUSED, "已暂停");
    }
}
