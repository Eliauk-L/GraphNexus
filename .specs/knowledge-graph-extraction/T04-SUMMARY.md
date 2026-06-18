# T04-SUMMARY: 4 类 Neo4j 图节点实体

- **Task ID**: T04
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建 4 个 Neo4j 图节点子类，继承 GraphNode，各自标注 `@Node`：

| 类 | @Node Label | 特有字段 |
|----|-------------|----------|
| DocumentNode | `Document` | mysqlId, name, subject, pageCount |
| EntityNode | `Entity` | entityType(DEFINITION/FORMULA/CONCEPT/EXAMPLE/SOLUTION), name, originalText, pageNumber, metadata |
| KnowledgePointNode | `KnowledgePoint` | name, description, subject, gradeLevel |
| KnowledgeCategoryNode | `KnowledgeCategory` | name, level, parentName |

每个子类构造器自动调用 `super(NodeType.XXX.getLabel())` 设置 nodeType。

## 改动文件

- `src/main/java/com/graphnexus/infrastructure/neo4j/node/DocumentNode.java`
- `src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityNode.java`
- `src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java`
- `src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgeCategoryNode.java`

## verify

```
$ mvn compile -q
（编译通过）
```

## 越界检查

TASK write_files: 4 项 | diff: 4 项 | 越界: 0 ✅

## 完成判定

4 个 @Node 子类编译通过；EntityNode.entityType 支持 5 种枚举值。