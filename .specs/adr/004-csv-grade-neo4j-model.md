# ADR-004: CSV 成绩的 Neo4j 简化三元模型

- **日期**: 2026-06-15
- **状态**: accepted
- **来源**: `csv-grade-import` DESIGN

---

## Context

CSV 成绩文件上传后需要在 Neo4j 中表达"哪个学生在哪次考试中被考查了哪些知识点"。参考文档 `docs/知识图谱构建示例-初中数学教辅.md` 提出了完整的 EventNode 模型：

```
Student -[HAS_EVENT]-> Event -[BELONGS_TO_EXAM]-> Exam
Event -[RELATES_TO]-> KnowledgePoint
```

该模型中每次考试每道题生成一个 Event 节点，携带 `rawScore`/`maxScore`/`eventType` 等属性。这是最细粒度的建模。

但用户明确要求 v1 简化，先不做 EventNode，且**分数全部留在 MySQL**，Neo4j 只存图结构。

## Decision

采用 **简化三元模型 + 分数存 MySQL**：

```
(:Student) -[:ATTENDED]-> (:Exam) -[:TESTED]-> (:KnowledgePoint)
```

- **ATTENDED**：Student → Exam，纯结构边，无属性。表达学生参加了考试
- **TESTED**：Exam → KnowledgePoint，纯结构边，无属性。表达考试考查了某知识点
- **所有分数**（各题得分、总分、班级排名）仅在 MySQL `exam_record` 表中
- 同一 CSV 内所有 Student 共享一个 Exam 节点
- 同一 CSV 内去重后的知识点各一个 KnowledgePoint 节点

### 查询模式

两步组合：
1. **Neo4j**：`MATCH (s)-[:ATTENDED]->(e)-[:TESTED]->(kp)` 获取知识点关联列表
2. **MySQL**：`SELECT score_details FROM exam_record WHERE student_no=? AND exam_no=?` 获取分数
3. 应用层按 kp.name 对齐后分析

## Consequences

### 优点

- **实现简单**：只需新增 2 种节点类 + 2 种边类，GraphNodeRepository 的 `instanceof` 分支增加 2 个
- **查询直观**：`MATCH (s:Student)-[:ATTENDED]->(e:Exam)-[:TESTED]->(kp:KnowledgePoint)` 一条语句拿到全部成绩
- **边数可控**：N 个 Student → N 条 ATTENDED + M 个 KP → M 条 TESTED，总共 N+M 条边（vs 之前 N×M 条）

### 缺点

- **查询需两步**：不能从 Neo4j 单次查询得到分数，需回 MySQL 查 `exam_record`，应用层组合
- **无法从图直接筛选**：如"找 weight<0.6 的知识点"需先查 MySQL 拿到符合条件的学生名单，再回 Neo4j 遍历——但对于 v1 的"分析单个学生某次考试"场景，这完全够用

### 升级路径

当需要更细粒度的分析时，可升级为 EventNode 模型：

1. 新增 `EventNode`（eventType, rawScore, maxScore, questionNo, eventTime）
2. 新增 `HasEventEdge`（Student→Event）、`RelatesToEdge`（Event→KnowledgePoint）、`BelongsToExamEdge`（Event→Exam）
3. 迁移脚本：遍历现有 TESTED 边 → 为每条创建 EventNode + 三条新边 → 删除 TESTED 边（ATTENDED 保留）

迁移代价评估：中等。需要 Cypher 脚本遍历所有 TESTED 边并重建，但 ATTENDED 边和 Student/Exam 节点可保留。

---

> 本 ADR 在升级为 EventNode 模型或被宽图谱融合 change 的 MASTERS 聚合边替代时需重审。