# T02-SUMMARY: DDL init SQL + yml 启动配置调整

- **任务**: T02 — DDL init SQL + yml 启动配置调整
- **状态**: ✅ done
- **提交**: `cb615f5`

---

## 做了什么

1. **DDL SQL**：创建 `src/main/resources/db/init-document.sql`，含完整 `document` 表定义：
   - 17 个字段：id, document_no, name, subject, file_size, minio_path, text_content, page_count, metadata_json, status, fail_reason, uploaded_by, is_deleted, create_time, update_time
   - 3 个索引：PRIMARY KEY(id), UNIQUE KEY uk_document_subject(document_no, subject), KEY idx_minio_path(200), KEY idx_status
   - 含回滚注释（DROP TABLE）

2. **application.yml**：移除 4 项 AutoConfig exclude，激活 MySQL/JPA/Transaction：
   - ❌ DataSourceAutoConfiguration (已移除)
   - ❌ HibernateJpaAutoConfiguration (已移除)
   - ❌ JpaRepositoriesAutoConfiguration (已移除)
   - ❌ TransactionAutoConfiguration (已移除)
   - ✅ 保留 Neo4j/Redis/RabbitMQ/Security 的 exclude

3. **application-dev.yml**：新增 multipart 上传限制配置：
   - `spring.servlet.multipart.max-file-size: 50MB`
   - `spring.servlet.multipart.max-request-size: 55MB`

## 改动文件

- `src/main/resources/db/init-document.sql` (新增, 24 行)
- `src/main/resources/application.yml` (修改, -4 行 exclude)
- `src/main/resources/application-dev.yml` (修改, +5 行 multipart)

## verify 输出

```
=== DDL checks ===
document_no: 2 (字段 + 索引)
text_content: 1
metadata_json: 1
uk_document_subject: 1

=== yml MySQL/JPA exclude checks ===
PASS: DataSource no longer excluded
PASS: HibernateJpa no longer excluded
PASS: JpaRepos no longer excluded
PASS: Transaction no longer excluded

=== multipart config check ===
PASS: 50MB multipart configured

mvn compile: EXIT 0
```

## 数据库迁移

⚠️ DDL 文件已生成（本地无 mysql CLI，需手动或通过 Testcontainers 执行）：
- 文件：`src/main/resources/db/init-document.sql`
- 执行命令：`mysql -u graphnexus -p graphnexus < src/main/resources/db/init-document.sql`
- 验证命令：`DESCRIBE document;`
- 环境清单：
  - [ ] local（手动执行）
  - [ ] dev（手动执行）
  - [ ] staging
  - [ ] prod

## 6 维自查

- R1 认知过载：无函数改动
- R2 变更传播：仅 resources/ 下 3 文件
- R3 知识重复：无
- R4 偶然复杂：无
- R5 依赖混乱：无
- R6 领域扭曲：DDL 表名/字段名对齐用户提供的 schema

## 越界检查

```
✅ TASK write_files：3 项 (init-document.sql, application.yml, application-dev.yml)
✅ 实际 diff 涉及：3 项
✅ 越界：0
```

## LESSONS 扫描

无相关条目。

## TDD 豁免

纯 DDL + 配置任务，无业务逻辑可测试。