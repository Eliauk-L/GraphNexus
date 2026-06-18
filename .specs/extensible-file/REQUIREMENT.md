# REQUIREMENT: 文件上传可扩展架构

- **Change ID**: extensible-file
- **关联**: `@.specs/extensible-file/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为管理员，我想上传文档文件（PDF/TXT）后系统自动完成解析→知识抽取→图谱融合，一次请求返回最终结果，以便无需手动逐步操作。
- **US-2**：作为管理员，我想通过独立的成绩上传端点上传 CSV 成绩文件，以便成绩数据和文档数据职责清晰、互不耦合。
- **US-3**：作为开发者，我想新增一种文件类型只需实现解析器接口并注册，无需修改 Controller 和 Service 核心代码，以便系统具备开闭原则的可扩展性。
- **US-4**：作为管理员，我想在文档列表页面按文件类型和文件名筛选并分页浏览，以便快速定位目标文件。
- **US-5**：作为管理员，我想查看每个文档的具体处理阶段（解析中/已解析/抽取中/已抽取/融合中/已完成），以便精确定位处理进度和失败位置。
- **US-6**：作为管理员，我想在文档处理失败后从失败步骤手动重试，保留已成功的步骤产物，以便从瞬态错误中恢复而无需重新上传。
- **US-7**：作为管理员，我想保留手动触发解析和手动触发融合的独立端点，以便在异常场景下有精细化的干预能力。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · PDF 同步上传全链路

- **Given** 系统正常运行（MinerU、LLM、Neo4j 可用），本地有一份 ≤50MB 的有效 PDF 文件
- **When** 调用 `POST /api/v1/document/upload` 上传该 PDF，指定 `subject=MATH`
- **Then** HTTP 200 响应返回 `DocumentVO`，其中 `status = "COMPLETED"`，`textContent` 非空，`pageCount > 0`；Neo4j 中存在该文档对应的 EntityNode 和 KnowledgePointNode；MASTERS 边已更新
- **验证方式**: `curl -X POST http://localhost:8080/api/v1/document/upload -F "file=@test.pdf" -F "subject=MATH"` → 查 MySQL `document` 表 status + Neo4j `MATCH (n:KnowledgePoint) RETURN count(n)`

### AC-2 · TXT 同步上传全链路

- **Given** 系统正常运行，本地有一份 UTF-8 编码的 `.txt` 文件，内容为教育类文本（如数学讲义），大小 ≤50MB
- **When** 调用 `POST /api/v1/document/upload` 上传该 TXT 文件，指定 `subject=MATH`
- **Then** HTTP 200 响应返回 `DocumentVO`，其中 `status = "COMPLETED"`，`textContent` 等于 TXT 文件原始文本内容，`fileType = "TXT"`；Neo4j 中存在从该文档文本中抽取的 EntityNode 和 KnowledgePointNode
- **验证方式**: 准备含已知知识点名称的 TXT 文件（如"二次函数顶点坐标公式为..."），上传后查 Neo4j `MATCH (k:KnowledgePoint {name: '二次函数顶点坐标'}) RETURN k` 存在

### AC-3 · GBK 编码 TXT 自动识别

- **Given** 系统正常运行，本地有一份 GBK 编码的 `.txt` 文件
- **When** 调用 `POST /api/v1/document/upload` 上传
- **Then** HTTP 200，`textContent` 无乱码，中文内容正确解析
- **验证方式**: 准备 GBK 编码 TXT（含中文字符），上传后查 `document.text_content` 无 `?` 或乱码字符

### AC-4 · 状态机阶段可观测

- **Given** 上传一个较大 PDF 文件触发同步处理
- **When** 在处理过程中，另一个请求调用 `GET /api/v1/document/{id}` 查询该文档
- **Then** 响应中 `status` 字段为具体阶段状态（`PARSING`/`PARSED`/`EXTRACTING`/`EXTRACTED`/`FUSING`/`COMPLETED`），而非笼统的 `PROCESSING`
- **验证方式**: 上传大文件后立即轮询 `GET /api/v1/document/{id}`，至少观察到一次非 `UPLOADED` 且非 `COMPLETED` 的中间状态

### AC-5 · 同步链路中途失败保留已成功步骤

- **Given** LLM 服务不可用（或模拟超时场景），已成功完成解析步骤的文档（`status = PARSED`，`textContent` 已入库）
- **When** 同步链路的抽取步骤失败
- **Then** 文档 `status` 停留在 `PARSED`（最后一个成功步骤），`failReason` 字段记录抽取失败原因（含步骤标识 `EXTRACTING` + 错误信息），`textContent` 保留不丢失；Neo4j 中不残留不完整的抽取数据
- **验证方式**: 关闭 LLM 服务后上传 PDF → 查 DB 确认 `status = 'PARSED'` 且 `fail_reason IS NOT NULL` 且 `text_content IS NOT NULL`

### AC-6 · 手动 /process 从失败点继续

- **Given** 一份文档处于 `PARSED` 状态且 `failReason` 记录了抽取失败（即 AC-5 后的状态），LLM 服务已恢复
- **When** 调用 `POST /api/v1/document/{id}/process`
- **Then** 从抽取步骤继续执行（跳过大成功的解析），走完抽取→融合后 `status = COMPLETED`，`failReason` 清空，Neo4j 中存在抽取结果
- **验证方式**: 在 AC-5 基础上恢复 LLM → `curl -X POST /api/v1/document/{id}/process` → 查 status 变为 COMPLETED + Neo4j 有 KP 节点

### AC-7 · 手动 /process 从 UPLOADED 触发全链路

- **Given** 一份刚上传的文档处于 `UPLOADED` 状态（未触发同步链路，或同步链路被配置跳过）
- **When** 调用 `POST /api/v1/document/{id}/process`
- **Then** 执行完整链路（解析→抽取→融合）后 `status = COMPLETED`
- **验证方式**: 直接插一条 UPLOADED 文档记录 → `curl -X POST /api/v1/document/{id}/process` → status 变为 COMPLETED

### AC-8 · CSV 成绩独立上传端点

- **Given** 系统正常运行，本地有一份有效 CSV 成绩文件（双行表头格式）
- **When** 调用 `POST /api/v1/grade/upload` 上传该 CSV，指定 `subject=MATH`
- **Then** HTTP 200，响应结构与原 `DocumentController` 的 CSV 上传结果一致；MySQL `exam_record` 表有对应记录；Neo4j 存在 ExamNode + StudentNode + ATTENDED/TESTED 边
- **验证方式**: `curl -X POST http://localhost:8080/api/v1/grade/upload -F "file=@grades.csv" -F "subject=MATH"` → 查 exam_record 表 + Neo4j 图

### AC-9 · CSV 成绩分页列表查询

- **Given** 已通过 `POST /api/v1/grade/upload` 上传了 15 份 CSV 成绩文件
- **When** 调用 `GET /api/v1/grade?pageNum=1&pageSize=10`
- **Then** HTTP 200，返回 `PageResult` 含 10 条记录，`total = 15`
- **验证方式**: `curl "http://localhost:8080/api/v1/grade?pageNum=1&pageSize=10"` → `total: 15, list.length: 10`

### AC-10 · 文档列表按 file_type 精确筛选

- **Given** `document` 表中存在 `file_type = 'PDF'`（3 条）和 `file_type = 'TXT'`（2 条）
- **When** 调用 `GET /api/v1/document?pageNum=1&pageSize=10&file_type=TXT`
- **Then** 返回 `total = 2`，且所有记录的 `fileType` 均为 `"TXT"`
- **验证方式**: `curl "http://localhost:8080/api/v1/document?pageNum=1&pageSize=10&file_type=TXT"` → 验证 list 中全部 fileType=TXT

### AC-11 · 文档列表按 name 模糊搜索

- **Given** `document` 表中存在名为 `"二次函数讲义.pdf"` 和 `"一次函数笔记.txt"` 的文档
- **When** 调用 `GET /api/v1/document?pageNum=1&pageSize=10&name=二次函数`
- **Then** 仅返回文件名包含"二次函数"的文档（1 条）
- **验证方式**: `curl "http://localhost:8080/api/v1/document?pageNum=1&pageSize=10&name=二次函数"` → `total: 1, list[0].name 含"二次函数"`

### AC-12 · 文档列表筛选条件组合

- **Given** `document` 表中有 `PDF+name含"函数"`（2 条）和 `TXT+name含"函数"`（1 条）
- **When** 调用 `GET /api/v1/document?pageNum=1&pageSize=10&file_type=PDF&name=函数`
- **Then** 仅返回同时满足两个条件的文档（2 条），均为 PDF 且 name 含"函数"
- **验证方式**: `curl "GET ...?file_type=PDF&name=函数"` → total=2，均为 PDF + name 含"函数"

### AC-13 · 文档列表不传筛选参数向后兼容

- **Given** `document` 表中有若干条记录
- **When** 调用 `GET /api/v1/document?pageNum=1&pageSize=10`（不传 `file_type` 和 `name`）
- **Then** 返回全部未删除文档的分页列表，行为与改造前完全一致
- **验证方式**: 对比改造前后同一请求的响应（`total`、`list` 排序一致）

### AC-14 · 新增文件类型的可扩展性验证

- **Given** 需要新增 `.docx` 文件类型支持（仅验证可扩展性，不实现完整逻辑）
- **When** 开发者：① 创建 `DocxParser implements FileParser`（返回 `supportedExtensions = {"docx"}` + `supportedType = DOCUMENT`），② 将该类标记为 Spring Bean
- **Then** `FileParserRegistry` 自动发现该解析器；`POST /api/v1/document/upload` 上传 `.docx` 文件时可被路由到文档处理链路；Controller/Service 核心代码无任何修改
- **验证方式**: 创建模拟 DocxParser（只做简单文本提取），上传 .docx 验证路由成功，确认 FileController/DocumentServiceImpl 未改一行

### AC-15 · 现有 PDF 上传行为回归

- **Given** 改造前的 PDF 上传全链路行为作为基线
- **When** 改造后执行相同的 PDF 上传操作
- **Then** 所有可观测结果一致：API 响应结构、MinIO 存储路径、`document` 表记录字段（除新增 `file_type`）、Neo4j 图谱结构、事件发布（`GraphChangedEvent`）
- **验证方式**: 运行项目现有的集成测试套件，全部通过

### AC-16 · 现有 CSV 删除行为保持

- **Given** 已通过 `POST /api/v1/grade/upload` 上传了一份 CSV
- **When** 调用 `DELETE /api/v1/grade/exam/{examNo}`
- **Then** 与改造前 `DELETE /api/v1/document/grade/exam/{examNo}` 行为一致：MySQL exam_record 标记删除、Neo4j ExamNode/边清除、MinIO 文件删除
- **验证方式**: 执行删除 → 查 exam_record.is_deleted=1 + Neo4j MATCH Exam 不存在 + MinIO 文件不存在

---

## 范围切分

### v1（本次必做）

- 文件上传可扩展架构（Pipeline 抽象 + Parser 注册机制）
- `DocumentController` 拆分为 `FileController` + `GradeController`
- 8 状态文档状态机（`UPLOADED → PARSING → PARSED → EXTRACTING → EXTRACTED → FUSING → COMPLETED/FAILED` + `DELETING`）
- PDF 同步上传全链路（上传→解析→抽取→融合→返回）
- TXT 文件类型支持（UTF-8/GBK 编码，直接文本读取）
- `document` 表新增 `file_type` 字段 + 存量数据回填为 `PDF`
- 分页列表条件查询（`file_type` + `name` 可选筛选）
- CSV 成绩独立端点（`POST /api/v1/grade/upload` + `GET /api/v1/grade`）
- 手动 `/process` 端点保留，支持从失败步骤继续
- 手动 `/fusion/execute` 端点保留
- 现有行为 100% 回归通过

### v2（下一轮考虑，不本次）

- 异步处理模式（大文件/慢网络场景，上传立即返回 `UPLOADED`，后台异步走链路，前端轮询或 WebSocket 通知）
- `.docx` / `.xlsx` 文件类型支持
- 全文检索（接入 Elasticsearch 或 MySQL FULLTEXT INDEX）
- 处理进度百分比实时推送（SSE/WebSocket）
- 批量上传（一次上传多个文件并行处理）
- 成绩列表的高级筛选（按科目、日期范围、考试名称等）

### out（永远不做）

- 用户自定义文件类型处理器（上传 jar 包热加载）—— 安全风险，新增类型走代码发布
- 文件格式转换（如 PDF → TXT、DOCX → PDF）—— 这不是 GraphNexus 的职责
- 文件在线预览/编辑 —— 不在此产品范围
- 第三方云存储适配（S3/OSS/CDN 替换 MinIO）—— 存储层已在 infrastructure 抽象，但 v1 只支持 MinIO

---

## 非功能性需求

- **性能**: 同步上传链路（含解析+抽取+融合）对 ≤10MB PDF 文件在 120s 内完成返回；分页查询响应 ≤500ms
- **可观测性**: 每次状态转换记录日志（INFO 级别），含 `documentId` + `fromStatus` + `toStatus` + `elapsedMs`；失败时记录 ERROR 含 `step` + `errorDetail`
- **安全**: 文件上传保持现有校验（类型白名单 + 大小 ≤50MB + MD5 内容指纹）；TXT 文件额外拒绝非文本 MIME 类型
- **兼容性**: API 响应结构（`ApiResult<T>` / `PageResult<T>` 包装）不变；`DocumentVO` 仅新增 `fileType` 字段，不影响已有字段
- **数据完整性**: `file_type` 字段 NOT NULL，存量数据通过 DDL 或启动脚本回填为 `PDF`；状态机转换规则在 DESIGN 阶段精确定义，禁止非法跳转（如 `COMPLETED → PARSING`）

## 依赖与假设

- **依赖**: MinerU v4 API、LLMGateway、Neo4j 5.x、MinIO — 均为已有组件，本次不改动
- **假设**: 历史 `document` 表所有记录均为 PDF 文件（`file_type` 存量回填默认值为 `PDF`）
- **假设**: 前端 Vue 3 SPA 可同步更新 API 端点路径（`/api/v1/document/upload` 仅发文档文件；CSV 发 `/api/v1/grade/upload`）
- **假设**: TXT 文件的 LLM 知识抽取质量不低于 PDF 抽取的 70%（以抽取出的 KnowledgePoint 数量为指标），若实际低于此阈值，不作为本次缺陷
- **假设**: 同步链路各步骤（解析/抽取/融合）的顺序依赖成立，不可并行或乱序

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。