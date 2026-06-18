# ADR-008: 融合日志驱动回滚机制

- **日期**: 2026-06-15
- **状态**: accepted
- **来源**: `wide-graph-fusion` DESIGN

---

## Context

KP 融合是不可逆操作——融合后源 KP 节点被 DETACH DELETE，边被重定向。如果融合结果异常（模糊匹配误合了不相关的 KP），需要能恢复到融合前的 Neo4j 图状态。

传统方案（Neo4j dump/restore）粒度过粗——恢复一次融合会影响所有不相关数据。需要一种**精确到单次融合操作**的回滚机制。

## Decision

### 融合日志记录完整快照

`fusion_log` 表含两个 JSON 列，记录融合前后的图状态差异：

**fusion_detail_json** — 融合明细（回滚时用于重建源 KP 和原始边）：

```json
[
  {
    "groupId": 1,
    "sourceKpIds": ["uuid-kp-a", "uuid-kp-b"],
    "sourceKpProperties": [
      {"id": "uuid-kp-a", "name": "二次函数顶点坐标", "subject": "数学", "documentId": "1", "fusionSource": "DOCUMENT", ...},
      {"id": "uuid-kp-b", "name": "顶点坐标公式", "subject": "数学", "documentId": null, "fusionSource": "CSV_IMPORT", ...}
    ],
    "targetKpId": "uuid-kp-a",
    "redirectedEdges": [
      {"type": "ALIGNED_TO", "fromId": "uuid-kp-b", "toId": "uuid-entity-1", "direction": "INCOMING"},
      {"type": "TESTED", "fromId": "uuid-exam-1", "toId": "uuid-kp-b", "direction": "INCOMING"}
    ]
  }
]
```

**masters_snapshot_json** — MASTERS 变更快照（回滚时用于恢复旧权重）：

```json
[
  {
    "studentNo": "S2024001",
    "kpName": "对称轴",
    "oldWeight": 0.35,
    "newWeight": 0.55
  },
  {
    "studentNo": "S2024001",
    "kpName": "顶点坐标",
    "oldWeight": null,
    "newWeight": 0.20
  }
]
```

> `oldWeight = null` 表示融合前不存在该 MASTERS 边——回滚时 DELETE 该边（而非 UPDATE 到 null）。

### 回滚流程

```
1. 查 fusion_log → 不存在 → A0012
2. 校验当前 Neo4j 状态与快照一致性：
   ├─ 规范 KP 节点必须存在（targetKpId）
   ├─ MASTERS 边 weight 必须 ≈ newWeight（容差 ±0.01）
   └─ 任一校验失败 → A0014 "图状态已变更，无法回滚"
3. 逆向恢复（一个 Neo4j 事务）：
   ├─ DETACH DELETE 所有 targetKpId 节点
   ├─ CREATE 所有 sourceKp 节点（含原始属性，从 sourceKpProperties 还原）
   ├─ CREATE 所有被重定向的边（从 redirectedEdges 逆向：原方向 INCOMING → 指回源 KP）
   ├─ for each masters_snapshot:
   │   ├─ oldWeight == null → DELETE MASTERS 边
   │   └─ oldWeight != null → SET r.weight = oldWeight
4. 标记 fusion_log.rolled_back = true
5. 幂等：已回滚的日志再次回滚 → 直接返回成功（校验通过后不重复恢复）
```

### yml 配置

```yaml
fusion:
  rollback:
    weight-tolerance: 0.01   # MASTERS weight 校验容差
```

## Consequences

### 优点

- **精确回滚**：仅恢复被本次融合影响的节点和边，不影响图的其他部分
- **可审计**：fusion_log 提供完整的操作历史，任意时间点可查"谁在何时融合了什么"
- **安全校验**：回滚前验证当前状态与快照一致，防止基于过期快照破坏数据
- **幂等**：重复回滚无副作用

### 缺点

- **快照 JSON 体积**：千级 KP 融合时 JSON 可能达 MB 级。MEDIUMTEXT（16MB）仍可承载，但 MySQL 行过大可能影响 InnoDB 页效率
- **外部修改阻断回滚**：融合后若有其他操作修改了相关 KP 或 MASTERS 边，一致性校验会拒绝回滚。此时需人工介入
- **不跨多次融合回滚**：若用户融合了 3 次（F001→F002→F003），想回滚到 F001 前状态，必须按 F003→F002→F001 顺序逐个回滚（或手动全量重建）

### 升级路径

- 多步回滚（一次回滚到指定历史点）：需 fusion_log 记录累积快照（v2）
- 回滚预览（dry-run）：回滚前返回将变更的节点/边列表（v2）

---

> 本 ADR 与 ADR-009（自动增量融合钩子）配合：增量融合的日志同样支持回滚，保证自动和手动融合的恢复能力一致。