-- ============================================================
-- 公安视图智能综合实战指挥平台 - 智能派单V2升级SQL脚本
-- 版本：2.0.0-smart-eta
-- 日期：2025
-- 功能：高德实时路况ETA / 偏航检测 / 会合点规划 / 路况快照
-- ============================================================

-- -----------------------------------------------------------
-- 1. dispatch_record 派单记录表扩展（18个新增字段）
-- -----------------------------------------------------------
ALTER TABLE dispatch_record
    ADD COLUMN dispatch_algorithm    VARCHAR(64)   DEFAULT NULL  COMMENT '派单算法：NORMAL直线距离/ETA_WEIGHTED_SCORE加权评分' AFTER priority,
    ADD COLUMN dispatch_mode         VARCHAR(32)   DEFAULT NULL  COMMENT '派单模式：SMART智能/MANUAL人工/RECALC重算/NORMAL普通' AFTER dispatch_algorithm,
    ADD COLUMN dispatch_version      VARCHAR(32)   DEFAULT '1.0' COMMENT '派单版本号' AFTER dispatch_mode,
    ADD COLUMN traffic_snapshot_id   VARCHAR(64)   DEFAULT NULL  COMMENT '路况快照ID（关联dispatch_traffic_snapshot.snapshot_id）' AFTER dispatch_version,
    ADD COLUMN avg_traffic_level     DECIMAL(3,1)  DEFAULT NULL  COMMENT '平均路况等级：1畅通/2缓行/3拥堵/4严重拥堵' AFTER traffic_snapshot_id,
    ADD COLUMN fastest_eta_seconds   INT           DEFAULT NULL  COMMENT '最快到达时间（秒）' AFTER avg_traffic_level,
    ADD COLUMN fastest_police_id     BIGINT        DEFAULT NULL  COMMENT '最快警力ID' AFTER fastest_eta_seconds,
    ADD COLUMN saved_eta_percent     DECIMAL(5,2)  DEFAULT NULL  COMMENT 'ETA节省百分比（对比直线距离估算）' AFTER fastest_police_id,
    ADD COLUMN police_count          INT           DEFAULT NULL  COMMENT '实际派出警力数' AFTER saved_eta_percent,
    ADD COLUMN required_officer_count INT          DEFAULT NULL  COMMENT '需求警力数' AFTER police_count,
    ADD COLUMN rendezvous_longitude  DECIMAL(12,8) DEFAULT NULL  COMMENT '会合点经度' AFTER required_officer_count,
    ADD COLUMN rendezvous_latitude   DECIMAL(12,8) DEFAULT NULL  COMMENT '会合点纬度' AFTER rendezvous_longitude,
    ADD COLUMN rendezvous_name       VARCHAR(128)  DEFAULT NULL  COMMENT '会合点名称' AFTER rendezvous_latitude,
    ADD COLUMN yaw_recalc_count      INT           DEFAULT 0     COMMENT '偏航/路况触发重算次数' AFTER rendezvous_name,
    ADD COLUMN last_recalc_reason    VARCHAR(255)  DEFAULT NULL  COMMENT '最后一次重算原因' AFTER yaw_recalc_count,
    ADD COLUMN last_recalc_time      DATETIME      DEFAULT NULL  COMMENT '最后一次重算时间' AFTER last_recalc_reason,
    ADD COLUMN recalc_by_commander_id BIGINT       DEFAULT NULL  COMMENT '手动重算指挥长ID' AFTER last_recalc_time,
    ADD COLUMN route_strategy        VARCHAR(32)   DEFAULT NULL  COMMENT '路线策略：SPEED速度优先/DISTANCE距离优先/NO_EXPRESS不走高速';

-- -----------------------------------------------------------
-- 2. dispatch_traffic_snapshot 派单路况快照表（新建）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS dispatch_traffic_snapshot (
    id                          BIGINT          NOT NULL  COMMENT '主键ID',
    snapshot_id                 VARCHAR(64)     NOT NULL  COMMENT '快照唯一编码 SNAP-{雪花ID}',
    dispatch_id                 BIGINT          DEFAULT NULL  COMMENT '派单ID',
    dispatch_no                 VARCHAR(64)     DEFAULT NULL  COMMENT '派单编号',
    alarm_id                    BIGINT          NOT NULL  COMMENT '警情ID',
    snapshot_type               VARCHAR(64)     DEFAULT NULL  COMMENT '快照类型：INITIAL初派/RECALC重算/YAW偏航重算',
    snapshot_time               DATETIME        NOT NULL  COMMENT '快照时间',
    alarm_longitude             DECIMAL(12,8)   DEFAULT NULL  COMMENT '警情经度',
    alarm_latitude              DECIMAL(12,8)   DEFAULT NULL  COMMENT '警情纬度',
    police_count                INT             DEFAULT NULL  COMMENT '参与派单警力数',
    police_ids_str              TEXT            DEFAULT NULL  COMMENT '警力ID列表JSON',
    officer_eta_data            MEDIUMTEXT      DEFAULT NULL  COMMENT '各警力ETA详细数据JSON数组',
    route_polyline_data         MEDIUMTEXT      DEFAULT NULL  COMMENT '路线坐标JSON（Map<警员ID, polyline>）',
    traffic_data                MEDIUMTEXT      DEFAULT NULL  COMMENT '高德路况原始数据JSON',
    weather_data                TEXT            DEFAULT NULL  COMMENT '天气数据JSON（扩展）',
    avg_traffic_level           DECIMAL(3,1)    DEFAULT NULL  COMMENT '平均路况等级',
    avg_eta_seconds             INT             DEFAULT NULL  COMMENT '平均ETA（秒）',
    fastest_eta_seconds         INT             DEFAULT NULL  COMMENT '最快ETA（秒）',
    fastest_police_id           BIGINT          DEFAULT NULL  COMMENT '最快警力ID',
    total_road_distance         DECIMAL(12,2)   DEFAULT NULL  COMMENT '总道路距离（米）',
    rendezvous_longitude        DECIMAL(12,8)   DEFAULT NULL  COMMENT '会合点经度',
    rendezvous_latitude         DECIMAL(12,8)   DEFAULT NULL  COMMENT '会合点纬度',
    rendezvous_name             VARCHAR(128)    DEFAULT NULL  COMMENT '会合点名称',
    rendezvous_eta_seconds      INT             DEFAULT NULL  COMMENT '会合点到警情ETA（秒）',
    multi_dispatch_plan_data    MEDIUMTEXT      DEFAULT NULL  COMMENT '多警力联合出警规划JSON',
    source                      VARCHAR(64)     DEFAULT NULL  COMMENT '来源：SMART_DISPATCH/MANUAL/RECALC/YAW_AUTO',
    remark                      VARCHAR(512)    DEFAULT NULL  COMMENT '备注',
    create_time                 DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted                     TINYINT         DEFAULT 0     COMMENT '逻辑删除 0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_id (snapshot_id),
    KEY idx_alarm_id (alarm_id),
    KEY idx_dispatch_id (dispatch_id),
    KEY idx_snapshot_time (snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单路况快照表（智能派单V2复盘用）';

-- -----------------------------------------------------------
-- 3. 索引优化
-- -----------------------------------------------------------
ALTER TABLE dispatch_record ADD INDEX idx_traffic_snapshot_id (traffic_snapshot_id);
ALTER TABLE dispatch_record ADD INDEX idx_fastest_eta (fastest_eta_seconds);
ALTER TABLE dispatch_record ADD INDEX idx_dispatch_mode (dispatch_mode);

-- -----------------------------------------------------------
-- 4. Redis常量对应说明（供参考）
-- -----------------------------------------------------------
-- traffic:status:circle:{lon_lat_radius}   - 路况缓存 300秒
-- route:eta:{start}_{end}_{strategy}        - 路线ETA缓存 60秒
-- dispatch:track:{alarmId}                  - 派单轨迹 1800秒
-- dispatch:yaw:{dispatchId}_{policeId}      - 偏航检测注册 1800秒
-- dispatch:recalc_lock:{dispatchId}         - 重算分布式锁 180秒
-- admin:division:{adcode}                   - 行政区划 86400秒

-- -----------------------------------------------------------
-- 5. 测试数据（可选，Mock模式用）
-- -----------------------------------------------------------
-- INSERT INTO dispatch_traffic_snapshot (id, snapshot_id, dispatch_id, dispatch_no, alarm_id, snapshot_type, snapshot_time, alarm_longitude, alarm_latitude, police_count, source)
-- VALUES (1, 'SNAP-TEST-001', NULL, 'PD2025001', 1001, 'INITIAL', NOW(), 116.397428, 39.90923, 3, 'SMART_DISPATCH');
