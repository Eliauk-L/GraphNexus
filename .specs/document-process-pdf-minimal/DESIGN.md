# DESIGN: 文档处理模块 — PDF 上传/解析/管理最小化实现

- **Change ID**: `document-process-pdf-minimal`
- **关联**: `@.specs/document-process-pdf-minimal/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 CONTEXT.md「已锁技术决策」锁定，不重选。

| 项 | 取值 |
|:--|:--|
| **语言/框架** | Java 17 + Spring Boot 3.3.5 |
| **文件存储** | MinIO 8.x（`minio` Java Client） |
| **PDF 解析** | Apache PDFBox 3.x |
| **持久化** | MySQL 8.0 + Spring Data JPA + Hibernate 6.4+ |
| **注入方式** | 构造器注入（`@RequiredArgsConstructor`） |
| **对象映射** | MapStruct 1.5.x |
| **测试** | JUnit 5 + Mockito + Testcontainers（MinIO + MySQL） |
| **理由** | 全栈已锁，`pom.xml` 已含全部依赖，无需新增 |
| **明确排除** | 不引入 Tika（过度工程）；不引入 Spring Content（PDF 上传场景简单，MinioClient 直接调用即可） |

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（需修改的既有文件）：
- src/main/resources/application.yml                  （需取消 MySQL/JPA/Transaction AutoConfig exclude）
- src/main/resources/application-dev.yml              （需新增 multipart 上传大小配置）
- src/main/java/com/graphnexus/GraphNexusApplication.java  （需从 exclude 中移除 DataSource/JPA/Transaction）
- src/main/java/com/graphnexus/common/exception/ErrorCode.java  （需新增 3 个文档相关错误码）

新增模块：
- src/main/java/com/graphnexus/infrastructure/storage/   （MinIO 配置类 + 文件服务）
- src/main/java/com/graphnexus/infrastructure/mysql/     （DocumentDO + DocumentRepository）
- src/main/java/com/graphnexus/application/document/    （DocumentParser 接口 + PdfBoxDocumentParser + DocumentService）
- src/main/java/com/graphnexus/api/document/            （DocumentController + VO/DTO）
- src/test/java/com/graphnexus/application/document/    （DocumentParser 单元测试 + DocumentService 集成测试）

禁动清单（与本次无关，AI 不许碰）：
- pom.xml                                               （禁动清单已登记）
- docs/项目规范.md                                      （禁动清单已登记）
- docs/tech-stack-java.md                               （禁动清单已登记）
- src/main/java/com/graphnexus/common/**                （除 ErrorCode 新增枚举值外，不修改既有类逻辑）
- src/main/java/com/graphnexus/api/analysis/**          （与文档处理无关）
- src/main/java/com/graphnexus/api/graph/**             （与文档处理无关）
- src/main/java/com/graphnexus/api/query/**             （与文档处理无关）
- src/main/java/com/graphnexus/infrastructure/neo4j/**  （本次不涉及）
- src/main/java/com/graphnexus/infrastructure/redis/**  （本次不涉及）
- src/main/java/com/graphnexus/infrastructure/mq/**     （本次不涉及）
- src/main/java/com/graphnexus/infrastructure/llm/**    （本次不涉及）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|:--|:--|:--|
| 统一异常抛收 | `common/exception/BusinessException.java` | **沿用**，新增 `ErrorCode` 枚举值即可 |
| 统一 API 响应 | `common/ApiResponse.java` | **沿用**，Controller 返回 `ApiResponse<DocumentVO>` |
| 统一分页 | `common/PageResult.java` | **沿用**，分页查询返回 `ApiResponse<PageResult<DocumentVO>>` |
| 全链路 Trace ID | `common/logging/TraceIdFilter.java` | **沿用**，无需额外配置 |
| 全局异常处理 | `common/exception/GlobalExceptionHandler.java` | **沿用**，无需改动 |
| 应用启动类 | `GraphNexusApplication.java` | **沿用**，需移除 MySQL/JPA/Transaction exclude |
| MinIO 客户端 | 没有 | **新建**（第一次连接 MinIO） |
| PDF 解析器 | 没有 | **新建**（第一次引入 PDFBox） |
| JPA Repository | 没有（目录和 pom 已就绪，但无实现类） | **新建**（第一次落 JPA Entity + Repository） |
| MySQL 表结构 | 没有 | **新建**（第一次落 DDL） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：       **沿用** Spring Data JPA Repository 模式（项目已选型 JPA + Hibernate）
- 文件存储：       **引入新模式** → MinioClient 直接调用（项目已选型 MinIO 8.x，但无抽象层；本次新建 infrastructure/storage/ 做薄封装）
- 异常处理：       **沿用** throw BusinessException → GlobalExceptionHandler（已落地）
- 响应格式：       **沿用** ApiResponse<T> record + PageResult<T> record（已落地）
- 构造器注入：     **沿用** @RequiredArgsConstructor（CONTEXT 已锁）
- 分层调用：       **沿用** L1 → L2 → L3，L1 不直调 L3（ArchUnit 已校验）
- 解析器可替换：   **引入新模式** → Strategy 模式：DocumentParser 接口 + PdfBoxDocumentParser 实现（首次引入，为 MinerU 预留插槽）
- 状态管理：       **引入新模式** → 数据库字段状态 + Service 层校验状态转换（首次业务状态机）
- API 命名：       **沿用** 小写下划线分隔，单数资源名（`/api/v1/document`）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|:--|:--|:--|:--|:--|
| D1 | **DocumentParser 接口放 L2 application/document/service/** | 放 L3 infrastructure/ 或 common/ | 解析策略选择是**业务决策**非技术细节。未来切换 PDFBox→MinerU 是业务需求驱动，应由 L2 控制。L3 只提供 PDFBox 库的薄封装，接口在 L2 定义，L3 提供具体实现 | PdfBoxDocumentParser 实现类需 import PDFBox API，理论上这是 infra 关注点——但实现类本身就是适配器，放 L2 仅依赖接口，PdfBox 实现类返回 `ParseResult`（POJO），不泄露 L3 依赖 |
| D2 | **MinIO 适配层放 L3 infrastructure/storage/，Service 层放 L2** | 直接在 L2 DocumentService 里调 MinioClient | 分层清晰：L3 封装技术细节（endpoint/accessKey/bucket/分片），L2 编排业务逻辑（上传→校验→存 MinIO→写 MySQL），符合 ArchUnit 校验规则 | L3 storage 层目前只有一个 MinIO 实现，未来换存储需改 L3 代码——但对于单存储场景这种"过度分层"是项目标准的代价 |
| D3 | **DocumentParser 接口签名：`ParseResult parse(byte[] pdfBytes)`** | `ParseResult parse(InputStream is)` / `ParseResult parse(Path filePath)` | `byte[]` 最通用：上传后 MinIO 返回 `InputStream`，Service 将其读入 `byte[]` 传给 parser。后续 MinerU API 接受字节流同样适用。避免流/路径在不同存储后端的兼容问题 | 大文件（50MB）全读入内存有 OOM 风险——缓解：上传大小限制 50MB + JVM 堆 ≥ 512M |
| D4 | **文档状态机用枚举 + Service 校验，不引入 Spring State Machine** | Spring State Machine 框架 / 数据库 CHECK 约束 | 状态只有 5 个（v1 使用 UPLOADED/PROCESSING/COMPLETED/FAILED，DELETING/DELETED 预留）、转换规则简单（单向无分支），引入框架是过度工程。Service 层 `validateStateTransition(from, to)` 方法 + `DocumentStatus` 枚举内嵌转换规则即可 | 如果未来状态变多（6+）且有条件分支，需重构为状态机框架——但当前简单场景足够 |
| D5 | **MySQL DDL 用 Hibernate `ddl-auto: update`（开发期），生产后续 flyway** | 手写 SQL / Flyway 首发 | 项目处于骨架搭建期，表结构会频繁迭代，`update` 可自动同步 JPA Entity → DDL。进入生产前引入 Flyway 做版本迁移 | `update` 不生成迁移脚本，无法回滚——但当前无生产数据，可接受 |
| D6 | **上传解析同步完成（单次请求内 上传→存 MinIO→写 DB→解析→更新状态）** | 异步 MQ 解析 / `@Async` 线程池 | AC 要求解析结果立即可查，且 v1 不引入消息队列异步。PDFBox 解析 50MB PDF ≤ 500ms，在请求超时（默认 30s）内完全可完成 | 大并发上传时请求线程被 PDFBox 阻塞——但 v1 无并发压力；v2 规划异步解决 |
| D7 | **REST API 路径：`/api/v1/document`（单数 resource）** | `/api/v1/documents`（复数） | CONTEXT 已锁"REST API 小写下划线分隔，单数资源名" | 无 |
| D8 | **Document 删除：逻辑删除（is_deleted）+ MinIO 物理删除** | 物理 DELETE / 逻辑删除 + MinIO 保留 | 用户确认方案：数据库保留记录（审计可查），MinIO 释放存储空间（避免冷文件堆积）。与 CONTEXT 默认行为一致 | 误删后无法从 MinIO 恢复文件——v2 可考虑回收站/软保留期 |
| D9 | **新增 ErrorCode 枚举值：A0004（文件类型不支持）、A0005（文件大小超限）、A0006（文档不存在）** | 复用 A0001/A0002 / 新建 B 类错误 | 三个场景均为用户端输入问题（传了非 PDF / 超大文件 / 查了不存在的 ID），归 A（用户端错误）。语义独立便于前端区分提示文案 | ErrorCode 枚举膨胀——但每个枚举值精确映射到 AC 场景，代价可接受 |
| D10 | **`ddl-auto: update` 改为 `validate` + 手动提供 DDL 脚本** | 保留 `update` | 生产可控性：手写 DDL 确保字段类型/索引/字符集完全精确，`update` 只能做"有表就行"的保证。开发期用 `validate` 验证 JPA Entity 与表结构一致，不对齐时启动报错而非静默改表 | 开发体验略降：每次改 Entity 需手动改 DDL——但表结构变更低频，可接受 |

---

## 2. 数据流 / 架构图

### 2.1 整体分层调用链

```
HTTP Request (multipart PDF)
       │
       ▼
┌─────────────────────────────────────────────────────┐
│  L1  api/document/                                   │
│                                                       │
│  DocumentController                                   │
│    POST /api/v1/document/upload                       │
│    POST /api/v1/document/{id}/process                   │
│    GET  /api/v1/document                              │
│    GET  /api/v1/document/{id}                         │
│    PUT  /api/v1/document/{id}                         │
│    DELETE /api/v1/document/{id}                       │
│                                                       │
│    DTO: DocumentVO, ParseResultVO, UpdateRequestDTO   │
│    ─── 调用 L2 ───>                                   │
└──────────────────────────┬──────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────┐
│  L2  application/document/                            │
│                                                       │
│  DocumentService (interface)                          │
│    + upload(MultipartFile, String subject): DocumentBO │
│    + process(Long documentId): ParseResultBO           │
│    + listDocuments(PageQuery): Page<DocumentBO>       │
│    + getDocument(Long id): DocumentBO                 │
│    + updateDocument(Long id, UpdateBO): DocumentBO    │
│    + deleteDocument(Long id): void                    │
│                                                       │
│  DocumentServiceImpl                                  │
│    编排: 校验→存MinIO→写DB→解析→更新状态             │
│                                                       │
│  DocumentParser (interface · 策略模式)                 │
│    + parse(byte[] pdfBytes): ParseResult              │
│                                                       │
│  PdfBoxDocumentParser (implements DocumentParser)     │
│    ─── 调用 L3 ───>                                   │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────┐
│  L3  infrastructure/                                  │
│                                                       │
│  infrastructure/storage/                               │
│    MinioConfig      → MinioClient Bean                │
│    FileStorageService                                 │
│      + uploadFile(InputStream, minioPath, contentType)│
│      + getFile(minioPath): InputStream                │
│      + deleteFile(minioPath): void                    │
│                                                       │
│  infrastructure/mysql/document/                        │
│    DocumentDO (JPA Entity)                            │
│      id, documentNo, name, subject, fileSize,           │
│      minioPath, textContent, pageCount, metadataJson,   │
│      status, failReason, uploadedBy,                    │
│      isDeleted, createTime, updateTime                  │
│                                                       │
│    DocumentRepository (Spring Data JPA)               │
│      extends JpaRepository<DocumentDO, Long>          │
│      + findByIsDeletedFalse(Pageable)                 │
│                                                       │
│         ┌──────────┼──────────┐                       │
│         ▼          ▼          ▼                       │
│      MinIO      MySQL       PDFBox                     │
│  (文件存储)   (元数据)   (文本提取)                      │
└─────────────────────────────────────────────────────┘
```

### 2.2 PDF 上传 → 解析完整数据流

```
Client                     Controller(L1)           DocumentService(L2)
  │                              │                        │
  │  POST /upload (multipart)    │                        │
  │─────────────────────────────>│                        │
  │                              │  upload(file, subject)  │
  │                              │───────────────────────>│
  │                              │                        │
  │                              │   ① 校验 MIME type     │
  │                              │      (application/pdf) │
  │                              │   ② 校验文件大小 ≤ 50M │
  │                              │   ③ 计算 MD5 内容指纹  │
  │                              │      (documentNo)      │
│                              │   ④ 查 uk_document_     │
│                              │      subject 去重        │
│                              │   ⑤ 生成 minioPath       │
  │                              │                        │
  │                              │   ⑥ FileStorageService │
  │                              │     .uploadFile(       │
  │                              │       inputStream,     │
  │                              │       minioPath,       │
  │                              │       "application/pdf"│
  │                              │     )                  │
  │                              │         │              │
  │                              │         ▼              │
  │                              │      MinIO             │
  │                              │      (bucket:          │
  │                              │       graphnexus-dev)  │
  │                              │         │              │
  │                              │   ⑦ save DocumentDO:   │
  │                              │      name,subject,    │
  │                              │      fileSize,minioPath,│
  │                              │      documentNo,       │
  │                              │      status=UPLOADED   │
  │                              │         │              │
  │                              │         ▼              │
  │                              │      MySQL             │
  │                              │         │              │
  │                              │   ⑧ 返回 DocumentBO    │
  │                              │<───────────────────────│
  │                              │                        │
  │  ApiResponse<DocumentVO>     │                        │
  │<─────────────────────────────│                        │
  │                              │                        │
  │  POST /{id}/process            │                        │
  │─────────────────────────────>│                        │
  │                              │  process(id)             │
  │                              │───────────────────────>│
  │                              │                        │
  │                              │   ① 查 DocumentDO      │
  │                              │   ② 校验 status=       │
  │                              │      UPLOADED          │
  │                              │   ③ 更新 status=       │
  │                              │      PROCESSING           │
  │                              │   ④ 从 MinIO 获取文件  │
  │                              │      inputStream       │
  │                              │   ⑤ 读入 byte[]        │
  │                              │   ⑥ parser.parse(      │
  │                              │        pdfBytes)       │
  │                              │         │              │
  │                              │         ▼              │
  │                              │      PDFBox            │
  │                              │      提取 text + pages │
  │                              │      提取 metadata     │
  │                              │         │              │
  │                              │   ⑦ 更新 DO:           │
  │                              │      textContent=...,  │
  │                              │      pageCount=...,    │
  │                              │      metadataJson=..., │
  │                              │      status=COMPLETED     │
  │                              │         │              │
  │                              │   ⑧ 返回 ParseResultBO │
  │                              │<───────────────────────│
  │                              │                        │
  │  ApiResponse<ParseResultVO>  │                        │
  │<─────────────────────────────│                        │
```

### 2.3 模块依赖方向

```
api/document ──▶ application/document ──▶ infrastructure/storage
                    │                           │
                    │                           ├──▶ MinIO
                    │                           │
                    ├──▶ infrastructure/mysql    │
                    │         │                  │
                    │         └──▶ MySQL          │
                    │                            │
                    └──▶ common/exception         │
                          common/ApiResponse      │
                          common/PageResult       │
```

ArchUnit 校验：L1 api.document 只访问 L2 application.document + common ✓；L2 application.document 只访问 L3 infrastructure + common ✓

---

## 3. 关键状态机

### 3.1 Document 状态转换

```
         ┌──────────┐
         │ UPLOADED │  初始状态：上传成功、文件在 MinIO、DB 有记录
         └────┬─────┘
              │
              │ process() 被调用
              ▼
         ┌──────────────┐
         │  PROCESSING   │  解析进行中（短暂状态，同步场景下仅在方法执行期间）
         └────┬─────────┘
              │
          ┌───┴───┐
          ▼       ▼
   ┌───────────┐  ┌────────┐
   │ COMPLETED │  │ FAILED │  终态（可重新 process 回到 PROCESSING）
   └───────────┘  └────────┘
```

**转换规则（`DocumentService.validateStateTransition`）**：

| from | → to | 允许？ | 触发 |
|:--|:--|:--:|:--|
| UPLOADED | PROCESSING | ✅ | `process()` |
| PROCESSING | COMPLETED | ✅ | 解析成功 |
| PROCESSING | FAILED | ✅ | 解析异常 |
| COMPLETED | PROCESSING | ✅ | 重新 `process()`（重新解析） |
| FAILED | PROCESSING | ✅ | 重新 `process()`（重试） |
| UPLOADED | COMPLETED | ❌ | 禁止跳过 PROCESSING |
| COMPLETED | UPLOADED | ❌ | 禁止回退 |
| FAILED | UPLOADED | ❌ | 禁止回退 |

**实现方式**：`DocumentStatus` 枚举内含 `Set<DocumentStatus> allowedTargets`，Service 层调用 `status.validateTransition(target)`。不引入 Spring State Machine。

---

## 4. ADR 索引

本 change 涉及 1 项不可逆/高代价决策，单独写 ADR：

- `@.specs/adr/001-document-parser-strategy.md` — DocumentParser 接口策略模式：为何接口放 L2 而非 L3

其余 D1~D10 均在决策清单中完成，被推翻时只需改实现类或配置，不单独写 ADR。

---

## 5. 风险

| # | 风险 | 类别 | 影响 | 概率 | 缓解 |
|:--|:--|:--|:--|:--|:--|
| R1 | **PDFBox CJK 中文提取乱码** | 实现 | AC-2「解析返回文本内容」验收失败 | 中 | ① 测试阶段使用含中日韩文字的 PDF 验证；② 若乱码，PDFBox 支持自定义 `Encoding`，可指定 `GBK`/`UTF-8`；③ 严重乱码时记录为已知问题，v2 切换 MinerU 解决 |
| R2 | **大 PDF（~50MB）全读入 `byte[]` 导致 OOM** | 上线 | 上传/解析 50MB 文件时 JVM OOM，服务崩溃 | 低 | ① JVM 默认堆 1/4 内存（通常 ≥ 2GB），50MB 远在安全范围内；② 单次请求只处理一个 PDF；③ v2 异步解析可流式处理 |
| R3 | **MinIO 连接失败导致上传不可用** | 上线 | 所有文档上传 500 错误 | 中 | ① 应用启动时 `MinioConfig` 尝试 `listBuckets()` 健康检查，失败时 WARN 日志（不阻塞启动）；② MinIO 恢复后自动可用（MinioClient 无状态）；③ v2 加 Actuator HealthIndicator |
| R4 | **MySQL `ddl-auto: validate` + 表未创建导致启动失败** | 实现 | 第一次启动前未执行 DDL，应用起不来 | 高 | ① TASK 阶段必须产出手动 DDL SQL 脚本（`src/main/resources/db/init-document.sql`）；② 本地开发启动前先执行；③ 或首次用 `update` 生成表后切回 `validate`（startup 脚本自动化） |
| R5 | **既有 `application.yml` 修改后影响 `init-platform` 的无基础设施启动能力** | 实现 | 修改 autoconfigure exclude 后，本地无 MySQL/MinIO 时启动失败 | 中 | ① 使用 `spring.profiles.active: dev` 控制，只在 dev profile 下连接 MinIO/MySQL；② 保留一个 `local` profile 继续启用所有 exclude 做无基础设施启动 |
| R6 | **并发上传时 MinIO minioPath UUID 碰撞（极端情况）** | 上线 | 后上传文件覆盖先上传文件 | 极低 | UUID v4 碰撞概率 < 10^-15，实际可忽略；兜底：上传前检查 MinIO 中 key 是否已存在，存在则重新生成 |
| R7 | **长期债务：`DocumentParser` 接口签名 `byte[]` 不适用于流式解析** | 长期 | 超大 PDF（> 100MB）或流式来源无法适配 | 低 | 当前 PDF 限 50MB，够用；后续可新增接口方法 `parse(InputStream is, long size)`，`byte[]` 版本作为 default method 调用流版本 |

---

## 6. 不在范围

- **不设计 Neo4j 节点/关系**：本次仅落 MySQL 文档元数据，不将解析结果写入图数据库
- **不设计 RabbitMQ 异步消息**：解析同步完成，不定义 Exchange/Queue
- **不设计 Redis 缓存**：文档查询直接走 MySQL，无缓存层
- **不设计 JWT 认证过滤**：API 端点暂不对未认证请求拦截（与 init-platform 现状一致）
- **不设计 PDF 版本管理**：覆盖上传同名文件为新建记录
- **不设计 MinIO 分片上传/断点续传**：50MB 以下单次上传即可
- **不设计文档批量操作**：单条上传/删除/解析，不设计 batch API
- **不设计 Swagger/OpenAPI 文档注解**：Controller 暂不加 `@Operation` 等注解
- **不设计 MapStruct 转换器**：VO/BO/DO 手动转换，不引入 MapStruct（本次对象少，手写即可）

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|:--|:--|:--|:--|
| `infrastructure/storage/FileStorageService.java` | MinIO 文件上传/下载/删除薄封装 | 所有需要文件存储的场景 | 后续 CSV 导入、报告导出、备份文件均通过此 Service 操作 MinIO |
| `application/document/service/DocumentParser.java` | 可扩展 PDF 解析接口 | 任何需要提取 PDF 文本的场景 | v2 MinerU 适配器实现同一接口，调用方零改动 |

### 9.2 新增的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|:--|:--|:--|:--|
| DocumentParser 接口归属 L2 | 解析策略接口定义在 L2 application 层，具体实现可跨层调用 L3 | 所有 PDF 解析场景 | 低——移到 common 或 L3 只需改 import |
| 文档状态机实现方式 | 枚举内嵌转换规则 + Service 层校验（不引入 Spring State Machine） | 所有带状态的业务实体 | 低——换框架只需改 Service 层 |
| MinIO 配置最小化 | endpoint + accessKey + secretKey + bucket，无分片/加密/S3 兼容层 | 所有 MinIO 交互 | 低——MinioConfig 类可增量加配置 |

### 9.3 新增的跨模块契约

```
- POST /api/v1/document/upload     multipart/form-data → ApiResponse<DocumentVO>
- POST /api/v1/document/{id}/process                           → ApiResponse<ParseResultVO>
- GET  /api/v1/document?pageNum=&pageSize=                    → ApiResponse<PageResult<DocumentVO>>
- GET  /api/v1/document/{id}                                  → ApiResponse<DocumentVO>
- PUT  /api/v1/document/{id}        application/json          → ApiResponse<DocumentVO>
- DELETE /api/v1/document/{id}                                → ApiResponse<null>

- DocumentVO: { documentId, documentNo, name, subject, fileSize, minioPath, status, pageCount, createTime, updateTime }
- ParseResultVO: { documentId, textContent, pageCount, metadata: {} }
```

### 9.4 依赖变动

无新增依赖。所有依赖已在 `pom.xml` 预配置（`minio`、`pdfbox`、`spring-boot-starter-data-jpa`、`mysql-connector-j`），本次首次激活使用。

### 9.5 禁动清单变化

```
新增禁动（本次后生效）：
- application/document/service/DocumentParser.java         接口契约不可改（修改签名会破坏所有实现类 + 调用方）
- application/document/service/DocumentService.java        接口方法不可删（L1 依赖此接口）
- infrastructure/storage/FileStorageService.java            文件操作统一入口，禁止 L2 直接注入 MinioClient
- 新增禁动：MySQL document 表 DDL（表结构变更需走独立 CHANGE，禁止在业务 task 中顺手加字段）

解禁：
- application.yml 中的 MySQL/JPA/Transaction AutoConfig 排除项 → 本次取消，MySQL 成为活跃基础设施
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。