# REQUIREMENT: 成绩管理重构

- **Change ID**: `grade-management-refactor`
- **关联**: `@.specs/grade-management-refactor/CHANGE.md`、`@.specs/CONTEXT.md`、`@.specs/csv-grade-import/REQUIREMENT.md`

---

## 用户故事

- **US-1**：作为教务人员，我想上传 CSV 或 Excel 格式的成绩文件，以便将考试成绩数据纳入系统进行图谱化存储和后续分析。
- **US-2**：作为教务人员，我想按班级、学生姓名、学号、考试编号、考试名称、学科等条件灵活查询成绩记录，以便快速定位和核对数据。
- **US-3**：作为教务人员，我想按考试编号删除整场考试的全部成绩记录，以便在上传错误时清理数据后重新上传。
- **US-4**：作为系统，我想在成绩上传时按考试编号判重并拒绝重复上传，以防止同一考试被意外覆盖导致数据不一致。
- **US-5**：作为系统（架构约束），成绩处理模块应通过 Spring 事件驱动后续图谱操作，而非直接调用图谱业务代码，以保持成绩模块与图谱模块的解耦。

---

## 架构约束（进入 DESIGN 前必须遵守）

> 成绩处理模块（`application/file/grade/`）不直接依赖图谱模块（`application/graph/` 和 `infrastructure/neo4j/`）。

- 成绩上传链路：解析文件 → MySQL 持久化 → **发布 `GradeUploadedEvent`**（携带完整解析结果）→ 结束。图谱操作（Student/Exam/KP 节点 + ATTENDED/TESTED 边）由独立的 `GradeGraphEventListener` 监听同一事件异步完成
- 成绩删除链路：MySQL 标记删除/物理删除 → **发布 `GradeDeletedEvent`**（携带 examNo + 关联信息）→ 结束。图谱清理（Exam 节点 + 边）由独立的 `GradeGraphEventListener` 监听同一事件完成
- 现有 `GradeUploadedEventListener`（融合触发）保持不变，与新的图谱构建 Listener 各自独立消费同一事件
- 成绩模块仅保留对 `ApplicationEventPublisher` 和 `GradeUploadedEvent`/`GradeDeletedEvent` 的依赖，不注入任何 `GraphNodeRepository` 或图谱 Service

---

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · CSV 成绩上传 — MySQL + Neo4j 全链路（无 MinIO）

- **Given** 系统正常运行，MySQL / Neo4j 均可用。上传的 CSV 文件符合双行表头格式：第 1 行 `学号,姓名,班级,考试编号,考试名称,日期,总分,班级排名,题1,...,题N`，第 2 行为每题对应知识点名称（同一题多知识点用 `;` 分隔），数据行成绩格式为 `raw_score/max_score`，列数一致
- **When** 发送 `POST /api/v1/file/grades/upload`（`multipart/form-data`，字段名 `file`=CSV 文件，`subject`=考试学科），系统按 `.csv` 后缀识别为成绩文件
- **Then** HTTP 200，响应体包含解析摘要（`examNo`、`examName`、`examDate`、`subject`、`studentCount`、`questionCount`、`knowledgePoints`）。响应体中**不包含** `csvFilePath` 和 `csvMd5` 字段。同时：
  - **MySQL** `exam_record` 表中新增 N 条记录（N = 学生人数），`score_details` 为 JSON 数组（每元素 `{questionLabel, kpNames, rawScore, maxScore}`），`csv_file_path` 和 `csv_md5` 列已不存在
  - **Neo4j** 中（通过事件监听器异步完成）：N 个 `Student` 节点、1 个 `Exam` 节点、M 个 `KnowledgePoint` 节点、N 条 `ATTENDED` 边（Student→Exam）、M 条 `TESTED` 边（Exam→KnowledgePoint）
  - **MinIO** 中**无任何文件写入**
  - **成绩模块不直接调用图谱代码**：`GradeUploadService` 仅发布 `GradeUploadedEvent`，图谱构建由独立的 `GradeGraphEventListener` 完成
- **验证方式**: 准备 `docs/学生成绩表示例.csv`，`curl -X POST -F "file=@学生成绩表示例.csv" -F "subject=数学" http://localhost:8080/api/v1/file/grades/upload`，然后：
  - MySQL: `SELECT COUNT(*) FROM exam_record WHERE exam_no='<返回的examNo>'` 断言 = 学生数
  - Neo4j: `MATCH (s:Student)-[:ATTENDED]->(e:Exam {examNo:'<examNo>'})-[:TESTED]->(kp) RETURN count(*)` 断言 > 0
  - MinIO: 检查无新文件

### AC-2 · Excel 成绩上传 — 与 CSV 同等结果

- **Given** 系统正常运行，上传的 Excel 文件（`.xlsx` 或 `.xls`）使用与 CSV 相同的双行表头格式（第 1 行军行标题 + 第 2 行知识点），无合并单元格
- **When** 发送 `POST /api/v1/file/grades/upload`（`multipart/form-data`，`file`=Excel 文件，`subject`=考试学科），系统按 `.xlsx`/`.xls` 后缀识别并路由到 `ExcelGradeParser`
- **Then** HTTP 200，解析结果与同等内容的 CSV 文件一致（`studentCount`、`questionCount`、`knowledgePoints` 完全相同）。MySQL 记录和 Neo4j 图数据与 AC-1 无差异。响应中包含 `fileType: "EXCEL"` 表示来源格式
- **验证方式**: 将 AC-1 的 CSV 数据手工录入 Excel（保持行列结构一致），分别上传 CSV 和 Excel，断言两次返回的 `studentCount` 和 `questionCount` 相同；查询 MySQL 断言 `score_details` 内容一致

### AC-3 · exam_no 去重 — 拒绝重复上传

- **Given** 已通过 AC-1 成功上传考试编号为 `E20200041` 的成绩文件
- **When** 再次上传任意成绩文件（内容可不同），其 CSV/Excel 中 `考试编号` 列的值同样是 `E20200041`
- **Then** HTTP 409，响应体包含错误码（如 `A0016`）和提示信息"考试编号 E20200041 已存在，请先删除再上传"。MySQL 和 Neo4j 均无任何变化
- **验证方式**: 第一次上传成功后记录返回的 `examNo`，用另一个内容不同的文件（但 `考试编号` 列值相同）再次上传，断言返回 409；查询 MySQL `SELECT COUNT(*) FROM exam_record WHERE exam_no='E20200041'` 断言与第一次一致

### AC-4 · CSV 格式校验 — 非法文件拒绝

- **Given** 系统正常运行
- **When** 发送 `POST /api/v1/file/grades/upload`，上传 `.csv` 后缀文件但内容不符合成绩模板：
  - 表头只有 1 行（缺少知识点行）
  - 成绩格式异常（如 `A/B` 含非数字字符，且非缺考标记 `-/-`）
  - 数据行列数与表头列数不一致
- **Then** HTTP 400，响应体包含错误码和具体错误描述（指明哪一行/哪一列有问题）。MySQL / Neo4j 均无任何写入
- **验证方式**: 准备 3 个非法 CSV 文件，逐一 curl 上传，断言全部返回 400 + 错误码；查询 MySQL/Neo4j 确认无残留数据

### AC-5 · Excel 格式校验 — 非法文件拒绝

- **Given** 系统正常运行
- **When** 上传 `.xlsx` 后缀文件但内容不符合成绩模板（缺少知识点行 / 成绩格式异常 / 列数不一致）
- **Then** HTTP 400，与 AC-4 同等的错误处理。MySQL / Neo4j 无写入
- **验证方式**: 与 AC-4 对等，使用非法 Excel 文件验证

### AC-6 · 条件查询 — 按班级 + 学生姓名

- **Given** 已上传多个班级的多场考试成绩（如一班 S001 张三、一班 S002 李四、二班 S003 王五）
- **When** 发送 `GET /api/v1/file/grades?className=一班&name=张三&pageNum=1&pageSize=20`
- **Then** HTTP 200，响应体包含分页的成绩记录列表，每条含 `studentNo`、`name`、`className`、`examNo`、`examName`、`subject`、`totalScore`、`classRank`、`scoreDetails`。结果仅包含一班张三的记录，不包含李四或王五
- **验证方式**: curl 查询后 `jq '.data.list | length'` 断言 = 一班张三的考试记录数；`jq '.data.list[].name'` 全部为"张三"；`jq '.data.list[].className'` 全部为"一班"

### AC-7 · 条件查询 — 按考试编号 / 考试名称

- **Given** 已上传多场考试，其中 `examNo=E001`、`examName=第一次月考`
- **When** 发送 `GET /api/v1/file/grades?examNo=E001` 或 `GET /api/v1/file/grades?examName=月考`
- **Then** 前者精确匹配 `examNo=E001` 的全部记录；后者模糊匹配 `examName` 含"月考"的所有记录（如"第一次月考""第二次月考"）。均支持分页
- **验证方式**: `curl "http://localhost:8080/api/v1/file/grades?examNo=E001"` 断言 `jq '.data.list[].examNo'` 全部为 E001；`curl "http://localhost:8080/api/v1/file/grades?examName=月考"` 断言返回行数 ≥ 2 场考试的记录

### AC-8 · 条件查询 — 组合条件

- **Given** 已上传多场考试（不同学科、不同班级）
- **When** 发送 `GET /api/v1/file/grades?subject=数学&className=一班&pageNum=1&pageSize=20`
- **Then** 返回的结果同时满足 `subject=数学` 且 `className=一班`，支持所有可选参数任意组合（`studentNo`、`name`、`className`、`examNo`、`examName`、`subject`）
- **验证方式**: 组合查询后逐条验证结果符合所有条件

### AC-9 · 级联删除 — 整场考试（无 MinIO）

- **Given** 已上传考试 `examNo=E001`，MySQL 中有对应记录，Neo4j 中有对应 Exam 节点 + ATTENDED/TESTED 边
- **When** 发送 `DELETE /api/v1/file/grades/exam/E001`
- **Then** HTTP 200，响应体包含 `examNo`、`deletedRecordCount`。同时：
  - **MySQL** `exam_record` 表中 `exam_no=E001` 的所有记录被物理删除
  - **Neo4j** 中（通过事件监听器异步完成）：该 Exam 节点被删除，所有 ATTENDED 边和 TESTED 边被删除
  - **Neo4j** 中 Student 节点和 KnowledgePoint 节点**不被删除**
  - **MinIO** 中**无任何删除操作**
  - **成绩模块不直接调用图谱代码**：`GradeServiceImpl` 仅发布 `GradeDeletedEvent`，图谱清理由独立的 `GradeGraphEventListener` 完成（因上传时即未写入）
- **验证方式**: 先上传获取 `examNo`，再 `curl -X DELETE http://localhost:8080/api/v1/file/grades/exam/{examNo}`，然后：
  - MySQL: `SELECT COUNT(*) FROM exam_record WHERE exam_no='{examNo}'` 断言 = 0
  - Neo4j: `MATCH (e:Exam {examNo:'{examNo}'}) RETURN count(e)` 断言 = 0
  - Neo4j: `MATCH (s:Student) RETURN count(s)` 断言 > 0
  - Neo4j: `MATCH (kp:KnowledgePoint) RETURN count(kp)` 断言 > 0

### AC-10 · 删除幂等

- **Given** `examNo=E001` 已被 AC-9 删除
- **When** 再次发送 `DELETE /api/v1/file/grades/exam/E001`
- **Then** HTTP 200，无异常，`deletedRecordCount` = 0
- **验证方式**: 连续两次 DELETE，断言两次均返回 200，第二次 `deletedRecordCount=0`

### AC-11 · 缺考标记容错

- **Given** 成绩文件中某学生的某题成绩标记为 `-/-`（表示缺考/未作答）
- **When** 上传该文件
- **Then** HTTP 200，整体解析成功。该题在 MySQL `score_details` JSON 中 `rawScore=null, maxScore=null`；Neo4j 中该知识点 TESTED 边的创建不受影响（缺考不改变考试考查了哪些知识点这一结构事实）
- **验证方式**: 准备含 `-/-` 的 CSV 和 Excel 各一份，上传后查询 MySQL 确认该题 score 为 null

---

## 范围切分

### v1（本次必做）

- 双行表头 CSV 解析器（已有，本次适配列删除和枚举变更）
- 双行表头 Excel 解析器（`.xlsx` + `.xls`，Apache POI）
- `GradeFileType` 枚举拆为 `CSV` 和 `EXCEL`
- `exam_record` 表 DROP `csv_file_path` + `csv_md5` 列及其索引
- 上传链路移除 MinIO 文件写入
- 上传链路移除直接图谱调用：`GradeUploadService` 仅做 解析 → MySQL → 发布 `GradeUploadedEvent`
- 新增 `GradeGraphEventListener`：监听 `GradeUploadedEvent` → 构建 Neo4j 图（Student/Exam/KP + ATTENDED/TESTED）；监听 `GradeDeletedEvent` → 清理 Neo4j 图（Exam + 边）
- 新增 `GradeDeletedEvent`：成绩删除事件，携带 examNo + 关联学生数 + 知识点列表
- 删除链路移除 MinIO 文件清理
- 删除链路移除直接图谱调用：`GradeServiceImpl` 仅做 MySQL 删除 → 发布 `GradeDeletedEvent`
- exam_no 判重拒绝上传（HTTP 409）
- 统一条件查询接口 `GET /api/v1/file/grades`（6 个可选参数：`studentNo`、`name`、`className`、`examNo`、`examName`、`subject`，支持组合 + 分页）
- 合并 `GET /api/v1/file/grades/exam/{examNo}` 到条件查询接口
- 级联删除 `DELETE /api/v1/file/grades/exam/{examNo}`（MySQL + Neo4j，无 MinIO）
- 格式校验（CSV + Excel 双行表头验证、成绩格式验证、列数一致性）
- 缺考标记 `-/-` 容错
- DO / Repository / Service / Controller / VO / 测试全链路 `csv_*` 引用清理
- 错误码新增（如 A0016 exam_no 重复拒绝）

### v2（下一轮考虑，不本次）

- **ODS 格式支持**：LibreOffice 等使用的 `.ods` 格式
- **Excel 合并单元格处理**：Excel 文件中含合并单元格时的智能解析
- **成绩聚合统计 API**：按班级/学科/考试维度的平均分、最高分、分数段分布
- **成绩文件模板下载**：提供标准 CSV/Excel 模板供教务下载填写

### out（永远不做）

- **单条学生成绩删除**：不支持删除某次考试中的单个学生记录。删除粒度 = 整场考试（exam_no），与上传粒度对称
- **成绩文件 MinIO 存储**：成绩文件解析后不保留原始文件，仅保留 MySQL 结构化数据 + Neo4j 图数据
- **自动替换覆盖**：重复 exam_no 不自动覆盖，必须用户显式删除后重新上传
- **手动录入成绩**：仅支持文件批量导入，不做单条成绩手动录入表单
- **成绩原始文件下载**：移除 MinIO 后不再提供原始文件下载

---

## 非功能性需求

- **性能**: 单次成绩上传（≤ 50 学生 × ≤ 30 题）同步处理完成时间 ≤ 3s（原 5s，因移除 MinIO 上传减少等待）
- **安全**: 上传接口需认证（JWT），未登录返回 401；文件大小限制 ≤ 10MB
- **兼容性**: CSV 支持 UTF-8 和 GBK 编码；Excel 支持 `.xlsx`（Office 2007+）和 `.xls`（Office 97-2003）；Excel 日期列兼容日期数值和字符串两种格式
- **可观测性**: 上传请求记录 traceId + 文件名 + 文件格式（CSV/EXCEL）+ examNo + 学生数 + 题目数 + 处理耗时到日志；解析失败记录具体行号和错误原因

## 依赖与假设

- **依赖**:
  - `org.apache.poi:poi-ooxml` — Excel 解析（需评估 pom.xml 禁动清单）
  - `application/file/parse/FileParser` 接口 + `FileParserRegistry` — 解析器注册与路由
  - `infrastructure/mysql/file/repository/ExamRecordRepository` — MySQL 持久化（需更新方法签名）
  - `common/exception/ErrorCode` — 新增 A0016（exam_no 重复拒绝）
  - `org.springframework.context.ApplicationEventPublisher` — 事件发布（成绩模块仅依赖此 Spring 基础设施）
  - `application/file/grade/event/GradeUploadedEvent` — 已有，成绩上传事件（数据可能需扩展以携带完整解析结果）
  - `application/file/grade/event/GradeDeletedEvent` — **新增**，成绩删除事件
  - `infrastructure/neo4j/repository/GraphNodeRepository` — **仅由事件监听器注入**，成绩模块不直接依赖
  - `infrastructure/storage/FileStorageService` / MinIO — **不再依赖**（移除注入和调用）
- **假设**:
  - 成绩文件包含学号列（`student_no`）和考试编号列（`考试编号`），否则上传被拒绝
  - 成绩格式统一为 `raw_score/max_score`，缺考为 `-/-`
  - Excel 文件无合并单元格（与 CSV 双行表头结构一致）
  - MySQL / Neo4j 均通过 podman 本地运行
  - 同一文件内所有学生属于同一次考试（一个文件 = 一个 exam_no）

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。
