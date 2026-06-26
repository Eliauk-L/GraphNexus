# ADR-019: Subject 节点化数据模型

- **状态**: accepted
- **日期**: 2026-06-20
- **来源**: `graph-construction-refactor` DESIGN § D4

---

## Context

当前 Neo4j 中学科信息以 `String subject` 属性存储在各节点（KnowledgePointNode、ExamNode、FileNode）。融合分组依赖 `groupBy(subject string)` 精确匹配：

- `"数学"` 和 `"高中数学"` 被错误分入不同融合组
- 一个 KP 只能属于一个学科（属性 1:1）
- 学科无层级关系

## Decision

将 subject 从节点属性改为独立 SubjectNode + BELONGS_TO_SUBJECT 关系边：

```
BEFORE:
  (kp:KnowledgePoint {name:"二次函数", subject:"数学"})

AFTER:
  (s:Subject {name:"数学"})
  (kp:KnowledgePoint {name:"二次函数"}) -[:BELONGS_TO_SUBJECT]-> (s)
```

### 关键设计点

1. **SubjectNode** — Label `:Subject`，唯一属性 `name`（唯一标识），extends GraphNode
2. **BELONGS_TO_SUBJECT 边** — 有向边，支持从 KP/Exam/FileNode 导航到 Subject
3. **MERGE 策略** — 创建/查找 SubjectNode 时使用 `MERGE (s:Subject {name: $name})`，保证同名字符串只创建一个 Subject 节点
4. **融合分组** — 从 `groupBy(kp.subject)` 改为按 `BELONGS_TO_SUBJECT` 边指向的 Subject 节点引用分组
5. **存量迁移** — 幂等 Cypher 脚本：
   ```cypher
   // 1. 创建 SubjectNode（从现有 subject 属性）
   MATCH (n) WHERE n.subject IS NOT NULL
   MERGE (s:Subject {name: n.subject})
   // 2. 创建 BELONGS_TO_SUBJECT 边
   MATCH (n) WHERE n.subject IS NOT NULL
   MATCH (s:Subject {name: n.subject})
   CREATE (n)-[:BELONGS_TO_SUBJECT]->(s)
   // 3. 移除 subject 属性
   MATCH (n) WHERE n.subject IS NOT NULL
   REMOVE n.subject
   ```
6. **MySQL 保留** — `document.subject` 和 `exam_record.subject` 字段不删
7. **v2 预留** — Subject 树形层级 `(:Subject)-[:CHILD_OF]->(:Subject)`

## Consequences

### 正面

- 融合分组永远正确（同一 Subject 节点的 KP 在同一组）
- 支持 v2 多学科归属 + 学科层级
- 学科成为一等公民节点，可挂载元数据（如学科描述、图标）
- 消除字符串拼写不一致的问题

### 负面

- 存量迁移 one-shot 成本（需备份 Neo4j）
- 所有按 subject 过滤的 Cypher 查询需改写为关系遍历
- KnowledgePointNode / ExamNode / FileNode 构造器变更（breaking API change）
- 新增 Neo4j 索引 `CREATE INDEX subject_name IF NOT EXISTS FOR (s:Subject) ON (s.name)`
