# T06-SUMMARY: GraphNodeRepository 通用图仓库

- **Task ID**: T06
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建 `GraphNodeRepository.java`，面向 GraphNode/GraphEdge 抽象基类编程：

| 方法 | 实现方式 | 说明 |
|------|----------|------|
| `save(T)` | Neo4jTemplate.save() | SDN 自动映射节点 |
| `saveAll(List<T>)` | 遍历 save() | 批量保存 |
| `saveEdge(GraphEdge)` | Neo4jClient.query(CREATE) | Cypher 显式创建关系 |
| `saveAllEdges(List)` | 遍历 saveEdge() | 批量保存边 |
| `findByDocumentId(String)` | Neo4jTemplate.findAll(Cypher) | MATCH WHERE documentId |
| `findEdgesByDocumentId(String)` | Neo4jClient.query().fetch() | RETURN sourceNodeId/targetNodeId/edgeType |
| `deleteByDocumentId(String)` | Neo4jClient.query(DETACH DELETE) | 级联删除节点+边 |

## 改动文件

- `src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java`（新增）

## verify

`mvn compile -q` — 通过

## 越界检查

TASK write_files: 1 项 | diff: 1 项 | 越界: 0 ✅

## 完成判定

Repository 提供 save/saveAll/saveEdge/findByDocumentId/deleteByDocumentId 5 类方法，编译通过。