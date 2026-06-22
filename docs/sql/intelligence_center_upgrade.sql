-- ============================================================================
-- 情报研判中心升级脚本
-- 功能：1)情报产品 2)研判模型 3)舆情爬虫 4)舆情数据 5)串并案聚类 6)热点预测
-- 创建：基于 police-vision-control 模块
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. intelligence_product（情报产品表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS intelligence_product;
CREATE TABLE intelligence_product (
    id                      BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    product_id              VARCHAR(64)     NOT NULL COMMENT '情报产品唯一ID',
    product_no              VARCHAR(64)     DEFAULT NULL COMMENT '情报产品编号',
    product_type            VARCHAR(32)     DEFAULT NULL COMMENT '产品类型(DAILY-日报/WEEKLY-周报/MONTHLY-月报/SPECIAL-专报)',
    product_type_name       VARCHAR(64)     DEFAULT NULL COMMENT '产品类型名称',
    title                   VARCHAR(255)    DEFAULT NULL COMMENT '标题',
    summary                 VARCHAR(2000)   DEFAULT NULL COMMENT '摘要',
    content                 TEXT            DEFAULT NULL COMMENT '内容(HTML)',
    markdown_content        MEDIUMTEXT      DEFAULT NULL COMMENT '内容(Markdown)',
    report_date             DATE            DEFAULT NULL COMMENT '报告日期',
    report_start_date       DATE            DEFAULT NULL COMMENT '统计开始日期',
    report_end_date         DATE            DEFAULT NULL COMMENT '统计结束日期',
    alarm_count             INT             DEFAULT 0 COMMENT '预警数量',
    case_count              INT             DEFAULT 0 COMMENT '案件数量',
    person_count            INT             DEFAULT 0 COMMENT '人员数量',
    vehicle_count           INT             DEFAULT 0 COMMENT '车辆数量',
    opinion_count           INT             DEFAULT 0 COMMENT '舆情数量',
    hotspots                JSON            DEFAULT NULL COMMENT '热点信息(JSON)',
    trends                  JSON            DEFAULT NULL COMMENT '趋势数据(JSON)',
    suggestions             JSON            DEFAULT NULL COMMENT '处置建议(JSON)',
    model_id                VARCHAR(64)     DEFAULT NULL COMMENT '关联研判模型ID',
    model_name              VARCHAR(128)    DEFAULT NULL COMMENT '关联研判模型名称',
    generate_params         JSON            DEFAULT NULL COMMENT '生成参数(JSON)',
    status                  TINYINT         DEFAULT 0 COMMENT '状态(0-草稿/1-生成中/2-已生成/3-已发布/4-已归档)',
    status_name             VARCHAR(32)     DEFAULT '草稿' COMMENT '状态名称',
    generate_time           DATETIME        DEFAULT NULL COMMENT '生成时间',
    generate_seconds        BIGINT          DEFAULT NULL COMMENT '生成耗时(秒)',
    police_station_code     VARCHAR(32)     DEFAULT NULL COMMENT '管辖派出所编码',
    police_station_name     VARCHAR(128)    DEFAULT NULL COMMENT '管辖派出所名称',
    create_by               BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by               BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT         DEFAULT 0 COMMENT '是否删除(0-否/1-是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id (product_id),
    KEY idx_product_type (product_type),
    KEY idx_report_date (report_date),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情报产品表';

-- ---------------------------------------------------------------------------
-- 2. analysis_model（研判模型表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS analysis_model;
CREATE TABLE analysis_model (
    id                      BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    model_id                VARCHAR(64)     NOT NULL COMMENT '模型唯一ID',
    model_no                VARCHAR(64)     DEFAULT NULL COMMENT '模型编号',
    model_name              VARCHAR(128)    NOT NULL COMMENT '模型名称',
    model_code              VARCHAR(64)     NOT NULL COMMENT '模型编码(唯一标识)',
    model_type              VARCHAR(32)     DEFAULT NULL COMMENT '模型类型(SENTIMENT-舆情/CLUSTER-串并案/PREDICTION-热点预测/REPORT-情报报告)',
    model_type_name         VARCHAR(64)     DEFAULT NULL COMMENT '模型类型名称',
    description             VARCHAR(500)    DEFAULT NULL COMMENT '模型描述',
    category                VARCHAR(64)     DEFAULT NULL COMMENT '模型分类',
    algorithm               VARCHAR(128)    DEFAULT NULL COMMENT '核心算法',
    param_config            JSON            DEFAULT NULL COMMENT '参数配置(JSON)',
    data_source             VARCHAR(255)    DEFAULT NULL COMMENT '数据源',
    cron_expression         VARCHAR(128)    DEFAULT NULL COMMENT '调度Cron表达式',
    job_handler             VARCHAR(128)    DEFAULT NULL COMMENT 'XXL-Job任务处理器',
    enabled                 TINYINT         DEFAULT 0 COMMENT '是否启用(0-停用/1-启用)',
    execute_status          TINYINT         DEFAULT 0 COMMENT '执行状态(0-空闲/1-运行中/2-成功/3-失败)',
    execute_status_name     VARCHAR(32)     DEFAULT '空闲' COMMENT '执行状态名称',
    last_execute_time       DATETIME        DEFAULT NULL COMMENT '最后执行时间',
    last_execute_result     TEXT            DEFAULT NULL COMMENT '最后执行结果',
    version                 INT             DEFAULT 1 COMMENT '版本号',
    police_station_code     VARCHAR(32)     DEFAULT NULL COMMENT '管辖派出所编码',
    police_station_name     VARCHAR(128)    DEFAULT NULL COMMENT '管辖派出所名称',
    create_by               BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by               BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT         DEFAULT 0 COMMENT '是否删除(0-否/1-是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_id (model_id),
    UNIQUE KEY uk_model_code (model_code),
    KEY idx_model_type (model_type),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='研判模型表';

-- ---------------------------------------------------------------------------
-- 3. crawler_task（舆情爬虫任务表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS crawler_task;
CREATE TABLE crawler_task (
    id                      BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    task_id                 VARCHAR(64)     NOT NULL COMMENT '任务唯一ID',
    task_no                 VARCHAR(64)     DEFAULT NULL COMMENT '任务编号',
    task_name               VARCHAR(255)    NOT NULL COMMENT '任务名称',
    site_name               VARCHAR(128)    DEFAULT NULL COMMENT '目标网站名称',
    site_url                VARCHAR(500)    DEFAULT NULL COMMENT '目标网站URL',
    entry_urls              TEXT            DEFAULT NULL COMMENT '入口URL列表(逗号分隔)',
    allow_domains           VARCHAR(500)    DEFAULT NULL COMMENT '允许的域名(逗号分隔)',
    url_pattern             VARCHAR(500)    DEFAULT NULL COMMENT 'URL匹配正则',
    content_selector        VARCHAR(500)    DEFAULT NULL COMMENT '内容CSS选择器',
    title_selector          VARCHAR(500)    DEFAULT NULL COMMENT '标题CSS选择器',
    author_selector         VARCHAR(255)    DEFAULT NULL COMMENT '作者CSS选择器',
    publish_time_selector   VARCHAR(255)    DEFAULT NULL COMMENT '发布时间CSS选择器',
    keywords                VARCHAR(500)    DEFAULT NULL COMMENT '关键词过滤(逗号分隔)',
    area_filter             VARCHAR(255)    DEFAULT NULL COMMENT '地域过滤',
    crawl_depth             INT             DEFAULT 3 COMMENT '爬取深度',
    thread_count            INT             DEFAULT 5 COMMENT '线程数',
    sleep_millis            INT             DEFAULT 1000 COMMENT '请求间隔(毫秒)',
    enabled                 TINYINT         DEFAULT 1 COMMENT '是否启用(0-停用/1-启用)',
    task_status             TINYINT         DEFAULT 0 COMMENT '任务状态(0-空闲/1-运行中/2-已暂停/3-异常)',
    task_status_name        VARCHAR(32)     DEFAULT '空闲' COMMENT '任务状态名称',
    last_start_time         DATETIME        DEFAULT NULL COMMENT '最后开始时间',
    last_end_time           DATETIME        DEFAULT NULL COMMENT '最后结束时间',
    last_duration_seconds   BIGINT          DEFAULT NULL COMMENT '最后执行耗时(秒)',
    last_crawl_count        INT             DEFAULT NULL COMMENT '最后爬取总数',
    last_new_count          INT             DEFAULT NULL COMMENT '最后新增数',
    total_crawl_count       INT             DEFAULT 0 COMMENT '累计爬取总数',
    cron_expression         VARCHAR(128)    DEFAULT NULL COMMENT '调度Cron表达式',
    police_station_code     VARCHAR(32)     DEFAULT NULL COMMENT '管辖派出所编码',
    police_station_name     VARCHAR(128)    DEFAULT NULL COMMENT '管辖派出所名称',
    create_by               BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by               BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT         DEFAULT 0 COMMENT '是否删除(0-否/1-是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_enabled (enabled),
    KEY idx_site_name (site_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舆情爬虫任务表';

-- ---------------------------------------------------------------------------
-- 4. public_opinion（舆情数据表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS public_opinion;
CREATE TABLE public_opinion (
    id                      BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    opinion_id              VARCHAR(64)     NOT NULL COMMENT '舆情唯一ID',
    opinion_no              VARCHAR(64)     DEFAULT NULL COMMENT '舆情编号',
    source_site             VARCHAR(64)     DEFAULT NULL COMMENT '来源站点编码',
    source_site_name        VARCHAR(128)    DEFAULT NULL COMMENT '来源站点名称',
    source_url              VARCHAR(1000)   NOT NULL COMMENT '来源URL',
    title                   VARCHAR(500)    DEFAULT NULL COMMENT '标题',
    content                 MEDIUMTEXT      DEFAULT NULL COMMENT '正文内容',
    author                  VARCHAR(128)    DEFAULT NULL COMMENT '作者',
    author_id               VARCHAR(128)    DEFAULT NULL COMMENT '作者ID',
    publish_time            DATETIME        DEFAULT NULL COMMENT '发布时间',
    view_count              INT             DEFAULT 0 COMMENT '浏览量',
    like_count              INT             DEFAULT 0 COMMENT '点赞数',
    comment_count           INT             DEFAULT 0 COMMENT '评论数',
    share_count             INT             DEFAULT 0 COMMENT '分享数',
    sentiment_label         TINYINT         DEFAULT NULL COMMENT '情感标签(-1-负面/0-中性/1-正面)',
    sentiment_label_name    VARCHAR(32)     DEFAULT NULL COMMENT '情感标签名称',
    sentiment_score         DECIMAL(5,4)    DEFAULT NULL COMMENT '情感分值(-1.0~1.0)',
    sentiment_keywords      VARCHAR(500)    DEFAULT NULL COMMENT '情感关键词(逗号分隔)',
    sentiment_analysis      VARCHAR(1000)   DEFAULT NULL COMMENT '情感分析说明',
    keywords                VARCHAR(500)    DEFAULT NULL COMMENT '关键词(逗号分隔)',
    topics                  VARCHAR(500)    DEFAULT NULL COMMENT '主题标签(逗号分隔)',
    area_code               VARCHAR(32)     DEFAULT NULL COMMENT '所属区域编码',
    area_name               VARCHAR(128)    DEFAULT NULL COMMENT '所属区域名称',
    longitude               DECIMAL(12,8)   DEFAULT NULL COMMENT '经度',
    latitude                DECIMAL(12,8)   DEFAULT NULL COMMENT '纬度',
    alert_level             TINYINT         DEFAULT 0 COMMENT '预警等级(0-无/1-低/2-中/3-高/4-极高)',
    alert_level_name        VARCHAR(32)     DEFAULT '无' COMMENT '预警等级名称',
    status                  TINYINT         DEFAULT 0 COMMENT '状态(0-待处理/1-处理中/2-已处置/3-已关闭)',
    status_name             VARCHAR(32)     DEFAULT '待处理' COMMENT '状态名称',
    handle_remark           VARCHAR(500)    DEFAULT NULL COMMENT '处置说明',
    handle_officer_id       BIGINT          DEFAULT NULL COMMENT '处置民警ID',
    handle_time             DATETIME        DEFAULT NULL COMMENT '处置时间',
    crawler_task_id         VARCHAR(64)     DEFAULT NULL COMMENT '爬虫任务ID',
    create_by               BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by               BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT         DEFAULT 0 COMMENT '是否删除(0-否/1-是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_opinion_id (opinion_id),
    UNIQUE KEY uk_source_url (source_url(500)),
    KEY idx_publish_time (publish_time),
    KEY idx_sentiment_label (sentiment_label),
    KEY idx_alert_level (alert_level),
    KEY idx_source_site (source_site)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舆情数据表';

-- ---------------------------------------------------------------------------
-- 5. case_cluster（串并案聚类表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS case_cluster;
CREATE TABLE case_cluster (
    id                      BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    cluster_id              VARCHAR(64)     NOT NULL COMMENT '聚类唯一ID',
    cluster_no              VARCHAR(64)     DEFAULT NULL COMMENT '聚类编号',
    cluster_name            VARCHAR(255)    DEFAULT NULL COMMENT '聚类名称',
    modus_operandi          TEXT            DEFAULT NULL COMMENT '作案手段描述',
    modus_keywords          VARCHAR(1000)   DEFAULT NULL COMMENT '作案手段关键词(逗号分隔)',
    case_type               VARCHAR(64)     DEFAULT NULL COMMENT '案件类型编码',
    case_type_name          VARCHAR(128)    DEFAULT NULL COMMENT '案件类型名称',
    start_time              DATETIME        DEFAULT NULL COMMENT '起始案发时间',
    end_time                DATETIME        DEFAULT NULL COMMENT '结束案发时间',
    case_count              INT             DEFAULT 0 COMMENT '关联案件数',
    case_ids                TEXT            DEFAULT NULL COMMENT '案件ID列表(逗号分隔)',
    case_nos                TEXT            DEFAULT NULL COMMENT '案件编号列表(逗号分隔)',
    area_code               VARCHAR(32)     DEFAULT NULL COMMENT '所属区域编码',
    area_name               VARCHAR(128)    DEFAULT NULL COMMENT '所属区域名称',
    center_longitude        DECIMAL(12,8)   DEFAULT NULL COMMENT '中心经度',
    center_latitude         DECIMAL(12,8)   DEFAULT NULL COMMENT '中心纬度',
    radius_meters           DECIMAL(10,2)   DEFAULT NULL COMMENT '覆盖半径(米)',
    suspect_ids             TEXT            DEFAULT NULL COMMENT '嫌疑人ID列表(逗号分隔)',
    vehicle_ids             TEXT            DEFAULT NULL COMMENT '车辆ID列表(逗号分隔)',
    similarity_score        DECIMAL(5,4)    DEFAULT NULL COMMENT '相似度分值(0~1)',
    cluster_features        JSON            DEFAULT NULL COMMENT '聚类特征(JSON)',
    alert_level             TINYINT         DEFAULT 1 COMMENT '预警等级(1-低/2-中/3-高/4-极高)',
    alert_level_name        VARCHAR(32)     DEFAULT '低' COMMENT '预警等级名称',
    status                  TINYINT         DEFAULT 0 COMMENT '状态(0-待核查/1-核查中/2-确认并案/3-排除/4-已结案)',
    status_name             VARCHAR(32)     DEFAULT '待核查' COMMENT '状态名称',
    investigation_suggestion TEXT           DEFAULT NULL COMMENT '侦查建议',
    handle_remark           VARCHAR(500)    DEFAULT NULL COMMENT '处置说明',
    handle_officer_id       BIGINT          DEFAULT NULL COMMENT '处置民警ID',
    handle_time             DATETIME        DEFAULT NULL COMMENT '处置时间',
    analysis_model_id       VARCHAR(64)     DEFAULT NULL COMMENT '研判模型ID',
    analysis_model_name     VARCHAR(128)    DEFAULT NULL COMMENT '研判模型名称',
    create_by               BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by               BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT         DEFAULT 0 COMMENT '是否删除(0-否/1-是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cluster_id (cluster_id),
    KEY idx_case_type (case_type),
    KEY idx_alert_level (alert_level),
    KEY idx_start_time (start_time),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='串并案聚类表';

-- ---------------------------------------------------------------------------
-- 6. hotspot_prediction（热点预测表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS hotspot_prediction;
CREATE TABLE hotspot_prediction (
    id                      BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    prediction_id           VARCHAR(64)     NOT NULL COMMENT '预测唯一ID',
    prediction_no           VARCHAR(64)     DEFAULT NULL COMMENT '预测编号',
    prediction_batch        VARCHAR(64)     DEFAULT NULL COMMENT '预测批次ID',
    predict_start_time      DATETIME        DEFAULT NULL COMMENT '预测窗口开始时间',
    predict_end_time        DATETIME        DEFAULT NULL COMMENT '预测窗口结束时间',
    predict_hours           INT             DEFAULT 24 COMMENT '预测时长(小时)',
    area_code               VARCHAR(32)     DEFAULT NULL COMMENT '所属区域编码',
    area_name               VARCHAR(128)    DEFAULT NULL COMMENT '所属区域名称',
    grid_code               VARCHAR(32)     DEFAULT NULL COMMENT '网格编码',
    grid_center_lng         DECIMAL(12,8)   DEFAULT NULL COMMENT '网格中心经度',
    grid_center_lat         DECIMAL(12,8)   DEFAULT NULL COMMENT '网格中心纬度',
    case_type               VARCHAR(64)     DEFAULT NULL COMMENT '案件类型编码',
    case_type_name          VARCHAR(128)    DEFAULT NULL COMMENT '案件类型名称',
    predicted_count         INT             DEFAULT NULL COMMENT '预测案件数',
    probability             DECIMAL(5,4)    DEFAULT NULL COMMENT '发生概率(0~1)',
    risk_score              DECIMAL(5,4)    DEFAULT NULL COMMENT '风险评分(0~1)',
    risk_level              TINYINT         DEFAULT 1 COMMENT '风险等级(1-低/2-中/3-高/4-极高)',
    risk_level_name         VARCHAR(32)     DEFAULT '低' COMMENT '风险等级名称',
    historical_count        INT             DEFAULT NULL COMMENT '历史同期案件数',
    trend_rate              DECIMAL(8,4)    DEFAULT NULL COMMENT '环比变化率(%)',
    seasonal_pattern        VARCHAR(255)    DEFAULT NULL COMMENT '季节性模式',
    contributing_factors    JSON            DEFAULT NULL COMMENT '影响因素(JSON)',
    prevention_suggestion   VARCHAR(1000)   DEFAULT NULL COMMENT '防控建议',
    actual_count            INT             DEFAULT NULL COMMENT '实际案件数(验证用)',
    prediction_accuracy     DECIMAL(5,4)    DEFAULT NULL COMMENT '预测准确率(验证用)',
    status                  TINYINT         DEFAULT 0 COMMENT '状态(0-预测中/1-已发布/2-已验证/3-已失效)',
    status_name             VARCHAR(32)     DEFAULT '预测中' COMMENT '状态名称',
    model_run_time          DATETIME        DEFAULT NULL COMMENT '模型运行时间',
    model_version           VARCHAR(64)     DEFAULT NULL COMMENT '模型版本',
    sarima_params           JSON            DEFAULT NULL COMMENT 'SARIMA模型参数(JSON)',
    police_station_code     VARCHAR(32)     DEFAULT NULL COMMENT '管辖派出所编码',
    police_station_name     VARCHAR(128)    DEFAULT NULL COMMENT '管辖派出所名称',
    create_by               BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time             DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by               BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time             DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT         DEFAULT 0 COMMENT '是否删除(0-否/1-是)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prediction_id (prediction_id),
    KEY idx_batch (prediction_batch),
    KEY idx_grid_code (grid_code),
    KEY idx_risk_level (risk_level),
    KEY idx_predict_time (predict_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='热点预测表';

-- ---------------------------------------------------------------------------
-- 7. 预置数据：6条研判模型
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO analysis_model (
    id, model_id, model_no, model_name, model_code, model_type, model_type_name,
    description, category, algorithm, cron_expression, job_handler,
    enabled, execute_status, execute_status_name, version,
    create_time, update_time, deleted
) VALUES
(1, 'MODEL-DAILY-REPORT', 'M001', '每日情报报告生成模型', 'DAILY_REPORT_GENERATOR', 'REPORT', '情报报告',
    '基于每日警情、案件、舆情数据自动生成每日情报研判报告', '情报产品', 'DeepSeek LLM + Template',
    '0 0 6 * * ?', 'dailyReportJobHandler',
    1, 0, '空闲', 1, NOW(), NOW(), 0),

(2, 'MODEL-WEEKLY-REPORT', 'M002', '每周情报报告生成模型', 'WEEKLY_REPORT_GENERATOR', 'REPORT', '情报报告',
    '基于每周警情、案件、舆情数据自动生成每周情报研判报告', '情报产品', 'DeepSeek LLM + Statistics',
    '0 0 7 ? * MON', 'weeklyReportJobHandler',
    1, 0, '空闲', 1, NOW(), NOW(), 0),

(3, 'MODEL-MONTHLY-REPORT', 'M003', '每月情报报告生成模型', 'MONTHLY_REPORT_GENERATOR', 'REPORT', '情报报告',
    '基于每月警情、案件、舆情数据自动生成每月情报研判报告', '情报产品', 'DeepSeek LLM + Trend Analysis',
    '0 0 8 1 * ?', 'monthlyReportJobHandler',
    1, 0, '空闲', 1, NOW(), NOW(), 0),

(4, 'MODEL-SENTIMENT-ANALYSIS', 'M004', '舆情情感分析模型', 'SENTIMENT_ANALYZER', 'SENTIMENT', '舆情分析',
    '对爬取的舆情数据进行情感倾向分析和预警等级判定', '舆情研判', 'BERT + Dictionary',
    '0 */30 * * * ?', 'sentimentAnalysisJobHandler',
    1, 0, '空闲', 1, NOW(), NOW(), 0),

(5, 'MODEL-CASE-CLUSTER', 'M005', '串并案聚类分析模型', 'CASE_CLUSTER_ANALYZER', 'CLUSTER', '串并案',
    '基于作案手段、时间、地点、嫌疑人等特征进行案件聚类', '案件研判', 'DBSCAN + Cosine Similarity',
    '0 0 */4 * * ?', 'caseClusterJobHandler',
    1, 0, '空闲', 1, NOW(), NOW(), 0),

(6, 'MODEL-HOTSPOT-PREDICTION', 'M006', '热点区域预测模型', 'HOTSPOT_PREDICTOR', 'PREDICTION', '热点预测',
    '基于历史案件数据利用SARIMA模型预测未来热点案发区域', '预测预警', 'SARIMA + Spatial Interpolation',
    '0 0 5 * * ?', 'hotspotPredictionJobHandler',
    1, 0, '空闲', 1, NOW(), NOW(), 0);

-- ---------------------------------------------------------------------------
-- 8. 预置数据：2个示例爬虫任务
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO crawler_task (
    id, task_id, task_no, task_name, site_name, site_url,
    entry_urls, allow_domains, url_pattern,
    content_selector, title_selector, author_selector, publish_time_selector,
    keywords, area_filter, crawl_depth, thread_count, sleep_millis,
    enabled, task_status, task_status_name, cron_expression,
    create_time, update_time, deleted
) VALUES
(1, 'CRAWLER-LOCAL-FORUM', 'C001', '本地论坛舆情爬虫', '本地生活论坛', 'https://bbs.local-example.com',
    'https://bbs.local-example.com/forum-1-1.html,https://bbs.local-example.com/forum-2-1.html,https://bbs.local-example.com/forum-3-1.html',
    'bbs.local-example.com',
    '/thread-\\d+-1-1\\.html',
    'div.t_fsz, div.post_content', 'h1.ts, span#thread_subject', 'a.xw1, .authi a', 'span.time-show, em[id*=authorposton]',
    '警情,事故,纠纷,投诉,城管,拆迁,维权,举报,污染,噪音',
    '本市',
    3, 5, 1000,
    1, 0, '空闲', '0 0 */2 * * ?',
    NOW(), NOW(), 0),

(2, 'CRAWLER-LOCAL-NEWS', 'C002', '本地新闻舆情爬虫', '本地新闻门户', 'https://news.local-example.com',
    'https://news.local-example.com/local/,https://news.local-example.com/society/,https://news.local-example.com/politics/',
    'news.local-example.com',
    '/\\d{8}/\\d+\\.shtml',
    'div.article-content, div#content', 'h1.article-title, div.title h1', 'span.source, .article-author', 'span.publish-time, .pubtime',
    '警方,公安,派出所,案件,事故,违法,犯罪,查处,通报,警情',
    '本市',
    3, 5, 1500,
    1, 0, '空闲', '0 30 */2 * * ?',
    NOW(), NOW(), 0);
