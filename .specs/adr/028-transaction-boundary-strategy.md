# ADR-028: 事务边界策略（短事务 / 无事务 / Neo4j 独立 / 事件事务外）

- **日期**: 2026-06-22
- **来源**: `transaction-management-refactor` DESIGN § 1 (D1-D10)
- **状态**: proposed

---

## Context

当前项目后端涉及两个独立存储系统（MySQL + Neo4j），各自有独立的事务管理器（`JpaTransactionManager` @Primary + `Neo4jTransactionManager`）。由于两个事务管理器无法在同一分布式事务中协调（Neo4j 不支持 XA），且长耗时操作（LLM 调用 30s+、MinerU API 调用、MinIO 文件 I/O）被包裹在 `@Transactional` 方法中，导致：

1. **状态可见性缺失**：MySQL InnoDB `REPEATABLE_READ` 下，`saveAndFlush()` 的未提交数据对其他数据库连接不可见，前端轮询无法看到中间处理状态
2. **长事务风险**：慢操作占用数据库连接 + undo log 膨胀
3. **跨存储不一致**：Neo4j 写操作不在 JPA 事务内，但被同步监听器 join 到 JPA 事务中执行，形成嵌套但无协调的伪事务
4. **afterCommit hack**：为延迟事件发布到事务提交后，使用 `TransactionSynchronizationManager.registerSynchronization()` 手动回调

需建立项目级统一的事务边界策略，适用于所有 L2 Service。

---

## Decision

本项目所有 L2 Service 方法遵循以下 6 条事务边界策略：

### 规则 1 · 状态更新短事务

MySQL 状态变更（如文档状态 UPLOADED→PARSING、EXTRACTING→EXTRACTED）使用**独立短事务**立即提交：
- 方法标注 `@Transactional`
- 仅含 MySQL CRUD 操作（≤5 条 SQL）
- 不包含外部 API 调用、Neo4j 写入、事件发布
- 预期执行时间 ≤50ms
- 目的：提交后其他数据库连接（前端轮询）可立即读到新状态

### 规则 2 · 长耗时操作无事务

LLM API 调用、MinerU API 调用、MinIO 文件 I/O **不在 `@Transactional` 方法内**执行：
- 调用时 `TransactionSynchronizationManager.isActualTransactionActive() == false`
- 如果操作失败抛异常，由调用方在新短事务中执行补偿（状态回退 + failReason）

### 规则 3 · Neo4j 独立事务

Neo4j 写操作由 `Neo4jTransactionManager` + `TransactionTemplate` 独立管理：
- 不参与 JPA 事务（不与 `@Transactional` 方法嵌套）
- 与 `FusionServiceImpl` 范式一致：`new TransactionTemplate(neo4jTransactionManager).execute(status -> { ... })`
- Neo4j 事务失败时，由调用方执行 MySQL 补偿事务（见 ADR-029）

### 规则 4 · 事件事务外发布

`ApplicationEventPublisher.publishEvent()` 不在任何 `@Transactional` 方法内调用：
- 发布方方法不标注 `@Transactional`
- 调用方负责确保顺序：先短事务 commit，后 publishEvent
- 禁止使用 `TransactionSynchronizationManager.registerSynchronization()` 手动回调
- `@Async` 监听器在新线程中自然读到已提交数据

### 规则 5 · @EventListener 不标注 @Transactional

`@EventListener` 方法上不标注 `@Transactional`：
- 需要事务时委托给独立 `@Service` public 方法（如 `TextbookService.finalizeDeletion()`）
- 原因：Spring `@EventListener` 适配器可能绕过 AOP 代理导致注解不生效

### 规则 6 · @Transactional 仅放 public 方法

所有 `@Transactional` 注解位于 L2 Service 的 **public** 方法上：
- 私有方法不标注（Spring AOP 自调用穿透）
- 包级私有（package-private）方法不标注

---

## Consequences

### 正面的

- **状态实时可见**：前端轮询（2s 间隔）可在状态变更后下一个轮询周期内看到新状态，支持管线进度指示器
- **无长事务锁**：数据库连接仅在短事务期间持有，慢操作期间释放回池
- **afterCommit 消除**：代码简化，无手动事务同步回调
- **策略一致可审计**：grep + ArchUnit 可验证规则遵守情况
- **跨存储风险显式化**：补偿逻辑（ADR-029）让 Neo4j 失败不再静默

### 负面的

- **多次 commit 开销**：原来 1 个 @Transactional 包裹的方法现在会有 2-3 次 commit，增加 ~50-100ms
- **代码行数增加**：拆分后方法数增加（每个短事务一个方法），但每个方法职责更单一
- **补偿复杂度**：Neo4j 失败后需显式处理 MySQL 回退，而非"反正事务回滚一切还原"
- **重构风险**：现有 13 个文件需要修改，涉及核心处理管线

### 需注意的

- 该策略**不追求** MySQL + Neo4j 的跨存储原子性。接受最终一致性窗口（≤1s）
- 规则 3（Neo4j 独立事务）替代 `CONTEXT.md` 原有 `通过消息队列最终一致` 的措辞——当前实际不使用 MQ 做最终一致，改为同步补偿
- 规则 4（事件事务外）是**约定优于配置**，无编译时强制——依赖代码审查 + ArchUnit 测试保障

---

> 本 ADR 的 6 条规则已同步写入 `CONTEXT.md` 默认行为段。