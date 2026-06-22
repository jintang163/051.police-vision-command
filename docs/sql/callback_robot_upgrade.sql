-- =====================================================
-- 警情智能回访机器人升级脚本
-- 版本: 1.0.0
-- 日期: 2026-06-22
-- 说明: 创建回访任务、回访结果、话术模板三张表及预置数据
-- =====================================================

-- -----------------------------------------------------
-- 8.1 回访任务表 callback_task
-- -----------------------------------------------------
DROP TABLE IF EXISTS `callback_task`;
CREATE TABLE `callback_task` (
    `id`                    BIGINT          NOT NULL                COMMENT '主键ID',
    `callback_task_id`      VARCHAR(64)     NOT NULL                COMMENT '回访任务ID(业务主键)',
    `callback_task_no`      VARCHAR(64)     DEFAULT NULL            COMMENT '任务编号',
    `source_type`           TINYINT         DEFAULT NULL            COMMENT '来源类型(1:结案案件 2:投诉工单 3:重点警情)',
    `source_type_name`      VARCHAR(32)     DEFAULT NULL            COMMENT '来源类型名称',
    `source_id`             VARCHAR(64)     DEFAULT NULL            COMMENT '来源ID(案件ID/工单ID等)',
    `source_no`             VARCHAR(64)     DEFAULT NULL            COMMENT '来源编号',
    `case_type`             VARCHAR(32)     DEFAULT NULL            COMMENT '警情/案件类型',
    `case_type_name`        VARCHAR(64)     DEFAULT NULL            COMMENT '警情/案件类型名称',
    `brief_description`     VARCHAR(500)    DEFAULT NULL            COMMENT '警情简述',
    `alert_officer_id`      BIGINT          DEFAULT NULL            COMMENT '处警警员ID',
    `alert_officer_name`    VARCHAR(64)     DEFAULT NULL            COMMENT '处警警员姓名',
    `alert_dept_code`       VARCHAR(32)     DEFAULT NULL            COMMENT '处警单位代码',
    `alert_dept_name`       VARCHAR(128)    DEFAULT NULL            COMMENT '处警单位名称',
    `reporter_name`         VARCHAR(64)     DEFAULT NULL            COMMENT '报案人姓名',
    `reporter_phone`        VARCHAR(20)     DEFAULT NULL            COMMENT '报案人联系电话',
    `reporter_id_card`      VARCHAR(32)     DEFAULT NULL            COMMENT '报案人身份证(可选)',
    `report_time`           DATETIME        DEFAULT NULL            COMMENT '报案时间',
    `close_time`            DATETIME        DEFAULT NULL            COMMENT '结案时间',
    `close_dept_code`       VARCHAR(32)     DEFAULT NULL            COMMENT '结案单位代码',
    `close_dept_name`       VARCHAR(128)    DEFAULT NULL            COMMENT '结案单位名称',
    `scheduled_time`        DATETIME        DEFAULT NULL            COMMENT '计划回访时间(结案后24h)',
    `template_id`           VARCHAR(64)     DEFAULT NULL            COMMENT '话术模板ID',
    `template_name`         VARCHAR(128)    DEFAULT NULL            COMMENT '话术模板名称',
    `priority`              TINYINT         DEFAULT 2               COMMENT '优先级(1:高 2:中 3:低)',
    `task_status`           TINYINT         DEFAULT 0               COMMENT '任务状态(0:待发起 1:呼叫中 2:已完成 3:呼叫失败 4:需人工回访 5:已过期)',
    `task_status_name`      VARCHAR(32)     DEFAULT NULL            COMMENT '任务状态名称',
    `call_id`               VARCHAR(128)    DEFAULT NULL            COMMENT '阿里云呼叫ID',
    `call_start_time`       DATETIME        DEFAULT NULL            COMMENT '呼叫开始时间',
    `call_end_time`         DATETIME        DEFAULT NULL            COMMENT '呼叫结束时间',
    `call_duration`         INT             DEFAULT 0               COMMENT '通话时长(秒)',
    `call_result`           VARCHAR(32)     DEFAULT NULL            COMMENT '呼叫结果码',
    `call_result_msg`       VARCHAR(256)    DEFAULT NULL            COMMENT '呼叫结果描述',
    `call_times`            TINYINT         DEFAULT 0               COMMENT '呼叫次数',
    `last_attempt_time`     DATETIME        DEFAULT NULL            COMMENT '最后一次尝试时间',
    `next_attempt_time`     DATETIME        DEFAULT NULL            COMMENT '下次尝试时间',
    `max_retry_times`       TINYINT         DEFAULT 3               COMMENT '最大重试次数(默认3)',
    `transfer_human_flag`   TINYINT         DEFAULT 0               COMMENT '是否转人工(0:否 1:是)',
    `transfer_human_reason` VARCHAR(256)    DEFAULT NULL            COMMENT '转人工原因',
    `transfer_human_time`   DATETIME        DEFAULT NULL            COMMENT '转人工时间',
    `human_officer_id`      BIGINT          DEFAULT NULL            COMMENT '人工回访员ID',
    `human_officer_name`    VARCHAR(64)     DEFAULT NULL            COMMENT '人工回访员姓名',
    `human_finish_time`     DATETIME        DEFAULT NULL            COMMENT '人工回访完成时间',
    `area_code`             VARCHAR(32)     DEFAULT NULL            COMMENT '区划代码',
    `area_name`             VARCHAR(128)    DEFAULT NULL            COMMENT '区划名称',
    `remark`                VARCHAR(500)    DEFAULT NULL            COMMENT '备注',
    `create_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`             BIGINT          DEFAULT NULL            COMMENT '创建人',
    `update_by`             BIGINT          DEFAULT NULL            COMMENT '更新人',
    `deleted`               TINYINT         DEFAULT 0               COMMENT '删除标记(0:未删除 1:已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_callback_task_id` (`callback_task_id`),
    KEY `idx_task_status` (`task_status`),
    KEY `idx_scheduled_time` (`scheduled_time`),
    KEY `idx_source_id` (`source_id`),
    KEY `idx_reporter_phone` (`reporter_phone`),
    KEY `idx_close_time` (`close_time`),
    KEY `idx_transfer_human_flag` (`transfer_human_flag`),
    KEY `idx_area_code` (`area_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回访任务表';

-- -----------------------------------------------------
-- 8.2 回访结果表 callback_result
-- -----------------------------------------------------
DROP TABLE IF EXISTS `callback_result`;
CREATE TABLE `callback_result` (
    `id`                        BIGINT          NOT NULL                COMMENT '主键ID',
    `callback_result_id`        VARCHAR(64)     NOT NULL                COMMENT '回访结果ID(业务主键)',
    `callback_task_id`          VARCHAR(64)     NOT NULL                COMMENT '关联回访任务ID',
    `callback_task_no`          VARCHAR(64)     DEFAULT NULL            COMMENT '关联任务编号',
    `call_id`                   VARCHAR(128)    DEFAULT NULL            COMMENT '阿里云呼叫ID',
    `call_duration`             INT             DEFAULT 0               COMMENT '通话时长(秒)',
    `recording_url`             VARCHAR(500)    DEFAULT NULL            COMMENT '通话录音URL',
    `asr_full_text`             TEXT            DEFAULT NULL            COMMENT 'ASR全文转写',
    `asr_json`                  MEDIUMTEXT      DEFAULT NULL            COMMENT 'ASR分段JSON',
    `timeliness_score`          TINYINT         DEFAULT NULL            COMMENT '处置及时性评分(1-5)',
    `attitude_score`            TINYINT         DEFAULT NULL            COMMENT '警员态度评分(1-5)',
    `solving_score`             TINYINT         DEFAULT NULL            COMMENT '问题解决度评分(1-5)',
    `overall_score`             TINYINT         DEFAULT NULL            COMMENT '综合评分(三指标均值四舍五入)',
    `satisfaction_level`        TINYINT         DEFAULT NULL            COMMENT '满意度等级(1:非常满意 2:满意 3:一般 4:不满意 5:非常不满意)',
    `satisfaction_level_name`   VARCHAR(32)     DEFAULT NULL            COMMENT '满意度等级名称',
    `sentiment_label`           TINYINT         DEFAULT NULL            COMMENT '情感标签(0:负向 1:中性 2:正向)',
    `sentiment_label_name`      VARCHAR(32)     DEFAULT NULL            COMMENT '情感标签名称',
    `sentiment_score`           DECIMAL(5,4)    DEFAULT NULL            COMMENT '情感置信度(0-1)',
    `sentiment_keywords`        VARCHAR(1000)   DEFAULT NULL            COMMENT '情感关键词(JSON数组)',
    `sentiment_analysis`        VARCHAR(1000)   DEFAULT NULL            COMMENT '情感分析详细描述',
    `complaint_keywords`        VARCHAR(1000)   DEFAULT NULL            COMMENT '投诉关键词(JSON数组)',
    `praise_keywords`           VARCHAR(1000)   DEFAULT NULL            COMMENT '表扬关键词(JSON数组)',
    `suggestion_text`           TEXT            DEFAULT NULL            COMMENT '建议内容',
    `summary_text`              VARCHAR(1000)   DEFAULT NULL            COMMENT 'AI回访摘要',
    `auto_transfer_human`       TINYINT         DEFAULT 0               COMMENT '系统自动转人工(0:否 1:是)',
    `transfer_reason`           VARCHAR(256)    DEFAULT NULL            COMMENT '转人工触发原因',
    `reviewer_id`               BIGINT          DEFAULT NULL            COMMENT '审核人ID',
    `reviewer_name`             VARCHAR(64)     DEFAULT NULL            COMMENT '审核人姓名',
    `review_status`             TINYINT         DEFAULT 0               COMMENT '审核状态(0:待审核 1:已通过 2:需复核)',
    `review_status_name`        VARCHAR(32)     DEFAULT NULL            COMMENT '审核状态名称',
    `review_time`               DATETIME        DEFAULT NULL            COMMENT '审核时间',
    `review_remark`             VARCHAR(500)    DEFAULT NULL            COMMENT '审核备注',
    `create_time`               DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`                 BIGINT          DEFAULT NULL            COMMENT '创建人',
    `update_by`                 BIGINT          DEFAULT NULL            COMMENT '更新人',
    `deleted`                   TINYINT         DEFAULT 0               COMMENT '删除标记(0:未删除 1:已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_callback_result_id` (`callback_result_id`),
    UNIQUE KEY `uk_callback_task_id` (`callback_task_id`),
    KEY `idx_call_id` (`call_id`),
    KEY `idx_satisfaction_level` (`satisfaction_level`),
    KEY `idx_sentiment_label` (`sentiment_label`),
    KEY `idx_overall_score` (`overall_score`),
    KEY `idx_review_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回访结果表';

-- -----------------------------------------------------
-- 8.3 回访话术模板表 callback_template
-- -----------------------------------------------------
DROP TABLE IF EXISTS `callback_template`;
CREATE TABLE `callback_template` (
    `id`                        BIGINT          NOT NULL                COMMENT '主键ID',
    `template_id`               VARCHAR(64)     NOT NULL                COMMENT '模板ID(业务主键)',
    `template_no`               VARCHAR(64)     DEFAULT NULL            COMMENT '模板编号',
    `template_name`             VARCHAR(128)    NOT NULL                COMMENT '模板名称',
    `template_type`             TINYINT         DEFAULT NULL            COMMENT '模板类型(1:标准警情回访 2:投诉处理回访 3:重大案件回访 4:自定义)',
    `template_type_name`        VARCHAR(64)     DEFAULT NULL            COMMENT '模板类型名称',
    `tts_code`                  VARCHAR(32)     DEFAULT 'xiaoyun'       COMMENT '阿里云TTS音色编码',
    `tts_name`                  VARCHAR(64)     DEFAULT NULL            COMMENT 'TTS音色名称',
    `welcome_text`              VARCHAR(1000)   DEFAULT NULL            COMMENT '开场白文本',
    `question1_timeliness`      VARCHAR(1000)   DEFAULT NULL            COMMENT '问题1:处置及时性',
    `question2_attitude`        VARCHAR(1000)   DEFAULT NULL            COMMENT '问题2:警员态度',
    `question3_solving`         VARCHAR(1000)   DEFAULT NULL            COMMENT '问题3:问题解决度',
    `extra_questions`           MEDIUMTEXT      DEFAULT NULL            COMMENT '附加问题(JSON数组)',
    `end_thank_text`            VARCHAR(500)    DEFAULT NULL            COMMENT '结束语',
    `transfer_human_text`       VARCHAR(500)    DEFAULT NULL            COMMENT '转人工话术',
    `dissatisfaction_followup`  VARCHAR(1000)   DEFAULT NULL            COMMENT '不满意追问话术',
    `keywords_map`              MEDIUMTEXT      DEFAULT NULL            COMMENT '关键词映射(JSON)',
    `priority`                  INT             DEFAULT 100             COMMENT '优先级(数字越小优先级越高)',
    `status`                    TINYINT         DEFAULT 1               COMMENT '状态(0:禁用 1:启用)',
    `status_name`               VARCHAR(32)     DEFAULT NULL            COMMENT '状态名称',
    `default_flag`              TINYINT         DEFAULT 0               COMMENT '是否默认模板(0:否 1:是)',
    `create_time`               DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by`                 BIGINT          DEFAULT NULL            COMMENT '创建人',
    `update_by`                 BIGINT          DEFAULT NULL            COMMENT '更新人',
    `deleted`                   TINYINT         DEFAULT 0               COMMENT '删除标记(0:未删除 1:已删除)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_id` (`template_id`),
    KEY `idx_status` (`status`),
    KEY `idx_default_flag` (`default_flag`),
    KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回访话术模板表';

-- -----------------------------------------------------
-- 8.4 预置3条模板数据
-- -----------------------------------------------------

-- TPL001: 标准警情回访
INSERT INTO `callback_template` (
    `id`, `template_id`, `template_no`, `template_name`, `template_type`, `template_type_name`,
    `tts_code`, `tts_name`, `welcome_text`, `question1_timeliness`, `question2_attitude`,
    `question3_solving`, `extra_questions`, `end_thank_text`, `transfer_human_text`,
    `dissatisfaction_followup`, `keywords_map`, `priority`, `status`, `status_name`, `default_flag`
) VALUES (
    1, 'TPL001', 'TPL001', '标准警情回访', 1, '标准警情回访',
    'xiaoyun', '小云',
    '您好，我是公安局智能回访助手，关于您之前报警的警情，想做一个简短的回访，请问方便吗？大约需要一分钟时间。',
    '第一个问题，您对我们的出警速度是否满意？请从1到5分进行评价，1分表示非常不满意，5分表示非常满意。',
    '第二个问题，您对处警民警的服务态度是否满意？请从1到5分进行评价。',
    '第三个问题，您对问题的处理结果是否满意？请从1到5分进行评价。',
    NULL,
    '感谢您的配合和反馈，我们会继续努力改进工作。祝您生活愉快，再见！',
    '好的，已为您转接人工坐席，请稍候。',
    '请问具体是哪方面让您不满意呢？可以简单描述一下吗？',
    '{"5分":["满意","很好","非常好","不错","可以","满分"],"4分":["还行","还可以","一般偏上","不错的"],"3分":["一般","还行吧","凑活","就那样"],"2分":["不太满意","不好","差","不行"],"1分":["不满意","很差","非常差","极差","恶劣"]}',
    1, 1, '启用', 1
);

-- TPL002: 投诉处理回访
INSERT INTO `callback_template` (
    `id`, `template_id`, `template_no`, `template_name`, `template_type`, `template_type_name`,
    `tts_code`, `tts_name`, `welcome_text`, `question1_timeliness`, `question2_attitude`,
    `question3_solving`, `extra_questions`, `end_thank_text`, `transfer_human_text`,
    `dissatisfaction_followup`, `keywords_map`, `priority`, `status`, `status_name`, `default_flag`
) VALUES (
    2, 'TPL002', 'TPL002', '投诉处理回访', 2, '投诉处理回访',
    'xiaoyun', '小云',
    '您好，我是公安局投诉处理回访专员。关于您之前提交的投诉事项，想就处理情况进行回访，请问现在方便吗？',
    '首先，您对我们受理投诉后的响应速度和处理时效是否满意？请从1到5分评价。',
    '其次，您对负责处理您投诉事项的工作人员态度是否满意？请从1到5分评价。',
    '最后，您认为本次投诉的问题是否得到了妥善解决？请从1到5分评价。',
    NULL,
    '感谢您的宝贵意见，我们将持续改进工作作风和服务质量。如果您还有其他问题，欢迎随时与我们联系。再见！',
    '感谢您的坦诚反馈，我马上为您转接专人处理，请稍等。',
    '非常抱歉给您带来了不好的体验。能否请您具体说明一下，是哪些方面还需要改进？您的建议对我们非常重要。',
    '{"5分":["满意","很好","非常好","处理得当","公正"],"4分":["还行","还可以","基本满意"],"3分":["一般","还行吧","勉强"],"2分":["不太满意","处理不好","有偏见"],"1分":["不满意","很差","非常差","不公正","包庇"]}',
    2, 1, '启用', 0
);

-- TPL003: 重大案件回访
INSERT INTO `callback_template` (
    `id`, `template_id`, `template_no`, `template_name`, `template_type`, `template_type_name`,
    `tts_code`, `tts_name`, `welcome_text`, `question1_timeliness`, `question2_attitude`,
    `question3_solving`, `extra_questions`, `end_thank_text`, `transfer_human_text`,
    `dissatisfaction_followup`, `keywords_map`, `priority`, `status`, `status_name`, `default_flag`
) VALUES (
    3, 'TPL003', 'TPL003', '重大案件回访', 3, '重大案件回访',
    'xiaoyun', '小云',
    '您好，我是公安局案件回访中心。针对您所涉及的重大案件，我们进行专门的回访调查。您的反馈对我们改进办案工作、提升执法质量非常重要，请问现在方便接受回访吗？',
    '第一，您对公安机关立案后的响应速度和出警处置的及时性如何评价？请用1到5分打分。',
    '第二，您对办案民警在案件办理过程中的执法态度和专业水平是否满意？请用1到5分打分。',
    '第三，您对目前案件的办理进展和处理结果的满意度如何？请用1到5分打分。',
    NULL,
    '感谢您对公安工作的支持与配合。我们将继续推进案件侦办工作，依法保障您的合法权益。如有任何新的情况或线索，请及时与我们联系。祝您平安！',
    '理解您的心情。我立即为您转接案件负责人，请您稍等，他/她会与您详细沟通。',
    '非常理解您的感受。案件办理过程中哪些环节让您存在顾虑或不满，能否详细说明一下？我们会认真对待每一条意见。',
    '{"5分":["满意","很好","非常好","专业","高效","公正"],"4分":["还行","还可以","基本满意","不错的"],"3分":["一般","还行吧","还需改进"],"2分":["不太满意","进度慢","态度不好"],"1分":["不满意","很差","非常差","不作为","渎职"]}',
    3, 1, '启用', 0
);
