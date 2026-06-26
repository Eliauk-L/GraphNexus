# DESIGN: 后端事务管理策略梳理与重构

- **Change ID**: `transaction-management-refactor`
- **关联**: `@.specs/transaction-management-refactor/REQUIREMENT.md`、`@.specs/CONTEXT.md`、`@.specs/CHANGE.md`

---

## 0. 技术栈选定

> 技术栈已由 CONTEXT.md 锁定。本次纯重构，无新增依赖。

- **语言**: Java 17 LTS
- **框架**: Spring Boot 3.3.x
- **数据库**: MySQL 8.0 (InnoDB, REPEATABLE_READ) + Neo4j 5.x
- **事务管理**: Spring `@Transactional` (JPA/Hibernate) + `Neo4jTransactionManager` + `TransactionTemplate`
- **事件机制**: Spring `ApplicationEventPublisher` + `@EventListener`（同步/异步）
- **理由**: 纯重构，不引入新技术栈。复用既有 Spring 事务基础设施，通过方法级拆分 + 调用顺序调整实现事务策略变更
- **明确排除**: 
  - 不引入 Seata/Atomikos/XA 分布式事务框架（太重）
  - 不引入 RabbitMQ 做最终一致性（已有但 v1 不用）
  - 不改 MySQL 隔离级别（维持 REPEATABLE_READ）

---

## 0.5 既有架构对齐

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（实际 grep 出来的清单）：
- src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java（既有 · 核心重构目标 · 6 个 @Transactional + 4 个 saveAndFlush）
- src/main/java/com/graphnexus/application/file/textbook/service/TextbookUploadService.java（既有 · 1 个 @Transactional · 调整为短事务）
- src/main/java/com/graphnexus/application/file/textbook/listener/TextbookGraphClearedEventListener.java（既有 · 逻辑不变）
- src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java（既有 · 核心重构目标 · 4 个 @Transactional + 2 个 saveAndFlush）
- src/main/java/com/graphnexus/application/graph/construction/listener/TextbookParsedEventListener.java（既有 · 1 个 @Transactional 自调用修复）
- src/main/java/com/graphnexus/application/graph/construction/listener/TextbookDeletedEventListener.java（既有 · 逻辑不变）
- src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java（既有 · 逻辑不变）
- src/main/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListener.java（既有 · 事务边界重设计）
- src/main/java/com/graphnexus/application/analysis/fusion/service/impl/FusionServiceImpl.java（既有 · updateLogFailed 笔误修复）
- src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java（既有 · 2 个 @Transactional · 事件移出事务）
- src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java（既有 · 1 个 @Transactional · 拆分为短事务+事务外事件）
- src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java（既有 · 3 个 private @Transactional 不改行为）
- src/main/java/com/graphnexus/infrastructure/neo4j/config/Neo4jTxConfig.java（既有 · 不变 · 事务管理器配置保持）

新增模块：无（纯重构，不新建模块）

禁动清单（与本次无关，不许"顺手"碰）：
- src/main/java/com/graphnexus/application/graph/metrics/*（指标模块 · 本次不涉及）
- src/main/java/com/graphnexus/application/query/chat/strategy/*（剪枝策略 · 本次不涉及）
- src/main/java/com/graphnexus/infrastructure/neo4j/repository/*.java（Repository 实现 · 不改变查询逻辑）
- src/main/java/com/graphnexus/infrastructure/neo4j/gds/*（GDS 适配器 · 本次不涉及）
- pom.xml（依赖不变 · 禁动清单）
- frontend/*（前端无变更）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|----------|---------------|------|
| JPA 事务管理 | `JpaTransactionManager` (Neo4jTxConfig) | **沿用**，不作变更 |
| Neo4j 事务管理 | `Neo4jTransactionManager` + `TransactionTemplate` (FusionServiceImpl) | **沿用**，扩展到 ConstructionServiceImpl |
| 事件发布 | `ApplicationEventPublisher` (Spring) | **沿用**，仅调整调用时机（移到 @Transactional 外） |
| 事务同步回调 | `TransactionSynchronizationManager` | **删除使用**，afterCommit workaround 不再需要 |
| Spring AOP 事务代理 | 既有 CGLIB 代理机制 | **沿用**，@Transactional 仅 public 方法利用 |
| MySQL Repository | `TextbookRepository` / `ExamRecordRepository` / `FusionLogRepository` / `QueryTaskRepository` | **沿用**，不变 |
| Neo4j Repository | `ConstructionGraphRepository` / `FusionGraphRepository` / `QueryGraphRepository` | **沿用**，不变 |
| 状态机枚举 | `FileStatus` enum | **沿用**，不变 |
| 短事务方法抽取模式 | `TextbookService.finalizeDeletion()` (为 @EventListener 独立事务而抽取) | **沿用**，类似模式用于 parse/extract 拆分 |

### 0.5.3 沿用模式 vs 引入新模式

```
- 事件在事务外发布：**引入新模式**（替代既有 afterCommit 模式）。理由：所有事件发布者自身不再持有事务，天然保证事件在 commit 后。消除手动回调。
- MySQL 状态更新短事务：**引入新模式**（替代既有 @Transactional 包裹整个长方法）。理由：满足 AC-1 前端轮询可见中间态。每个状态变更独立 commit。
- Neo4j TransactionTemplate 扩展到构建模块：**引入新模式**（替代既有 Neo4j 裸写无事务保护）。理由：ConstructionServiceImpl 当前 Neo4j 写入无事务，扩展后与 FusionServiceImpl 范式一致。
- 跨存储补偿：**引入新模式**（替代既有"失败只记 log"）。理由：满足 AC-8，Neo4j 失败时 MySQL 状态可回退。实现为 try/catch + 回退短事务。
- 分层异常传递：**沿用** (L3→L2→L1 / BusinessException)
- 构造器注入：**沿用** (@RequiredArgsConstructor)
- 事件驱动解耦：**沿用** (模块间通过事件通信，不直接注入对方 Service)
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|------|------|---------|---------|
| D1 | **parse() 拆分为 3 段：短事务(状态→PARSING) → 无事务(MinIO+解析) → 短事务(状态→PARSED)** | A. 保持一个 @Transactional 但改隔离级别为 READ_COMMITTED / B. 使用 @Transactional(propagation=REQUIRES_NEW) 嵌套 | 选拆分。A 改全局隔离级别有并发语义风险（不可重复读/幻读），且只解决可见性不解决长事务锁表。B 嵌套事务在 Spring 中实际是逻辑事务（PROPAGATION_REQUIRES_NEW 挂起外层而非真正嵌套），且方法仍在同线程同连接，saveAndFlush 的可见性问题依旧（同一连接的未提交数据对其他连接仍不可见） | 多次 commit 有额外 I/O 开销（podman 本地 MySQL ≤50ms/次），总耗时 +10% 在可接受范围（AC-9 非功能性 ≤1.1x） |
| D2 | **extract() 拆分为 4 段：短事务(→EXTRACTING) → 无事务(LLM+Neo4j写) → 短事务(→EXTRACTED) → 事务外事件** | A. extract() 拆掉 @Transactional，内部全部手动控制 / B. 保留 @Transactional 但缩小范围仅包 MySQL 操作 | 选拆分（兼容 B）。LLM 调用无法放事务内（慢 + 外部 API）。Neo4j 用 TransactionTemplate 独立管理（对齐 FusionServiceImpl 范式）。B 的问题是 saveAndFlush 大量穿插在 Neo4j 操作中，MySQL 事务仍持有连接 | Neo4j 写入失败时已提交的 EXTRACTING→EXTRACTED 需要补偿回退（新短事务），增加异常处理复杂度 |
| D3 | **事件发布移到 @Transactional 方法外（调用方负责顺序：先 tx 提交，后 publish）** | A. @TransactionalEventListener(phase=AFTER_COMMIT) / B. 保持 afterCommit 回调 | 选移到事务外。A 的 @TransactionalEventListener 要求事件发布方仍在事务内，且 Spring 实现机制与 @Async 组合有坑（当前代码注释已验证）。移到事务外最直接——发布者自身不在事务中，天然 commit 后发布 | 调用方需自行保证"先 tx 提交，后 publish"的顺序。如果 publish 放在 tx 方法内，静态分析查不出来——靠 AC-4 的 grep + 审查 + ArchUnit 测试兜底 |
| D4 | **EventListener 不标注 @Transactional，事务逻辑委托给独立 Service public 方法** | A. 给所有 @EventListener 方法加 @Transactional / B. 迁移到 @TransactionalEventListener | 选委托。A 的 AOP 穿透问题已在 TextbookGraphClearedEventListener 注释中验证（适配器绕过代理 → 注解不生效）。B 引入新注解但语义与 @EventListener 重叠，增加认知负担。委托模式与 finalizeDeletion() 既有范式一致 | 每个需要事务的监听器需要额外一次 Service 方法调用，增加一行代码。但避免了 AOP 代理的不确定性 |
| D5 | **GradeUploadService.upload() 拆分：解析(无事务) → 短事务(去重+saveAll) → 事务外事件** | A. 保持单 @Transactional 但事件用 afterCommit / B. 全部无事务 | 选拆分。解析是纯 CPU 操作不需要事务。去重+saveAll 需要原子性（查重+插入不可分割）。事件移出后 GradeGraphEventListener 在 @Async 线程中读到已提交数据 | 如果 saveAll 后、事件发布前进程崩溃，MySQL 有数据但 Neo4j 无图——与当前行为一致（当前事件也在 tx 内，但监听器 Neo4j 写无事务保护），不引入新问题 |
| D6 | **GradeServiceImpl.deleteByExamNo() 拆分：短事务(查询+物理删除) → 事务外事件** | A. 保持单 @Transactional / B. 物理删除改为逻辑删除 | 选拆分。与 D5 对称——原子操作（查询+deleteAll）在短事务内，事件在事务外。B 超出本次范围（成绩已是物理删除，见 CONTEXT.md L155） | 当前已是物理删除，事件移出不改变删除语义 |
| D7 | **FusionServiceImpl.updateLogFailed() status="FAILED"（非 "COMPLETED"）** | — | 笔误修复。见 CHANGE.md 问题 6。该行位于 catch 块，语义明确应为失败 | 无代价。修复后需确认无调用方依赖旧 "COMPLETED" 值——grep 确认 `fusion_log.status` 消费方仅 `getStatus()` 查询，不依赖 "COMPLETED" 字面值 |
| D8 | **QueryServiceImpl 3 个 private @Transactional 方法：v1 仅注释标注已知技术债，不改行为** | A. 改为 public + 移动到独立 Service / B. 用自注入 (self-injection) | 选 v1 不改。理由：① 这 3 个方法被 @Async 方法 executeAsync() 调用，所在线程不同于 HTTP 请求线程，@Transactional 的 AOP 穿透问题可能已被 ThreadLocal 事务上下文影响，实际行为复杂；② 改 public 需验证异步线程中的事务行为是否改变；③ 属于 query_task 日志表写入，失败不影响主业务 | v1 保持现状，@Transactional 可能不生效（数据可能未在事务中写入），但 query_task 为日志类表，容忍。v2 专项处理 |
| D9 | **跨存储补偿策略：Neo4j 写入失败 → 开新短事务回退 MySQL 状态 + failReason** | A. Neo4j 失败不补偿（仅 log.error）/ B. 同步删 MySQL 数据回滚 | 选补偿回退。A 会导致状态机卡中间态（EXTRACTING 但 Neo4j 无数据），前端无法恢复。B 删除数据过于激进。补偿回退到上一稳定态（如 EXTRACTING→PARSED）让用户可手动重试 | 补偿事务自身也可能失败（DB 宕机），此时状态卡住需手动处理——这是可接受的降级（概率极低，DB 宕机时整个系统不可用） |
| D10 | **Neo4j 写操作在 ConstructionServiceImpl 中使用 TransactionTemplate(neo4jTransactionManager) 包裹** | A. 保持裸写无事务 / B. 用 Neo4j @Transactional(transactionManager="neo4jTransactionManager") | 选 TransactionTemplate。与 FusionServiceImpl 范式保持一致。B 的问题：Neo4j @Transactional 注解在 Spring Data Neo4j 7.x 中仅对 SDN repository 方法自动管理（通过 Neo4jTemplate），对 Neo4jClient 手动 Cypher 不保证事务性。TransactionTemplate 最直接可控 | extract() 方法中新增 `new TransactionTemplate(neo4jTransactionManager)` 注入和 1 行 execute() 包裹，约 +5 行代码 |

---

## 2. 数据流 / 架构图

### 2.1 文档处理管线（重构后）

```
POST /parse/{id}
  │
  ├─1─ Short Tx (JPA): findById → status=PARSING → commit          ◄── ≤50ms，前端轮询可见
  │
  ├─2─ No Tx: MinIO read → Parser chain → ParseResult
  │     ↓ fail → Short Tx: status=UPLOADED + failReason
  │
  ├─3─ Short Tx (JPA): status=PARSED + textContent + pageCount → commit  ◄── ≤50ms
  │
  └─4─ Publish TextbookParsedEvent ────────────────────────────────────── ◄── 事务外

@Async("queryAsyncExecutor") listener:
  TextbookParsedEvent → ConstructionService.extract(documentId)
       │
       ├─1─ Short Tx (JPA): validate + status=EXTRACTING → commit    ◄── 前端轮询可见
       │
       ├─2─ No Tx: LLM extraction (ExtractionService)
       │     ↓ fail → Short Tx: status=PARSED + failReason (补偿回退)
       │
       ├─3─ Neo4j Tx (TransactionTemplate): phase1_build
       │     ↓ fail → Short Tx: status=PARSED + failReason (补偿回退)
       │
       ├─4─ Short Tx (JPA): status=EXTRACTED → commit
       │
       ├─5─ Publish GraphChangedEvent ───────────────────────────── ◄── 事务外
       │
       ├─6─ Publish GraphConstructedEvent(mode=INCREMENTAL) ──────── ◄── 事务外
       │     │
       │     └─→ GraphConstructedEventListener (同步):
       │          ├─ Short Tx: status=FUSING → commit
       │          ├─ Neo4j Tx: FusionService.fuseIncremental()
       │          │     ↓ fail → Short Tx: status=EXTRACTED + failReason (补偿)
       │          ├─ Short Tx: status=COMPLETED → commit (成功)
       │          └─ Publish GraphChangedEvent (融合后 · 事务外)
       │
       └─7─ (前端轮询可见: EXTRACTING→EXTRACTED→FUSING→COMPLETED)
```

### 2.2 成绩上传管线（重构后）

```
POST /api/v1/file/grades/upload
  │
  ├─1─ No Tx: 读取文件字节 → FileParserRegistry 选解析器 → parse → GradeParsePayload
  │
  ├─2─ Short Tx (JPA): exam_no 去重检查 → saveAll(records) → commit   ◄── ≤50ms
  │
  └─3─ Publish GradeUploadedEvent ─────────────────────────────────── ◄── 事务外

@EventListener listener (同步):
  GradeGraphEventListener.onGradeUploaded():
    ├─ Neo4j: 读写 examRecord (MySQL · 读已提交) → 构建图     ◄── MySQL 数据已在 step2 commit 可见
    ├─ Publish GraphConstructedEvent(mode=FULL) → GraphConstructedEventListener → fusion
    └─ Publish GraphChangedEvent
```

### 2.3 文档删除管线（重构后）

```
POST /api/v1/file/textbooks/{id}
  │
  ├─1─ Short Tx (JPA): findById → status=DELETING → commit     ◄── ≤50ms
  │
  └─2─ Publish TextbookDeletedEvent ──────────────────────────── ◄── 事务外

@Async("queryAsyncExecutor") listener:
  TextbookDeletedEvent → TextbookDeletedEventListener:
    ├─ Neo4j: deleteByDocumentId (幂等)
    ├─ Publish GraphChangedEvent
    └─ Publish TextbookGraphClearedEvent
         └─ TextbookGraphClearedEventListener:
              └─ TextbookService.finalizeDeletion (Short Tx):
                   ├─ MinIO 删除 (幂等 · 容忍不存在)
                   └─ MySQL 物理删除
```

### 2.4 成绩删除管线（重构后）

```
DELETE /api/v1/file/grades/{examNo}
  │
  ├─1─ Short Tx (JPA): 查询 → deleteAll → commit    ◄── ≤50ms
  │
  └─2─ Publish GradeDeletedEvent ─────────────────── ◄── 事务外

@EventListener listener (同步):
  GradeGraphEventListener.onGradeDeleted():
    ├─ Neo4j: deleteEdgesByExamNo + deleteExamNode (幂等)
    └─ Publish GraphChangedEvent
```

### 2.5 融合操作（不变 · 仅修复笔误）

```
FusionServiceImpl.fuseFull() / fuseIncremental():
  ├─ 预计算: 纯内存（事务外）
  ├─ Neo4j Tx (TransactionTemplate): merge + MASTERS 重算
  ├─ MySQL: fusionLogRepository.save() (隐式事务 · JPA repository 自带)
  ├─ updateLogCompleted: status=COMPLETED ✓
  ├─ updateLogFailed: status=FAILED     ← D7 修复（原为 COMPLETED 笔误）
  └─ Publish GraphChangedEvent (事务外 · 本次保持原位置)
```

---

## 3. 关键状态机（不变）

文档状态机行为不变，仅每个状态变更的**事务提交时机**提前：

```
UPLOADED ──[短tx commit]──> PARSING ──[无tx: MinerU]──> 
  ┌─ 失败回退（短tx: UPLOADED+failReason）
  
PARSED ──[@Async: 短tx commit]──> EXTRACTING ──[无tx: LLM]──> 
  └─ [Neo4j Tx: 写入]──>
  ┌─ LLM/Neo4j 失败补偿（短tx: PARSED+failReason）
  
EXTRACTED ──[短tx commit]──> FUSING ──[Neo4j Tx: fusion]──> 
  ┌─ Fusion 失败补偿（短tx: EXTRACTED+failReason）
  
COMPLETED  ←── 成功终点
```

---

## 4. ADR 索引

| ADR | 标题 | 决策摘要 |
|-----|------|---------|
| `@.specs/adr/025-transaction-boundary-strategy.md` | 事务边界策略（短事务/无事务/Neo4j独立/事件事务外） | 本项目所有 L2 Service 的事务边界遵循统一策略：MySQL 状态更新=短事务、慢操作=无事务、Neo4j=独立 TransactionTemplate、事件=事务外发布 |
| `@.specs/adr/026-cross-storage-compensation.md` | 跨存储（MySQL+Neo4j）失败补偿策略 | Neo4j 写入失败时，通过独立短事务将 MySQL 状态回退到上一稳定态 + failReason，接受最终一致性而非强一致 |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|------|------|------|------|
| R1 | **短事务 + 补偿事务在极端故障下状态卡住**：短事务 commit 后进程崩溃，MySQL 已是最新状态（如 EXTRACTING），但后续 Neo4j 补偿事务未执行 | 文档状态卡在中间态，需手动 re-process | 低 | ① `TextbookParsedEventListener` 已有 try/catch + saveFailReason 模式，补偿失败至少写 failReason 到 DB；② 前端状态轮询最多 5 分钟，超时后状态不变 = 用户可见异常；③ 恢复路径：用户点「重新图谱化」触发 extract() 重新从 EXTRACTING 开始（validateDocStatus 允许 EXTRACTING→重试） |
| R2 | **多次短事务 commit 导致性能回退**：原来 1 次 commit 的操作现在变 2-3 次 commit | 响应时间 +10-20%（podman 本地 MySQL commit ~30-50ms） | 中 | ① HikariCP `autoCommit` 关闭 + 显式事务（commit 本身开销极小）；② AC-9 设定 ≤1.1x 性能预算；③ 集成测试测量端到端耗时，超标则考虑合并相邻短事务 |
| R3 | **异步线程中 MySQL 数据不可见**：@Async 监听器在新线程中读取 MySQL，如果发布方短事务 commit 在 publishEvent 之前但异步线程早于预期启动 | TextBookParsedEventListener 读到旧的文档状态 | 极低 | ① D3 决策中 publishEvent 在 commit 之后（调用方代码顺序保证）；② 即使极端情况下（CPU 指令重排？Java happens-before 保证 `commit()` 和 `publishEvent()` 的顺序），事件对象中的 documentId 已足够，监听器自行 findById 获取最新状态 |
| R4 | **ArchUnit 测试规则过于严格**：AC-3 的 "no @Transactional method accesses Neo4j" 规则可能误杀合法的只读 Neo4j 查询（如 subgraph 查询） | CI 假失败 | 中 | ArchUnit 规则设为 `archunit_guard` profile 的 WARN 而非 ERROR；手工审查豁免 list 在 ADR-025 中维护 |
| R5 | **既有 @TransactionalEventListener 的遗留代码**：项目当前已无 @TransactionalEventListener 使用（确认 via grep），但未来可能被错误引入 | 偏离本次策略 | 低 | ADR-025 写入 CONTEXT.md 默认行为，并加入代码审查 Checklist |

---

## 6. 不在范围

- **异步融合**（v2 · REQUIREMENT.md 已声明）
- **成绩路径 Neo4j 失败补偿**（v2 · 成绩图谱构建失败保持现有"仅 log.error"行为）
- **QueryServiceImpl 私有 @Transactional 修复**（v2 · 当前仅注释标注技术债）
- **@TransactionalEventListener 迁移**（out · 项目不使用该机制，保持统一用 plain @EventListener + Service 委托）
- **MySQL 隔离级别变更**（out · 维持 REPEATABLE_READ）
- **分布式追踪/事务监控**（v2 · 可观测性增强）

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

本 change 无新增可复用抽象。重构将既有的 `TransactionSynchronizationManager.registerSynchronization()` 手动回调模式替换为"事务外发布事件"模式，该模式成为项目默认约定（见 § 9.2），但不创建新的 `lib/` / `utils/` 类文件。

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|------|------|---------|---------|
| 事务边界策略（6 条细则） | 见 ADR-025 | 所有 L2 Service 方法 | 高：推翻意味着所有 @Transactional 方法的审查标准改变，需重新评估每个方法的并发安全性 |

### 9.3 新增 / 修改的跨模块契约

```
- 事件发布时序契约：所有 ApplicationEventPublisher.publishEvent() 调用必须在 JPA 事务提交之后（即发布方方法不标注 @Transactional，或显式在事务外发布）
- TextbookParsedEvent 发布时机变更：从 parse() @Transactional 内的 afterCommit 回调 → parse() 方法末尾事务外直接发布。消费者（TextbookParsedEventListener）行为不变
- TextbookDeletedEvent 发布时机变更：从 deleteTextBook() @Transactional 内的 afterCommit 回调 → 事务外直接发布
- GradeUploadedEvent 发布时机变更：从 GradeUploadService.upload() @Transactional 内 → 事务外发布
- GradeDeletedEvent 发布时机变更：从 GradeServiceImpl.deleteByExamNo() @Transactional 内 → 事务外发布
```

### 9.4 新增 / 升级的依赖

本 change 无新增依赖。

### 9.5 禁动清单变化

- 新增禁动：禁止在 `@Transactional` 方法内调用 `ApplicationEventPublisher.publishEvent()`
- 新增禁动：禁止在 `@EventListener` 方法上标注 `@Transactional`
- 新增禁动：禁止使用 `TransactionSynchronizationManager.registerSynchronization()` 手动注册 afterCommit 回调
- 新增禁动：禁止在 `@Transactional` 方法内执行 LLM API 调用 / MinerU API 调用 / MinIO 文件 I/O

---

> 设计决策 D1–D10 详细说明见 § 1。ADR-025 和 ADR-026 单独文件提供完整 Context/Decision/Consequences。