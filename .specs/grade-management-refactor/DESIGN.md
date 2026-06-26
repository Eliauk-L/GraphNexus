# DESIGN: 成绩管理重构

- **Change ID**: `grade-management-refactor`
- **关联**: `@.specs/grade-management-refactor/REQUIREMENT.md`、`@.specs/grade-management-refactor/CHANGE.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

- **选定**: 1 Apache POI（Excel 解析）
- **后端**: Java 17 LTS + Spring Boot 3.3.x（沿用）
- **数据库**: MySQL 8.0 + Spring Data JPA + Hibernate 6.4+（沿用）；Neo4j 5.x + Spring Data Neo4j 7.x（沿用）
- **关键依赖**: Apache POI 5.2.x（`poi-ooxml`，仅需 `poi` + `poi-ooxml` 两个 artifact，约 4MB），**无其他新依赖**
- **理由**: 项目成绩文件规模小（≤50 学生 × ≤30 题），POI 内存模式完全够用；POI 是 Java 生态 Excel 解析事实标准，日期单元格处理完善，`WorkbookFactory.create()` 自动识别 .xls/.xlsx 格式
- **明确排除**: EasyExcel（Alibaba）——流式优势在本场景用不上，额外引入增加依赖复杂度；JExcelApi——不支持 .xlsx

> `pom.xml` 在禁动清单中，本次需显式申请解禁引入 `org.apache.poi:poi-ooxml:5.2.5`。

---

## 0.5 既有架构对齐

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 出的实际清单）：
- api/file/controller/GradeController.java（既有 · GET/DELETE 路由调整）
- api/file/dto/grade/GradeUploadResultVO.java（既有 · csvFilePath/csvMd5 字段删除）
- api/file/dto/grade/GradeRecordVO.java（既有 · csvFilePath/csvMd5 字段删除）
- api/file/dto/grade/DeleteResultVO.java（既有 · filePath/deletedEdgeCount 字段删除）
- application/file/grade/model/GradeFileType.java（既有 · CSV_GRADE → CSV + EXCEL）
- application/file/grade/model/GradeUploadResultBO.java（既有 · csvFilePath/csvMd5 字段删除）
- application/file/grade/model/GradeRecordBO.java（既有 · 不变）
- application/file/grade/parser/CsvGradeParser.java（既有 · CsvParsePayload → GradeParsePayload）
- application/file/grade/service/GradeService.java（既有 · queryByExam 方法移除）
- application/file/grade/service/GradeServiceImpl.java（既有 · 移除 MinIO/图谱调用，新增事件发布）
- application/file/grade/service/GradeUploadService.java（既有 · 移除 MinIO/图谱调用，重构为纯解析+MySQL+事件）
- application/file/grade/event/GradeUploadedEvent.java（既有 · 扩展 payload 字段）
- application/graph/fusion/event/GradeUploadedEventListener.java（既有 · 加 @Order(2)）
- infrastructure/mysql/file/entity/ExamRecordDO.java（既有 · csvFilePath/csvMd5 字段删除）
- infrastructure/mysql/file/repository/ExamRecordRepository.java（既有 · 删除 csvMd5 查询方法，新增条件查询方法，扩展 JpaSpecificationExecutor）
- application/file/textbook/model/DeleteResultBO.java（既有 · filePath 字段移除）
- common/exception/ErrorCode.java（既有 · 新增 A0016）
- src/main/resources/db/init.sql（既有 · exam_record DDL 更新）
- test/.../application/file/grade/parser/CsvGradeParserTest.java（既有 · 适配新类名）

新增模块：
- application/file/grade/parser/ExcelGradeParser.java（新 · FileParser 的 Excel 实现）
- application/file/grade/event/GradeDeletedEvent.java（新 · 删除事件）
- application/file/grade/model/GradeParsePayload.java（新 · 原 CsvParsePayload 重命名）
- application/graph/grade/event/GradeGraphEventListener.java（新 · 图谱构建+清理监听器）
- .specs/grade-management-refactor/migrations/20260619_grade-management-refactor_T00_drop_csv_columns.sql（新 · DDL migration）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- application/file/textbook/**（Textbook 模块，完全独立）
- application/graph/fusion/service/impl/FusionServiceImpl.java（融合核心，不变）
- application/graph/fusion/service/impl/MastersRecalculationService.java（仅读取 ExamRecordDO，列删除后需确认兼容）
- application/graph/core/service/impl/GraphServiceImpl.java（知识抽取，无关）
- infrastructure/storage/**（MinIO 模块，成绩不再使用但文档模块继续使用）
- infrastructure/neo4j/repository/GraphNodeRepository.java（仅由新 Listener 使用，接口不变）
- api/file/controller/TextbookController.java（文档上传，无关）
- pom.xml（仅新增 poi-ooxml 依赖，不改现有依赖）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|----------|-----------------|------|
| 文件解析器注册与路由 | `FileParser` 接口 + `FileParserRegistry` | **沿用** — ExcelGradeParser 实现 FileParser，Spring 自动注册 |
| 解析结果载体 | `FileParseResult<T>` + `FileParseRequest` | **沿用** — 不变 |
| 成绩文件业务类型标识 | `FileParser.BIZ_GRADE = "GRADE"` | **沿用** — CSV 和 Excel Parser 均返回此 businessType |
| MySQL 成绩持久化 | `ExamRecordDO` + `ExamRecordRepository` | **沿用** — 删除 csv 列，新增 Specification 查询 |
| Neo4j 图写入/删除 | `GraphNodeRepository` | **沿用** — 仅由新 Listener 调用，方法签名不变 |
| Spring 事件发布 | `ApplicationEventPublisher` | **沿用** — 上传/删除后发布事件 |
| 事件监听 | `@EventListener` + `ApplicationEvent` | **沿用** — 新增 GradeGraphEventListener，@Order 控制顺序 |
| 统一异常处理 | `BusinessException` + `ErrorCode` | **沿用** — 新增 A0016 |
| 构造器注入 | `@RequiredArgsConstructor` + `private final` | **沿用** — 所有新类遵循 |
| Excel 解析 | 无 | **新建**（理由：项目首次需要 Excel 解析） |
| 动态条件查询 | 无（仅有方法名派生 + @Query） | **引入新模式** → `JpaSpecificationExecutor<ExamRecordDO>`（理由：6 个可选参数无法用方法名派生穷举 64 种组合） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 分层架构：**沿用** L1(api) → L2(application) → L3(infrastructure)，common 被所有层依赖
- 依赖注入：**沿用** 构造器注入 + @RequiredArgsConstructor
- 文件解析扩展：**沿用** FileParser 接口 + FileParserRegistry 策略路由
- 事件驱动：**沿用** ApplicationEventPublisher + @EventListener（同步执行，无 @Async）
- 数据访问：**沿用** Spring Data JPA Repository 模式
- Neo4j 访问：**沿用** Neo4jClient + 手动 Cypher
- 动态条件查询：**引入新模式** → JpaSpecificationExecutor（理由：既有方法名派生无法穷举 6 个可选参数组合；Specification 是 Spring Data JPA 原生机制，非外部库；与既有 Repository 模式兼容）
- 图谱操作隔离：**引入新模式** → 独立 GradeGraphEventListener（理由：REQUIREMENT 架构约束要求成绩模块不直接调用图谱代码；Spring 事件机制天然支持此解耦）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|------|------|---------|---------|
| D1 | ExcelGradeParser 与 CsvGradeParser 共享 `GradeParsePayload` 作为解析输出 | ExcelGradeParser 独立定义 ExcelParsePayload | 两个 Parser 解析完全相同的双行表头结构，输出字段一致（examNo/students/knowledgePoints 等），共享 payload 消除重复。原 `CsvParsePayload` 重命名为 `GradeParsePayload` | 若未来 CSV 和 Excel 格式出现差异，需拆分 payload 或引入子类 |
| D2 | GradeUploadedEvent 仅携带 examNo + subject + knowledgePoints，图谱 Listener 自行从 MySQL 读取学生明细 | Event 携带完整 GradeParsePayload（含所有学生记录） | 事件保持轻量（避免大对象在内存中传递）。Listener 通过 `ExamRecordRepository.findByExamNoAndIsDeleted()` 获取学生明细，与既有融合模块的读取模式一致 | Listener 需注入 ExamRecordRepository，增加 MySQL 依赖；事件与数据库读取之间存在微小的时序窗口（但在同步事件模型下无影响） |
| D3 | 图谱构建与融合通过 `@Order` 控制 Listener 顺序：GradeGraphEventListener(@Order(1)) → GradeUploadedEventListener(@Order(2)) | 图谱 Listener 内直接调用融合；或使用独立事件两阶段发布 | @Order 是 Spring 原生机制，两个 Listener 各自独立、单一职责。融合 Listener 的既有逻辑无需修改 | @Order 引入了隐式的 Listener 间依赖（融合依赖图谱已构建），但两个 Listener 在同一线程同步执行，无并发问题 |
| D4 | 条件查询使用 `JpaSpecificationExecutor` + 动态 `Specification<ExamRecordDO>` | 方法名派生 64 个方法；或单个 @Query 用 COALESCE 拼接 | 6 个可选参数无法用方法名穷举；JPQL COALESCE 动态拼接可读性差。Specification 是 Spring Data JPA 原生 API，支持类型安全的条件组合，与既有 Repository 共存 | 引入 `JpaSpecificationExecutor` 接口，Repository 需多继承一个父接口；Specification 的 where-if 样板代码较多 |
| D5 | 删除 GradeServiceImpl 中 MinIO 调用、图谱调用、csvMd5 判重，仅保留 MySQL 操作 + 事件发布 | — | 与 REQUIREMENT 架构约束一致 | 删除链路简化，DeleteResultVO 不再返回 filePath 和 deletedEdgeCount |
| D6 | `exam_record` 表 DROP `csv_file_path` + `csv_md5` 列 + DROP `idx_csv_md5` 索引 | 列重命名保留 | 两列已无实际用途（不存 MinIO、不用 MD5 判重），保留空列/弃用列是技术债 | 存量数据丢失 csv_file_path 和 csv_md5 信息（当前为本地开发环境，无可恢复需求） |
| D7 | GradeFileType 枚举拆为 `CSV` 和 `EXCEL`，businessType 统一为 `BIZ_GRADE` | 单一 GRADE 值，格式通过扩展名推断 | 格式信息有业务价值（审计/日志/响应体中显示来源格式），拆开后可扩展更多格式而无需改枚举语义 | 枚举值从 1 变 2，所有引用处需更新 |

---

## 2. 数据流 / 架构图

### 2.1 成绩上传链路（重构后）

```
  POST /api/v1/file/grades/upload (multipart file + subject)
         │
         v
  GradeController.upload()
         │
         v
  GradeUploadService.upload(file, subject)
         │
         ├─ 1. 读取文件字节 → 提取 exam_no
         │
         ├─ 2. exam_no 去重检查
         │      ExamRecordRepository.existsByExamNoAndIsDeletedFalse(exam_no)
         │      ├─ true  → throw BusinessException(A0016, "考试编号已存在，请先删除")
         │      └─ false → 继续
         │
         ├─ 3. 文件解析
         │      FileParserRegistry.getParser(filename, BIZ_GRADE)
         │      ├─ .csv  → CsvGradeParser.parse()  → GradeParsePayload
         │      └─ .xlsx → ExcelGradeParser.parse() → GradeParsePayload
         │
         ├─ 4. MySQL 批量写入
         │      ExamRecordRepository.saveAll(List<ExamRecordDO>)
         │
         ├─ 5. 发布 GradeUploadedEvent(examNo, subject, knowledgePoints)
         │      ApplicationEventPublisher.publishEvent(...)
         │
         └─ 6. 返回 GradeUploadResultBO → GradeUploadResultVO
                （无 csvFilePath / csvMd5，含 fileType: CSV/EXCEL）

  ┌─── 同步事件链（同一线程）───┐
  │
  │  GradeGraphEventListener.onGradeUploaded()  [@Order(1)]
  │    ├─ ExamRecordRepository.findByExamNoAndIsDeleted(examNo, 0)
  │    ├─ MERGE StudentNode × N
  │    ├─ MERGE ExamNode × 1
  │    ├─ MERGE KnowledgePointNode × M
  │    ├─ CREATE AttendedEdge × N
  │    └─ CREATE TestedEdge × M
  │
  │  GradeUploadedEventListener.onGradeUploaded()  [@Order(2)]
  │    ├─ FusionService.fuseFull()
  │    └─ ApplicationEventPublisher.publishEvent(GraphChangedEvent)
  │
  └──────────────────────────┘
```

### 2.2 成绩删除链路（重构后）

```
  DELETE /api/v1/file/grades/exam/{examNo}
         │
         v
  GradeController.deleteByExamNo(examNo)
         │
         v
  GradeServiceImpl.deleteByExamNo(examNo)
         │
         ├─ 1. 查询未删除记录
         │      ExamRecordRepository.findByExamNoAndIsDeleted(examNo, 0)
         │      └─ 空 → 幂等返回（deletedRecordCount=0）
         │
         ├─ 2. MySQL 物理删除
         │      ExamRecordRepository.deleteAll(records)
         │
         ├─ 3. 发布 GradeDeletedEvent(examNo, recordCount)
         │      ApplicationEventPublisher.publishEvent(...)
         │
         └─ 4. 返回 DeleteResultBO → DeleteResultVO
                （仅含 examNo + deletedRecordCount，无 filePath / deletedEdgeCount）

  ┌─── 同步事件链 ───┐
  │
  │  GradeGraphEventListener.onGradeDeleted()
  │    ├─ GraphNodeRepository.deleteEdgesByExamNo(examNo, "ATTENDED")
  │    ├─ GraphNodeRepository.deleteEdgesByExamNo(examNo, "TESTED")
  │    └─ GraphNodeRepository.deleteExamNode(examNo)
  │
  └──────────────────┘
```

### 2.3 条件查询链路

```
  GET /api/v1/file/grades?examNo=...&examName=...&studentNo=...&name=...&className=...&subject=...&pageNum=1&pageSize=20
         │
         v
  GradeController.listGrades(params)
         │
         v
  GradeServiceImpl.queryByConditions(query)
         │
         ├─ 构建 Specification<ExamRecordDO>
         │      where(examNo == null ? null : examNoEqual)
         │        .and(name == null ? null : nameLike)
         │        .and(className == null ? null : classNameEqual)
         │        .and(studentNo == null ? null : studentNoEqual)
         │        .and(examName == null ? null : examNameLike)
         │        .and(subject == null ? null : subjectEqual)
         │        .and(isDeletedFalse)  // 始终过滤已删除
         │
         ├─ ExamRecordRepository.findAll(spec, pageable)
         │
         └─ Page<ExamRecordDO> → PageResult<GradeRecordVO>
```

### 2.4 模块依赖关系（重构后）

```
  ┌─────────────────────────────────────────────────┐
  │  L1 api/file/                                    │
  │  GradeController                                 │
  │       │                                          │
  │       ├──> GradeUploadService (L2)               │
  │       └──> GradeService (L2)                     │
  └─────────────────────────────────────────────────┘
                │                    │
  ┌─────────────┼────────────────────┼────────────────┐
  │  L2 application/file/grade/     │                │
  │                                  │                │
  │  GradeUploadService ───> GradeService             │
  │       │                      │                   │
  │       ├──> FileParserRegistry  ├──> ExamRecordRepo│
  │       ├──> ExamRecordRepo     └──> EventPublisher │
  │       └──> EventPublisher          │              │
  │                                     │              │
  │  CsvGradeParser  ExcelGradeParser  │              │
  │       │                │           │              │
  │       └─── GradeParsePayload ──────┘              │
  │                                                  │
  │  GradeUploadedEvent   GradeDeletedEvent           │
  └──────────────────────────────────────────────────┘
                │                    │
         (事件订阅)              (事件订阅)
                │                    │
  ┌─────────────┼────────────────────┼────────────────┐
  │  L2 application/graph/          │                │
  │                                  │                │
  │  GradeGraphEventListener [@Order(1)]               │
  │       ├──> ExamRecordRepo (只读)                   │
  │       └──> GraphNodeRepository (写入/删除)         │
  │                                                  │
  │  GradeUploadedEventListener [@Order(2)]            │
  │       └──> FusionService                          │
  └──────────────────────────────────────────────────┘
```

**关键边界**：
- `application/file/grade/` 模块 **不注入** `GraphNodeRepository`、`FileStorageService`
- `application/file/grade/` 模块 **仅注入** `ApplicationEventPublisher` + 自己的事件类
- 图谱操作完全隔离在 `GradeGraphEventListener` 中

---

## 3. 关键状态机

本 change 不引入新状态机。成绩记录生命周期简单：
- 上传 → MySQL 持久化（无中间状态）
- 删除 → MySQL 物理删除（无逻辑删除，因 exam_no 判重已保护误删）

> `is_deleted` 字段保留用于全局删除约束 C3（中间状态），但本次重构简化 delete 流程：直接物理删除，不再经过 `markDeleted()` 中间态。因为 exam_no 判重（409 拒绝）已消除"并发上传冲突"场景，无需 `is_deleted` 作为删除中间态。

---

## 4. ADR 索引

| ADR | 标题 | 决策点 |
|-----|------|--------|
| ADR-001 | [Excel 解析库选型](.specs/adr/001-excel-parser-apache-poi.md) | Apache POI vs EasyExcel vs JExcelApi |
| ADR-002 | [成绩模块事件驱动解耦](.specs/adr/002-grade-event-driven-decoupling.md) | 事件驱动 vs 直接调用图谱代码 |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|------|------|------|------|
| R1 | **实现风险** — Apache POI 日期单元格处理不当，Excel 日期列（如 `2024-03-15`）被读成数值 45365 | Excel 导入的 `exam_date` 全错，导致 MySQL 和 Neo4j 中日期数据错误 | 中 | `ExcelGradeParser` 区分 `CellType.NUMERIC` + `DateUtil.isCellDateFormatted()` 双重检测；对字符串日期尝试 3 种格式（`yyyy-MM-dd` / `yyyy/MM/dd` / `yyyyMMdd`）解析；单元测试覆盖日期数值和字符串两种输入 |
| R2 | **上线风险** — DROP COLUMN 导致 `MastersRecalculationService` 读取 `ExamRecordDO` 时 Hibernate 映射失败 | 融合/MASTERS 重算流程报错，影响智能问答功能 | 低 | `ExamRecordDO` 删除两字段后 Hibernate 自动适配（`@Column` 注解不存在则映射忽略）；融合模块代码不直接引用 `csvFilePath`/`csvMd5` 字段，只需确认 `score_details` JSON 列读取不受影响 |
| R3 | **长期债务** — 条件查询 `Specification<ExamRecordDO>` 的性能随数据量增长而下降，全表 LIKE 模糊搜索 `%keyword%` 无法走索引 | 成绩表达到万级后查询变慢 | 低（当前数据量极小） | `examNo`、`studentNo`、`subject` 等精确匹配字段已有索引；`name` 和 `examName` 模糊搜索前期数据量不大可接受；后续数据量大时可引入 MySQL FULLTEXT 索引或 Elasticsearch，通过 `ExamRecordRepository` 切换实现不改变 Service 层接口 |
| R4 | **实现风险** — `@Order` 在两个不同包（`application/graph/grade/` 和 `application/graph/fusion/`）中的 Listener 之间，Spring 是否能正确排序 | 图谱未构建完融合就开始执行，导致融合找不到 KP 节点 | 低 | Spring `@Order` 对 `@EventListener` 的支持是全局的（基于 `AnnotationAwareOrderComparator`），与包路径无关；若实际运行中发现排序不稳定，可改为两阶段事件（`GradeGraphBuiltEvent` 桥接） |
| R5 | **上线风险** — `DELETE /api/v1/file/grades/exam/{examNo}` 行为变更（不再返回 `filePath` 和 `deletedEdgeCount`），前端如果依赖这些字段可能出错 | 前端删除后无法显示原有的删除详情 | 低 | 在 CHANGE.md 和 REQUIREMENT.md 已标注 VO breaking change；前端 `frontend-ui` change 需同步适配 |

---

## 6. 不在范围

- **Textbook 模块的 MinIO 存储**：文档 PDF/TXT 仍正常存入 MinIO，不受本次成绩模块 MinIO 移除影响
- **`is_deleted` 字段移除**：虽然本次简化删除流程（直接物理删除），但 `is_deleted` 列保留不动，避免 DDL 过大变更
- **Neo4j 图投影/指标重算触发**：`GraphChangedEvent` 仍在融合完成后发布，指标缓存刷新流程不受影响
- **Excel 格式写入/导出**：仅实现读取解析，不做成绩数据导出为 Excel
- **CSV/Excel 模板下载**：v2 需求
- **考试成绩统计聚合 API**：v2 需求

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|------|------|---------|---------|
| `application/file/grade/model/GradeParsePayload.java` | 成绩文件解析结果统一载体（CSV 和 Excel 共用） | 新增成绩文件格式时，Parser 输出此 payload 即可复用全链路 | 未来新增 ODS 等格式只需实现 `FileParser` + 输出此 payload |
| `application/graph/grade/event/GradeGraphEventListener.java` | 成绩事件 → Neo4j 图谱操作的隔离监听器 | 成绩模块与图谱模块的边界守门人 | 新增其他"文件→图谱"的业务（如作业批改上传）可复用此模式：业务模块只发事件，图谱操作在独立 Listener 中完成 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|------|------|---------|---------|
| 成绩模块与图谱模块事件解耦 | `ApplicationEventPublisher` + `@EventListener` + `@Order` 控制执行顺序 | 所有"文件上传→图谱操作"类业务 | 低（Spring 事件是标准机制，替换为其他方案只需改 Listener 注册方式） |
| 动态条件查询方式 | `JpaSpecificationExecutor<ExamRecordDO>` | 所有需要多条件组合查询的 Repository | 中（若切换到 QueryDSL 或 jOOQ，需替换所有 Specification 代码） |

### 9.3 新增 / 修改的跨模块契约

```
- GradeUploadedEvent 契约不变（examNo + subject + knowledgePoints），但语义变化：原来在 Neo4j 已构建后发布，现在在 MySQL 写入后发布，图谱构建由 Listener 完成
- 新增 GradeDeletedEvent（examNo + recordCount），由 GradeServiceImpl 发布，GradeGraphEventListener 消费
- DELETE /api/v1/file/grades/exam/{examNo} 响应体变更：移除 filePath 和 deletedEdgeCount 字段
- GET /api/v1/file/grades/exam/{examNo} 端点移除，功能合并到 GET /api/v1/file/grades?examNo=
```

### 9.4 新增 / 升级的依赖

| 包 | 版本 | 用途 | 是否替换既有 |
|----|------|------|-------------|
| `org.apache.poi:poi` | 5.2.5 | Apache POI 核心 | 否（新增） |
| `org.apache.poi:poi-ooxml` | 5.2.5 | .xlsx 格式支持 | 否（新增） |

### 9.5 禁动清单变化

```
- 新增禁动：
  - application/file/grade/** 禁止注入 GraphNodeRepository（架构约束，由 GradeGraphEventListener 专责图谱操作）
  - application/file/grade/** 禁止注入 FileStorageService / MinioFileStorageService（成绩模块不再操作 MinIO）
- 解禁：
  - pom.xml 本次解禁以新增 poi-ooxml 依赖
```

---

> 本文件不含完整代码实现。函数签名和接口定义见各 ADR 补充。