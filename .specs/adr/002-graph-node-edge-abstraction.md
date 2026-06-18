# ADR-002: GraphNode / GraphEdge 抽象层设计

- **状态**: accepted
- **日期**: 2026-06-13
- **关联**: `knowledge-graph-extraction` DESIGN § D2

---

## Context

`knowledge-graph-extraction` 需要将 LLM 抽取结果持久化到 Neo4j。本次落地 4 类节点（DocumentNode、EntityNode、KnowledgePointNode、KnowledgeCategoryNode）和 8 类边。但 CHANGE.md 明确要求：**图节点和边必须做抽象处理**，后续新增节点/边类型（如 QuestionNode、MasteryEdge）只需注册新类型，不应修改核心抽取与持久化链路。

核心争议：如何在 Spring Data Neo4j 7.x 的框架约束下，设计既满足类型安全又满足可扩展性的抽象层？

## Decision

**选择方案**：父类不标注 `@Node`，仅作为 Java 层面的字段容器 + 类型契约；子类各自标注 `@Node("Label")`；通用 Repository 通过 `<T extends GraphNode>` + 动态 Cypher 实现类型无关查询；类型元信息通过 `NodeType`/`EdgeType` 枚举注册。

### 具体设计

```
infrastructure/neo4j/
├── node/
│   ├── GraphNode.java                  ← 抽象基类（不标注 @Node）
│   │   ├── @Id String id              ← SDN 要求有 @Id
│   │   ├── String nodeType            ← 节点类型标识（对应 NodeType.label，如 "Entity"）
│   │   ├── String documentId          ← 关联文档（通用字段）
│   │   ├── LocalDateTime createdAt
│   │   └── Map<String, Object> properties  ← 扩展属性（灵活字段）
│   ├── DocumentNode.java              ← @Node("Document") + name, fileSize...
│   ├── EntityNode.java                ← @Node("Entity") + entityType, originalText, pageNumber...
│   ├── KnowledgePointNode.java        ← @Node("KnowledgePoint") + name, description...
│   └── KnowledgeCategoryNode.java     ← @Node("KnowledgeCategory") + name, parentId...
│
├── edge/
│   ├── GraphEdge.java                  ← 抽象基类（不标注 @RelationshipProperties）
│   │   ├── String sourceNodeId
│   │   ├── String targetNodeId
│   │   ├── String edgeType             ← 关系类型字符串（如 "EXTRACTS"）
│   │   ├── LocalDateTime createdAt     ← 关系创建时间，便于后续事件发生时间扩展
│   │   └── Map<String, Object> properties
│   ├── ExtractsEdge.java              ← @RelationshipProperties + Document→Entity
│   ├── ReferencesEdge.java            ← @RelationshipProperties + Entity→Entity (type: DERIVES|CONTAINS|REFERENCES)
│   ├── AlignedToEdge.java             ← @RelationshipProperties + Entity→KnowledgePoint
│   ├── BelongsToEdge.java             ← @RelationshipProperties + KnowledgePoint→KnowledgeCategory
│   ├── ChildOfEdge.java               ← @RelationshipProperties + KnowledgeCategory→KnowledgeCategory
│   └── PrerequisiteEdge.java          ← @RelationshipProperties + KnowledgePoint→KnowledgePoint (strength, description)
│
├── registry/
│   ├── NodeType.java                  ← 枚举：ENTITY, KNOWLEDGE_POINT, KNOWLEDGE_CATEGORY, DOCUMENT + label映射
│   └── EdgeType.java                  ← 枚举：EXTRACTS, REFERENCES, ALIGNED_TO... + relationshipType映射
│
└── repository/
    └── GraphNodeRepository.java       ← <T extends GraphNode> 通用仓库
```

### 通用 Repository 伪代码

```java
// GraphNodeRepository.java — 类型无关的通用图仓库
public class GraphNodeRepository {
    private final Neo4jTemplate neo4jTemplate;

    // 保存任意 GraphNode 子类
    public <T extends GraphNode> T save(T node) {
        return neo4jTemplate.save(node);
    }

    // 按文档 ID 查找所有节点（不关心具体子类）
    public List<GraphNode> findByDocumentId(String documentId) {
        return neo4jTemplate.findAll(
            "MATCH (n) WHERE n.documentId = $docId RETURN n",
            Map.of("docId", documentId),
            GraphNode.class  // 返回基类，调用方自行 instanceof 判断
        );
    }

    // 按文档 ID 删除所有节点和边
    public void deleteByDocumentId(String documentId) {
        neo4jTemplate.query(
            "MATCH (n {documentId: $docId}) DETACH DELETE n",
            Map.of("docId", documentId)
        );
    }
}
```

### 类型注册枚举

```java
public enum NodeType {
    ENTITY("Entity", EntityNode.class),
    KNOWLEDGE_POINT("KnowledgePoint", KnowledgePointNode.class),
    KNOWLEDGE_CATEGORY("KnowledgeCategory", KnowledgeCategoryNode.class),
    DOCUMENT("Document", DocumentNode.class);
    // 后续新增：QUESTION("Question", QuestionNode.class)
}
```

### 扩展流程（未来的开发者视角）

假设要新增 `QuestionNode`：
1. 创建 `QuestionNode extends GraphNode`，标注 `@Node("Question")`
2. 在 `NodeType` 枚举中加一行 `QUESTION("Question", QuestionNode.class)`
3. 在 LLM Prompt 的 JSON Schema 中注册新的 entityType 枚举值
4. 完成——无需修改 `GraphNodeRepository`、`GraphService`、`GraphController` 的任何代码

## Consequences

**正向**：
- 父类 `GraphNode` 不标注 `@Node`，子类各自拥有独立 label，符合 Neo4j 的 label 语义（不同节点类型天然就是不同 label）
- 通用 Repository 面向基类编程，新增子类零改动
- `NodeType` 枚举充当类型注册中心，所有支持的节点类型在一处可见
- `properties` Map 字段提供临时扩展能力——新增字段可以先用 Map 存，后续再正式升级为子类的 `@Property` 字段

**负向**：
- 查询返回 `GraphNode` 基类后，调用方需要 `instanceof` 或访问 `nodeType` 枚举来区分具体子类——这是面向抽象的必要代价
- `properties` Map 字段削弱了类型安全：编译器无法校验 Map 中的 key 是否合法。约束：`properties` 仅用于 LLM 抽取出的非固定字段（如 EntityNode 的 `metadata`），核心字段必须在子类中显式定义
- SDN 7.x 的 `Neo4jTemplate.save()` 对基类的序列化行为可能需要实测验证——如果 SDN 按运行时类型而非声明类型序列化，`save(GraphNode)` 将正确工作；否则需要显式 cast 到子类

**被否决的方案**：

| 方案 | 否决理由 |
|:--|:--|
| 父类标注 `@Node` + 子类继承 | SDN 7.x 会将父类 `@Node` 的 label 应用于所有子类，导致 EntityNode 和 KnowledgePointNode 共享 `GraphNode` label——这不符合 Neo4j 的独立 label 设计 |
| 每个节点类型独立的 Repository | 新增节点类型需要新建 Repository 类 + Service 中新增注入——违反"新增类型不改核心链路"的约束。NodeType 枚举一盘就够 |
| 不用 SDN，纯 Cypher 模板 | v1 过度灵活，代码膨胀。后续需要复杂图遍历算法时再引入 Cypher template 作为补充，SDN 仍用于基本的 CRUD |

---

> 推翻本 ADR 的触发条件：① SDN 7.x 实测发现 `save(GraphNode)` 无法正确序列化子类字段；② 需要跨节点类型的复杂图遍历（如 PREREQUISITE_OF 的多跳追溯）且 SDN 无法表达，需切换到纯 Cypher。

---

## 实现偏差（DEV 阶段）

### 节点持久化

原设计的 `Neo4jTemplate.save(node)` 在实际运行中遇到两个问题：
1. SDN 7.x 的 `@Node(primaryLabel, labels)` 多 label 注解导致 `Neo4jTemplate.save()` 抛出 NPE
2. SDN 无法将 Cypher MERGE 创建的节点通过 `findAll(cypher, GraphNode.class)` 反序列化为抽象父类实例

**实际方案**：
- `save()` 使用 `Neo4jClient.query("MERGE (n:Document {id: \$id}) SET n = \$props")` 手动 Cypher
- `findByDocumentId()` 使用 `Neo4jClient.query().fetch().all()` → `org.neo4j.driver.types.Node.asMap()` 手动提取属性
- 节点查询结果以 `SimpleGraphNode`（匿名内部类）实例返回

### @Node 注解

原设计的 `@Node(primaryLabel = "Document", labels = {"GraphNode"})` 回退为 `@Node("Document")`，不再使用公共 label。原因：SDN 7.x 的 `labels` 属性在实际保存时触发 NPE。

### GraphEdge 扩展

实际实现中 `GraphEdge` 增加了两个通用属性：
- `Double weight`（默认 1.0）— 为后续事件边（如 MasteryEdge）预留
- `String description` — 说明边存在的原因；`PrerequisiteEdge.strength` 同步映射到 `weight`

### ReferencesEdge 拆分

原设计用 `ReferencesEdge(referenceType)` 一个类通过字段区分 DERIVES/CONTAINS/REFERENCES 三种边类型。实际实现拆为三个独立类：`DerivesEdge`、`ContainsEdge`、`ReferencesEdge`，与 EdgeType 枚举一一对应。