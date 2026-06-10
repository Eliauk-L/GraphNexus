# GraphNexus 逻辑视图 — 类图设计

> 版本：v2.0 | 日期：2026-06-10
>
> 基于功能文档 v2.4-mvp 与用户故事 28 个 Story，匹配五层开发架构与物理部署拓扑。
> v2.0 新增：知识图谱节点与边的抽象层设计，严格区分图存储（Neo4j 属性图）与关系存储（MySQL 表）。

---

## 一、设计思路

GraphNexus 的数据分为两类存储：

| 存储 | 技术 | 内容 | UML 表示 |
|:--|:--|:--|:--|
| **图存储** | Neo4j | 宽图谱的节点（顶点）与边（关系） | `<<Node>>` / `<<Edge>>` 构造型 |
| **关系存储** | MySQL | 配置、规则、日志、RBAC、统计等 | 常规 `<<Entity>>` 类 |

本文档的核心设计原则：

1. **图节点** 均继承自抽象基类 `GraphNode`，对应 Neo4j 中带 Label 的顶点
2. **图边** 均继承自抽象基类 `GraphEdge`，对应 Neo4j 中带 Type 的有向关系
3. **配置/日志/RBAC 类** 为常规 MySQL 实体，与图存储无继承关系
4. 节点与边的属性要求"小而精确"，仅保留实现 28 个 Story 所必需的字段

---

## 二、包结构总览

```mermaid
classDiagram
namespace 图节点抽象层 {
    class GraphNode
    class StudentNode
    class KnowledgePointNode
    class KnowledgeCategoryNode
    class DocumentNode
    class EntityNode
    class ExamNode
    class QuestionNode
    class EventNode
}
namespace 图边抽象层 {
    class GraphEdge
    class MasteryEdge
    class PrerequisiteEdge
    class BelongsToEdge
    class ChildOfEdge
    class ExtractsEdge
    class ReferencesEdge
    class AlignedToEdge
    class HasEventEdge
    class RelatesToEdge
    class ContainsEdge
    class TestsEdge
    class ScoresOnEdge
    class BelongsToExamEdge
}
namespace 用户与权限域 {
    class User
    class Role
    class Permission
    class UserRole
    class RolePermission
    class LoginLog
    class AuditLog
}
namespace 文档处理域 {
    class Document
    class ParseTask
    class ParseResult
    class ExtractedEntity
    class ExtractedRelation
    class CsvImportTask
    class CsvImportRecord
}
namespace 图谱业务配置域 {
    class EntityAlignment
    class AlignmentAudit
    class PruningStrategy
    class PruningStrategyVersion
    class WeightRule
    class WeightChangeLog
    class ScoreAllocationRule
    class ScheduledTask
    class TaskExecution
}
namespace 图谱分析域 {
    class SubGraph
    class GraphMetric
    class CommunityResult
    class GraphSnapshot
}
namespace 智能查询域 {
    class AttributionQuery
    class AttributionReport
    class AttributionEvidence
    class TeachingSuggestion
    class ReviewPath
    class ReportExport
    class ShareLink
    class LlmConfig
    class PromptTemplate
    class LlmCallLog
}
namespace 运维运营域 {
    class ServiceHealth
    class AlertRule
    class AlertEvent
    class BackupTask
    class BackupRecord
    class UsageMetric
    class DocumentProcessMetric
}
```

---

## 三、知识图谱节点与边抽象层（核心）

> 本节是类图设计的核心：将 Neo4j 属性图模型抽象为 `GraphNode` / `GraphEdge` 继承树，所有图操作（构建、查询、剪枝、融合）均以此抽象层为契约。

### 3.1 图节点继承体系

```mermaid
classDiagram
class GraphNode {
    <<abstract>> <<Node>>
    +String id
    +Set~String~ labels
    +Map~String,Object~ properties
    +LocalDateTime createdAt
    +LocalDateTime updatedAt
    +getLabel() String
    +getProperty(String key) Object
    +setProperty(String key, Object val) void
    +hasLabel(String label) boolean
}

class StudentNode {
    <<Node>> "label: Student"
    +String studentNo
    +String name
    +String className
    +String grade
    +Integer enrollYear
    +String status
    +archive() void
    +changeClass(String c) void
}

class KnowledgePointNode {
    <<Node>> "label: KnowledgePoint"
    +String name
    +String normalizedName
    +String description
    +String subject
    +String source
    +String status
    +Float pageRank
    +Float degreeCentrality
    +merge(KnowledgePointNode t) void
    +deprecate() void
}

class KnowledgeCategoryNode {
    <<Node>> "label: KnowledgeCategory"
    +String name
    +String subject
    +Integer level
    +Integer sortOrder
    +getSubTree() List~KnowledgeCategoryNode~
    +checkIntegrity() IntegrityReport
}

class DocumentNode {
    <<Node>> "label: Document"
    +String name
    +String fileName
    +String subject
    +Long fileSize
    +String minioPath
    +String status
    +String failReason
    +Integer pageCount
    +Integer entityCount
    +Integer relationCount
    +Float avgConfidence
    +markFailed(String reason) void
    +getCoverage() CoverageReport
}

class EntityNode {
    <<Node>> "label: Entity"
    +String name
    +String entityType
    +Float confidence
    +Integer pageNumber
    +String description
    +String normalizedName
    +Long alignedKpId
    +isAligned() boolean
}

class ExamNode {
    <<Node>> "label: Exam"
    +String name
    +String subject
    +String grade
    +LocalDate examDate
    +String examType
    +Integer totalScore
    +String granularity
    +getQuestions() List~QuestionNode~
}

class QuestionNode {
    <<Node>> "label: Question"
    +Integer sectionNumber
    +Integer questionNumber
    +Integer maxScore
    +String questionType
    +Integer sortOrder
    +mapKnowledgePoints(List~Long~ kpIds) void
}

class EventNode {
    <<Node>> "label: Event"
    +String eventType
    +Float rawScore
    +Float allocatedScore
    +LocalDateTime eventTime
    +String sourceFile
    +Integer sourceRow
    +String description
    +calculateAllocatedScore() float
    +triggerWeightUpdate() void
}

GraphNode <|-- StudentNode
GraphNode <|-- KnowledgePointNode
GraphNode <|-- KnowledgeCategoryNode
GraphNode <|-- DocumentNode
GraphNode <|-- EntityNode
GraphNode <|-- ExamNode
GraphNode <|-- QuestionNode
GraphNode <|-- EventNode
```

### 3.2 图边继承体系

```mermaid
classDiagram
class GraphEdge {
    <<abstract>> <<Edge>>
    +String id
    +String type
    +String sourceNodeId
    +String targetNodeId
    +Map~String,Object~ properties
    +LocalDateTime createdAt
    +getType() String
    +getProperty(String key) Object
    +setProperty(String key, Object val) void
}

class MasteryEdge {
    <<Edge>> "type: MASTERS"
    "Student → KnowledgePoint"
    +Float weight
    +Float confidence
    +Integer eventCount
    +LocalDateTime lastEventAt
    +LocalDateTime lastUpdatedAt
    +adjustWeight(float delta, String trigger) void
    +applyDecay(DecayRule rule) void
}

class PrerequisiteEdge {
    <<Edge>> "type: PREREQUISITE_OF"
    "KnowledgePoint → KnowledgePoint"
    +Float strength
    +String description
    +detectCycle() boolean
}

class BelongsToEdge {
    <<Edge>> "type: BELONGS_TO"
    "KnowledgePoint → KnowledgeCategory"
}

class ChildOfEdge {
    <<Edge>> "type: CHILD_OF"
    "KnowledgeCategory → KnowledgeCategory"
    +Integer sortOrder
}

class ExtractsEdge {
    <<Edge>> "type: EXTRACTS"
    "Document → Entity"
    +Float confidence
}

class ReferencesEdge {
    <<Edge>> "type: REFERENCES/DERIVES/CONTAINS"
    "Entity → Entity"
    +String relationSubType
    +Float confidence
    +String context
}

class AlignedToEdge {
    <<Edge>> "type: ALIGNED_TO"
    "Entity → KnowledgePoint"
    +Float confidence
    +String alignmentMethod
}

class HasEventEdge {
    <<Edge>> "type: HAS_EVENT"
    "Student → Event"
}

class RelatesToEdge {
    <<Edge>> "type: RELATES_TO"
    "Event → KnowledgePoint"
    +Float score
    +Float maxScore
    +Float weightDelta
}

class ContainsEdge {
    <<Edge>> "type: CONTAINS"
    "Exam → Question"
    +Integer sortOrder
}

class TestsEdge {
    <<Edge>> "type: TESTS"
    "Question → KnowledgePoint"
    +Float weightRatio
}

class ScoresOnEdge {
    <<Edge>> "type: SCORES_ON"
    "Event → Question"
    +Float score
    +Float maxScore
}

class BelongsToExamEdge {
    <<Edge>> "type: BELONGS_TO_EXAM"
    "Event → Exam"
}

GraphEdge <|-- MasteryEdge
GraphEdge <|-- PrerequisiteEdge
GraphEdge <|-- BelongsToEdge
GraphEdge <|-- ChildOfEdge
GraphEdge <|-- ExtractsEdge
GraphEdge <|-- ReferencesEdge
GraphEdge <|-- AlignedToEdge
GraphEdge <|-- HasEventEdge
GraphEdge <|-- RelatesToEdge
GraphEdge <|-- ContainsEdge
GraphEdge <|-- TestsEdge
GraphEdge <|-- ScoresOnEdge
GraphEdge <|-- BelongsToExamEdge
```

### 3.3 宽图谱拓扑结构

```mermaid
classDiagram
direction LR

StudentNode --> MasteryEdge : source
MasteryEdge --> KnowledgePointNode : target

KnowledgePointNode --> PrerequisiteEdge : source
PrerequisiteEdge --> KnowledgePointNode : target

KnowledgePointNode --> BelongsToEdge : source
BelongsToEdge --> KnowledgeCategoryNode : target

KnowledgeCategoryNode --> ChildOfEdge : source
ChildOfEdge --> KnowledgeCategoryNode : target

DocumentNode --> ExtractsEdge : source
ExtractsEdge --> EntityNode : target

EntityNode --> ReferencesEdge : source
ReferencesEdge --> EntityNode : target

EntityNode --> AlignedToEdge : source
AlignedToEdge --> KnowledgePointNode : target

StudentNode --> HasEventEdge : source
HasEventEdge --> EventNode : target

EventNode --> RelatesToEdge : source
RelatesToEdge --> KnowledgePointNode : target

EventNode --> ScoresOnEdge : source
ScoresOnEdge --> QuestionNode : target

EventNode --> BelongsToExamEdge : source
BelongsToExamEdge --> ExamNode : target

ExamNode --> ContainsEdge : source
ContainsEdge --> QuestionNode : target

QuestionNode --> TestsEdge : source
TestsEdge --> KnowledgePointNode : target

class StudentNode {
    <<Node>>
    +String studentNo
    +String name
    +String className
}

class KnowledgePointNode {
    <<Node>>
    +String name
    +String subject
    +Float pageRank
}

class KnowledgeCategoryNode {
    <<Node>>
    +String name
    +Integer level
}

class DocumentNode {
    <<Node>>
    +String name
    +String subject
}

class EntityNode {
    <<Node>>
    +String name
    +Float confidence
}

class ExamNode {
    <<Node>>
    +String name
    +LocalDate examDate
}

class QuestionNode {
    <<Node>>
    +Integer maxScore
}

class EventNode {
    <<Node>>
    +String eventType
    +Float rawScore
}

class MasteryEdge {
    <<Edge>>
    +Float weight
    +Integer eventCount
}

class PrerequisiteEdge {
    <<Edge>>
    +Float strength
}

class RelatesToEdge {
    <<Edge>>
    +Float score
    +Float weightDelta
}

class TestsEdge {
    <<Edge>>
    +Float weightRatio
}

class AlignedToEdge {
    <<Edge>>
    +Float confidence
}

class ReferencesEdge {
    <<Edge>>
    +String relationSubType
}
```

### 3.4 Neo4j 属性图模型映射表

#### 节点映射

| 图节点类 | Neo4j Label | 主键属性 | 核心业务属性 |
|:--|:--|:--|:--|
| `StudentNode` | `:Student` | `studentNo` | `name, className, grade, enrollYear, status` |
| `KnowledgePointNode` | `:KnowledgePoint` | `normalizedName` | `name, description, subject, source, status, pageRank, degreeCentrality` |
| `KnowledgeCategoryNode` | `:KnowledgeCategory` | `name + subject` | `level, sortOrder` |
| `DocumentNode` | `:Document` | `minioPath` | `name, fileName, subject, fileSize, status, pageCount` |
| `EntityNode` | `:Entity` | `name + pageNumber + docId` | `entityType, confidence, description, normalizedName` |
| `ExamNode` | `:Exam` | `name + subject + examDate` | `grade, examType, totalScore, granularity` |
| `QuestionNode` | `:Question` | `examId + questionNumber` | `sectionNumber, maxScore, questionType, sortOrder` |
| `EventNode` | `:Event` | `eventTime + sourceRow` | `eventType, rawScore, allocatedScore, sourceFile, description` |

#### 边映射

| 图边类 | Neo4j Type | 方向 | 核心属性 |
|:--|:--|:--|:--|
| `MasteryEdge` | `MASTERS` | `(Student)→(KnowledgePoint)` | `weight, confidence, eventCount, lastEventAt` |
| `PrerequisiteEdge` | `PREREQUISITE_OF` | `(KnowledgePoint)→(KnowledgePoint)` | `strength, description` |
| `BelongsToEdge` | `BELONGS_TO` | `(KnowledgePoint)→(KnowledgeCategory)` | — |
| `ChildOfEdge` | `CHILD_OF` | `(KnowledgeCategory)→(KnowledgeCategory)` | `sortOrder` |
| `ExtractsEdge` | `EXTRACTS` | `(Document)→(Entity)` | `confidence` |
| `ReferencesEdge` | `REFERENCES` / `DERIVES` / `CONTAINS` | `(Entity)→(Entity)` | `relationSubType, confidence` |
| `AlignedToEdge` | `ALIGNED_TO` | `(Entity)→(KnowledgePoint)` | `confidence, alignmentMethod` |
| `HasEventEdge` | `HAS_EVENT` | `(Student)→(Event)` | — |
| `RelatesToEdge` | `RELATES_TO` | `(Event)→(KnowledgePoint)` | `score, maxScore, weightDelta` |
| `ContainsEdge` | `CONTAINS` | `(Exam)→(Question)` | `sortOrder` |
| `TestsEdge` | `TESTS` | `(Question)→(KnowledgePoint)` | `weightRatio` |
| `ScoresOnEdge` | `SCORES_ON` | `(Event)→(Question)` | `score, maxScore` |
| `BelongsToExamEdge` | `BELONGS_TO_EXAM` | `(Event)→(Exam)` | — |

---

## 四、用户与权限域（MySQL）

```mermaid
classDiagram
class User {
    -Long id
    -String username
    -String passwordHash
    -String realName
    -String email
    -String phone
    -String status
    -LocalDateTime createdAt
    -LocalDateTime lastLoginAt
    +createAccount(UserCreateDTO) User
    +disableAccount() void
    +unlockAccount() void
    +resetPassword(String newPassword) void
}

class Role {
    -Long id
    -String name
    -String code
    -String description
    -Boolean isPreset
    -LocalDateTime createdAt
    +updatePermissions(List~Long~ permIds) void
    +getPermissionTree() List~Permission~
}

class Permission {
    -Long id
    -String name
    -String code
    -String resourceType
    -String action
    -Long parentId
    -Integer sortOrder
    +isReadOnly() boolean
}

class UserRole {
    -Long id
    -Long userId
    -Long roleId
    -LocalDateTime assignedAt
    -String assignedBy
    +assign(userId, roleId) void
    +revoke() void
}

class RolePermission {
    -Long id
    -Long roleId
    -Long permissionId
    +grant() void
    +revoke() void
}

class LoginLog {
    -Long id
    -Long userId
    -String ipAddress
    -String result
    -String failReason
    -LocalDateTime loginAt
}

class AuditLog {
    -Long id
    -Long operatorId
    -String operationType
    -String targetType
    -Long targetId
    -String detail
    -String traceId
    -LocalDateTime operatedAt
}

User "1" --> "*" UserRole : 拥有
Role "1" --> "*" UserRole : 被分配
Role "1" --> "*" RolePermission : 包含
Permission "1" --> "*" RolePermission : 被授予
Permission "1" --> "*" Permission : 父子层级
User "1" --> "*" LoginLog : 产生
User "1" --> "*" AuditLog : 触发
```

---

## 五、文档处理域

```mermaid
classDiagram
class ParseTask {
    -Long id
    -Long documentId
    -String stage
    -String status
    -LocalDateTime startedAt
    -LocalDateTime completedAt
    -Long durationMs
    -String errorMessage
    -Integer retryCount
    +execute() void
    +retry() void
    +getProgress() ParseProgress
}

class ParseResult {
    -Long id
    -Long documentId
    -Long parseTaskId
    -Integer entityCount
    -Integer relationCount
    -JsonNode entityDistribution
    -JsonNode relationDistribution
    -JsonNode confidenceDistribution
    -List~JsonNode~ lowConfEntities
    -LocalDateTime createdAt
    +generateSummary() ParseSummary
}

class ExtractedEntity {
    -Long id
    -Long documentId
    -String name
    -String entityType
    -Float confidence
    -Integer pageNumber
    -String region
    -String description
    -String normalizedName
    +toEntityNode() EntityNode
    +match(ExtractedEntity other) float
}

class ExtractedRelation {
    -Long id
    -Long documentId
    -Long sourceEntityId
    -Long targetEntityId
    -String relationType
    -Float confidence
    -String context
    +toReferencesEdge() ReferencesEdge
}

class CsvImportTask {
    -Long id
    -String fileName
    -String csvType
    -Integer totalRows
    -Integer successRows
    -Integer failRows
    -String status
    -Long uploadedBy
    -LocalDateTime createdAt
    +validate() List~ValidationError~
    +execute(String conflictStrategy) ImportResult
}

class CsvImportRecord {
    -Long id
    -Long importTaskId
    -Integer rowNumber
    -JsonNode rawData
    -String status
    -String errorReason
    -Long studentId
    -Long examId
    +resolve() void
    +skip() void
}

DocumentNode "1" --> "0..*" ParseTask : 触发
ParseTask "1" --> "0..1" ParseResult : 产出
DocumentNode "1" --> "*" ExtractedEntity : 抽取
DocumentNode "1" --> "*" ExtractedRelation : 抽取
CsvImportTask "1" --> "*" CsvImportRecord : 包含
```

> **注意：** `DocumentNode` 为图节点（Neo4j 存储），其属性定义见 §3.1；此处仅展示处理流水线关联。
> `ExtractedEntity` / `ExtractedRelation` 为解析中间产物（MySQL），最终分别转化为 `EntityNode` / `ReferencesEdge` 导入 Neo4j。

---

## 六、图谱业务配置域（MySQL）

> 本节包含实体对齐、剪枝策略、权重规则等图谱相关的配置与管理实体，均存储于 MySQL。

### 6.1 实体对齐

```mermaid
classDiagram
class EntityAlignment {
    -Long id
    -Long sourceEntityId
    -Long targetEntityId
    -Float similarityScore
    -String confidence
    -String status
    -Long reviewedBy
    -LocalDateTime reviewedAt
    -JsonNode snapshotBefore
    +confirmMerge() void
    +reject() void
    +manualCorrect(String newName) void
    +rollback() void
}

class AlignmentAudit {
    -Long id
    -Long alignmentId
    -String operationType
    -Long operatedBy
    -JsonNode snapshotBefore
    -JsonNode snapshotAfter
    -LocalDateTime operatedAt
    +record(JsonNode event) void
}

EntityAlignment "1" --> "*" AlignmentAudit : 审计日志
```

### 6.2 剪枝策略与权重规则

```mermaid
classDiagram
class PruningStrategy {
    -Long id
    -String name
    -String taskType
    -Integer maxHops
    -Integer maxNeighborsPerHop
    -Float weightThreshold
    -List~String~ relationTypeFilter
    -Boolean isActive
    -LocalDateTime createdAt
    -LocalDateTime updatedAt
    +execute(Long rootKpId) SubGraph
    +compareWith(PruningStrategy other) CompareResult
    +saveVersion() PruningStrategyVersion
    +rollbackTo(Long versionId) void
}

class PruningStrategyVersion {
    -Long id
    -Long strategyId
    -Integer versionNumber
    -JsonNode paramsSnapshot
    -String changeDescription
    -LocalDateTime createdAt
    +diff(PruningStrategyVersion other) DiffResult
}

class WeightRule {
    -Long id
    -String name
    -String ruleType
    -String decayCurve
    -Float halfLifeDays
    -String subject
    -JsonNode eventAdjustments
    -Boolean isActive
    -LocalDateTime createdAt
    +calculateDecay(float weight, int days) float
    +getEventDelta(String eventType) float
    +simulate(List~Long~ studentIds, int days) SimulationResult
}

class WeightChangeLog {
    -Long id
    -Long masteryEdgeId
    -Float weightBefore
    -Float weightAfter
    -String triggerType
    -String triggerId
    -String source
    -LocalDateTime changedAt
    +query(LocalDateTime since, LocalDateTime until) List~WeightChangeLog~
}

class ScoreAllocationRule {
    -Long id
    -String name
    -String strategy
    -String subject
    -String examType
    -Boolean isGlobal
    -Boolean isActive
    -LocalDateTime createdAt
    +allocateScore(float rawScore, List~Long~ kpIds) Map~Long,Float~
}

class ScheduledTask {
    -Long id
    -String name
    -String description
    -String cronExpression
    -String jobClass
    -String status
    -Long dependsOnTaskId
    -LocalDateTime lastRunAt
    -LocalDateTime nextRunAt
    +trigger() TaskExecution
    +pause() void
    +resume() void
    +getDag() TaskDag
}

class TaskExecution {
    -Long id
    -Long scheduledTaskId
    -String status
    -LocalDateTime startedAt
    -LocalDateTime completedAt
    -Long durationMs
    -Integer processedCount
    -String errorStack
    -String diagnosisSuggestion
    +getDetail() ExecutionDetail
    +retry() void
}

PruningStrategy "1" --> "*" PruningStrategyVersion : 版本历史
WeightRule "1" --> "*" WeightChangeLog : 触发变更
ScheduledTask "1" --> "*" TaskExecution : 执行记录
ScheduledTask "1" --> "0..1" ScheduledTask : 依赖
```

---

## 七、图谱分析域

```mermaid
classDiagram
class SubGraph {
    -String id
    -String rootNodeId
    -Long pruningStrategyId
    -Integer nodeCount
    -Integer relationCount
    -Integer tokenEstimate
    -JsonNode nodes
    -JsonNode edges
    -LocalDateTime generatedAt
    +toPromptContext() String
    +getPath(String rootId, String leafId) List~String~
    +compare(SubGraph other) CompareResult
}

class GraphMetric {
    -Long id
    -LocalDateTime calculatedAt
    -Integer totalNodes
    -Integer totalRelations
    -Float avgDegree
    -Integer componentCount
    -List~Long~ isolatedNodeIds
    -JsonNode densityHeatmap
    -JsonNode communityDistribution
    +detectCommunities() List~CommunityResult~
    +findIsolatedNodes() List~Long~
    +generateHealthReport() HealthReport
}

class CommunityResult {
    -Long id
    -String communityLabel
    -String subject
    -List~Long~ memberNodeIds
    -Integer size
    -Float modularity
    +getBridgeNodes() List~Long~
    +overlapWith(CommunityResult other) float
}

class GraphSnapshot {
    -Long id
    -String name
    -Long studentId
    -JsonNode graphState
    -Integer nodeCount
    -Integer relationCount
    -LocalDateTime capturedAt
    +restore() SubGraph
    +compare(GraphSnapshot other) SnapshotDiff
}

SubGraph "1" --> "1" PruningStrategy : 由策略生成
GraphMetric "1" --> "*" CommunityResult : 包含
```

---

## 八、智能查询域

```mermaid
classDiagram
class AttributionQuery {
    -Long id
    -Long userId
    -Long studentId
    -Long knowledgePointId
    -String taskType
    -String naturalLanguageInput
    -Long pruningStrategyId
    -Long subGraphId
    -String status
    -LocalDateTime createdAt
    +execute() AttributionReport
    +followUp(Long rootCauseId) AttributionReport
    +getTracePath() List~KnowledgePointNode~
}

class AttributionReport {
    -Long id
    -Long queryId
    -String rootCauseSummary
    -List~JsonNode~ rootCauses
    -Integer totalTokenUsed
    -Long llmCallId
    -LocalDateTime generatedAt
    +toExportable(String format) byte[]
    +getEvidenceChain(String causeId) List~AttributionEvidence~
}

class AttributionEvidence {
    -Long id
    -Long reportId
    -String evidenceType
    -String description
    -Float confidence
    -String sourceCitation
    -JsonNode supportingData
    +verify() boolean
}

class TeachingSuggestion {
    -Long id
    -Long reportId
    -Long studentId
    -String severity
    -Integer stepCount
    -List~JsonNode~ steps
    -List~JsonNode~ resources
    -Integer estimatedMinutes
    -LocalDateTime createdAt
    +generatePlan() JsonNode
    +export(String format) byte[]
    +trackEffect() JsonNode
}

class ReviewPath {
    -Long id
    -Long studentId
    -List~JsonNode~ items
    -Integer totalEstimatedMinutes
    -LocalDateTime createdAt
    +prioritize() void
    +truncate(int availableMin) void
    +markReviewed(Long itemId) void
    +calculateEffect() JsonNode
}

class ReportExport {
    -Long id
    -Long reportId
    -String format
    -List~String~ selectedSections
    -String status
    -String minioPath
    -LocalDateTime createdAt
    +generate() void
    +batchExport(List~Long~ reportIds) byte[]
}

class ShareLink {
    -Long id
    -Long reportId
    -String token
    -String scope
    -String password
    -LocalDateTime expiresAt
    -Integer viewCount
    -LocalDateTime createdAt
    +generate() String
    +validate(String token) boolean
    +recordView(Long userId) void
}

class LlmConfig {
    -Long id
    -String modelName
    -String apiEndpoint
    -String apiKey
    -Integer maxTokens
    -Float temperature
    -String assignedTask
    -Boolean isActive
    -Integer dailyQuota
    -Integer dailyUsed
    +switchModel(String m) void
    +checkQuota() boolean
    +degrade() void
}

class PromptTemplate {
    -Long id
    -String taskType
    -String name
    -String content
    -List~String~ variableSlots
    -Integer version
    -Boolean isActive
    +render(Map~String,String~ vars) String
    +preview(Map~String,String~ vars) String
    +rollbackTo(Integer ver) void
}

class LlmCallLog {
    -Long id
    -String taskType
    -String modelName
    -Long promptTemplateId
    -Integer inputTokens
    -Integer outputTokens
    -Long latencyMs
    -String status
    -String errorMessage
    -String traceId
    -JsonNode requestSnapshot
    -LocalDateTime calledAt
    +replay() void
}

AttributionQuery "1" --> "0..1" AttributionReport : 生成
AttributionReport "1" --> "*" AttributionEvidence : 包含证据
AttributionReport "1" --> "0..1" TeachingSuggestion : 衍生
AttributionReport "1" --> "*" ReportExport : 导出
ReportExport "1" --> "0..*" ShareLink : 生成分享
LlmConfig "1" --> "*" PromptTemplate : 关联模板
LlmConfig "1" --> "*" LlmCallLog : 产生日志
PromptTemplate "1" --> "*" LlmCallLog : 被使用
```

---

## 九、运维运营域

```mermaid
classDiagram
class ServiceHealth {
    -Long id
    -String serviceName
    -String status
    -Integer connectionCount
    -Integer maxConnections
    -Float p50Latency
    -Float p99Latency
    -Integer queueLength
    -LocalDateTime lastCheckAt
    +check() String
    +getHistory(int days) List~JsonNode~
    +getDependencyTopology() JsonNode
}

class AlertRule {
    -Long id
    -String name
    -String serviceName
    -String level
    -String metricType
    -Float threshold
    -Integer durationSeconds
    -Integer cooldownMinutes
    -List~String~ notifyChannels
    -Boolean isActive
    +evaluate(Float metricValue) boolean
    +suppress(AlertRule other) boolean
    +escalate() void
}

class AlertEvent {
    -Long id
    -Long alertRuleId
    -String level
    -String message
    -Float currentValue
    -Float threshold
    -String status
    -Long acknowledgedBy
    -LocalDateTime triggeredAt
    -LocalDateTime resolvedAt
    +acknowledge(Long userId) void
    +resolve(String resolution) void
}

class BackupTask {
    -Long id
    -String type
    -String cronExpression
    -Integer retentionCount
    -Boolean isActive
    -LocalDateTime nextRunAt
    +execute() BackupRecord
    +validate() boolean
}

class BackupRecord {
    -Long id
    -Long backupTaskId
    -String type
    -Long fileSize
    -String minioPath
    -String checksum
    -Integer nodeCount
    -Integer relationCount
    -String status
    -LocalDateTime startedAt
    -LocalDateTime completedAt
    +verify() boolean
    +restore(String targetDb) JsonNode
}

class UsageMetric {
    -Long id
    -LocalDate date
    -String dimension
    -String dimensionValue
    -Integer dau
    -Integer wau
    -Integer mau
    -Integer queryCount
    -Integer exportCount
    -Integer pdfViewCount
    -Float growthRate
    +aggregate(LocalDate since, LocalDate until) List~UsageMetric~
    +compare(String p1, String p2) CompareResult
}

class DocumentProcessMetric {
    -Long id
    -LocalDate date
    -Integer totalPdfCount
    -Integer parsedPdfCount
    -Float parseSuccessRate
    -Integer totalPages
    -Integer totalEntities
    -Integer totalRelations
    -Integer csvRowCount
    -Integer coveredStudentCount
    -Float avgParseDuration
    +aggregate(LocalDate since, LocalDate until) List~DocumentProcessMetric~
    +getKnowledgeGaps() List~JsonNode~
    +findZombieDocuments() List~Long~
}

ServiceHealth "1" --> "*" AlertRule : 关联规则
AlertRule "1" --> "*" AlertEvent : 触发
BackupTask "1" --> "*" BackupRecord : 产生
```

---

## 十、跨域关系总览

```mermaid
classDiagram
%% ── 图节点 ↔ 图边（图存储层） ──
StudentNode "1" --> "*" MasteryEdge : "← MASTERS"
MasteryEdge "*" --> "1" KnowledgePointNode : "→ target"
StudentNode "1" --> "*" HasEventEdge : "← HAS_EVENT"
HasEventEdge "*" --> "1" EventNode : "→ target"
KnowledgePointNode "1" --> "*" PrerequisiteEdge : "← PREREQUISITE_OF"
PrerequisiteEdge "*" --> "1" KnowledgePointNode : "→ target"
KnowledgePointNode "*" --> "1" KnowledgeCategoryNode : "BELONGS_TO →"
KnowledgeCategoryNode "1" --> "*" KnowledgeCategoryNode : "CHILD_OF →"
DocumentNode "1" --> "*" ExtractsEdge : "← EXTRACTS"
ExtractsEdge "*" --> "1" EntityNode : "→ target"
EntityNode "1" --> "*" AlignedToEdge : "← ALIGNED_TO"
AlignedToEdge "*" --> "1" KnowledgePointNode : "→ target"
EntityNode "1" --> "*" ReferencesEdge : "← REFERENCES"
ReferencesEdge "*" --> "1" EntityNode : "→ target"
ExamNode "1" --> "*" ContainsEdge : "← CONTAINS"
ContainsEdge "*" --> "1" QuestionNode : "→ target"
QuestionNode "1" --> "*" TestsEdge : "← TESTS"
TestsEdge "*" --> "1" KnowledgePointNode : "→ target"
EventNode "1" --> "*" RelatesToEdge : "← RELATES_TO"
RelatesToEdge "*" --> "1" KnowledgePointNode : "→ target"
EventNode "1" --> "*" ScoresOnEdge : "← SCORES_ON"
ScoresOnEdge "*" --> "1" QuestionNode : "→ target"
EventNode "1" --> "*" BelongsToExamEdge : "← BELONGS_TO_EXAM"
BelongsToExamEdge "*" --> "1" ExamNode : "→ target"

%% ── 文档处理 → 图存储 ──
DocumentNode "1" --> "*" ExtractedEntity : "解析产生"
ExtractedEntity "*" --> "1" EntityNode : "转化为"

%% ── 业务配置 → 图存储 ──
EntityAlignment "*" --> "1" EntityNode : "对齐源/目标"
PruningStrategy "1" --> "*" SubGraph : "生成"
WeightChangeLog "*" --> "1" MasteryEdge : "变更"
ScoreAllocationRule "1" --> "*" EventNode : "分配"

%% ── 查询域 → 图存储 ──
AttributionQuery "*" --> "1" StudentNode : "查询目标"
AttributionQuery "*" --> "1" PruningStrategy : "使用策略"
AttributionEvidence "*" --> "1" MasteryEdge : "证据来源"
AttributionEvidence "*" --> "1" EventNode : "证据来源"
SubGraph "1" --> "1" AttributionReport : "上下文输入"

%% ── 用户域 → 业务域 ──
User "1" --> "*" EntityAlignment : 审核
User "1" --> "*" PruningStrategy : 配置
User "1" --> "*" WeightRule : 配置
User "1" --> "*" AttributionQuery : 发起
User "1" --> "*" ReportExport : 导出
```

---

## 十一、枚举与值对象

| 枚举 | 取值 | 所属域 |
|:--|:--|:--|
| `NodeLabel` | Student / KnowledgePoint / KnowledgeCategory / Document / Entity / Exam / Question / Event | 图节点 |
| `EdgeType` | MASTERS / PREREQUISITE_OF / BELONGS_TO / CHILD_OF / EXTRACTS / REFERENCES / DERIVES / CONTAINS / ALIGNED_TO / HAS_EVENT / RELATES_TO / TESTS / SCORES_ON / BELONGS_TO_EXAM | 图边 |
| `UserStatus` | ACTIVE / DISABLED / LOCKED / ARCHIVED | 用户 |
| `DocStatus` | UPLOADED / PARSING / PARSED / FAILED | 文档 |
| `TaskType` | ATTRIBUTION / REVIEW_RECOMMEND / CLASS_OVERVIEW | 剪枝策略 |
| `EntityType` | CONCEPT / FORMULA / THEOREM / DEFINITION | 抽取实体 |
| `StudentStatus` | ACTIVE / SUSPENDED / TRANSFERRED / GRADUATED | 学生 |
| `KpStatus` | ACTIVE / DEPRECATED / MERGED | 知识点 |
| `DecayCurve` | EXPONENTIAL / LINEAR / STEP | 权重衰减 |
| `AlignmentConfidence` | HIGH / MEDIUM / LOW | 实体对齐 |
| `ScoreGranularity` | TOTAL_ONLY / PER_QUESTION / PER_KNOWLEDGE_POINT | 得分粒度 |
| `ScoreAllocationStrategy` | AVERAGE / WEIGHTED / FULL | 得分分配 |
| `Severity` | MILD / MODERATE / SEVERE | 薄弱程度 |
| `ExportFormat` | PDF / HTML / MARKDOWN | 报告导出 |
| `ShareScope` | SCHOOL_TEACHERS / SPECIFIC_PARENT / PUBLIC | 分享范围 |
| `AlertLevel` | P0_CRITICAL / P1_WARNING / P2_INFO | 告警级别 |
| `BackupType` | FULL / INCREMENTAL | 备份类型 |
| `CallStatus` | SUCCESS / FAILED / TIMEOUT | LLM 调用 |

---

## 十二、统计摘要

| 包 | 类数量 | 核心类型 | 存储 |
|:--|:--:|:--|:--|
| 图节点抽象层 | 9 (1抽象+8实体) | GraphNode → 8 × Node | **Neo4j** |
| 图边抽象层 | 14 (1抽象+13实体) | GraphEdge → 13 × Edge | **Neo4j** |
| 用户与权限域 | 7 | User / Role / Permission | MySQL |
| 文档处理域 | 7 | DocumentNode(图) + ParseTask/CsvImport(MySQL) | Neo4j + MySQL |
| 图谱业务配置域 | 9 | EntityAlignment / PruningStrategy / WeightRule / ScheduledTask | MySQL |
| 图谱分析域 | 4 | SubGraph / GraphMetric / CommunityResult / GraphSnapshot | MySQL + Redis |
| 智能查询域 | 11 | AttributionQuery / Report / LlmConfig / PromptTemplate | MySQL + Redis |
| 运维运营域 | 7 | ServiceHealth / AlertRule / BackupTask / UsageMetric | MySQL |
| **合计** | **68** | | Neo4j: 22 <<Node>>/<<Edge>>, MySQL: 44 |

---

## 十三、与开发架构图的映射

| 开发架构层 | 对应类 |
|:--|:--|
| **网关层（认证/鉴权）** | `User` / `Role` / `Permission` / `UserRole` / `RolePermission` |
| **应用层-文档处理** | `DocumentNode`(图) + `ParseTask` / `ParseResult` / `ExtractedEntity` / `CsvImportTask` |
| **应用层-图处理** | 全部 `GraphNode`/`GraphEdge` 子类 + `WeightRule` / `WeightChangeLog` / `ScheduledTask` |
| **应用层-图分析** | `PruningStrategy` / `EntityAlignment` / `SubGraph` / `GraphMetric` / `CommunityResult` |
| **应用层-智能查询** | `AttributionQuery` / `AttributionReport` / `TeachingSuggestion` / `ReviewPath` / `ReportExport` |
| **应用层-基础数据** | `User`/`Role`/`Permission` + 运维运营域全部 |
| **应用层-LLM 网关** | `LlmConfig` / `PromptTemplate` / `LlmCallLog` |
| **基础设施层** | 类图中不体现（Neo4j / MySQL / Redis / MinIO / RabbitMQ 为外部组件） |
| **横向切面** | `AuditLog` / `LoginLog` / `LlmCallLog` / `WeightChangeLog` / `AlertEvent` / `BackupRecord` |

---

## 十四、与物理部署图的兼容性

| 部署组件 | 类图映射 | 说明 |
|:--|:--|:--|
| **Neo4j :7687** | 全部 `GraphNode`/`GraphEdge` 子类 | Spring Data Neo4j 映射，Cypher 查询 |
| **MySQL :3306** | 全部配置/日志/RBAC/统计类 | JPA/Hibernate 持久化 |
| **MinIO :9000** | `DocumentNode.minioPath` / `BackupRecord.minioPath` / `ReportExport.minioPath` | 对象存储路径 |
| **Redis :6379** | `LlmConfig.dailyUsed` / `SubGraph` 缓存 / Session | 配额计数 + 热点缓存 |
| **RabbitMQ :5672** | `ParseTask` / `WeightChangeLog` / `TaskExecution` 异步消费 | 领域事件驱动 |
| **外部 LLM API** | `LlmConfig.apiEndpoint` / `LlmCallLog` | HTTPS 出站调用 |
| **Nginx :80** | 无类图映射 | 反向代理，不参与业务逻辑建模 |

---

> 下一步：可基于此节点/边抽象层进行 Cypher 查询模板设计、剪枝算法伪代码、或全链路时序图（PDF 上传→实体抽取→图节点/边导入→宽图谱融合→归因查询）。
