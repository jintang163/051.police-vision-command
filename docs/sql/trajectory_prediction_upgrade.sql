-- ============================================================================
-- 重点人员轨迹预测与预警系统升级脚本
-- 功能：1)GPS轨迹存储 2)LSTM轨迹预测 3)预测预警推送 4)模型训练评估
-- 关联模块：police-vision-control + police-vision-mobile + police-vision-websocket
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. person_track_point（重点人员GPS轨迹历史表 - 支持90天数据存储）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS person_track_point;
CREATE TABLE person_track_point (
    id                  BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    track_id            VARCHAR(64)     NOT NULL COMMENT '轨迹点唯一ID',
    person_id           VARCHAR(64)     NOT NULL COMMENT '重点人员ID',
    person_name         VARCHAR(100)    DEFAULT NULL COMMENT '人员姓名(冗余便于查询)',
    longitude           DECIMAL(15,10)  NOT NULL COMMENT '经度(WGS84/GCJ02)',
    latitude            DECIMAL(15,10)  NOT NULL COMMENT '纬度(WGS84/GCJ02)',
    altitude            DECIMAL(10,3)   DEFAULT NULL COMMENT '海拔高度(米)',
    speed               DECIMAL(8,2)    DEFAULT NULL COMMENT '速度(km/h)',
    direction           DECIMAL(5,2)    DEFAULT NULL COMMENT '方向角度(0-359度，正北为0)',
    accuracy            INT             DEFAULT NULL COMMENT '定位精度(米)',
    source_type         VARCHAR(32)     DEFAULT 'GPS' COMMENT '来源类型:GPS/北斗/基站/WIFI/人脸识别/车辆卡口',
    device_id           VARCHAR(128)    DEFAULT NULL COMMENT '采集设备ID(手机IMEI/摄像头ID/基站ID)',
    gps_time            DATETIME        NOT NULL COMMENT 'GPS定位时间(采集设备上报时间)',
    location_type       VARCHAR(32)     DEFAULT 'GPS' COMMENT '定位类型:GPS/WIFI/LBS/BEIDOU',
    location_desc       VARCHAR(500)    DEFAULT NULL COMMENT '地理位置描述(POI地址)',
    extra_data          JSON            DEFAULT NULL COMMENT '扩展数据(JSON:基站信息/WIFI列表/信号强度等)',
    deleted             TINYINT(1)      DEFAULT 0 COMMENT '是否删除(0-否/1-是)',
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_track_id (track_id),
    KEY idx_person_id (person_id),
    KEY idx_person_gps_time (person_id, gps_time),
    KEY idx_gps_time (gps_time),
    KEY idx_source_type (source_type),
    KEY idx_lng_lat (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重点人员GPS轨迹历史表';

-- 按gps_time按日分区，便于90天滚动清理
ALTER TABLE person_track_point PARTITION BY RANGE (TO_DAYS(gps_time)) (
    PARTITION p20260101 VALUES LESS THAN (TO_DAYS('2026-01-02')),
    PARTITION p20260102 VALUES LESS THAN (TO_DAYS('2026-01-03')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- ---------------------------------------------------------------------------
-- 2. trajectory_prediction（LSTM轨迹预测结果表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS trajectory_prediction;
CREATE TABLE trajectory_prediction (
    id                  BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    prediction_id       VARCHAR(64)     NOT NULL COMMENT '预测结果唯一ID',
    person_id           VARCHAR(64)     NOT NULL COMMENT '重点人员ID',
    person_name         VARCHAR(100)    DEFAULT NULL COMMENT '人员姓名(冗余)',
    prediction_batch    VARCHAR(64)     NOT NULL COMMENT '预测批次ID(同批次关联)',
    prediction_rank     INT         DEFAULT 1 COMMENT '预测排名(1-3表示TOP1-TOP3)',
    longitude           DECIMAL(15,10)  NOT NULL COMMENT '预测经度',
    latitude            DECIMAL(15,10)  NOT NULL COMMENT '预测纬度',
    location_desc       VARCHAR(500)    DEFAULT NULL COMMENT '预测位置描述',
    probability         DOUBLE          NOT NULL COMMENT '预测概率(0-1)',
    predict_minutes_ahead INT        DEFAULT 30 COMMENT '预测多少分钟之后(默认30分钟)',
    predict_time        DATETIME        NOT NULL COMMENT '预测执行时间',
    predict_window_start DATETIME       NOT NULL COMMENT '预测时间窗口开始',
    predict_window_end  DATETIME        NOT NULL COMMENT '预测时间窗口结束',
    area_code           VARCHAR(64)     DEFAULT NULL COMMENT '所属行政区划编码',
    area_name           VARCHAR(100)    DEFAULT NULL COMMENT '所属行政区划名称',
    is_sensitive_area   TINYINT(1)      DEFAULT 0 COMMENT '是否落入敏感区域(0-否/1-是)',
    sensitive_area_type VARCHAR(32)     DEFAULT NULL COMMENT '敏感区域类型:GOVERNMENT/SCHOOL/HOSPITAL/STATION/TRAFFIC_HUB/CROWD',
    crowd_risk_level    INT         DEFAULT 0 COMMENT '聚集风险等级(0-无/1-低/2-中/3-高/4-极高)',
    model_version       VARCHAR(32)     NOT NULL COMMENT '模型版本(如lstm-v2.1-90d)',
    confidence          DOUBLE          DEFAULT 0.75 COMMENT '模型置信度(从模型评估获取)',
    feature_snapshot    JSON            DEFAULT NULL COMMENT '特征快照(用于模型解释)',
    status              TINYINT(1)      DEFAULT 1 COMMENT '状态(0-失效/1-有效)',
    deleted             TINYINT(1)      DEFAULT 0,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prediction_id (prediction_id),
    KEY idx_person_id (person_id),
    KEY idx_prediction_batch (prediction_batch),
    KEY idx_predict_time (predict_time),
    KEY idx_sensitive (is_sensitive_area),
    KEY idx_crowd_risk (crowd_risk_level),
    KEY idx_probability (probability),
    KEY idx_person_rank (person_id, prediction_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LSTM轨迹预测结果表';

-- ---------------------------------------------------------------------------
-- 3. prediction_alert（轨迹预测预警表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS prediction_alert;
CREATE TABLE prediction_alert (
    id                  BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    alert_id            VARCHAR(64)     NOT NULL COMMENT '预警唯一ID',
    alert_no            VARCHAR(64)     NOT NULL COMMENT '预警编号(PA+yyyyMMddHHmmss+4位随机)',
    alert_type          VARCHAR(32)     NOT NULL COMMENT '预警类型:SENSITIVE_AREA(敏感区)/CROWD_GATHERING(多人聚集)/SENSITIVE_CROWD(双重预警)',
    alert_type_name     VARCHAR(50)     NOT NULL COMMENT '预警类型名称',
    alert_level         INT         DEFAULT 2 COMMENT '预警等级(1-低/2-中/3-高/4-极高)',
    person_id           VARCHAR(64)     NOT NULL COMMENT '触发预警的重点人员ID',
    person_name         VARCHAR(100)    DEFAULT NULL COMMENT '人员姓名',
    person_type         VARCHAR(32)     DEFAULT NULL COMMENT '人员类型',
    control_level       INT         DEFAULT 1 COMMENT '管控级别',
    longitude           DECIMAL(15,10)  NOT NULL COMMENT '预警位置经度',
    latitude            DECIMAL(15,10)  NOT NULL COMMENT '预警位置纬度',
    location_desc       VARCHAR(500)    DEFAULT NULL COMMENT '预警位置描述',
    probability         DOUBLE          DEFAULT NULL COMMENT '关联预测概率',
    predict_time        DATETIME        DEFAULT NULL COMMENT '关联预测时间',
    prediction_id       VARCHAR(64)     DEFAULT NULL COMMENT '关联预测结果ID',
    prediction_batch    VARCHAR(64)     DEFAULT NULL COMMENT '关联预测批次',
    trigger_reason      VARCHAR(1000)   DEFAULT NULL COMMENT '触发原因描述',
    sensitive_area_name VARCHAR(200)    DEFAULT NULL COMMENT '敏感区域名称',
    sensitive_area_type VARCHAR(32)     DEFAULT NULL COMMENT '敏感区域类型',
    crowd_count         INT         DEFAULT 0 COMMENT '真实聚集人数(从聚集检测系统获取)',
    target_person_count INT         DEFAULT 0 COMMENT '聚集人员中的重点人员数量',
    nearby_police_ids   VARCHAR(500)    DEFAULT NULL COMMENT '附近可派警的警员ID列表(逗号分隔)',
    status              TINYINT(1)      DEFAULT 0 COMMENT '状态(0-待处置/1-处理中/2-已处置/3-已关闭)',
    status_name         VARCHAR(32)     DEFAULT '待处理' COMMENT '状态名称',
    handle_remark       VARCHAR(2000)   DEFAULT NULL COMMENT '处置说明',
    handle_officer_id   BIGINT          DEFAULT NULL COMMENT '处置民警ID',
    handle_officer_name VARCHAR(100)    DEFAULT NULL COMMENT '处置民警姓名',
    handle_time         DATETIME        DEFAULT NULL COMMENT '处置时间',
    police_station_code VARCHAR(64)     DEFAULT NULL COMMENT '管辖派出所编码',
    police_station_name VARCHAR(200)    DEFAULT NULL COMMENT '管辖派出所名称',
    extra_data          JSON            DEFAULT NULL COMMENT '扩展数据',
    deleted             TINYINT(1)      DEFAULT 0,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_id (alert_id),
    UNIQUE KEY uk_alert_no (alert_no),
    KEY idx_person_id (person_id),
    KEY idx_prediction_batch (prediction_batch),
    KEY idx_alert_level (alert_level),
    KEY idx_status (status),
    KEY idx_create_time (create_time),
    KEY idx_lng_lat (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轨迹预测预警表';

-- ---------------------------------------------------------------------------
-- 4. model_training_job（LSTM模型训练任务表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS model_training_job;
CREATE TABLE model_training_job (
    id                  BIGINT          NOT NULL COMMENT '主键ID',
    job_id              VARCHAR(64)     NOT NULL COMMENT '任务唯一ID',
    model_type          VARCHAR(32)     DEFAULT 'LSTM' COMMENT '模型类型:LSTM/GRU/Transformer/HMM',
    model_version       VARCHAR(32)     NOT NULL COMMENT '模型版本(如lstm-v2.2)',
    status              VARCHAR(32)     DEFAULT 'PENDING' COMMENT '状态:PENDING/TRAINING/EVALUATING/SUCCESS/FAILED',
    status_name         VARCHAR(50)     DEFAULT '待训练' COMMENT '状态名称',
    train_start_date    DATE            NOT NULL COMMENT '训练数据起始日期',
    train_end_date      DATE            NOT NULL COMMENT '训练数据结束日期',
    train_sample_count  BIGINT          DEFAULT 0 COMMENT '训练样本量',
    eval_sample_count   BIGINT          DEFAULT 0 COMMENT '评估样本量',
    train_params        JSON            DEFAULT NULL COMMENT '训练超参数(lr/batch_size/layers/hidden_size等)',
    train_loss          DOUBLE          DEFAULT NULL COMMENT '训练损失值',
    eval_mae            DOUBLE          DEFAULT NULL COMMENT '评估指标:平均绝对误差(米)',
    eval_rmse           DOUBLE          DEFAULT NULL COMMENT '评估指标:均方根误差(米)',
    eval_accuracy_top1  DOUBLE          DEFAULT NULL COMMENT '评估指标:Top1位置准确率(50米内)',
    eval_accuracy_top3  DOUBLE          DEFAULT NULL COMMENT '评估指标:Top3位置准确率(50米内)',
    eval_accuracy_30m   DOUBLE          DEFAULT NULL COMMENT '评估指标:30米内准确率',
    eval_accuracy_50m   DOUBLE          DEFAULT NULL COMMENT '评估指标:50米内准确率',
    eval_accuracy_100m  DOUBLE          DEFAULT NULL COMMENT '评估指标:100米内准确率',
    eval_report         JSON            DEFAULT NULL COMMENT '完整评估报告',
    model_path          VARCHAR(500)    DEFAULT NULL COMMENT '模型文件存储路径(OSS/HDFS)',
    deployed            TINYINT(1)      DEFAULT 0 COMMENT '是否已部署(0-否/1-是)',
    deploy_time         DATETIME        DEFAULT NULL COMMENT '部署时间',
    accuracy_estimate   DOUBLE          DEFAULT NULL COMMENT '对外提供的准确率估计值(取eval_accuracy_top1)',
    trigger_mode        VARCHAR(32)     DEFAULT 'AUTO' COMMENT '触发方式:AUTO(自动定期)/MANUAL(手动触发)',
    start_time          DATETIME        DEFAULT NULL COMMENT '任务开始时间',
    end_time            DATETIME        DEFAULT NULL COMMENT '任务结束时间',
    duration_seconds    BIGINT          DEFAULT NULL COMMENT '耗时(秒)',
    error_message       TEXT            DEFAULT NULL COMMENT '错误信息',
    created_by          VARCHAR(64)     DEFAULT NULL,
    created_at          DATETIME        DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_id (job_id),
    KEY idx_model_version (model_version),
    KEY idx_status (status),
    KEY idx_deployed (deployed),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型训练任务与评估表';

-- ---------------------------------------------------------------------------
-- 5. 插入初始模型评估数据（首次部署用，之后由训练任务动态更新）
-- ---------------------------------------------------------------------------
INSERT INTO model_training_job (
    id, job_id, model_type, model_version, status, status_name,
    train_start_date, train_end_date, train_sample_count, eval_sample_count,
    eval_accuracy_top1, eval_accuracy_top3, eval_accuracy_50m, accuracy_estimate,
    deployed, deploy_time, created_at
) VALUES (
    NULL, 'TRAIN-20260622-00001', 'LSTM', 'lstm-v2.1-90d', 'SUCCESS', '训练成功',
    DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY),
    158420, 39605,
    0.752, 0.876, 0.752, 0.75,
    1, NOW(), NOW()
);

-- ---------------------------------------------------------------------------
-- 6. 创建索引优化查询性能
-- ---------------------------------------------------------------------------
CREATE INDEX idx_pt_person_time_desc ON person_track_point(person_id, gps_time DESC);
CREATE INDEX idx_tp_person_time_desc ON trajectory_prediction(person_id, predict_time DESC);
CREATE INDEX idx_pa_status_level ON prediction_alert(status, alert_level);
