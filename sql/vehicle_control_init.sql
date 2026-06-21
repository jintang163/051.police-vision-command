-- =============================================
-- 车辆稽查布控模块 数据库脚本
-- 数据库版本: MySQL 8.0+
-- =============================================

USE `police_vision`;

-- =============================================
-- 8. 车辆稽查布控模块
-- =============================================

-- 8.1 车辆布控表
DROP TABLE IF EXISTS `vehicle_control`;
CREATE TABLE `vehicle_control` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `control_no` VARCHAR(32) NOT NULL COMMENT '布控编号',
    `control_name` VARCHAR(128) NOT NULL COMMENT '布控名称',
    `control_type` TINYINT NOT NULL COMMENT '布控类型: 1-涉案车辆 2-违法未处理 3-重点关注 4-被盗抢 5-自定义',
    `control_level` TINYINT NOT NULL DEFAULT 2 COMMENT '布控级别: 1-一级 2-二级 3-三级',
    `plate_no` VARCHAR(32) COMMENT '车牌号(支持通配符*?)',
    `plate_color` VARCHAR(16) COMMENT '车牌颜色',
    `vehicle_type` VARCHAR(32) COMMENT '车辆类型',
    `vehicle_color` VARCHAR(32) COMMENT '车身颜色',
    `vehicle_brand` VARCHAR(64) COMMENT '车辆品牌',
    `area_code` VARCHAR(64) COMMENT '区域编码',
    `area_name` VARCHAR(128) COMMENT '区域名称',
    `crossing_ids` VARCHAR(1024) COMMENT '卡口ID列表(逗号分隔)',
    `start_time` DATETIME COMMENT '布控开始时间',
    `end_time` DATETIME COMMENT '布控结束时间',
    `time_rules` VARCHAR(512) COMMENT '时间段规则(如: 22:00-06:00)',
    `control_reason` VARCHAR(512) COMMENT '布控原因',
    `description` TEXT COMMENT '详细描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-已撤控 1-布控中 2-临时暂停',
    `warning_count` INT DEFAULT 0 COMMENT '预警次数',
    `last_warning_time` DATETIME COMMENT '最后预警时间',
    `create_dept_id` BIGINT COMMENT '创建部门ID',
    `create_user_id` BIGINT COMMENT '创建人ID',
    `approve_user_id` BIGINT COMMENT '审批人ID',
    `approve_time` DATETIME COMMENT '审批时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_control_no` (`control_no`),
    KEY `idx_plate_no` (`plate_no`),
    KEY `idx_control_type` (`control_type`),
    KEY `idx_control_level` (`control_level`),
    KEY `idx_status` (`status`),
    KEY `idx_area_code` (`area_code`),
    KEY `idx_time_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆布控表';

-- 8.2 车辆布控告警表
DROP TABLE IF EXISTS `vehicle_control_alert`;
CREATE TABLE `vehicle_control_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `alert_no` VARCHAR(32) NOT NULL COMMENT '告警编号',
    `control_id` VARCHAR(32) COMMENT '关联布控ID',
    `control_no` VARCHAR(32) COMMENT '关联布控编号',
    `control_name` VARCHAR(128) COMMENT '布控名称',
    `control_level` TINYINT COMMENT '布控级别',
    `alert_type` TINYINT NOT NULL COMMENT '告警类型: 11-车辆布控 12-跟车分析 13-昼伏夜出',
    `alert_name` VARCHAR(64) COMMENT '告警名称',
    `alert_level` TINYINT NOT NULL DEFAULT 2 COMMENT '告警级别: 1-高危 2-中危 3-低危',
    `plate_no` VARCHAR(32) COMMENT '车牌号',
    `plate_color` VARCHAR(16) COMMENT '车牌颜色',
    `vehicle_type` VARCHAR(32) COMMENT '车辆类型',
    `vehicle_color` VARCHAR(32) COMMENT '车身颜色',
    `vehicle_brand` VARCHAR(64) COMMENT '车辆品牌',
    `crossing_id` VARCHAR(32) COMMENT '卡口ID',
    `crossing_name` VARCHAR(128) COMMENT '卡口名称',
    `camera_id` VARCHAR(32) COMMENT '摄像头ID',
    `camera_name` VARCHAR(128) COMMENT '摄像头名称',
    `lane_no` VARCHAR(16) COMMENT '车道号',
    `speed` DECIMAL(8,2) COMMENT '车速(km/h)',
    `direction` INT COMMENT '行驶方向(0-360度)',
    `longitude` DECIMAL(12,8) COMMENT '经度',
    `latitude` DECIMAL(12,8) COMMENT '纬度',
    `image_url` VARCHAR(512) COMMENT '抓拍图片URL',
    `plate_image_url` VARCHAR(512) COMMENT '车牌特写URL',
    `capture_time` DATETIME COMMENT '抓拍时间',
    `alert_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警时间',
    `description` VARCHAR(1024) COMMENT '告警描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-待处理 2-处理中 3-已确认 4-已排除 5-已误报',
    `handler_id` BIGINT COMMENT '处理人ID',
    `handler_name` VARCHAR(64) COMMENT '处理人姓名',
    `handle_time` DATETIME COMMENT '处理时间',
    `handle_result` VARCHAR(512) COMMENT '处理结果',
    `handle_remark` TEXT COMMENT '处理备注',
    `push_status` TINYINT DEFAULT 0 COMMENT '推送状态: 0-未推送 1-已推送',
    `push_time` DATETIME COMMENT '推送时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_no` (`alert_no`),
    KEY `idx_plate_no` (`plate_no`),
    KEY `idx_alert_type` (`alert_type`),
    KEY `idx_alert_level` (`alert_level`),
    KEY `idx_status` (`status`),
    KEY `idx_alert_time` (`alert_time`),
    KEY `idx_crossing_id` (`crossing_id`),
    KEY `idx_control_id` (`control_id`),
    KEY `idx_location` (`longitude`, `latitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆布控告警表';

-- 8.3 卡口设备表
DROP TABLE IF EXISTS `traffic_crossing`;
CREATE TABLE `traffic_crossing` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `crossing_code` VARCHAR(64) NOT NULL COMMENT '卡口编码',
    `crossing_name` VARCHAR(128) NOT NULL COMMENT '卡口名称',
    `crossing_type` TINYINT NOT NULL COMMENT '类型: 1-高清卡口 2-电子警察 3-治安卡口 4-智能卡口',
    `location` VARCHAR(256) COMMENT '位置描述',
    `road_name` VARCHAR(128) COMMENT '道路名称',
    `direction_desc` VARCHAR(64) COMMENT '方向描述',
    `lane_count` INT DEFAULT 0 COMMENT '车道数',
    `longitude` DECIMAL(12,8) NOT NULL COMMENT '经度',
    `latitude` DECIMAL(12,8) NOT NULL COMMENT '纬度',
    `area_code` VARCHAR(64) COMMENT '所属区域编码',
    `area_name` VARCHAR(128) COMMENT '所属区域名称',
    `manufacturer` VARCHAR(64) COMMENT '设备厂商',
    `model` VARCHAR(64) COMMENT '设备型号',
    `ip_address` VARCHAR(64) COMMENT 'IP地址',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-离线 1-在线 2-故障 3-维护中',
    `last_online_time` DATETIME COMMENT '最后在线时间',
    `description` VARCHAR(512) COMMENT '备注描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_crossing_code` (`crossing_code`),
    KEY `idx_status` (`status`),
    KEY `idx_area_code` (`area_code`),
    KEY `idx_location` (`longitude`, `latitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交通卡口表';

-- 8.4 跟车分析结果表
DROP TABLE IF EXISTS `vehicle_follow_record`;
CREATE TABLE `vehicle_follow_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_no` VARCHAR(32) NOT NULL COMMENT '记录编号',
    `crossing_id` VARCHAR(32) COMMENT '卡口ID',
    `crossing_name` VARCHAR(128) COMMENT '卡口名称',
    `road_direction` VARCHAR(16) COMMENT '行驶方向',
    `vehicle_count` INT COMMENT '同行车辆数',
    `plate_nos` TEXT COMMENT '车牌号列表(JSON数组)',
    `vehicle_types` VARCHAR(256) COMMENT '车辆类型列表',
    `average_speed` DECIMAL(8,2) COMMENT '平均车速(km/h)',
    `window_start` DATETIME COMMENT '窗口开始时间',
    `window_end` DATETIME COMMENT '窗口结束时间',
    `alert_level` TINYINT COMMENT '告警级别',
    `description` VARCHAR(512) COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-已忽略 1-待核查 2-已核查',
    `handler_id` BIGINT COMMENT '处理人ID',
    `handler_name` VARCHAR(64) COMMENT '处理人姓名',
    `handle_time` DATETIME COMMENT '处理时间',
    `handle_remark` VARCHAR(512) COMMENT '处理备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_no` (`record_no`),
    KEY `idx_crossing_id` (`crossing_id`),
    KEY `idx_window_time` (`window_start`, `window_end`),
    KEY `idx_alert_level` (`alert_level`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跟车分析记录表';

-- 8.5 昼伏夜出车辆分析表
DROP TABLE IF EXISTS `vehicle_night_active`;
CREATE TABLE `vehicle_night_active` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_no` VARCHAR(32) NOT NULL COMMENT '记录编号',
    `plate_no` VARCHAR(32) NOT NULL COMMENT '车牌号',
    `vehicle_type` VARCHAR(32) COMMENT '车辆类型',
    `vehicle_color` VARCHAR(32) COMMENT '车身颜色',
    `night_capture_count` INT COMMENT '夜间出现次数',
    `day_capture_count` INT COMMENT '白天出现次数',
    `night_day_ratio` DECIMAL(8,2) COMMENT '日夜比例',
    `crossing_ids` VARCHAR(512) COMMENT '经过卡口列表',
    `crossing_names` VARCHAR(1024) COMMENT '卡口名称列表',
    `statistics_start_time` DATETIME COMMENT '统计开始时间',
    `statistics_end_time` DATETIME COMMENT '统计结束时间',
    `alert_level` TINYINT COMMENT '告警级别',
    `description` VARCHAR(512) COMMENT '描述',
    `last_longitude` DECIMAL(12,8) COMMENT '最后出现经度',
    `last_latitude` DECIMAL(12,8) COMMENT '最后出现纬度',
    `last_capture_time` DATETIME COMMENT '最后出现时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-已忽略 1-待核查 2-已核查',
    `handler_id` BIGINT COMMENT '处理人ID',
    `handler_name` VARCHAR(64) COMMENT '处理人姓名',
    `handle_time` DATETIME COMMENT '处理时间',
    `handle_remark` VARCHAR(512) COMMENT '处理备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_no` (`record_no`),
    KEY `idx_plate_no` (`plate_no`),
    KEY `idx_alert_level` (`alert_level`),
    KEY `idx_status` (`status`),
    KEY `idx_statistics_time` (`statistics_start_time`, `statistics_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='昼伏夜出车辆分析表';

-- =============================================
-- 初始化数据
-- =============================================

-- 卡口设备示例数据
INSERT INTO `traffic_crossing` (`crossing_code`, `crossing_name`, `crossing_type`, `location`, `road_name`, `lane_count`, `longitude`, `latitude`, `area_code`, `area_name`, `manufacturer`, `status`) VALUES
('KQ001', '东长安街卡口', 1, '东长安街王府井路口', '东长安街', 6, 116.410128, 39.915527, 'AREA_001', '东城区', '海康威视', 1),
('KQ002', '西长安街卡口', 2, '西长安街西单路口', '西长安街', 6, 116.375128, 39.913527, 'AREA_002', '西城区', '大华', 1),
('KQ003', '北京站卡口', 1, '北京站东街', '北京站街', 4, 116.427128, 39.903527, 'AREA_001', '东城区', '华为', 1),
('KQ004', '天安门东卡口', 3, '天安门广场东侧路', '广场东侧路', 4, 116.398128, 39.916527, 'AREA_001', '东城区', '海康威视', 1),
('KQ005', '王府井北卡口', 2, '王府井大街北口', '王府井大街', 4, 116.409128, 39.915527, 'AREA_001', '东城区', '宇视', 1),
('KQ006', '建国门卡口', 1, '建国门桥', '建国门外大街', 8, 116.435128, 39.908527, 'AREA_001', '东城区', '海康威视', 1);

-- 车辆布控示例数据
INSERT INTO `vehicle_control` (`control_no`, `control_name`, `control_type`, `control_level`, `plate_no`, `plate_color`, `vehicle_type`, `vehicle_color`, `area_code`, `area_name`, `start_time`, `control_reason`, `status`, `warning_count`) VALUES
('VC001', '涉案车辆布控-京A88888', 1, 1, '京A88888', '蓝牌', '小轿车', '黑色', 'AREA_001', '东城区', NOW(), '涉嫌抢劫在逃车辆', 1, 0),
('VC002', '夜间可疑车辆布控', 3, 2, '京B*', '蓝牌', NULL, NULL, NULL, NULL, NOW(), '夜间频繁出入可疑车辆', 1, 0),
('VC003', '被盗车辆布控-京C66666', 4, 1, '京C66666', '蓝牌', 'SUV', '白色', 'AREA_002', '西城区', NOW(), '被盗车辆', 1, 0);
