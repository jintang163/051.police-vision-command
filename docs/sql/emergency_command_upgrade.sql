-- ============================================================================
-- 应急指挥调度系统升级脚本
-- 功能：1)预案模板化 2)指令闭环 3)封控区图上作业 4)应急物资 5)WebRTC视频会商
-- 创建：基于 police-vision-event 模块
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 扩展 sec_security_plan（安保预案表）新增模板化字段
-- ---------------------------------------------------------------------------
ALTER TABLE sec_security_plan
    ADD COLUMN plan_template_code       VARCHAR(64)     COMMENT '预案模板编码(对应枚举EmergencyPlanTemplateEnum)',
    ADD COLUMN is_template              TINYINT(1)  DEFAULT 0 COMMENT '是否为模板(0-实例预案/1-系统模板)',
    ADD COLUMN emergency_level          VARCHAR(32)     COMMENT '应急响应级别(TERROR_RESPONSE_LEVEL_1等Nacos配置key)',
    ADD COLUMN resource_radius          INT         DEFAULT 500 COMMENT '默认资源搜索半径(米)',
    ADD COLUMN auto_allocate_resources  TINYINT(1)  DEFAULT 1 COMMENT '启动时是否自动调配周边资源',
    ADD COLUMN auto_start_video_conference TINYINT(1) DEFAULT 1 COMMENT '启动时是否自动创建视频会商室',
    ADD COLUMN nacos_config_key         VARCHAR(128)    COMMENT '关联Nacos动态配置key',
    ADD COLUMN description              VARCHAR(1024)   COMMENT '预案模板说明';

CREATE INDEX idx_plan_template_code ON sec_security_plan(plan_template_code);
CREATE INDEX idx_plan_is_template ON sec_security_plan(is_template);

-- ---------------------------------------------------------------------------
-- 2. sec_emergency_command（应急指令主表 - 闭环追踪）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sec_emergency_command;
CREATE TABLE sec_emergency_command (
    id                  BIGINT          NOT NULL COMMENT '主键ID(雪花算法)',
    event_id            VARCHAR(64)     NOT NULL COMMENT '关联事件ID',
    plan_id             VARCHAR(64)     DEFAULT NULL COMMENT '关联预案ID',
    command_no          VARCHAR(64)     NOT NULL COMMENT '指令编号(自动生成: CMD-YYYYMMDDHHMMSS-xxxx)',
    command_title       VARCHAR(200)    NOT NULL COMMENT '指令标题',
    command_content     TEXT            NOT NULL COMMENT '指令内容',
    priority            INT         DEFAULT 3 COMMENT '优先级(1-紧急#ff4d4f/2-高#faad14/3-普通#1890ff/4-低#52c41a)',
    status              INT         DEFAULT 0 COMMENT '状态(0-已创建/1-已下达/2-已接收/3-执行中/4-已反馈/5-已完成/6-已取消/7-已超时)',
    sender_id           VARCHAR(64)     DEFAULT NULL COMMENT '下发人ID',
    sender_name         VARCHAR(100)    DEFAULT NULL COMMENT '下发人姓名',
    sender_dept         VARCHAR(200)    DEFAULT NULL COMMENT '下发人部门',
    receiver_dept_ids   JSON            DEFAULT NULL COMMENT '接收部门ID列表(JSON数组)',
    receiver_names      VARCHAR(1024)   DEFAULT NULL COMMENT '接收单位名称(逗号分隔/便于展示)',
    dispatch_time       DATETIME        DEFAULT NULL COMMENT '下达时间',
    receive_time        DATETIME        DEFAULT NULL COMMENT '接收时间',
    execute_start_time  DATETIME        DEFAULT NULL COMMENT '开始执行时间',
    feedback_time       DATETIME        DEFAULT NULL COMMENT '反馈时间',
    complete_time       DATETIME        DEFAULT NULL COMMENT '完成时间',
    deadline_minutes    INT         DEFAULT 60 COMMENT '时限要求(分钟)',
    timeout_count       INT         DEFAULT 0 COMMENT '超时计数',
    feedback_content    TEXT            DEFAULT NULL COMMENT '执行反馈内容',
    attach_files        JSON            DEFAULT NULL COMMENT '附件列表(JSON)',
    extra_data          JSON            DEFAULT NULL COMMENT '扩展数据',
    status              INT         DEFAULT 0 COMMENT '备用(预留字段)',
    create_by           VARCHAR(64)     DEFAULT NULL,
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT NULL,
    update_time         DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark              VARCHAR(500)    DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_command_no (command_no),
    KEY idx_cmd_event_id (event_id),
    KEY idx_cmd_status (status),
    KEY idx_cmd_priority (priority),
    KEY idx_cmd_dispatch_time (dispatch_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应急指令表(闭环追踪)';

-- ---------------------------------------------------------------------------
-- 3. sec_command_status_log（指令状态流水日志）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sec_command_status_log;
CREATE TABLE sec_command_status_log (
    id              BIGINT          NOT NULL COMMENT '主键',
    command_id      BIGINT          NOT NULL COMMENT '指令ID',
    event_id        VARCHAR(64)     DEFAULT NULL COMMENT '事件ID(冗余便于查询)',
    from_status     INT         DEFAULT NULL COMMENT '源状态',
    to_status       INT             NOT NULL COMMENT '目标状态',
    operator_id     VARCHAR(64)     DEFAULT NULL COMMENT '操作人ID',
    operator_name   VARCHAR(100)    DEFAULT NULL COMMENT '操作人姓名',
    operator_dept   VARCHAR(200)    DEFAULT NULL COMMENT '操作人部门',
    operate_time    DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    operate_remark  VARCHAR(1024)   DEFAULT NULL COMMENT '操作备注',
    extra_data      JSON            DEFAULT NULL COMMENT '扩展数据',
    PRIMARY KEY (id),
    KEY idx_log_command_id (command_id),
    KEY idx_log_event_id (event_id),
    KEY idx_log_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指令状态流转日志';

-- ---------------------------------------------------------------------------
-- 4. sec_emergency_fence（应急封控区 - 图上作业）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sec_emergency_fence;
CREATE TABLE sec_emergency_fence (
    id              VARCHAR(64)     NOT NULL COMMENT '主键ID',
    event_id        VARCHAR(64)     NOT NULL COMMENT '关联事件ID',
    fence_code      VARCHAR(64)     DEFAULT NULL COMMENT '封控区编码',
    fence_name      VARCHAR(200)    NOT NULL COMMENT '封控区名称',
    fence_type      VARCHAR(32)     NOT NULL COMMENT '封控区类型(blockade-封控/control-管控/prevention-防范/assembly-集结/checkpoint-检查点)',
    fence_geometry  JSON            NOT NULL COMMENT '多边形GeoJSON(含type+coordinates)',
    center_lng      DECIMAL(12,8)   DEFAULT NULL COMMENT '区域中心经度',
    center_lat      DECIMAL(12,8)   DEFAULT NULL COMMENT '区域中心纬度',
    radius_meters   INT         DEFAULT 500 COMMENT '半径/影响距离(米)',
    fill_color      VARCHAR(64)     DEFAULT NULL COMMENT '填充颜色(rgba)',
    stroke_color    VARCHAR(32)     DEFAULT NULL COMMENT '描边颜色',
    stroke_weight   INT         DEFAULT 2 COMMENT '描边线宽(像素)',
    opacity         DECIMAL(4,2)    DEFAULT 0.7 COMMENT '透明度0~1',
    sort_order      INT         DEFAULT 0 COMMENT '显示排序',
    status          INT         DEFAULT 1 COMMENT '状态(1-生效/0-停用)',
    create_by       VARCHAR(64)     DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark          VARCHAR(500)    DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_fence_event_id (event_id),
    KEY idx_fence_type (fence_type),
    KEY idx_fence_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应急封控区(图上作业)';

-- ---------------------------------------------------------------------------
-- 5. sec_emergency_supply（应急物资）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sec_emergency_supply;
CREATE TABLE sec_emergency_supply (
    id              VARCHAR(64)     NOT NULL COMMENT '主键',
    event_id        VARCHAR(64)     NOT NULL COMMENT '关联事件ID(临时调配则填)',
    plan_id         VARCHAR(64)     DEFAULT NULL COMMENT '关联预案ID',
    supply_type     VARCHAR(32)     NOT NULL COMMENT '物资类别(equipment-装备/communication-通讯/medical-医疗/logistics-后勤)',
    supply_name     VARCHAR(200)    NOT NULL COMMENT '物资名称',
    quantity        INT         DEFAULT 0 COMMENT '数量',
    unit            VARCHAR(32)     DEFAULT '个' COMMENT '计量单位(个/套/台/箱)',
    longitude       DECIMAL(12,8)   DEFAULT NULL COMMENT '存放经度',
    latitude        DECIMAL(12,8)   DEFAULT NULL COMMENT '存放纬度',
    address         VARCHAR(500)    DEFAULT NULL COMMENT '存放地址',
    contact_person  VARCHAR(100)    DEFAULT NULL COMMENT '联络人',
    contact_phone   VARCHAR(64)     DEFAULT NULL COMMENT '联系电话',
    distance_meters INT         DEFAULT NULL COMMENT '距离事件点距离(计算值)',
    status          INT         DEFAULT 1 COMMENT '状态(1-可用/2-已调度/0-不可用)',
    create_by       VARCHAR(64)     DEFAULT NULL,
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT NULL,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    remark          VARCHAR(500)    DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_supply_event_id (event_id),
    KEY idx_supply_type (supply_type),
    KEY idx_supply_name (supply_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应急物资表';

-- ---------------------------------------------------------------------------
-- 6. 初始化：预置预案模板数据（插入到 sec_security_plan）
-- ---------------------------------------------------------------------------
INSERT INTO sec_security_plan
    (id, name, plan_template_code, is_template, emergency_level, resource_radius,
     auto_allocate_resources, auto_start_video_conference, nacos_config_key, description,
     create_time, status, create_by)
VALUES
    ('PLAN_TPL_001', '暴恐袭击应急预案',        'terrorism',  1, 'I级', 500, 1, 1, 'TERROR_RESPONSE_LEVEL_1',
     '发生恐怖袭击时启动：成立反恐指挥部、调度特警/排爆/谈判专家、500米半径严格封控、多部门协同。',
     NOW(), 1, 'system'),

    ('PLAN_TPL_002', '劫持人质应急预案',        'kidnapping', 1, 'I级', 500, 1, 1, 'KIDNAP_RESPONSE_LEVEL_1',
     '发生劫持人质时启动：谈判组+突击组双线部署、狙击手布控、媒体管控、家属安抚。',
     NOW(), 1, 'system'),

    ('PLAN_TPL_003', '火灾爆炸应急预案',        'fire',       1, 'II级', 500, 1, 1, 'FIRE_RESPONSE_LEVEL_2',
     '发生火灾/爆炸时启动：消防优先、周边疏散、危险源排查、医疗救护、环境监测。',
     NOW(), 1, 'system'),

    ('PLAN_TPL_004', '群体性事件应急预案',      'crowd',      1, 'II级', 1000, 1, 1, 'CROWD_RESPONSE_LEVEL_2',
     '发生群体性聚集/骚乱时启动：人群分割、重点人员识别、舆情监测、宣传劝导。',
     NOW(), 1, 'system'),

    ('PLAN_TPL_005', '重大交通事故应急预案',    'traffic',    1, 'III级', 500, 1, 0, 'TRAFFIC_RESPONSE_LEVEL_3',
     '重大交通事故：破拆救援、医疗救护、交通疏导、事故鉴定、危险品处理。',
     NOW(), 1, 'system'),

    ('PLAN_TPL_006', '公共卫生突发事件预案',    'health',     1, 'I级', 2000, 1, 1, 'HEALTH_RESPONSE_LEVEL_1',
     '传染病/食物中毒等：疾控介入、溯源调查、隔离管控、医疗救治、物资保供。',
     NOW(), 1, 'system'),

    ('PLAN_TPL_007', '自然灾害救援应急预案',    'disaster',   1, 'II级', 1000, 1, 1, 'DISASTER_RESPONSE_LEVEL_2',
     '地震/洪水/台风：人员搜救、转移安置、物资投放、道路抢修、通信保障。',
     NOW(), 1, 'system'),

    ('PLAN_TPL_008', '人群踩踏应急预案',        'stampede',   1, 'I级', 500, 1, 1, 'STAMPEDE_RESPONSE_LEVEL_1',
     '踩踏事故：紧急疏散、生命救援、通道清理、现场检伤分类、舆情回应。',
     NOW(), 1, 'system');

-- ============================================================================
-- 注意事项：
-- 1) RocketMQ需要预先创建的Topic：
--    EMERGENCY_COMMAND_TOPIC  (指令消息, 分区8)
--    WEBRTC_SIGNAL_TOPIC      (WebRTC信令, 分区4)
-- 2) ConsumerGroup：
--    EMERGENCY_COMMAND_GROUP          (CLUSTERING - 集群消费处理业务)
--    EMERGENCY_COMMAND_BROADCAST_GROUP (BROADCASTING - 广播推送)
--    WEBRTC_SIGNAL_GROUP              (BROADCASTING - 信令广播)
-- 3) Nacos需要配置（dataId: police-vision-event.yml）：
--    emergency.default.resource.radius: 500
--    emergency.default.template: terrorism
--    emergency.command.default.deadline.minutes: 60
--    sentinel.emergency.plan.start.qps: 10
--    sentinel.emergency.command.dispatch.qps: 50
-- ============================================================================
