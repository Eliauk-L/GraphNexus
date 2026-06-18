# ADR-009: 自动增量融合的触发位置与容错策略

- **日期**: 2026-06-15
- **状态**: accepted
- **来源**: `wide-graph-fusion` DESIGN

---

## Context

REQUIREMENT AC-6 要求新文档抽取或新 CSV 上传完成后**自动触发增量融合**——仅融合新涉及的 KP + 重算受影响学生的 MASTERS 边。

需要在既有代码中确定钩子位置，并设计容错策略（增量融合失败不应阻塞主流程）。

## Decision

### 钩子位置

**位置 1：`GradeServiceImpl.uploadGradeCsv()` 末尾**

```java
// 位置：Neo4j 图构建完成后（return 语句之前）
// 理由：此时新 KP 已 MERGE、TESTED/ATTENDED 边已创建，融合可直接操作这些新数据
// affectedKpNames = payload.knowledgePoints()（本次 CSV 中出现的所有 KP 名称）
try {
    fusionService.fuseIncremental(payload.knowledgePoints(), payload.subject());
} catch (Exception e) {
    log.error("增量融合失败（CSV 上传后），examNo={}, kps={}，可手动全量融合修复",
            payload.examNo(), payload.knowledgePoints(), e);
    // 不抛异常，不阻塞主流程
}
```

**位置 2：`GraphServiceImpl.extract()` 末尾**

```java
// 位置：抽取完成 + Neo4j 写入后（return 语句之前）
// 理由：此时新 KP 已创建、ALIGNED_TO/BELONGS_TO/PREREQUISITE_OF 边已建立
// affectedKpNames = extracted.knowledgePoints().stream().map(KP::getName).toList()
try {
    fusionService.fuseIncremental(affectedKpNames, doc.getSubject());
} catch (Exception e) {
    log.error("增量融合失败（文档抽取后），documentId={}, kps={}，可手动全量融合修复",
            documentId, affectedKpNames, e);
    // 不抛异常，不阻塞主流程
}
```

### 容错策略

```
增量融合失败时：
  ├─ ERROR 日志（含完整上下文：触发来源 + affectedKpNames + 异常堆栈）
  ├─ 不抛异常（主流程已成功，融合是增强操作）
  ├─ 不写 fusion_log（无 RUNNING/FAILED 状态残留）
  └─ 后续手动全量融合自动修复（全量融合忽略历史失败，从头重算）
```

### 增量融合范围控制

`fuseIncremental(kpNames, subject)` 仅处理：

1. **KP 匹配范围**：仅查询 name IN kpNames 的 KP（同 subject），匹配分组仅限于此范围内
2. **MASTERS 重算范围**：仅重算通过 TESTED 边关联到这些 KP 的 Student
   ```cypher
   MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint)
   WHERE kp.name IN $kpNames AND kp.subject = $subject
   RETURN DISTINCT s
   ```
3. **不触发全量重融**：其他 KP 和 Student 不受影响

## Consequences

### 优点

- **零用户感知**：上传/抽取完成后融合自动生效，无需额外操作
- **精确范围**：增量融合仅触碰受影响的数据，性能远优于全量（O(affected) vs O(all)）
- **静默容错**：融合失败不影响主流程（上传/抽取本身已成功），用户可后续手动修复
- **钩子轻量**：每个调用点仅新增 1 个 try-catch 块 + 1 行委托调用

### 缺点

- **Service 耦合**：GradeServiceImpl 和 GraphServiceImpl 各新增对 FusionService 的依赖（构造器注入 +1 参数）。但依赖方向正确（L2 → L2，同层委托，不跨层）
- **静默失败风险**：增量融合失败仅记 ERROR 日志，若运维未监控日志，可能长期未发现 MASTERS 边未更新
- **测试复杂度**：既有 Service 的单元测试需 mock FusionService（或验证调用次数）

### 升级路径

- 增量融合失败的**告警**：接入监控系统（Prometheus metrics 或健康检查端点），暴露 `fusion.incremental.failure.count` 指标（v2）
- MQ 异步：增量融合通过 RabbitMQ 异步执行，彻底解耦主流程（v2）

---

> 本 ADR 与 ADR-008（回滚机制）配合：增量融合同样产生 fusion_log 记录，支持独立回滚。