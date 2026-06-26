# CHANGE: 后端事务管理策略梳理与重构

- **Change ID**: `transaction-management-refactor`
- **创建日期**: 2026-06-22
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: proposed
- **触发场景**: 用户发现后端多组件多状态处理中，事务管理混乱。典型问题：文档处理管线需要在数据库中保存中间状态（PARSING→EXTRACTING→FUSING...）供前端轮询可见，但 `@Transactional` 包裹的长方法导致 `saveAndFlush()` 的状态变更在事务提交前对其他数据库连接不可见（MySQL InnoDB 默认 REPEATABLE_READ），前端无法及时看到进度更新。核心矛盾：**事务原子性 vs 状态可见性 vs 跨存储系统（MySQL + Neo4j）一致性**。

---

## Why（为什么做）

当前后端系统的事务管理存在以下系统性问题：

### 1. 长事务 + saveAndFlush() 的假可见性

`TextbookServiceImpl.parse()`（66 行，`@Transactional`）内部调用 `saveAndFlush()` 4 次以更新文档状态（UPLOADED→PARSING→PARSED/失败回退），其中夹杂 MinIO 文件读取 + MinerU API 调用（可能耗时 30s+）。同样 `ConstructionServiceImpl.extract()`（43 行，`@Transactional`）内部 `saveAndFlush()` 3 次（EXTRACTING→EXTRACTED），夹杂 LLM 调用 + Neo4j 批量写入。

**问题**：MySQL InnoDB 默认 `REPEATABLE_READ` 隔离级别下，即使 `saveAndFlush()` 将数据写入磁盘，其他数据库连接（前端轮询请求）在自己的快照读中也看不到这些中间状态。`saveAndFlush()` 只是让**同一事务内**的后续查询能读到未提交变更，对外部连接无效。这导致「管线进度指示器」（`frontend-status-flow`）无法实时展示文档处理进度——状态跳变发生在事务提交瞬间，前端只能看到 UPLOADED 直接跳到 PARSED/COMPLETED。

### 2. 混合事务管理器无协调机制

`Neo4jTxConfig` 注册了两个独立的事务管理器：
- `JpaTransactionManager`（`@Primary`）— 管理 MySQL
- `Neo4jTransactionManager` — 管理 Neo4j

`@Transactional` 注解默认走 JPA 事务管理器，这意味着：
- **Neo4j 写操作完全在 JPA 事务之外** —— `ConstructionServiceImpl.phase1_build()` 中 `constructionGraphRepository.save()` / `saveEdge()` 等 Neo4j 写入不受 `@Transactional` 保护
- **FusionServiceImpl** 手动用 `TransactionTemplate(neo4jTransactionManager)` 包裹 Neo4j 操作，但与外层 JPA 事务无协调 —— 嵌套在 `GraphConstructedEventListener`（join 发布方的 JPA `@Transactional`）中时，Neo4j 融合提交后外层 JPA 事务仍可能回滚
- 没有任何分布式事务协议（XA/2PC/Saga）协调两个存储系统

### 3. 事件发布时机依赖脆弱的手动 afterCommit 模式

`TextbookServiceImpl.parse()` 和 `deleteTextBook()` 中使用了 `TransactionSynchronizationManager.registerSynchronization()` + `afterCommit()` 来延迟事件发布，确保 `@Async` 监听器在新线程中读到已提交状态。

**问题**：
- 这种模式仅因 `@Async` 监听器需要读到已提交数据才存在 —— 如果事件发布本身不在事务内，就不需要这个 workaround
- 它导致 `parse()` 方法职责混杂：既管解析流程，又管事务同步回调
- 回调中的 `this` 引用不是 Spring 代理，容易踩坑

### 4. 事务注解在事件监听器上不稳定

`TextbookGraphClearedEventListener` 的注释明确指出：`@EventListener` 适配器可能绕过 AOP 代理，导致监听器方法上的 `@Transactional` 不生效。当前 workaround：将事务逻辑抽取到独立的 `TextbookService.finalizeDeletion()` 方法中。

`TextbookParsedEventListener` 也存在类似问题——`saveFailReason()` 标注 `@Transactional`，但被同类的非事务方法调用，也可能因自调用绕过代理。

### 5. 缺乏统一的事务策略

各模块的事务使用方式不一致：

| 模块 | 事务方式 | 问题 |
|------|---------|------|
| `TextbookServiceImpl.parse()` | `@Transactional` + 4 次 `saveAndFlush()` | 长事务 + 假可见性 |
| `ConstructionServiceImpl.extract()` | `@Transactional` + 3 次 `saveAndFlush()` + Neo4j 不受保护 | 跨存储无协调 |
| `FusionServiceImpl` | `TransactionTemplate(Neo4jTM)` | 独立的 Neo4j 事务，与 JPA 无协调 |
| `GradeUploadService.upload()` | `@Transactional` + 事件发布在事务内 | Neo4j 监听器操作在事务外 |
| `QueryServiceImpl` | 3 个 `@Transactional` 私有方法 | 自调用可能绕过代理 |
| `GradeServiceImpl.deleteByExamNo()` | `@Transactional` + 物理删除 | 删除后事件发布在事务内 |

### 6. 具体事务 Bug

- **`GraphConstructedEventListener.onGraphConstructed()`** 是同步 `@EventListener`，join 发布方 `ConstructionServiceImpl.extract()` 的 JPA `@Transactional`（PROPAGATION_REQUIRED）。其内部调用 `FusionServiceImpl.fuseIncremental()` 使用 `TransactionTemplate(Neo4jTM)` 开启**独立的 Neo4j 事务**。Neo4j 事务提交后，外层 JPA 事务若因后续操作失败回滚，Neo4j 融合结果已不可逆——出现 MySQL 回滚到 EXTRACTING 但 Neo4j 图谱已融合的不一致状态。

- **`FusionServiceImpl.updateLogFailed()`** 将 status 设为 `"COMPLETED"` 而非 `"FAILED"`（第 228 行），疑似笔误或设计遗留。

- **`GradeUploadService.upload()`** 中事件 `GradeUploadedEvent` 在 `@Transactional` 内发布。同步监听器 `GradeGraphEventListener.onGradeUploaded()` 写入 Neo4j 不受 JPA 事务保护。若后续 JPA 事务回滚（虽然当前不会），Neo4j 已写入不可逆。

---

## What（做什么）

### 核心问题：是否需要事务管理？

**是，但需要正确使用。** 事务管理不是简单的"加不加 `@Transactional`"的二元选择，而是需要根据**业务语义**和**存储系统特性**做分类设计。

本 CHANGE 的目标是梳理当前所有事务使用场景，建立明确的事务策略，而非简单地"去掉事务"或"全加事务"。

### 重构方向

#### 方向一：状态更新与长耗时操作分离（解决假可见性问题）

文档处理管线的状态更新应该是**短事务**（只写 MySQL），不应与长耗时操作（MinIO 读取、LLM 调用、Neo4j 写入）混在同一事务中。

**策略**：
- 状态变更（UPLOADED→PARSING、EXTRACTING→EXTRACTED 等）使用独立短事务，立即提交，使状态对外可见
- 长耗时操作（LLM 调用、Neo4j 写入）在事务外执行
- 失败时开启新事务回写失败状态

#### 方向二：明确 MySQL 与 Neo4j 的事务边界（解决跨存储一致性问题）

MySQL 和 Neo4j 不可能在同一分布式事务中（无 XA 支持），必须接受最终一致性。

**策略**：
- JPA `@Transactional` 只包裹纯 MySQL 操作
- Neo4j 操作由 `Neo4jTransactionManager` 独立管理（维持 `FusionServiceImpl` 的 `TransactionTemplate` 模式）
- 跨存储操作序列：① MySQL 短事务（更新状态+提交）→ ② Neo4j 操作（独立事务）→ ③ MySQL 短事务（更新最终状态+提交）
- 任一步失败通过状态机回退 + 补偿机制处理，不追求强一致

#### 方向三：事件发布移到事务外（简化事件模型）

当前 `afterCommit` workaround 的根因是事件发布在 `@Transactional` 内。如果状态更新是短事务（提交后再发事件），就不需要 `afterCommit`。

**策略**：
- 事件在事务提交后发布（移出 `@Transactional` 方法）
- 消除 `TransactionSynchronization.afterCommit()` 手动回调
- `@Async` 监听器天然读到已提交状态

#### 方向四：统一事务注解使用规范

**策略**：
- `@Transactional` 仅放 L2 Service 的 public 方法（维持 CONTEXT.md 规范）
- 私有方法不标注 `@Transactional`（Spring AOP 自调用穿透问题）
- `@EventListener` 方法不标注 `@Transactional`，事务逻辑委托给 Service
- `readOnly = true` 仅用于纯查询方法

### 影响范围

- [x] 影响 `REQUIREMENT.md` — 新增事务策略 AC
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① 分类事务策略（短事务状态更新 vs 无事务长操作）；② 跨存储最终一致性方案；③ 事件发布时机规范
- [x] 影响代码 — 涉及所有 `@Transactional` 方法的审查和可能的拆分重构
- [ ] 影响数据模型 / 迁移 — 无 schema 变更
- [ ] 影响外部 API — 无 API 变更（内部重构）
- [ ] 依赖新模块 — 无新增依赖

### 涉及的既有模块与文件

| 文件 | 当前事务问题 |
|------|-------------|
| `TextbookServiceImpl.parse()` | 长事务 + afterCommit workaround + 4 次 saveAndFlush |
| `TextbookServiceImpl.deleteTextBook()` | afterCommit workaround |
| `TextbookServiceImpl.finalizeDeletion()` | 被 @EventListener 间接调用，事务正确性依赖代理 |
| `ConstructionServiceImpl.extract()` | 长事务 + Neo4j 在 JPA 事务外 + 事件发布在事务内 |
| `FusionServiceImpl.fuseFull/fuseIncremental()` | Neo4j TransactionTemplate 与外围 JPA 事务无协调 |
| `FusionServiceImpl.updateLogFailed()` | status = "COMPLETED" 疑似笔误 |
| `GradeUploadService.upload()` | 事件发布在 @Transactional 内 |
| `GradeServiceImpl.deleteByExamNo()` | 事件发布在 @Transactional 内 |
| `GradeGraphEventListener` | 同步监听器，Neo4j 不受 JPA 保护 |
| `GraphConstructedEventListener` | join JPA tx，内含独立 Neo4j TransactionTemplate |
| `TextbookParsedEventListener` | @Transactional 自调用 + @Async |
| `TextbookGraphClearedEventListener` | 通过代理委托规避 AOP 穿透 |
| `QueryServiceImpl` 3 个 @Transactional 方法 | 私有方法 @Transactional 可能不生效 |
| `TextbookUploadService.upload()` | MinIO + DB insert 在同一 @Transactional 内 |

### 核心设计约束（进入 DESIGN 前必须遵守）

- **不引入分布式事务框架**（XA/Atomikos/Seata）：太重，与项目规模不匹配
- **接受 MySQL + Neo4j 最终一致性**：不强求跨存储原子性，通过状态机 + 补偿实现
- **状态变更必须在独立短事务中立即提交**：使前端轮询能看到中间状态，满足 `frontend-status-flow` 的进度展示需求
- **长耗时操作（LLM/MinerU/MinIO）不在事务内**：避免长事务锁表、undo log 膨胀
- **事件发布统一移到事务外**：消除 `afterCommit` workaround
- **`@Transactional` 仅放 public 方法**（维持 CONTEXT.md 规范），私有方法通过 public 代理调用
- **全局删除约束 C1-C5 不受影响**：删除的事务策略可能调整，但级联删除的顺序和幂等语义保持不变
- **既有功能等价回归**：重构不改变各模块的外部行为（文档上传/解析/抽取/融合/删除、成绩上传/查询/删除、智能问答），仅改变内部事务边界

### 范围排除（这次不做）

- ❌ **引入分布式事务框架**（Seata/Atomikos/XA）：项目规模不需要
- ❌ **引入消息队列做最终一致性**：RabbitMQ 已有但仅作预留，本次不用
- ❌ **Neo4j 迁移到 MySQL 事务内**：技术上不可行，Neo4j 通过 HTTP/Bolt 协议通信，与 JPA 不在同一资源管理器
- ❌ **数据库隔离级别变更**：维持 MySQL InnoDB 默认 REPEATABLE_READ，不降级为 READ_COMMITTED
- ❌ **前端改动**：纯后端重构，前端无感知（除状态轮询时效性可能改善外）
- ❌ **新增监控/告警/分布式追踪**

---

## 验收线（粗粒度，不是 AC）

1. **状态可见性**：文档处理管线的中间状态（PARSING→EXTRACTING→FUSING）在前端轮询中可实时可见（状态更新后 1 秒内其他数据库连接可读到）
2. **长耗时操作无事务**：LLM 调用、MinerU API 调用、MinIO 文件 I/O 不在任何 `@Transactional` 方法内
3. **跨存储最终一致**：MySQL 状态变更 + Neo4j 图写入的失败场景下，状态机正确回退（如 Neo4j 写入失败 → MySQL 状态从 EXTRACTING 回退到 PARSED + failReason）
4. **事件发布在事务外**：所有 `ApplicationEventPublisher.publishEvent()` 调用不在 `@Transactional` 方法内（或使用 `afterCommit` 被消除后的等价机制）
5. **afterCommit 消除**：不再使用 `TransactionSynchronizationManager.registerSynchronization()` 手动回调
6. **@Transactional 规范统一**：所有 `@Transactional` 注解在 public 方法上，私有方法不标注；`@EventListener` 方法不标注，事务委托给 Service
7. **FusionServiceImpl.updateLogFailed 修复**：status 正确设为 "FAILED"
8. **既有测试全量通过**：所有已有单元测试 + 集成测试无回归（行为等价）
9. **无功能变更**：文档/成绩/融合/问答的外部行为不变

## 风险与未知

- **REPEATABLE_READ 下状态可见性的实际行为**：需在 podman MySQL 8.0 环境实测验证——`saveAndFlush()` 后其他连接是否能看到已刷入但未提交的数据。如果 REPEATABLE_READ 确实阻断了可见性，需确认短事务（commit 后）方案在性能上可接受
- **`TextbookServiceImpl.parse()` 拆分复杂度**：当前 66 行方法包含 MinIO 读取 + 解析器链执行 + 状态更新，拆分为多个短事务方法可能改变异常处理语义
- **`GraphConstructedEventListener` 事务边界重设计**：当前 join 发布方 JPA 事务，若改为独立事务需要仔细处理文档状态机的 FUSING→COMPLETED/EXTRACTED 转换
- **`ConstructionServiceImpl.extract()` 拆分**：Neo4j 写入失败后如何补偿已提交的 MySQL EXTRACTING 状态变更
- **性能回归风险**：拆分为多个短事务（每次 commit 触发磁盘写入）可能比原来一个长事务慢
- **`QueryServiceImpl` 私有 @Transactional 方法**：使用 `@Transactional` 自调用可能已不生效，需确认当前行为是否正常

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。