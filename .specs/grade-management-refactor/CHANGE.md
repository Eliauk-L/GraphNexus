# CHANGE: 成绩管理重构 — 去考试编号重复 + Excel 解析 + 去格式硬编码 + 移除 MinIO

- **Change ID**: `grade-management-refactor`
- **创建日期**: 2026-06-19
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

`csv-grade-import` 落地了成绩上传 → MySQL + MinIO + Neo4j 全链路，但存在五个设计债：

1. **去重维度错误**：当前以 `csv_md5`（文件内容 MD5）为判重键，重复上传自动覆盖。但成绩文件以考试为主体——一个文件 = 一个考试编号（`exam_no`）。以 MD5 判重意味着：同一考试编号上传两份内容不同的文件时不会触发冲突，导致数据不一致。应以 `exam_no` 为去重维度，重复上传时拒绝并提示用户先手动删除。

2. **文件格式硬编码为 CSV**：表字段 `csv_file_path`、`csv_md5`、枚举 `CSV_GRADE` 将"成绩文件 = CSV"写死在 SQL 层和 Java 层。这些 CSV 特定字段本身也无实际用途（不做 MinIO 存储、不用 MD5 判重），应当直接移除。

3. **仅支持 CSV 格式**：教务实际使用 Excel（`.xlsx`/`.xls`）的频率不亚于 CSV，系统需要支持 Excel 成绩文件上传解析。

4. **成绩文件不必要地存入 MinIO**：成绩文件上传解析后即不可变，解析结果已完整持久化到 MySQL（`exam_record` 记录 + `score_details` JSON）和 Neo4j（Student/Exam/KP 节点 + 边）。原始文件留存 MinIO 无后续使用场景，反而增加了上传链路的复杂度、存储成本、以及删除时需要级联清理 MinIO 的负担。

5. **成绩查询能力不足**：当前仅支持按 `exam_no` 查询整场考试记录，缺少按班级、学生姓名、学号等维度的条件查询。教务日常使用中需要灵活检索成绩数据（如"查看一班所有学生的历次考试成绩"、"搜索张三的成绩记录"），当前只能先查到 exam_no 再按考试查询，效率低且不直观。

## What（做什么）

从 SQL 层面开始，系统性消除成绩管理模块中的格式硬编码，重构去重策略，并移除不必要的 MinIO 依赖：

1. **SQL 表结构变更**：
   - `csv_file_path` 列 → **删除**（成绩文件不再存入 MinIO，无需保留路径）
   - `csv_md5` 列 → **删除**（去重已改为 exam_no 维度，无 MinIO 存储则 MD5 无使用场景）
   - 对应索引同步删除（`idx_csv_md5`）
2. **去重策略重构**：从"MD5 判重 → 自动覆盖"改为"`exam_no` 判重 → 拒绝上传 + 提示先手动删除"
3. **新增 Excel 解析器**：实现 `ExcelGradeParser`（`FileParser` 接口），支持 `.xlsx`/`.xls`，解析与 CSV 相同的双行表头成绩格式
4. **枚举按格式拆分**：`GradeFileType.CSV_GRADE` → 拆为 `GradeFileType.CSV` 和 `GradeFileType.EXCEL`，分别表示 CSV 和 Excel（.xlsx/.xls）格式。业务类型统一由 `FileParser.BIZ_GRADE` 表达，格式细节由 `FileParserRegistry` 按扩展名路由到对应 Parser，Parser 返回自身对应的 `GradeFileType` 枚举值
5. **移除 MinIO 交互**：上传链路不再上传文件到 MinIO；删除链路不再清理 MinIO 文件
6. **成绩条件查询接口**：将原有的 `GET /api/v1/file/grades/exam/{examNo}` 单一考试查询端点合并到 `GET /api/v1/file/grades` 统一条件查询中，支持按考试编号（`examNo`）、考试名称（`examName`，模糊匹配）、班级（`className`）、学生姓名（`name`，模糊匹配）、学号（`studentNo`）、学科（`subject`）等多个维度的可选组合条件查询，支持分页。Repository 层通过 Spring Data JPA 方法名派生或 `@Query` 实现，Service 层封装查询逻辑
7. **全链路引用更新**：DO / Repository / Service / Controller / VO / 测试 中所有 `csv_*` 命名字段/方法/变量同步重命名或删除

## 影响面

- [x] 影响 `REQUIREMENT.md` — 去重行为变更（AC-3 "MD5 判重覆盖" → "exam_no 判重拒绝"），移除 MinIO 存储（AC-1/AC-7 中 MinIO 相关步骤删除），新增 Excel 解析 AC，新增条件查询 AC
- [x] 影响 `DESIGN.md` / 引入新 ADR — SQL 迁移方案（DROP 两列 + 索引清理）+ Excel 解析器设计 + 去重策略设计 + 移除 MinIO 后的链路简化 + 条件查询接口设计
- [x] 影响现有 AC — AC-1 移除 MinIO 验证步骤；AC-3 行为完全变更；AC-7 级联删除移除 MinIO 清理步骤；新增条件查询 AC
- [x] 影响数据模型 / 迁移 — `exam_record` 表删除 `csv_file_path` 列和 `csv_md5` 列及其索引，需 migration SQL；条件查询可能新增复合索引
- [x] 影响外部 API 兼容性 — `GET /api/v1/file/grades/exam/{examNo}` 端点合并到 `GET /api/v1/file/grades?examNo=`；响应 VO 中 `csvFilePath` 和 `csvMd5` 字段删除；属于 breaking change
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- ❌ **双行表头格式变更**：无论是 CSV 还是 Excel，解析后的数据结构不变（`ScoreDetail` 含 questionLabel/kpNames/rawScore/maxScore）
- ❌ **新增 API 端点**：`GET /api/v1/file/grades/exam/{examNo}` 合并到 `GET /api/v1/file/grades`（通过 `?examNo=` 查询参数），不新增独立端点，保持接口简洁。`DELETE /api/v1/file/grades/exam/{examNo}` 保留不动
- ❌ **Neo4j 图模型变更**：Student → Exam → KnowledgePoint 三元模型不变
- ❌ **ODS / 其他表格格式**：仅支持 `.csv` + `.xlsx` + `.xls`，不做 `.ods` 等
- ❌ **Excel 合并单元格处理**：假设 Excel 文件无合并单元格（与 CSV 双行表头结构一致），合并单元格场景 v2
- ❌ **前端成绩管理界面同步适配**：VO 字段变更后前端需跟进，但不属于本次后端 refactor 范围
- ❌ **文件上传后自动替换旧记录**：用户明确选择"拒绝重复"而非"自动覆盖"，删除操作需用户显式执行
- ❌ **成绩文件重新下载**：移除 MinIO 存储后不再提供原始文件下载，仅保留解析后的结构化数据

## 验收线（粗粒度，不是 AC）

1. **Excel 端到端**：上传双行表头 `.xlsx` 成绩文件 → 解析成功，MySQL + Neo4j 全链路数据与 CSV 上传结果一致，无 MinIO 文件写入
2. **exam_no 去重拒绝**：上传文件 A（exam_no=E001）成功 → 上传文件 B（exam_no=E001）→ HTTP 409，提示"考试编号 E001 已存在，请先删除再上传"，数据库无变化
3. **条件查询可用**：`GET /api/v1/file/grades?className=一班&name=张三` → 查到该班级该学生所有考试记录；`?examNo=E001` → 等价于原 `GET .../exam/{examNo}` 功能；`?examName=月考` → 模糊匹配所有月考记录；`?subject=数学` → 所有数学考试记录；支持多条件组合和分页
4. **SQL 零 CSV 硬编码**：`exam_record` 表中无 `csv_*` 前缀列名，`SHOW COLUMNS FROM exam_record` 无 `csv_file_path` 和 `csv_md5` 列

## 风险与未知

- **VO 字段删除的 breaking change**：前端 `GradeUploadResultVO` 和 `GradeRecordVO` 的 `csvFilePath` 和 `csvMd5` 字段删除，需与前端 `frontend-ui` change 协调同步
- **Excel 日期列解析**：CSV 中日期为字符串（`yyyy-MM-dd` 等），Excel 中日期列可能是 Excel 日期数值或字符串，解析器需兼容两种
- **存量数据迁移**：已有 `exam_record` 表需执行 DROP 两列 + 索引清理，需确认是否有生产数据
- **Apache POI 依赖**：Excel 解析需要引入 `org.apache.poi:poi-ooxml`，需评估是否需走独立 change 评估（`pom.xml` 在禁动清单中）
- **MinIO 存量文件清理**：历史已上传的成绩 CSV 文件残留在 MinIO 中，本次不处理（非关键路径，可后续手动清理）

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。
