# ADR-029: 跨存储（MySQL + Neo4j）失败补偿策略

- **日期**: 2026-06-22
- **来源**: `transaction-management-refactor` DESIGN § 1 (D9)
- **状态**: proposed

---

## Context

项目同时使用 MySQL（文档元数据、成绩记录、融合日志）和 Neo4j（图节点、关系边），两者有独立的物理事务管理器。由于 Neo4j 不支持 XA 协议，无法与 MySQL 在同一分布式事务中协调。

当前代码中跨存储操作的典型模式（以文档抽取为例）：
1. MySQL: `saveAndFlush(EXTRACTING)`（`@Transactional` 内）
2. Neo4j: `constructionGraphRepository.save(...)`（**事务外**——因为 `@Transactional` 默认用 JPA TM）
3. MySQL: `saveAndFlush(EXTRACTED)`（`@Transactional` 内）
4. Publish events → 同步监听器 → Neo4j fusion（`TransactionTemplate`）

问题：如果步骤 2 的 Neo4j 写入失败（如 Neo4j 宕机、Cypher 语法错误），步骤 1 的 `EXTRACTING` 和步骤 3 的 `EXTRACTED` 都在同一个 `@Transactional` 中。如果异常从步骤 2 抛出让整个 `@Transactional` 回滚，则状态回到 `PARSED`——这是期望行为。但如果 `@Transactional` 已按 ADR-028 拆分为短事务（步骤 1 已 commit），则 Neo4j 失败时 `EXTRACTING` 已落库且不可回滚。

类似的跨存储不一致风险存在于：文档删除（DELETING 已 commit 但 Neo4j 清理失败）、融合操作（Neo4j fusion 已 commit 但 MySQL fusion_log 写入失败）。

需定义 Neo4j 写入失败时的 MySQL 状态补偿策略，使系统进入已知可恢复状态。

---

## Decision

### 核心原则

接受 MySQL + Neo4j 的**最终一致性**而非强一致。Neo4j 写入失败时，MySQL 状态通过**独立补偿短事务**回退到上一稳定态，并在 `failReason` 中记录失败原因。

### 补偿协议

#### 文档抽取路径（图形化）

```
1. Short Tx: status=EXTRACTING → commit ✓
2. No Tx: LLM extraction
3. Neo4j Tx: phase1_build ──→ 失败 ──→ 4a. Short Tx: status=PARSED + failReason="LLM抽取失败: ..." → commit
                          ──→ 成功 ──→ 4b. Short Tx: status=EXTRACTED → commit
```

#### 文档融合路径（图形化）

```
1. Short Tx: status=FUSING → commit ✓
2. Neo4j Tx: fuseIncremental ──→ 失败 ──→ 3a. Short Tx: status=EXTRACTED + failReason="增量融合失败: ..." → commit
                            ──→ 成功 ──→ 3b. Short Tx: status=COMPLETED → commit
```

#### 补偿规则

1. **补偿回退到上一稳定态**（如 EXTRACTING→PARSED、FUSING→EXTRACTED），不直接跳到 UPLOADED（保留已完成的工作）
2. **failReason 记录异常 message**，截断至 ≤300 字符
3. **补偿事务是独立的 `@Transactional` 方法**，失败只记 log.error（不再嵌套补偿）
4. **用户可通过前端「重新图谱化」按钮从稳定态重试**（`validateDocStatus` 允许 PARSED→EXTRACTING 重入、EXTRACTED→EXTRACTING 重入）
5. **Neo4j 中已写入的部分节点/边由下一次 extract() 的全量覆盖策略清理**（`deleteByDocumentId` 先删旧图再写新图，幂等）

### 适用场景

| 场景 | 补偿方式 | 补偿后状态 |
|------|---------|-----------|
| 文档 LLM 抽取失败 | Short Tx: PARSED + failReason | 用户可手动点「重新图谱化」 |
| 文档 Neo4j phase1 写入失败 | Short Tx: PARSED + failReason | 用户可手动点「重新图谱化」 |
| 文档 Neo4j 增量融合失败 | Short Tx: EXTRACTED + failReason | 用户可手动触发全量融合 |
| 成绩 Neo4j 构建失败 | **v1 仅 log.error**（v2 补补偿） | 需手动处理 |
| 删除 Neo4j 清理失败 | DELETING 状态保留，等待手动重试 | 管理员介入 |

---

## Consequences

### 正面的

- **系统不会卡死**：失败后回退到已知稳定态，而非中间态（如 FUSING 永久卡住）
- **用户可恢复**：前端展示 failReason + 操作按钮（重新图谱化 / 重新融合）
- **简单显式**：补偿逻辑在 try/catch 中清晰可见，不依赖复杂的 Saga 编排框架

### 负面的

- **不是强一致**：在 Neo4j 失败和 MySQL 补偿之间，存在短暂的中间态窗口（≤100ms）
- **残留图数据**：Neo4j 写入部分成功后失败（如 100 个节点写入了 50 个后异常），这些半成品数据会在 Neo4j 中残留直到下次全量 extract() 覆盖清理
- **成绩路径无补偿**（v1 仅文档路径）：成绩图谱构建失败时 MySQL 已提交且无回退，需 v2 补齐

### 需注意的

- 该策略**依赖** `deleteByDocumentId` 的全量覆盖语义（先删旧图再写新图）。如果未来 extract() 改为增量写入，补偿策略需要同步更新
- 补偿事务自身失败（如 DB 宕机）是最坏情况——此时状态卡在中间态，需运维手动修复。概率极低，且 DB 宕机 = 整体不可用，该风险可接受

---

> 补偿协议的具体实现伪代码见 `DESIGN.md` § 2.1 数据流图。本 ADR 仅定义策略语义，不包含实现细节。