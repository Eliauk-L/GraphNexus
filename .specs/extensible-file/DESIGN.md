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
触碰模块（基于实际 grep 结果）：
- api/file/controller/FileController.java（既有 · 拆分为 FileController + GradeController）
- api/file/dto/core/FileVO.java（既有 · 新增 fileType 字段）
- api/file/dto/upload/GradeUploadResultVO.java（既有 · 迁入 GradeController）
- application/file/core/service/FileService.java + impl（既有 · 重构为 Pipeline 模式）
- application/file/upload/service/GradeService.java + impl（既有 · 迁入 GradePipeline）
- application/file/parse/parser/FileParser.java（既有 · 保持接口不变）
- application/file/parse/parser/DocumentParser.java（既有 · 让 TxtFileParser 实现）
- application/file/parse/parser/FileParserRegistry.java（既有 · 扩展注册）
- application/file/parse/model/FileParseType.java（既有 · 新增 TXT 枚举值）
- infrastructure/mysql/file/FileDO.java（既有 · 新增 fileType 字段）
- infrastructure/mysql/file/FileStatus.java（既有 · 扩展 8 状态）
- infrastructure/mysql/file/FileRepository.java（既有 · 新增条件查询方法）
- application/graph/fusion/event/GradeUploadedEventListener.java（既有 · 无变更）
- application/graph/metrics/event/GraphChangedEvent.java（既有 · 无变更）

新增模块：
- application/file/core/pipeline/FileProcessingPipeline.java（新接口）
- application/file/core/pipeline/DocumentProcessingPipeline.java（新实现）
- application/file/core/pipeline/GradeProcessingPipeline.java（新实现）
- application/file/parse/parser/TxtFileParser.java（新解析器）
- api/file/controller/GradeController.java（新 Controller）
- api/file/dto/core/FileQueryRequest.java（新查询 DTO）

禁动清单（与本次无关，AI 不许碰）：
- application/graph/fusion/service/FusionService.java（只调用，不改逻辑）
- application/graph/construction/（图谱构建逻辑不变）
- application/llmgateway/（LLM 调用不变）
- infrastructure/neo4j/（Neo4j 持久化层不变）
- infrastructure/storage/（MinIO 适配器不变）
- pom.xml（禁动清单中的依赖管理文件）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|----------|-----------------|------|
| 文件扩展名→解析器路由 | `FileParserRegistry`（按 extension 索引） | **沿用**，扩展 PDF/TXT 注册 |
| PDF 解析 | `DocumentParser` 接口 + `MinerUDocumentParser` + `PdfBoxDocumentParser` | **沿用**，保持 MinerU→PDFBox 兜底链 |
| 解析结果模型 | `ParseResult`（textContent/pageCount/metadata） | **沿用**，TXT 也返回此结构 |
| 文档元数据持久化 | `FileDO` → `document` 表 + `FileRepository` | **沿用**，新增 `fileType` + 扩展 `status` |
| MinIO 文件存储 | `FileStorageService`（upload/download/delete） | **沿用**，路径结构不变 |
| Neo4j 图写入 | `GraphNodeRepository`（Cypher MERGE/MATCH） | **沿用**，不修改 |
| 事件发布 | `ApplicationEventPublisher` + `@EventListener` | **沿用**，`GradeUploadedEvent` / `GraphChangedEvent` 发布点不变 |
| 分页响应 | `PageResult<T>` record | **沿用** |
| 依赖注入 | 构造器注入（`@RequiredArgsConstructor`） | **沿用** |
| 异常处理 | `BusinessException` + `GlobalExceptionHandler` | **沿用** |
| TXT 文本解析 | **没有**（PDFBox 只能处理 PDF，MinerU 也是 PDF 专用） | **新建** `TxtFileParser`（理由：首次支持纯文本文件） |
| 全链路 Pipeline 编排 | **没有**（当前 upload/process 分离，手动触发） | **新建** `FileProcessingPipeline`（理由：同步链路的编排抽象是首次引入） |
| Controller 级别文件类型路由 | **没有**（当前在 `FileController.upload()` 内 if-else） | **新建** `FilePipelineRegistry`（理由：消除 Controller 中的硬编码分支） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：**沿用** Repository 模式（JpaRepository + 显式 @Query JPQL）
- 依赖注入：**沿用** 构造器注入（@RequiredArgsConstructor + private final）
- 错误处理：**沿用** BusinessException + GlobalExceptionHandler
- 事件解耦：**沿用** ApplicationEventPublisher + @EventListener（模块间通知）
- 策略模式：**沿用**（FileParser 接口 + Spring Bean 自动注册，与既有 KpMatchingStrategy/WeightCalculationStrategy/SubgraphPruningStrategy 一致）
- Pipeline 编排：**引入新模式** → 理由：既有架构中没有"串联多步骤 + 状态机驱动 + 断点续跑"的编排抽象。这是一个新的关注点，但遵循 Spring 单机同步执行的既有范式，不引入工作流引擎
- Controller 拆分：**沿用** 既有分层（L1 Controller → L2 Service → L3 Infrastructure），GradeController 与 FileController 各自独立但共享底层 Service
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| **D1** | **Pipeline 抽象**：定义 `FileProcessingPipeline` 接口（`process(MultipartFile, subject)` + `retry(documentId)`），由 `DocumentProcessingPipeline` 和 `GradeProcessingPipeline` 分别实现 | (A) 不引入新接口，在 Service 层加 if-else 分支；(B) 用 Spring StateMachine 做状态机驱动的编排 | 选 A 违反开闭原则正是本次要解决的；选 B 太重，引入新依赖 + 学习曲线。自己写 Pipeline 可以精确控制状态机 + 同步执行 + 保持架构简单 | 需手工管理状态转换和错误恢复，约多写 ~100 行编排代码，但能精确控制行为 |
| **D2** | **Parser 统一**：保留 `FileParser` → `DocumentParser` 继承链不变，新增 `TxtFileParser` 直接实现 `DocumentParser`。`FileParserRegistry` 同时注册 PDF 和 TXT 解析器 | (A) 合并 FileParser 和 DocumentParser 为单一接口；(B) 让 TXT 解析器另起一个独立接口 | 选 A 改动面太大（PdfBox/MinerU/Csv 三个实现类都得改）；选 B 又走回两套接口的老路。保留继承链 + 新增 TXT 实现改动最小 | `DocumentParser.parse(byte[])` 方法名暗示 PDF，对 TXT 略语义不匹配，但接口契约（byte[] → ParseResult）完全通用 |
| **D3** | **同步链路编排**：`DocumentProcessingPipeline.process()` 内联调用 解析 → 抽取 → 融合，状态机在 Pipeline 内部管理。HTTP 请求等待全链路完成再返回 | (A) 异步模式（上传立即返回 UPLOADED，后台队列处理）；(B) 同步但每步独立请求（保持现有 upload + /process 两步） | 需求明确要求同步链路 + 一次请求返回最终结果。异步模式在 v2 考虑。保持两步操作则未解决痛点 | 大文件可能 60-90s 才返回，HTTP 超时风险需配置 `spring.mvc.async.request-timeout` 或改用 SSE 推送进度（v2） |
| **D4** | **状态机扩展**：`FileStatus` 扩展为 8 状态 `UPLOADED → PARSING → PARSED → EXTRACTING → EXTRACTED → FUSING → COMPLETED`，`*ING` 失败回退到上一步 `*ED` + 记录 `failReason`；`FAILED` 保留给不可恢复错误 | (A) 5 状态（最小改动）；(B) 每步仅有 ING 无 ED（7 状态） | 需要 ED 状态来表示"该步骤成功完成，可从此继续"，否则失败后无法区分"没做"还是"正在做"。AC-5 要求保留已完成步骤产物，必须有 ED 状态 | 状态数增加 3 倍，状态转换矩阵从 6 条规则变为 ~20 条。需在 `FileStatus.validateTransition()` 中严格校验 |
| **D5** | **TXT 解析**：`TxtFileParser` 实现 `DocumentParser`，编码检测 UTF-8 → GBK 回退。`parse(byte[])` 将字节按编码转为字符串，返回 `ParseResult(textContent, 1, emptyMap)` | (A) 用 Apache Tika 自动检测文件类型和编码；(B) 直接用 `new String(bytes, UTF-8)` 不做编码检测 | Tika 引入新依赖且过度（只需要文本读取）。手工编码检测用 `juniversalchardet` 或简单 BOM 判断即可。TXT 文件通常 UTF-8，GBK 是少数情况 | 编码检测不如 Tika 全面（如 ISO-2022-JP 等罕见编码不支持），但目标用户群（中文教育场景）只涉及 UTF-8/GBK |
| **D6** | **Controller 拆分**：`FileController`（`/api/v1/document`）处理文档文件 + `GradeController`（`/api/v1/grade`）处理成绩文件。CSV 上传从原 `POST /api/v1/file/document/upload` 迁移到 `POST /api/v1/grade/upload` | (A) 保留统一入口 `POST /api/v1/document/upload` 兼容旧调用；(B) 拆为两个 controller 各自独立 | 需求选 B。前端同步更新端点路径。旧路径不再兼容 CSV，CSV 调用方需更新 | 旧 CSV 上传调用方（如有脚本/测试）需更新 URL。前端同步调整 |
| **D7** | **条件查询实现**：`FileRepository` 新增 `findByConditions(fileType, nameLike, pageable)` 方法，使用 JPQL `WHERE ... AND (:fileType IS NULL OR d.fileType = :fileType) AND (:name IS NULL OR d.name LIKE %:name%)` 动态条件 | (A) Spring Data JPA Specification + Criteria API；(B) 多条 `findByXxx` 派生查询方法组合 | Specification 代码冗长且不易读。JPQL 动态条件用 `IS NULL OR` 模式简洁，两条可选参数即可覆盖。本项目已有使用 `@Query` 的先例（Hibernate Boolean bug workaround） | 无法在编译期检查 JPQL 正确性，但可在集成测试中覆盖。不能做多条件 AND/OR 组合（AC 明确不在 v1 范围） |
| **D8** | **`file_type` 字段**：`document` 表新增 `file_type VARCHAR(20) NOT NULL DEFAULT 'PDF'`。`FileDO` 映射为 `@Enumerated(STRING) FileParseType fileType`。存量数据通过 DEFAULT 值自动为 `PDF` | (A) 允许 NULL，代码中 null → 默认 PDF；(B) 用 `file_type` 关联一张 `file_type_config` 表 | NOT NULL + DEFAULT 保证了 DDL 执行时存量自动填充，无需脚本。单独配置表过度设计（v1 仅 3 种类型） | `FileParseType` 枚举新增 TXT 值后，旧代码中 `switch` 若无 default 分支可能编译警告 |

---

## 2. 数据流 / 架构图

### 2.1 文档上传同步链路（Document Pipeline）

```
Client                    FileController         DocumentProcessingPipeline      FileParserRegistry    MinerU/PDFBox    LLMGateway    FusionService
  │                            │                          │                           │                   │              │             │
  │  POST /api/v1/document     │                          │                           │                   │              │             │
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
  │  POST /api/v1/document     │                          │
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

### 2.3 CSV 成绩上传链路（Grade Pipeline）

```
Client                    GradeController         GradeProcessingPipeline       GradeService (既有)
  │                            │                          │                        │
  │  POST /api/v1/grade        │                          │                        │
  │  /upload (file + subject)  │                          │                        │
  │───────────────────────────>│  process(file, subject)  │                        │
  │                            │─────────────────────────>│                        │
  │                            │                          │  uploadGradeCsv(file,  │
  │                            │                          │    subject)            │
  │                            │                          │───────────────────────>│
  │                            │                          │                        │── CSV parse (FileParserRegistry)
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
  - FileController: POST/GET /api/v1/document/* (文档 CRUD + 上传 + 解析触发) — 复用原 FileController 路径
  - GradeController: POST /api/v1/grade/upload, GET /api/v1/grade, GET /api/v1/grade/exam/{examNo}, DELETE /api/v1/grade/exam/{examNo} — 新端点
  - 废弃: POST /api/v1/file/document/upload 不再接受 CSV 文件（CSV 必须走 /api/v1/grade/upload）
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