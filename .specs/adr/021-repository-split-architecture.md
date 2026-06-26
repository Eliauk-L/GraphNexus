# ADR-021: Repository 按模块拆分架构

- **状态**: accepted
- **日期**: 2026-06-20
- **来源**: `graph-construction-refactor` DESIGN § D3

---

## Context

`GraphNodeRepository`（666 行）同时服务 6 个子域：construction（图谱构建）、grade events（成绩事件图谱）、fusion（融合）、MASTERS（掌握度）、intelligent QA（智能问答）、metrics（指标）。修改任意子域的 Cypher 查询都可能影响其他模块。

此次 change 将 construction 模块独立（重命名为 `ConstructionController`），需要对应的数据访问层也独立。

## Decision

按三个 Controller 模块边界，将 `GraphNodeRepository` 拆分为三个独立 Repository，原类删除：

```
GraphNodeRepository (666行, 6子域)
  → DELETE
  → ConstructionGraphRepository  (construction + grade events)
  → FusionGraphRepository        (fusion + MASTERS)
  → QueryGraphRepository         (QA + metrics, 只读)
```

### 方法分配

**ConstructionGraphRepository** (构建 + 成绩事件):
```
save(T extends GraphNode) → MERGE node
saveAll(List<T>)
saveEdge(GraphEdge) → CREATE edge
saveAllEdges(List<? extends GraphEdge>) → UNWIND batch CREATE
findByDocumentId(String) → MATCH nodes by documentId
findEdgesByDocumentId(String) → MATCH edges by documentId
deleteByDocumentId(String) → DETACH DELETE by documentId
deleteEdgesByExamNo(String, String) → DELETE ATTENDED/TESTED edges
deleteExamNode(String) → DETACH DELETE Exam node
```

**FusionGraphRepository** (融合 + MASTERS):
```
findAllKnowledgePoints() → MATCH all KPs
findKnowledgePointsByNamesAndSubject(List, String) → MATCH KPs by names+subject
redirectEdges(String, String) → redirect all edges from KP A to KP B
deleteKnowledgePoints(List<String>) → DETACH DELETE KPs
batchUpsertMastersEdges(String, List<MastersEdgeData>) → UNWIND MERGE MASTERS
findAllStudentsBySubject(String) → MATCH students by subject
findStudentsByKpNamesAndSubject(List, String) → MATCH students by KP names+subject
findStudentsByKpNames(List<String>) → MATCH students by KP names (rollback)
findMastersEdgesByStudent(String) → MATCH MASTERS edges (rollback validation)
deleteIncomingEdges(String, String) → DELETE incoming edges to KP (rollback)
updateNodeProperties(String, Map) → SET node properties (rollback)
createNode(String, Map) → CREATE node (rollback)
deleteMastersEdge(String, String) → DELETE MASTERS edge (rollback)
updateMastersWeight(String, String, double, String) → SET MASTERS weight (rollback)
MastersEdgeData record
```

**QueryGraphRepository** (QA + Metrics, 仅只读查询):
```
findStudentByName(String) → MATCH Student by name
findStudentByNo(String) → MATCH Student by studentNo
findMastersByStudentAndSubject(String, String) → MATCH MASTERS by student+subject
findTestedKpsByStudentAndSubject(String, String) → MATCH TESTED path
findPrerequisitesUpstream(List, int) → MATCH PREREQUISITE_OF chain
findMastersByStudentAndKpIds(String, List) → MATCH MASTERS by student+KP ids
findDistinctSubjects() → MATCH DISTINCT subjects (需适配 Subject 节点化)
```

### 调用方切换

| 调用方 | 旧注入 | 新注入 |
|--------|--------|--------|
| `ConstructionServiceImpl` (was GraphServiceImpl) | GraphNodeRepository | ConstructionGraphRepository + QueryGraphRepository (getSubgraph) |
| `FusionServiceImpl` | GraphNodeRepository | FusionGraphRepository |
| `FusionGroupBuilder` | GraphNodeRepository | FusionGraphRepository |
| `FusionRollbackService` | GraphNodeRepository | FusionGraphRepository |
| `MastersRecalculationService` | GraphNodeRepository | FusionGraphRepository |
| `MetricsServiceImpl` | GraphNodeRepository | QueryGraphRepository |
| `QueryServiceImpl` | GraphNodeRepository | QueryGraphRepository |
| `GradeGraphEventListener` | GraphNodeRepository | ConstructionGraphRepository |
| `TextbookServiceImpl` | GraphNodeRepository | ConstructionGraphRepository |
| `StudentDiagnosisStrategy` | GraphNodeRepository | QueryGraphRepository |

## Consequences

### 正面

- 模块边界清晰：修改融合逻辑不会误改构建查询
- 每个 Repository ≤ 300 行（不再有 666 行上帝类）
- 依赖注入明确了模块依赖关系（grep `GraphNodeRepository` import → 精确知道哪个模块依赖什么）

### 负面

- 10 个调用方需更新 import + 字段名 + 构造器参数
- 如果某个调用方同时需要 construction + fusion 的 Neo4j 操作，需注入两个 Repository（还没有这种情况）
- 原 `GraphNodeRepository` 删除后，已有的外部工具脚本引用会报错
