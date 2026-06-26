# ADR-020: 融合原子性策略 — Neo4j 事务包装

- **状态**: accepted
- **日期**: 2026-06-20
- **来源**: `graph-construction-refactor` DESIGN § D6

---

## Context

当前 `FusionServiceImpl.fuseFull()` 按 subject 逐个执行 `merge()` + `recalculateAll()`，每次调用直接提交 Neo4j 操作。若中途失败（如 数学成功、物理失败），已融合的 subject 无法回滚。

`FusionGroupBuilder.merge()` 内部同样逐 group 提交——group[1] 的源 KP 已删除后 group[2] 失败，group[1] 不可恢复。

失败时 `fusionDetailJson` 未写入，`FusionRollbackService` 无法用于补偿回滚。

### 备选方案

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A) Neo4j 事务包装** | 使用 `Neo4jTemplate.transactional()` 包装全量/增量融合的全部 merge + MASTERS 重算 | 全有全无；实现简单；DB 层保证 | 大事务锁时间较长 |
| B) 先记后做补偿回滚 | 先完整写入 fusionDetailJson，执行中失败则通过 FusionRollbackService 回滚 | 每步独立提交，无大事务 | 补偿回滚本身可能失败；需维护两套逻辑 |

## Decision

选择 **方案 A — Neo4j 事务包装**。

### 实现约束

```
伪代码：

FusionServiceImpl.fuseFull() {
    // 1. 预计算所有融合组（在事务外，纯内存计算）
    List<FusionGroup> allGroups = buildAllGroups()

    // 2. 写入 fusionLog（在事务外，MySQL 事务）
    FusionLogDO logEntry = createLogEntry("MANUAL_FULL")

    try {
        // 3. 在 Neo4j 事务内执行全部 merge + MASTERS 重算
        neo4jTemplate.transactional(() -> {
            for (FusionGroup group : allGroups) {
                merge(group)           // redirectEdges + deleteKPs + updateProps
            }
            for (String subject : subjects) {
                recalculateAll(subject)
            }
        })

        // 4. 事务成功后更新日志
        updateLogCompleted(logEntry, detailJson)

    } catch (Exception e) {
        // Neo4j 事务已自动回滚
        updateLogFailed(logEntry)
        throw e
    }
}
```

### 关键约束

- 预计算阶段（build groups + MASTERS 数据收集）在事务**外**完成，减少事务内时间
- 事务内仅执行 Neo4j 写操作（merge + MASTERS upsert）
- Neo4j 事务超时设为可配置（默认 30s，通过 `spring.neo4j.pool.transaction-timeout`）
- 增量融合 `fuseIncremental` 同样包装在 Neo4j 事务中
- `fusionLog`（MySQL）与 Neo4j 事务**分离**——日志先写入 MySQL 事务（记录 RUNNING 状态），Neo4j 事务独立提交

## Consequences

### 正面

- 融合原子性由 Neo4j 原生事务保证，无中间状态
- 失败时无需补偿逻辑，Neo4j 自动回滚
- 融合日志状态准确反映融合结果

### 负面

- 大事务（全量融合 1000+ KPs）可能导致长时间 Neo4j 写锁
- 如果事务超时，整个融合失败——用户需重试（而非部分生效）
- 与当前 `Neo4jClient` 直接运行查询的模式不同，需要使用 `Neo4jTemplate` 或 `TransactionTemplate` 管理事务边界

### 性能回退计划

如果全量融合因大事务频繁超时（> 50% 失败率），回退到 B 方案（先记后做）。
监控指标：融合事务平均耗时、超时次数、事务内 Neo4j 操作次数。
