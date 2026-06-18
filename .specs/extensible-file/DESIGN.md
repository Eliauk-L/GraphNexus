# DESIGN: 文件上传可扩展架构

- **Change ID**: extensible-file
- **关联**: `@.specs/extensible-file/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> CONTEXT.md 已有完整锁定栈，跳过卡片选择，直接锁定。

- **选定**：既有 Java 后端栈（沿用，无变更）
- **后端**：Java 17 LTS + Spring Boot 3.3.x + Maven 3.9.x（单模块）
- **数据库**：MySQL 8.0（Spring Data JPA + Hibernate 6.4+） + Neo4j 5.x（Spring Data Neo4j 7.x + Neo4jClient）
- **文件存储**：MinIO 8.x
- **关键依赖**：Spring AI 1.0.x（LLM）、MinerU v4 API（PDF 解析）、Apache PDFBox 3.x（PDF 兜底）、Spring ApplicationEvent（模块解耦）
- **理由**：纯后端重构，不涉及新技术栈引入。所有依赖已在项目中稳定运行
- **明确排除**：不引入工作流引擎（如 Camunda）、不引入 OSGi/SPI 插件框架、不引入新的消息队列模式（RabbitMQ 虽已配置但本次不用）

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（已验证的实际文件路径）：

【重构 · 既有类】
- api/file/controller/FileController.java
  · 移除成绩端点（queryGrade/deleteGrade → GradeController）
  · upload() 移除 CSV 路由分支 + 字符串比较 → 统一走 Pipeline
  · @RequestMapping 从 /api/v1/file/document → /api/v1/document
- api/file/dto/core/FileVO.java
  · 新增 fileType 字段
- application/file/core/service/FileService.java
  · 移除 uploadGradeCsv/deleteGradeByExamNo/queryGradeByExam 三个方法
- application/file/core/service/impl/FileServiceImpl.java
  · 移除 PDF_MIME_TYPE 硬编码 MIME 校验（line 83）
  · 移除 MinerU→PDFBox 硬编码回退链（line 161-201）→ 由 Pipeline 编排
  · 移除三个成绩委托方法（line 316-327）
  · 显式构造器注入改为 @RequiredArgsConstructor
  · 新增条件查询方法
- application/file/parse/parser/FileParserRegistry.java
  · Map<String, FileParser> → Map<String, List<FileParser>>，支持多解析器 per extension
  · 新增 getParsers() 返回有序列表（主解析器优先），getParser() 保持兼容
- application/file/parse/model/FileParseType.java
  · PDF_DOCUMENT 重命名为 DOCUMENT（语义不再绑定 PDF）
  · 新增 TXT 值
- application/file/parse/parser/DocumentParser.java（保持接口不变）
- application/file/parse/parser/PdfBoxDocumentParser.java
  · supportedType() 返回值由 PDF_DOCUMENT → DOCUMENT
- application/file/parse/parser/MinerUDocumentParser.java
  · supportedType() 返回值由 PDF_DOCUMENT → DOCUMENT
- infrastructure/mysql/file/entity/FileDO.java
  · 修正 @Table(name = "document") → @Table(name = "file")（与 DB 实际表名一致）
  · 新增 fileType 字段（@Enumerated(STRING) FileParseType）
- infrastructure/mysql/file/entity/FileStatus.java
  · 扩展 5→8 状态：新增 PARSING/PARSED/EXTRACTING/EXTRACTED/FUSING
  · 重写 validateTransition() 支持失败回退逻辑
- infrastructure/mysql/file/repository/FileRepository.java
  · 新增条件查询方法 findByConditions(fileType, nameLike, pageable)
- application/file/core/model/FileBO.java
  · 新增 fileType 字段
- application/file/core/model/UpdateFileBO.java（无变更）
- application/file/parse/model/ParseResult.java（无变更）
- infrastructure/storage/FileStorageService.java（无变更）

【新建】
- api/file/controller/GradeController.java
  · POST /api/v1/grade/upload, GET /api/v1/grade,
    GET /api/v1/grade/exam/{examNo}, DELETE /api/v1/grade/exam/{examNo}
- application/file/core/pipeline/FileProcessingPipeline.java（接口）
- application/file/core/pipeline/DocumentProcessingPipeline.java
- application/file/core/pipeline/GradeProcessingPipeline.java
- application/file/parse/parser/TxtFileParser.java（实现 DocumentParser）
- api/file/dto/core/FileQueryRequest.java（查询 DTO record）

【不动 · 只调用不修改】
- application/file/upload/service/GradeService.java + impl（CSV 逻辑保持不变）
- application/file/parse/parser/CsvGradeParser.java（FileParser 实现不变）
- application/file/upload/event/GradeUploadedEvent.java（发布点不变）
- application/graph/fusion/event/GradeUploadedEventListener.java
- application/graph/metrics/event/GraphChangedEvent.java + MetricsCacheInvalidator.java

【禁动 · AI 不许碰】
- application/graph/fusion/service/FusionService.java（只调用）
- application/graph/construction/（图谱构建逻辑）
- application/llmgateway/（LLM 调用）
- infrastructure/neo4j/（Neo4j 持久化层）
- infrastructure/storage/FileStorageService.java（MinIO 适配器）
- pom.xml
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|----------|-----------------|------|
| 文件扩展名→解析器路由 | `FileParserRegistry`（`Map<String, FileParser>` 单 key 单 value） | **重构**为 `Map<String, List<FileParser>>`，支持多解析器 per extension + 优先级排序（理由：`MinerUDocumentParser` 和 `PdfBoxDocumentParser` 都注册 `.pdf`，后者静默覆盖前者，无法表达 MinerU→PDFBox 兜底链） |
| PDF 解析器接口 | `DocumentParser extends FileParser` + `MinerUDocumentParser` + `PdfBoxDocumentParser` | **沿用**，`supportedType()` 从 `PDF_DOCUMENT` 改为 `DOCUMENT`（语义通用化，不再绑定 PDF） |
| TXT 文本解析 | **没有**（PDFBox 和 MinerU 都只能处理 PDF） | **新建** `TxtFileParser`（实现 `DocumentParser`，编码检测 UTF-8→GBK 回退，复用 `CsvGradeParser.tryDecode()` 同款策略） |
| CSV 成绩解析 | `CsvGradeParser implements FileParser` | **沿用**（无变更） |
| 文件类型枚举 | `FileParseType`（`CSV_GRADE`, `PDF_DOCUMENT`） | **扩展**：`PDF_DOCUMENT` → `DOCUMENT` + 新增 `TXT` |
| 解析结果模型 | `ParseResult`（textContent/pageCount/metadata） | **沿用**，TXT 返回 pageCount=1 + metadata=emptyMap |
| 文档元数据持久化 | `FileDO` → `@Table(name = "document")` + `FileRepository` | **沿用**，新增 `fileType` 字段 + 扩展 `status` 枚举值 |
| 文档状态枚举 | `FileStatus`（5 状态：UPLOADED/PROCESSING/COMPLETED/FAILED/DELETING） | **扩展**为 8 状态，重写 `validateTransition()` 支持失败回退 |
| MinIO 文件存储 | `FileStorageService`（upload/download/delete） | **沿用**，路径结构不变 |
| Neo4j 图写入 | `GraphNodeRepository`（Cypher MERGE/MATCH） | **沿用**，不修改 |
| 事件发布 | `ApplicationEventPublisher` + `@EventListener` | **沿用**，`GradeUploadedEvent` / `GraphChangedEvent` 发布点不变 |
| 分页响应 | `PageResult<T>` record | **沿用** |
| FileService 接口 | `FileService`（含 3 个成绩委托方法：`uploadGradeCsv`/`deleteGradeByExamNo`/`queryGradeByExam`） | **清理**：移除三个成绩委托方法，接口回归纯文档职责 |
| MIME 类型校验 | `FileServiceImpl.upload()` 硬编码 `"application/pdf"` 白名单（line 83） | **移除**，改为 Pipeline 层按 `FileParseType` 校验（PDF→application/pdf, TXT→text/plain, CSV→text/csv） |
| 全链路 Pipeline 编排 | **没有**（当前 upload/process 分离，手动触发；`FileServiceImpl.process()` 硬编码 MinerU→PDFBox 链） | **新建** `FileProcessingPipeline` + `DocumentProcessingPipeline` + `GradeProcessingPipeline` |
| Controller 路由 | `FileController.upload()` 内 `name().equals("CSV_GRADE")` 字符串比较（line 69） | **消除**：Controller 统一走 Pipeline，CSV 拆出 `GradeController` |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：**沿用** Repository 模式（JpaRepository + 显式 @Query JPQL）
- 依赖注入：**统一** @RequiredArgsConstructor（FileServiceImpl 当前手动构造器注入，改为 Lombok 与项目风格一致）
- 错误处理：**沿用** BusinessException + GlobalExceptionHandler
- 事件解耦：**沿用** ApplicationEventPublisher + @EventListener（模块间通知）
- 策略模式：**沿用**（FileParser 接口 + Spring Bean 自动注册，与既有 KpMatchingStrategy/WeightCalculationStrategy/SubgraphPruningStrategy 一致）
- FileParserRegistry：**重构**（Map<String, FileParser> → Map<String, List<FileParser>>，支持多解析器 per extension + 优先级排序）
- Pipeline 编排：**引入新模式** → 理由：既有架构中没有"串联多步骤 + 状态机驱动 + 断点续跑"的编排抽象。遵循 Spring 单机同步执行范式，不引入工作流引擎
- Controller 拆分：**引入新模式** → 理由：当前 FileController 混合文档+成绩端点。引入 GradeController 实现职责分离，遵循既有 L1→L2→L3 分层
- 接口隔离：**清理** FileService 移除成绩委托方法 → 理由：接口含 uploadGradeCsv/deleteGradeByExamNo/queryGradeByExam 三个纯委托方法，违反接口隔离原则
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| **D1** | **Pipeline 抽象**：定义 `FileProcessingPipeline` 接口（`process(MultipartFile, subject)` + `retry(documentId)`），由 `DocumentProcessingPipeline` 和 `GradeProcessingPipeline` 分别实现 | (A) 不引入新接口，在 Service 层加 if-else 分支；(B) 用 Spring StateMachine 做状态机驱动的编排 | 选 A 违反开闭原则正是本次要解决的；选 B 太重，引入新依赖 + 学习曲线。自己写 Pipeline 可以精确控制状态机 + 同步执行 + 保持架构简单 | 需手工管理状态转换和错误恢复，约多写 ~100 行编排代码，但能精确控制行为 |
| **D2** | **Parser 统一 + FileParseType 重命名**：保留 `FileParser` → `DocumentParser` 继承链不变。`FileParseType.PDF_DOCUMENT` 重命名为 `DOCUMENT`（语义不再绑定 PDF），新增 `TXT`。`PdfBoxDocumentParser`/`MinerUDocumentParser` 的 `supportedType()` 返回 `DOCUMENT`，`TxtFileParser` 也返回 `DOCUMENT`。`FileParserRegistry` 通过扩展名区分 PDF vs TXT | (A) 保留 `PDF_DOCUMENT` + 新增 `TXT_DOCUMENT`；(B) 合并为一个 `DOCUMENT` 类型 | 选 A 会导致 TXT 和 PDF 在类型系统上被当作不同类别，但实际上它们共享同一条处理链路（DocumentProcessingPipeline）。合并为 `DOCUMENT` + 扩展名路由更简洁 | PDF/TXT 的类型信息在 `FileParseType` 层面丢失（需通过 `fileType` + 扩展名联合区分），但列表查询可同时用 `file_type` + 文件名后缀来判断 |
| **D3** | **同步链路编排**：`DocumentProcessingPipeline.process()` 内联调用 解析 → 抽取 → 融合，状态机在 Pipeline 内部管理。HTTP 请求等待全链路完成再返回 | (A) 异步模式（上传立即返回 UPLOADED，后台队列处理）；(B) 同步但每步独立请求（保持现有 upload + /process 两步） | 需求明确要求同步链路 + 一次请求返回最终结果。异步模式在 v2 考虑。保持两步操作则未解决痛点 | 大文件可能 60-90s 才返回，HTTP 超时风险需配置 `spring.mvc.async.request-timeout` 或改用 SSE 推送进度（v2） |
| **D4** | **状态机扩展**：`FileStatus` 扩展为 8 状态 `UPLOADED → PARSING → PARSED → EXTRACTING → EXTRACTED → FUSING → COMPLETED`，`*ING` 失败回退到上一步 `*ED` + 记录 `failReason`；`FAILED` 保留给不可恢复错误 | (A) 5 状态（最小改动）；(B) 每步仅有 ING 无 ED（7 状态） | 需要 ED 状态来表示"该步骤成功完成，可从此继续"，否则失败后无法区分"没做"还是"正在做"。AC-5 要求保留已完成步骤产物，必须有 ED 状态 | 状态数增加 3 倍，状态转换矩阵从 6 条规则变为 ~20 条。需在 `FileStatus.validateTransition()` 中严格校验 |
| **D5** | **TXT 解析**：`TxtFileParser` 实现 `DocumentParser`，编码检测 UTF-8 → GBK 回退（复用 `CsvGradeParser.tryDecode()` 同款策略）。`parse(byte[])` 将字节按编码转为字符串，返回 `ParseResult(textContent, 1, emptyMap)` | (A) 用 Apache Tika 自动检测文件类型和编码；(B) 直接用 `new String(bytes, UTF-8)` 不做编码检测 | Tika 引入新依赖且过度（只需要文本读取）。CsvGradeParser 已有成熟的 UTF-8→GBK 回退实现，TxtFileParser 直接复用同款策略，代码风格一致 | 编码检测不如 Tika 全面（如 ISO-2022-JP 等罕见编码不支持），但目标用户群（中文教育场景）只涉及 UTF-8/GBK |
| **D6** | **Controller 拆分 + 统一上传入口**：`FileController`（`/api/v1/file/document`）保留 `POST /upload` 作为**所有文件类型统一上传入口**，通过 `FileParserRegistry` 查找扩展名 → 获取 `FileParseType` → 路由到对应 Pipeline（DOCUMENT → `DocumentProcessingPipeline`，CSV_GRADE → `GradeProcessingPipeline`）。`GradeController`（`/api/v1/file/grade`）仅负责成绩查询/删除（GET 列表、GET /exam/{examNo}、DELETE /exam/{examNo}），**不提供上传端点** | (A) 保留统一入口兼容旧调用；(B) 拆为两个 controller 各自独立，路径简化 | 需求选 B。前端同步更新端点路径。`/file/document` → `/document` 去掉了冗余的 `/file` 前缀 | 旧 CSV 上传调用方（如有脚本/测试）需更新 URL。前端同步调整 |
| **D7** | **条件查询实现**：`FileRepository` 新增 `findByConditions(fileType, nameLike, pageable)` 方法，使用 JPQL `WHERE ... AND (:fileType IS NULL OR d.fileType = :fileType) AND (:name IS NULL OR d.name LIKE %:name%)` 动态条件 | (A) Spring Data JPA Specification + Criteria API；(B) 多条 `findByXxx` 派生查询方法组合 | Specification 代码冗长且不易读。JPQL 动态条件用 `IS NULL OR` 模式简洁。本项目已有使用 `@Query` 的先例（Hibernate Boolean bug workaround） | 无法在编译期检查 JPQL 正确性，但可在集成测试中覆盖。不能做多条件 AND/OR 组合（AC 明确不在 v1 范围） |
| **D8** | **`file_type` 字段**：`file` 表新增 `file_type VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT'`。`FileDO` 映射为 `@Enumerated(STRING) FileParseType fileType`。存量数据通过 DEFAULT 值自动为 `DOCUMENT`。同时修正 `FileDO` 的 `@Table(name = "document")` → `@Table(name = "file")`（与 DB 实际表名一致） | (A) 允许 NULL，代码中 null → 默认 DOCUMENT；(B) 用 `file_type` 关联一张 `file_type_config` 表 | NOT NULL + DEFAULT 保证了 DDL 执行时存量自动填充，无需脚本。单独配置表过度设计。@Table 修正是既有 bug——上次重构改了 DB 表名但漏了 JPA 注解 | DDL DEFAULT 'DOCUMENT' 匹配重命名后的枚举值 DOCUMENT；存量数据都是 PDF 正确回填 |
| **D9** | **FileParserRegistry 增强**：将内部存储从 `Map<String, FileParser>` 改为 `Map<String, List<FileParser>>`。新增方法 `List<FileParser> getParsers(String filename)` 返回按优先级排序的解析器列表。PDF 扩展名返回 `[MinerUDocumentParser(priority=0), PdfBoxDocumentParser(priority=1)]`，TXT 返回 `[TxtFileParser]`。`getParser()` 保持兼容返回第一个 | (A) 引入 `@Priority` 注解 + `Ordered` 接口排序；(B) 在 `FileParser` 接口上新增 `int priority()` 方法 | 选 B 更显式——优先级是解析器的固有属性，不应依赖外部注解。`DocumentParser` 的 default 方法设为 0（最高），CsvGradeParser 默认 0 | `FileParser` 接口新增方法，所有实现类都需要实现（或通过 default 方法）。但 4 个实现类改动都很小 |
| **D10** | **FileService 接口清理**：从 `FileService` 接口中移除 `uploadGradeCsv(MultipartFile, String)`、`deleteGradeByExamNo(String)`、`queryGradeByExam(String)` 三个方法。`FileServiceImpl` 同步移除这三个纯委托方法。`GradeController` 直接注入 `GradeService` | (A) 保留在 FileService 中作为快捷方式；(B) 移除，各 Controller 直接注入各自 Service | 这三个方法体是单行委托（`return gradeService.xxx(...)`），放在 FileService 中违反接口隔离原则。移除后 FileService 回归纯文档职责 | GradeController 需要额外注入 GradeService（但本来就该如此），FileServiceImpl 减少 3 个方法和 1 个依赖 |
| **D11** | **MIME 校验下放**：`FileServiceImpl.upload()` 中移除 `"application/pdf"` 硬编码 MIME 白名单。MIME 校验下放到各 `FileParser` 实现中（`supportedExtensions()` 已隐含类型约束）。`DocumentProcessingPipeline` 通过 `FileParserRegistry` 获取解析器时即完成了类型校验——找不到解析器则拒绝 | (A) 在 Pipeline 层统一维护 MIME 白名单 Map；(B) 完全依赖扩展名判断，不校验 MIME | 选 B 最简单——扩展名已经能区分类型，MIME 是冗余校验。当前实现中 `upload()` 的 MIME 检查实际只拒绝非 PDF，而 CSV 通过 Controller 分支绕过了这个检查——说明 MIME 检查本来就不一致 | 恶意修改扩展名的文件会走到错误的解析器（如把 .txt 改成 .pdf），解析失败时会被正确标记为 FAILED。风险可接受 |

---

## 2. 数据流 / 架构图

### 2.1 文档上传同步链路（Document Pipeline）

```
Client                    FileController         DocumentProcessingPipeline      FileParserRegistry    MinerU/PDFBox    LLMGateway    FusionService
  │                            │                          │                           │                   │              │             │
  │  POST /api/v1/file/document│                          │                           │                   │              │             │
  │  /upload (file + subject)  │                          │                           │                   │              │             │
  │───────────────────────────>│                          │                           │                   │              │             │
  │                            │  process(file, subject)  │                           │                   │              │             │
  │                            │─────────────────────────>│                           │                   │              │             │
  │                            │                          │                           │                   │              │             │
  │                            │                          │ ① validate + MD5 dedup    │                   │              │             │
  │                            │                          │ ② MinIO upload            │                   │              │             │
  │                            │                          │ ③ DB insert: UPLOADED     │                   │              │             │
  │                            │                          │                           │                   │              │             │
  │                            │                          │ ④ getParser(filename)     │                   │              │             │
  │                            │                          │──────────────────────────>│                   │              │             │
  │                            │                          │    return FileParser      │                   │              │             │
  │                            │                          │<──────────────────────────│                   │              │             │
  │                            │                          │                           │                   │              │             │
  │                            │                          │ ⑤ DB update: PARSING      │                   │              │             │
  │                            │                          │ ⑥ parser.parse(bytes)     │                   │              │             │
  │                            │                          │───────────────────────────┼──────────────────>│              │             │
  │                            │                          │    ParseResult            │    (MinerU 1st,   │              │             │
  │                            │                          │<───────────────────────────┼──── PDFBox fallback)             │             │
  │                            │                          │ ⑦ DB update: PARSED       │                   │              │             │
  │                            │                          │    (textContent+pageCount) │                   │              │             │
  │                            │                          │                           │                   │              │             │
  │                            │                          │ ⑧ DB update: EXTRACTING    │                   │              │             │
  │                            │                          │ ⑨ LLM extraction          │                   │              │             │
  │                            │                          │──────────────────────────────────────────────────────────────>│             │
  │                            │                          │    entities + kps + edges  │                   │              │             │
  │                            │                          │<──────────────────────────────────────────────────────────────│             │
  │                            │                          │ ⑩ Neo4j write subgraph     │                   │              │             │
  │                            │                          │ ⑪ DB update: EXTRACTED     │                   │              │             │
  │                            │                          │                           │                   │              │             │
  │                            │                          │ ⑫ DB update: FUSING        │                   │              │             │
  │                            │                          │ ⑬ fusionService.fuseIncremental(kps, subject)                │             │
  │                            │                          │──────────────────────────────────────────────────────────────────────────>│
  │                            │                          │    fusion done             │                   │              │             │
  │                            │                          │<──────────────────────────────────────────────────────────────────────────│
  │                            │                          │ ⑭ DB update: COMPLETED     │                   │              │             │
  │                            │                          │ ⑮ publish GraphChangedEvent│                   │              │             │
  │                            │                          │                           │                   │              │             │
  │                            │  FileVO (status=COMPLETED)                           │                   │              │             │
  │                            │<─────────────────────────│                           │                   │              │             │
  │  HTTP 200 + FileVO         │                          │                           │                   │              │             │
  │<───────────────────────────│                          │                           │                   │              │             │
```

### 2.2 手动重试链路（从 PARSED + failReason 继续）

```
Client                    FileController         DocumentProcessingPipeline
  │                            │                          │
  │  POST /api/v1/file/document│                          │
  │  /{id}/process             │                          │
  │───────────────────────────>│  retry(id)               │
  │                            │─────────────────────────>│
  │                            │                          │ ① load FileDO by id
  │                            │                          │ ② check status = PARSED, failReason like '%EXTRACTING%'
  │                            │                          │ ③ skip parse (already PARSED), start from EXTRACTING
  │                            │                          │ ④ EXTRACTING → EXTRACTED → FUSING → COMPLETED
  │                            │  ParseResultVO           │
  │                            │<─────────────────────────│
  │  HTTP 200                  │                          │
  │<───────────────────────────│                          │
```

### 2.3 CSV 成绩上传链路（统一入口 → Grade Pipeline）

```
Client                    FileController          GradeProcessingPipeline       GradeService (既有)
  │                            │                          │                        │
  │  POST /api/v1/file/document│                          │                        │
  │  /upload (CSV + subject)   │                          │                        │
  │───────────────────────────>│                          │                        │
  │                            │ ① FileParserRegistry     │                        │
  │                            │    .getParser("xxx.csv") │                        │
  │                            │    → CSV_GRADE           │                        │
  │                            │ ② route to               │                        │
  │                            │   GradeProcessingPipeline │                        │
  │                            │─────────────────────────>│                        │
  │                            │                          │  uploadGradeCsv(file,  │
  │                            │                          │    subject)            │
  │                            │                          │───────────────────────>│
  │                            │                          │                        │── CSV parse
  │                            │                          │                        │── MinIO upload
  │                            │                          │                        │── MySQL batch insert
  │                            │                          │                        │── Neo4j graph write
  │                            │                          │                        │── publish GradeUploadedEvent
  │                            │                          │  GradeUploadResultBO    │
  │                            │                          │<───────────────────────│
  │                            │  GradeUploadResultVO     │                        │
  │                            │<─────────────────────────│                        │
  │  HTTP 200                  │                          │                        │
  │<───────────────────────────│                          │                        │
```

---

## 3. 关键状态机

### 3.1 文档处理状态机 v2

```
                    ┌──────────┐
                    │ UPLOADED │  ← 上传完成，等待处理
                    └────┬─────┘
                         │
                    ┌────▼─────┐
                    │ PARSING  │  ← 解析进行中（MinerU/PDFBox/TxtFileParser）
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
         (成功)     (可恢复失败)  (不可恢复)
              │          │          │
         ┌────▼────┐     │     ┌────▼────┐
         │ PARSED  │     │     │ FAILED   │  ← 文件损坏/格式不支持
         └────┬────┘     │     └──────────┘
              │          │
              │     ┌────▼────┐
              │     │ UPLOADED │  ← 回退（failReason="parse failed: ..."）
              │     └──────────┘
              │
         ┌────▼─────┐
         │EXTRACTING│  ← LLM 知识抽取进行中
         └────┬─────┘
              │
         ┌────┼────┐
         │    │    │
    (成功)  (失败) (不可恢复)
         │    │    │
    ┌────▼──┐│ ┌──▼─────┐
    │EXTRACTED│ │ PARSED  │  ← 回退（failReason="extraction failed: ..."）
    └────┬──┘│ └────────┘
         │   │
         │   └── textContent 保留，Neo4j 不残留部分数据
         │
    ┌────▼─────┐
    │  FUSING  │  ← 融合进行中（增量 KP 融合 + MASTERS 重算）
    └────┬─────┘
         │
    ┌────┼────┐
    │    │    │
  (成功)(失败)(不可恢复)
    │    │    │
┌───▼──┐│ ┌──▼───────┐
│COMPLETED│ EXTRACTED │ ← 回退（failReason="fusion failed: ..."）
└───────┘│ └──────────┘
         │
         └── Neo4j 抽取结果保留，融合未执行
```

### 3.2 状态转换规则表

| 当前状态 | 允许的目标状态 | 触发条件 |
|---------|--------------|---------|
| `UPLOADED` | `PARSING` | 开始同步链路 / 手动 process |
| `UPLOADED` | `DELETING` | 用户删除 |
| `PARSING` | `PARSED` | 解析成功 |
| `PARSING` | `UPLOADED` + failReason | 解析可恢复失败（MinerU 超时/网络错误） |
| `PARSING` | `FAILED` + failReason | 解析不可恢复失败（文件损坏/格式不支持） |
| `PARSED` | `EXTRACTING` | 自动继续同步链路 / 手动 retry |
| `PARSED` | `DELETING` | 用户删除 |
| `EXTRACTING` | `EXTRACTED` | LLM 抽取成功 |
| `EXTRACTING` | `PARSED` + failReason | 抽取可恢复失败（LLM 超时/限流） |
| `EXTRACTING` | `FAILED` + failReason | 抽取不可恢复失败（文本内容不足以抽取） |
| `EXTRACTED` | `FUSING` | 自动继续同步链路 / 手动 retry |
| `EXTRACTED` | `DELETING` | 用户删除 |
| `FUSING` | `COMPLETED` | 融合成功 |
| `FUSING` | `EXTRACTED` + failReason | 融合可恢复失败（Neo4j 暂时不可用） |
| `FUSING` | `FAILED` + failReason | 融合不可恢复失败（数据不一致无法自动修复） |
| `COMPLETED` | `PARSING` | 手动 re-parse（重新处理） |
| `COMPLETED` | `EXTRACTING` | 手动 re-extract（解析结果满意但需重抽取） |
| `COMPLETED` | `DELETING` | 用户删除 |
| `FAILED` | `PARSING` | 手动 retry from scratch |
| `FAILED` | `DELETING` | 用户删除 |
| `DELETING` | (terminal) | 删除完成后 MySQL 物理删除 / is_deleted=1 |

### 3.3 retry() 断点续跑逻辑

```
retry(documentId):
  doc = repository.findById(id)
  switch (doc.status):
    UPLOADED    → run full chain: parse → extract → fuse
    PARSED      → skip parse, run: extract → fuse
    EXTRACTED   → skip parse+extract, run: fuse only
    FAILED      → run full chain from scratch (clear failReason)
    COMPLETED   → run full chain (re-process)
    PARSING/EXTRACTING/FUSING → error "document is currently processing"
    DELETING    → error "document is being deleted"
```

---

## 4. ADR 索引

凡可逆性低的决策，单独写 ADR 文件：

| ADR | 文件 | 决策 |
|-----|------|------|
| ADR-001 | `.specs/adr/001-file-pipeline-abstraction.md` | D1: FileProcessingPipeline 接口设计 |
| ADR-002 | `.specs/adr/002-document-state-machine-v2.md` | D4: 8 状态文档状态机 |
| ADR-003 | `.specs/adr/003-controller-split.md` | D6: FileController + GradeController 拆分 |
| ADR-004 | `.specs/adr/004-parser-registry-multi-parser.md` | D9: FileParserRegistry 多解析器 per extension |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| **R1** | **同步链路超时**：大 PDF（~50MB）MinerU 轮询 30s + LLM 抽取 30s + 融合 10s ≈ 70s 可能触发 HTTP/网关超时 | 用户上传大文件收到 504，体验差 | 中 | ① 配置 `spring.mvc.async.request-timeout=120s`；② `application-dev.yml` 设置 `mineru.api.poll-timeout=300s` 已预留；③ v2 引入异步模式（上传立即返回 UPLOADED，SSE 推送进度） |
| **R2** | **TXT 抽取质量不达预期**：纯文本缺少 PDF 的版面结构（标题层级、公式 LaTeX 标注），LLM 抽取出的 KnowledgePoint 数量可能显著少于 PDF | 用户上传 TXT 后期望与 PDF 同等的抽取效果 | 高 | ① REQUIREMENT 已设预期"不低于 PDF 70%"不作为缺陷；② 可在 prompt 模板中增强对纯文本的指令（提示 LLM 注意段落边界和关键词）；③ 未来可引入 TXT 预处理（按空行分段、识别标题模式） |
| **R3** | **状态机并发冲突**：同步链路执行中用户同时调 `/process` 或 `/delete`，可能导致状态不一致 | 文档卡在中间状态或数据损坏 | 低 | ① `FileServiceImpl` 已有 `@Transactional`，DB 行级锁保护状态更新；② `*ING` 状态时 `retry()` 直接拒绝（见 §3.3）；③ `DELETING` 状态同理拒绝处理 |
| **R4** | **存量数据 `file_type` 正确性**：历史 `document` 表所有记录默认回填 `PDF`，但若有非 PDF 文件（理论上不存在）会被错误标记 | 查询 `file_type=TXT` 时漏掉本该是 TXT 的历史记录 | 极低 | DEFAULT 'PDF' 在 DDL 中设置，v1 无历史 TXT 数据，风险可控。若未来上传非 PDF 文件，`file_type` 由 Pipeline 主动写入保证正确 |
| **R5** | **Controller 拆分导致前端/脚本断裂**：CSV 上传从 `/api/v1/file/document/upload` 迁移到 `/api/v1/grade/upload`，旧调用方未同步更新 | CSV 上传 404，成绩数据中断 | 中 | ① 前端同步更新（需求假设前端可同步调整）；② 如有外部脚本/CI，在 INTEGRATION 阶段回归 CSV 上传测试；③ 可在 v1 短期保留旧端点的兼容转发（返回 301 + 新 URL，提醒调用方迁移），v2 移除 |
| **R6** | **LLM 抽取覆盖旧图谱数据**：同步链路每次重新抽取会先删旧子图再写新子图（既有的"全量覆盖"策略），若融合未执行即失败，图谱中残留不完整数据 | 用户查询图谱时看到不完整/不一致的知识点 | 中 | ① EXTRACTING 失败时回退到 PARSED，已经写入 Neo4j 的数据在 `GraphNodeRepository.deleteByDocumentId()` 后是空白的（抽取前先清理）；② 抽取成功但融合失败 → 图谱有抽取结果但 MASTERS 未更新，状态 EXTRACTED + failReason 明确指示 |
| **R7** | **长期债务：Pipeline 难测试**：`DocumentProcessingPipeline.process()` 是一个长方法，依赖多个外部服务（MinIO/MinerU/LLM/Neo4j），单元测试困难 | 重构或修 bug 时回归成本高 | 中 | ① 每个子步骤（parse/extract/fuse）已有独立 Service，可单独单元测试；② Pipeline 本身的集成测试用 `@SpringBootTest` + `@ActiveProfiles("dev")` 直连 podman 真实组件（既有模式）；③ 未来可引入步骤间状态持久化（如 pipeline_execution 表），使 Pipeline 成为可恢复的状态机 |
| **R8** | **@Table 注解不一致**：`FileDO.@Table(name = "document")` 但 DB 实际表名为 `file`（上次重构改了 DB 表名但漏了 JPA 注解）。若某处代码使用 JPA 原生查询引用 `document` 表名，与实际 DB 不一致 | 查询报错 "Table 'graphnexus.document' doesn't exist" | 低 | 本次 T02 一并修正 `@Table(name = "file")`，与 DB 实际表名一致。grep 全项目确认无硬编码 `document` 表名的 JPQL/SQL |

---

## 6. 不在范围

- 异步处理模式（v2）—— 本次仅同步链路
- `.docx` / `.xlsx` 文件类型支持（v2）
- 全文检索（Elasticsearch / MySQL FULLTEXT INDEX）（v2）
- 处理进度百分比实时推送（SSE/WebSocket）（v2）
- 批量上传（一次多文件）（v2）
- 手动融合触发 UI 变更 —— 仅保证后端端点保留
- 现有前端 SPA 的同步更新 —— 前端改动属于 `frontend-ui` change，不在此 change 范围
- `pom.xml` 依赖变更 —— 禁动清单，本次不引入任何新依赖

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径（预留） | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/file/core/pipeline/FileProcessingPipeline.java` | 文件处理全链路 Pipeline 抽象（process + retry） | 新增文件类型/处理链路时实现此接口 | 类似 `KpMatchingStrategy`、`SubgraphPruningStrategy` 的策略模式，保持与项目既有扩展范式一致 |
| `application/file/parse/parser/TxtFileParser.java` | TXT 文本解析（编码检测 + 字节→文本） | 未来支持 `.md`、`.log` 等纯文本格式时可复用 | 编码检测逻辑可抽为 `EncodingDetector` 工具类供其他解析器复用 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|------|------|---------|---------|
| Pipeline 编排模式 | `FileProcessingPipeline` 接口 + 同步串联执行 | 所有文件类型的处理链路 | 改为异步需改动 Pipeline 接口（process 返回 Future/ListenableFuture）+ 引入消息队列，约 3-5 天 |
| 文档状态机 v2 | 8 状态模型（UPLOADED→...→COMPLETED），`*ING` 失败回退 `*ED` | `document` 表 status 字段 + `FileStatus` 枚举 + 所有读写 document 状态的代码 | 涉及 DB 字段约束和状态转换逻辑，推翻需重写 validateTransition() + 数据迁移，约 2-3 天 |

### 9.3 新增 / 修改的跨模块契约

```
- API 端点变更：
  - FileController: POST /api/v1/file/document/upload (统一上传入口，PDF/TXT/CSV 自动路由), GET /api/v1/file/document (列表+筛选), GET/PUT/DELETE /api/v1/file/document/{id}, POST /api/v1/file/document/{id}/process
  - GradeController: GET /api/v1/file/grade, GET /api/v1/file/grade/exam/{examNo}, DELETE /api/v1/file/grade/exam/{examNo} — 仅查询/删除，不上传
  - 废弃: FileController 中原有的 GET/DELETE /grade/exam/{examNo} → 迁入 GradeController
- 数据库 schema：
  - document 表: 新增 file_type VARCHAR(20) NOT NULL DEFAULT 'PDF'
  - document.status: 扩展枚举值（PARSING/PARSED/EXTRACTING/EXTRACTED/FUSING）
  - exam_record 表: 无变更
- 事件：
  - GradeUploadedEvent 发布点不变（仍在 GradeServiceImpl 中发布）
  - GraphChangedEvent 发布点新增：DocumentProcessingPipeline 融合完成后发布
```

### 9.4 新增 / 升级的依赖

本 change 不引入任何新依赖。所有功能基于既有栈实现。

### 9.5 禁动清单变化

```
- 新增禁动: application/file/core/pipeline/FileProcessingPipeline.java — 接口签名变更需走新 CHANGE
- 新增禁动: infrastructure/mysql/file/FileStatus.java — 状态枚举和转换规则变更需走新 CHANGE（影响所有依赖方）
- 不变: pom.xml（已有禁动）
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。