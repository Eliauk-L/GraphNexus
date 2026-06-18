# T08-SUMMARY: GraphService 接口 + 实现

- **Task ID**: T08
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建 L2 图服务层，4 个文件：

| 文件 | 职责 |
|------|------|
| ExtractionResultBO | 抽取摘要（entityCount/kpCount/categoryCount/edgeCount）|
| GraphSubgraphBO | 子图结果（nodes + edges）|
| GraphService | 接口：extract() + getSubgraph() |
| GraphServiceImpl | 实现：校验→LLM抽取→事务写Neo4j→子图查询 |

**extract() 流程**：
1. DocumentRepository 查 MySQL → 不存在抛 A0006
2. status≠COMPLETED → A0009
3. textContent 为空 → A0008
4. 构建 DocumentNode
5. ExtractionService.extract() → LLM 抽取
6. `@Transactional("neo4jTransactionManager")` 内：deleteByDocumentId → save nodes → saveAllEdges
7. 返回 ExtractionResultBO

**getSubgraph() 流程**：findByDocumentId + findEdgesByDocumentId → GraphSubgraphBO

## 改动文件（4 个新增）

- `model/ExtractionResultBO.java` / `model/GraphSubgraphBO.java`
- `service/GraphService.java` / `service/impl/GraphServiceImpl.java`

## verify

`mvn compile -q` — 通过

## 越界检查

TASK write_files: 4 项 | diff: 4 项 | 越界: 0 ✅

## 完成判定

extract() 含 5 种前置校验 + 事务原子覆盖；getSubgraph() 返回节点+边。