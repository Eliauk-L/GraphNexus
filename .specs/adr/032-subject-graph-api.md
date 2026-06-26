# ADR-032: 学科全景图 API 设计

- **日期**: 2026-06-22
- **状态**: proposed
- **关联**: `knowledge-graph-viz-enhance` DESIGN D1, D2, D8

---

## Context

当前图谱可视化页面仅支持按文档 ID 加载子图（`GET /api/v1/graph/construction/document/{documentId}`）。用户需求是按学科查看所有知识点的聚合图。

两个新 API 端点被提出：
1. 学科列表 — 供前端学科选择器下拉框使用
2. 学科全景图 — 返回指定学科下所有 KP + PREREQUISITE_OF + CHILD_OF 子图

需要决定端点路径、返回格式、查询策略。

## Decision

### D1 · 端点路径

- **学科列表**: `GET /api/v1/graph/subjects` → 返回 `ApiResult<List<String>>`
- **学科全景图**: `GET /api/v1/graph/construction/subject/{subjectName}` → 返回 `ApiResult<GraphSubgraphVO>`

**理由**:
- 学科列表是跨模块的图谱元数据查询，URL 放在 `/graph/subjects` 短路径下，不归属 `/construction/`（它不是构建产物，而是 Neo4j 中的 Subject 节点枚举）
- 学科全景图与文档子图（`/construction/document/{id}`）对称，URL 结构一致：`/construction/<维度>/<标识>`
- 两者均放在 `ConstructionController` 中实现（避免新建单方法 Controller），但 URL 前缀不同

### D2 · 返回格式

复用现有 `GraphSubgraphVO` 结构（`nodes: GraphNodeVO[]` + `edges: GraphEdgeVO[]`），不新建 VO。

**理由**:
- 学科全景图的节点/边语义与文档子图一致（都是 Neo4j 节点+关系边），无需新结构
- 前端 `graphAdapter.ts` 的 `transformDocumentSubgraph` 可直接处理学科图数据，零适配成本
- `GraphNodeVO.properties` Map 携带 `name`、`description`、`subject` 等业务字段

### D3 · Neo4j 查询策略

单 Cypher 查询，模式：

```cypher
MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name: $subjectName})
OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp:KnowledgePoint)
OPTIONAL MATCH (kp)-[:CHILD_OF]->(cat:KnowledgeCategory)
OPTIONAL MATCH (cat)-[:CHILD_OF]->(parentCat:KnowledgeCategory)
WITH collect(DISTINCT kp) + collect(DISTINCT nextKp) + collect(DISTINCT cat) + collect(DISTINCT parentCat) AS allNodes
UNWIND allNodes AS n
WITH DISTINCT n WHERE n IS NOT NULL
RETURN n ORDER BY labels(n)[0]
LIMIT 1000
```

**理由**:
- 与现有 `findByDocumentId` 的 OPTIONAL MATCH + DISTINCT 模式一致
- 一次往返拿全子图，减少 N+1 风险
- `LIMIT 1000` 防护极端情况（预防 R1 风险）

**代价**:
- 需在 `ConstructionGraphRepository` 中新增方法（约 40 行），不改变现有方法
- 边查询需另写（参照 `findEdgesByDocumentId` 模式），或在同一查询中 COLLECT 边关系

## Consequences

- **正面**: 前端无需学习新数据结构；学科全景图与文档子图共用 `GraphSubgraphVO` 渲染管线
- **正面**: URL 结构与现有端点对称，API 文档清晰
- **负面**: 若某学科 KP 超过 1000，`LIMIT` 会截断——此时需分页（v2）
- **负面**: 学科全景图不包含 EntityNode（文档原文片段），因为学科视图是跨文档的 KP 骨架——若后续需要 Entity→KP 的对齐视图，需新端点
- **注意**: 实现时 `BELONGS_TO_SUBJECT` 边覆盖率需验证——若部分 KP 未关联 Subject，学科全景图将遗漏这些节点