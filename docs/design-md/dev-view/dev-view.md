# GraphNexus 开发视图设计文档

> 基于图谱技术的 AI 上下文处理与精准问答系统 — 开发视图
>
> 版本：v1.2 | 创建日期：2026-06-05 | 修订日期：2026-06-08
>
> 参考文档：[logical-view.md](logical-view.md) · 分层架构（Section 4）、[user-stories-simplify.md](user-stories-simplify.md)

---

## 一、分层架构原则

### 1.1 核心约束

开发视图采用**严格的四层架构 + 一条横切关注点**组织代码。每层有且仅有一个明确的责任，子系统只能依赖同层或下层，不允许向上依赖。

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  L1 · 表示层                                                     │
│  职责：将信息展示给用户，接收用户操作，不包含业务逻辑              │
│                                                                 │
│  依赖规则：只能依赖 L2 应用层                                      │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  L2 · 应用层                                                     │
│  职责：编排用例流程，执行权限校验和输入校验，不包含算法实现         │
│                                                                 │
│  依赖规则：可以依赖 L3 领域层、L4 基础设施层、公共库               │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  L3 · 领域层                                                     │
│  职责：封装核心业务规则、算法和领域模型，不感知 HTTP 和 UI          │
│                                                                 │
│  依赖规则：可以依赖同层（L3 内部）、L4 基础设施层、公共库           │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  L4 · 基础设施层                                                  │
│  职责：封装数据库、外部 API、文件存储等基础技术细节                 │
│                                                                 │
│  依赖规则：可以依赖公共库，不依赖任何业务层（L1/L2/L3）             │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  横切 · M7 · 系统运维                                            │
│  职责：提供日志、备份、定时任务、通知等跨层基础设施                 │
│                                                                 │
│  依赖规则：独立于业务依赖链，被所有层通过 AOP/中间件消费            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

                        依赖方向：自上而下（单向）
                        L1 ──▶ L2 ──▶ L3 ──▶ L4
                        横切关注点独立于分层，跨层生效
```

### 1.2 逐层发布策略

分层架构确保**每一层可独立构建、独立测试、独立发布**：

| 层次 | 发布单元 | 发布触发条件 | 影响范围 |
|------|---------|-------------|---------|
| **L1 表示层** | 7 个前端包 | 任一前端页面变更 | 仅影响该前端应用的用户 |
| **L2 应用层** | 2 个包（api + application） | 新增用例编排、接口变更 | 影响所有前端消费者 |
| **L3 领域层** | 6 个包（领域核心 + 领域支撑） | 核心算法变更、领域模型调整 | 影响 L2 应用层和所有同层消费者 |
| **L4 基础设施层** | 1 个包（infrastructure） | 外部技术选型升级 | 影响所有使用该适配器的领域模块 |
| **横切 M7** | 1 个包（ops） | 运维功能变更 | 由 AOP 注入，运行时生效 |

**发布顺序规则**：必须先发布下层，再发布上层。例如 L4 变更 → 先发布 L4 → 再发布依赖它的 L3 模块 → 再发布 L2 → 最后发布 L1。

### 1.3 分层到代码模块的映射

```
逻辑视图分层                      开发视图代码模块（19 个）
─────────────────                ──────────────────────
L1 · 表示层                      前端项目群（7 个包）
   5 个界面 + 2 个公共库          graphnexus-admin / teacher / student
                                  / ops-console / operations-console
                                  + shared-types / shared-components

L2 · 应用层                      后端编排层（2 个包）
   M6 · 应用服务                  graphnexus-api + graphnexus-application

L3 · 领域层                      后端领域模块群（6 个包）
   领域核心（M4, M5）              graphnexus-graph-core / graphnexus-ai-analysis
   领域支撑（M1, M2, M3, M8）     graphnexus-master-data / graphnexus-knowledge-system
                                   / graphnexus-ingestion / graphnexus-operations

L4 · 基础设施层                   后端适配器（1 个包）
   Neo4j / LLM / 存储 / 消息队列  graphnexus-infrastructure

横切 · M7 · 系统运维              后端运维（1 个包）
   日志/备份/定时任务/通知         graphnexus-ops

公共                              graphnexus-common（1 个包）
```

### 1.4 完整代码模块目录树

```
graphnexus/
│
├── backend/                              # 后端项目群（多模块构建，12 个包）
│   │
│   ├── graphnexus-api/                   # L2: HTTP 入口（接入层）
│   ├── graphnexus-application/           # L2: 用例编排（服务层）
│   │
│   ├── graphnexus-graph-core/           # L3 领域核心: 图谱引擎
│   ├── graphnexus-ai-analysis/          # L3 领域核心: AI 分析
│   │
│   ├── graphnexus-master-data/          # L3 领域支撑: 主数据
│   ├── graphnexus-knowledge-system/     # L3 领域支撑: 知识体系
│   ├── graphnexus-ingestion/            # L3 领域支撑: 数据入库
│   ├── graphnexus-operations/           # L3 领域支撑: 运营分析
│   │
│   ├── graphnexus-infrastructure/       # L4: 基础设施适配器
│   ├── graphnexus-ops/                   # 横切: 系统运维
│   │
│   └── graphnexus-common/               # 公共基础库（全模块共享）
│
├── frontend/                             # 前端项目群（多包构建，7 个包）
│   │
│   ├── graphnexus-admin/                # L1: P1 管理后台
│   ├── graphnexus-teacher/              # L1: P2 教师工作台
│   ├── graphnexus-student/              # L1: P3 学生自助端
│   ├── graphnexus-ops-console/          # L1: P4 运维控制台
│   ├── graphnexus-operations-console/   # L1: P5 运营看板
│   │
│   ├── graphnexus-shared-types/         # L1 公共: 类型定义
│   └── graphnexus-shared-components/    # L1 公共: UI 组件库
│
├── build-config/                         # 构建配置
└── docker-compose.yml                    # 本地开发环境
```

---

## 二、L1 · 表示层

> **单一职责**：将信息展示给用户，接收用户交互操作，不包含任何业务逻辑。
>
> **依赖规则**：只能依赖 L1 同层公共库（shared-types、shared-components），通过 HTTP 调用 L2 的 graphnexus-api。

### 2.1 模块划分

将逻辑视图 L1 的 5 个界面（P1~P5）映射为 5 个独立前端项目 + 2 个公共库：

| 界面 | 代码模块 | 目标用户 | 对应用例 |
|------|---------|---------|---------|
| P1 | `graphnexus-admin` | 管理员 | Story 1.1~1.10 教辅上传、成绩导入、实体对齐、知识体系、剪枝策略、权重规则、全局图谱、模型定义 |
| P2 | `graphnexus-teacher` | 教师 | Story 2.1~2.11 归因查询、班级概览、知识图谱、趋势追踪、报告导出、个人教辅、作业/测验录入 |
| P3 | `graphnexus-student` | 学生 | Story 3.1~3.4 薄弱总览、复习推荐、进度追踪、班级对比、复习效果 |
| P4 | `graphnexus-ops-console` | 运维人员 | Story 4.1~4.3 日志搜索、备份管理、定时任务、LLM 配置、性能告警 |
| P5 | `graphnexus-operations-console` | 运营人员 | Story 5.1 文档产能统计、知识点覆盖、文档利用率、处理时效 |

### 2.2 公共库

| 模块 | 类型 | 内容 |
|------|------|------|
| `graphnexus-shared-types` | 纯类型定义包 | 枚举定义、DTO 接口定义、API 路径常量 |
| `graphnexus-shared-components` | UI 组件库 | 图谱可视化组件、图表组件、报告渲染组件、通用 UI 组件 |

### 2.3 前端模块内部结构（以 `graphnexus-teacher` 为例）

```
graphnexus-teacher/
├── build-config                          # 构建配置
│
└── src/
    ├── pages/                            # 页面组件
    │   ├── attribution-query/            # 归因查询页
    │   │   ├── AttributionQueryPage
    │   │   ├── QueryInput                # 自动补全搜索框
    │   │   ├── AttributionResult         # 归因结果展示
    │   │   └── DrillDownPanel            # 追问下钻
    │   │
    │   ├── class-overview/               # 班级概览页
    │   │   ├── ClassOverviewPage
    │   │   ├── WeaknessTopN
    │   │   └── StudentDrillDown
    │   │
    │   ├── student-graph/                # 学生知识图谱页
    │   │   ├── StudentGraphPage
    │   │   └── GraphSnapshotCompare
    │   │
    │   ├── homework-entry/               # 作业/测验录入页
    │   │   ├── AssignmentEntryPage
    │   │   └── QuizEntryPage
    │   │
    │   └── personal-kb/                  # 个人教辅管理页
    │       ├── PersonalKBPage
    │       └── PublicApplyDialog
    │
    ├── components/                       # 模块内组件
    ├── hooks/                            # 自定义 Hook
    │   ├── useAttributionQuery
    │   └── useAutoComplete
    ├── services/                         # API 调用封装
    │   ├── attributionApi
    │   ├── classApi
    │   └── ingestionApi
    ├── stores/                           # 状态管理
    │   ├── attributionStore
    │   └── classOverviewStore
    └── routes/                           # 路由配置
        └── index
```

### 2.4 前端依赖关系

```
graphnexus-admin ──────────┐
graphnexus-teacher ────────┤
graphnexus-student ────────┤
graphnexus-ops-console ────┤
graphnexus-operations- ────┤
      console               │
                            │ 依赖（同层）
                            ▼
              graphnexus-shared-components   ← L1 同层公共库
                            │
                            │ 依赖（同层）
                            ▼
              graphnexus-shared-types        ← L1 同层公共库

所有前端模块通过 HTTP 调用 graphnexus-api（L2）
前端模块不直接依赖任何 L3/L4 后端模块
```

---

## 三、L2 · 应用层

> **单一职责**：编排用例流程，执行权限校验和输入校验，组装面向表示层的 VO。不包含核心算法和业务规则。
>
> **依赖规则**：可以依赖 L3 领域层（通过接口）、L4 基础设施层、公共库。不依赖 L1 表示层。

### 3.1 模块划分

L2 应用层拆分为两个后端模块，分别承担不同的关注点：

| 模块 | 层次内职责 | 包含内容 |
|------|---------|---------|
| `graphnexus-api` | HTTP 入口层 | 请求处理器（Controller）、请求/响应 DTO、全局异常处理、认证拦截器 |
| `graphnexus-application` | 用例编排层 | 7 组用例编排实现、权限校验、输入校验、VO 组装 |

**拆分原因**：HTTP 协议细节（路由、序列化、状态码）与业务用例编排（权限、输入校验、多服务调度）是两个独立的变化维度，分离后各自可独立演进和发布。

### 3.2 `graphnexus-api`（HTTP 入口）

```
graphnexus-api/
└── src/api/
    ├── handler/                          # 请求处理器
    │   ├── admin/
    │   │   ├── DocumentHandler           # POST /api/admin/documents
    │   │   ├── IngestionHandler          # POST /api/admin/ingestion
    │   │   ├── AlignmentHandler          # GET/POST /api/admin/alignment
    │   │   ├── KnowledgeHandler          # CRUD /api/admin/knowledge
    │   │   ├── StrategyHandler           # CRUD /api/admin/strategies
    │   │   └── ModelHandler              # CRUD /api/admin/models
    │   │
    │   ├── teacher/
    │   │   ├── AttributionHandler        # POST /api/teacher/attribution
    │   │   ├── ClassHandler              # GET /api/teacher/class/{id}
    │   │   ├── VisualizationHandler      # GET /api/teacher/visualization
    │   │   ├── ReportHandler             # POST /api/teacher/reports
    │   │   ├── PersonalKBHandler         # CRUD /api/teacher/kb
    │   │   └── HomeworkHandler           # POST /api/teacher/homework
    │   │
    │   ├── student/
    │   │   └── StudentSelfHandler        # GET /api/student/*
    │   │
    │   ├── ops/
    │   │   ├── LogHandler                # GET /api/ops/logs
    │   │   ├── BackupHandler             # POST /api/ops/backup
    │   │   └── TaskHandler               # CRUD /api/ops/tasks
    │   │
    │   └── operations/
    │       └── DashboardHandler           # GET /api/operations/dashboard
    │
    ├── dto/                              # 请求/响应 DTO
    │   ├── request/
    │   │   ├── AttributionQueryRequest
    │   │   ├── DocumentUploadRequest
    │   │   └── ImportRequest
    │   └── response/
    │       ├── ApiResponse               # 统一响应封装
    │       ├── AttributionReportResponse
    │       └── ClassOverviewResponse
    │
    ├── config/
    │   ├── WebConfig                     # CORS、拦截器
    │   ├── SecurityConfig                # 认证配置
    │   └── ApiDocConfig                  # API 文档
    │
    └── exception/
        ├── GlobalExceptionHandler        # 全局异常处理
        └── ErrorCode                     # 错误码枚举
```

### 3.3 `graphnexus-application`（用例编排层）

```
graphnexus-application/
└── src/application/
    ├── attribution/                      # ① 归因查询编排
    │   ├── AttributionQueryService
    │   ├── QueryIntentRouter             # 意图识别 → TaskType 路由
    │   ├── AutoCompleteService
    │   └── vo/AttributionReportVO
    │
    ├── class-analysis/                   # ② 班级分析编排
    │   ├── ClassAnalysisService
    │   ├── WeaknessCalculator
    │   └── vo/WeaknessOverviewVO
    │
    ├── visualization/                    # ③ 可视化编排
    │   ├── VisualizationService
    │   ├── KnowledgeGraphBuilder
    │   ├── TrendDataBuilder
    │   └── CrossSubjectAnalyzer
    │
    ├── report-export/                    # ④ 报告导出编排
    │   ├── ReportExportService
    │   ├── PdfRenderer
    │   ├── MarkdownRenderer
    │   └── ShareLinkService
    │
    ├── personal-kb/                      # ⑤ 个人知识库编排
    │   ├── PersonalKBService
    │   └── PublicApplicationManager
    │
    ├── student-self/                     # ⑥ 学生自助编排
    │   ├── StudentSelfService
    │   ├── RadarDataBuilder
    │   └── ReviewEffectTracker
    │
    ├── operations-dashboard/             # ⑦ 运营看板编排
    │   ├── OperationsDashboardService
    │   └── DashboardAggregator
    │
    └── common/                           # 编排层公共逻辑
        ├── PermissionValidator
        ├── InputSanitizer
        └── VoAssembler
```

### 3.4 L2 内部依赖

```
graphnexus-api
    │
    │ 调用（同层 L2）
    ▼
graphnexus-application
    │
    │ 编排调用（依赖下层 L3，通过接口）
    ▼
L3 领域层模块
```

---

## 四、L3 · 领域层

> **单一职责**：封装核心业务规则、算法和领域模型。不感知 HTTP 协议、UI 渲染、数据库实现细节。
>
> **依赖规则**：可以依赖 L3 同层（领域核心可依赖领域支撑）、L4 基础设施层（通过接口）、公共库。不依赖 L1/L2。

### 4.1 模块划分

L3 领域层内部按**领域核心**和**领域支撑**两层组织，领域核心包含系统的核心算法，领域支撑为领域核心提供基础数据服务：

```
L3 内部层次结构：

  领域核心（Domain Core）           领域支撑（Domain Support）
  ────────────────                 ──────────────────
  graphnexus-graph-core             graphnexus-master-data
    (M4 图谱核心引擎)                 (M1 主数据管理)

  graphnexus-ai-analysis            graphnexus-knowledge-system
    (M5 AI 分析引擎)                  (M2 知识体系管理)

                                    graphnexus-ingestion
                                      (M3 数据入库引擎)

                                    graphnexus-operations
                                      (M8 运营分析)

  依赖方向：领域核心 → 领域支撑（单向）
  领域支撑之间：M3 → M1, M2（入库匹配学号和知识点）
```

### 4.2 领域核心模块

#### 4.2.1 `graphnexus-graph-core`（M4 · 图谱核心引擎）

```
graphnexus-graph-core/
└── src/graph/
    ├── api/                             # 对外接口定义
    │   ├── IGraphFusionService          # 宽图谱融合
    │   ├── IPruningService              # 剪枝引擎
    │   ├── IWeightService               # 权重引擎
    │   └── dto/                         # 接口 DTO
    │
    ├── domain/                          # 图谱领域模型
    │   ├── node/
    │   │   ├── GraphNode                # 节点基类
    │   │   ├── StudentNode
    │   │   ├── KnowledgePointNode
    │   │   ├── DocumentNode
    │   │   └── EventNode                # 事件多态基类
    │   ├── edge/
    │   │   ├── GraphEdge                # 边基类
    │   │   ├── MasteryRelation
    │   │   ├── PrerequisiteRelation
    │   │   └── KnowledgeRelation
    │   ├── Subgraph
    │   └── WideGraph
    │
    ├── fusion/                          # M4-a: 宽图谱融合
    │   ├── WideGraphFusionService
    │   ├── FusionEngine
    │   └── algo/                        # 图算法（包内可见）
    │       ├── PageRankCalculator
    │       ├── DegreeCentrality
    │       └── CommunityDetector
    │
    ├── pruning/                         # M4-b: 剪枝引擎
    │   ├── PruningEngine
    │   ├── PruningStrategy
    │   ├── StrategyVersionManager
    │   └── filter/                      # 过滤链（包内可见）
    │       ├── HopFilter
    │       ├── WeightFilter
    │       └── RelationTypeFilter
    │
    ├── weight/                          # M4-c: 权重引擎
    │   ├── WeightEngine
    │   ├── rule/                        # 规则多态（包内可见）
    │   │   ├── TimeDecayRuleExecutor
    │   │   ├── BehaviorWeightAdjuster
    │   │   └── ChainPropagationHandler
    │   ├── simulator/
    │   │   └── WeightSimulator
    │   └── log/
    │       └── WeightChangeLogger
    │
    └── persistence/                     # 图谱持久化
        ├── GraphRepository              # 实现 L4 的 IGraphRepository
        ├── QueryBuilder
        └── TransactionManager
```

#### 4.2.2 `graphnexus-ai-analysis`（M5 · AI 分析引擎）

```
graphnexus-ai-analysis/
└── src/ai/
    ├── api/                             # 对外接口定义
    │   ├── IContextBuilderService
    │   ├── ILLMGatewayService
    │   ├── IAnalysisGeneratorService
    │   └── dto/
    │
    ├── context/                         # M5-a: 上下文构建
    │   ├── ContextBuilder
    │   ├── SubgraphSerializer
    │   ├── TokenBudgetTrimmer
    │   └── MultiSourceFusion
    │
    ├── gateway/                         # M5-b: LLM 网关
    │   ├── LLMGateway
    │   ├── ModelRouter
    │   ├── QuotaManager
    │   ├── FallbackStrategy
    │   └── CallLogger
    │
    ├── generator/                       # M5-c: 分析生成器
    │   ├── AnalysisGenerator            # 抽象基类（模板方法）
    │   ├── AttributionGenerator
    │   ├── TeachingSuggestGenerator
    │   ├── ReviewPathGenerator
    │   └── TrendPredictor
    │
    └── template/                        # Prompt 模板管理
        ├── PromptTemplateManager
        ├── TemplateRenderer
        └── TemplateVersionManager
```

### 4.3 领域支撑模块

#### 4.3.1 `graphnexus-master-data`（M1 · 主数据管理）

```
graphnexus-master-data/
└── src/masterdata/
    ├── api/
    │   ├── IStudentService
    │   ├── ITeacherService
    │   ├── IClassService
    │   └── IAuthorizationService
    │
    ├── student/
    │   ├── Student                      # 学生实体（含状态机）
    │   ├── StudentService
    │   └── StudentStatus
    │
    ├── teacher/
    │   ├── Teacher
    │   └── TeacherService
    │
    ├── class/
    │   ├── Class
    │   └── ClassService
    │
    ├── auth/
    │   ├── AuthorizationService
    │   └── AccessScopeEvaluator
    │
    └── persistence/
        ├── StudentRepository
        ├── TeacherRepository
        └── ClassRepository
```

#### 4.3.2 `graphnexus-knowledge-system`（M2 · 知识体系管理）

```
graphnexus-knowledge-system/
└── src/knowledge/
    ├── api/
    │   ├── IKnowledgeCategoryService
    │   ├── IKnowledgePointService
    │   ├── IPrerequisiteService
    │   └── IEntityAlignmentService
    │
    ├── category/
    │   ├── KnowledgeCategory            # 分类树节点（组合模式）
    │   ├── CategoryTreeService
    │   └── CategoryLevel
    │
    ├── kp/
    │   ├── KnowledgePoint
    │   └── KnowledgePointService
    │
    ├── prerequisite/
    │   ├── PrerequisiteRelation
    │   ├── PrerequisiteService
    │   ├── CycleDetector
    │   └── DependencyChain
    │
    └── alignment/
        ├── EntityAlignmentPair
        ├── EntityAlignmentService
        └── MergeStrategy
```

#### 4.3.3 `graphnexus-ingestion`（M3 · 数据入库引擎）

```
graphnexus-ingestion/
└── src/ingestion/
    ├── api/
    │   ├── IDocumentIngestionService
    │   ├── IEventIngestionService
    │   └── IAssociationModelService
    │
    ├── document/                        # M3-a: PDF 流水线
    │   ├── pipeline/
    │   │   ├── DocumentIngestionPipeline
    │   │   ├── DocumentProcessStep      # 步骤接口
    │   │   └── DocumentContext           # 流水线上下文
    │   └── step/
    │       ├── LayoutAnalysisStep
    │       ├── NerStep
    │       ├── ReStep
    │       └── GraphImportStep
    │
    ├── event/                           # M3-b: 事件导入流水线
    │   ├── pipeline/
    │   │   └── EventIngestionPipeline
    │   ├── step/
    │   │   ├── CsvValidateStep
    │   │   ├── StudentMatchStep
    │   │   ├── KpMatchStep
    │   │   └── EventCreateStep
    │   └── handler/
    │       ├── GradeCsvHandler
    │       ├── AssignmentHandler
    │       └── QuizHandler
    │
    ├── model/                           # M3-c: 关联模型定义
    │   ├── AssociationModelService
    │   ├── structure/
    │   │   └── ExamPaperDefinition
    │   ├── score/
    │   │   ├── ScoreAllocationStrategy
    │   │   └── ScoreAllocationRule
    │   └── validation/
    │       └── DataValidator
    │
    └── domain/
        ├── Document
        └── DocStatus
```

#### 4.3.4 `graphnexus-operations`（M8 · 运营分析）

```
graphnexus-operations/
└── src/operations/
    ├── api/
    │   └── IOperationsAnalyticsService
    │
    ├── throughput/
    │   └── DocumentThroughputAnalyzer
    │
    ├── coverage/
    │   ├── KnowledgeCoverageAnalyzer
    │   └── GapIdentifier
    │
    ├── utilization/
    │   ├── DocumentUtilizationAnalyzer
    │   └── ZombieDocumentDetector
    │
    ├── latency/
    │   └── ProcessingLatencyAnalyzer
    │
    └── overview/
        ├── SystemOverviewService
        └── DataAggregator
```

---

## 五、L4 · 基础设施层

> **单一职责**：封装数据库、外部 API、文件存储等基础技术实现。为上层提供技术能力，隔离外部变化。
>
> **依赖规则**：只能依赖公共库。不依赖任何业务层（L1/L2/L3）。

### 5.1 `graphnexus-infrastructure`

```
graphnexus-infrastructure/
└── src/infra/
    ├── persistence/                     # 持久化适配器
    │   ├── graph/
    │   │   ├── GraphDbConfig            # 图数据库连接配置
    │   │   ├── GraphRepositoryImpl      # 实现 L3 的 IGraphRepository
    │   │   └── QueryTemplate            # 图查询模板
    │   └── relational/
    │       ├── RelationalDbConfig       # 关系数据库配置
    │       └── BaseRepository           # 通用 Repository
    │
    ├── llm/                             # LLM 适配器
    │   ├── LLMClientAdapter             # 实现 L3 的 ILLMClient
    │   ├── LLMProvider                  # 具体 LLM 服务商调用
    │   └── LLMConfig
    │
    ├── storage/                         # 文件存储适配器
    │   ├── FileStorageAdapter           # 实现 L3 的 IFileStorage
    │   └── ObjectStorageProvider        # 对象存储实现
    │
    ├── messaging/                       # 消息队列适配器
    │   ├── MessageQueueAdapter
    │   ├── MessageProducer
    │   └── MessageConsumer
    │
    ├── cache/                           # 缓存适配器
    │   ├── CacheAdapter
    │   └── CacheProvider
    │
    └── notification/                    # 通知适配器
        ├── EmailSender
        ├── InAppNotifier
        └── WebhookSender
```

### 5.2 接口定义在 L3，实现在 L4

```
L3 领域层（定义接口）                    L4 基础设施层（提供实现）
────────────────────                    ────────────────────
graphnexus-graph-core                   graphnexus-infrastructure
  └── domain/repository/                  └── persistence/graph/
      IGraphRepository                        GraphRepositoryImpl
                                              (implements IGraphRepository)

graphnexus-ai-analysis
  └── gateway/
      ILLMClient                         └── llm/
                                              LLMClientAdapter
                                              (implements ILLMClient)

graphnexus-ingestion
  └── document/
      IFileStorage                       └── storage/
                                              FileStorageAdapter
                                              (implements IFileStorage)
```

**发布优势**：更换技术选型（如切换图数据库或 LLM 服务商）只需替换 L4 适配器实现，L3 领域层零改动，L2/L1 完全无感知。

---

## 六、横切关注点 · M7 · 系统运维

> **单一职责**：提供日志、备份、定时任务、通知、监控等跨层基础能力。
>
> **依赖规则**：独立于业务依赖链，被所有层通过 AOP/中间件/事件订阅的方式消费，不反向依赖业务模块。

### 6.1 `graphnexus-ops`

```
graphnexus-ops/
└── src/ops/
    ├── log/                             # 日志服务
    │   ├── LogService
    │   ├── LogSearchEngine
    │   └── TraceChainBuilder
    │
    ├── backup/                          # 备份服务
    │   ├── BackupService
    │   ├── BackupPolicy
    │   └── IntegrityVerifier
    │
    ├── scheduler/                       # 定时任务服务
    │   ├── ScheduledTaskService
    │   ├── CronJobManager
    │   └── TaskDagExecutor
    │
    ├── notification/                    # 通知服务
    │   ├── NotificationService
    │   └── template/
    │       └── NotificationRenderer
    │
    └── monitoring/                      # 监控告警
        ├── HealthCheckService
        └── SlowQueryDetector
```

---

## 七、公共基础库

> **单一职责**：提供所有后端模块共享的基础类型、异常体系和工具类。不包含任何业务逻辑。
>
> **依赖规则**：不依赖任何模块。被所有模块依赖。

### 7.1 `graphnexus-common`

```
graphnexus-common/
└── src/common/
    ├── model/                           # 共享基类
    │   ├── BaseEntity                   # 实体基类
    │   └── ValueObject                   # 值对象基类
    │
    ├── event/                           # 领域事件基类
    │   ├── DomainEvent
    │   └── EventPublisher
    │
    ├── exception/                       # 统一异常体系
    │   ├── BusinessException
    │   ├── NotFoundException
    │   └── UnauthorizedException
    │
    └── util/                            # 工具类
        ├── IdGenerator
        ├── DateTimeUtils
        └── CollectionUtils
```

---

## 八、模块间依赖关系

### 8.1 编译时依赖图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         依赖方向：自上而下（单向）                    │
│                                                                     │
│  L1 前端（7 个包）                                                   │
│    │                                                                │
│    │ HTTP 调用                                                       │
│    ▼                                                                │
│  L2 ┌──────────────────┐                                            │
│     │ graphnexus-api   │                                            │
│     └────────┬─────────┘                                            │
│              │ 调用（同层 L2）                                       │
│     ┌────────▼─────────┐                                            │
│     │graphnexus-app    │                                            │
│     └────────┬─────────┘                                            │
│              │ 编排（依赖下层 L3，通过接口）                          │
│     ┌────────┼─────────┬──────────────┐                             │
│     ▼        ▼         ▼              ▼                             │
│  L3 ┌────────────┐ ┌────────────┐ ┌────────────┐                   │
│     │graph-core  │ │ai-analysis │ │ingestion   │ ...               │
│     │(领域核心)  │ │(领域核心)  │ │(领域支撑)  │                   │
│     └─────┬──────┘ └──────┬─────┘ └──────┬─────┘                   │
│           │               │              │                          │
│           │   依赖（同层 L3，单向）       │                          │
│           ▼               ▼              ▼                          │
│     ┌────────────┐ ┌────────────┐ ┌────────────┐                   │
│     │master-data │ │knowledge   │ │operations  │                   │
│     │(领域支撑)  │ │-system     │ │(领域支撑)  │                   │
│     └─────┬──────┘ └──────┬─────┘ └──────┬─────┘                   │
│           │               │              │                          │
│           └───────────────┼──────────────┘                          │
│                           │ 依赖（下层 L4，通过接口）                 │
│                           ▼                                         │
│  L4               ┌──────────────┐                                  │
│                   │infrastructure│                                  │
│                   └──────┬───────┘                                  │
│                          │ 依赖                                     │
│                          ▼                                          │
│                    ┌──────────┐                                     │
│                    │ common   │                                     │
│                    └──────────┘                                     │
│                                                                     │
│  ╔═══════════════════════════════════════════════════════════════╗  │
│  ║  graphnexus-ops (横切 M7)                                    ║  │
│  ║  独立于主依赖链，通过 AOP/中间件/事件订阅跨层生效               ║  │
│  ╚═══════════════════════════════════════════════════════════════╝  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 依赖规则（强制）

| 规则 | 说明 |
|------|------|
| **L1 → L2** | 前端只能通过 HTTP 调用 `graphnexus-api`，不能直接调用任何后端模块 |
| **L2 → L3** | `graphnexus-application` 通过接口依赖 L3 模块，不直接依赖实现类 |
| **L2 → L4** | L2 不直接依赖 L4，通过 L3 的接口间接使用基础设施能力 |
| **L3 → L4** | 领域层定义接口（Repository/Client/Adapter），L4 提供实现 |
| **L3 同层** | 领域核心（M4/M5）可依赖领域支撑（M1/M2/M3/M8），反向禁止 |
| **L4 → 外部** | 仅 L4 可依赖外部技术库（数据库驱动、LLM SDK、存储 SDK） |
| **禁止循环** | 任何两个模块之间不允许存在循环依赖 |
| **M7 独立** | `graphnexus-ops` 独立于主依赖链，通过 AOP/中间件跨层生效 |

### 8.3 依赖矩阵

| 依赖方 ↓ \ 被依赖方 → | common | infra | master-data | knowledge | ingestion | graph-core | ai-analysis | operations | application | api | ops |
|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|
| **common** | — | — | — | — | — | — | — | — | — | — | — |
| **infrastructure** | ✅ | — | — | — | — | — | — | — | — | — | — |
| **master-data** | ✅ | ✅ | — | — | — | — | — | — | — | — | — |
| **knowledge-system** | ✅ | ✅ | — | — | — | — | — | — | — | — | — |
| **ingestion** | ✅ | ✅ | ✅ | ✅ | — | — | — | — | — | — | — |
| **graph-core** | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | — | — | — |
| **ai-analysis** | ✅ | ✅ | — | ✅ | ✅ | ✅ | — | — | — | — | — |
| **operations** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | — | — |
| **application** | ✅ | — | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — |
| **api** | ✅ | — | — | — | — | — | — | — | ✅ | — | — |
| **ops** | ✅ | ✅ | — | — | — | — | — | — | — | — | — |

> 空单元格表示该模块**不允许**依赖被依赖方。✅ 表示允许的编译时依赖。

---

## 九、构建配置

### 9.1 模块声明

```
项目根构建配置声明所有子模块：

// L2
include 'graphnexus-api'
include 'graphnexus-application'

// L3 领域核心
include 'graphnexus-graph-core'
include 'graphnexus-ai-analysis'

// L3 领域支撑
include 'graphnexus-master-data'
include 'graphnexus-knowledge-system'
include 'graphnexus-ingestion'
include 'graphnexus-operations'

// L4
include 'graphnexus-infrastructure'

// 横切
include 'graphnexus-ops'

// 公共
include 'graphnexus-common'
```

### 9.2 关键模块依赖声明示例

```
// graphnexus-graph-core（L3 领域核心）
dependencies {
    // 同层依赖（L3 领域支撑）
    implementation project(':graphnexus-master-data')
    implementation project(':graphnexus-knowledge-system')
    implementation project(':graphnexus-ingestion')

    // 下层依赖（L4 通过接口）
    implementation project(':graphnexus-infrastructure')

    // 公共
    implementation project(':graphnexus-common')

    // 外部依赖（仅 L4 和公共库可引入外部技术库）
    implementation 'graph-db-driver:3.2.9'
}

// graphnexus-application（L2 应用层）
dependencies {
    // 下层依赖（L3 领域层，通过接口）
    implementation project(':graphnexus-master-data')
    implementation project(':graphnexus-knowledge-system')
    implementation project(':graphnexus-ingestion')
    implementation project(':graphnexus-graph-core')
    implementation project(':graphnexus-ai-analysis')
    implementation project(':graphnexus-operations')

    // 公共
    implementation project(':graphnexus-common')

    // 注意：不依赖 graphnexus-infrastructure（L2 不直接依赖 L4）
}

// graphnexus-api（L2 HTTP 入口）
dependencies {
    // 同层依赖（L2 编排层）
    implementation project(':graphnexus-application')

    // 公共
    implementation project(':graphnexus-common')

    // 外部依赖
    implementation 'web-framework:3.0'
}
```

---

## 十、与逻辑视图分层对照

| 逻辑视图分层 | 逻辑模块 | 开发视图代码模块 | 项目类型 |
|------------|---------|----------------|---------|
| **L1 表示层** | P1~P5 五个界面 | `graphnexus-admin` / `teacher` / `student` / `ops-console` / `operations-console` | 前端 ×5 |
| | 公共 UI | `graphnexus-shared-components` | 前端 |
| | 公共类型 | `graphnexus-shared-types` | 前端 |
| **L2 应用层** | M6 应用服务 | `graphnexus-api` + `graphnexus-application` | 后端 ×2 |
| **L3 领域核心** | M4 图谱核心 | `graphnexus-graph-core` | 后端 |
| | M5 AI 分析 | `graphnexus-ai-analysis` | 后端 |
| **L3 领域支撑** | M1 主数据 | `graphnexus-master-data` | 后端 |
| | M2 知识体系 | `graphnexus-knowledge-system` | 后端 |
| | M3 数据入库 | `graphnexus-ingestion` | 后端 |
| | M8 运营分析 | `graphnexus-operations` | 后端 |
| **L4 基础设施** | — | `graphnexus-infrastructure` | 后端 |
| **横切** | M7 系统运维 | `graphnexus-ops` | 后端 |
| **公共** | — | `graphnexus-common` | 后端 |
| **合计** | | **19 个代码模块** | 后端×12 + 前端×7 |