# ADR-010: SubgraphPruningStrategy 接口 + StudentDiagnosisStrategy 分步 Cypher 剪枝

## Context

`intelligent-qa` 需要从宽图谱中按任务类型裁剪出最小够用子图，v1 仅支持 `STUDENT_DIAGNOSIS` 一种意图。未来会扩展更多意图（KP_ANALYSIS、CLASS_OVERVIEW、PREREQUISITE_CHAIN 等）。

核心问题：
1. 如何设计策略接口使其对 v1 够用、对 v2 可扩展？
2. StudentDiagnosisStrategy 的剪枝算法：单次巨型 Cypher vs 分步查询？

## Decision

### D1: SubgraphPruningStrategy 接口设计

```java
public interface SubgraphPruningStrategy {
    PrunedSubgraph prune(PruningRequest request);
}

// PruningRequest: intent, entityId, subject, params(Map<String,Object>)
// PrunedSubgraph: nodes(List<GraphNode>), edges(List<GraphEdge>), meta(PruningMeta)
```

策略注册方式：QueryService 内部 switch-case（非 Spring Bean 自动发现），v1 只有 1 个 case。新增意图加 case 即可。

### D2: StudentDiagnosisStrategy 分步 Cypher

分 4 步查询，结果在 Java 层合并去重：

1. **确认学生存在** + 获取属性：`MATCH (s:Student {studentNo: $studentNo}) RETURN s`
2. **找到薄弱点**：`MATCH (s)-[m:MASTERS]->(kp:KnowledgePoint {subject: $subject}) WHERE m.weight < $threshold RETURN s,m,kp`；若 MASTERS 不存在则降级查 TESTED 路径
3. **展开前置依赖链**（≤ maxHops 跳）：`MATCH (kp:KnowledgePoint)-[:PREREQUISITE_OF*1..{maxHops}]->(pre:KnowledgePoint) WHERE kp.id IN $weakKpIds RETURN kp.id AS fromId, pre, length(path) AS hops`
4. **补全前置 KP 的掌握度**：`MATCH (s:Student {id: $sid})-[m:MASTERS]->(kp:KnowledgePoint) WHERE kp.id IN $preKpIds RETURN m,kp`

选择分步而非单次巨型 Cypher的理由：
- 每步查询独立可调试、可单独加日志和耗时统计
- 避免 OPTIONAL MATCH 在多分支路径下的笛卡尔积重复行
- 总 round-trip ≤ 4 次，学生诊断场景延迟可接受（< 500ms DB 时间）
- MASTERS 降级只需替换第 2 步查询，其余步骤不变

## Consequences

### 正面
- 策略接口简单，`PruningRequest`/`PrunedSubgraph` 对多种意图通用（v2 新增意图无需改接口契约）
- 分步 Cypher 每步 ≤ 50 行，可读性远优于单次 200 行巨型查询
- 降级逻辑局部化（仅步骤 2），不影响其他步骤

### 负面
- switch-case 注册在意图 > 10 种后会变得臃肿，届时需重构为策略工厂 + Map 自动注入
- 分步查询意味着 Neo4j 连接在 Java 层串行持有，比单次查询多占用连接时间（毫秒级，可忽略）
- `PruningMeta` 的结构目前是自由 Map，v2 可能需要正式 schema

### 风险缓解
- maxPrerequisiteHops 通过配置限制在 ≤ 3，防止深度遍历
- 每步 Cypher 加 `LIMIT 200` 保护，防止意外大规模结果集
- `PruningMeta` 中记录 `mastersAvailable: boolean`，前端可据此展示数据质量提示