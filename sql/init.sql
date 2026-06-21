-- =============================================
-- 公安视图智能综合实战指挥平台 数据库初始化脚本
-- 数据库版本: MySQL 8.0+
-- 字符集: utf8mb4
-- =============================================

CREATE DATABASE IF NOT EXISTS `police_vision`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `police_vision`;

-- =============================================
-- 1. 用户与权限模块
-- =============================================

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
    `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名',
    `police_number` VARCHAR(32) NOT NULL COMMENT '警号',
    `phone` VARCHAR(20) COMMENT '手机号',
    `email` VARCHAR(64) COMMENT '邮箱',
    `avatar` VARCHAR(256) COMMENT '头像',
    `department_id` BIGINT COMMENT '所属部门ID',
    `rank` VARCHAR(32) COMMENT '警衔',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(64) COMMENT '最后登录IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_police_number` (`police_number`),
    KEY `idx_department_id` (`department_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='警员用户表';

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    `description` VARCHAR(256) COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

DROP TABLE IF EXISTS `sys_department`;
CREATE TABLE `sys_department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父部门ID',
    `dept_name` VARCHAR(64) NOT NULL COMMENT '部门名称',
    `dept_code` VARCHAR(32) NOT NULL COMMENT '部门编码',
    `leader` VARCHAR(64) COMMENT '负责人',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `address` VARCHAR(256) COMMENT '部门地址',
    `longitude` DECIMAL(12, 8) COMMENT '经度',
    `latitude` DECIMAL(12, 8) COMMENT '纬度',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- =============================================
-- 2. GIS地图模块
-- =============================================

DROP TABLE IF EXISTS `police_location`;
CREATE TABLE `police_location` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID(终端/车载)',
    `user_id` BIGINT COMMENT '关联用户ID(单兵)',
    `car_id` BIGINT COMMENT '关联车辆ID(巡逻车)',
    `device_type` TINYINT NOT NULL COMMENT '设备类型: 1-单兵终端 2-车载终端 3-无人机',
    `longitude` DECIMAL(12, 8) NOT NULL COMMENT '经度',
    `latitude` DECIMAL(12, 8) NOT NULL COMMENT '纬度',
    `speed` DECIMAL(8, 2) DEFAULT 0 COMMENT '速度(km/h)',
    `direction` INT DEFAULT 0 COMMENT '方向(0-360度)',
    `altitude` DECIMAL(8, 2) COMMENT '海拔(米)',
    `accuracy` DECIMAL(8, 2) COMMENT '定位精度(米)',
    `battery` INT COMMENT '电量(%)',
    `signal_strength` INT COMMENT '信号强度(%)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-离线 1-在线 2-待命 3-出警中 4-休息',
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '定位时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_device_id` (`device_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_timestamp` (`timestamp`),
    KEY `idx_status` (`status`),
    KEY `idx_location` (`longitude`, `latitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='警力位置表(GPS轨迹)';

DROP TABLE IF EXISTS `patrol_car`;
CREATE TABLE `patrol_car` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `car_number` VARCHAR(32) NOT NULL COMMENT '车牌号',
    `car_type` VARCHAR(32) COMMENT '车辆类型: 警车/特警車/消防车/救护车',
    `car_model` VARCHAR(64) COMMENT '车辆型号',
    `device_id` VARCHAR(64) COMMENT '车载终端ID',
    `driver_id` BIGINT COMMENT '驾驶员ID',
    `current_user_ids` VARCHAR(256) COMMENT '当前乘车人员ID列表(逗号分隔)',
    `department_id` BIGINT COMMENT '所属部门',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-离线 1-在线待命 2-巡逻中 3-出警中 4-维修中',
    `load_capacity` INT COMMENT '载人数',
    `equipment` TEXT COMMENT '车载装备(JSON)',
    `longitude` DECIMAL(12, 8) COMMENT '当前经度',
    `latitude` DECIMAL(12, 8) COMMENT '当前纬度',
    `last_inspection_time` DATETIME COMMENT '上次年检时间',
    `next_inspection_time` DATETIME COMMENT '下次年检时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_car_number` (`car_number`),
    KEY `idx_device_id` (`device_id`),
    KEY `idx_status` (`status`),
    KEY `idx_department_id` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='巡逻车辆表';

DROP TABLE IF EXISTS `camera_device`;
CREATE TABLE `camera_device` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `camera_code` VARCHAR(64) NOT NULL COMMENT '摄像头编码',
    `camera_name` VARCHAR(128) NOT NULL COMMENT '摄像头名称',
    `camera_type` TINYINT NOT NULL COMMENT '类型: 1-枪机 2-球机 3-半球 4-高清卡口',
    `manufacturer` VARCHAR(64) COMMENT '厂商: 海康/大华/华为/宇视',
    `model` VARCHAR(64) COMMENT '型号',
    `ip_address` VARCHAR(64) COMMENT 'IP地址',
    `port` INT DEFAULT 554 COMMENT '端口',
    `username` VARCHAR(64) COMMENT '登录用户名',
    `password` VARCHAR(128) COMMENT '登录密码',
    `rtsp_url` VARCHAR(512) COMMENT 'RTSP播放地址',
    `hls_url` VARCHAR(512) COMMENT 'HLS播放地址',
    `flv_url` VARCHAR(512) COMMENT 'FLV播放地址',
    `longitude` DECIMAL(12, 8) NOT NULL COMMENT '经度',
    `latitude` DECIMAL(12, 8) NOT NULL COMMENT '纬度',
    `address` VARCHAR(256) COMMENT '安装地址',
    `coverage_radius` INT DEFAULT 100 COMMENT '覆盖半径(米)',
    `direction` INT DEFAULT 0 COMMENT '朝向(0-360度)',
    `ptz_support` TINYINT DEFAULT 0 COMMENT '是否支持PTZ: 0-否 1-是',
    `ai_support` TINYINT DEFAULT 0 COMMENT '是否支持AI分析: 0-否 1-是',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-离线 1-在线 2-故障 3-维护中',
    `last_online_time` DATETIME COMMENT '最后在线时间',
    `group_id` BIGINT COMMENT '所属分组ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_camera_code` (`camera_code`),
    KEY `idx_ip_address` (`ip_address`),
    KEY `idx_status` (`status`),
    KEY `idx_location` (`longitude`, `latitude`),
    KEY `idx_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监控摄像头表';

DROP TABLE IF EXISTS `map_layer`;
CREATE TABLE `map_layer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `layer_name` VARCHAR(64) NOT NULL COMMENT '图层名称',
    `layer_code` VARCHAR(64) NOT NULL COMMENT '图层编码',
    `layer_type` VARCHAR(32) NOT NULL COMMENT '图层类型: police-警力 alarm-警情 camera-摄像头 traffic-交通 keyarea-重点区域',
    `description` VARCHAR(256) COMMENT '图层描述',
    `style_config` TEXT COMMENT '样式配置(JSON)',
    `visible` TINYINT DEFAULT 1 COMMENT '是否默认可见: 0-否 1-是',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_layer_code` (`layer_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地图图层表';

DROP TABLE IF EXISTS `key_area`;
CREATE TABLE `key_area` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `area_name` VARCHAR(128) NOT NULL COMMENT '区域名称',
    `area_type` VARCHAR(32) NOT NULL COMMENT '区域类型: station-车站 airport-机场 school-学校 hospital-医院 mall-商场 government-政府',
    `area_level` TINYINT DEFAULT 2 COMMENT '级别: 1-一级 2-二级 3-三级',
    `address` VARCHAR(256) COMMENT '地址',
    `polygon_coords` TEXT NOT NULL COMMENT '多边形坐标(JSON数组)',
    `center_longitude` DECIMAL(12, 8) NOT NULL COMMENT '中心经度',
    `center_latitude` DECIMAL(12, 8) NOT NULL COMMENT '中心纬度',
    `person_in_charge` VARCHAR(64) COMMENT '责任人',
    `contact_phone` VARCHAR(20) COMMENT '联系电话',
    `police_station` VARCHAR(128) COMMENT '所属派出所',
    `risk_level` TINYINT DEFAULT 2 COMMENT '风险等级: 1-高 2-中 3-低',
    `description` TEXT COMMENT '描述说明',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_area_type` (`area_type`),
    KEY `idx_area_level` (`area_level`),
    KEY `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重点区域表';

-- =============================================
-- 3. 警情接报与派单模块
-- =============================================

DROP TABLE IF EXISTS `alarm_order`;
CREATE TABLE `alarm_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `alarm_no` VARCHAR(32) NOT NULL COMMENT '警情编号(自动生成)',
    `alarm_type` TINYINT NOT NULL COMMENT '警情类型: 1-盗窃 2-斗殴 3-走失 4-火警 5-交通事故 6-其他',
    `alarm_level` TINYINT NOT NULL DEFAULT 3 COMMENT '警情级别: 1-重大 2-较大 3-一般',
    `alarm_source` TINYINT NOT NULL COMMENT '来源: 1-110电话 2-短信 3-APP 4-视频识别 5-其他',
    `caller_name` VARCHAR(64) COMMENT '报警人姓名',
    `caller_phone` VARCHAR(20) COMMENT '报警人电话',
    `caller_id_card` VARCHAR(32) COMMENT '报警人身份证',
    `alarm_time` DATETIME NOT NULL COMMENT '报警时间',
    `alarm_address` VARCHAR(512) NOT NULL COMMENT '报警地址',
    `longitude` DECIMAL(12, 8) COMMENT '经度',
    `latitude` DECIMAL(12, 8) COMMENT '纬度',
    `grid_code` VARCHAR(64) COMMENT '网格编码',
    `alarm_content` TEXT NOT NULL COMMENT '报警内容',
    `voice_url` VARCHAR(512) COMMENT '报警录音URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-待派单 2-已派单 3-出警中 4-到达现场 5-处理中 6-已办结 7-已取消',
    `priority` INT DEFAULT 0 COMMENT '优先级(数值越大越优先)',
    `dispatch_time` DATETIME COMMENT '派单时间',
    `arrive_time` DATETIME COMMENT '到达现场时间',
    `finish_time` DATETIME COMMENT '办结时间',
    `cancel_reason` VARCHAR(512) COMMENT '取消原因',
    `handle_result` TEXT COMMENT '处理结果',
    `handle_user_id` BIGINT COMMENT '处理警员ID',
    `handle_department_id` BIGINT COMMENT '处理部门ID',
    `is_cooperation` TINYINT DEFAULT 0 COMMENT '是否联合办案: 0-否 1-是',
    `cooperation_depts` VARCHAR(512) COMMENT '协同部门ID列表',
    `parent_alarm_id` BIGINT COMMENT '关联主警情ID(联合办案)',
    `feedback_score` INT COMMENT '群众评价分数(1-5)',
    `feedback_content` TEXT COMMENT '群众评价内容',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alarm_no` (`alarm_no`),
    KEY `idx_alarm_type` (`alarm_type`),
    KEY `idx_alarm_level` (`alarm_level`),
    KEY `idx_status` (`status`),
    KEY `idx_alarm_time` (`alarm_time`),
    KEY `idx_handle_user` (`handle_user_id`),
    KEY `idx_location` (`longitude`, `latitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='警情工单表';

DROP TABLE IF EXISTS `dispatch_record`;
CREATE TABLE `dispatch_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `alarm_id` BIGINT NOT NULL COMMENT '警情ID',
    `dispatch_type` TINYINT NOT NULL COMMENT '派单类型: 1-自动派单 2-人工派单 3-转单 4-催办 5-升级',
    `dispatched_user_id` BIGINT COMMENT '派单警员ID',
    `dispatched_car_id` BIGINT COMMENT '派单车辆ID',
    `dispatched_dept_id` BIGINT COMMENT '派单部门ID',
    `dispatcher_id` BIGINT COMMENT '调度员ID',
    `dispatch_reason` VARCHAR(512) COMMENT '派单说明',
    `dispatch_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '派单时间',
    `expected_arrive_time` DATETIME COMMENT '预计到达时间',
    `actual_arrive_time` DATETIME COMMENT '实际到达时间',
    `arrive_distance` DECIMAL(8, 2) COMMENT '到达距离(公里)',
    `response_time` INT COMMENT '响应时间(秒)',
    `is_accepted` TINYINT DEFAULT 0 COMMENT '是否已接收: 0-否 1-是',
    `accept_time` DATETIME COMMENT '接收时间',
    `is_timeout` TINYINT DEFAULT 0 COMMENT '是否超时: 0-否 1-是',
    `timeout_reason` VARCHAR(512) COMMENT '超时原因',
    `navigation_route` TEXT COMMENT '导航路线(JSON)',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-无效 1-有效',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_alarm_id` (`alarm_id`),
    KEY `idx_dispatched_user` (`dispatched_user_id`),
    KEY `idx_dispatched_dept` (`dispatched_dept_id`),
    KEY `idx_dispatch_time` (`dispatch_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='派单记录表';

DROP TABLE IF EXISTS `alarm_handle_log`;
CREATE TABLE `alarm_handle_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `alarm_id` BIGINT NOT NULL COMMENT '警情ID',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(64) COMMENT '操作人姓名',
    `operation_type` VARCHAR(32) NOT NULL COMMENT '操作类型: create-创建 dispatch-派单 accept-接收 arrive-到达 process-处理 complete-办结 cancel-取消 urge-催办 transfer-转单',
    `operation_content` TEXT COMMENT '操作内容',
    `old_status` TINYINT COMMENT '原状态',
    `new_status` TINYINT COMMENT '新状态',
    `longitude` DECIMAL(12, 8) COMMENT '操作时经度',
    `latitude` DECIMAL(12, 8) COMMENT '操作时纬度',
    `remark` VARCHAR(512) COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_alarm_id` (`alarm_id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_operation_type` (`operation_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='警情处理日志表';

-- =============================================
-- 4. 视频AI分析模块
-- =============================================

DROP TABLE IF EXISTS `target_person`;
CREATE TABLE `target_person` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `person_no` VARCHAR(32) NOT NULL COMMENT '人员编号',
    `name` VARCHAR(64) NOT NULL COMMENT '姓名',
    `id_card` VARCHAR(32) COMMENT '身份证号',
    `gender` TINYINT COMMENT '性别: 1-男 2-女',
    `birthday` DATE COMMENT '出生日期',
    `nationality` VARCHAR(32) COMMENT '民族',
    `address` VARCHAR(256) COMMENT '户籍地',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `target_type` TINYINT NOT NULL COMMENT '布控类型: 1-重点人员 2-涉案人员 3-失踪人员 4-关注人员',
    `target_level` TINYINT NOT NULL DEFAULT 2 COMMENT '布控级别: 1-一级 2-二级 3-三级',
    `control_reason` VARCHAR(512) COMMENT '布控原因',
    `control_start_time` DATETIME NOT NULL COMMENT '布控开始时间',
    `control_end_time` DATETIME COMMENT '布控结束时间',
    `face_image_url` VARCHAR(512) COMMENT '人脸图片URL',
    `face_feature` TEXT COMMENT '人脸特征向量(Base64编码)',
    `description` TEXT COMMENT '特征描述(身高、体型、着装等)',
    `case_info` TEXT COMMENT '涉案信息',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-已撤控 1-布控中 2-临时暂停',
    `warning_count` INT DEFAULT 0 COMMENT '预警次数',
    `last_warning_time` DATETIME COMMENT '最后预警时间',
    `create_dept_id` BIGINT COMMENT '创建部门ID',
    `create_user_id` BIGINT COMMENT '创建人ID',
    `approve_user_id` BIGINT COMMENT '审批人ID',
    `approve_time` DATETIME COMMENT '审批时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_person_no` (`person_no`),
    KEY `idx_id_card` (`id_card`),
    KEY `idx_target_type` (`target_type`),
    KEY `idx_target_level` (`target_level`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='布控人员表';

DROP TABLE IF EXISTS `target_vehicle`;
CREATE TABLE `target_vehicle` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `vehicle_no` VARCHAR(32) NOT NULL COMMENT '车辆编号',
    `plate_number` VARCHAR(32) NOT NULL COMMENT '车牌号',
    `plate_color` VARCHAR(16) COMMENT '车牌颜色',
    `vehicle_type` VARCHAR(32) COMMENT '车辆类型',
    `vehicle_color` VARCHAR(32) COMMENT '车身颜色',
    `vehicle_brand` VARCHAR(64) COMMENT '车辆品牌',
    `vehicle_model` VARCHAR(64) COMMENT '车辆型号',
    `owner_name` VARCHAR(64) COMMENT '车主姓名',
    `owner_id_card` VARCHAR(32) COMMENT '车主身份证',
    `owner_phone` VARCHAR(20) COMMENT '车主电话',
    `target_type` TINYINT NOT NULL COMMENT '布控类型: 1-涉案车辆 2-违法未处理 3-重点关注 4-被盗抢',
    `target_level` TINYINT NOT NULL DEFAULT 2 COMMENT '布控级别: 1-一级 2-二级 3-三级',
    `control_reason` VARCHAR(512) COMMENT '布控原因',
    `control_start_time` DATETIME NOT NULL COMMENT '布控开始时间',
    `control_end_time` DATETIME COMMENT '布控结束时间',
    `vehicle_image_url` VARCHAR(512) COMMENT '车辆图片URL',
    `plate_feature` TEXT COMMENT '车牌特征向量',
    `description` TEXT COMMENT '特征描述',
    `case_info` TEXT COMMENT '涉案信息',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-已撤控 1-布控中 2-临时暂停',
    `warning_count` INT DEFAULT 0 COMMENT '预警次数',
    `last_warning_time` DATETIME COMMENT '最后预警时间',
    `create_dept_id` BIGINT COMMENT '创建部门ID',
    `create_user_id` BIGINT COMMENT '创建人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_vehicle_no` (`vehicle_no`),
    KEY `idx_plate_number` (`plate_number`),
    KEY `idx_target_type` (`target_type`),
    KEY `idx_target_level` (`target_level`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='布控车辆表';

DROP TABLE IF EXISTS `face_record`;
CREATE TABLE `face_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `camera_id` BIGINT NOT NULL COMMENT '摄像头ID',
    `camera_code` VARCHAR(64) COMMENT '摄像头编码',
    `capture_time` DATETIME NOT NULL COMMENT '抓拍时间',
    `image_url` VARCHAR(512) COMMENT '抓拍图片URL',
    `face_image_url` VARCHAR(512) COMMENT '人脸截图URL',
    `longitude` DECIMAL(12, 8) COMMENT '经度',
    `latitude` DECIMAL(12, 8) COMMENT '纬度',
    `target_person_id` BIGINT COMMENT '匹配到的布控人员ID',
    `person_name` VARCHAR(64) COMMENT '人员姓名',
    `id_card` VARCHAR(32) COMMENT '身份证号',
    `similarity` DECIMAL(5, 2) COMMENT '相似度(0-100)',
    `age` INT COMMENT '年龄估计',
    `gender` TINYINT COMMENT '性别估计: 1-男 2-女',
    `mask` TINYINT COMMENT '是否戴口罩: 0-否 1-是',
    `glasses` TINYINT COMMENT '是否戴眼镜: 0-否 1-是',
    `face_feature` TEXT COMMENT '人脸特征向量',
    `is_warning` TINYINT DEFAULT 0 COMMENT '是否触发预警: 0-否 1-是',
    `warning_id` BIGINT COMMENT '关联预警ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_camera_id` (`camera_id`),
    KEY `idx_capture_time` (`capture_time`),
    KEY `idx_target_person` (`target_person_id`),
    KEY `idx_is_warning` (`is_warning`),
    KEY `idx_id_card` (`id_card`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人脸识别记录表';

DROP TABLE IF EXISTS `plate_record`;
CREATE TABLE `plate_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `camera_id` BIGINT NOT NULL COMMENT '摄像头ID(卡口)',
    `camera_code` VARCHAR(64) COMMENT '摄像头编码',
    `capture_time` DATETIME NOT NULL COMMENT '抓拍时间',
    `image_url` VARCHAR(512) COMMENT '抓拍图片URL',
    `plate_image_url` VARCHAR(512) COMMENT '车牌截图URL',
    `longitude` DECIMAL(12, 8) COMMENT '经度',
    `latitude` DECIMAL(12, 8) COMMENT '纬度',
    `plate_number` VARCHAR(32) NOT NULL COMMENT '识别车牌号',
    `plate_color` VARCHAR(16) COMMENT '车牌颜色',
    `vehicle_color` VARCHAR(32) COMMENT '车身颜色',
    `vehicle_type` VARCHAR(32) COMMENT '车辆类型',
    `vehicle_brand` VARCHAR(64) COMMENT '车辆品牌',
    `target_vehicle_id` BIGINT COMMENT '匹配到的布控车辆ID',
    `similarity` DECIMAL(5, 2) COMMENT '相似度(0-100)',
    `speed` DECIMAL(8, 2) COMMENT '车速(km/h)',
    `is_speeding` TINYINT DEFAULT 0 COMMENT '是否超速: 0-否 1-是',
    `is_running_red_light` TINYINT DEFAULT 0 COMMENT '是否闯红灯: 0-否 1-是',
    `plate_feature` TEXT COMMENT '车牌特征向量',
    `is_warning` TINYINT DEFAULT 0 COMMENT '是否触发预警: 0-否 1-是',
    `warning_id` BIGINT COMMENT '关联预警ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_camera_id` (`camera_id`),
    KEY `idx_capture_time` (`capture_time`),
    KEY `idx_plate_number` (`plate_number`),
    KEY `idx_target_vehicle` (`target_vehicle_id`),
    KEY `idx_is_warning` (`is_warning`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车牌识别记录表';

DROP TABLE IF EXISTS `alert_record`;
CREATE TABLE `alert_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `alert_no` VARCHAR(32) NOT NULL COMMENT '预警编号',
    `alert_type` TINYINT NOT NULL COMMENT '预警类型: 1-重点人员布控 2-车牌识别 3-打架斗殴 4-人群聚集 5-异常行为 6-翻越围栏',
    `alert_level` TINYINT NOT NULL DEFAULT 2 COMMENT '预警级别: 1-高危 2-中危 3-低危',
    `alert_name` VARCHAR(128) NOT NULL COMMENT '预警名称',
    `alert_content` TEXT COMMENT '预警详情',
    `camera_id` BIGINT COMMENT '来源摄像头ID',
    `camera_code` VARCHAR(64) COMMENT '摄像头编码',
    `camera_name` VARCHAR(128) COMMENT '摄像头名称',
    `longitude` DECIMAL(12, 8) COMMENT '经度',
    `latitude` DECIMAL(12, 8) COMMENT '纬度',
    `address` VARCHAR(256) COMMENT '地址',
    `capture_time` DATETIME NOT NULL COMMENT '抓拍时间',
    `alert_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
    `image_url` VARCHAR(512) COMMENT '预警图片URL',
    `video_url` VARCHAR(512) COMMENT '预警视频URL(前后10秒)',
    `snapshot_url` VARCHAR(512) COMMENT '视频截图URL',
    `target_person_id` BIGINT COMMENT '关联布控人员ID',
    `target_vehicle_id` BIGINT COMMENT '关联布控车辆ID',
    `face_record_id` BIGINT COMMENT '关联人脸记录ID',
    `plate_record_id` BIGINT COMMENT '关联车牌记录ID',
    `confidence` DECIMAL(5, 2) COMMENT '置信度(0-100)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-待处理 2-处理中 3-已确认 4-已排除 5-已误报',
    `handler_id` BIGINT COMMENT '处理人ID',
    `handler_name` VARCHAR(64) COMMENT '处理人姓名',
    `handle_time` DATETIME COMMENT '处理时间',
    `handle_result` VARCHAR(512) COMMENT '处理结果',
    `handle_remark` TEXT COMMENT '处理备注',
    `dispatch_status` TINYINT DEFAULT 0 COMMENT '派单状态: 0-未派单 1-已派单',
    `alarm_id` BIGINT COMMENT '关联警情ID',
    `push_status` TINYINT DEFAULT 0 COMMENT '推送状态: 0-未推送 1-已推送',
    `push_time` DATETIME COMMENT '推送时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_no` (`alert_no`),
    KEY `idx_alert_type` (`alert_type`),
    KEY `idx_alert_level` (`alert_level`),
    KEY `idx_status` (`status`),
    KEY `idx_alert_time` (`alert_time`),
    KEY `idx_camera_id` (`camera_id`),
    KEY `idx_target_person` (`target_person_id`),
    KEY `idx_target_vehicle` (`target_vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI预警记录表';

DROP TABLE IF EXISTS `video_storage`;
CREATE TABLE `video_storage` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `video_no` VARCHAR(64) NOT NULL COMMENT '视频编号',
    `video_type` TINYINT NOT NULL COMMENT '类型: 1-告警视频 2-取证视频 3-直播录像 4-会议录像',
    `source_type` TINYINT NOT NULL COMMENT '来源: 1-摄像头 2-执法记录仪 3-无人机 4-手机',
    `source_id` VARCHAR(64) COMMENT '来源ID',
    `camera_id` BIGINT COMMENT '摄像头ID',
    `alert_id` BIGINT COMMENT '关联预警ID',
    `alarm_id` BIGINT COMMENT '关联警情ID',
    `file_name` VARCHAR(256) NOT NULL COMMENT '文件名',
    `file_path` VARCHAR(512) NOT NULL COMMENT '存储路径',
    `file_size` BIGINT COMMENT '文件大小(字节)',
    `file_format` VARCHAR(16) COMMENT '文件格式: mp4/flv/m3u8',
    `duration` INT COMMENT '时长(秒)',
    `resolution` VARCHAR(32) COMMENT '分辨率: 1080p/720p/4K',
    `bitrate` INT COMMENT '码率(kbps)',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `longitude` DECIMAL(12, 8) COMMENT '经度',
    `latitude` DECIMAL(12, 8) COMMENT '纬度',
    `thumbnail_url` VARCHAR(512) COMMENT '缩略图URL',
    `storage_bucket` VARCHAR(64) COMMENT '存储桶(MinIO)',
    `storage_type` TINYINT DEFAULT 1 COMMENT '存储类型: 1-标准存储 2-低频存储 3-归档存储',
    `expire_time` DATETIME COMMENT '过期时间',
    `upload_user_id` BIGINT COMMENT '上传人ID',
    `upload_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `download_count` INT DEFAULT 0 COMMENT '下载次数',
    `play_count` INT DEFAULT 0 COMMENT '播放次数',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-已删除 1-正常 2-转码中 3-已归档',
    `tags` VARCHAR(512) COMMENT '标签(逗号分隔)',
    `description` TEXT COMMENT '描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_video_no` (`video_no`),
    KEY `idx_source` (`source_type`, `source_id`),
    KEY `idx_alert_id` (`alert_id`),
    KEY `idx_alarm_id` (`alarm_id`),
    KEY `idx_upload_time` (`upload_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频存储表';

-- =============================================
-- 5. 应急指挥与协同模块
-- =============================================

DROP TABLE IF EXISTS `emergency_plan`;
CREATE TABLE `emergency_plan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `plan_no` VARCHAR(32) NOT NULL COMMENT '预案编号',
    `plan_name` VARCHAR(128) NOT NULL COMMENT '预案名称',
    `plan_type` VARCHAR(32) NOT NULL COMMENT '预案类型: 火灾/地震/反恐/群体性事件/交通事故',
    `plan_level` TINYINT NOT NULL COMMENT '预案级别: 1-一级 2-二级 3-三级 4-四级',
    `applicable_scene` TEXT COMMENT '适用场景',
    `trigger_condition` TEXT COMMENT '触发条件',
    `organization` TEXT COMMENT '组织机构(各部门职责 JSON)',
    `disposal_process` TEXT COMMENT '处置流程(JSON)',
    `resource_allocation` TEXT COMMENT '资源配置(JSON)',
    `contact_list` TEXT COMMENT '通讯录(JSON)',
    `attachments` VARCHAR(512) COMMENT '附件URL',
    `version` VARCHAR(32) DEFAULT '1.0' COMMENT '版本号',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-草稿 1-已发布 2-已停用',
    `create_dept_id` BIGINT COMMENT '创建部门',
    `create_user_id` BIGINT COMMENT '创建人',
    `approve_user_id` BIGINT COMMENT '审批人',
    `approve_time` DATETIME COMMENT '审批时间',
    `exercise_count` INT DEFAULT 0 COMMENT '演练次数',
    `last_exercise_time` DATETIME COMMENT '最后演练时间',
    `use_count` INT DEFAULT 0 COMMENT '实际使用次数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_no` (`plan_no`),
    KEY `idx_plan_type` (`plan_type`),
    KEY `idx_plan_level` (`plan_level`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应急预案表';

DROP TABLE IF EXISTS `drone_device`;
CREATE TABLE `drone_device` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `drone_code` VARCHAR(64) NOT NULL COMMENT '无人机编号',
    `drone_name` VARCHAR(128) COMMENT '无人机名称',
    `drone_model` VARCHAR(64) COMMENT '型号',
    `manufacturer` VARCHAR(64) COMMENT '厂商',
    `purchase_date` DATE COMMENT '采购日期',
    `department_id` BIGINT COMMENT '所属部门',
    `pilot_id` BIGINT COMMENT '飞手ID',
    `battery_capacity` INT COMMENT '电池容量(mAh)',
    `max_flight_time` INT COMMENT '最大续航时间(分钟)',
    `max_flight_height` INT COMMENT '最大飞行高度(米)',
    `max_flight_distance` INT COMMENT '最大飞行距离(米)',
    `camera_resolution` VARCHAR(32) COMMENT '相机分辨率',
    `has_thermal_imaging` TINYINT DEFAULT 0 COMMENT '是否有热成像: 0-否 1-是',
    `has_loudspeaker` TINYINT DEFAULT 0 COMMENT '是否有扩音: 0-否 1-是',
    `has_thrower` TINYINT DEFAULT 0 COMMENT '是否有抛投: 0-否 1-是',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0-离线 1-待命 2-飞行中 3-充电中 4-维修中',
    `current_longitude` DECIMAL(12, 8) COMMENT '当前经度',
    `current_latitude` DECIMAL(12, 8) COMMENT '当前纬度',
    `current_altitude` DECIMAL(8, 2) COMMENT '当前高度(米)',
    `current_speed` DECIMAL(8, 2) COMMENT '当前速度(km/h)',
    `current_battery` INT COMMENT '当前电量(%)',
    `current_signal` INT COMMENT '当前信号强度(%)',
    `current_task_id` BIGINT COMMENT '当前执行任务ID',
    `last_maintenance_time` DATETIME COMMENT '最后维护时间',
    `next_maintenance_time` DATETIME COMMENT '下次维护时间',
    `total_flight_time` INT DEFAULT 0 COMMENT '累计飞行时间(小时)',
    `total_flight_count` INT DEFAULT 0 COMMENT '累计飞行架次',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drone_code` (`drone_code`),
    KEY `idx_status` (`status`),
    KEY `idx_department_id` (`department_id`),
    KEY `idx_pilot_id` (`pilot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='无人机设备表';

-- =============================================
-- 6. 执法监督模块
-- =============================================

DROP TABLE IF EXISTS `enforcement_record`;
CREATE TABLE `enforcement_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_no` VARCHAR(32) NOT NULL COMMENT '记录编号',
    `enforcement_type` VARCHAR(32) NOT NULL COMMENT '执法类型: 现场处置/案件办理/调解/检查',
    `enforcer_id` BIGINT NOT NULL COMMENT '执法人员ID',
    `enforcer_name` VARCHAR(64) COMMENT '执法人员姓名',
    `enforcer_police_no` VARCHAR(32) COMMENT '执法人员警号',
    `partner_ids` VARCHAR(256) COMMENT '陪同人员ID列表',
    `alarm_id` BIGINT COMMENT '关联警情ID',
    `case_id` VARCHAR(64) COMMENT '案件编号',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `longitude` DECIMAL(12, 8) COMMENT '执法地点经度',
    `latitude` DECIMAL(12, 8) COMMENT '执法地点纬度',
    `address` VARCHAR(256) COMMENT '执法地点',
    `party_name` VARCHAR(64) COMMENT '当事人姓名',
    `party_id_card` VARCHAR(32) COMMENT '当事人身份证',
    `party_phone` VARCHAR(20) COMMENT '当事人电话',
    `enforcement_content` TEXT COMMENT '执法内容',
    `enforcement_result` TEXT COMMENT '执法结果',
    `video_count` INT DEFAULT 0 COMMENT '关联视频数',
    `photo_count` INT DEFAULT 0 COMMENT '关联照片数',
    `audio_count` INT DEFAULT 0 COMMENT '关联录音数',
    `evidence_urls` TEXT COMMENT '证据文件URL列表(JSON)',
    `is_standard` TINYINT DEFAULT 1 COMMENT '是否规范: 0-不规范 1-规范',
    `standard_score` INT COMMENT '规范评分(0-100)',
    `standard_problem` TEXT COMMENT '存在问题',
    `review_status` TINYINT DEFAULT 0 COMMENT '审核状态: 0-待审核 1-已审核 2-需整改',
    `reviewer_id` BIGINT COMMENT '审核人ID',
    `review_time` DATETIME COMMENT '审核时间',
    `review_opinion` TEXT COMMENT '审核意见',
    `rectification_status` TINYINT DEFAULT 0 COMMENT '整改状态: 0-无需整改 1-待整改 2-已整改 3-已验证',
    `rectification_time` DATETIME COMMENT '整改时间',
    `rectification_content` TEXT COMMENT '整改内容',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_no` (`record_no`),
    KEY `idx_enforcer_id` (`enforcer_id`),
    KEY `idx_alarm_id` (`alarm_id`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_review_status` (`review_status`),
    KEY `idx_is_standard` (`is_standard`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='执法记录表';

-- =============================================
-- 7. 初始化数据
-- =============================================

-- 角色数据
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`) VALUES
(1, '超级管理员', 'admin', '系统超级管理员，拥有所有权限'),
(2, '指挥长', 'commander', '指挥中心负责人，负责全局指挥调度'),
(3, '指挥调度员', 'dispatcher', '指挥中心调度员，负责接警派单'),
(4, '值班长', 'shift_leader', '值班长，负责班组管理'),
(5, '执勤民警', 'police', '一线执勤民警'),
(6, '视频巡查员', 'video_inspector', '视频监控巡查员');

-- 部门数据
INSERT INTO `sys_department` (`id`, `parent_id`, `dept_name`, `dept_code`, `longitude`, `latitude`) VALUES
(1, 0, '公安局指挥中心', 'JZZHZX', 116.397128, 39.916527),
(2, 1, '情报指挥科', 'QBZHK', 116.397128, 39.916527),
(3, 1, '视频侦查科', 'SPZCK', 116.397128, 39.916527),
(4, 1, '合成作战科', 'HZZZK', 116.397128, 39.916527),
(5, 0, '交警支队', 'JJZD', 116.387128, 39.906527),
(6, 0, '刑侦支队', 'XZZD', 116.407128, 39.926527),
(7, 0, '派出所A', 'PCS_A', 116.400128, 39.910527),
(8, 0, '派出所B', 'PCS_B', 116.410128, 39.920527),
(9, 0, '派出所C', 'PCS_C', 116.390128, 39.900527);

-- 用户数据 (密码: 123456)
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `police_number`, `phone`, `department_id`, `rank`, `status`) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', '000001', '13800138000', 1, '一级警督', 1),
(2, 'commander', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张指挥', '000002', '13800138001', 1, '二级警督', 1),
(3, 'dispatcher01', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李调度', '000003', '13800138002', 2, '三级警督', 1),
(4, 'dispatcher02', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '王调度', '000004', '13800138003', 2, '一级警司', 1),
(5, 'police01', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '赵警官', '000101', '13800138011', 7, '二级警司', 1),
(6, 'police02', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '钱警官', '000102', '13800138012', 7, '三级警司', 1),
(7, 'police03', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '孙警官', '000201', '13800138021', 8, '二级警司', 1),
(8, 'police04', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '周警官', '000301', '13800138031', 9, '一级警司', 1);

-- 用户角色关联
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1), (2, 2), (3, 3), (4, 3), (5, 5), (6, 5), (7, 5), (8, 5);

-- 地图图层
INSERT INTO `map_layer` (`id`, `layer_name`, `layer_code`, `layer_type`, `visible`, `sort_order`) VALUES
(1, '警力分布', 'police', 'police', 1, 1),
(2, '警情热点', 'alarm', 'alarm', 1, 2),
(3, '监控点位', 'camera', 'camera', 1, 3),
(4, '交通卡口', 'traffic', 'traffic', 1, 4),
(5, '重点区域', 'keyarea', 'keyarea', 1, 5);

-- 巡逻车辆
INSERT INTO `patrol_car` (`id`, `car_number`, `car_type`, `device_id`, `department_id`, `status`, `load_capacity`, `longitude`, `latitude`) VALUES
(1, '京A0001警', '警车', 'CAR001', 7, 1, 5, 116.401128, 39.911527),
(2, '京A0002警', '警车', 'CAR002', 7, 1, 5, 116.402128, 39.912527),
(3, '京A0003警', '特警車', 'CAR003', 6, 1, 8, 116.408128, 39.927527),
(4, '京A0004警', '消防车', 'CAR004', 1, 1, 6, 116.398128, 39.917527),
(5, '京A0005警', '警车', 'CAR005', 8, 1, 5, 116.411128, 39.921527),
(6, '京A0006警', '警车', 'CAR006', 9, 1, 5, 116.391128, 39.901527);

-- 摄像头数据
INSERT INTO `camera_device` (`id`, `camera_code`, `camera_name`, `camera_type`, `manufacturer`, `ip_address`, `rtsp_url`, `longitude`, `latitude`, `address`, `coverage_radius`, `ptz_support`, `ai_support`, `status`) VALUES
(1, 'CAM001', '天安门广场东', 1, '海康威视', '192.168.1.101', 'rtsp://192.168.1.101:554/stream1', 116.397128, 39.916527, '北京市东城区天安门广场东侧', 150, 1, 1, 1),
(2, 'CAM002', '天安门广场西', 2, '海康威视', '192.168.1.102', 'rtsp://192.168.1.102:554/stream1', 116.396128, 39.915527, '北京市东城区天安门广场西侧', 150, 1, 1, 1),
(3, 'CAM003', '王府井大街北', 1, '大华', '192.168.1.103', 'rtsp://192.168.1.103:554/stream1', 116.410128, 39.915527, '北京市东城区王府井大街北段', 100, 0, 1, 1),
(4, 'CAM004', '王府井大街南', 1, '大华', '192.168.1.104', 'rtsp://192.168.1.104:554/stream1', 116.409128, 39.914527, '北京市东城区王府井大街南段', 100, 0, 1, 1),
(5, 'CAM005', '北京站东', 4, '华为', '192.168.1.105', 'rtsp://192.168.1.105:554/stream1', 116.427128, 39.903527, '北京市东城区北京火车站东侧', 200, 0, 1, 1),
(6, 'CAM006', '北京站西', 4, '华为', '192.168.1.106', 'rtsp://192.168.1.106:554/stream1', 116.426128, 39.902527, '北京市东城区北京火车站西侧', 200, 0, 1, 1),
(7, 'CAM007', '西单路口东', 2, '海康威视', '192.168.1.107', 'rtsp://192.168.1.107:554/stream1', 116.375128, 39.913527, '北京市西城区西单路口东侧', 150, 1, 1, 1),
(8, 'CAM008', '西单路口西', 2, '海康威视', '192.168.1.108', 'rtsp://192.168.1.108:554/stream1', 116.374128, 39.912527, '北京市西城区西单路口西侧', 150, 1, 1, 1);

-- 重点区域
INSERT INTO `key_area` (`id`, `area_name`, `area_type`, `area_level`, `address`, `polygon_coords`, `center_longitude`, `center_latitude`, `person_in_charge`, `contact_phone`, `police_station`, `risk_level`) VALUES
(1, '天安门广场', 'government', 1, '北京市东城区天安门广场', '[[116.395,39.914],[116.399,39.914],[116.399,39.918],[116.395,39.918],[116.395,39.914]]', 116.397, 39.916, '张主任', '13800138001', '天安门派出所', 1),
(2, '北京站', 'station', 1, '北京市东城区毛家湾胡同甲13号', '[[116.424,39.900],[116.430,39.900],[116.430,39.906],[116.424,39.906],[116.424,39.900]]', 116.427, 39.903, '李站长', '13800138002', '北京站派出所', 1),
(3, '王府井步行街', 'mall', 2, '北京市东城区王府井大街', '[[116.407,39.912],[116.412,39.912],[116.412,39.918],[116.407,39.918],[116.407,39.912]]', 116.4095, 39.915, '王经理', '13800138003', '王府井派出所', 2),
(4, '协和医院', 'hospital', 2, '北京市东城区东单帅府园1号', '[[116.415,39.910],[116.420,39.910],[116.420,39.915],[116.415,39.915],[116.415,39.910]]', 116.4175, 39.9125, '赵院长', '13800138004', '东单派出所', 2),
(5, '北京四中', 'school', 3, '北京市西城区地安门西大街甲89号', '[[116.380,39.930],[116.385,39.930],[116.385,39.935],[116.380,39.935],[116.380,39.930]]', 116.3825, 39.9325, '孙校长', '13800138005', '地安门派出所', 3);

-- 布控人员示例
INSERT INTO `target_person` (`id`, `person_no`, `name`, `id_card`, `gender`, `target_type`, `target_level`, `control_reason`, `control_start_time`, `status`) VALUES
(1, 'BP001', '张三', '110101198001011234', 1, 1, 1, '涉嫌重大刑事案件，在逃人员', NOW(), 1),
(2, 'BP002', '李四', '110101198505055678', 1, 2, 2, '涉嫌盗窃，多次作案', NOW(), 1),
(3, 'BP003', '王五', '110101199003039012', 2, 3, 2, '失踪人员，家属报案', NOW(), 1);

-- 布控车辆示例
INSERT INTO `target_vehicle` (`id`, `vehicle_no`, `plate_number`, `plate_color`, `vehicle_type`, `vehicle_color`, `target_type`, `target_level`, `control_reason`, `control_start_time`, `status`) VALUES
(1, 'BV001', '京A88888', '蓝牌', '小轿车', '黑色', 1, 1, '涉案车辆，涉嫌抢劫', NOW(), 1),
(2, 'BV002', '京B66666', '蓝牌', 'SUV', '白色', 2, 2, '50次违法未处理', NOW(), 1);

-- 无人机设备
INSERT INTO `drone_device` (`id`, `drone_code`, `drone_name`, `drone_model`, `manufacturer`, `department_id`, `max_flight_time`, `camera_resolution`, `has_thermal_imaging`, `has_loudspeaker`, `status`) VALUES
(1, 'DRONE001', '侦查无人机1号', 'DJI Mavic 3E', '大疆创新', 3, 45, '2000万像素', 1, 1, 1),
(2, 'DRONE002', '侦查无人机2号', 'DJI Mavic 3T', '大疆创新', 3, 45, '2000万像素+热成像', 1, 1, 1),
(3, 'DRONE003', '喊话无人机', 'DJI Matrice 300 RTK', '大疆创新', 6, 40, '2000万像素', 0, 1, 1);

-- 应急预案示例
INSERT INTO `emergency_plan` (`id`, `plan_no`, `plan_name`, `plan_type`, `plan_level`, `applicable_scene`, `status`, `create_dept_id`) VALUES
(1, 'EP001', '大规模群体性事件处置预案', '群体性事件', 1, '发生50人以上群体性聚集、上访、示威等事件', 1, 1),
(2, 'EP002', '恐怖袭击事件处置预案', '反恐', 1, '发生爆炸、纵火、劫持人质等恐怖袭击事件', 1, 1),
(3, 'EP003', '重大火灾事故处置预案', '火灾', 2, '发生造成人员伤亡或重大财产损失的火灾', 1, 1),
(4, 'EP004', '重大交通事故处置预案', '交通事故', 2, '发生3人以上死亡或10人以上受伤的交通事故', 1, 5);

-- =============================================
-- 8. 索引优化说明
-- =============================================
-- 1. 所有表都有主键索引
-- 2. 常用查询字段都建立了单列索引
-- 3. 位置查询建立了联合索引 (longitude, latitude)
-- 4. 时间范围查询建立了时间字段索引
-- 5. 状态字段都建立了索引便于快速筛选
-- 6. 唯一性约束通过唯一索引实现

-- =============================================
-- 9. 默认账号说明
-- =============================================
-- 管理员账号: admin / 123456
-- 指挥长账号: commander / 123456
-- 调度员账号: dispatcher01 / 123456
-- 民警账号: police01 / 123456

-- =============================================
-- 初始化完成
-- =============================================