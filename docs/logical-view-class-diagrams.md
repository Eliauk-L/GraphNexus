# GraphNexus 关键实体建模（类图）

> 基于图谱技术的 AI 上下文处理与精准问答系统 — 逻辑视图 · 步骤五
>
> 版本：v1.0 | 创建日期：2026-06-05
>
> 参考文档：[logical-view.md](logical-view.md)

---

## 一、建模范围与原则

本文档聚焦于**有设计决策的关键类**，不穷举所有实体。重点关注：

- 多态与继承体系（Event 多态、规则多态、分析器多态）
- 核心领域模型（宽图谱节点与边的类结构）
- 设计模式应用（策略模式、工厂模式、流水线模式、模板方法）
- 跨模块协作接口

# **不展开**：CRUD 类、DTO/VO 传输对象、前端组件类。

---

## 二、核心图谱领域模型（Wide Graph Domain Model）

宽图谱是系统的核心数据结构，以下类图定义了图谱中节点和边的类型体系。

### 2.1 图谱节点基类与具体节点

```
┌─────────────────────────────────────────────────────────────┐
│                    «abstract» GraphNode                      │
├─────────────────────────────────────────────────────────────┤
│ - nodeId: String                                             │
│ - nodeType: NodeType                                         │
│ - createdAt: DateTime                                        │
│ - updatedAt: DateTime                                        │
│ - metadata: Map<String, Object>                              │
├─────────────────────────────────────────────────────────────┤
│ + getId(): String                                            │
│ + getType(): NodeType                                        │
│ + getNeighbors(hops: Int): List<GraphNode>                   │
└──────────────────────────┬──────────────────────────────────┘
                           │ ▲
            ┌──────────────┼┴──────────────────────┐
            │              │                        │
            ▼              ▼                        ▼
┌───────────────┐ ┌──────────────────┐  ┌──────────────────────┐
│   Student      │ │ KnowledgePoint   │  │      Document        │
├───────────────┤ ├──────────────────┤  ├──────────────────────┤
│ studentId: ID  │ │ kpId: ID         │  │ docId: ID            │
│ name: String   │ │ name: String     │  │ fileName: String     │
│ classId: ID    │ │ description: Str │  │ subjectId: ID        │
│ enrollYear: Int│ │ subjectId: ID    │  │ status: DocStatus    │
│ status: StudSts│ │ source: KpSource │  │ version: Int         │
└───────────────┘ │ status: KpStatus │  │ uploaderId: ID       │
                  └────────┬─────────┘  │ uploaderRole: Role   │
                           │            └──────────────────────┘
                           │                    ▲
                           │                    │
              ┌────────────┼────────────┐       │
              │            │            │       │
              ▼            ▼            ▼       │
        ┌──────────┐ ┌──────────┐ ┌──────────┐ │
        │ Teacher  │ │  Class   │ │  Event   │ │
        ├──────────┤ ├──────────┤ │(abstract)│ │
        │teacherId │ │classId   │ ├──────────┤ │
        │name      │ │name      │ │eventId   │ │
        │subject   │ │grade     │ │studentId │ │
        │classIds  │ │year      │ │kpIds     │ │
        └──────────┘ └──────────┘ │eventTime │ │
                                  └─────┬────┘ │
                                        │      │
                                        ▼      │
        ┌───────────────────────────────────────┘
        │
        ▼ (Event 多态子类，详见 2.2)
```

**NodeType 枚举**：

```
«enumeration» NodeType
├── STUDENT          — 学生节点
├── KNOWLEDGE_POINT  — 知识点节点
├── DOCUMENT         — 文档节点
├── EVENT            — 事件节点（多态基类）
├── TEACHER          — 教师节点
├── CLASS            — 班级节点
├── EXAM_PAPER       — 试卷节点
└── QUESTION         — 题目节点
```

### 2.2 Event 多态体系

Event 是宽图谱中将**结构化数据行转化为图谱节点**的核心抽象。三种事件子类型共享基类属性，各自扩展特定字段。

```
                    ┌──────────────────────────┐
                    │    «abstract» Event       │
                    │    extends GraphNode      │
                    ├──────────────────────────┤
                    │ eventId: String           │
                    │ studentId: String          │
                    │ knowledgePointIds: List<ID>│
                    │ eventTime: DateTime        │
                    │ eventType: EventType       │
                    │ score: Float               │
                    │ maxScore: Float            │
                    ├──────────────────────────┤
                    │ + getScoreRatio(): Float   │
                    │ + getEventType(): EventType│
                    │ + toEventNode(): EventNode │
                    └──────────┬───────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
┌──────────────────┐ ┌────────────────┐ ┌─────────────────┐
│   ExamEvent       │ │AssignmentEvent │ │   QuizEvent     │
├──────────────────┤ ├────────────────┤ ├─────────────────┤
│ examPaperId: ID   │ │ assignType:    │ │ quizName: String│
│ examName: String  │ │  AssignType    │ │ templateId: ID  │
│ questionScores:   │ │ gradingResult: │ │ teacherId: ID   │
│  List<QScore>     │ │  GradingResult │ │ classId: ID     │
│ totalScore: Float │ │ errorCategory: │ │                 │
│                   │ │  ErrorCategory │ │                 │
├──────────────────┤ ├────────────────┤ ├─────────────────┤
│ 来源: CSV 导入    │ │ 来源: CSV/手动 │ │ 来源: 教师录入   │
│ 频率: 低(月考/期考)│ │ 频率: 中(周)   │ │ 频率: 高(日/课)  │
└──────────────────┘ └────────────────┘ └─────────────────┘
```

**关联枚举**：

```
«enumeration» EventType          «enumeration» AssignType
├── EXAM                         ├── DAILY_PRACTICE    — 日常练习
├── ASSIGNMENT                   ├── SPECIAL_TRAINING  — 专项训练
└── QUIZ                         └── CHAPTER_QUIZ      — 章节测验

«enumeration» GradingResult      «enumeration» ErrorCategory
├── CORRECT       — 正确          ├── CALCULATION_ERROR — 计算错误
├── PARTIAL       — 部分正确      ├── CONCEPT_CONFUSION — 概念混淆
├── WRONG         — 错误          ├── METHOD_MISSING   — 方法缺失
└── NOT_SUBMITTED — 未提交        └── CARELESS         — 粗心
```

### 2.3 图谱关系边（Graph Edge）

图谱中的边本身是**一等公民**，具有独立属性和行为。

```
┌─────────────────────────────────────────────────────────────┐
│                    «abstract» GraphEdge                      │
├─────────────────────────────────────────────────────────────┤
│ - edgeId: String                                             │
│ - sourceNodeId: String                                       │
│ - targetNodeId: String                                       │
│ - edgeType: EdgeType                                         │
│ - createdAt: DateTime                                        │
│ - properties: Map<String, Object>                            │
├─────────────────────────────────────────────────────────────┤
│ + getSource(): String                                        │
│ + getTarget(): String                                        │
│ + getType(): EdgeType                                        │
└────────────┬──────────┬──────────────┬──────────────────────┘
             │          │              │
             ▼          ▼              ▼
┌────────────────┐ ┌────────────────┐ ┌───────────────────┐
│MasteryRelation  │ │PrerequisiteRel │ │ExtractionRelation │
├────────────────┤ ├────────────────┤ ├───────────────────┤
│weight: Float   │ │depth: Int      │ │confidence: Float  │
│ (0.0 ~ 1.0)    │ │(依赖链深度)     │ │extractionMethod:  │
│lastUpdated: DT │ │                │ │ String            │
│lastTrigger:    │ │                │ │docId: ID          │
│ TriggerSource  │ │                │ │kpId: ID           │
├────────────────┤ ├────────────────┤ └───────────────────┘
│ Student → KP   │ │ KP → KP (有向) │
│ 动态权重        │ │ 学习路径依赖    │ │ Document → KP     │
└────────────────┘ └────────────────┘ │ 抽取来源标注       │
                                     └───────────────────┘

┌──────────────────────────────────┐
│      KnowledgeRelation            │
├──────────────────────────────────┤
│ relationType: KnowledgeRelType   │
│ strength: Float                  │
├──────────────────────────────────┤
│ KP ↔ KP (无向/双向)              │
│ 引用/推导/包含/同知识点           │
└──────────────────────────────────┘
```

**关联枚举**：

```
«enumeration» EdgeType             «enumeration» KnowledgeRelType
├── MASTERY         — 掌握关系      ├── REFERENCE    — 引用
├── PREREQUISITE    — 前置依赖      ├── DERIVATION   — 推导
├── EXTRACTION      — 抽取关系      ├── CONTAINS     — 包含
├── KNOWLEDGE_REL   — 知识关联      └── SAME_AS      — 同知识点
└── EVENT_REL       — 事件关联
```

### 2.4 Subgraph 与 WideGraph

```
┌───────────────────────────────────┐     ┌───────────────────────────────────┐
│           Subgraph                │     │           WideGraph               │
├───────────────────────────────────┤     ├───────────────────────────────────┤
│ nodes: Set<GraphNode>            │     │ allNodes: Map<ID, GraphNode>      │
│ edges: Set<GraphEdge>            │     │ allEdges: Map<ID, GraphEdge>      │
│ taskType: TaskType               │     │ graphVersion: Int                 │
│ strategyVersion: String          │     │ lastFusionTime: DateTime          │
│ generatedAt: DateTime            │     ├───────────────────────────────────┤
│ tokenEstimate: Int               │     │ + addNode(node: GraphNode): Void  │
├───────────────────────────────────┤     │ + addEdge(edge: GraphEdge): Void  │
│ + getNodeCount(): Int            │     │ + removeNode(id: ID): Void        │
│ + getEdgeCount(): Int            │     │ + getNode(id: ID): GraphNode      │
│ + getNodesByType(t: NodeType):   │     │ + findSubgraph(startId, hops):    │
│     List<GraphNode>              │     │     Subgraph                      │
│ + getEdgesByType(t: EdgeType):   │     │ + computePageRank(topK):          │
│     List<GraphEdge>              │     │     List<RankedEntity>            │
│ + merge(other: Subgraph):        │     │ + getHealthMetrics():             │
│     Subgraph                     │     │     HealthMetrics                 │
└───────────────────────────────────┘     └───────────────────────────────────┘
                                                    │
                                                    │ 包含
                                                    ▼
                                              Subgraph (查询结果)
```

---

## 三、M4 · 图谱核心引擎 — 关键类设计

M4 是系统技术核心，包含三大子模块的类设计：宽图谱融合、剪枝引擎（策略模式）、权重引擎（规则多态）。

### 3.1 M4-b 剪枝引擎 — 策略模式 (Strategy Pattern)

剪枝引擎的核心设计：**剪枝策略是可配置、可版本化的策略对象**，不同任务类型使用不同策略实例。

```
┌─────────────────────────────────────────────────────────────┐
│                       PruningEngine                          │
├─────────────────────────────────────────────────────────────┤
│ - strategyRepository: IStrategyRepository                   │
│ - graphRepository: IGraphRepository                         │
│ - contextBuilder: IContextBuilderService                    │
├─────────────────────────────────────────────────────────────┤
│ + prune(targetNodeId: ID, taskType: TaskType,               │
│         strategyId?: ID): Subgraph                          │
│   ┌──────────────────────────────────────────────────┐      │
│   │ 1. 加载策略: strategyRepository.getStrategy()     │      │
│   │ 2. 按跳数展开: expandByHops()                     │      │
│   │ 3. 按邻居数截断: truncateByTopK()                 │      │
│   │ 4. 按权重阈值过滤: filterByWeight()               │      │
│   │ 5. 按关系类型过滤: filterByRelationType()         │      │
│   │ 6. 组装子图: buildSubgraph()                      │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ + compareStrategy(targetId, stratA, stratB): CompareResult  │
│ + simulatePrune(strategy, scope): SimulationResult           │
└──────────────────────┬──────────────────────────────────────┘
                       │ 使用
                       ▼
        ┌──────────────────────────────────┐
        │        PruningStrategy           │
        ├──────────────────────────────────┤
        │ strategyId: String               │
        │ taskType: TaskType               │
        │ maxHops: Int              深度    │
        │ maxNeighborsPerHop: Int   广度    │
        │ weightThreshold: Float    阈值    │
        │ relationTypeWhitelist:           │
        │   Set<EdgeType>           过滤    │
        │ version: Int                     │
        │ createdAt: DateTime              │
        │ createdBy: String                │
        ├──────────────────────────────────┤
        │ + matchesTask(task: TaskType):   │
        │     Boolean                      │
        │ + diff(other: PruningStrategy):  │
        │     StrategyDiff                 │
        │ + clone(): PruningStrategy       │
        └──────────────────────────────────┘
                       │
                       │ 版本管理
                       ▼
        ┌──────────────────────────────────┐
        │       StrategyVersion            │
        ├──────────────────────────────────┤
        │ versionId: String                │
        │ strategyId: String               │
        │ version: Int                     │
        │ snapshot: PruningStrategy        │
        │ changedAt: DateTime              │
        │ changedBy: String                │
        │ changeDiff: StrategyDiff         │
        └──────────────────────────────────┘
```

**TaskType 枚举**（驱动策略路由）：

```
«enumeration» TaskType
├── ATTRIBUTION_ANALYSIS    — 归因分析（深追溯，3跳，保留前置依赖）
├── REVIEW_RECOMMENDATION   — 复习推荐（聚焦薄弱，2跳，保留掌握关系）
├── CLASS_OVERVIEW          — 班级概览（广覆盖，1跳，保留所有关系）
├── WEAKNESS_DIAGNOSIS      — 薄弱溯源
└── KNOWLEDGE_GAP           — 知识缺口诊断
```

### 3.2 M4-c 权重引擎 — 规则多态 (Polymorphic Rules)

权重引擎的核心设计：**权重更新规则是可多态扩展的规则对象**，时间衰减和行为事件触发使用不同的规则子类。

```
┌─────────────────────────────────────────────────────────────┐
│                       WeightEngine                           │
├─────────────────────────────────────────────────────────────┤
│ - ruleRepository: IWeightRuleRepository                     │
│ - graphRepository: IGraphRepository                         │
│ - changeLogger: IWeightChangeLogger                         │
├─────────────────────────────────────────────────────────────┤
│ + adjustWeightByEvent(eventId: ID): WeightChangeResult      │
│   ┌──────────────────────────────────────────────────┐      │
│   │ 1. 读取事件数据                                    │      │
│   │ 2. 查找匹配的 BehaviorWeightRule                  │      │
│   │ 3. 计算权重调整 Δ                                 │      │
│   │ 4. 更新 MasteryRelation.weight                   │      │
│   │ 5. 记录 WeightChangeLog                          │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ + executeTimeDecay(scope: DecayScope): DecayResult          │
│   ┌──────────────────────────────────────────────────┐      │
│   │ 1. 加载 TimeDecayRule                            │      │
│   │ 2. 查找受影响的 MasteryRelation                   │      │
│   │ 3. 按衰减曲线计算新权重                            │      │
│   │ 4. 批量更新 + 记录日志                             │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ + simulateRule(ruleId, scope): SimulationResult             │
│ + manualOverride(studentId, kpId, weight, reason):          │
│     MasteryRelation                                         │
└──────────────────────┬──────────────────────────────────────┘
                       │ 使用
                       ▼
        ┌──────────────────────────────────────┐
        │    «abstract» WeightUpdateRule       │
        ├──────────────────────────────────────┤
        │ ruleId: String                       │
        │ ruleName: String                     │
        │ scope: RuleScope                     │
        │ enabled: Boolean                     │
        │ createdAt: DateTime                  │
        ├──────────────────────────────────────┤
        │ + apply(relations: List<MasteryRel>, │
        │   context: RuleContext):             │
        │     List<WeightChange>               │
        │ + simulate(scope): SimulationResult  │
        └──────────┬───────────────┬───────────┘
                   │               │
                   ▼               ▼
┌────────────────────────┐ ┌──────────────────────────────┐
│   TimeDecayRule        │ │    BehaviorWeightRule         │
├────────────────────────┤ ├──────────────────────────────┤
│ curveType: DecayCurve  │ │ eventType: EventType          │
│ halfLifeDays: Int      │ │ delta: Float        (+Δ/-Δ)  │
│ computeInterval: Cron  │ │ affectScope: AffectScope     │
│ subjectFilter: ID?     │ │ gradingMapping: Map<         │
├────────────────────────┤ │   GradingResult, Float>      │
│ 指数衰减:              │ ├──────────────────────────────┤
│  w' = w × e^(-λt)     │ │ 正确作答:  +0.05             │
│                        │ │ 错误作答:  -0.08             │
│ 线性衰减:              │ │ 部分正确:  -0.02             │
│  w' = w - k×t         │ │ 未提交:    -0.03             │
│                        │ │                              │
│ 阶梯衰减:              │ │ 可选: 影响前置依赖链          │
│  w' = step(w, t)      │ │ affectScope == CHAIN 时       │
└────────────────────────┘ │ 沿前置依赖链传播权重变化      │
                           └──────────────────────────────┘
```

**关联枚举**：

```
«enumeration» DecayCurve           «enumeration» AffectScope
├── EXPONENTIAL     — 指数衰减      ├── CURRENT_ONLY   — 仅当前知识点
├── LINEAR          — 线性衰减      └── PREREQ_CHAIN   — 影响前置依赖链
└── STEP            — 阶梯衰减

«enumeration» TriggerSource        «enumeration» RuleScope
├── TIME_DECAY      — 时间衰减      ├── GLOBAL         — 全局
├── EXAM_EVENT      — 考试事件      ├── BY_SUBJECT     — 按学科
├── ASSIGNMENT_EVENT— 作业事件      ├── BY_CLASS       — 按班级
├── QUIZ_EVENT      — 测验事件      └── BY_STUDENT     — 按学生
└── MANUAL_OVERRIDE — 手动修正
```

### 3.3 WeightChangeLog — 审计追溯

```
┌─────────────────────────────────────────┐
│          WeightChangeLog                │
├─────────────────────────────────────────┤
│ logId: String                           │
│ studentId: String                       │
│ knowledgePointId: String                │
│ previousWeight: Float                   │
│ newWeight: Float                        │
│ changeAmount: Float    (Δ值)            │
│ triggerSource: TriggerSource            │
│ triggerRefId: String   (规则ID/事件ID)   │
│ operatorSource: OperatorSource          │
│ changedAt: DateTime                     │
│ traceId: String        (链路追踪)        │
├─────────────────────────────────────────┤
│ + getDelta(): Float                     │
│ + isDecay(): Boolean                    │
│ + isImprovement(): Boolean              │
└─────────────────────────────────────────┘

«enumeration» OperatorSource
├── SYSTEM_AUTO       — 系统自动（衰减/事件触发）
└── MANUAL_CORRECTION — 管理员手动修正
```

---

## 四、M3 · 数据入库引擎 — 流水线模式 (Pipeline Pattern)

M3 的核心设计：两条入库流水线各自由多个**处理步骤**串联组成，每个步骤是一个可独立替换的处理器。

### 4.1 文档解析流水线 (PDF Pipeline)

```
┌─────────────────────────────────────────────────────────────┐
│                  DocumentIngestionPipeline                   │
├─────────────────────────────────────────────────────────────┤
│ - steps: List<DocumentProcessStep>     (有序步骤链)          │
│ - graphRepository: IGraphRepository                         │
│ - notificationService: INotificationService                 │
├─────────────────────────────────────────────────────────────┤
│ + execute(document: Document): IngestionResult              │
│   ┌──────────────────────────────────────────────────┐      │
│   │ for step in steps:                               │      │
│   │   context = step.process(context)                │      │
│   │   if context.hasError(): break and report        │      │
│   │ return buildResult(context)                      │      │
│   └──────────────────────────────────────────────────┘      │
└──────────────────────┬──────────────────────────────────────┘
                       │ 执行
                       ▼
┌─────────────────────────────────────────────────────────────┐
│           «interface» DocumentProcessStep                    │
├─────────────────────────────────────────────────────────────┤
│ + process(context: DocumentContext): DocumentContext         │
│ + getStepName(): String                                      │
│ + getOrder(): Int                                            │
└──────────┬──────────┬──────────────┬────────────────────────┘
           │          │              │
           ▼          ▼              ▼
┌──────────────┐ ┌──────────┐ ┌────────────────┐
│ LayoutAnalysis│ │NERStep   │ │REStep          │
│   Step        │ ├──────────┤ ├────────────────┤
├──────────────┤ │ 实体抽取  │ │ 关系抽取        │
│ 版面分析      │ │ 概念      │ │ 引用/推导/     │
│ 正文/标题/    │ │ 公式      │ │ 包含/前置依赖   │
│ 表格/公式区域  │ │ 定理/定义 │ │                │
└──────────────┘ └──────────┘ └────────────────┘
                                    │
                                    ▼
                          ┌──────────────────┐
                          │ GraphImportStep  │
                          ├──────────────────┤
                          │ 导入 Neo4j       │
                          │ 创建节点和边      │
                          │ 关联文档元数据    │
                          └──────────────────┘
```

**DocumentContext**（流水线上下文，在步骤间传递）：

```
┌─────────────────────────────────────────────┐
│            DocumentContext                   │
├─────────────────────────────────────────────┤
│ document: Document                          │
│ rawContent: byte[]          — PDF 原始内容   │
│ layoutRegions: List<Region> — 版面分析结果   │
│ extractedEntities: List<KP> — NER 抽取结果   │
│ extractedRelations: List<KR>— RE 抽取结果    │
│ confidenceScores: Map<ID, Float>            │
│ errors: List<IngestionError>                │
│ processingTime: Duration                    │
├─────────────────────────────────────────────┤
│ + hasError(): Boolean                       │
│ + addError(error: IngestionError): Void     │
│ + getLowConfidenceEntities(): List<KP>      │
└─────────────────────────────────────────────┘
```

### 4.2 事件导入流水线 (Event Pipeline)

```
┌─────────────────────────────────────────────────────────────┐
│                   EventIngestionPipeline                     │
├─────────────────────────────────────────────────────────────┤
│ + importGradesCSV(csvFile, config): ImportResult            │
│ + importAssignments(request): ImportResult                  │
│ + importQuizResults(request): ImportResult                  │
└──────────────────────┬──────────────────────────────────────┘
                       │ 步骤
                       ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ CSVValidate  │→│ StudentMatch │→│ KPMatch       │→│ EventCreate  │
│   Step       │  │   Step       │  │   Step       │  │   Step       │
├──────────────┤  ├──────────────┤  ├──────────────┤  ├──────────────┤
│ 字段格式校验  │  │ 学号匹配M1   │  │ 知识点匹配M2 │  │ 生成事件节点  │
│ 类型检查      │  │ 冲突策略:    │  │ 模糊匹配     │  │ 关联图谱     │
│ 必填校验      │  │ · 自动创建   │  │ 未匹配标记   │  │ 触发权重更新  │
│              │  │ · 跳过       │  │ "待确认"     │  │ M4.weight    │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘

ImportResult
├── successCount: Int
├── failureCount: Int
├── skippedCount: Int
├── errors: List<ImportError>
│   ├── rowNumber: Int
│   ├── fieldName: String
│   └── errorMessage: String
└── processingTime: Duration
```

---

## 五、M5 · AI 分析引擎 — 工厂 + 模板方法模式

### 5.1 分析生成器体系 (Analysis Generator Hierarchy)

```
┌─────────────────────────────────────────────────────────────┐
│            «abstract» AnalysisGenerator                      │
├─────────────────────────────────────────────────────────────┤
│ # pruningEngine: PruningEngine                              │
│ # contextBuilder: IContextBuilderService                    │
│ # llmGateway: ILLMGatewayService                           │
├─────────────────────────────────────────────────────────────┤
│ + generate(request: AnalysisRequest): AnalysisResult        │
│   ┌──────────────────────────────────────────────────┐      │
│   │ Template Method:                                 │      │
│   │ 1. subgraph = prune(request)           剪枝      │      │
│   │ 2. context = buildContext(subgraph)    构建上下文  │      │
│   │ 3. prompt = buildPrompt(context)       组装Prompt │      │
│   │ 4. response = callLLM(prompt)          调用LLM   │      │
│   │ 5. result = parseResponse(response)    解析结果   │      │
│   │    ↑ 子类实现                                     │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ # abstract getTaskType(): TaskType                          │
│ # abstract parseResponse(raw: String): AnalysisResult       │
│ # abstract buildPrompt(context: LLMContext): PromptTemplate │
└──────────┬───────────────────┬──────────────────────────────┘
           │                   │
           ▼                   ▼
┌────────────────────┐ ┌─────────────────────────┐
│AttributionGenerator│ │TeachingSuggestGenerator  │
├────────────────────┤ ├─────────────────────────┤
│ 归因分析器          │ │ 教学建议器               │
│                    │ │                         │
│ 输出: 归因报告      │ │ 输出: 教学建议           │
│ · 根因列表          │ │ · 补救路径(分步)         │
│ · 多维证据链        │ │ · 教辅资源匹配(页码/题号) │
│ · 置信度标注        │ │ · 时间估算              │
│ · 追溯路径          │ │                         │
└────────────────────┘ │ 依赖: M2前置依赖链       │
                       │       M3教辅资源检索     │
                       └─────────────────────────┘

┌────────────────────┐
│ReviewPathGenerator │
├────────────────────┤
│ 复习规划器          │
│                    │
│ 输出: 复习路径      │
│ · 优先级排序        │
│ · 时间预估          │
│ · 资源推荐          │
│                    │
│ 依赖: M4 PageRank  │
│       M2 依赖链     │
└────────────────────┘
```

### 5.2 LLM 网关 — 配额与降级

```
┌─────────────────────────────────────────────────────────────┐
│                      LLMGateway                              │
├─────────────────────────────────────────────────────────────┤
│ - modelRouter: ModelRouter                                  │
│ - quotaManager: QuotaManager                                │
│ - fallbackStrategy: FallbackStrategy                        │
│ - callLogger: ILLMCallLogger                                │
├─────────────────────────────────────────────────────────────┤
│ + call(request: LLMCallRequest): LLMCallResult              │
│   ┌──────────────────────────────────────────────────┐      │
│   │ 1. model = modelRouter.route(taskType)           │      │
│   │ 2. if quotaManager.isExceeded(model):            │      │
│   │      model = fallbackStrategy.fallback(model)    │      │
│   │ 3. prompt = templateEngine.render(template, vars)│      │
│   │ 4. response = client.call(prompt)                │      │
│   │ 5. callLogger.log(callRecord)                    │      │
│   │ 6. return buildResult(response)                  │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ + switchModel(taskType, modelId): ModelConfig               │
│ + getQuotaStatus(modelId): QuotaStatus                      │
└──────────────────────┬──────────────────────────────────────┘
                       │ 使用
                       ▼
┌──────────────────────────────────┐
│           LLMConfig              │
├──────────────────────────────────┤
│ modelId: String                  │
│ modelName: String                │
│ taskType: TaskType               │
│ dailyQuota: Int                  │
│ weeklyQuota: Int                 │
│ monthlyQuota: Int                │
│ tokenBudget: Int                 │
│ fallbackModelId: String          │
│ timeoutMs: Int                   │
│ apiEndpoint: String              │
├──────────────────────────────────┤
│ + isQuotaExceeded(): Boolean     │
│ + getFallback(): String          │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│          LLMCallLog              │
├──────────────────────────────────┤
│ callId: String                   │
│ taskType: TaskType               │
│ modelUsed: String                │
│ promptTemplateVersion: Int       │
│ inputTokens: Int                 │
│ outputTokens: Int                │
│ latencyMs: Int                   │
│ result: CallResult               │
│ calledAt: DateTime               │
│ traceId: String                  │
├──────────────────────────────────┤
│ + getCostEstimate(): Float       │
│ + isSuccessful(): Boolean        │
└──────────────────────────────────┘

«enumeration» CallResult
├── SUCCESS
├── TIMEOUT
├── RATE_LIMITED
├── QUOTA_EXCEEDED
└── API_ERROR
```

### 5.3 上下文构建器 (Context Builder)

```
┌─────────────────────────────────────────────────────────────┐
│                    ContextBuilder                             │
├─────────────────────────────────────────────────────────────┤
│ - serializers: Map<NodeType, NodeSerializer>                │
│ - tokenCounter: TokenCounter                                │
├─────────────────────────────────────────────────────────────┤
│ + buildContext(subgraph: Subgraph, taskType: TaskType,      │
│                extraInfo?: ContextExtra): LLMContext         │
│   ┌──────────────────────────────────────────────────┐      │
│   │ 1. serialize(subgraph)    子图 → 文本/JSON       │      │
│   │ 2. fuse(extraInfo)       融合教辅引用+历史趋势    │      │
│   │ 3. trim(tokenBudget)     Token 预算裁剪          │      │
│   │ 4. format(taskType)      按任务类型格式化         │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ + estimateTokenCount(subgraph, taskType): Int               │
└─────────────────────────────────────────────────────────────┘

LLMContext
├── serializedGraph: String    — 子图序列化文本
├── studentProfile: String     — 学生基本信息
├── knowledgeContext: String   — 知识点上下文
├── historicalTrend: String    — 历史趋势数据
├── teachingResourceRefs: List — 教辅引用(页码/题号)
├── totalTokens: Int           — 总 Token 数
└── taskType: TaskType         — 任务类型
```

---

## 六、M2 · 知识体系 — 分类树与前置依赖

### 6.1 知识分类树 (Category Tree)

```
┌─────────────────────────────────────────────┐
│          KnowledgeCategory                   │
├─────────────────────────────────────────────┤
│ categoryId: String                          │
│ name: String                                │
│ level: CategoryLevel                        │
│ parentId: String?                           │
│ subjectId: String                           │
│ sortOrder: Int                              │
│ children: List<KnowledgeCategory>           │
│ linkedKpId: String?     (叶子节点关联知识点) │
├─────────────────────────────────────────────┤
│ + isLeaf(): Boolean                         │
│ + getPath(): List<KnowledgeCategory>        │
│ + addChild(child: KnowledgeCategory): Void  │
│ + moveTo(newParentId: ID): Void             │
│ + hasCycle(): Boolean                       │
└─────────────────────────────────────────────┘

«enumeration» CategoryLevel
├── SUBJECT      — 学科（数学/物理/...）
├── MODULE       — 一级模块（代数/几何/...）
├── CHAPTER      — 二级章节（函数/方程/...）
└── KNOWLEDGE_POINT — 具体知识点
```

### 6.2 前置依赖与循环检测

```
┌─────────────────────────────────────────────────────────────┐
│                 PrerequisiteService                           │
├─────────────────────────────────────────────────────────────┤
│ - graphRepo: IGraphRepository                               │
│ - cycleDetector: CycleDetector                              │
├─────────────────────────────────────────────────────────────┤
│ + addPrerequisite(kpId, prereqKpId): PrerequisiteRelation   │
│   ┌──────────────────────────────────────────────────┐      │
│   │ 1. cycleDetector.wouldCreateCycle(kpId, prereq)  │      │
│   │ 2. if cycle → reject with cycle path             │      │
│   │ 3. else → create edge                            │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ + getPrerequisites(kpId, depth?): List<KnowledgePoint>      │
│ + getDependents(kpId): List<KnowledgePoint>                 │
│ + getDependencyChain(kpId): DependencyChain                 │
│ + detectCycles(): List<CyclePath>                           │
└─────────────────────────────────────────────────────────────┘

DependencyChain
├── rootKpId: String
├── nodes: List<KnowledgePoint>
├── edges: List<PrerequisiteRelation>
├── maxDepth: Int
└── leafNodes: List<KnowledgePoint>   — 无前置依赖的根知识点

CyclePath
├── path: List<String>    — 循环路径中的知识点ID序列
└── detectedAt: DateTime
```

---

## 七、M1 · 主数据 — 状态机模式 (State Pattern)

### 7.1 学生状态生命周期

```
┌─────────────────────────────────────────────────────────────┐
│                       Student                                │
├─────────────────────────────────────────────────────────────┤
│ studentId: String                                            │
│ name: String                                                 │
│ classId: String                                              │
│ enrollYear: Int                                              │
│ status: StudentStatus                                        │
│ archivedAt: DateTime?                                        │
├─────────────────────────────────────────────────────────────┤
│ + isActive(): Boolean        — 是否参与权重计算               │
│ + archive(): Void            — 归档（毕业/转学）              │
│ + transferTo(newClassId): Void                               │
│ + canParticipateInAnalysis(): Boolean                        │
│   ┌──────────────────────────────────────────────────┐      │
│   │ return status == ACTIVE                          │      │
│   │ // 休学/转学/毕业 → 不参与权重计算和归因分析       │      │
│   └──────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘

                    ┌──────────┐
                    │  ACTIVE   │ ← 初始状态（入学）
                    │  在读     │
                    └────┬─────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
              ▼          ▼          ▼
        ┌──────────┐ ┌────────┐ ┌──────────┐
        │SUSPENDED │ │TRANSFER│ │ GRADUATED│
        │  休学     │ │  转学   │ │  毕业    │
        └────┬─────┘ └───┬────┘ └──────────┘
             │           │          ↑
             │           │          │
             └───────────┴──────────┘
                 (均可转为归档状态)

        归档后：数据保留，不再参与权重计算

«enumeration» StudentStatus
├── ACTIVE       — 在读（参与所有计算）
├── SUSPENDED    — 休学（不参与计算，保留数据）
├── TRANSFERRED  — 转学（归档）
└── GRADUATED    — 毕业（归档）
```

---

## 八、M6 · 应用层 — 用例编排类

应用层不包含核心算法，其类设计重点是**编排逻辑**和**结果组装**。

### 8.1 归因查询编排

```
┌─────────────────────────────────────────────────────────────┐
│              AttributionQueryService                          │
│              (L2 应用层 · 用例编排)                            │
├─────────────────────────────────────────────────────────────┤
│ - authService: IAuthorizationService     (M1)               │
│ - pruningEngine: PruningEngine           (M4)               │
│ - analysisGenerator: AnalysisGenerator   (M5)               │
│ - studentService: IStudentService        (M1)               │
│ - kpService: IKnowledgePointService      (M2)               │
├─────────────────────────────────────────────────────────────┤
│ + queryAttribution(query: AttributionQuery):                │
│     AttributionReportVO                                     │
│   ┌──────────────────────────────────────────────────┐      │
│   │ 1. student = studentService.resolve(query)       │      │
│   │ 2. authService.checkPermission(user, student,    │      │
│   │                                  Action.READ)    │      │
│   │ 3. taskType = routeIntent(query)                 │      │
│   │ 4. subgraph = pruningEngine.prune(student.id,    │      │
│   │                                   taskType)      │      │
│   │ 5. report = analysisGenerator.generate(request)  │      │
│   │ 6. vo = assembleVO(report, subgraph)             │      │
│   │ 7. return vo                                     │      │
│   └──────────────────────────────────────────────────┘      │
│                                                              │
│ + autoComplete(input: String): List<Suggestion>             │
│ + getDrillDown(attributionId, kpId): AttributionReportVO    │
└─────────────────────────────────────────────────────────────┘

AttributionReportVO (面向表示层的组装结果)
├── report: AttributionReport         — 归因报告
├── visualizationData: SubgraphVO     — 可视化子图数据
├── drillDownCandidates: List<KP>     — 可下钻的知识点列表
├── trendData: TrendData              — 趋势曲线数据
└── relatedResources: List<Resource>  — 相关教辅资源
```

---

## 九、关键枚举与值对象汇总

### 9.1 全局枚举

```
┌─────────────────────────────────────────────────────────────┐
│                     全局枚举定义                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  NodeType        — 图谱节点类型（7种）                        │
│  EdgeType        — 图谱边类型（6种）                          │
│  EventType       — 事件类型（3种）                            │
│  TaskType        — 任务类型（5种）                            │
│  StudentStatus   — 学生状态（4种）                            │
│  KpStatus        — 知识点状态（2种: ACTIVE/DEPRECATED）       │
│  KpSource        — 知识点来源（3种: AUTO/MANUAL/CSV_IMPORT）  │
│  DocStatus       — 文档状态（5种，见下）                       │
│  CategoryLevel   — 分类层级（4种）                            │
│  DecayCurve      — 衰减曲线（3种）                            │
│  AssignType      — 作业类型（3种）                            │
│  GradingResult   — 批改结果（4种）                            │
│  ErrorCategory   — 错题原因（4种）                            │
│  KnowledgeRelType— 知识关联类型（4种）                        │
│  CallResult      — LLM调用结果（5种）                         │
│  TriggerSource   — 权重触发来源（5种）                        │
│  OperatorSource  — 操作来源（2种）                            │
│  AffectScope     — 影响范围（2种）                            │
│  RuleScope       — 规则范围（4种）                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 文档处理状态

```
                    ┌──────────┐
                    │ UPLOADED  │ ← 上传完成
                    │  已上传    │
                    └────┬─────┘
                         │ 开始处理
                         ▼
                    ┌──────────┐
                    │PROCESSING│ ← 流水线执行中
                    │  处理中   │
                    └────┬─────┘
                    ┌────┴─────┐
                    │          │
                    ▼          ▼
              ┌──────────┐ ┌──────────┐
              │COMPLETED │ │ FAILED   │
              │  已完成   │ │  失败    │
              └──────────┘ └────┬─────┘
                                │ 重新上传
                                ▼
                           ┌──────────┐
                           │RE_UPLOAD │
                           │  重新处理 │
                           └──────────┘

«enumeration» DocStatus
├── UPLOADED       — 已上传，待处理
├── PROCESSING     — 处理中（流水线执行）
├── COMPLETED      — 处理完成
├── FAILED         — 处理失败（扫描版PDF/加密PDF等）
└── RE_UPLOADING   — 重新上传处理中
```

### 9.3 实体对齐状态

```
«enumeration» AlignmentConfidence
├── HIGH    (> 0.9)  — 高置信度，建议自动合并
├── MEDIUM  (0.6~0.9)— 中置信度，需人工确认
└── LOW     (< 0.6)  — 低置信度，仅提示

«enumeration» AlignmentStatus
├── PENDING    — 待审核
├── MERGED     — 已合并
└── REJECTED   — 已驳回（加入白名单）
```

---

## 十、设计模式总结

| 模式 | 应用位置 | 解决的问题 |
|------|---------|-----------|
| **策略模式** | M4-b PruningEngine + PruningStrategy | 不同任务类型使用不同剪枝参数，策略可配置、可版本化、可 A/B 对比 |
| **模板方法** | M5 AnalysisGenerator 抽象基类 | 归因/教学建议/复习路径三种分析共享"剪枝→上下文→LLM→解析"流程，子类只实现差异步骤 |
| **流水线模式** | M3 DocumentIngestionPipeline | PDF 解析多步骤串联，每步可独立替换，失败可定位到具体步骤 |
| **状态模式** | M1 Student + Document 状态流转 | 实体生命周期有明确状态转换规则，非法转换需拒绝 |
| **工厂模式** | M5 ModelRouter | 按 TaskType 路由到不同 LLM 模型，新增任务类型只需扩展路由表 |
| **观察者模式** | M4 WeightEngine → WeightChangeLog | 权重变更自动触发日志记录和通知，解耦变更与审计 |
| **适配器模式** | L3 → L4 接口契约 | 领域层定义接口（IGraphRepository），基础设施层提供实现（Neo4jGraphRepository），技术可替换 |
| **多态继承** | Event 基类 + 3 子类型 | 考试/作业/测验共享事件基础属性，各自扩展特定字段 |
| **组合模式** | KnowledgeCategory 树形结构 | 分类树节点递归包含子节点，统一处理叶子和非叶子节点 |

---

## 十一、类图与接口的对应关系

| 接口（Section 3 定义） | 核心实现类（本文档） | 设计模式 |
|:--|:--|:--|
| IPruningService | PruningEngine, PruningStrategy, StrategyVersion | 策略模式 |
| IWeightService | WeightEngine, TimeDecayRule, BehaviorWeightRule, WeightChangeLog | 规则多态 |
| IGraphFusionService | WideGraph, Subgraph, GraphNode/GraphEdge 体系 | 领域模型 |
| IDocumentIngestionService | DocumentIngestionPipeline, DocumentProcessStep, DocumentContext | 流水线模式 |
| IEventIngestionService | EventIngestionPipeline, Event 多态体系 | 流水线 + 多态 |
| IAnalysisGeneratorService | AnalysisGenerator, AttributionGenerator, TeachingSuggestGenerator, ReviewPathGenerator | 模板方法 |
| ILLMGatewayService | LLMGateway, LLMConfig, LLMCallLog, ModelRouter | 工厂 + 降级策略 |
| IContextBuilderService | ContextBuilder, LLMContext | 构建器 |
| IPrerequisiteService | PrerequisiteService, DependencyChain, CycleDetector | 图算法 |
| IStudentService | Student (状态机) | 状态模式 |
| IKnowledgeCategoryService | KnowledgeCategory (树) | 组合模式 |
