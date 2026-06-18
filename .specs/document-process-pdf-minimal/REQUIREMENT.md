# REQUIREMENT: 文档处理模块 — PDF 上传/解析/管理最小化实现

- **Change ID**: `document-process-pdf-minimal`
- **关联**: `@.specs/document-process-pdf-minimal/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师/管理员，我想通过 REST API 上传 PDF 教辅文件，以便将教学资料纳入系统进行后续处理。
- **US-2**：作为系统（自动化流程），我想对已上传的 PDF 文件调用解析能力提取文本内容、页数和元信息，以便为下游模块（图构建/NLP 抽取）提供结构化输入。
- **US-3**：作为用户，我想查询、更新、删除已上传的文档记录，以便管理文档生命周期。
- **US-4**：作为开发者，我想 `DocumentParser` 接口定义清晰的契约（输入 PDF 字节流 → 输出解析结果对象），以便未来从 PDFBox 切换到 MinerU 时无需修改 L2 Service 和 L1 Controller 的调用代码。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · PDF 文件上传并存储至 MinIO

- **Given** 系统已连接 MinIO 和 MySQL
- **When** 发送 `POST /api/v1/document/upload`，`multipart/form-data` 携带一个有效的 PDF 文件（≤ 50MB，`application/pdf`）
- **Then** HTTP 200，响应体 `ApiResponse<DocumentVO>` 包含 `documentId`、`name`、`fileSize`、`status=UPLOADED`；MinIO 中对应 bucket 存在该文件；MySQL `document` 表中存在对应记录
- **验证方式**: `curl -F "file=@test.pdf" http://localhost:8080/api/v1/document/upload`，检查响应 JSON + MinIO 控制台 + MySQL 查询

### AC-2 · PDF 解析提取文本

- **Given** 文档记录已存在且 status 为 UPLOADED
- **When** 发送 `POST /api/v1/document/{documentId}/process`
- **Then** HTTP 200，响应体 `ApiResponse<ParseResultVO>` 包含 `textContent`（非空字符串）、`pageCount`（≥ 1）、`metadata`（标题/作者等，可为空）；文档 status 最终为 COMPLETED
- **验证方式**: 先完成 AC-1 上传，再 `curl -X POST http://localhost:8080/api/v1/document/{id}/process`，检查返回的 `textContent` 与原始 PDF 内容一致，`pageCount` 与原始 PDF 页数一致

### AC-3 · 文档状态流转正确

- **Given** 文档记录已存在
- **When** 文档经历上传→解析→完成（或失败）
- **Then** 状态按 `UPLOADED → PROCESSING → COMPLETED` 严格流转；解析异常时转为 `FAILED`；状态不可逆向跳过（如 UPLOADED 直接到 COMPLETED 被拒绝）
- **验证方式**: 单元测试验证状态机转换表；集成测试模拟解析失败，断言状态为 FAILED 且 `failReason` 非空

### AC-4 · 文档分页查询

- **Given** 系统中存在 N 条文档记录
- **When** 发送 `GET /api/v1/document?pageNum=1&pageSize=10`
- **Then** HTTP 200，响应体 `ApiResponse<PageResult<DocumentVO>>`，`data.list` 长度 ≤ 10，`data.total` = N，`data.pageNum` = 1，`data.pageSize` = 10
- **验证方式**: 上传 3 个 PDF 后 `curl "http://localhost:8080/api/v1/document?pageNum=1&pageSize=2"`，断言 `list` 长度 = 2，`total` = 3

### AC-5 · 文档删除

- **Given** 文档记录已存在且 MinIO 中有关联文件
- **When** 发送 `DELETE /api/v1/document/{documentId}`
- **Then** HTTP 200，MySQL 中对应记录逻辑删除（`is_deleted` = 1），MinIO 中文件被删除
- **验证方式**: `curl -X DELETE http://localhost:8080/api/v1/document/{id}`，再查 MySQL `is_deleted`=1 且 MinIO 中文件不存在

### AC-6 · DocumentParser 接口可替换

- **Given** 定义了 `DocumentParser` 接口（方法签名 `ParseResult parse(byte[] pdfBytes)`），已有 `PdfBoxDocumentParser` 实现类
- **When** 在测试中注入一个 mock 的 `DocumentParser` 实现替换 `PdfBoxDocumentParser`
- **Then** L2 Service 调用的是 mock 实现，`PdfBoxDocumentParser` 未被调用；Service 代码只依赖 `DocumentParser` 接口，不依赖具体实现类
- **验证方式**: `mvn test` 中 `DocumentServiceTest` 使用 `@MockBean` 或构造器注入 mock，断言 service 行为与 mock 一致

### AC-7 · 非法文件被拒绝

- **Given** 系统已启动
- **When** 发送 `POST /api/v1/document/upload`，携带非 PDF 文件（如 `test.txt`、`test.png`）或超过 50MB 的文件
- **Then** HTTP 400（文件类型错误返回 `ErrorCode.FILE_TYPE_NOT_SUPPORTED`）或 413（文件过大返回 `ErrorCode.FILE_SIZE_EXCEEDED`），MinIO 和 MySQL 均无新记录
- **验证方式**: `curl -F "file=@test.txt" ...` 断言 HTTP 400 + 错误码 `A0002`；`dd if=/dev/zero bs=1m count=51 | curl -F "file=@-" ...` 断言被拒绝

### AC-8 · 文档更新

- **Given** 文档记录已存在
- **When** 发送 `PUT /api/v1/document/{documentId}`，body 携带 `{"name": "renamed.pdf"}`
- **Then** HTTP 200，MySQL 中 `name` 更新为 `renamed.pdf`，`update_time` 自动刷新
- **验证方式**: `curl -X PUT -H "Content-Type: application/json" -d '{"name":"renamed.pdf"}' ...` 后查 MySQL 确认

---

## 范围切分

### v1（本次必做）

- PDF 文件上传 → MinIO 存储 → MySQL 元数据记录
- PDF 解析（Apache PDFBox）→ 文本提取 + 页数 + 元信息
- 文档 CRUD（查询/分页/更新/删除）
- 文档状态机（UPLOADED → PROCESSING → COMPLETED / FAILED）
- `DocumentParser` 可扩展接口 + `PdfBoxDocumentParser` 实现
- 文件类型校验（仅 `application/pdf`）+ 大小限制
- L2 `application/document/service/` + L1 `api/document/controller/` 全链路
- L3 `infrastructure/storage/` MinIO 适配器 + `infrastructure/mysql/` Document DO/Repository
- MySQL `document` 表 DDL（含 `is_deleted` 逻辑删除）
- 异常处理：文件不存在、解析失败、非法状态转换 → 走已有 `BusinessException` + `GlobalExceptionHandler` 体系

### v2（下一轮考虑，不本次）

- 异步解析：上传后通过 RabbitMQ 消息触发解析，避免请求线程阻塞
- MinerU 适配器：实现 `MinerUDocumentParser`，通过 `DocumentParser` 接口插拔替换 PDFBox
- 解析结果缓存：Redis 缓存已解析文本，避免重复解析
- 文件版本管理：同名文件覆盖时保留历史版本
- 批量上传：`multipart/mixed` 一次上传多个 PDF
- 文档搜索：MySQL `LIKE` 查询 `textContent` 基础搜索（或引入 Elasticsearch）
- 解析进度查询：`GET /api/v1/document/{id}/process/progress` 返回百分比（异步场景）

### out（永远不做）

- NLP 实体/关系抽取：属于未来「图处理」模块或独立 NLP 微服务
- OCR 图像识别（扫描版 PDF）：不属于 PDF 文本提取范畴，需专用 OCR 引擎
- PDF 生成/导出/水印：系统是消费 PDF，不是生产 PDF
- 前端文件拖拽上传界面：纯后端 API 模块
- 文档预览/缩略图生成
- 第三方云存储（OSS/S3 直接上传）：MinIO 为唯一存储后端

---

## 非功能性需求

- **性能**: 50MB 以内的 PDF 上传+存储 ≤ 3s；解析 ≤ 500ms（PDFBox 同步提取）；分页查询 ≤ 200ms
- **可访问性**: 无（纯后端 API）
- **安全**: 仅允许 `application/pdf` MIME 类型上传；文件大小上限 50MB（可配置）；API 路径需通过认证后访问（JWT 由后续 `basic` 模块实现，本次不对未认证请求放行或放行不做强制——与 `init-platform` 现状一致：Spring Security 依赖已引入但未启用安全规则）
- **兼容性**: PDFBox 支持 PDF 1.0 ~ 2.0 规范；JDK 17；MinIO 8.x API
- **可观测性**: 全链路 Trace ID 串联上传→解析（已有 `TraceIdFilter`）；解析失败时 `errorMessage` 写入 DB + 日志

## 依赖与假设

- **依赖**: `init-platform` 已完成的公共模块（`BusinessException`、`ErrorCode`、`GlobalExceptionHandler`、`TraceIdFilter`、`ApiResponse`、`PageResult`）
- **依赖**: MinIO 容器本地运行（端点 `http://localhost:9000`，bucket 由应用启动时自动创建）
- **依赖**: MySQL 8.0 本地运行（`graphnexus` 数据库已创建，DDL 由 Hibernate `ddl-auto: update` 或手动脚本执行）
- **依赖**: `pom.xml` 中已存在的依赖：`minio`、`pdfbox`、`spring-boot-starter-data-jpa`、`mysql-connector-j`
- **假设**: 测试用 PDF 为文本型 PDF（非扫描图片），PDFBox 能正常提取文本
- **假设**: 上传的 PDF 文件编码为 UTF-8 或 Latin-1，CJK 字体可能需额外测试
- **假设**: MinIO bucket 名称为 `graphnexus-dev`，应用启动时自动创建

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。