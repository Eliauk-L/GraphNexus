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
    is_deleted      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_subject (document_no, subject),
    KEY idx_file_type (file_type),
    KEY idx_status (status),
    KEY idx_uploaded_by (uploaded_by)
    KEY idx_is_deleted (is_deleted)
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
    csv_file_path VARCHAR(500)                        COMMENT 'CSV 文件 MinIO 路径',
    csv_md5     VARCHAR(32)                           COMMENT 'CSV 文件 MD5 内容指纹',
    is_deleted  TINYINT     NOT NULL DEFAULT 0        COMMENT '逻辑删除 0=正常 1=已删除',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_exam (student_no,exam_no),
    INDEX idx_exam_no (exam_no),
    INDEX idx_csv_md5 (csv_md5),
    INDEX idx_student_no (student_no)
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
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能问答任务记录表(日志类表，不设逻辑删除)';
