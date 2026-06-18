# REQUIREMENT: CSV 成绩文件上传与解析入库

- **Change ID**: `csv-grade-import`
- **关联**: `@.specs/csv-grade-import/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师/教务人员，我想上传包含学生考试成绩的 CSV 文件，以便将成绩数据纳入 GraphNexus 系统进行图谱化存储和后续分析。
- **US-2**：作为教师，我想查看已上传的成绩记录，以便核对 CSV 解析结果是否与原始文件一致。
- **US-3**：作为系统（自动化流程），我想 CSV 上传后自动解析并同步写入 MySQL（原始记录 + JSON 成绩明细）、MinIO（原始文件留存）和 Neo4j（Student → Exam → KnowledgePoint 图谱路径），以便为宽图谱融合和掌握度分析提供结构化数据基础。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · CSV 上传成功 — 全链路入库

- **Given** 系统正常运行，MinIO / MySQL / Neo4j 均可用，上传的 CSV 文件符合模板格式（双行表头：第 1 行 `姓名,班级,考试编号,考试名称,日期,总分,班级排名,题1,...,题N`，第 2 行为每题对应知识点名称，**同一题含多个知识点时用 `;` 分隔**，如 `二次函数图像与性质;二次函数顶点式`），数据行成绩格式为 `raw_score/max_score`，列数一致
- **When** 发送 `POST /api/v1/document/upload`（`multipart/form-data`，字段名 `file`=CSV 文件，`subject`=考试学科），系统按 `.csv` 后缀识别为成绩文件并走成绩处理链路
- **Then** HTTP 200，响应体包含解析摘要（`examNo`、`examName`、`examDate`、`studentCount`、`questionCount`、`knowledgePoints` 列表）。同时：
  - **MySQL** `exam_record` 表中新增 N 条记录（N = 学生人数），每条含 `student_no`、`name`、`class_name`、`exam_no`（来源于 CSV `考试编号` 列，格式如 `E20200041`，同一 CSV 内所有记录共享同一 `exam_no`）、`exam_name`、`exam_date`、`total_score`、`class_rank`、`score_details`（JSON 数组，每元素 `{question, kpNames: [...], rawScore, maxScore}`）、`csv_file_path`（MinIO 路径）、`csv_md5`
  - **MinIO** 中原始 CSV 文件已保存，路径与 MySQL 记录一致
  - **Neo4j** 中存在：N 个 `Student` 节点（`studentNo` + `name` + `className`），1 个 `Exam` 节点（`examNo` 来自 CSV `考试编号` + `name` + `examDate` + `subject`），M 个 `KnowledgePoint` 节点（`name` + `subject`，M = 去重后的知识点数），N 条 `ATTENDED` 边（`Student → Exam`，纯结构无属性），M 条 `TESTED` 边（`Exam → KnowledgePoint`，纯结构无属性，去重后每个知识点一条）。**所有分数仅存 MySQL**
- **验证方式**: 准备 `docs/学生成绩表示例.csv`（需先补上学号列），`curl -X POST -F "file=@学生成绩表示例.csv" -F "subject=数学" http://localhost:8080/api/v1/document/upload`，然后：
  - MySQL: `SELECT COUNT(*) FROM exam_record WHERE exam_name='第一次月考'` 断言 = 10
  - MinIO: 检查 `csv_file_path` 对应的文件是否可下载
  - Neo4j: `MATCH (s:Student)-[:ATTENDED]->(e:Exam)-[:TESTED]->(kp:KnowledgePoint) RETURN count(*)` 断言 > 0

### AC-2 · CSV 格式校验 — 非法文件拒绝

- **Given** 系统正常运行
- **When** 发送 `POST /api/v1/document/upload`，上传 `.csv` 后缀文件但内容不符合成绩 CSV 模板：
  - 表头行数与模板不符（只有 1 行表头，缺少知识点行）
  - 成绩格式异常（如 `A/B` 含非数字字符，且非缺考标记 `-/-`）
  - 数据行列数与表头列数不一致
- **Then** HTTP 400，响应体包含错误码（如 `A0011`）和具体错误描述（指明哪一行/哪一列有问题），MySQL / MinIO / Neo4j 均无任何写入
- **验证方式**: 准备 3 个非法 CSV 文件（缺知识点行 / 成绩格式异常 / 列数不一致），逐一 curl 上传，断言全部返回 400 + 错误码，再查询 MySQL/Neo4j 确认无残留数据

### AC-3 · 重复上传幂等 — 覆盖更新

- **Given** AC-1 已成功上传过一次 CSV 文件（MD5 = `abc123`）
- **When** 再次上传内容完全相同的 CSV 文件（MD5 相同）
- **Then** HTTP 200，MySQL 中该 CSV 对应的 `exam_record` 记录被覆盖更新（非新增重复记录），Neo4j 中同一 Exam 节点下的 `ATTENDED` 和 `TESTED` 边被删除后重建（非创建重复边），MinIO 中文件覆盖
- **验证方式**: 第一次上传后记录 MySQL `exam_record` 行数 R，第二次上传相同 CSV 后断言行数仍为 R；Neo4j 中 `MATCH (e:Exam {examNo:'E20200041'})-[t:TESTED]->() RETURN count(t)` 断言边数等于去重后知识点数

### AC-4 · 查询成绩记录列表

- **Given** AC-1 已上传过 CSV，`exam_record` 表中有数据
- **When** 发送 `GET /api/v1/document/grade/exam/{examName}?examDate=2025-03-15`
- **Then** HTTP 200，响应体包含分页的学生成绩列表，每条含 `studentNo`、`name`、`className`、`totalScore`、`classRank`、`scoreDetails`（JSON 数组）
- **验证方式**: `curl "http://localhost:8080/api/v1/document/grade/exam/第一次月考?examDate=2025-03-15"`，用 `jq '.data.list | length'` 断言 = 10

### AC-5 · Neo4j 图谱路径可遍历 + MySQL 分数可查

- **Given** AC-1 已上传过 CSV
- **When** 执行两步查询：
  ① Neo4j：`MATCH (s:Student {studentNo:'S2024001'})-[:ATTENDED]->(e:Exam {examNo:'E20200041'})-[:TESTED]->(kp) RETURN kp.name`
  ② MySQL：`SELECT score_details FROM exam_record WHERE student_no='S2024001' AND exam_no='E20200041'`
- **Then** Neo4j 返回该考试考查的所有知识点名称列表，MySQL 返回该学生各题得分明细（含知识点名和 rawScore/maxScore），应用层可按知识点名称对齐两份结果
- **验证方式**: 分别执行上述 Cypher 和 SQL，断言 Neo4j 返回行数 = 去重后知识点数，MySQL `score_details` JSON 数组长度 = 题目数

### AC-6 · 解析容错 — 缺考标记处理

- **Given** CSV 中某学生的某题成绩标记为 `-/-`（表示缺考/未作答）
- **When** 上传该 CSV
- **Then** HTTP 200，整体解析成功。该题在 MySQL `score_details` JSON 中 `rawScore=null, maxScore=null`；Neo4j 中该知识点 TESTED 边的创建不受影响（缺考不改变考试考查了哪些知识点这一结构事实）
- **验证方式**: 准备含 `-/-` 的 CSV（如将张三的题4改为 `-/-`），上传后查询 MySQL 确认该题 score 为 null，Neo4j 中张三对应的 TESTED 边数 = 总题数 - 缺考题数

### AC-7 · 级联删除考试成绩

- **Given** AC-1 已上传过 CSV，MySQL `exam_record` 表中有该次考试记录，MinIO 中存在对应 CSV 文件，Neo4j 中存在对应 Exam 节点及 ATTENDED/TESTED 边
- **When** 发送 `DELETE /api/v1/document/grade/exam/{examNo}`
- **Then** HTTP 200，响应体包含被删除的 `examNo` 和清理摘要（删除的学生记录数、MinIO 文件路径、Neo4j 边数）。同时：
  - **MySQL** `exam_record` 表中该 `examNo` 对应的所有记录被删除
  - **MinIO** 中对应的 CSV 文件被删除
  - **Neo4j** 中该 Exam 节点被删除，所有指向/来自该 Exam 的 ATTENDED 边和 TESTED 边被删除
  - **Neo4j** 中 Student 节点和 KnowledgePoint 节点**不被删除**（它们可能被其他考试引用）
- **验证方式**: 先上传 CSV 获取 `examNo`，再 `curl -X DELETE http://localhost:8080/api/v1/document/grade/exam/{examNo}`，然后：
  - MySQL: `SELECT COUNT(*) FROM exam_record WHERE exam_no='{examNo}'` 断言 = 0
  - MinIO: 检查原 `csv_file_path` 对应文件是否不存在
  - Neo4j: `MATCH (e:Exam {examNo:'{examNo}'}) RETURN count(e)` 断言 = 0
  - Neo4j: `MATCH (s:Student) RETURN count(s)` 断言 > 0（Student 节点保留）
  - Neo4j: `MATCH (kp:KnowledgePoint) RETURN count(kp)` 断言 > 0（KP 节点保留）

---

## 范围切分

### v1（本次必做）

- 通过现有 `POST /api/v1/document/upload` 接口上传，按 `.csv` 后缀自动识别为成绩文件并走成绩处理链路（`.pdf` 走既有文档处理链路不变）
- 双行表头 CSV 解析器（动态列数支持）
- MySQL `exam_record` 表 + JPA Repository + JSON 列 `score_details`
- MinIO CSV 文件存储（复用 `FileStorageService`）
- Neo4j `StudentNode` + `ExamNode` + `KnowledgePointNode` 创建/MERGE
- Neo4j `ATTENDED` 边 + `TESTED` 边写入
- 同步处理（上传 → 解析 → MySQL → MinIO → Neo4j → 返回）
- CSV MD5 判重 + 幂等覆盖更新
- 基本格式校验（扩展名/表头行数/成绩格式/列数一致性）
- 缺考标记 `-/-` 容错
- 成绩记录查询 API（`GET /api/v1/document/grade/exam/{examName}`）
- 成绩删除 API（`DELETE /api/v1/document/grade/exam/{examNo}`），级联删除 MySQL + MinIO + Neo4j（Exam 节点 + ATTENDED/TESTED 边），保留 Student 和 KnowledgePoint 节点
- 错误码 A0011~A0015（CSV 解析/删除相关）

### v2（下一轮考虑，不本次）

- **学号自动生成**：当 CSV 缺少学号列时，系统按 `班级+姓名` hash 自动生成 `studentNo`
- **CSV 模板预设**：支持在系统中预设 CSV 列映射模板，兼容不同学校的 CSV 格式变体
- **成绩查询增强**：按学生查询历史成绩、按知识点查询得分分布、分页/排序/筛选
- **异步处理**：大文件（>100 学生）改为 MQ 异步处理，上传立即返回 `taskId` 供轮询
- **CSV 解析预览**：上传后先返回解析预览（前 3 行），用户确认后再正式入库
- **同一 CSV 内同名 KP 合并**：当前 v1 每题独立创建 KnowledgePoint，v2 可在同次考试内按 KP 名称去重（多个 `题N` 指向同一 KP 名称时共享一个节点）

### out（永远不做）

- **EventNode 细粒度事件模型**：每道题一个事件节点（`HAS_EVENT` + `RELATES_TO` + `BELONGS_TO_EXAM`）——本 change 采用 Student → Exam → KnowledgePoint 三元模型，EventNode 不在本系统范围内
- **与已有文档图谱 KnowledgePoint 自动融合**：CSV 解析的 KP 与文档抽取的 KP 做 ALIGNED_TO 对齐——属于独立 change `wide-graph-fusion`
- **跨考试 MASTERS 掌握度聚合边**：Student → KnowledgePoint 直连的累积掌握度——属于宽图谱融合 change 的聚合输出
- **前端成绩管理界面**：纯后端 API，不做可视化
- **手动录入成绩**：仅支持 CSV 文件批量导入，不提供单条成绩手动录入表单

---

## 非功能性需求

- **性能**: 单次 CSV 上传（≤ 50 学生 × ≤ 30 题）同步处理完成时间 ≤ 5s（不含 MinIO 上传网络延迟）
- **可访问性**: 无
- **安全**: CSV 上传接口需认证（JWT），未登录用户返回 401；CSV 文件大小限制 ≤ 10MB
- **兼容性**: 支持 UTF-8 和 GBK 编码的 CSV 文件（自动检测 BOM 或通过 charset 参数指定）
- **可观测性**: 上传请求记录 traceId + 文件名 + MD5 + 学生数 + 题目数 + 处理耗时到日志；解析失败记录具体行号和错误原因

## 依赖与假设

- **依赖**:
  - `api/document/controller/` — 新增 `GradeController`（或扩展现有 `DocumentController`），归入 document 模块 L1
  - `application/document/service/` — 新增 `GradeService` 负责 CSV 解析 + 入库编排 + 级联删除，归入 document 模块 L2
  - `infrastructure/storage/FileStorageService` — MinIO CSV 文件上传与删除（已有 `deleteFile`）
  - `infrastructure/neo4j/repository/GraphNodeRepository` — Neo4j 通用图写入与删除（已有）
  - `infrastructure/neo4j/node/` — 复用 `KnowledgePointNode`，新增 `StudentNode`、`ExamNode`
  - `infrastructure/neo4j/edge/` — 新增 `AttendedEdge`、`TestedEdge`
  - `common/exception/ErrorCode` — 新增错误码 A0011~A0015
  - MySQL JPA + Hibernate — `exam_record` 表与 DO 映射（`infrastructure/mysql/document/`）
- **假设**:
  - CSV 文件包含学号列（`student_no`）和考试编号列（`考试编号`，如 `E20200041`），否则上传被拒绝（v1 不做自动生成）
  - CSV 成绩格式统一为 `raw_score/max_score`，缺考为 `-/-`
  - CSV 编码为 UTF-8（GBK 容错在 DESIGN 中评估实现成本后决定是否纳入 v1）
  - MinIO / MySQL / Neo4j 均通过 podman 本地运行（与既有开发环境一致）
  - 同一 CSV 内所有学生属于同一次考试（一个 CSV = 一个 Exam 节点）

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。