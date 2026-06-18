# T02-SUMMARY: 图节点/边抽象基类 + 类型注册枚举

- **Task ID**: T02
- **Change ID**: `knowledge-graph-extraction`
- **日期**: 2026-06-13

---

## 做了什么

创建图模型的抽象基础设施（见 ADR-002）：

1. **GraphNode.java** — Neo4j 节点抽象基类
   - `id`（UUID）、`nodeType`（类型标识）、`documentId`（关联文档）、`createdAt`、`properties`
   - 不标注 `@Node`，由子类各自标注独立 label
   - 子类构造器通过 `GraphNode(String nodeType)` 自动生成 ID + 设 nodeType

2. **GraphEdge.java** — Neo4j 边抽象基类
   - `sourceNodeId`、`targetNodeId`、`edgeType`、`createdAt`、`properties`
   - `createdAt` 为后续事件图谱记录事件发生时间预留

3. **NodeType.java** — 节点类型枚举注册中心
   - DOCUMENT / ENTITY / KNOWLEDGE_POINT / KNOWLEDGE_CATEGORY
   - 每个值含 `label`（Neo4j label 名）和 `nodeClass`（Java 类引用）
   - `fromLabel(String)` 静态工厂方法

4. **EdgeType.java** — 边类型枚举注册中心
   - EXTRACTS / REFERENCES / DERIVES / CONTAINS / ALIGNED_TO / BELONGS_TO / CHILD_OF / PREREQUISITE_OF
   - 每个值含 `relationshipType`（Neo4j relationship type 名）
   - `fromType(String)` 静态工厂方法

## 改动文件

- `src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java`（新增）
- `src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java`（新增）
- `src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java`（新增）
- `src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java`（新增）

## verify 输出

```
$ mvn compile -q
（无错误输出，编译通过）
```

## 6 维自查

- **R1 认知过载**：GraphNode/GraphEdge 基类各约 50 行，字段清晰
- **R2 变更传播**：无越界，仅 neo4j/node/ 和 neo4j/edge/ 下的新文件
- **R3 知识重复**：GraphNode 和 GraphEdge 各自独立，无重复
- **R4 偶然复杂**：NodeType.nodeClass 预留为 null，当前不引入反射实例化
- **R5 依赖混乱**：GraphNode/GraphEdge 都在 infrastructure 层，符合 L3 定位；枚举互相引用但均在同层
- **R6 领域扭曲**：字段命名使用图谱领域术语（nodeType/edgeType/documentId）

✅ 沿用既有抽象 grep（R6.4）：
- Neo4j 实体模式：项目首次使用 Neo4j，无既有模式 → 新建（DESIGN 0.5.3 已批准）
- Lombok 风格：沿用 `@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor`（参考 DocumentDO）
- 注释风格：沿用中文注释 + @author Jay

## 越界检查（R6.5）

- TASK write_files：4 项
- 实际 diff 涉及：4 项
- 越界：0 ✅

## 完成判定

GraphNode/GraphEdge 抽象基类 + NodeType/EdgeType 枚举编译通过，子类可继承扩展。