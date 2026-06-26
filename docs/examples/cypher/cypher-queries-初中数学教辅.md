# GraphNexus 初中数学教辅知识图谱 — Cypher 查询语句大全

> 基于 [知识图谱构建示例-初中数学教辅.md](../docs/知识图谱构建示例-初中数学教辅.md) 生成
>
> 日期：2026-06-14 | 生成工具：neo4j-cypher-skill

---

## 前置：图模型速查

### 节点类型

| Label | 关键属性 | 说明 |
|:--|:--|:--|
| `:Document` | `id`, `name`, `subject`, `pageCount`, `documentId` | PDF 文档 |
| `:Entity` | `id`, `entityType`, `name`, `originalText`, `pageNumber`, `documentId` | LLM 抽取的知识片段 |
| `:KnowledgePoint` | `id`, `name`, `description`, `subject`, `gradeLevel`, `documentId` | 标准化知识点 |
| `:KnowledgeCategory` | `id`, `name`, `level`, `parentName`, `documentId` | 知识点分类树节点 |
| `:Student` | `id`, `studentNo`, `name`, `className`, `grade` | 学生 |
| `:Exam` | `id`, `name`, `subject`, `examDate`, `totalScore` | 考试 |
| `:Event` | `id`, `eventType`, `rawScore`, `maxScore`, `eventTime` | 考试答题事件 |

### 边类型

| Type | 方向 | 属性 | 说明 |
|:--|:--|:--|:--|
| `EXTRACTS` | `(:Document)→(:Entity)` | — | 文档抽取实体 |
| `DERIVES` | `(:Entity)→(:Entity)` | — | 实体间推导 |
| `CONTAINS` | `(:Entity)→(:Entity)` | — | 实体间包含 |
| `REFERENCES` | `(:Entity)→(:Entity)` | — | 实体间引用 |
| `ALIGNED_TO` | `(:Entity)→(:KnowledgePoint)` | — | 实体对齐知识点 |
| `BELONGS_TO` | `(:KnowledgePoint)→(:KnowledgeCategory)` | — | 知识点归属分类 |
| `CHILD_OF` | `(:KnowledgeCategory)→(:KnowledgeCategory)` | — | 分类父子关系 |
| `PREREQUISITE_OF` | `(:KnowledgePoint)→(:KnowledgePoint)` | `strength`, `description` | 前置依赖 |
| `HAS_EVENT` | `(:Student)→(:Event)` | — | 学生产生事件 |
| `RELATES_TO` | `(:Event)→(:KnowledgePoint)` | `score`, `maxScore`, `weightDelta` | 事件关联知识点 |
| `BELONGS_TO_EXAM` | `(:Event)→(:Exam)` | — | 事件归属考试 |
| `MASTERS` | `(:Student)→(:KnowledgePoint)` | `weight` | 学生掌握度 |

### 已有索引

```
:Document(documentId), :Document(id)
:Entity(documentId),   :Entity(id)
:KnowledgePoint(documentId), :KnowledgePoint(id)
:KnowledgeCategory(documentId), :KnowledgeCategory(id)
```

---

## 一、Document 文档节点 CRUD

### 1.1 创建文档节点

```cypher
CYPHER 25
CREATE (d:Document {
  id: $id,
  nodeType: 'Document',
  documentId: $documentId,
  createdAt: datetime(),
  name: $name,
  subject: $subject,
  pageCount: $pageCount,
  properties: {}
})
RETURN d.id, d.name, d.subject, d.pageCount
```

**参数示例：**
```json
{
  "id": "doc-uuid-001",
  "documentId": "doc-uuid-001",
  "name": "二次函数章节复习.pdf",
  "subject": "数学",
  "pageCount": 15
}
```

### 1.2 按 ID 查询文档

```cypher
CYPHER 25
MATCH (d:Document {id: $docId})
RETURN d.id, d.name, d.subject, d.pageCount, d.createdAt
```

### 1.3 按名称模糊查询文档

```cypher
CYPHER 25
MATCH (d:Document)
WHERE d.name CONTAINS $keyword
RETURN d.id, d.name, d.subject, d.pageCount
LIMIT 25
```

### 1.4 更新文档信息

```cypher
CYPHER 25
MATCH (d:Document {id: $docId})
SET d += $updates
RETURN d.id, d.name, d.subject, d.pageCount
```

**参数示例：**
```json
{
  "docId": "doc-uuid-001",
  "updates": { "name": "二次函数章节复习-v2.pdf", "pageCount": 16 }
}
```

### 1.5 删除文档及其关联的所有节点和边（级联删除）

```cypher
CYPHER 25
MATCH (d:Document {id: $docId})
CALL (d) {
  MATCH (d)-[:EXTRACTS]->(e:Entity)
  DETACH DELETE e
}
CALL (d) {
  MATCH (d)-[:EXTRACTS]->(e:Entity)-[:ALIGNED_TO]->(kp:KnowledgePoint)
  DETACH DELETE kp
}
DETACH DELETE d
```

> ⚠️ 注意：此操作不可逆。生产环境建议先标记 `archived: true` 做软删除，或使用 `DELETE` 前确认关联节点数量。

---

## 二、Entity 实体节点 CRUD

### 2.1 创建单个实体节点

```cypher
CYPHER 25
CREATE (e:Entity {
  id: $id,
  nodeType: 'Entity',
  documentId: $documentId,
  createdAt: datetime(),
  entityType: $entityType,
  name: $name,
  originalText: $originalText,
  pageNumber: $pageNumber,
  metadata: $metadata,
  properties: {}
})
RETURN e.id, e.name, e.entityType, e.pageNumber
```

**参数示例（E1: 二次函数定义）：**
```json
{
  "id": "entity-uuid-e1",
  "documentId": "doc-uuid-001",
  "entityType": "DEFINITION",
  "name": "二次函数定义",
  "originalText": "二次函数是指形如 y=ax²+bx+c（a≠0）的函数",
  "pageNumber": 2,
  "metadata": {}
}
```

### 2.2 批量创建实体节点（UNWIND）

```cypher
CYPHER 25
UNWIND $entities AS item
CREATE (e:Entity {
  id: item.id,
  nodeType: 'Entity',
  documentId: item.documentId,
  createdAt: datetime(),
  entityType: item.entityType,
  name: item.name,
  originalText: item.originalText,
  pageNumber: item.pageNumber,
  metadata: item.metadata,
  properties: {}
})
RETURN count(e) AS createdCount
```

**参数示例：**
```json
{
  "entities": [
    {
      "id": "entity-uuid-e1", "documentId": "doc-uuid-001",
      "entityType": "DEFINITION", "name": "二次函数定义",
      "originalText": "二次函数是指形如 y=ax²+bx+c（a≠0）的函数",
      "pageNumber": 2, "metadata": {}
    },
    {
      "id": "entity-uuid-e2", "documentId": "doc-uuid-001",
      "entityType": "FORMULA", "name": "二次函数一般式",
      "originalText": "y=ax²+bx+c（一般式）",
      "pageNumber": 2, "metadata": {}
    },
    {
      "id": "entity-uuid-e3", "documentId": "doc-uuid-001",
      "entityType": "FORMULA", "name": "二次函数顶点式",
      "originalText": "y=a(x-h)²+k（顶点式）",
      "pageNumber": 3, "metadata": {}
    }
  ]
}
```

### 2.3 按文档 ID 查询所有实体

```cypher
CYPHER 25
MATCH (e:Entity {documentId: $docId})
RETURN e.id, e.name, e.entityType, e.originalText, e.pageNumber
ORDER BY e.pageNumber ASC, e.name ASC
LIMIT 100
```

### 2.4 按实体类型过滤

```cypher
CYPHER 25
MATCH (e:Entity {documentId: $docId})
WHERE e.entityType = $entityType
RETURN e.id, e.name, e.originalText, e.pageNumber
ORDER BY e.pageNumber ASC
LIMIT 25
```

**entityType 可选值：** `DEFINITION`, `FORMULA`, `CONCEPT`, `EXAMPLE`, `SOLUTION`

### 2.5 按页码查询实体

```cypher
CYPHER 25
MATCH (e:Entity {documentId: $docId})
WHERE e.pageNumber = $pageNumber
RETURN e.id, e.name, e.entityType, e.originalText
ORDER BY e.name
LIMIT 25
```

### 2.6 更新实体文本/名称

```cypher
CYPHER 25
MATCH (e:Entity {id: $entityId})
SET e += $updates
RETURN e.id, e.name, e.entityType, e.originalText
```

### 2.7 删除单个实体

```cypher
CYPHER 25
MATCH (e:Entity {id: $entityId})
DETACH DELETE e
```

---

## 三、Entity 之间的关系边 CRUD

### 3.1 创建实体间推导关系 (DERIVES)

```cypher
// E1 -[DERIVES]-> E2  （定义推导出一般式）
CYPHER 25
MATCH (a:Entity {id: $sourceId})
MATCH (b:Entity {id: $targetId})
CREATE (a)-[r:DERIVES]->(b)
SET r.createdAt = datetime()
RETURN a.name, type(r), b.name
```

**参数示例：**
```json
{ "sourceId": "entity-uuid-e1", "targetId": "entity-uuid-e2" }
```

### 3.2 批量创建实体间关系（UNWIND）

```cypher
CYPHER 25
UNWIND $edges AS edge
MATCH (a:Entity {id: edge.sourceId})
MATCH (b:Entity {id: edge.targetId})
CREATE (a)-[r:$($edge.relType)]->(b)
SET r.createdAt = datetime()
```

**参数示例（对应文档第一步的全部实体间关系）：**
```json
{
  "edges": [
    { "sourceId": "entity-uuid-e1", "targetId": "entity-uuid-e2", "relType": "DERIVES" },
    { "sourceId": "entity-uuid-e3", "targetId": "entity-uuid-e4", "relType": "DERIVES" },
    { "sourceId": "entity-uuid-e5", "targetId": "entity-uuid-e6", "relType": "CONTAINS" },
    { "sourceId": "entity-uuid-e2", "targetId": "entity-uuid-e5", "relType": "DERIVES" },
    { "sourceId": "entity-uuid-e8", "targetId": "entity-uuid-e2", "relType": "CONTAINS" },
    { "sourceId": "entity-uuid-e9", "targetId": "entity-uuid-e3", "relType": "REFERENCES" },
    { "sourceId": "entity-uuid-e10", "targetId": "entity-uuid-e6", "relType": "DERIVES" }
  ]
}
```

### 3.3 删除实体间的某条关系

```cypher
CYPHER 25
MATCH (a:Entity {id: $sourceId})-[r:DERIVES]->(b:Entity {id: $targetId})
DELETE r
```

### 3.4 文档→实体 EXTRACTS 批量创建

```cypher
CYPHER 25
MATCH (doc:Document {id: $docId})
UNWIND $entityIds AS entityId
MATCH (e:Entity {id: entityId})
CREATE (doc)-[r:EXTRACTS]->(e)
SET r.createdAt = datetime()
RETURN count(r) AS extractsCreated
```

---

## 四、KnowledgeCategory 分类树 CRUD

### 4.1 创建根分类节点

```cypher
CYPHER 25
CREATE (kc:KnowledgeCategory {
  id: $id,
  nodeType: 'KnowledgeCategory',
  documentId: $documentId,
  createdAt: datetime(),
  name: $name,
  level: 1,
  parentName: null,
  properties: {}
})
RETURN kc.id, kc.name, kc.level
```

**参数示例：**
```json
{
  "id": "kpcat-uuid-root",
  "documentId": "doc-uuid-001",
  "name": "初中数学"
}
```

### 4.2 批量创建分类树（UNWIND + 后续建 CHILD_OF 边）

```cypher
// 第一步：创建所有分类节点
CYPHER 25
UNWIND $categories AS cat
CREATE (kc:KnowledgeCategory {
  id: cat.id,
  nodeType: 'KnowledgeCategory',
  documentId: cat.documentId,
  createdAt: datetime(),
  name: cat.name,
  level: cat.level,
  parentName: cat.parentName,
  properties: {}
})
RETURN count(kc) AS categoryCount
```

**参数示例：**
```json
{
  "categories": [
    { "id": "kpcat-uuid-root", "documentId": "doc-uuid-001", "name": "初中数学", "level": 1, "parentName": null },
    { "id": "kpcat-uuid-algebra", "documentId": "doc-uuid-001", "name": "代数", "level": 2, "parentName": "初中数学" },
    { "id": "kpcat-uuid-function", "documentId": "doc-uuid-001", "name": "函数", "level": 3, "parentName": "代数" },
    { "id": "kpcat-uuid-linear", "documentId": "doc-uuid-001", "name": "一次函数", "level": 4, "parentName": "函数" },
    { "id": "kpcat-uuid-quadratic", "documentId": "doc-uuid-001", "name": "二次函数", "level": 4, "parentName": "函数" },
    { "id": "kpcat-uuid-equation", "documentId": "doc-uuid-001", "name": "方程", "level": 3, "parentName": "代数" },
    { "id": "kpcat-uuid-quad-eq", "documentId": "doc-uuid-001", "name": "一元二次方程", "level": 4, "parentName": "方程" }
  ]
}
```

```cypher
// 第二步：创建 CHILD_OF 关系（用 parentName 匹配）
CYPHER 25
UNWIND $parentMappings AS mapping
MATCH (child:KnowledgeCategory {name: mapping.childName, documentId: $docId})
MATCH (parent:KnowledgeCategory {name: mapping.parentName, documentId: $docId})
CREATE (child)-[r:CHILD_OF]->(parent)
SET r.createdAt = datetime()
```

**参数示例：**
```json
{
  "docId": "doc-uuid-001",
  "parentMappings": [
    { "childName": "代数", "parentName": "初中数学" },
    { "childName": "函数", "parentName": "代数" },
    { "childName": "一次函数", "parentName": "函数" },
    { "childName": "二次函数", "parentName": "函数" },
    { "childName": "方程", "parentName": "代数" },
    { "childName": "一元二次方程", "parentName": "方程" }
  ]
}
```

### 4.3 查询完整分类树（从根向下递归）

```cypher
CYPHER 25
MATCH path = (root:KnowledgeCategory {name: '初中数学'})-[:CHILD_OF*0..]->(descendant:KnowledgeCategory)
RETURN descendant.id, descendant.name, descendant.level, length(path) AS depth
ORDER BY descendant.level, descendant.name
```

### 4.4 查询某分类下的所有子分类

```cypher
CYPHER 25
MATCH (parent:KnowledgeCategory {name: $categoryName})
MATCH (parent)<-[:CHILD_OF*1..]-(descendant:KnowledgeCategory)
RETURN descendant.id, descendant.name, descendant.level
ORDER BY descendant.level, descendant.name
LIMIT 50
```

### 4.5 更新分类名称

```cypher
CYPHER 25
MATCH (kc:KnowledgeCategory {id: $categoryId})
SET kc.name = $newName
RETURN kc.id, kc.name, kc.level
```

### 4.6 删除分类节点（级联）

```cypher
CYPHER 25
MATCH (kc:KnowledgeCategory {id: $categoryId})
DETACH DELETE kc
```

---

## 五、KnowledgePoint 知识点 CRUD

### 5.1 创建单个知识点

```cypher
CYPHER 25
CREATE (kp:KnowledgePoint {
  id: $id,
  nodeType: 'KnowledgePoint',
  documentId: $documentId,
  createdAt: datetime(),
  name: $name,
  description: $description,
  subject: $subject,
  gradeLevel: $gradeLevel,
  properties: {}
})
RETURN kp.id, kp.name, kp.subject, kp.gradeLevel
```

**参数示例（KP: 二次函数定义）：**
```json
{
  "id": "kp-uuid-001",
  "documentId": "doc-uuid-001",
  "name": "二次函数定义",
  "description": "形如 y=ax²+bx+c（a≠0）的函数称为二次函数",
  "subject": "数学",
  "gradeLevel": "初三"
}
```

### 5.2 批量创建知识点（对应示例中全部 11 个 KP）

```cypher
CYPHER 25
UNWIND $knowledgePoints AS kp
CREATE (n:KnowledgePoint {
  id: kp.id,
  nodeType: 'KnowledgePoint',
  documentId: kp.documentId,
  createdAt: datetime(),
  name: kp.name,
  description: kp.description,
  subject: kp.subject,
  gradeLevel: kp.gradeLevel,
  properties: {}
})
RETURN count(n) AS kpCount
```

**参数示例：**
```json
{
  "knowledgePoints": [
    { "id": "kp-uuid-001", "documentId": "doc-uuid-001", "name": "二次函数定义", "description": "形如 y=ax²+bx+c（a≠0）的函数", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-002", "documentId": "doc-uuid-001", "name": "二次函数一般式", "description": "y=ax²+bx+c，a决定开口方向和大小", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-003", "documentId": "doc-uuid-001", "name": "二次函数顶点式", "description": "y=a(x-h)²+k，顶点为(h,k)", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-004", "documentId": "doc-uuid-001", "name": "二次函数交点式", "description": "y=a(x-x₁)(x-x₂)，x₁,x₂为与x轴交点", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-005", "documentId": "doc-uuid-001", "name": "对称轴", "description": "直线 x=-b/(2a)", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-006", "documentId": "doc-uuid-001", "name": "顶点坐标", "description": "(-b/(2a), (4ac-b²)/(4a))", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-007", "documentId": "doc-uuid-001", "name": "开口方向", "description": "a>0 开口向上，a<0 开口向下", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-008", "documentId": "doc-uuid-001", "name": "判别式 Δ", "description": "Δ=b²-4ac，Δ>0有两个交点", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-009", "documentId": "doc-uuid-001", "name": "二次函数综合应用", "description": "综合运用顶点、对称轴、判别式解题", "subject": "数学", "gradeLevel": "初三" },
    { "id": "kp-uuid-010", "documentId": "doc-uuid-001", "name": "一次函数定义", "description": "形如 y=kx+b（k≠0）的函数（前置知识）", "subject": "数学", "gradeLevel": "初二" },
    { "id": "kp-uuid-011", "documentId": "doc-uuid-001", "name": "求根公式", "description": "x=(-b±√Δ)/(2a)（一元二次方程求解）", "subject": "数学", "gradeLevel": "初三" }
  ]
}
```

### 5.3 按学科/年级查询知识点

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint)
WHERE kp.subject = $subject AND kp.gradeLevel = $gradeLevel
RETURN kp.id, kp.name, kp.description
ORDER BY kp.name
LIMIT 50
```

### 5.4 按名称模糊搜索知识点

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint)
WHERE kp.name CONTAINS $keyword
RETURN kp.id, kp.name, kp.description, kp.gradeLevel
LIMIT 25
```

### 5.5 更新知识点描述

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint {id: $kpId})
SET kp += $updates
RETURN kp.id, kp.name, kp.description
```

### 5.6 删除知识点

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint {id: $kpId})
DETACH DELETE kp
```

---

## 六、知识点与分类的归属关系 (BELONGS_TO)

### 6.1 批量创建知识点→分类归属

```cypher
CYPHER 25
UNWIND $belongings AS b
MATCH (kp:KnowledgePoint {id: b.kpId})
MATCH (cat:KnowledgeCategory {id: b.categoryId})
CREATE (kp)-[r:BELONGS_TO]->(cat)
SET r.createdAt = datetime()
RETURN count(r) AS belongingCount
```

**参数示例（将二次函数相关的 KP 归属到"二次函数"分类）：**
```json
{
  "belongings": [
    { "kpId": "kp-uuid-001", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-002", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-003", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-004", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-005", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-006", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-007", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-008", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-009", "categoryId": "kpcat-uuid-quadratic" },
    { "kpId": "kp-uuid-010", "categoryId": "kpcat-uuid-linear" },
    { "kpId": "kp-uuid-011", "categoryId": "kpcat-uuid-quad-eq" }
  ]
}
```

### 6.2 查询某分类下所有知识点

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint)-[:BELONGS_TO]->(cat:KnowledgeCategory {name: $categoryName})
RETURN kp.id, kp.name, kp.description
ORDER BY kp.name
LIMIT 50
```

### 6.3 查询某知识点所属的分类路径（向上追溯到根）

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint {name: $kpName})-[:BELONGS_TO]->(leaf:KnowledgeCategory)
MATCH path = (leaf)-[:CHILD_OF*0..]->(ancestor:KnowledgeCategory)
RETURN kp.name AS knowledgePoint, [node IN nodes(path) | node.name] AS categoryPath
```

---

## 七、实体对齐知识点 (ALIGNED_TO)

### 7.1 批量创建实体→知识点对齐

```cypher
CYPHER 25
UNWIND $alignments AS align
MATCH (e:Entity {id: align.entityId})
MATCH (kp:KnowledgePoint {id: align.kpId})
CREATE (e)-[r:ALIGNED_TO]->(kp)
SET r.createdAt = datetime()
RETURN count(r) AS alignmentCount
```

**参数示例（对应文档中 E1~E9 的 9 条对齐）：**
```json
{
  "alignments": [
    { "entityId": "entity-uuid-e1",  "kpId": "kp-uuid-001" },
    { "entityId": "entity-uuid-e2",  "kpId": "kp-uuid-002" },
    { "entityId": "entity-uuid-e3",  "kpId": "kp-uuid-003" },
    { "entityId": "entity-uuid-e4",  "kpId": "kp-uuid-004" },
    { "entityId": "entity-uuid-e5",  "kpId": "kp-uuid-005" },
    { "entityId": "entity-uuid-e6",  "kpId": "kp-uuid-006" },
    { "entityId": "entity-uuid-e7",  "kpId": "kp-uuid-007" },
    { "entityId": "entity-uuid-e8",  "kpId": "kp-uuid-008" },
    { "entityId": "entity-uuid-e9",  "kpId": "kp-uuid-009" }
  ]
}
```

### 7.2 查询某知识点被哪些实体引用（反向查原文出处）

```cypher
CYPHER 25
MATCH (e:Entity)-[:ALIGNED_TO]->(kp:KnowledgePoint {name: $kpName})
RETURN e.id, e.name, e.entityType, e.originalText, e.pageNumber
ORDER BY e.pageNumber
LIMIT 25
```

### 7.3 查询实体对齐到的知识点

```cypher
CYPHER 25
MATCH (e:Entity {id: $entityId})-[:ALIGNED_TO]->(kp:KnowledgePoint)
RETURN kp.id, kp.name, kp.description
```

---

## 八、PrerequisiteEdge 前置依赖 CRUD

### 8.1 创建单条前置依赖

```cypher
CYPHER 25
MATCH (prereq:KnowledgePoint {id: $prerequisiteId})
MATCH (target:KnowledgePoint {id: $targetId})
CREATE (prereq)-[r:PREREQUISITE_OF]->(target)
SET r.strength = $strength,
    r.description = $description,
    r.createdAt = datetime()
RETURN prereq.name, type(r), target.name, r.strength
```

**参数示例：**
```json
{
  "prerequisiteId": "kp-uuid-001",
  "targetId": "kp-uuid-002",
  "strength": 0.95,
  "description": "理解一般式 y=ax²+bx+c 才能计算对称轴 x=-b/(2a)"
}
```

### 8.2 批量创建前置依赖链

```cypher
CYPHER 25
UNWIND $prerequisites AS prereq
MATCH (a:KnowledgePoint {id: prereq.from})
MATCH (b:KnowledgePoint {id: prereq.to})
CREATE (a)-[r:PREREQUISITE_OF]->(b)
SET r.strength = prereq.strength,
    r.description = prereq.description,
    r.createdAt = datetime()
RETURN count(r) AS prerequisiteCount
```

**参数示例（对应文档中 7 条前置依赖）：**
```json
{
  "prerequisites": [
    {
      "from": "kp-uuid-001", "to": "kp-uuid-002",
      "strength": 0.95,
      "description": "理解二次函数定义才能学习一般式表达式"
    },
    {
      "from": "kp-uuid-002", "to": "kp-uuid-005",
      "strength": 0.95,
      "description": "理解一般式 y=ax²+bx+c 才能计算对称轴 x=-b/(2a)"
    },
    {
      "from": "kp-uuid-002", "to": "kp-uuid-006",
      "strength": 0.90,
      "description": "一般式参数 a,b,c 是顶点坐标公式的基础"
    },
    {
      "from": "kp-uuid-005", "to": "kp-uuid-006",
      "strength": 0.85,
      "description": "对称轴 x=-b/(2a) 是顶点横坐标，与顶点坐标有直接关联"
    },
    {
      "from": "kp-uuid-007", "to": "kp-uuid-009",
      "strength": 0.60,
      "description": "开口方向判断是综合应用题的基础步骤之一"
    },
    {
      "from": "kp-uuid-006", "to": "kp-uuid-009",
      "strength": 0.90,
      "description": "顶点坐标是综合应用题的核心工具"
    },
    {
      "from": "kp-uuid-010", "to": "kp-uuid-001",
      "strength": 0.70,
      "description": "一次函数是函数概念的入门，为二次函数学习打基础（跨分类依赖）"
    },
    {
      "from": "kp-uuid-011", "to": "kp-uuid-008",
      "strength": 0.80,
      "description": "求根公式中 Δ 的含义来自判别式（跨分类依赖）"
    }
  ]
}
```

### 8.3 查询某知识点的所有前置依赖（向前追溯）

```cypher
CYPHER 25
MATCH (prereq:KnowledgePoint)-[r:PREREQUISITE_OF]->(kp:KnowledgePoint {name: $kpName})
RETURN prereq.id, prereq.name, r.strength, r.description
ORDER BY r.strength DESC
```

### 8.4 查询某知识点是哪些知识点的前置（向后追溯）

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint {name: $kpName})-[r:PREREQUISITE_OF]->(dependent:KnowledgePoint)
RETURN dependent.id, dependent.name, r.strength, r.description
ORDER BY r.strength DESC
```

### 8.5 查询完整前置依赖链（多跳追溯）

```cypher
// 查询"顶点坐标"的全部前置依赖链（不限跳数）
CYPHER 25
MATCH path = (prereq:KnowledgePoint)-[:PREREQUISITE_OF*1..5]->(target:KnowledgePoint {name: '顶点坐标'})
RETURN [node IN nodes(path) | node.name] AS prerequisiteChain,
       length(path) AS chainDepth
ORDER BY chainDepth
```

### 8.6 更新前置依赖强度

```cypher
CYPHER 25
MATCH (a:KnowledgePoint {id: $fromId})-[r:PREREQUISITE_OF]->(b:KnowledgePoint {id: $toId})
SET r.strength = $newStrength,
    r.description = $newDescription
RETURN a.name, r.strength, b.name
```

### 8.7 删除前置依赖

```cypher
CYPHER 25
MATCH (a:KnowledgePoint {id: $fromId})-[r:PREREQUISITE_OF]->(b:KnowledgePoint {id: $toId})
DELETE r
```

---

## 九、Student / Exam / Event 事件图谱 CRUD

### 9.1 创建学生节点

```cypher
CYPHER 25
MERGE (s:Student {studentNo: $studentNo})
ON CREATE SET s.id = $id,
              s.name = $name,
              s.className = $className,
              s.grade = $grade,
              s.createdAt = datetime()
ON MATCH SET s.name = $name,
             s.className = $className,
             s.grade = $grade
RETURN s.id, s.studentNo, s.name, s.className
```

> `MERGE` 以 `studentNo`（学号）作为业务唯一键，避免重复创建。

**参数示例：**
```json
{
  "id": "student-uuid-001",
  "studentNo": "S2024001",
  "name": "张三",
  "className": "初三(1)班",
  "grade": "初三"
}
```

### 9.2 创建考试节点

```cypher
CYPHER 25
MERGE (exam:Exam {id: $id})
ON CREATE SET exam.name = $name,
              exam.subject = $subject,
              exam.examDate = $examDate,
              exam.totalScore = $totalScore,
              exam.createdAt = datetime()
ON MATCH SET exam += $updates
RETURN exam.id, exam.name, exam.examDate, exam.totalScore
```

**参数示例：**
```json
{
  "id": "exam-uuid-001",
  "name": "10月月考",
  "subject": "数学",
  "examDate": "2024-10-15",
  "totalScore": 120
}
```

### 9.3 创建考试事件 + 关联学生、考试、知识点（一步完成）

```cypher
// 一条事件需要同时建立 HAS_EVENT、BELONGS_TO_EXAM、RELATES_TO 三条边
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})
MATCH (exam:Exam {id: $examId})
MATCH (kp:KnowledgePoint {id: $kpId})

CREATE (evt:Event {
  id: $eventId,
  eventType: 'EXAM_QUESTION',
  rawScore: $rawScore,
  maxScore: $maxScore,
  eventTime: $eventTime,
  createdAt: datetime()
})

CREATE (s)-[:HAS_EVENT {createdAt: datetime()}]->(evt)
CREATE (evt)-[:BELONGS_TO_EXAM {createdAt: datetime()}]->(exam)

// weightDelta = (rawScore/maxScore - 0.6) * 0.5，可根据业务调整
CREATE (evt)-[rel:RELATES_TO]->(kp)
SET rel.score = $rawScore,
    rel.maxScore = $maxScore,
    rel.weightDelta = $weightDelta,
    rel.createdAt = datetime()

RETURN evt.id, s.name, exam.name, kp.name, rel.weightDelta
```

**参数示例（张三 Q3 二次函数定义 5/5）：**
```json
{
  "studentNo": "S2024001",
  "examId": "exam-uuid-001",
  "kpId": "kp-uuid-001",
  "eventId": "event-uuid-001",
  "rawScore": 5,
  "maxScore": 5,
  "eventTime": "2024-10-15",
  "weightDelta": 0.08
}
```

### 9.4 批量创建事件（从 CSV 数据 UNWIND）

```cypher
CYPHER 25
UNWIND $events AS evtData
MATCH (s:Student {studentNo: evtData.studentNo})
MATCH (exam:Exam {id: evtData.examId})
MATCH (kp:KnowledgePoint {name: evtData.kpName})

CREATE (evt:Event {
  id: evtData.eventId,
  eventType: 'EXAM_QUESTION',
  rawScore: evtData.rawScore,
  maxScore: evtData.maxScore,
  eventTime: evtData.eventTime,
  createdAt: datetime()
})

CREATE (s)-[:HAS_EVENT {createdAt: datetime()}]->(evt)
CREATE (evt)-[:BELONGS_TO_EXAM {createdAt: datetime()}]->(exam)
CREATE (evt)-[rel:RELATES_TO]->(kp)
SET rel.score = evtData.rawScore,
    rel.maxScore = evtData.maxScore,
    rel.weightDelta = evtData.weightDelta,
    rel.createdAt = datetime()
```

**参数示例（全部 4 条事件）：**
```json
{
  "events": [
    { "eventId": "event-uuid-001", "studentNo": "S2024001", "examId": "exam-uuid-001", "kpName": "二次函数定义", "rawScore": 5, "maxScore": 5, "eventTime": "2024-10-15", "weightDelta": 0.08 },
    { "eventId": "event-uuid-002", "studentNo": "S2024001", "examId": "exam-uuid-001", "kpName": "对称轴", "rawScore": 3, "maxScore": 8, "eventTime": "2024-10-15", "weightDelta": -0.15 },
    { "eventId": "event-uuid-003", "studentNo": "S2024001", "examId": "exam-uuid-001", "kpName": "顶点坐标", "rawScore": 2, "maxScore": 10, "eventTime": "2024-10-15", "weightDelta": -0.25 },
    { "eventId": "event-uuid-004", "studentNo": "S2024002", "examId": "exam-uuid-001", "kpName": "对称轴", "rawScore": 8, "maxScore": 8, "eventTime": "2024-10-15", "weightDelta": 0.12 }
  ]
}
```

### 9.5 查询学生的所有考试事件

```cypher
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})-[:HAS_EVENT]->(evt:Event)
MATCH (evt)-[:RELATES_TO]->(kp:KnowledgePoint)
MATCH (evt)-[:BELONGS_TO_EXAM]->(exam:Exam)
RETURN evt.id, exam.name AS examName, kp.name AS kpName,
       evt.rawScore, evt.maxScore,
       round(evt.rawScore * 100.0 / evt.maxScore, 1) AS scorePercent,
       evt.eventTime
ORDER BY evt.eventTime DESC, kpName
LIMIT 50
```

### 9.6 查询某次考试中某知识点的所有学生表现

```cypher
CYPHER 25
MATCH (exam:Exam {id: $examId})<-[:BELONGS_TO_EXAM]-(evt:Event)
MATCH (evt)-[:RELATES_TO]->(kp:KnowledgePoint {name: $kpName})
MATCH (s:Student)-[:HAS_EVENT]->(evt)
RETURN s.studentNo, s.name, evt.rawScore, evt.maxScore,
       round(evt.rawScore * 100.0 / evt.maxScore, 1) AS scorePercent
ORDER BY scorePercent DESC
```

### 9.7 删除事件

```cypher
CYPHER 25
MATCH (evt:Event {id: $eventId})
DETACH DELETE evt
```

---

## 十、MasteryEdge 学生掌握度 CRUD

### 10.1 创建/更新学生→知识点掌握度（UPSERT）

```cypher
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})
MATCH (kp:KnowledgePoint {id: $kpId})
MERGE (s)-[r:MASTERS]->(kp)
ON CREATE SET r.weight = $weight,
              r.createdAt = datetime()
ON MATCH SET r.weight = r.weight + $weightDelta,
             r.updatedAt = datetime()
RETURN s.name, kp.name, r.weight
```

> `weight` 建议初始值 0.5（未知），取值范围 [0, 1]。每次考试事件后根据 `weightDelta` 增量更新。

### 10.2 基于事件批量更新掌握度

```cypher
// 从 RELATES_TO 边汇总 weightDelta，更新对应 MASTERS 边
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})-[:HAS_EVENT]->(evt:Event)-[rel:RELATES_TO]->(kp:KnowledgePoint)
WHERE evt.eventTime = $examDate
MERGE (s)-[m:MASTERS]->(kp)
ON CREATE SET m.weight = 0.5 + rel.weightDelta,
              m.createdAt = datetime()
ON MATCH SET m.weight = m.weight + rel.weightDelta,
             m.updatedAt = datetime()
RETURN kp.name, m.weight
```

### 10.3 查询学生对所有知识点的掌握度

```cypher
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})-[m:MASTERS]->(kp:KnowledgePoint)
RETURN kp.id, kp.name, m.weight,
       CASE
         WHEN m.weight >= 0.8 THEN '✅ 掌握'
         WHEN m.weight >= 0.6 THEN '⚠️ 待加强'
         ELSE '❌ 薄弱'
       END AS level
ORDER BY m.weight ASC
LIMIT 50
```

### 10.4 查询学生薄弱知识点（weight < 0.6）

```cypher
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})-[m:MASTERS]->(kp:KnowledgePoint)
WHERE m.weight < $threshold
RETURN kp.id, kp.name, m.weight
ORDER BY m.weight ASC
LIMIT 25
```

### 10.5 查询某知识点全班掌握度分布

```cypher
CYPHER 25
MATCH (s:Student)-[m:MASTERS]->(kp:KnowledgePoint {name: $kpName})
RETURN s.studentNo, s.name, m.weight
ORDER BY m.weight ASC
LIMIT 50
```

---

## 十一、复杂查询场景

### 11.1 任务驱动剪枝：分析学生薄弱点（核心查询）

> 对应文档附录的 5 步剪枝策略。

```cypher
// 步骤 1-2：锚定学生 → 拿薄弱知识点（weight < 0.6）
// 步骤 3：反向追溯前置依赖链
// 步骤 4：拿到原始文档实体作为 LLM 上下文
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})-[m:MASTERS]->(weakKp:KnowledgePoint)
WHERE m.weight < $threshold

// 反向追溯前置依赖链（不限跳数）
OPTIONAL MATCH prereqChain = (root:KnowledgePoint)-[:PREREQUISITE_OF*1..4]->(weakKp)

// 追溯到原始文档实体
OPTIONAL MATCH (docEntity:Entity)-[:ALIGNED_TO]->(weakKp)
OPTIONAL MATCH (doc:Document)-[:EXTRACTS]->(docEntity)

// 也追溯前置链上的实体
OPTIONAL MATCH (chainEntity:Entity)-[:ALIGNED_TO]->(root)

RETURN weakKp.name AS weakPoint,
       m.weight AS masteryWeight,
       [node IN nodes(prereqChain) | node.name] AS prerequisiteChain,
       collect(DISTINCT {
         entityName: docEntity.name,
         entityType: docEntity.entityType,
         originalText: docEntity.originalText,
         pageNumber: docEntity.pageNumber,
         docName: doc.name
       }) AS sourceMaterials,
       collect(DISTINCT {
         entityName: chainEntity.name,
         originalText: chainEntity.originalText
       }) AS prereqMaterials
ORDER BY m.weight ASC
LIMIT 10
```

### 11.2 路径归因分析：为什么某知识点得分低？

```cypher
// 以"张三为什么顶点坐标得分低？"为例的因果路径查询
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})-[m:MASTERS]->(targetKp:KnowledgePoint {name: $kpName})

// 路径 A：事件证据链
OPTIONAL MATCH (s)-[:HAS_EVENT]->(evt:Event)-[rel:RELATES_TO]->(targetKp)
OPTIONAL MATCH (evt)-[:BELONGS_TO_EXAM]->(exam:Exam)

// 路径 B：前置依赖链上的掌握度
OPTIONAL MATCH prereqPath = (prereq:KnowledgePoint)-[:PREREQUISITE_OF*1..3]->(targetKp)
OPTIONAL MATCH (s)-[pm:MASTERS]->(prereq)

RETURN targetKp.name AS targetKP,
       m.weight AS targetMastery,
       collect(DISTINCT {
         examName: exam.name,
         rawScore: evt.rawScore,
         maxScore: evt.maxScore,
         scoreRate: round(evt.rawScore * 100.0 / evt.maxScore, 1)
       }) AS examEvidence,
       collect(DISTINCT {
         prereqName: prereq.name,
         prereqMastery: pm.weight,
         relationStrength: relationships(prereqPath)[0].strength
       }) AS prereqStatus
```

### 11.3 查询某知识点的学习路径（从基础到高级）

```cypher
// 从"一次函数定义"到"二次函数综合应用"的完整前置链
CYPHER 25
MATCH path = (start:KnowledgePoint {name: $startKp})-[:PREREQUISITE_OF*1..6]->(end:KnowledgePoint {name: $endKp})
RETURN [node IN nodes(path) | node.name] AS learningPath,
       length(path) AS steps,
       reduce(total = 0.0, r IN relationships(path) | total + r.strength) / length(path) AS avgStrength
ORDER BY steps
LIMIT 5
```

### 11.4 文档全景查询：一份 PDF 的完整图谱

```cypher
// 查询文档 → 实体 → 知识点 → 分类 完整链路
CYPHER 25
MATCH (doc:Document {name: $docName})-[:EXTRACTS]->(e:Entity)
OPTIONAL MATCH (e)-[rel:DERIVES|CONTAINS|REFERENCES]->(otherEntity:Entity)
OPTIONAL MATCH (e)-[:ALIGNED_TO]->(kp:KnowledgePoint)
OPTIONAL MATCH (kp)-[:BELONGS_TO]->(cat:KnowledgeCategory)

RETURN doc.name AS document,
       e.name AS entity, e.entityType, e.pageNumber,
       collect(DISTINCT { relType: type(rel), target: otherEntity.name }) AS entityRelations,
       collect(DISTINCT kp.name) AS alignedKnowledgePoints,
       collect(DISTINCT cat.name) AS categories
ORDER BY e.pageNumber, e.name
LIMIT 100
```

### 11.5 跨文档知识融合：同一知识点被哪些文档的哪些实体引用

```cypher
CYPHER 25
MATCH (kp:KnowledgePoint {name: $kpName})<-[:ALIGNED_TO]-(e:Entity)<-[:EXTRACTS]-(doc:Document)
RETURN kp.name AS knowledgePoint,
       collect(DISTINCT {
         docName: doc.name,
         entityName: e.name,
         entityType: e.entityType,
         originalText: e.originalText
       }) AS crossDocReferences
```

### 11.6 班级薄弱知识点热力图

```cypher
CYPHER 25
MATCH (s:Student {className: $className})-[m:MASTERS]->(kp:KnowledgePoint)
WITH kp.name AS kpName,
     avg(m.weight) AS classAvg,
     min(m.weight) AS classMin,
     count(s) AS studentCount
WHERE classAvg < 0.7
RETURN kpName, round(classAvg, 3) AS avgMastery,
       round(classMin, 3) AS weakestMastery,
       studentCount
ORDER BY classAvg ASC
LIMIT 25
```

### 11.7 学生成绩趋势（按考试时间线）

```cypher
CYPHER 25
MATCH (s:Student {studentNo: $studentNo})-[:HAS_EVENT]->(evt:Event)-[rel:RELATES_TO]->(kp:KnowledgePoint)
MATCH (evt)-[:BELONGS_TO_EXAM]->(exam:Exam)
WITH exam.examDate AS examDate, exam.name AS examName,
     kp.name AS kpName,
     round(evt.rawScore * 100.0 / evt.maxScore, 1) AS scoreRate
ORDER BY examDate, kpName
RETURN examDate, examName,
       collect({ kp: kpName, scoreRate: scoreRate }) AS details,
       round(avg(scoreRate), 1) AS examAvgScore
LIMIT 50
```

### 11.8 知识点间的交叉引用发现（通过 EVENT 关联）

```cypher
// 发现常在同一考试中被一起答错的知识点对 → 可能的教学关联
CYPHER 25
MATCH (evt1:Event)-[:RELATES_TO]->(kp1:KnowledgePoint)
MATCH (evt2:Event)-[:RELATES_TO]->(kp2:KnowledgePoint)
WHERE evt1.eventType = 'EXAM_QUESTION'
  AND evt2.eventType = 'EXAM_QUESTION'
  AND evt1.eventTime = evt2.eventTime
  AND id(kp1) < id(kp2)  // 避免重复对
  AND evt1.rawScore * 1.0 / evt1.maxScore < 0.6
  AND evt2.rawScore * 1.0 / evt2.maxScore < 0.6
RETURN kp1.name AS kpA, kp2.name AS kpB,
       count(*) AS cooccurrenceCount
ORDER BY cooccurrenceCount DESC
LIMIT 25
```

---

## 十二、宽图谱融合 — 一键构建脚本

以下脚本按文档五个步骤，一次性构建完整的二次函数知识图谱：

### 12.1 完整构建（按顺序执行）

```cypher
// ============================================
// 第一步：创建文档节点
// ============================================
CYPHER 25
CREATE (doc:Document {
  id: 'doc-uuid-quadratic',
  nodeType: 'Document',
  documentId: 'doc-uuid-quadratic',
  createdAt: datetime(),
  name: '二次函数章节复习.pdf',
  subject: '数学',
  pageCount: 15,
  properties: {}
})
RETURN 'Step 1 done: Document created' AS status;
```

```cypher
// ============================================
// 第二步：批量创建 10 个实体节点 + EXTRACTS 边
// ============================================
CYPHER 25
UNWIND [
  {id: 'e1', type: 'DEFINITION', name: '二次函数定义', text: '二次函数是指形如 y=ax²+bx+c（a≠0）的函数', page: 2},
  {id: 'e2', type: 'FORMULA', name: '二次函数一般式', text: 'y=ax²+bx+c（一般式）', page: 2},
  {id: 'e3', type: 'FORMULA', name: '二次函数顶点式', text: 'y=a(x-h)²+k（顶点式）', page: 3},
  {id: 'e4', type: 'FORMULA', name: '二次函数交点式', text: 'y=a(x-x₁)(x-x₂)（交点式）', page: 3},
  {id: 'e5', type: 'CONCEPT', name: '对称轴', text: '对称轴：直线 x=-b/(2a)', page: 4},
  {id: 'e6', type: 'CONCEPT', name: '顶点坐标', text: '顶点坐标：(-b/(2a), (4ac-b²)/(4a))', page: 4},
  {id: 'e7', type: 'CONCEPT', name: '开口方向', text: '开口方向：a>0 开口向上，a<0 开口向下', page: 4},
  {id: 'e8', type: 'CONCEPT', name: '判别式', text: '判别式 Δ=b²-4ac，Δ>0 与 x 轴有两个交点', page: 5},
  {id: 'e9', type: 'EXAMPLE', name: '例题-求顶点坐标', text: '已知二次函数 y=x²-4x+3，求顶点坐标和对称轴', page: 8},
  {id: 'e10', type: 'SOLUTION', name: '配方法解法', text: '配方法：y=(x-2)²-1，顶点(2,-1)，对称轴 x=2', page: 9}
] AS item

MATCH (doc:Document {id: 'doc-uuid-quadratic'})

CREATE (e:Entity {
  id: 'entity-uuid-' + item.id,
  nodeType: 'Entity',
  documentId: 'doc-uuid-quadratic',
  createdAt: datetime(),
  entityType: item.type,
  name: item.name,
  originalText: item.text,
  pageNumber: item.page,
  metadata: {},
  properties: {}
})

CREATE (doc)-[:EXTRACTS {createdAt: datetime()}]->(e)

RETURN 'Step 2 done: 10 Entities created' AS status;
```

```cypher
// ============================================
// 第三步：创建 7 条实体间引用边
// ============================================
CYPHER 25
UNWIND [
  ['entity-uuid-e1',  'DERIVES',   'entity-uuid-e2'],
  ['entity-uuid-e3',  'DERIVES',   'entity-uuid-e4'],
  ['entity-uuid-e5',  'CONTAINS',  'entity-uuid-e6'],
  ['entity-uuid-e2',  'DERIVES',   'entity-uuid-e5'],
  ['entity-uuid-e8',  'CONTAINS',  'entity-uuid-e2'],
  ['entity-uuid-e9',  'REFERENCES','entity-uuid-e3'],
  ['entity-uuid-e10', 'DERIVES',   'entity-uuid-e6']
] AS edge

MATCH (a:Entity {id: edge[0]})
MATCH (b:Entity {id: edge[2]})

CREATE (a)-[r:$($edge[1])]->(b)
SET r.createdAt = datetime()

RETURN 'Step 3 done: 7 Entity relations created' AS status;
```

```cypher
// ============================================
// 第四步：批量创建知识分类 + 知识点 + 关联
// ============================================
// 4a. 创建分类节点
CYPHER 25
UNWIND [
  {id: 'cat-root', name: '初中数学', level: 1, parent: null},
  {id: 'cat-algebra', name: '代数', level: 2, parent: 'cat-root'},
  {id: 'cat-function', name: '函数', level: 3, parent: 'cat-algebra'},
  {id: 'cat-linear', name: '一次函数', level: 4, parent: 'cat-function'},
  {id: 'cat-quadratic', name: '二次函数', level: 4, parent: 'cat-function'},
  {id: 'cat-equation', name: '方程', level: 3, parent: 'cat-algebra'},
  {id: 'cat-quad-eq', name: '一元二次方程', level: 4, parent: 'cat-equation'}
] AS cat

CREATE (kc:KnowledgeCategory {
  id: 'kpcat-uuid-' + cat.id,
  nodeType: 'KnowledgeCategory',
  documentId: 'doc-uuid-quadratic',
  createdAt: datetime(),
  name: cat.name,
  level: cat.level,
  parentName: cat.parent,
  properties: {}
});

// 4b. 建立 CHILD_OF 边
CYPHER 25
UNWIND [
  ['cat-algebra',   'cat-root'],
  ['cat-function',  'cat-algebra'],
  ['cat-linear',    'cat-function'],
  ['cat-quadratic', 'cat-function'],
  ['cat-equation',  'cat-algebra'],
  ['cat-quad-eq',   'cat-equation']
] AS pair

MATCH (child:KnowledgeCategory {id: 'kpcat-uuid-' + pair[0]})
MATCH (parent:KnowledgeCategory {id: 'kpcat-uuid-' + pair[1]})
CREATE (child)-[:CHILD_OF {createdAt: datetime()}]->(parent);
```

```cypher
// ============================================
// 第五步：批量创建知识点 + BELONGS_TO + ALIGNED_TO
// 按 11.2 节的参数执行（略，参见上方各节）
// ============================================
```

### 12.2 完整删除（清理测试数据）

```cypher
CYPHER 25
MATCH (d:Document {id: 'doc-uuid-quadratic'})

// 查找关联的所有节点
OPTIONAL MATCH (d)-[:EXTRACTS]->(e:Entity)
OPTIONAL MATCH (e)-[:ALIGNED_TO]->(kp:KnowledgePoint)
OPTIONAL MATCH (kp)-[:BELONGS_TO]->(kc:KnowledgeCategory)
OPTIONAL MATCH (kc)-[:CHILD_OF]->(parentKc:KnowledgeCategory)

// 查找事件相关节点（如果有）
OPTIONAL MATCH (s:Student)-[:HAS_EVENT]->(evt:Event)-[:RELATES_TO]->(kp)
OPTIONAL MATCH (evt)-[:BELONGS_TO_EXAM]->(exam:Exam)
OPTIONAL MATCH (s)-[m:MASTERS]->(kp)

// 收集所有节点去重后 DETACH DELETE
WITH d, collect(DISTINCT e) + collect(DISTINCT kp) + collect(DISTINCT kc) +
        collect(DISTINCT evt) + collect(DISTINCT exam) AS toDelete
UNWIND toDelete AS n
DETACH DELETE n;

// 最后删除文档节点
MATCH (d:Document {id: 'doc-uuid-quadratic'})
DETACH DELETE d

RETURN 'Cleanup complete' AS status;
```

---

## 十三、查询模板速查

| 场景 | 入口节点 | 边遍历 | 返回 |
|:--|:--|:--|:--|
| 文档→实体→知识点 | `:Document` | `EXTRACTS → ALIGNED_TO` | 知识片段+标准化知识点 |
| 知识点→原文出处 | `:KnowledgePoint` | `←ALIGNED_TO ←EXTRACTS` | 原始 PDF 文本和页码 |
| 学生薄弱点 | `:Student` | `MASTERS (weight<0.6)` | 薄弱知识点+掌握度 |
| 前置依赖链 | `:KnowledgePoint` | `PREREQUISITE_OF*1..n` | 学习路径 |
| 分类下知识点 | `:KnowledgeCategory` | `←BELONGS_TO` | 该分类所有知识点 |
| 考试表现 | `:Exam` | `←BELONGS_TO_EXAM →RELATES_TO` | 学生得分率 |
| 归因分析 | `:Student` | `MASTERS + HAS_EVENT + PREREQUISITE_OF` | 原因链条 |
| 分类树 | `:KnowledgeCategory` | `CHILD_OF*0..n` | 完整层级结构 |

---

## 附录：edgeType 枚举值参考

| edgeType | 方向 | 含义 |
|:--|:--|:--|
| `EXTRACTS` | Doc → Entity | 文档抽取 |
| `DERIVES` | Entity → Entity | 推导 |
| `CONTAINS` | Entity → Entity | 包含 |
| `REFERENCES` | Entity → Entity/KP | 引用 |
| `ALIGNED_TO` | Entity → KP | 对齐 |
| `BELONGS_TO` | KP → Category | 归属 |
| `CHILD_OF` | Category → Category | 子分类 |
| `PREREQUISITE_OF` | KP → KP | 前置依赖 |
| `HAS_EVENT` | Student → Event | 事件归属 |
| `RELATES_TO` | Event → KP | 事件关联 |
| `BELONGS_TO_EXAM` | Event → Exam | 考试归属 |
| `MASTERS` | Student → KP | 掌握度 |