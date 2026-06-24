-- =============================================================================
-- GraphNexus 教材文档元数据表 DDL
-- =============================================================================

CREATE TABLE IF NOT EXISTS textbook (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '技术主键',
    document_no     CHAR(32)        NOT NULL COMMENT 'MD5 内容指纹',
    name            VARCHAR(255)    NOT NULL COMMENT '文件名称（原始文件名清洗后）',
    subject         VARCHAR(20)     NOT NULL COMMENT '所属学科 MATH/PHYSICS/CHEMISTRY',
    file_type       VARCHAR(20)     NOT NULL DEFAULT 'pdf' COMMENT '文件类型扩展名 pdf/txt',
    file_size       BIGINT          DEFAULT NULL COMMENT '文件大小(字节)',
    file_path       VARCHAR(500)    NOT NULL COMMENT '完整文件访问路径',
    text_content    MEDIUMTEXT      DEFAULT NULL COMMENT '提取的文本内容',
    page_count      INT             DEFAULT NULL COMMENT '总页数',
    status          VARCHAR(20)     NOT NULL DEFAULT 'UPLOADED' COMMENT '文件状态 UPLOADED/PARSING/PARSED/EXTRACTING/EXTRACTED/FUSING/COMPLETED/FAILED',
    fail_reason     VARCHAR(512)    DEFAULT NULL COMMENT '失败原因',
    uploaded_by     BIGINT UNSIGNED DEFAULT NULL COMMENT '上传人ID FK→user_account.id',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_subject (document_no, subject),
    KEY idx_file_type (file_type),
    KEY idx_status (status),
    KEY idx_uploaded_by (uploaded_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教材文档元数据表';

-- ALTER TABLE 增量迁移（已存在的 file 表 → textbook）：
-- ALTER TABLE file RENAME TO textbook;
-- ALTER TABLE textbook CHANGE COLUMN minio_path file_path VARCHAR(500) NOT NULL COMMENT '完整文件访问路径';
-- ALTER TABLE textbook DROP COLUMN metadata_json;
-- ALTER TABLE textbook DROP COLUMN update_time;
-- ALTER TABLE textbook ADD INDEX IF NOT EXISTS idx_file_path (file_path(200));
-- DROP TABLE IF EXISTS textbook;
-- =============================================================================

-- up: 创建 exam_record 表
-- 设计依据：DESIGN D3 (JSON 列) + D10 (is_deleted 中间状态)
CREATE TABLE IF NOT EXISTS exam_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '技术主键',
    student_no  VARCHAR(64)  NOT NULL                 COMMENT '学号',
    name        VARCHAR(128)                          COMMENT '学生姓名',
    class_name  VARCHAR(128)                          COMMENT '班级',
    exam_no     VARCHAR(64)  NOT NULL                 COMMENT '考试编号（文件提供）',
    exam_name   VARCHAR(255)                          COMMENT '考试名称',
    exam_date   DATE                                  COMMENT '考试日期',
    subject     VARCHAR(20)                           COMMENT '学科',
    total_score INT                                   COMMENT '总分',
    class_rank  INT                                   COMMENT '班级排名',
    score_details JSON                                COMMENT '成绩明细 [{questionLabel, kpNames[], rawScore, maxScore}]',
    csv_file_path VARCHAR(500)                        COMMENT '文件 MinIO 路径',
    csv_md5     VARCHAR(32)                           COMMENT '文件 MD5 内容指纹',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_exam (student_no,exam_no),
    INDEX idx_exam_no (exam_no),
    INDEX idx_student_no (student_no),
    INDEX idx_csv_md5 (csv_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试成绩记录表';

-- down: 删除 exam_record 表
-- DROP TABLE IF EXISTS exam_record;

-- up: 创建 fusion_log 表
-- 设计依据: DESIGN D6 (JSON 列存快照) + D7 (回滚日志) + D9 (并发控制)
CREATE TABLE IF NOT EXISTS fusion_log (
    id                    BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '技术主键',
    trigger_type          VARCHAR(32)  NOT NULL                 COMMENT '触发方式 MANUAL_FULL / AUTO_INCREMENTAL',
    status                VARCHAR(32)  NOT NULL                 COMMENT '融合状态 RUNNING / COMPLETED / ROLLED_BACK',
    merged_kp_group_count INT          NOT NULL DEFAULT 0       COMMENT '融合 KP 组数',
    masters_edge_count    INT          NOT NULL DEFAULT 0       COMMENT '更新的 MASTERS 边数',
    fusion_detail_json    MEDIUMTEXT                            COMMENT '融合明细 JSON [{groupId, sourceKpIds[], targetKpId, redirectedEdges[]}]',
    masters_snapshot_json MEDIUMTEXT                            COMMENT 'MASTERS 变更快照 [{studentNo, kpName, oldWeight, newWeight}]',
    rolled_back           TINYINT      NOT NULL DEFAULT 0       COMMENT '是否已回滚 0=否 1=是',
    executed_at           DATETIME     NOT NULL                 COMMENT '融合执行时间',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_status (status),
    INDEX idx_executed_at (executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宽图谱融合操作日志表';

-- down: 删除 fusion_log 表
-- DROP TABLE IF EXISTS fusion_log;
-- ======================== intelligent-qa query_task 表 ========================
CREATE TABLE IF NOT EXISTS query_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL UNIQUE COMMENT '任务UUID',
    question TEXT NOT NULL COMMENT '用户原始问题',
    student_name VARCHAR(128) COMMENT '目标学生姓名',
    student_no VARCHAR(64) COMMENT '目标学生学号',
    subject VARCHAR(32) COMMENT '学科',
    intent VARCHAR(32) COMMENT '识别意图类型',
    status VARCHAR(20) NOT NULL COMMENT '任务状态 PENDING/PROCESSING/COMPLETED/FAILED',
    answer MEDIUMTEXT COMMENT 'LLM生成的分析答案(Markdown)',
    subgraph_json MEDIUMTEXT COMMENT '剪枝子图JSON',
    token_usage_json JSON COMMENT 'Token用量JSON',
    error_message TEXT COMMENT '失败时错误信息',
    retry_count INT DEFAULT 0 COMMENT 'LLM调用重试次数',
    elapsed_ms BIGINT COMMENT '任务总耗时(毫秒)',
    created_by VARCHAR(64) COMMENT '创建者用户名（执行诊断的用户）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能问答任务记录表(日志类表，不设逻辑删除)';

-- =============================================================================
-- user-auth-rbac 用户认证与角色权限管理
-- =============================================================================

-- up: 创建 user_account 表
CREATE TABLE IF NOT EXISTS user_account (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '技术主键',
    username    VARCHAR(64)  NOT NULL                 COMMENT '用户名（登录凭证）',
    password    VARCHAR(255) NOT NULL                 COMMENT '密码（BCrypt 加密）',
    real_name   VARCHAR(128)                          COMMENT '真实姓名',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态 ENABLED/DISABLED',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号表';

-- down: 删除 user_account 表
-- DROP TABLE IF EXISTS user_account;

-- up: 创建 role 表
CREATE TABLE IF NOT EXISTS role (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '技术主键',
    code        VARCHAR(32)  NOT NULL                 COMMENT '角色代码 ADMIN/TEACHER/STUDENT/OPS_STAFF/OPS_MANAGER',
    name        VARCHAR(64)  NOT NULL                 COMMENT '角色中文名',
    description VARCHAR(255)                          COMMENT '角色描述',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色定义表';

-- down: 删除 role 表
-- DROP TABLE IF EXISTS role;

-- up: 创建 user_role 关联表
CREATE TABLE IF NOT EXISTS user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT  COMMENT '技术主键',
    user_id BIGINT NOT NULL                 COMMENT 'FK → user_account.id',
    role_id BIGINT NOT NULL                 COMMENT 'FK → role.id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- down: 删除 user_role 表
-- DROP TABLE IF EXISTS user_role;

-- =============================================================================
-- 角色预置数据（user-auth-rbac）
-- 使用 INSERT IGNORE 保证幂等，多次启动不重复插入
-- =============================================================================
INSERT IGNORE INTO role (id, code, name, description) VALUES
    (1, 'ADMIN',       '超级管理员', '系统最高权限，管理数据资产与系统权限，继承教师全部用例'),
    (2, 'TEACHER',     '教师',       '消费分析结果与生产教学数据'),
    (3, 'STUDENT',     '学生',       '感知自身学习状态，仅查看个人数据'),
    (4, 'OPS_STAFF',   '运维人员',   '保障系统稳定运行，管理 LLM 配置与日志'),
    (5, 'OPS_MANAGER', '运营人员',   '度量系统使用效果，查看运营数据');

-- up: 创建默认管理员 admin / admin123
INSERT IGNORE INTO user_account (id, username, password, real_name, status) VALUES
    (1, 'admin', '$2b$10$Hl5BMmyPxNBMvcfW6fD5du/5jo.zC8EOgGzQN1ogbZwTtAiFtgCMO', '管理员', 'ENABLED');

-- up: 为默认管理员分配所有角色
INSERT IGNORE INTO user_role (user_id, role_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5);

-- down: 删除默认管理员及其角色关联
-- DELETE FROM user_role WHERE user_id = 1;
-- DELETE FROM user_account WHERE id = 1;

-- =============================================================================
-- GraphNexus 系统配置表 DDL（config-management）
-- =============================================================================

CREATE TABLE IF NOT EXISTS system_config (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '技术主键',
    config_key      VARCHAR(128)    NOT NULL COMMENT '配置键（如 fusion.kp-matching.threshold）',
    config_value    TEXT            DEFAULT NULL COMMENT '自定义值，NULL表示使用yml默认值',
    config_type     VARCHAR(16)     NOT NULL DEFAULT 'STRING' COMMENT '值类型：NUMBER|STRING|BOOLEAN|TEXT',
    category        VARCHAR(32)     NOT NULL COMMENT '分类：BUSINESS_PARAM|LLM_PROMPT|LLM_MODEL',
    config_name     VARCHAR(64)     NOT NULL COMMENT '中文显示名',
    description     VARCHAR(256)    DEFAULT '' COMMENT '配置说明',
    default_value   VARCHAR(512)    DEFAULT NULL COMMENT 'yml默认值，前端展示用',
    required        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否必填',
    validation_rule JSON            DEFAULT NULL COMMENT '校验规则JSON，如{"min":0,"max":1}',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '前端展示排序',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_category_sort (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- Seed data: 所有可管理配置项（config_value=NULL 表示使用 yml 默认值）
-- BUSINESS_PARAM 类（约 18 项）
INSERT IGNORE INTO system_config (config_key, config_type, category, config_name, description, default_value, required, validation_rule, sort_order) VALUES
('fusion.kp-matching.strategy', 'STRING', 'BUSINESS_PARAM', 'KP匹配策略', '融合时使用的知识点匹配策略名称：fuzzy/exact', 'fuzzy', 1, NULL, 10),
('fusion.kp-matching.threshold', 'NUMBER', 'BUSINESS_PARAM', '融合匹配阈值', 'KP匹配相似度阈值(0~1)，越大匹配越严格', '0.85', 1, '{"min":0,"max":1}', 11),
('fusion.weight.strategy', 'STRING', 'BUSINESS_PARAM', '权重计算策略', 'MASTERS权重计算策略名称：time-decay/simple-average', 'time-decay', 1, NULL, 12),
('fusion.strategy.fuzzy.alpha', 'NUMBER', 'BUSINESS_PARAM', '模糊匹配α权重', '字符Jaccard权重(0~1)', '0.3', 0, '{"min":0,"max":1}', 20),
('fusion.strategy.fuzzy.beta', 'NUMBER', 'BUSINESS_PARAM', '模糊匹配β权重', 'Bigram Jaccard权重(0~1)', '0.5', 0, '{"min":0,"max":1}', 21),
('fusion.strategy.fuzzy.gamma', 'NUMBER', 'BUSINESS_PARAM', '模糊匹配γ权重', '归一化编辑距离权重(0~1)', '0.2', 0, '{"min":0,"max":1}', 22),
('fusion.strategy.time-decay.factor', 'NUMBER', 'BUSINESS_PARAM', '时间衰减因子', '月衰减因子(0~1)', '0.9', 0, '{"min":0,"max":1}', 30),
('graph.metrics.cache.ttl-minutes', 'NUMBER', 'BUSINESS_PARAM', '指标缓存TTL', 'Caffeine缓存TTL（分钟）', '5', 0, '{"min":1,"max":1440}', 40),
('graph.metrics.cache.max-size', 'NUMBER', 'BUSINESS_PARAM', '指标缓存上限', '最大缓存条目数', '50', 0, '{"min":1,"max":1000}', 41),
('graph.metrics.page-rank.max-iterations', 'NUMBER', 'BUSINESS_PARAM', 'PageRank最大迭代', 'PageRank算法最大迭代次数', '20', 0, '{"min":1,"max":100}', 42),
('graph.metrics.page-rank.damping-factor', 'NUMBER', 'BUSINESS_PARAM', 'PageRank阻尼因子', 'PageRank算法阻尼因子(0~1)', '0.85', 0, '{"min":0,"max":1}', 43),
('mineru.enabled', 'BOOLEAN', 'BUSINESS_PARAM', 'MinerU开关', '是否启用MinerU PDF解析', 'true', 1, NULL, 50),
('mineru.api.poll-timeout', 'STRING', 'BUSINESS_PARAM', 'MinerU轮询超时', 'MinerU解析轮询超时时间', '300s', 0, NULL, 51),
('mineru.api.poll-interval', 'STRING', 'BUSINESS_PARAM', 'MinerU轮询间隔', 'MinerU解析轮询间隔时间', '3s', 0, NULL, 52),
('query.token-budget.max-input-tokens', 'NUMBER', 'BUSINESS_PARAM', 'Token预算上限', 'LLM输入token上限', '8000', 1, '{"min":100,"max":128000}', 60),
('query.token-budget.chars-per-token', 'NUMBER', 'BUSINESS_PARAM', '字符/Token比', '中文≈2,英文≈4，取3', '3', 0, '{"min":1,"max":10}', 61),
('query.pruning.weak-threshold', 'NUMBER', 'BUSINESS_PARAM', '薄弱点阈值', '掌握度低于此值视为薄弱点(0~1)', '0.6', 0, '{"min":0,"max":1}', 62),
('query.pruning.max-prerequisite-hops', 'NUMBER', 'BUSINESS_PARAM', '前置依赖最大跳数', 'PREREQUISITE_OF遍历最大跳数', '2', 0, '{"min":1,"max":5}', 63),
('query.retry.max-retries', 'NUMBER', 'BUSINESS_PARAM', 'LLM重试次数', 'LLM调用失败最大重试次数', '2', 0, '{"min":0,"max":10}', 64),
('query.retry.retry-delay-ms', 'NUMBER', 'BUSINESS_PARAM', '重试间隔', 'LLM重试间隔（毫秒）', '1000', 0, '{"min":0,"max":30000}', 65),
('query.output-format', 'STRING', 'BUSINESS_PARAM', '问答输出格式', 'LLM输出格式：html-svg|markdown', 'html-svg', 1, NULL, 66);

-- LLM_PROMPT 类（13 个提示词模板）
INSERT IGNORE INTO system_config (config_key, config_type, category, config_name, description, default_value, required, sort_order) VALUES
('prompt.extraction-system', 'TEXT', 'LLM_PROMPT', '抽取System Prompt', 'LLM知识抽取的角色设定和指令', 'classpath:/prompts/extraction-system.md', 0, 10),
('prompt.extraction-user', 'TEXT', 'LLM_PROMPT', '抽取User Prompt', 'LLM知识抽取的用户消息模板', 'classpath:/prompts/extraction-user.md', 0, 11),
('prompt.extraction-fewshot-default', 'TEXT', 'LLM_PROMPT', '抽取Few-shot(默认)', '抽取few-shot示例-默认学科', 'classpath:/prompts/extraction-fewshot-default.md', 0, 12),
('prompt.extraction-fewshot-math', 'TEXT', 'LLM_PROMPT', '抽取Few-shot(数学)', '抽取few-shot示例-数学学科', 'classpath:/prompts/extraction-fewshot-math.md', 0, 13),
('prompt.student-diagnosis-system', 'TEXT', 'LLM_PROMPT', '诊断System Prompt', '学生诊断问答的角色设定(Markdown)', 'classpath:/prompts/student-diagnosis-system.md', 0, 20),
('prompt.student-diagnosis-user', 'TEXT', 'LLM_PROMPT', '诊断User Prompt', '学生诊断问答的用户消息模板(Markdown)', 'classpath:/prompts/student-diagnosis-user.md', 0, 21),
('prompt.student-diagnosis-system-html', 'TEXT', 'LLM_PROMPT', '诊断System Prompt(HTML)', '学生诊断问答的角色设定(HTML+SVG)', 'classpath:/prompts/student-diagnosis-system-html.md', 0, 22),
('prompt.student-diagnosis-user-html', 'TEXT', 'LLM_PROMPT', '诊断User Prompt(HTML)', '学生诊断问答的用户消息模板(HTML+SVG)', 'classpath:/prompts/student-diagnosis-user-html.md', 0, 23),
('prompt.class-weakness-overview-system', 'TEXT', 'LLM_PROMPT', '班级概览System Prompt', '班级薄弱概览的角色设定(Markdown)', 'classpath:/prompts/class-weakness-overview-system.md', 0, 24),
('prompt.class-weakness-overview-user', 'TEXT', 'LLM_PROMPT', '班级概览User Prompt', '班级薄弱概览的用户消息模板(Markdown)', 'classpath:/prompts/class-weakness-overview-user.md', 0, 25),
('prompt.class-weakness-overview-system-html', 'TEXT', 'LLM_PROMPT', '班级概览System Prompt(HTML)', '班级薄弱概览的角色设定(HTML+SVG)', 'classpath:/prompts/class-weakness-overview-system-html.md', 0, 26),
('prompt.class-weakness-overview-user-html', 'TEXT', 'LLM_PROMPT', '班级概览User Prompt(HTML)', '班级薄弱概览的用户消息模板(HTML+SVG)', 'classpath:/prompts/class-weakness-overview-user-html.md', 0, 27),
('prompt.intent-classification-system', 'TEXT', 'LLM_PROMPT', '意图分类System Prompt', 'LLM意图分类的角色设定和指令', 'classpath:/prompts/intent-classification-system.md', 0, 30);

-- LLM_MODEL 类（5 个模型参数）
INSERT IGNORE INTO system_config (config_key, config_type, category, config_name, description, default_value, required, validation_rule, sort_order) VALUES
('llm.base-url', 'STRING', 'LLM_MODEL', 'LLM API地址', 'LLM服务的基础URL', 'https://dashscope.aliyuncs.com/compatible-mode', 1, NULL, 5),
('llm.api-key', 'STRING', 'LLM_MODEL', 'LLM API Key', 'LLM服务的认证密钥', '${LLM_API_KEY:}', 1, NULL, 6),
('llm.model', 'STRING', 'LLM_MODEL', 'LLM模型名称', '调用LLM API时使用的模型标识', 'qwen3.6-plus', 1, NULL, 10),
('llm.temperature', 'NUMBER', 'LLM_MODEL', 'LLM温度参数', '生成温度(0~2)，越高越随机', '0.3', 0, '{"min":0,"max":2}', 11),
('llm.max-tokens', 'NUMBER', 'LLM_MODEL', 'LLM最大Token数', '单次生成最大token数，-1表示不限制', '-1', 0, '{"min":-1}', 12);

-- =============================================================================
-- 运营管理模块 DDL（ops-analytics）
-- =============================================================================

-- 操作审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '技术主键',
    user_id         BIGINT          NOT NULL COMMENT '操作人ID（引用user_account.id）',
    operation_type  VARCHAR(32)     NOT NULL COMMENT '操作类型：LOGIN/DOCUMENT_UPLOAD/DOCUMENT_PROCESS/QA_ASK',
    resource_id     VARCHAR(128)    DEFAULT NULL COMMENT '关联资源ID（documentId/taskId）',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_time (create_time),
    INDEX idx_audit_type (operation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

-- 统计快照表
CREATE TABLE IF NOT EXISTS stats_snapshot (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '技术主键',
    snapshot_date   DATE            NOT NULL COMMENT '快照日期',
    snapshot_data   JSON            NOT NULL COMMENT '快照数据（usage+documents+graph三层嵌套JSON）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'COMPLETED' COMMENT '快照状态：COMPLETED/PARTIAL/FAILED',
    fail_reason     VARCHAR(512)    DEFAULT NULL COMMENT '失败原因',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_snapshot_date (snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计快照表';