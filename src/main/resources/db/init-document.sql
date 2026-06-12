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