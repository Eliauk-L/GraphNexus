-- =============================================================================
-- GraphNexus 文档元数据表 DDL
-- Change: document-process-pdf-minimal
-- 首次执行: mysql -u graphnexus -p graphnexus < init-document.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS document (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '技术主键',
    document_no     CHAR(32)        NOT NULL COMMENT 'MD5(pdf_bytes) 内容指纹',
    name            VARCHAR(255)    NOT NULL COMMENT '文档名称（原始文件名清洗后）',
    subject         VARCHAR(20)     NOT NULL COMMENT '所属学科 MATH/PHYSICS/CHEMISTRY',
    file_size       BIGINT          DEFAULT NULL COMMENT '文件大小(字节)',
    minio_path      VARCHAR(500)    NOT NULL COMMENT 'MinIO 存储路径',
    text_content    MEDIUMTEXT      DEFAULT NULL COMMENT 'PDFBox 提取的文本内容',
    page_count      INT             DEFAULT NULL COMMENT '总页数',
    metadata_json   VARCHAR(2000)   DEFAULT NULL COMMENT 'PDF 元信息 JSON（标题/作者/创建日期等）',
    status          VARCHAR(20)     NOT NULL DEFAULT 'UPLOADED' COMMENT '文档状态 UPLOADED/PROCESSING/COMPLETED/FAILED',
    fail_reason     VARCHAR(512)    DEFAULT NULL COMMENT '失败原因',
    uploaded_by     BIGINT UNSIGNED DEFAULT NULL COMMENT '上传人ID FK→user_account.id',
    is_deleted      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=否 1=是',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_subject (document_no, subject),
    KEY idx_minio_path (minio_path(200)),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元数据表';

-- =============================================================================
-- down（回滚）
-- DROP TABLE IF EXISTS document;
-- =============================================================================

-- up: 创建 exam_record 表
-- 设计依据：DESIGN D3 (JSON 列) + D10 (is_deleted 中间状态)
CREATE TABLE IF NOT EXISTS exam_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '技术主键',
    student_no  VARCHAR(64)  NOT NULL                 COMMENT '学号',
    name        VARCHAR(128)                          COMMENT '学生姓名',
    class_name  VARCHAR(128)                          COMMENT '班级',
    exam_no     VARCHAR(64)  NOT NULL                 COMMENT '考试编号（CSV 提供）',
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