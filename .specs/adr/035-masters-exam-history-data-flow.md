# ADR-035 · MASTERS 考试历史数据传递路径

- **日期**: 2026-06-22
- **Change**: `diagnosis-subgraph-viz`
- **状态**: accepted

---

## Context

MASTERS 边的 `description` 字段存储了 `TimeDecayStrategy` 生成的考试历史 JSON：

```json
{
  "examCount": 3,
  "lastExamDate": "2025-01-15",
  "details": [
    {"examDate": "2024-09-10", "scoreRate": 0.65, "decayWeight": 0.85},
    {"examDate": "2024-11-20", "scoreRate": 0.72, "decayWeight": 0.90},
    {"examDate": "2025-01-15", "scoreRate": 0.58, "decayWeight": 0.95}
  ]
}
```

前端需要在详情面板中展示此数据（考试历史列表 + 趋势折线图）。

当前 `StudentDiagnosisStrategy.buildResult()` 创建 MASTERS 边时 `description` 参数传 `null`：

```java
edges.add(new GraphEdgeData(studentNode.getId(), kpId, "MASTERS", weight, null));
```

此外，`SubgraphResponse.EdgeVO` 的 API 契约不含 `description` 字段（仅 `sourceNodeId`/`targetNodeId`/`edgeType`/`weight`），即使后端透传了 description，前端也无法通过 EdgeVO 获取。

## Decision

将考试历史 JSON 作为 KP **节点**的 `properties.examHistory` 字段传递（而非扩展 EdgeVO 增加 description 字段）。同时 `properties.weight` 增加 MASTERS 掌握度值。

后端改动（`StudentDiagnosisStrategy.buildResult()`）：
1. 构建 KP 节点的 `GraphNodeData` 时，从 `mastersRows` 取 `description` → 放入 `properties.examHistory`
2. 构建 KP 节点的 `GraphNodeData` 时，从 `kpMasteryMap` 取 `weight` → 放入 `properties.weight`
3. MASTERS 边的 `description` 保持 `null`（考试历史已通过节点传递）

`SubgraphResponse.NodeVO.properties` 是 `Map<String, Object>`，天然兼容新增 key，**零 API 契约变更**。

## Consequences

### 正面

1. **零 API 契约变更**：不需要修改 `SubgraphResponse` record 结构，不需要更新 TypeScript 类型定义（`SubgraphNodeVO.properties` 已是 `Record<string, unknown>`）
2. **前后端可独立开发**：后端先补充 `examHistory` + `weight` 透传，前端按 key 读取，不存在契约同步问题
3. **向后兼容**：旧版前端（不读 `examHistory` key）不受影响；旧版后端（不传 `examHistory`）→ 前端降级展示"暂无历次考试数据"

### 负面

1. **语义歧义**：`examHistory` 本质上是 MASTERS 边的属性（描述学生对该知识点的历次考试表现），放在 KP 节点上语义不够精确。但诊断子图中每个 (Student, KP) 对最多一条 MASTERS 边，歧义无实际影响
2. **替代方案**：扩展 `EdgeVO` 增加 `description` 字段同样可行且语义更准确，但需要：
   - 修改 `SubgraphResponse.EdgeVO` record
   - 更新 `api/types.ts` 的 `SubgraphEdgeVO` interface
   - 更新 `graphAdapter.ts` 的 `transformPruningSubgraph()` 传递 description
   - 改动面更大，风险略高。当前方案更保守

### 未来如需要边级属性

如果未来边的属性传递成为普遍需求（≥3 种边需要透传属性），应升级 `EdgeVO` 增加 `properties: Map<String, Object>` 字段（与 `NodeVO.properties` 对称），届时 `examHistory` 可从节点迁回边。