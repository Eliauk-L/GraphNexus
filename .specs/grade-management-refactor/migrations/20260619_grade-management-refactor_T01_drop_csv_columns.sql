-- up: 删除成绩表中 CSV 相关列和索引
-- 背景: grade-management-refactor CHANGE - 成绩文件不再存入 MinIO，不再使用 MD5 判重
ALTER TABLE exam_record DROP COLUMN csv_file_path;
ALTER TABLE exam_record DROP COLUMN csv_md5;
DROP INDEX idx_csv_md5 ON exam_record;

-- down: 恢复 CSV 相关列和索引（数据丢失警告：恢复的列为空值）
ALTER TABLE exam_record ADD COLUMN csv_file_path VARCHAR(500) COMMENT 'CSV 文件 MinIO 路径';
ALTER TABLE exam_record ADD COLUMN csv_md5 VARCHAR(32) COMMENT 'CSV 文件 MD5 内容指纹';
CREATE INDEX idx_csv_md5 ON exam_record(csv_md5);