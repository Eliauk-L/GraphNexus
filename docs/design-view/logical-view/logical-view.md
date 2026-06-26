# GraphNexus 系统类图设计

> 基于图谱技术的 AI 上下文处理与精准问答系统 — 逻辑视图 · 类图
>
> 版本：v2.0 | 基于实际代码实现 | 修订日期：2026-06-23
>
> 参考文档：[需求文档](../基于图谱技术的 AI 上下文处理与精准问答系统.md)、[用户故事 MVP](../user-stories/user-stories-mvp.md)

---

## 一、问题域分析

### 1.1 系统目标

构建基于图谱的 AI 上下文处理系统，核心流程：

```
PDF 教辅 → LLM 实体/关系抽取 → 文档图谱 (Neo4j)
CSV 成绩 → 事件节点导入 → 成绩图谱 (Neo4j)
        ↘            ↙
        宽图谱融合 (KP 对齐 + MASTERS 权重计算)
              │
        任务驱动剪枝 (按意图类型裁剪子图)
              │
        LLM 分析 (归因诊断/薄弱分析)
```

### 1.2 已实现的核心流程

| 流程 | 入口 | 核心组件 |
|------|------|---------|
| PDF 文档图谱化 | API: 文档上传 | ExtractionService (LLM Prompt) → 构建 Entity/KP/Category 节点及关系边 |
| CSV 成绩事件化 | API: 成绩导入 | ConstructionService → 构建 Student/Exam/KP 节点 + ATTENDED/TESTED 边 |
| 宽图谱融合 | API: 融合触发 | FusionService → KP 匹配(M1/M2/N1) → KP 合并 → MASTERS 重算 |
| 任务驱动剪枝 | API: 分析查询 | SubgraphPruningStrategy → 按意图(StudentDiagnosis/ClassWeakness)裁剪子图 |
| LLM 分析 | API: 分析/查询 | LlmGateway.chat() → 返回分析结果 |
| 用户认证 | JWT 认证 | JwtTokenProvider + UserAccountDO/RoleDO (MySQL) |

### 1.3 分层架构

```
API 层 (Controller)         → AnalysisController / AuthController / ConfigController / ...
  │
应用层 (Application)         → ExtractionService / FusionService / ConstructionService
  │                              QueryService / AuthService / TokenService
  │
基础设施层 (Infrastructure)  → Neo4j (图谱节点/边) / MySQL (用户/角色/配置)
  │                              LLM (LangChain4j) / MinIO (PDF 存储)
```

---

## 二、类图设计

### 2.1 图谱节点继承体系（Neo4j）

> **实现位置**: `infrastructure/neo4j/node/`

```
┌──────────────────────────────────────────────────────────────┐
│                 «abstract» GraphNode                         │
├──────────────────────────────────────────────────────────────┤
│ - id: String              (UUID, @Id @GeneratedValue)        │
│ - nodeType: String        (对应 NodeType.label)              │
│ - documentId: String      (关联文档ID, 所有从文档抽取的节点)   │
│ - createdAt: LocalDateTime                                    │
│ - properties: Map<String, Object>                             │
├──────────────────────────────────────────────────────────────┤
│ + toProperties(): Map<String, Object>                         │
│   (多态方法，子类覆盖以追加自身字段)                            │
└──────────────────────────┬───────────────────────────────────┘
                           │
    ┌──────────┬───────────┼──────────┬──────────┬──────────┬──────────┐
    │          │           │          │          │          │          │
    ▼          ▼           ▼          ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌──────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌──────────┐
│FileNode│ │Entity  │ │Knowledge │ │Knowledg│ │Student │ │ExamNode│ │Subject   │
│        │ │Node    │ │PointNode │ │Category│ │Node    │ │        │ │Node      │
│@Node   │ │@Node   │ │@Node     │ │Node    │ │@Node   │ │@Node   │ │@Node     │
│Document│ │Entity  │ │Knowledge │ │@Node   │ │Student │ │Exam    │ │Subject   │
│        │ │        │ │Point     │ │Knowledge│         │ │        │ │          │
│        │ │        │ │          │ │Category│         │ │        │ │          │
├────────┤ ├────────┤ ├──────────┤ ├────────┤ ├────────┤ ├────────┤ ├──────────┤
│name    │ │entity  │ │name      │ │name    │ │student │ │examNo  │ │name      │
│pageCnt │ │Type    │ │description│ │level   │ │No      │ │name    │ │(学科名称) │
│        │ │name    │ │gradeLevel│ │parent  │ │name    │ │examDate│ │          │
│        │ │origText│ │fusionSrc │ │Name    │ │class   │ │        │ │          │
│        │ │pageNum │ │          │ │        │ │Name    │ │        │ │          │
│        │ │metadata│ │          │ │        │ │grade   │ │        │ │          │
└────────┘ └────────┘ └──────────┘ └────────┘ └────────┘ └────────┘ └──────────┘
```

**NodeType 枚举**：

```java
«enum» NodeType (注册 label 和 Java 类)
├── DOCUMENT          "Document"           → FileNode
├── ENTITY            "Entity"             → EntityNode
├── KNOWLEDGE_POINT   "KnowledgePoint"     → KnowledgePointNode
├── KNOWLEDGE_CATEGORY "KnowledgeCategory" → KnowledgeCategoryNode
├── STUDENT           "Student"            → StudentNode
├── EXAM              "Exam"               → ExamNode
└── SUBJECT           "Subject"            → SubjectNode
```

**EntityType 枚举**（EntityNode 的分类标签）：

```java
«enum» EntityType
├── DEFINITION  "概念定义"     如"二次函数是指形如 y=ax²+bx+c（a≠0）的函数"
├── FORMULA     "公式"        如"y=ax²+bx+c"
├── CONCEPT     "概念"        如"对称轴"
├── EXAMPLE     "例题"        如"已知二次函数 y=x²-4x+3，求顶点坐标..."
└── SOLUTION    "解法"        如"配方法"
```

**KnowledgePointNode 的 fusionSource**：

```java
fusionSource: String
├── "DOCUMENT"    — 从 PDF 文档抽取
├── "CSV_IMPORT"  — 从 CSV 成绩导入
└── "FUSION"      — 融合后生成的规范节点
```

---

### 2.2 图谱边继承体系（Neo4j）

> **实现位置**: `infrastructure/neo4j/edge/`

```
┌──────────────────────────────────────────────────────────────┐
│                 «abstract» GraphEdge                          │
├──────────────────────────────────────────────────────────────┤
│ - sourceNodeId: String                                       │
│ - targetNodeId: String                                       │
│ - edgeType: String          (对应 EdgeType.relationshipType)  │
│ - createdAt: LocalDateTime                                    │
│ - weight: Double            (0~1, 默认 1.0)                  │
│ - description: String        (边描述)                         │
│ - properties: Map<String, Object>                             │
└──────────────────────────┬───────────────────────────────────┘
                           │
    ┌──────┬──────┬───────┬───────┬───────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
    │      │      │       │       │       │      │      │      │      │      │      │      │
    ▼      ▼      ▼       ▼       ▼       ▼      ▼      ▼      ▼      ▼      ▼      ▼      ▼
┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐
│Extrcts│Refer-│Derives│Contai│Aligned│Belongs│ChildOf│Prereq│Attend│Tested│Master│BlngsTo│
│Edge  │encesE│Edge  │nsEdge│ToEdge│ToEdge│Edge  │uisite│edEdge│Edge  │sEdge │SubjE  │
│      │dge   │      │      │      │      │      │Edge  │      │      │      │       │
│File→│Ent→  │Ent→  │Ent→  │Ent→  │KP→   │Cat→  │KP→KP │Stu→  │Exam→ │Stu→  │KP│    │
│Ent   │Ent   │Ent   │Ent   │KP    │Cat   │Cat   │      │Exam  │KP    │KP    │Exam│   │
│      │      │      │      │      │      │      │      │      │      │      │File→│
│      │      │      │      │      │      │      │      │      │      │      │Subj  │
├──────┤├──────┤├──────┤├──────┤├──────┤├──────┤├──────┤├──────┤├──────┤├──────┤├──────┤
│(纯结构)│desc  │desc  │desc  │(纯结构)│(纯结构)│(纯结构)│strength│(纯结构)│(纯结构)│(衍生边)│(纯结构)│
│      │      │      │      │      │      │      │0~1     │      │      │      │       │
└──────┘└──────┘└──────┘└──────┘└──────┘└──────┘└──────┘└──────┘└──────┘└──────┘└──────┘
```

**EdgeType 枚举**：

```java
«enum» EdgeType (集中管理所有 Neo4j 边类型的名称/方向/中文说明)
├── EXTRACTS            "EXTRACTS"            文档抽取  File→Entity
├── REFERENCES          "REFERENCES"          实体引用  Entity→Entity
├── DERIVES             "DERIVES"             推导关系  Entity→Entity
├── CONTAINS            "CONTAINS"            包含关系  Entity→Entity
├── ALIGNED_TO          "ALIGNED_TO"          实体对齐  Entity→KP
├── BELONGS_TO          "BELONGS_TO"          分类归属  KP→Category
├── CHILD_OF            "CHILD_OF"            分类层级  子Category→父Category
├── PREREQUISITE_OF     "PREREQUISITE_OF"     前置依赖  前置KP→后置KP
├── ATTENDED            "ATTENDED"            学生参加   Student→Exam
├── TESTED              "TESTED"              考试考查   Exam→KP
├── MASTERS             "MASTERS"             学生掌握度 Student→KP (衍生边)
└── BELONGS_TO_SUBJECT  "BELONGS_TO_SUBJECT"  学科归属   KP|Exam|File→Subject
```

**MASTERS 边说明**：衍生边，由 `MastersRecalculationService` 全量重算覆盖。权重 `weight` 为掌握度（0~1），`description` 存 JSON 摘要（考试次数/最近考试日期/各次得分率）。

---

### 2.3 图构建 — 文档抽取与 CSV 导入

> **实现位置**: `application/graph/construction/`

```
┌──────────────────────────────────────────────────────────────┐
│                  ExtractionService                            │
│                  (LLM 文档实体/关系抽取)                        │
├──────────────────────────────────────────────────────────────┤
│ - llmGateway: LlmGateway                                     │
│ - promptBuilder: ExtractionPromptBuilder                     │
│ - jsonParser: ExtractionJsonParser                           │
│ - validator: ExtractionValidator                             │
├──────────────────────────────────────────────────────────────┤
│ + extractPage(textbookId, pageText: String):                 │
│     ExtractionRawResult   (原始 LLM JSON 输出)                │
│ + extractDocument(textbookId): ExtractionResultBO           │
│     (全文档抽取，分页调用 LLM)                                  │
└──────────────────────┬───────────────────────────────────────┘
                       │ 产出
                       ▼
┌──────────────────────────────────────────────────────────────┐
│                  ExtractionResultBO                           │
│                  (文档抽取结果)                                 │
├──────────────────────────────────────────────────────────────┤
│ - entities: List<GraphNodeData>   (EntityNode + KP 节点数据)  │
│ - edges: List<GraphEdgeData>      (关系边数据)                │
│ - categories: List<GraphNodeData> (知识分类节点数据)           │
├──────────────────────────────────────────────────────────────┤
│ + isValid(): boolean                                          │
│ + getEntityCount(): int                                       │
│ + getEdgeCount(): int                                         │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                  ConstructionService                          │
│                  (图构建编排 + CSV 导入)                        │
├──────────────────────────────────────────────────────────────┤
│ - neo4jClient: Neo4jClient                                    │
│ - extractionService: ExtractionService                       │
│ - dataConverter: GraphDataConverter                          │
├──────────────────────────────────────────────────────────────┤
│ + buildDocumentGraph(textbookId): GraphSubgraphBO           │
│   ┌──────────────────────────────────────────────────┐        │
│   │ 1. extractionService.extractDocument(textbookId) │        │
│   │ 2. dataConverter.convert(result) → 批量创建节/边   │        │
│   │ 3. 发布 GraphConstructedEvent                     │        │
│   └──────────────────────────────────────────────────┘        │
│ + importGrades(csvFile): ImportResult                         │
│   创建 Student/Exam/KP 节点 + ATTENDED/TESTED 边             │
│   发布 GradeGraphEvent → 触发融合                              │
└──────────────────────────────────────────────────────────────┘

GraphDataConverter   (双向转换器)
├── toNodeData(GraphNode): GraphNodeData
├── toEdgeData(GraphEdge): GraphEdgeData
└── toGraphNode(data: GraphNodeData): GraphNode

GraphNodeData (L2 传输对象)        GraphEdgeData (L2 传输对象)
├── id: String                    ├── sourceNodeId: String
├── nodeType: String              ├── targetNodeId: String
├── labels: List<String>          ├── edgeType: String
├── properties: Map<String,Obj>   └── properties: Map<String,Obj>
└── displayName: String
```

---

### 2.4 宽图谱融合引擎（策略模式）

> **实现位置**: `application/analysis/fusion/`

```
┌──────────────────────────────────────────────────────────────┐
│                     FusionService                             │
│                     (融合编排服务)                              │
├──────────────────────────────────────────────────────────────┤
│ - kpMatchingStrategy: KpMatchingStrategy                     │
│ - weightCalcStrategy: WeightCalculationStrategy              │
│ - mastersService: MastersRecalculationService                │
│ - neo4jClient, fusionProperties, gdsAdapter                  │
├──────────────────────────────────────────────────────────────┤
│ + executeFusion(subject?, trigger): FusionExecuteResult      │
│   ┌──────────────────────────────────────────────────┐        │
│   │ 1. 按学科分组 KP (BELONGS_TO_SUBJECT)            │        │
│   │ 2. kpMatchingStrategy.match() → 识别同名/同义 KP  │        │
│   │ 3. 合并冗余 KP (融合组 → 规范节点)                 │        │
│   │ 4. 重定向边 (ALIGNED_TO / BELONGS_TO 等)          │        │
│   │ 5. MastersRecalculationService.recalculate()      │        │
│   │    → 全量重算 MASTERS 边                          │        │
│   │ 6. 记录融合日志，返回结果                           │        │
│   └──────────────────────────────────────────────────┘        │
│ + rollbackFusion(fusionLogId): FusionRollbackResult          │
│ + getFusionStatus(): FusionStatusResult                      │
└──────────────────────┬───────────────────────────────────────┘
                       │ 依赖
                       ▼
┌──────────────────────────────────────────────────────────────┐
│       «interface» KpMatchingStrategy                          │
│       (KP 匹配策略 — 判断两个知识点是否同一概念)                  │
├──────────────────────────────────────────────────────────────┤
│ + match(a: KpCandidate, b: KpCandidate): double   (0~1)     │
│ + getName(): String            (策略标识，对应 yml 配置)       │
└──────┬────────────────────────┬──────────────────────────────┘
       │                        │
       ▼                        ▼
┌──────────────────┐  ┌──────────────────────┐
│ ExactMatchStrategy│  │ FuzzyMatchStrategy    │
│ (精确匹配)        │  │ (模糊匹配/编辑距离)     │
├──────────────────┤  ├──────────────────────┤
│ getName():       │  │ getName():           │
│   "exact"        │  │   "fuzzy"            │
└──────────────────┘  └──────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│       «interface» WeightCalculationStrategy                   │
│       (权重计算策略 — 从多条成绩聚合 MASTERS 掌握度)             │
├──────────────────────────────────────────────────────────────┤
│ + calculate(records: List<TestedRecord>): WeightResult       │
│ + getName(): String            (策略标识)                     │
└──────┬───────────────────────────────────────────────────────┘
       │                        │
       ▼                        ▼
┌──────────────────────┐  ┌──────────────────────┐
│ SimpleAverageStrategy│  │  TimeDecayStrategy    │
│ (简单平均)            │  │  (时间衰减加权)        │
├──────────────────────┤  ├──────────────────────┤
│ getName(): "simple"  │  │ getName():           │
│                      │  │   "time_decay"       │
│                      │  ├──────────────────────┤
│                      │  │ - decayConfig:       │
│                      │  │   TimeDecayProperties│
│                      │  │   (halfLifeDays,     │
│                      │  │    curveType, epoch) │
└──────────────────────┘  └──────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│              MastersRecalculationService                      │
│              (MASTERS 边全量重算服务)                           │
├──────────────────────────────────────────────────────────────┤
│ - weightCalcStrategy: WeightCalculationStrategy              │
│ - neo4jClient: Neo4jClient                                    │
├──────────────────────────────────────────────────────────────┤
│ + recalculate(subject?): int   (返回创建的 MASTERS 边数量)     │
│   ┌──────────────────────────────────────────────────┐        │
│   │ 1. 查询所有 Student-[ATTENDED]->Exam-[TESTED]->KP│        │
│   │ 2. 按 (Student, KP) 聚合 TestedRecord 列表        │        │
│   │ 3. weightCalcStrategy.calculate(records)         │        │
│   │ 4. 批量 MERGE MastersEdge (weight + description)  │        │
│   └──────────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────┘

FusionGroup (record)                 FusionExecuteResult (record)
├── groupId: int                    ├── fusionLogId: Long
├── sourceKpIds: List<String>       ├── mergedKpGroupCount: int
├── targetKpId: String              └── mastersEdgeCount: int
├── sourceKpProps: List<Map>        KpCandidate (data class)
├── targetKpProps: Map<String,Obj>  ├── id: String
└── redirectedEdges: List<EdgeRedir>├── name: String
                                    └── properties: Map<String,Obj>
                                    
TestedRecord (data class)           WeightResult (record)
├── examDate: LocalDate            ├── weight: double (0~1)
├── score: Double                  └── summaryJson: String
└── totalScore: Double                  (考试次数/最近日期/得分率)
```

---

### 2.5 任务驱动剪枝（策略模式）

> **实现位置**: `application/analysis/strategy/` + `application/analysis/model/`

```
┌──────────────────────────────────────────────────────────────┐
│       «interface» SubgraphPruningStrategy                     │
│       (图剪枝策略 — @FunctionalInterface)                     │
├──────────────────────────────────────────────────────────────┤
│ + prune(request: PruningRequest): PrunedSubgraph             │
│                                                               │
│ 实现类职责：                                                   │
│  · 根据 PruningRequest.intent 执行 Cypher 剪枝逻辑             │
│  · MASTERS 不可用时自动降级为 TESTED 路径                      │
│  · 在 PruningMeta 中标记降级状态                                │
└──────────────────────────┬───────────────────────────────────┘
                           │ 实现 (v1)
                           ▼
              ┌────────────────────────────┐
              │  StudentDiagnosisStrategy  │
              │  (学生诊断剪枝策略)          │
              ├────────────────────────────┤
              │ intent: "STUDENT_DIAGNOSIS"│
              │                            │
              │ prune逻辑:                 │
              │  1. 找 Student→MASTERS 弱  │
              │  2. 沿 PREREQUISITE_OF 溯源│
              │  3. 沿 BELONGS_TO 扩分类  │
              │  4. Token 预算截断         │
              └────────────────────────────┘

PruningRequest (record)
├── intent: String       "STUDENT_DIAGNOSIS" / "CLASS_WEAKNESS_OVERVIEW"
├── entityId: String     学生学号 或 班级名称
├── subject: String      学科（如"数学"）
└── params: Map<String, Object>   { weakThreshold, maxHops, ... }

PrunedSubgraph (record)
├── nodes: List<GraphNodeData>       (L2 传输对象，按优先级排序)
├── edges: List<GraphEdgeData>       (L2 传输对象)
└── meta: PruningMeta
       PruningMeta (record)
       ├── strategy: String          策略名称
       ├── mastersAvailable: boolean MASTERS 边是否可用
       ├── weakThreshold: double     弱掌握度阈值
       ├── maxHops: int              PREREQUISITE_OF 遍历跳数
       ├── totalNodes: int           子图节点总数
       ├── totalEdges: int           子图边总数
       ├── truncated: boolean        是否触发 Token 截断
       └── truncatedNodeNames: List  被截断的节点名称
```

---

### 2.6 LLM 网关

> **实现位置**: `common/LlmGateway.java` + `infrastructure/llm/client/Langchain4jLlmGateway.java`

```
┌──────────────────────────────────────────────────────────────┐
│         «interface» LlmGateway                                │
│         (LLM 调用网关 — 统一契约，隔离具体 API)                  │
├──────────────────────────────────────────────────────────────┤
│ + chat(systemPrompt: String, userMessage: String): String    │
│   ┌──────────────────────────────────────────────────┐        │
│   │ @param systemPrompt  系统提示词（角色设定/格式约束）│        │
│   │ @param userMessage   用户消息（待处理文本内容）     │        │
│   │ @return LLM 原始文本响应                         │        │
│   │ @throws BusinessException  错误码 C0001          │        │
│   └──────────────────────────────────────────────────┘        │
│                                                               │
│ 设计原则：                                                     │
│  · 所有 LLM 调用必须通过此接口，禁止业务代码直接使用具体 API    │
│  · 为后续模型路由/配额/降级/审计预留扩展点                       │
│  · 当前实现：OpenAI 兼容协议（通过 LangChain4j）               │
└──────────────────────┬───────────────────────────────────────┘
                       │ 实现
                       ▼
┌──────────────────────────────────────────────────────────────┐
│              Langchain4jLlmGateway                             │
│              (LangChain4j 实现 · OpenAI 兼容协议)               │
├──────────────────────────────────────────────────────────────┤
│ - baseUrl: String         (${spring.ai.openai.base-url})     │
│ - apiKey: String          (${spring.ai.openai.api-key})     │
│ - model: String           (${spring.ai.openai.chat.          │
│                             options.model})                   │
│ - temperature: Double     (默认 0.3)                          │
│ - maxTokens: Integer      (默认 -1 不限制)                    │
│ - timeout: Duration       (默认 120s)                        │
│ - logRequests: Boolean    (开发环境调试)                       │
├──────────────────────────────────────────────────────────────┤
│ + chat(systemPrompt, userMessage): String                    │
│   内部: OpenAiChatModel + SystemMessage + UserMessage        │
└──────────────────────────────────────────────────────────────┘
```

---

### 2.7 用户认证模型（MySQL）

> **实现位置**: `infrastructure/mysql/auth/entity/`

```
┌──────────────────────────────────────────────────────────────┐
│                    UserAccountDO                              │
│                    (用户账号 · MySQL)                          │
├──────────────────────────────────────────────────────────────┤
│ id: Long                    username: String (unique)        │
│ password: String            realName: String                  │
│ status: AccountStatus       ENABLED / DISABLED               │
│ createTime: LocalDateTime   updateTime: LocalDateTime        │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                      RoleDO                                   │
│                    (角色定义 · MySQL · 预置 5 行)                │
├──────────────────────────────────────────────────────────────┤
│ id: Long                    code: String (unique)             │
│   ┌─────────────────────────────────────────────────┐         │
│   │ ADMIN       "超级管理员" (继承 TEACHER 权限)      │         │
│   │ TEACHER     "教师"                               │         │
│   │ STUDENT     "学生"                               │         │
│   │ OPS_STAFF   "运维人员"                           │         │
│   │ OPS_MANAGER "运营人员"                           │         │
│   └─────────────────────────────────────────────────┘         │
│ name: String                description: String               │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    UserRoleDO                                 │
│                    (用户-角色关联 · MySQL · N:M 多对多)         │
├──────────────────────────────────────────────────────────────┤
│ userId: Long                roleId: Long                      │
└──────────────────────────────────────────────────────────────┘

TokenPair (record)                  UserPrincipal (implements UserDetails)
├── accessToken: String            ├── id: Long
├── refreshToken: String           ├── username: String
└── expires: Long                   ├── roles: Set<String>
                                    └── enabled: boolean
```

---

### 2.8 事件驱动协作

> **实现位置**: `common/event/` + 各模块 `listener/`

```
┌──────────────────────────────────────────────────────────────┐
│              GraphChangedEvent                                │
│              (图谱变更事件基类)                                 │
├──────────────────────────────────────────────────────────────┤
│ - source: String     (事件来源模块标识)                        │
│ - timestamp: LocalDateTime                                   │
└──────────────────────────┬───────────────────────────────────┘
                           │ 发布
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
    │GraphConstructe│ │GradeGraph    │ │TextbookParsed    │
    │dEvent        │ │Event         │ │Event             │
    │(图构建完成)   │ │(成绩导入完成) │ │(PDF解析完成)     │
    └──────┬───────┘ └──────┬───────┘ └────────┬─────────┘
           │                │                   │
           ▼                ▼                   ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
    │GradeGraph    │ │FusionService │ │Construction      │
    │EventListener │ │.execute      │ │Service           │
    │→ 触发挥点评估│ │Fusion()      │ │.buildGraph()     │
    │   + 融合     │ │              │ │→ 触发表解析      │
    └──────────────┘ └──────────────┘ └──────────────────┘
```

---

### 2.9 文件存储接口

> **实现位置**: `infrastructure/storage/`

```
┌──────────────────────────────────────────────────────────────┐
│       «interface» FileStorageService                          │
├──────────────────────────────────────────────────────────────┤
│ + uploadFile(fileName, inputStream): String   → 返回 fileUrl │
│ + downloadFile(fileName): InputStream                         │
│ + deleteFile(fileName): void                                  │
└──────────────────────┬───────────────────────────────────────┘
                       │ 实现
                       ▼
┌──────────────────────────────────────────────────────────────┐
│              MinioFileStorageService                          │
│              (MinIO 对象存储实现)                               │
├──────────────────────────────────────────────────────────────┤
│ - minioClient: MinioClient                                    │
│ - bucketName: String    (${minio.bucket-name})               │
│ - endpoint: String                                           │
├──────────────────────────────────────────────────────────────┤
│ + uploadFile(fileName, inputStream, contentType): String     │
│ + getPresignedUrl(fileName): String     (临时下载链接)        │
│ + fileExists(fileName): boolean                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 三、枚举汇总

```java
«enum» NodeType         «enum» EdgeType            «enum» EntityType
├── DOCUMENT            ├── EXTRACTS               ├── DEFINITION
├── ENTITY              ├── REFERENCES             ├── FORMULA
├── KNOWLEDGE_POINT     ├── DERIVES                ├── CONCEPT
├── KNOWLEDGE_CATEGORY  ├── CONTAINS               ├── EXAMPLE
├── STUDENT             ├── ALIGNED_TO             └── SOLUTION
├── EXAM                ├── BELONGS_TO
└── SUBJECT             ├── CHILD_OF               «enum» AccountStatus
                        ├── PREREQUISITE_OF        ├── ENABLED
                        ├── ATTENDED               └── DISABLED
                        ├── TESTED
                        ├── MASTERS
                        └── BELONGS_TO_SUBJECT
```

---

## 四、设计模式应用总结

| 模式 | 应用位置 | 解决的问题 |
|------|---------|-----------|
| **继承体系** | GraphNode + 7 子类、GraphEdge + 12 子类 | 复用公共字段（id/nodeType/documentId/properties），子类扩展专属字段 |
| **策略模式** | KpMatchingStrategy (exact/fuzzy) | KP 匹配算法可配置切换 |
| **策略模式** | WeightCalculationStrategy (simple/time_decay) | 权重计算策略可配置切换 |
| **策略模式** | SubgraphPruningStrategy (@FunctionalInterface) | 按意图类型执行不同剪枝逻辑，可独立扩展 |
| **多态方法** | GraphNode.toProperties() | 子类覆盖以追加自身字段，替代 instanceof 链 |
| **事件驱动** | GraphConstructedEvent → GradeGraphEventListener → FusionService | 成绩导入/文档解析完成后自动触发融合，模块解耦 |
| **适配器模式** | LlmGateway ← Langchain4jLlmGateway | 隔离 LLM API 细节，为模型路由/降级预留扩展点 |
| **DDD 传输对象** | GraphNodeData / GraphEdgeData (L2) ⇄ GraphNode / GraphEdge (L3) | 解耦 Neo4j 实体与业务层，GraphDataConverter 双向转换 |

---

## 五、类图与接口的对应关系

| 模块 | 核心接口/抽象类 | 主要实现类 | 设计模式 |
|:--|:--|:--|:--|
| Neo4j 节点 | GraphNode (abstract) | FileNode, EntityNode, KnowledgePointNode, KnowledgeCategoryNode, StudentNode, ExamNode, SubjectNode | 继承体系 + 多态 |
| Neo4j 边 | GraphEdge (abstract) | ExtractsEdge, ReferencesEdge, DerivesEdge, ContainsEdge, AlignedToEdge, BelongsToEdge, ChildOfEdge, PrerequisiteEdge, AttendedEdge, TestedEdge, MastersEdge, BelongsToSubjectEdge | 继承体系 |
| 文档抽取 | ExtractionService | ExtractionPromptBuilder, ExtractionJsonParser, ExtractionValidator | — |
| 图构建 | ConstructionService | GraphDataConverter, GraphNodeData, GraphEdgeData | DDD 传输对象 |
| 宽图谱融合 | FusionService | ExactMatchStrategy, FuzzyMatchStrategy (KpMatchingStrategy) / SimpleAverageStrategy, TimeDecayStrategy (WeightCalculationStrategy) / MastersRecalculationService | 策略模式 |
| 任务剪枝 | SubgraphPruningStrategy | StudentDiagnosisStrategy | 策略模式 (@FunctionalInterface) |
| LLM 调用 | LlmGateway (interface) | Langchain4jLlmGateway | 适配器模式 |
| 用户认证 | UserAccountDO, RoleDO, UserRoleDO | JwtTokenProvider, JwtAuthenticationFilter, AuthService, TokenService | — |
| 文件存储 | FileStorageService (interface) | MinioFileStorageService | 适配器模式 |
| 事件协作 | GraphChangedEvent | GraphConstructedEvent, GradeGraphEvent, TextbookParsedEvent + 各 EventListener | 事件驱动 |