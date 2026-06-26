# DESIGN: 图融合迁入图分析模块 + 构建完成事件驱动触发融合

- **Change ID**: `fusion-to-analysis-event-driven`
- **关联**: `@.specs/fusion-to-analysis-event-driven/REQUIREMENT.md`、`@.specs/fusion-to-analysis-event-driven/CHANGE.md`、`@.specs/CONTEXT.md`、`@.specs/adr/018-grade-event-driven-decoupling.md`、`@.specs/adr/020-fusion-atomicity-neo4j-tx.md`、`@.specs/adr/021-repository-split-architecture.md`
- **作者**: AI（Architect 角色）+ 人工 review
- **0₋ 架构基线门处置**: 用户选定「嵌入项目级 ADR-024」——本设计同时奠定项目级 ADR（模块依赖方向），沿用既有 23 条 ADR + `CONTEXT.md` 作为事实架构基线，不新建 `ARCHITECTURE.md`。

---

## 0. 技术栈选定

> `CONTEXT.md`「已锁技术决策」已锁定全栈，本 change 为纯重构 + 事件重构，无栈变更。依 2-design 步骤 0 例外直接读用。

- **选定**: 既有栈（无变更）
- **后端**: Spring Boot 3.3.x / Java 17 LTS / Spring 事件机制（`ApplicationEventPublisher` + `@EventListener`，`CONTEXT.md` L140 已引入）
- **图数据库**: Neo4j 5.x + Spring Data Neo4j 7.x（`Neo4jClient` + 手动 Cypher，`CONTEXT.md` L120）
- **关系数据库**: MySQL 8.0 + Spring Data JPA（`JpaTransactionManager` 为主事务管理器）
- **前端**: Vue 3 + TypeScript + Vite（`CONTEXT.md` L144）
- **关键依赖**: 无新增 `pom.xml` 依赖，复用既有 Spring 事件机制
- **理由**: 本次为模块搬迁 + 同步事件解耦，所有技术栈已在 `CONTEXT.md` 锁定且项目已大量使用（`GradeUploadedEvent`/`GraphChangedEvent`/`@EventListener`），无栈选型需求
- **明确排除**: 不引入 MQ / `@Async` / `@TransactionalEventListener`（违反 `CONTEXT.md` L151 同步链路 + AC-4 同步响应契约，见 D1）

---

## 0.5 既有架构对齐（brownfield 必填 · B2 老项目护栏）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 出来的实际清单）：
- src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java（既有 · 移除 FusionService 注入 + phase2_fuse 直接调用，改为发布 GraphConstructedEvent）
- src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java（既有 · 构建完成后发布 GraphConstructedEvent(mode=FULL)，移除 @Order(1)）
- src/main/java/com/graphnexus/application/graph/fusion/event/GradeUploadedEventListener.java（既有 · 整类删除，@Order(2) fuseFull 逻辑迁入 GraphConstructedEventListener）
- src/main/java/com/graphnexus/application/graph/fusion/**（既有 · 全栈迁入 analysis：config/event/model/service/service-impl/strategy）
- src/main/java/com/graphnexus/api/graph/controller/FusionController.java（既有 · 迁 api/analysis/controller/，@RequestMapping 改 /api/v1/analysis/fusion）
- src/main/java/com/graphnexus/api/graph/dto/fusion/*（既有 · 3 VO 迁 api/analysis/dto/fusion/）
- frontend/src/api/graph.ts（既有 · 3 融合调用迁出）
- frontend/src/views/fusion/fusionStore.ts（既有 · import 改 @/api/fusion）

新增模块：
- src/main/java/com/graphnexus/application/graph/construction/event/GraphConstructedEvent.java（新 · 事件契约，归发布方 graph 模块，见 D10）
- src/main/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListener.java（新 · 同步消费者，归 analysis 模块）
- frontend/src/api/fusion.ts（新 · 3 融合调用 + 类型）

不应触碰但 AI 容易"顺手改"的（禁动）：
- src/main/java/com/graphnexus/application/graph/metrics/**（metrics 留 graph 模块，本次不迁，CHANGE 范围排除）
- src/main/java/com/graphnexus/application/graph/construction/** 中非 extract 链路的代码（仅 extract 路径解耦）
- src/main/java/com/graphnexus/infrastructure/neo4j/repository/FusionGraphRepository.java（留原位 flat，见 D7）
- src/main/java/com/graphnexus/infrastructure/neo4j/edge/MastersEdge.java（留原位 flat，见 D7）
- src/main/java/com/graphnexus/infrastructure/mysql/fusion/**（留原位，AC-1 目标即此路径，见 D7）
- pom.xml / docs/项目规范.md / docs/tech-stack-java.md（CONTEXT 禁动清单）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 跨模块解耦通知 | `ApplicationEventPublisher` + `@EventListener`（`GradeUploadedEvent`/`GraphChangedEvent`，`CONTEXT.md` L140） | **沿用** Spring 事件机制 |
| 同步事件消费范式 | `MetricsCacheInvalidator.onGraphChanged`（plain `@EventListener`，无 `@Async`/`@Order`，`application/graph/metrics/event/MetricsCacheInvalidator.java:29-31`） | **沿用** plain `@EventListener` 同步同线程范式 |
| 融合原子性 | `FusionServiceImpl` 内 `TransactionTemplate(neo4jTransactionManager)`（ADR-020，`FusionServiceImpl.java:83,143`） | **沿用**，fuseFull/fuseIncremental 内部 Neo4j 事务不变 |
| 融合策略可配置 | `KpMatchingStrategy`/`WeightCalculationStrategy` 接口 + yml 切换 | **沿用**，零改动 |
| 文档状态机 | `FileStatus` 枚举 + `TextbookRepository.save(doc)`（`infrastructure/mysql/file/entity/FileStatus.java`，`TextbookDO.failReason` 字段已存在） | **沿用** `FileStatus` 转移规则 + `failReason` 字段承载失败原因 |
| 事件契约归属（发布方 vs 消费方） | ADR-018：`GradeUploadedEvent` 归发布方 `application/file/grade/event/`，消费方 graph import | **沿用此模式**：`GraphConstructedEvent` 归发布方 graph，消费方 analysis import（见 D10） |
| REST 统一响应 | `ApiResult` + `ExtractionResultVO.fusionWarning` 字段（`api/graph/dto/construction/ExtractionResultVO.java:33,42`） | **沿用**，fusionWarning 仍经 BO→VO 透传 |
| 前端 API 域分组 | `frontend/src/api/{graph,analysis,file}.ts` 按域分文件 | **沿用**，新建 `api/fusion.ts`（见 D9） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 跨模块通信：**沿用** Spring 同步事件（ADR-018 已立先例），引入新事件类型 GraphConstructedEvent（理由：既有无"构建完成"语义事件，GraphChangedEvent 语义为"图谱变更→缓存失效"不可复用，CHANGE 范围排除其语义变更）
- 事件消费：**沿用** plain @EventListener 同步同线程（对齐 MetricsCacheInvalidator），不引入 @TransactionalEventListener/@Async（理由：AC-4 要求同步响应携带 fusionWarning，AFTER_COMMIT/@Async 破坏同步契约，见 D1）
- 融合原子性：**沿用** ADR-020 Neo4j TransactionTemplate，不引入 Spring @Transactional 包融合（理由：MySQL tx 与 Neo4j tx 独立，既有 Javadoc ConstructionServiceImpl.java:40-41 已声明）
- 文档状态机推进：**引入新模式**（FUSING→COMPLETED 流转从 ConstructionServiceImpl 迁入 fusion 监听器）→ 理由：CHANGE §2 明确要求；状态机归属随融合触发入口一起迁出 construction
- 事件契约物理位置：**沿用** ADR-018「事件归发布方模块」模式（GraphConstructedEvent 归 graph/construction/event/）→ 理由：满足"发布方不引用消费方类型"+ ArchUnit analysis→graph.event 允许 / analysis→graph.service 禁止，见 D10
- @Order 隐式排序：**移除既有模式**（@Order(1)/@Order(2)）→ 理由：改由"构建完成后显式 publishEvent"编码顺序，消除脆弱的 @Order 数值依赖，见 D5
```

---

## 1. 决策清单

> **本设计同时奠定项目级 ADR-024**（模块依赖方向：`construction(graph) → 事件 → fusion(analysis)`）。D11 为项目级规则，其余 D1–D10 为本 change 落地决策。详见 `@.specs/adr/024-fusion-event-driven-decoupling.md`。

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | 事件机制：**plain 同步 `@EventListener`**（同线程，join 发布方 `@Transactional`） | `@TransactionalEventListener(AFTER_COMMIT)` / `@Async` / `@EventListener+REQUIRES_NEW` | AC-4 要求文档上传一次 HTTP 请求同步返回 `fusionWarning`，发布方 `extract()` 须在返回前读到融合结果。`AFTER_COMMIT` 在方法体返回后（tx 提交后）才执行，`extract()` 无法读取结果填响应 → 违反 AC-4；`@Async` 引入异步违反 `CONTEXT.md` L151 + AC-4/AC-5；对齐既有 `MetricsCacheInvalidator`/`GradeGraphEventListener` 同步范式 | 融合耗时计入上传请求耗时（现状已如此：`phase2_fuse` 本就在 `extract()` 同步调用内，`ConstructionServiceImpl.java:85`），无新增同步开销 |
| D2 | 事务边界：监听器 **join 既有 `@Transactional`**（PROPAGATION_REQUIRED），不用 `REQUIRES_NEW` | `@Transactional(REQUIRES_NEW)` / 无事务 / 独立 tx | 现 `phase2_fuse` 的 MySQL 写入（FUSING/COMPLETED/`fusion_log`）在 `extract()` 的 `@Transactional`（`ConstructionServiceImpl.java:61`）内提交。等价要求监听器 MySQL 写入 join 同一 tx。`REQUIRES_NEW` 会独立提交 `fusion_log`/status，若 `extract()` 后续回滚产生孤儿 `fusion_log`，且破坏"状态+日志同事务原子提交"语义。Neo4j 写入本就独立（构建 auto-commit + 融合 `TransactionTemplate` ADR-020），不受 MySQL tx 影响 | 监听器不可独立回滚；但融合失败被 try/catch 吞掉不抛出（D4），`extract()` 不因融合回滚，无实际危害 |
| D3 | `GraphConstructedEvent` 载荷：**纯不可变数据载荷**（`source`/`mode`/`subject`/`kpNames`/`documentId`/`examNo`），**无 mutable holder**；DOCUMENT 路径结果经**文档实体**（`doc.status`+`doc.failReason`）回传 | 载荷携带 mutable `FusionOutcome` holder / 信号式无载荷事件 / 载荷含消费方类型 | 纯不可变载荷最干净满足"载荷自包含，发布方不引用消费方类型"（AC-8）。结果回传：监听器写 `doc.status`（COMPLETED/EXTRACTED）+ `failReason`（失败时），`extract()` 在同 tx 内重读 `doc` 状态（JPA 一级缓存同托管实例，`ConstructionServiceImpl` 持有的 `doc` 与监听器 `findById` 返回同一对象）填 `fusionWarning`。`failReason` 字段已存在（`TextbookDO.java:74`），无需新字段。CSV 路径无 HTTP 响应需填，监听器仅 fuseFull+吞异常 | 依赖同 tx JPA 一级缓存（监听器须 `findById` 同一托管实例后 mutate+save，不可 new+merge）→ 用 AC-4 测试覆盖；属标准 JPA 语义，非脆弱假设 |
| D4 | 文档状态机归属：`FUSING→{COMPLETED, EXTRACTED+failReason}` 流转**迁入 `GraphConstructedEventListener`**（仅 DOCUMENT 路径）；失败时 **`FUSING→EXTRACTED`+`failReason`**（按 AC-4 + 用户决策，修复 latent bug） | 状态机留 `extract()` / holder 回传让 `extract()` 写状态 / 失败停留 FUSING | CHANGE §2 明确要求状态机迁入监听器。失败语义按用户确认 = EXTRACTED+failReason：满足 AC-4（"status=EXTRACTED 不进 COMPLETED"）+ `FileStatus.FUSING→{COMPLETED,FAILED,EXTRACTED}` 允许转移（`FileStatus.java:75`）+ 修复当前 `ConstructionServiceImpl.java:154-157` catch 吞异常未回退状态、文档卡 FUSING 的 latent bug。监听器按 `source` 分支：DOCUMENT→写文档状态+fuseIncremental；CSV→仅 fuseFull（成绩无文档状态机） | analysis-fusion 监听器注入 `TextbookRepository`（L3 共享）跨文档域写状态——属已知跨域写，由同步耦合需求正当化，DESIGN 显式标注（风险 R3）；偏离"严格等价当前实现"（当前停留 FUSING），REVIEW 须回归并显式确认 |
| D5 | `@Order` 隐式排序消除：**删除 `GradeUploadedEventListener`(@Order(2)) 整类**；**移除 `GradeGraphEventListener` 的 `@Order(1)`**；`GraphConstructedEventListener` **不加 `@Order`** | 保留 `@Order` / 用 `@Order` 编排多消费者 | 原 grade 路径靠 `@Order(1)`<`@Order(2)`（`GradeGraphEventListener.java:45`/`GradeUploadedEventListener.java:32`）隐式保证"构建先于融合"，顺序依赖数值，脆弱（ADR-018 Consequences 已记此隐患）。新设计把"构建完成→发事件→融合"编码进显式事件链（`GradeGraphEventListener` 在 KP+TESTED 写入循环后 `publishEvent`，`GradeGraphEventListener.java:80-82`），顺序由代码结构保证。`@Order(2)` 监听器整体删除（fuseFull+GraphChangedEvent 逻辑由 `GraphConstructedEventListener` 承接）。删除后 `GradeUploadedEvent` 仅剩 `GradeGraphEventListener` 一消费者，`@Order(1)` 失去意义，移除以明确隐式排序已消除 | 若未来 `GraphConstructedEvent` 有多消费者需排序须重引 `@Order`；当前单一消费者，YAGNI |
| D6 | `GraphChangedEvent` 发布归属：**保留在 `FusionServiceImpl`**（`FusionServiceImpl.java:102,153`）；**移除** `ConstructionServiceImpl.extract()` `:87` 与 `GradeUploadedEventListener` `:46` 的冗余发布；`GraphConstructedEventListener` **不重复发布** | 改由 `GraphConstructedEventListener` 发布 / 多处都发 | `FusionServiceImpl` 是融合执行唯一公共边界（被 `FusionController.execute` 手动 + `GraphConstructedEventListener` 事件驱动两入口调用）。`GraphChangedEvent` 留 `FusionServiceImpl` 保证**手动融合仍触发缓存失效**（无回归，AC-5/AC-10）。`:87`/`:46` 在融合已被 `FusionServiceImpl` 发布后属冗余，移除避免重复失效。CHANGE「GraphChangedEvent 由 fusion 监听器发布」按**语义**满足——监听器触发的融合经 `FusionServiceImpl` 发布了 `GraphChangedEvent` | 偏离 CHANGE 字面"监听器发布"措辞，但语义等价且 DRY、无回归；DESIGN 显式标注此细化，REVIEW 确认 |
| D7 | 基础设施层包路径：**不迁移**。`FusionGraphRepository`/`MastersEdge` 留 `neo4j/repository/`、`neo4j/edge/`（flat）；`infrastructure/mysql/fusion/` 留原位 | 迁 `neo4j/fusion/` 子目录 / 迁 `mysql/analysis/fusion/` | ADR-021 按类拆分但**明确允许 flat**（不强制子域子目录）；neo4j 层一律 flat（3 repo + 14 edge），单独为 fusion 建子目录破坏层内一致性。AC-1 仅要求 api-dto/application/infrastructure(mysql) 迁 analysis 命名空间，且 infrastructure(mysql) 目标即 `infrastructure/mysql/fusion/`（**已在此**，无需动）；AC-1 grep 项（`application.graph.fusion`/`api.graph.dto.fusion`/`api.graph.controller.FusionController`）未涉 neo4j 层。模块归属由 application/api 层表达，非 infra 路径。与既有 `QueryGraphRepository`（flat neo4j 但属 query/metrics 域）一致 | infra 类物理位置与 application 模块归属不完全一致；已是项目惯例（`QueryGraphRepository` 先例），非新引入 |
| D8 | REST 路径：`/api/v1/graph/fusion/*` → `/api/v1/analysis/fusion/*`，**不保留旧路径别名**（旧路径 404） | 保留别名 / 双写过渡 / 301 | 前端融合管理 UI 为唯一消费者（内部管理后台），本次同步迁移（AC-6）；无外部 API 消费者；保留别名引入长期双契约维护负担。CHANGE 风险建议 + REQUIREMENT AC-6 默认均为不保留别名 | 破坏性变更，遗漏前端调用点 → 404；由 AC-6 grep 校验（`grep -rn "/graph/fusion" frontend/src/` 返回空）+ 前端 UAT 兜底 |
| D9 | 前端 API 文件：**新建 `frontend/src/api/fusion.ts`** 收纳 3 融合调用 + 类型；`fusionStore.ts` 改 `import ... from '@/api/fusion'` | 留 `graph.ts` 仅改 URL / 并入 `analysis.ts` | 后端 fusion 已为独立模块（under analysis），前端 api 文件按域分组（`graph.ts`/`analysis.ts`/`file.ts`）。独立 `fusion.ts` 与后端模块对齐，消除"fusion 调用留 graph.ts"的语义错位。churn 小（3 函数 + 类型 + `fusionStore.ts:3` 一行 import） | 较"仅改 URL"略增改动；但完成迁移意图的全栈一致性，非范围蔓延（R7.1） |
| D10 | `GraphConstructedEvent` 类**物理位置归发布方 graph 模块**：`application/graph/construction/event/GraphConstructedEvent.java`；消费方 `GraphConstructedEventListener`（analysis）import 之 | 事件归消费方 analysis / 归中立共享包 | **沿用 ADR-018 模式**（`GradeUploadedEvent` 归发布方 `application/file/grade/event/`，消费方 graph import）。满足 AC-8「发布方不引用消费方类型」（事件在 graph，发布方 graph 不触 analysis）+ ArchUnit 规则 `application.analysis.. 不依赖 application.graph..service..`（事件在 `graph.construction.event`，非 `.service.`，analysis import 事件不违 service 规则）。运行时流 graph→analysis 与编译时依赖 analysis→graph（消费方→发布方事件）的标准事件倒置，与 ADR-018 一致 | analysis 编译时依赖 graph 的 `construction.event` 包（仅事件类型，非 Service）；这是事件驱动固有的依赖倒置，ADR-018 已先例 |
| D11 | **项目级**：模块依赖方向规则 `construction(graph) → 事件 → fusion(analysis)`；**禁止** analysis 反向注入 graph Service；**禁止** construction 注入 `FusionService`；事件载荷自包含（发布方不引用消费方类型）→ 详见 **ADR-024** | 双向依赖 / construction 直接调 fusion Service（现状）/ 中间接口编排 | 见 ADR-024。本 change 把 ADR-018（grade→graph 单例）泛化为项目级"构建方→事件→聚合方"方向规则 | 后续跨模块协作须遵循此方向规则；analysis 模块扩展受限（不可反向调 graph Service），需经事件或共享 L3 |

---

## 2. 数据流 / 架构图

### 2.1 文档路径（DOCUMENT · mode=INCREMENTAL）

```
HTTP POST /api/v1/graph/construction/extract/{documentId}
  │
  v
ConstructionServiceImpl.extract(documentId)  [@Transactional MySQL · ConstructionServiceImpl.java:61]
  │  注入移除: FusionService ❌（D11）；保留 TextbookRepository/ExtractionService/
  │           ConstructionGraphRepository/ApplicationEventPublisher
  │
  ├─ phase1_build(doc, extracted, subjectNode, neo4jDocumentId)
  │     ├─ Neo4j 节点/边写入: constructionGraphRepository.save/saveEdge/saveAllEdges
  │     │   （neo4jClient.query().run() 每条 auto-commit，独立于 MySQL tx，全局可见）
  │     └─ doc.status = EXTRACTED ; textbookRepository.save(doc)   [:127-128]
  │
  ├─ eventPublisher.publishEvent(
  │     new GraphConstructedEvent(this,
  │       source=DOCUMENT, mode=INCREMENTAL, subject, kpNames=affectedKpNames,
  │       documentId, examNo=null))                                  [新发布点 · 原 :85 phase2_fuse 替换]
  │     │  (同步, 同线程, join 同一 MySQL @Transactional · D1/D2)
  │     v
  │   GraphConstructedEventListener.onGraphConstructed(event)  [application/analysis/fusion/event/ · 新]
  │     ├─ if source==DOCUMENT:
  │     │     ├─ doc = textbookRepository.findById(documentId)   (同 tx 同托管实例)
  │     │     ├─ doc.status EXTRACTED→FUSING ; save(doc)         [状态机迁入监听器 · D4]
  │     │     ├─ try:
  │     │     │     fusionService.fuseIncremental(event.kpNames, event.subject)
  │     │     │       ├─ Neo4j TransactionTemplate (ADR-020, 独立 tx)  [FusionServiceImpl.java:143]
  │     │     │       ├─ fusion_log 写入 (MySQL, join 同 tx)          [FusionServiceImpl.java:150]
  │     │     │       └─ publishEvent(GraphChangedEvent) → MetricsCacheInvalidator.clearCache
  │     │     │                                                [FusionServiceImpl.java:153 · D6 保留]
  │     │     ├─     doc.status FUSING→COMPLETED ; save(doc)
  │     │     └─ catch Exception:
  │     │           log.error(...);  fusion_log.status=FAILED (由 FusionServiceImpl 记)
  │     │           doc.status FUSING→EXTRACTED + failReason=msg ; save(doc)   [D4 · 修复 latent bug]
  │     └─ if source==CSV: 见 2.2
  │
  ├─ (publishEvent 返回后) 重读 doc.status + doc.failReason
  │     result.setFusionWarning(doc.status==EXTRACTED ? doc.failReason : null)   [D3 · 经文档实体回传]
  │
  └─ return ExtractionResultBO  →  HTTP 200 ApiResult<ExtractionResultVO>
        (status COMPLETED 无 warning / status EXTRACTED + fusionWarning)        [AC-4]
```

### 2.2 成绩路径（CSV · mode=FULL）

```
HTTP 上传成绩 → GradeUploadService.upload(...)  [@Transactional MySQL · GradeUploadService.java:48]
  │
  ├─ MySQL exam_record 写入
  ├─ eventPublisher.publishEvent(new GradeUploadedEvent(this, examNo, subject, kps))  [:116]
  │     │  (同步, 同线程, join 同一 MySQL @Transactional)
  │     v
  │   GradeGraphEventListener.onGradeUploaded(event)  [application/graph/construction/listener/]
  │     [@Order(1) 已移除 · D5 · GradeUploadedEvent 唯一消费者]
  │     ├─ ① SubjectNode findOrCreate  ② ExamNode save + BELONGS_TO_SUBJECT
  │     ├─ ③ StudentNode + ATTENDED (loop records)     [:66-72]
  │     ├─ ④ KnowledgePointNode + TESTED + BELONGS_TO_SUBJECT (loop kps)  [:74-80]
  │     └─ eventPublisher.publishEvent(new GraphConstructedEvent(this,
  │           source=CSV, mode=FULL, subject, kpNames=event.knowledgePoints,
  │           documentId=null, examNo))                            [新 · :80-82 构建完成后]
  │           │  (同步嵌套, 同线程)
  │           v
  │         GraphConstructedEventListener.onGraphConstructed(event)  [analysis/fusion/event/]
  │           ├─ if source==CSV:
  │           │     try: fusionService.fuseFull()                  [FusionServiceImpl.java:55, 无参]
  │           │            ├─ Neo4j TransactionTemplate (ADR-020)  [:83]
  │           │            ├─ fusion_log 写入                      [:97]
  │           │            └─ publishEvent(GraphChangedEvent) → MetricsCacheInvalidator  [:102 · D6]
  │           │     catch Exception: log.error(...); fusion_log.status=FAILED
  │           │           不回滚构建, 不抛出                       [AC-9]
  │           └─ (CSV 无文档状态机, 不写 doc.status)
  │
  └─ return GradeUploadResultBO  →  HTTP 200   [构建+融合已串行完成, AC-5]

  [GradeUploadedEventListener(@Order(2)) 整类已删除 · D5]
```

### 2.3 手动全量融合（不经 GraphConstructedEvent · AC-10 例外）

```
HTTP POST /api/v1/analysis/fusion/execute  [新路径 · D8]
  │
  v
FusionController.execute()  [api/analysis/controller/ · 迁自 graph/controller/]
  └─ fusionService.fuseFull()
       ├─ Neo4j TransactionTemplate (ADR-020)
       ├─ fusion_log 写入
       └─ publishEvent(GraphChangedEvent) → MetricsCacheInvalidator   [D6 · 手动融合缓存失效不回归]
  [不经 GraphConstructedEvent；AC-10 允许 FusionController.execute 为手动触发例外]
```

### 2.4 模块依赖方向（D11 / ADR-024）

```
   application/graph/construction                application/analysis/fusion
   ┌─────────────────────────────┐               ┌────────────────────────────┐
   │ ConstructionServiceImpl      │               │ GraphConstructedEventListener│
   │ GradeGraphEventListener      │               │ FusionService/Impl          │
   │      │                       │               │      ▲                      │
   │      │ publishEvent          │               │      │ @EventListener(同步) │
   │      v                       │               │      │                      │
   │ GraphConstructedEvent        │ ──事件流──▶   │ (消费)                     │
   │ (construction/event/ · D10)  │               │                            │
   └─────────────────────────────┘               └────────────────────────────┘
            │  编译时: analysis → graph.construction.event (仅事件类型, ADR-018 倒置)
            │  运行时: graph → analysis (publish → consume)
            │  ❌ analysis → graph..service (ArchUnit 禁止, AC-8)
            │  ❌ construction → FusionService (D11 禁止)
            v
   共享 L3: TextbookRepository / FusionGraphRepository / FusionLogRepository / Neo4jClient
   (L2 → L3 合规; analysis 监听器注入 TextbookRepository 为跨域写, D4 已标注)
```

---

## 3. 关键状态机

### 3.1 文档状态机（FileStatus · 仅 DOCUMENT 路径触发）

```
EXTRACTED ──(GraphConstructedEvent source=DOCUMENT, 监听器接管)──▶ FUSING
FUSING    ──(fuseIncremental 成功)──▶ COMPLETED
FUSING    ──(fuseIncremental 失败, try/catch 吞)──▶ EXTRACTED + failReason   [D4 · 修复 latent bug]
```

- 转移规则来源：`FileStatus.java:74-75`（`EXTRACTED→{FUSING,DELETING}`，`FUSING→{COMPLETED,FAILED,EXTRACTED}`）。
- **现状偏差**（本设计修正）：当前 `ConstructionServiceImpl.java:154-157` catch 吞异常后不回退状态，文档卡 `FUSING`；本设计按 AC-4 + 用户决策迁入监听器时正确实现 `FUSING→EXTRACTED+failReason`。
- CSV 路径无文档状态机（成绩无 `document` 实体）。

### 3.2 事件链状态（消除 @Order 后）

| 阶段 | 触发 | 消费 | 顺序保证 |
|---|---|---|---|
| 成绩构建 | `GradeUploadedEvent` | `GradeGraphEventListener`（唯一消费者，无 `@Order`） | 单消费者，无排序需求 |
| 构建完成→融合 | `GraphConstructedEvent` | `GraphConstructedEventListener`（唯一消费者，无 `@Order`） | 发布点在构建循环后（`GradeGraphEventListener.java:80-82` / `ConstructionServiceImpl` phase1 后），代码结构显式保证 |
| 融合完成→缓存失效 | `GraphChangedEvent` | `MetricsCacheInvalidator`（无 `@Order`） | `FusionServiceImpl` 内发布，紧随融合 |

---

## 4. ADR 索引

- **`@.specs/adr/024-fusion-event-driven-decoupling.md`**（新建 · 项目级）— D11：模块依赖方向 `construction(graph) → 事件 → fusion(analysis)`，禁止 analysis→graph Service / construction→FusionService，事件载荷自包含。泛化 ADR-018（grade→graph 单例）为项目级方向规则。

延续（不 supersede，本 change 沿用其约束）：
- `@.specs/adr/018-grade-event-driven-decoupling.md` — 事件驱动解耦先例（本 change 的 `GraphConstructedEvent` 沿用其「事件归发布方 + 同步 `@EventListener`」模式）
- `@.specs/adr/020-fusion-atomicity-neo4j-tx.md` — 融合原子性（`FusionServiceImpl` 内 `TransactionTemplate` 不变）
- `@.specs/adr/021-repository-split-architecture.md` — Repository 按类拆分 + flat 布局允许（D7 沿用）
- `@.specs/adr/009-auto-incremental-fusion-hook.md` — 自动增量融合触发位置（本 change 将触发入口由直接调用/`@Order` 改为 `GraphConstructedEvent`，触发位置语义迁移，REVIEW 须回归对齐措辞）

---

## 5. 风险

| # | 风险 | 类型 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|---|
| R1 | 同步事件**嵌套发布**：`GradeGraphEventListener` 处理 `GradeUploadedEvent` 时 `publishEvent(GraphConstructedEvent)` 触发嵌套同步监听器，栈深增加；若嵌套监听器抛未捕获异常会冒泡回 `GradeUploadService.upload` 的 `@Transactional` 触发回滚 | 实现 | 成绩上传因融合异常整体回滚（含已写 MySQL `exam_record`） | 中 | `GraphConstructedEventListener` 全程 try/catch 吞融合异常不抛出（D4/AC-9，对齐 `GradeUploadedEventListener:38-43` 现状）；仅构建阶段异常（`GradeGraphEventListener` 内 Neo4j 写）可能冒泡——那本就该让上传失败。集成测试覆盖嵌套事件链路（AC-5/AC-9） |
| R2 | 破坏性 REST 路径迁移 `/api/v1/graph/fusion/*` → `/api/v1/analysis/fusion/*`，前端遗漏调用点 → 404 | 上线 | 融合管理 UI 功能不可用 | 中 | AC-6 grep 校验 `grep -rn "/graph/fusion" frontend/src/` 返回空；前端融合管理页手动全量融合/状态/回滚 UAT；API 文档同步 |
| R3 | analysis-fusion 监听器注入 `TextbookRepository` 跨文档域写文档状态——模块边界软违反；未来文档状态机演进须同步改 fusion 监听器 | 长期债务 | 跨域耦合维护成本上升 | 中 | DESIGN 显式标注（D4）；监听器内文档状态写集中一处 + 中文注释标明；未来可引入 `DocumentStatusPort` 接口解耦（v2，本次不做 per R7.1）；REVIEW 确认此让步可接受 |
| R4 | Neo4j 构建写入 auto-commit 独立于 MySQL tx——若 MySQL tx 回滚（构建阶段异常），Neo4j 已写节点成孤儿 | 实现 | dual-store 一致性缺口（图谱残留 vs MySQL 回滚） | 低 | **既有缺口**（`ConstructionServiceImpl.java:40-41` Javadoc 已声明），本次重构**不恶化**：融合失败被吞不回滚（D4），构建失败本就冒泡且现状已有此缺口。维持现状，记入 §6 未来项，本次不处理 |
| R5 | EXTRACTED-on-failure 修复改变可观测行为（当前 FUSING → 新 EXTRACTED），若有外部逻辑依赖"FUSING 即融合失败"会受影响 | 上线 | 依赖该 latent 行为的查询/重跑逻辑失效 | 低 | 确认无外部依赖（grep `FUSING` 消费方）；REVIEW 显式回归该行为变化；`failReason` 字段承载原因，可观测性不降反升 |
| R6 | 监听器经文档实体回传结果（D3）依赖同 tx JPA 一级缓存：监听器须 `findById` 同一托管实例后 mutate+save，若误用 new+merge 则 `extract()` 读不到更新 | 实现 | `fusionWarning` 未正确填入响应，违反 AC-4 | 中 | DESIGN 明确监听器 `findById`+mutate+save 模式（D3）；AC-4 测试用例（模拟融合失败断言响应含 `fusionWarning` 且 `status=EXTRACTED`）覆盖；代码 review 检查无 new+merge |

> 含实现风险（R1/R4/R6）、上线风险（R2/R5）、长期债务（R3）各至少一条。

---

## 6. 不在范围

- **`ARCHITECTURE.md` 建立**：用户选定嵌入 ADR-024，不新建项目级架构文档（既有 23 ADR + `CONTEXT.md` 充任基线）。
- **`DocumentStatusPort` 接口解耦文档状态写**：D4 的跨域写让步之长期解法，v2 评估（R7.1 本次不做）。
- **Neo4j/MySQL dual-store 事务一致性**：R4 既有缺口，独立 change 处理（需 ChainedTransactionManager 或 Saga，超出本次纯重构范围）。
- **metrics 模块搬迁**：CHANGE 范围排除，metrics 留 graph 模块。
- **异步融合 / MQ / `@Async`**：CHANGE out，同步事件。
- **REST 旧路径别名 / 双写过渡**：D8 不保留。
- **fusion 功能增强**（新匹配策略/权重算法/回滚能力）：CHANGE out，保持 `wide-graph-fusion` 既有功能集。
- **前端融合管理 UI 视觉重设计**：仅 API 路径同步，无视觉变更 → **前端任务可跳过 `UI-DESIGN.md`**（R2.10 允许，REQUIREMENT §范围排除已声明；本 DESIGN 确认前端无视觉改动，仅路径 + 文件归属变更）。

---

## 9. 架构沉淀建议（本 change 完成后供 `A-evolve` 同步用 · 软约束）

### 9.1 新增的可复用抽象（建议 append 到 CONTEXT「既有抽象索引」段）

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/graph/construction/event/GraphConstructedEvent.java` | "图谱构建完成"统一同步事件契约（载荷 `source`/`mode`/`subject`/`kpNames`/`documentId`/`examNo`） | 文档抽取完成 / 成绩图谱构建完成 | 未来任何"构建完成→下游聚合/通知"场景可复用此事件或仿其契约 |
| `application/analysis/fusion/event/GraphConstructedEventListener.java` | 同步 `@EventListener` 消费构建完成事件→触发融合→发 `GraphChangedEvent`；按 `source` 分支处理文档/成绩两路径 | `GraphConstructedEvent` 发布 | "构建完成→聚合"事件消费范式参考 |

### 9.2 项目级技术决策（建议 append 到 CONTEXT「已锁技术决策」段）

| 决策 | 取值 | 来源 |
|---|---|---|
| 模块依赖方向规则 | `construction(graph) → 事件 → fusion(analysis)`；禁止 analysis→graph Service；禁止 construction→FusionService；事件载荷自包含 | `fusion-to-analysis-event-driven` DESIGN（ADR-024） |
| 融合归属 | fusion 全栈归 analysis 模块（api-dto + application），infrastructure 留原位 | `fusion-to-analysis-event-driven` DESIGN（D7） |
| 构建完成→融合触发 | 同一同步事件 `GraphConstructedEvent`（DOCUMENT=INCREMENTAL / CSV=FULL），消除直接调用 + `@Order` | `fusion-to-analysis-event-driven` DESIGN（D1/D5） |

### 9.3 跨模块契约（建议 append 到 CONTEXT 术语表）

| 契约 | 内容 |
|---|---|
| `GraphConstructedEvent` | Spring 同步事件，图谱构建阶段全部完成后发布，载荷 `source`(DOCUMENT/CSV)/`mode`(FULL/INCREMENTAL)/`subject`/`kpNames`/`documentId`/`examNo`。由 `GraphConstructedEventListener`(analysis) 同步 `@EventListener` 消费触发融合。是"构建完成→融合"唯一显式触发源（手动 `FusionController.execute` 除外）。载荷自包含，发布方(graph)不引用消费方(analysis)类型；事件类归发布方 graph 模块（沿用 ADR-018 模式） |

### 9.4 依赖变动

N/A — 无 `pom.xml` 变动，复用既有 Spring 事件机制。

### 9.5 禁动清单变动（建议 append 到 CONTEXT「禁动清单」）

- `application/analysis/` 禁止反向注入 `application/graph/` 的 Service（依赖方向 ADR-024；ArchUnit `application.analysis.. 不依赖 application.graph..service..` 强制，AC-8）
- `application/graph/construction/` 禁止注入 `FusionService`（D11；构建与融合仅经 `GraphConstructedEvent` 通信）
- `GraphChangedEvent` 语义禁动：仍仅用于"图谱变更→指标缓存失效"，不得承载"构建完成"语义（CHANGE 范围排除）

---

> DESIGN 自检：技术栈已锁定（§0）✅；既有架构对齐已写入（§0.5 含触碰清单 + 沿用对照 + 沿用 vs 引新 + 禁动）✅；每条决策有备选+理由+代价（§1 D1–D11）✅；数据流/架构图（§2 四张）✅；风险 ≥3 且每条有缓解（§5 六条，含实现/上线/长期）✅；可逆性低决策有 ADR-024（§4）✅；不含完整代码实现（仅签名 + 注解行 + 调用点，R3.1）✅；§9 架构沉淀建议已写（有复用价值，非凑数）✅。
