-- =============================================
-- 安保事件管理模块数据库初始化脚本
-- 创建日期: 2026-06-21
-- =============================================

-- 1. 安保事件主表
DROP TABLE IF EXISTS `sec_event`;
CREATE TABLE `sec_event` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `event_name`      VARCHAR(200)    NOT NULL COMMENT '事件名称',
    `event_type`      VARCHAR(50)     NOT NULL COMMENT '事件类型(会议/赛事/演出/考察/其他)',
    `event_level`     TINYINT         NOT NULL DEFAULT 1 COMMENT '事件等级(1-一般 2-较大 3-重大 4-特别重大)',
    `start_time`      DATETIME        NOT NULL COMMENT '开始时间',
    `end_time`        DATETIME        NOT NULL COMMENT '结束时间',
    `organizer`       VARCHAR(200)    DEFAULT NULL COMMENT '主办单位',
    `description`     TEXT            DEFAULT NULL COMMENT '事件描述',
    `area_polygon`    JSON            DEFAULT NULL COMMENT '区域范围多边形坐标(JSON格式)',
    `address`         VARCHAR(500)    DEFAULT NULL COMMENT '详细地址',
    `lng`             DECIMAL(12,8)   DEFAULT NULL COMMENT '中心经度',
    `lat`             DECIMAL(12,8)   DEFAULT NULL COMMENT '中心纬度',
    `participant_count` INT           DEFAULT 0 COMMENT '参与人数',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态(0-草稿 1-待审批 2-已审批 3-进行中 4-已结束 5-已取消)',
    `approval_by`     BIGINT          DEFAULT NULL COMMENT '审批人ID',
    `approval_time`   DATETIME        DEFAULT NULL COMMENT '审批时间',
    `approval_remark` VARCHAR(500)    DEFAULT NULL COMMENT '审批备注',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_event_level` (`event_level`),
    KEY `idx_status` (`status`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安保事件主表';

-- 2. 事件资源关联表
DROP TABLE IF EXISTS `sec_event_resource`;
CREATE TABLE `sec_event_resource` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `resource_type`   VARCHAR(20)     NOT NULL COMMENT '资源类型(police-警力 camera-摄像头)',
    `resource_id`     BIGINT          NOT NULL COMMENT '资源ID',
    `resource_name`   VARCHAR(200)    NOT NULL COMMENT '资源名称',
    `lng`             DECIMAL(12,8)   DEFAULT NULL COMMENT '资源经度',
    `lat`             DECIMAL(12,8)   DEFAULT NULL COMMENT '资源纬度',
    `distance`        DECIMAL(10,2)   DEFAULT NULL COMMENT '距离事件中心距离(米)',
    `allocate_status` TINYINT         NOT NULL DEFAULT 0 COMMENT '分配状态(0-待分配 1-已分配 2-已到位 3-已撤离)',
    `allocate_time`   DATETIME        DEFAULT NULL COMMENT '分配时间',
    `arrive_time`     DATETIME        DEFAULT NULL COMMENT '到位时间',
    `remark`          VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_resource_type` (`resource_type`),
    KEY `idx_resource_id` (`resource_id`),
    KEY `idx_allocate_status` (`allocate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件资源关联表';

-- 3. 安保方案表
DROP TABLE IF EXISTS `sec_security_plan`;
CREATE TABLE `sec_security_plan` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `plan_name`       VARCHAR(200)    NOT NULL COMMENT '方案名称',
    `plan_type`       VARCHAR(50)     NOT NULL COMMENT '方案类型(总体方案/人流疏导/交通管制/应急处置)',
    `plan_content`    TEXT            DEFAULT NULL COMMENT '方案内容',
    `attachment_url`  VARCHAR(500)    DEFAULT NULL COMMENT '附件URL',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态(0-草稿 1-已发布 2-已启用 3-已停用)',
    `publish_time`    DATETIME        DEFAULT NULL COMMENT '发布时间',
    `publish_by`      BIGINT          DEFAULT NULL COMMENT '发布人ID',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_plan_type` (`plan_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安保方案表';

-- 4. 任务组表
DROP TABLE IF EXISTS `sec_task_group`;
CREATE TABLE `sec_task_group` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `plan_id`         BIGINT          NOT NULL COMMENT '方案ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `group_name`      VARCHAR(200)    NOT NULL COMMENT '小组名称',
    `group_code`      VARCHAR(50)     DEFAULT NULL COMMENT '小组编号',
    `group_leader`    BIGINT          DEFAULT NULL COMMENT '组长ID',
    `group_leader_name` VARCHAR(100)  DEFAULT NULL COMMENT '组长姓名',
    `group_leader_phone` VARCHAR(20)   DEFAULT NULL COMMENT '组长联系电话',
    `member_count`    INT             NOT NULL DEFAULT 0 COMMENT '成员数量',
    `description`     TEXT            DEFAULT NULL COMMENT '任务描述',
    `responsibility_area` VARCHAR(500) DEFAULT NULL COMMENT '责任区域',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_plan_id` (`plan_id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_group_leader` (`group_leader`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务组表';

-- 5. 岗位部署表
DROP TABLE IF EXISTS `sec_post`;
CREATE TABLE `sec_post` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `plan_id`         BIGINT          NOT NULL COMMENT '方案ID',
    `group_id`        BIGINT          DEFAULT NULL COMMENT '所属小组ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `post_name`       VARCHAR(200)    NOT NULL COMMENT '岗位名称',
    `post_code`       VARCHAR(50)     DEFAULT NULL COMMENT '岗位编号',
    `post_type`       VARCHAR(50)     DEFAULT NULL COMMENT '岗位类型(固定岗/巡逻岗/检查岗/指挥岗)',
    `lng`             DECIMAL(12,8)   NOT NULL COMMENT '岗位经度',
    `lat`             DECIMAL(12,8)   NOT NULL COMMENT '岗位纬度',
    `address`         VARCHAR(500)    DEFAULT NULL COMMENT '岗位地址',
    `police_id`       BIGINT          DEFAULT NULL COMMENT '部署警力ID',
    `police_name`     VARCHAR(100)    DEFAULT NULL COMMENT '部署警力姓名',
    `police_phone`    VARCHAR(20)     DEFAULT NULL COMMENT '警力联系电话',
    `police_count`    INT             NOT NULL DEFAULT 1 COMMENT '配置警力数量',
    `duty_content`    TEXT            DEFAULT NULL COMMENT '职责内容',
    `duty_start_time` DATETIME        DEFAULT NULL COMMENT '上岗时间',
    `duty_end_time`   DATETIME        DEFAULT NULL COMMENT '下岗时间',
    `check_in_status` TINYINT         NOT NULL DEFAULT 0 COMMENT '签到状态(0-未签到 1-已签到 2-已下岗)',
    `check_in_time`   DATETIME        DEFAULT NULL COMMENT '签到时间',
    `check_out_time`  DATETIME        DEFAULT NULL COMMENT '签退时间',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_plan_id` (`plan_id`),
    KEY `idx_group_id` (`group_id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_police_id` (`police_id`),
    KEY `idx_check_in_status` (`check_in_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位部署表';

-- 6. 路线规划表
DROP TABLE IF EXISTS `sec_route`;
CREATE TABLE `sec_route` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `route_name`      VARCHAR(200)    NOT NULL COMMENT '路线名称',
    `route_type`      VARCHAR(50)     DEFAULT NULL COMMENT '路线类型(车队路线/巡逻路线/疏散路线)',
    `start_point`     JSON            NOT NULL COMMENT '起点坐标(JSON: {name, lng, lat})',
    `end_point`       JSON            NOT NULL COMMENT '终点坐标(JSON: {name, lng, lat})',
    `waypoints`       JSON            DEFAULT NULL COMMENT '途经点集合(JSON数组)',
    `total_distance`  DECIMAL(10,2)   DEFAULT NULL COMMENT '总距离(米)',
    `estimated_time`  INT             DEFAULT NULL COMMENT '预计时间(分钟)',
    `escort_count`    INT             DEFAULT 0 COMMENT '护卫警力数量',
    `vehicle_count`   INT             DEFAULT 0 COMMENT '车辆数量',
    `plan_time`       DATETIME        DEFAULT NULL COMMENT '计划出发时间',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态(0-未开始 1-进行中 2-已完成 3-已取消)',
    `start_time`      DATETIME        DEFAULT NULL COMMENT '实际出发时间',
    `end_time`        DATETIME        DEFAULT NULL COMMENT '实际到达时间',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_route_type` (`route_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路线规划表';

-- 7. 路线摄像头关联表
DROP TABLE IF EXISTS `sec_route_camera`;
CREATE TABLE `sec_route_camera` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `route_id`        BIGINT          NOT NULL COMMENT '路线ID',
    `camera_id`       BIGINT          NOT NULL COMMENT '摄像头ID',
    `camera_name`     VARCHAR(200)    NOT NULL COMMENT '摄像头名称',
    `camera_url`      VARCHAR(500)    DEFAULT NULL COMMENT '摄像头视频地址',
    `lng`             DECIMAL(12,8)   DEFAULT NULL COMMENT '摄像头经度',
    `lat`             DECIMAL(12,8)   DEFAULT NULL COMMENT '摄像头纬度',
    `sequence`        INT             NOT NULL DEFAULT 0 COMMENT '播放顺序',
    `play_duration`   INT             DEFAULT 10 COMMENT '播放时长(秒)',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_route_id` (`route_id`),
    KEY `idx_camera_id` (`camera_id`),
    KEY `idx_sequence` (`sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路线摄像头关联表';

-- 8. 通行证管理表
DROP TABLE IF EXISTS `sec_pass`;
CREATE TABLE `sec_pass` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `pass_no`         VARCHAR(50)     NOT NULL COMMENT '通行证编号',
    `holder_name`     VARCHAR(100)    NOT NULL COMMENT '持证人姓名',
    `holder_idcard`   VARCHAR(30)     DEFAULT NULL COMMENT '持证人身份证号',
    `holder_phone`    VARCHAR(20)     DEFAULT NULL COMMENT '持证人手机号',
    `holder_avatar`   VARCHAR(500)    DEFAULT NULL COMMENT '持证人头像URL',
    `pass_type`       VARCHAR(50)     NOT NULL COMMENT '通行证类型(车辆通行证/人员通行证/工作证/采访证)',
    `pass_level`      TINYINT         NOT NULL DEFAULT 1 COMMENT '通行级别(1-A区 2-B区 3-C区 4-全场)',
    `vehicle_plate`   VARCHAR(20)     DEFAULT NULL COMMENT '车牌号码(车辆通行证)',
    `vehicle_type`    VARCHAR(50)     DEFAULT NULL COMMENT '车辆类型',
    `authorized_areas` JSON           DEFAULT NULL COMMENT '授权区域列表(JSON数组)',
    `authorized_time` JSON            DEFAULT NULL COMMENT '授权时段(JSON: {start, end})',
    `photo_url`       VARCHAR(500)    DEFAULT NULL COMMENT '证件照片URL',
    `qr_code`         VARCHAR(500)    DEFAULT NULL COMMENT '二维码图片URL',
    `qr_content`      TEXT            DEFAULT NULL COMMENT '二维码原始内容',
    `jwt_token`       TEXT            DEFAULT NULL COMMENT 'JWT令牌',
    `issue_time`      DATETIME        NOT NULL COMMENT '签发时间',
    `expire_time`     DATETIME        NOT NULL COMMENT '过期时间',
    `issue_by`        BIGINT          DEFAULT NULL COMMENT '签发人ID',
    `issue_by_name`   VARCHAR(100)    DEFAULT NULL COMMENT '签发人姓名',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态(0-已作废 1-有效 2-已过期 3-已挂失)',
    `verify_count`    INT             NOT NULL DEFAULT 0 COMMENT '核验次数',
    `last_verify_time` DATETIME       DEFAULT NULL COMMENT '最后核验时间',
    `last_verify_place` VARCHAR(200)  DEFAULT NULL COMMENT '最后核验地点',
    `cancel_reason`   VARCHAR(500)    DEFAULT NULL COMMENT '作废/挂失原因',
    `cancel_time`     DATETIME        DEFAULT NULL COMMENT '作废/挂失时间',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pass_no` (`pass_no`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_holder_idcard` (`holder_idcard`),
    KEY `idx_holder_phone` (`holder_phone`),
    KEY `idx_pass_type` (`pass_type`),
    KEY `idx_status` (`status`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_vehicle_plate` (`vehicle_plate`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通行证管理表';

-- 9. 交通预警表
DROP TABLE IF EXISTS `sec_traffic_alert`;
CREATE TABLE `sec_traffic_alert` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `alert_type`      VARCHAR(20)     NOT NULL COMMENT '预警类型(pedestrian-人流预警 vehicle-车流预警)',
    `alert_level`     TINYINT         NOT NULL DEFAULT 1 COMMENT '预警级别(1-正常 2-关注 3-预警 4-告警)',
    `location`        VARCHAR(500)    DEFAULT NULL COMMENT '预警位置描述',
    `area_code`       VARCHAR(50)     DEFAULT NULL COMMENT '区域编码',
    `lng`             DECIMAL(12,8)   DEFAULT NULL COMMENT '预警位置经度',
    `lat`             DECIMAL(12,8)   DEFAULT NULL COMMENT '预警位置纬度',
    `count_value`     INT             NOT NULL DEFAULT 0 COMMENT '当前统计数量',
    `threshold_value` INT             NOT NULL DEFAULT 0 COMMENT '阈值数量',
    `density`         DECIMAL(10,2)   DEFAULT NULL COMMENT '密度(人/平方米或车/公里)',
    `trend`           VARCHAR(20)     DEFAULT NULL COMMENT '趋势(rising-上升 falling-下降 stable-稳定)',
    `alert_time`      DATETIME        NOT NULL COMMENT '预警时间',
    `handled`         TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已处理(0-未处理 1-已处理)',
    `handle_time`     DATETIME        DEFAULT NULL COMMENT '处理时间',
    `handle_by`       BIGINT          DEFAULT NULL COMMENT '处理人ID',
    `handle_remark`   VARCHAR(500)    DEFAULT NULL COMMENT '处理备注',
    `dispatch_order_id` BIGINT        DEFAULT NULL COMMENT '关联派单ID',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_alert_type` (`alert_type`),
    KEY `idx_alert_level` (`alert_level`),
    KEY `idx_handled` (`handled`),
    KEY `idx_alert_time` (`alert_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交通预警表';

-- 10. 事件报告表
DROP TABLE IF EXISTS `sec_event_report`;
CREATE TABLE `sec_event_report` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `event_id`        BIGINT          NOT NULL COMMENT '事件ID',
    `report_name`     VARCHAR(200)    NOT NULL COMMENT '报告名称',
    `report_type`     VARCHAR(50)     NOT NULL COMMENT '报告类型(日报/周报/总结报告/专项报告)',
    `report_period`   VARCHAR(100)    DEFAULT NULL COMMENT '报告周期',
    `report_url`      VARCHAR(500)    DEFAULT NULL COMMENT '报告文件URL',
    `report_format`   VARCHAR(20)     DEFAULT 'PDF' COMMENT '报告格式(PDF/WORD/EXCEL)',
    `generate_time`   DATETIME        NOT NULL COMMENT '生成时间',
    `generate_by`     BIGINT          DEFAULT NULL COMMENT '生成人ID',
    `summary`         TEXT            DEFAULT NULL COMMENT '报告摘要',
    `pedestrian_count` BIGINT         DEFAULT 0 COMMENT '累计人流量',
    `vehicle_count`   BIGINT          DEFAULT 0 COMMENT '累计车流量',
    `peak_pedestrian` INT             DEFAULT 0 COMMENT '峰值人流',
    `peak_pedestrian_time` DATETIME   DEFAULT NULL COMMENT '峰值人流时间',
    `peak_vehicle`    INT             DEFAULT 0 COMMENT '峰值车流',
    `peak_vehicle_time` DATETIME      DEFAULT NULL COMMENT '峰值车流时间',
    `alert_count`     INT             DEFAULT 0 COMMENT '预警总数',
    `alert_level1_count` INT          DEFAULT 0 COMMENT '一级预警数',
    `alert_level2_count` INT          DEFAULT 0 COMMENT '二级预警数',
    `alert_level3_count` INT          DEFAULT 0 COMMENT '三级预警数',
    `alert_level4_count` INT          DEFAULT 0 COMMENT '四级预警数',
    `alert_handled_count` INT         DEFAULT 0 COMMENT '已处理预警数',
    `police_count`    INT             DEFAULT 0 COMMENT '投入警力数',
    `police_on_duty_count` INT        DEFAULT 0 COMMENT '在岗警力数',
    `post_count`      INT             DEFAULT 0 COMMENT '部署岗位数',
    `route_count`     INT             DEFAULT 0 COMMENT '规划路线数',
    `camera_count`    INT             DEFAULT 0 COMMENT '调用摄像头数',
    `pass_count`      INT             DEFAULT 0 COMMENT '发放通行证数',
    `pass_verify_count` BIGINT        DEFAULT 0 COMMENT '通行证核验次数',
    `task_group_count` INT            DEFAULT 0 COMMENT '任务组数量',
    `dispatch_count`  INT             DEFAULT 0 COMMENT '派单总数',
    `dispatch_completed_count` INT    DEFAULT 0 COMMENT '已完成派单数',
    `incident_count`  INT             DEFAULT 0 COMMENT '突发事故事件数',
    `create_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `update_by`       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除标识(0-未删除 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_report_type` (`report_type`),
    KEY `idx_generate_time` (`generate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件报告表';

-- =============================================
-- 初始化数据
-- =============================================
