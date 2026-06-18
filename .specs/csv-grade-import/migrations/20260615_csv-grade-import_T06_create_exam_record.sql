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