# ADR-033: 指标学科过滤策略 — 结果层后置过滤

- **日期**: 2026-06-22
- **状态**: proposed
- **关联**: `knowledge-graph-viz-enhance` DESIGN D3, R2

---

## Context

现有 `MetricsService.queryPageRank` 和 `queryDegree` 接受 `nodeTypes` + `edgeTypes` 参数，查询全图的 GDS 指标结果。REQUIREMENT AC-3 要求按学科过滤指标结果（`subject` 参数），仅返回指定学科的 KnowledgePoint 节点数据。

需要决定如何实现学科过滤：在 GDS 投影阶段过滤（缩小计算范围），还是在 GDS 全图计算后过滤结果集。

## Decision

**选 B：结果层后置过滤**。

### 方案 A · GDS 投影层过滤（被拒绝）

在 `GdsAdapter.projectGraph()` 的 node projection 中加入 Cypher 子查询，过滤出属于指定 Subject 的节点：

```cypher
CALL gds.graph.project(
  'graph-name',
  {
    KnowledgePoint: {
      label: 'KnowledgePoint',
      properties: {},
      filter: 'n.id IN [MATCH (n)-[:BELONGS_TO_SUBJECT]->(:Subject {name: "数学"}) | n.id]'
    }
  },
  ...
)
```

缺点：
- GDS `graph.project` 的 `filter` 参数语法有限制，复杂子查询可能不支持或性能差
- 每个学科需独立投影 → 每个学科独立缓存 → N 个学科 = N 次 GDS 计算（计算开销 ×N）
- 修改 `GdsAdapter` 接口（增加 `subjectName` 参数）→ 破坏其"只负责 Cypher 执行"的简洁契约

### 方案 B · 结果层后置过滤（选定）

流程：
```
MetricsService.queryDegree(nodeTypes, edgeTypes, subjectName)
  1. 调用现有 queryDegree(nodeTypes, edgeTypes)
     → 从 Caffeine 缓存获取全图计算结果（或触发一次 GDS 计算）
  2. 若 subjectName != null:
     → 查询 ConstructionGraphRepository.findKpIdsBySubject(subjectName)
     → 过滤结果：只保留 nodeId ∈ subjectKpIds && nodeType == 'KnowledgePoint' 的条目
  3. 返回过滤后的 List<MetricResultBO>
```

**理由**:
1. **GdsAdapter 零改动**：保持其接口稳定，不引入业务维度
2. **跨学科共享缓存**：全图 GDS 结果缓存一次，所有学科复用同一个缓存条目。3 个学科 = 1 次 GDS 计算 vs 方案 A 的 3 次
3. **过滤开销极小**：`findKpIdsBySubject` 是简单 Cypher（约 5ms），Java 内存过滤 O(n) 在 ≤2000 节点时可忽略
4. **PR 已确立**：此模式与 `graph-metrics` 的 `constrainEdgeTypes()` 思路一致——在 Service 层收敛结果，不推给下层

**代价**:
- 全图 GDS 计算比学科级投影稍重（首次请求）、后续命中缓存
- `findKpIdsBySubject` 是额外一次 Neo4j 往返（但 < 10ms）
- 极端场景（全图 10000+ KP，仅需 1 个学科的 50 个 KP）计算浪费明显——v2 可改为：当 `subjectKpCount < totalKpCount * 0.1` 时自动切换为方案 A

### 实现细节

```java
// MetricsServiceImpl 新增方法
public List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName) {
    // 1. 全图计算（命中缓存）
    List<MetricResultBO> fullResults = queryDegree(nodeTypes, edgeTypes);
    // 2. subject 过滤
    if (subjectName == null || subjectName.isBlank()) return fullResults;
    Set<String> subjectKpIds = constructionGraphRepository.findKpIdsBySubject(subjectName);
    return fullResults.stream()
        .filter(r -> subjectKpIds.contains(r.nodeId()))
        .collect(Collectors.toList());
}
```

缓存 key **不包含** `subjectName`：全图结果缓存一次，subject 过滤在上层完成，避免缓存碎片化。

## Consequences

- **正面**: 不改 GdsAdapter、不改缓存 key 结构、学科数量增长时缓存效率更高
- **正面**: `MetricsService` 原有无参方法保留不变，向后兼容
- **负面**: 全图计算开销恒定，与请求学科数无关——若图规模持续增长（> 5000 KP），需切换到方案 A
- **负面**: 过滤时需额外查询 subject KP ID 集合——通过 `QueryGraphRepository` 的轻量查询解决（不创建新 Repository 方法也可以，`findDistinctSubjects` 已就绪但需要的是 KP ID 列表而非学科名列表）
- **监控点**: 在生产环境观察 `GDS 计算耗时` 和 `缓存命中率` 指标，若 p99 > 5s 或命中率 < 50% 则触发方案 A 迁移