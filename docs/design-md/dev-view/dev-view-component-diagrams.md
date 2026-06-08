# GraphNexus 组件图

> 基于图谱技术的 AI 上下文处理与精准问答系统 — 开发视图 · 组件图
>
> 版本：v1.0 | 创建日期：2026-06-08
>
> 参考文档：[dev-view.md](dev-view.md) · 开发视图设计文档

---

## 一、系统整体组件图

展示 19 个代码模块按四层架构的组织关系和编译时依赖。

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              GraphNexus 系统组件图                                    │
│                              ──────────────────────                                   │
│                                                                                      │
│  ╔═════════════════════════════════ L1 · 表示层 ═══════════════════════════════════╗ │
│  ║                                                                                 ║ │
│  ║  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        ║ │
│  ║  │  «component» │  │  «component» │  │  «component» │  │  «component» │        ║ │
│  ║  │ graphnexus-  │  │ graphnexus-  │  │ graphnexus-  │  │ graphnexus-  │        ║ │
│  ║  │    admin     │  │   teacher    │  │   student    │  │ ops-console  │        ║ │
│  ║  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘        ║ │
│  ║                                                                                 ║ │
│  ║  ┌──────────────┐                                                               ║ │
│  ║  │  «component» │                                                               ║ │
│  ║  │ graphnexus-  │                                                               ║ │
│  ║  │ operations-  │                                                               ║ │
│  ║  │   console    │                                                               ║ │
│  ║  └──────────────┘                                                               ║ │
│  ║                                                                                 ║ │
│  ║  ┌──────────────────────┐  ┌──────────────────────┐                              ║ │
│  ║  │    «component»       │  │    «component»       │                              ║ │
│  ║  │  graphnexus-shared-  │◀─│  graphnexus-shared-  │                              ║ │
│  ║  │     components       │  │       types          │                              ║ │
│  ║  └──────────────────────┘  └──────────────────────┘                              ║ │
│  ║        ▲                           ▲                                             ║ │
│  ║        └──────────────┬────────────┘  (同层依赖)                                  ║ │
│  ║                       │                                                           ║ │
│  ╚═══════════════════════╪═══════════════════════════════════════════════════════════╝ │
│                          │                                                            │
│                    HTTP 调用                                                           │
│                          │                                                            │
│  ╔═══════════════════════╪════════════════ L2 · 应用层 ═════════════════════════════╗ │
│  ║                       │                                                           ║ │
│  ║  ┌────────────────────────────┐  ┌────────────────────────────┐                   ║ │
│  ║  │       «component»          │  │       «component»          │                   ║ │
│  ║  │     graphnexus-api         │  │  graphnexus-application    │                   ║ │
│  ║  ├────────────────────────────┤  ├────────────────────────────┤                   ║ │
│  ║  │ «provides»                 │  │ «provides»                 │                   ║ │
│  ║  │  · REST /admin/*           │  │  · IAttributionQuery       │                   ║ │
│  ║  │  · REST /teacher/*         │  │  · IClassAnalysis          │                   ║ │
│  ║  │  · REST /student/*         │  │  · IVisualization          │                   ║ │
│  ║  │  · REST /ops/*             │  │  · IReportExport           │                   ║ │
│  ║  │  · REST /operations/*      │  │  · IPersonalKB             │                   ║ │
│  ║  │                            │  │  · IStudentSelf            │                   ║ │
│  ║  │ «requires»                 │  │  · IOperationsDashboard    │                   ║ │
│  ║  │  ──▶ graphnexus-application│  │                            │                   ║ │
│  ║  └────────────┬───────────────┘  │ «requires»                 │                   ║ │
│  ║               │                  │  ──▶ L3 (多个)            │                   ║ │
│  ║               │ 调用(同层)        └────────────┬───────────────┘                   ║ │
│  ║               └───────────────────────────────┘                                   ║ │
│  ║                                                                                   ║ │
│  ╚═══════════════════════════════════════╪═══════════════════════════════════════════╝ │
│                                          │                                             │
│                                   编排调用(通过接口)                                    │
│                                          │                                             │
│  ╔═══════════════════════════════════════╪══════ L3 · 领域层 ════════════════════════╗ │
│  ║                                       │                                            ║ │
│  ║  ┌─── 领域核心 (Domain Core) ──────────────────────────────────────────────────┐  ║ │
│  ║  │                                                                              │  ║ │
│  ║  │  ┌──────────────────────────┐    ┌──────────────────────────┐                │  ║ │
│  ║  │  │      «component»         │    │      «component»         │                │  ║ │
│  ║  │  │  graphnexus-graph-core   │    │ graphnexus-ai-analysis   │                │  ║ │
│  ║  │  ├──────────────────────────┤    ├──────────────────────────┤                │  ║ │
│  ║  │  │ «provides»               │    │ «provides»               │                │  ║ │
│  ║  │  │  · IGraphFusionService   │    │  · IContextBuilderService │                │  ║ │
│  ║  │  │  · IPruningService       │    │  · ILLMGatewayService    │                │  ║ │
│  ║  │  │  · IWeightService        │    │  · IAnalysisGenerator    │                │  ║ │
│  ║  │  │                          │    │                          │                │  ║ │
│  ║  │  │ «requires»               │    │ «requires»               │                │  ║ │
│  ║  │  │  ──▶ master-data         │    │  ──▶ graph-core           │                │  ║ │
│  ║  │  │  ──▶ knowledge-system    │    │  ──▶ knowledge-system     │                │  ║ │
│  ║  │  │  ──▶ ingestion           │    │  ──▶ ingestion            │                │  ║ │
│  ║  │  └────────────┬─────────────┘    └────────────┬─────────────┘                │  ║ │
│  ║  │               │                               │ (同层依赖,单向)                │  ║ │
│  ║  └───────────────┼───────────────────────────────┼──────────────────────────────┘  ║ │
│  ║                  │                               │                                 ║ │
│  ║  ┌─── 领域支撑 (Domain Support) ─────────────────┼──────────────────────────────┐  ║ │
│  ║  │               │                               │                               │  ║ │
│  ║  │               ▼                               ▼                               │  ║ │
│  ║  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐                  │  ║ │
│  ║  │  │  «component»   │  │  «component»   │  │  «component»   │                  │  ║ │
│  ║  │  │ graphnexus-    │  │ graphnexus-    │  │ graphnexus-    │                  │  ║ │
│  ║  │  │ master-data    │  │ knowledge-     │  │  ingestion     │                  │  ║ │
│  ║  │  │                │  │ system         │  │                │                  │  ║ │
│  ║  │  ├────────────────┤  ├────────────────┤  ├────────────────┤                  │  ║ │
│  ║  │  │ «provides»     │  │ «provides»     │  │ «provides»     │                  │  ║ │
│  ║  │  │ · IStudent     │  │ · ICategory    │  │ · IDocument    │                  │  ║ │
│  ║  │  │ · ITeacher     │  │ · IKnowledgePt │  │ · IEvent       │                  │  ║ │
│  ║  │  │ · IClass       │  │ · IPrerequisite│  │ · IAssociation │                  │  ║ │
│  ║  │  │ · IAuth        │  │ · IAlignment   │  │                │                  │  ║ │
│  ║  │  │                │  │                │  │ «requires»     │                  │  ║ │
│  ║  │  │                │  │                │  │ ──▶ master-data│                  │  ║ │
│  ║  │  │                │  │                │  │ ──▶ knowledge  │                  │  ║ │
│  ║  │  └────────────────┘  └────────────────┘  └──────┬─────────┘                  │  ║ │
│  ║  │                          ▲                     │                             │  ║ │
│  ║  │                          │ (同层依赖)            │                             │  ║ │
│  ║  │  ┌────────────────┐      │                     │                             │  ║ │
│  ║  │  │  «component»   │◀─────┘                     │                             │  ║ │
│  ║  │  │ graphnexus-    │                            │                             │  ║ │
│  ║  │  │  operations    │                            │                             │  ║ │
│  ║  │  ├────────────────┤                            │                             │  ║ │
│  ║  │  │ «provides»     │                            │                             │  ║ │
│  ║  │  │ · IOperations  │                            │                             │  ║ │
│  ║  │  │   Analytics    │                            │                             │  ║ │
│  ║  │  └────────────────┘                            │                             │  ║ │
│  ║  │                                               │                             │  ║ │
│  ║  └───────────────────────────────────────────────┼─────────────────────────────┘  ║ │
│  ║                                                   │                               ║ │
│  ╚═══════════════════════════════════════════════════╪═══════════════════════════════╝ │
│                                                       │                                │
│                                               依赖(通过接口)                             │
│                                                       │                                │
│  ╔═══════════════════════════════════════════════════╪══ L4 · 基础设施层 ═════════════╗ │
│  ║                                                   │                                 ║ │
│  ║  ┌──────────────────────────────────────────────────────────────────────────┐      ║ │
│  ║  │                        «component»                                        │      ║ │
│  ║  │                  graphnexus-infrastructure                                │      ║ │
│  ║  ├──────────────────────────────────────────────────────────────────────────┤      ║ │
│  ║  │ «provides» [realizations]                                                │      ║ │
│  ║  │  · IGraphRepository     ──▶ GraphRepositoryImpl     (图数据库适配器)       │      ║ │
│  ║  │  · ILLMClient          ──▶ LLMClientAdapter        (LLM API适配器)       │      ║ │
│  ║  │  · IFileStorage        ──▶ FileStorageAdapter       (对象存储适配器)       │      ║ │
│  ║  │  · IMessageQueue       ──▶ MessageQueueAdapter      (消息队列适配器)       │      ║ │
│  ║  │  · ICache              ──▶ CacheAdapter             (缓存适配器)           │      ║ │
│  ║  │  · INotificationSender ──▶ EmailSender/InAppNotifier (通知适配器)          │      ║ │
│  ║  │                                                                           │      ║ │
│  ║  │ «requires»                                                                │      ║ │
│  ║  │  ──▶ graphnexus-common                                                   │      ║ │
│  ║  │  ──▶ 外部技术库 (图数据库驱动 / LLM SDK / 存储 SDK / 消息队列客户端)        │      ║ │
│  ║  └──────────────────────────────────────────────────────────────────────────┘      ║ │
│  ║                                                                                    ║ │
│  ╚════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐ │
│  │                          «component»  graphnexus-common                           │ │
│  │  提供: BaseEntity, ValueObject, DomainEvent, BusinessException, 工具类            │ │
│  │  被所有模块依赖                                                                   │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                        │
│  ╔════════════════════════════════════ 横切关注点 ════════════════════════════════════╗ │
│  ║                                                                                    ║ │
│  ║  ┌──────────────────────────────────────────────────────────────────────────────┐ ║ │
│  ║  │                            «component»                                        │ ║ │
│  ║  │                        graphnexus-ops                                         │ ║ │
│  ║  ├──────────────────────────────────────────────────────────────────────────────┤ ║ │
│  ║  │ «provides»                                                                    │ ║ │
│  ║  │  · ILogService              · IBackupService        · IScheduledTaskService  │ ║ │
│  ║  │  · INotificationService     · IMonitoringService                              │ ║ │
│  ║  │                                                                               │ ║ │
│  ║  │ «requires»                                                                    │ ║ │
│  ║  │  ──▶ graphnexus-common                                                       │ ║ │
│  ║  │  ──▶ graphnexus-infrastructure                                               │ ║ │
│  ║  └──────────────────────────────────────────────────────────────────────────────┘ ║ │
│  ║                                                                                    ║ │
│  ║  ┌─ 跨层生效方式 ────────────────────────────────────────────────────────────────┐ ║ │
│  ║  │  L1 ← M7 (日志采集: 前端错误上报)                                              │ ║ │
│  ║  │  L2 ← M7 (AOP 拦截: 请求日志、性能指标)                                       │ ║ │
│  ║  │  L3 ← M7 (事件订阅: 权重变更通知、备份触发)                                    │ ║ │
│  ║  │  L4 ← M7 (中间件: 数据库慢查询监控)                                            │ ║ │
│  ║  └────────────────────────────────────────────────────────────────────────────────┘ ║ │
│  ╚══════════════════════════════════════════════════════════════════════════════════════╝ │
│                                                                                        │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、L3 领域层内部组件图

展示 L3 领域核心与领域支撑之间的接口契约和依赖关系。

```
┌─────────────────────────────────────────────────────────────────┐
│                    L3 · 领域层 内部组件图                         │
│                    ──────────────────────                        │
│                                                                 │
│  ┌────────────────────── 领域核心 (Domain Core) ──────────────┐ │
│  │                                                              │ │
│  │  ┌─────────────────────────────────┐                        │ │
│  │  │     «component»                  │                       │ │
│  │  │ graphnexus-graph-core            │                       │ │
│  │  ├─────────────────────────────────┤                       │ │
│  │  │ «provides»                      │                       │ │
│  │  │  ○ IGraphFusionService          │                       │ │
│  │  │    · buildWideGraph(scope)       │─────── 宽图谱融合      │ │
│  │  │    · getGraphOverview()          │                       │ │
│  │  │    · computePageRank(topK)       │                       │ │
│  │  │    · getNodeNeighborhood(id)     │                       │ │
│  │  │                                 │                       │ │
│  │  │  ○ IPruningService              │                       │ │
│  │  │    · prune(nodeId, taskType)     │─────── 策略驱动剪枝    │ │
│  │  │    · createStrategy(config)      │                       │ │
│  │  │    · compareStrategy(A, B)       │                       │ │
│  │  │                                 │                       │ │
│  │  │  ○ IWeightService               │                       │ │
│  │  │    · adjustWeightByEvent(id)     │─────── 权重动态更新    │ │
│  │  │    · executeTimeDecay(scope)     │                       │ │
│  │  │    · simulateRule(ruleId, scope) │                       │ │
│  │  │    · getWeightHistory(sid, kpid) │                       │ │
│  │  │                                 │                       │ │
│  │  │  ○ IGraphRepository (定义)      │─────── 领域层定义接口  │ │
│  │  │    · findNode(id)               │        L4 提供实现    │ │
│  │  │    · findSubgraph(start, hops)  │                       │ │
│  │  │    · saveNode(node)             │                       │ │
│  │  └────────────┬────────────────────┘                       │ │
│  │               │                                            │ │
│  │  ┌────────────┴────────────────────┐                       │ │
│  │  │     «component»                  │                       │ │
│  │  │ graphnexus-ai-analysis           │                       │ │
│  │  ├─────────────────────────────────┤                       │ │
│  │  │ «provides»                      │                       │ │
│  │  │  ○ IContextBuilderService       │                       │ │
│  │  │    · buildContext(subgraph)      │─────── 上下文构建     │ │
│  │  │    · estimateTokenCount(subgraph)│                      │ │
│  │  │                                 │                       │ │
│  │  │  ○ ILLMGatewayService           │                       │ │
│  │  │    · callLLM(request)           │─────── LLM 网关       │ │
│  │  │    · switchModel(taskType, id)   │                       │ │
│  │  │    · getQuotaStatus(modelId)     │                       │ │
│  │  │                                 │                       │ │
│  │  │  ○ IAnalysisGeneratorService    │                       │ │
│  │  │    · generateAttribution(req)    │─────── 分析生成器     │ │
│  │  │    · generateTeachingSuggestion  │                       │ │
│  │  │    · generateReviewPath          │                       │ │
│  │  │                                 │                       │ │
│  │  │  ○ ILLMClient (定义)            │─────── 领域层定义接口  │ │
│  │  │    · call(prompt)               │        L4 提供实现    │ │
│  │  │    · stream(prompt)             │                       │ │
│  │  └────────────┬────────────────────┘                       │ │
│  │               │                                            │ │
│  └───────────────┼────────────────────────────────────────────┘ │
│                  │                                              │
│                  │  依赖（通过接口，单向）                        │
│                  ▼                                              │
│  ┌────────────────────── 领域支撑 (Domain Support) ──────────┐ │
│  │                                                             │ │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  │ │
│  │  │ «component»   │  │ «component»   │  │ «component»   │  │ │
│  │  │ graphnexus-   │  │ graphnexus-   │  │ graphnexus-   │  │ │
│  │  │ master-data   │  │ knowledge-    │  │  ingestion    │  │ │
│  │  │               │  │ system        │  │               │  │ │
│  │  ├───────────────┤  ├───────────────┤  ├───────────────┤  │ │
│  │  │ «provides»    │  │ «provides»    │  │ «provides»    │  │ │
│  │  │ ○ IStudent    │  │ ○ ICategory   │  │ ○ IDocument   │  │ │
│  │  │ ○ ITeacher    │  │ ○ IKnowledgePt│  │ ○ IEvent      │  │ │
│  │  │ ○ IClass      │  │ ○ IPrerequisite│ │ ○ IAssociation│  │ │
│  │  │ ○ IAuth       │  │ ○ IAlignment  │  │               │  │ │
│  │  │               │  │               │  │ «requires»    │  │ │
│  │  │               │  │               │  │ ──▶ master-   │  │ │
│  │  │               │  │               │  │     data      │  │ │
│  │  │               │  │               │  │ ──▶ knowledge-│  │ │
│  │  │               │  │               │  │     system    │  │ │
│  │  └───────────────┘  └───────────────┘  └───────┬───────┘  │ │
│  │                                                  │         │ │
│  │  ┌───────────────┐                              │         │ │
│  │  │ «component»   │◀─────────────────────────────┘         │ │
│  │  │ graphnexus-   │                                        │ │
│  │  │ operations    │                                        │ │
│  │  ├───────────────┤                                        │ │
│  │  │ «provides»    │                                        │ │
│  │  │ ○ IOperations │                                        │ │
│  │  │   Analytics   │                                        │ │
│  │  └───────────────┘                                        │ │
│  │                                                             │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
│  内部接口契约：                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  M4(graph-core)    ──▶ M1(master-data)     读取主数据        │ │
│  │                     ──▶ M2(knowledge-system) 读取知识体系     │ │
│  │                     ──▶ M3(ingestion)        消费入库数据     │ │
│  │                                                             │ │
│  │  M5(ai-analysis)   ──▶ M4(graph-core)       读取剪枝子图     │ │
│  │                     ──▶ M2(knowledge-system) 读取前置依赖     │ │
│  │                     ──▶ M3(ingestion)        匹配教辅资源     │ │
│  │                                                             │ │
│  │  M3(ingestion)     ──▶ M1(master-data)       学号匹配        │ │
│  │                     ──▶ M2(knowledge-system)  知识点匹配      │ │
│  │                                                             │ │
│  │  M8(operations)    ──▶ M1, M2, M3, M4        统计聚合查询    │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、L3 ↔ L4 接口与实现分离图

展示领域层定义接口、基础设施层提供实现的适配器模式关系。

```
┌─────────────────────────────────────────────────────────────────┐
│                  L3 ↔ L4 接口与实现关系                           │
│                  ──────────────────────                          │
│                                                                 │
│  L3 · 领域层（定义接口）          L4 · 基础设施层（提供实现）      │
│  ═══════════════════════          ════════════════════════════    │
│                                                                 │
│  ┌────────────────────────┐      ┌──────────────────────────┐  │
│  │   graphnexus-graph-core│      │  graphnexus-infrastructure│  │
│  │                        │      │                          │  │
│  │  ┌──────────────────┐  │      │  ┌────────────────────┐  │  │
│  │  │«interface»        │  │      │  │«component»         │  │  │
│  │  │IGraphRepository   │◀─┼──────┼──│GraphRepositoryImpl │  │  │
│  │  ├──────────────────┤  │ 实现  │  ├────────────────────┤  │  │
│  │  │+ findNode(id)    │  │      │  │- dbConfig          │  │  │
│  │  │+ findSubgraph()  │  │      │  │- queryTemplate     │  │  │
│  │  │+ saveNode(node)  │  │      │  │+ findNode(id)      │  │  │
│  │  │+ saveEdge(edge)  │  │      │  │+ findSubgraph()    │  │  │
│  │  │+ deleteNode(id)  │  │      │  │+ saveNode(node)    │  │  │
│  │  └──────────────────┘  │      │  └────────────────────┘  │  │
│  └────────────────────────┘      │                          │  │
│                                  │  ┌────────────────────┐  │  │
│  ┌────────────────────────┐      │  │«component»         │  │  │
│  │  graphnexus-ai-analysis│      │  │LLMClientAdapter    │  │  │
│  │                        │      │  ├────────────────────┤  │  │
│  │  ┌──────────────────┐  │      │  │- llmConfig         │  │  │
│  │  │«interface»        │  │      │  │- quotaManager     │  │  │
│  │  │ILLMClient         │◀─┼──────┼──│+ call(prompt)     │  │  │
│  │  ├──────────────────┤  │ 实现  │  │+ stream(prompt)   │  │  │
│  │  │+ call(prompt)    │  │      │  └────────────────────┘  │  │
│  │  │+ stream(prompt)  │  │      │                          │  │
│  │  │+ countTokens()   │  │      │  ┌────────────────────┐  │  │
│  │  └──────────────────┘  │      │  │«component»         │  │  │
│  └────────────────────────┘      │  │FileStorageAdapter  │  │  │
│                                  │  ├────────────────────┤  │  │
│  ┌────────────────────────┐      │  │- storageConfig     │  │  │
│  │  graphnexus-ingestion  │      │  │- bucketName        │  │  │
│  │                        │      │  │+ upload(file)      │  │  │
│  │  ┌──────────────────┐  │      │  │+ download(id)      │  │  │
│  │  │«interface»        │  │      │  │+ delete(id)        │  │  │
│  │  │IFileStorage       │◀─┼──────┼──│                    │  │  │
│  │  ├──────────────────┤  │ 实现  │  └────────────────────┘  │  │
│  │  │+ upload(file)    │  │      │                          │  │
│  │  │+ download(id)    │  │      │  ┌────────────────────┐  │  │
│  │  │+ delete(id)      │  │      │  │«component»         │  │  │
│  │  └──────────────────┘  │      │  │MessageQueueAdapter │  │  │
│  └────────────────────────┘      │  ├────────────────────┤  │  │
│                                  │  │+ publish(topic,msg)│  │  │
│  ┌────────────────────────┐      │  │+ subscribe(topic)  │  │  │
│  │  graphnexus-application│      │  └────────────────────┘  │  │
│  │                        │      │                          │  │
│  │  ┌──────────────────┐  │      │  ┌────────────────────┐  │  │
│  │  │«interface»        │  │      │  │«component»         │  │  │
│  │  │ICache             │◀─┼──────┼──│CacheAdapter        │  │  │
│  │  ├──────────────────┤  │ 实现  │  ├────────────────────┤  │  │
│  │  │+ get(key)        │  │      │  │+ get(key)          │  │  │
│  │  │+ set(key, val)   │  │      │  │+ set(key, val)     │  │  │
│  │  │+ delete(key)     │  │      │  └────────────────────┘  │  │
│  │  └──────────────────┘  │      │                          │  │
│  └────────────────────────┘      └──────────────────────────┘  │
│                                                                 │
│  关键约束：                                                      │
│  · 接口定义在 L3，实现在 L4                                       │
│  · L3 模块只依赖接口，不依赖 L4 的具体实现                         │
│  · 运行时通过依赖注入将 L4 实现绑定到 L3 接口                       │
│  · 更换技术选型只需替换 L4 组件，L3 零改动                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、L2 应用层编排接口图

展示 graphnexus-api 和 graphnexus-application 的内部接口契约。

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          L2 · 应用层 组件图                                │
│                          ──────────────────                               │
│                                                                          │
│         L1 前端调用                                                        │
│         ┌────────────┐                                                    │
│         │ admin      │────┐                                               │
│         │ teacher    │────┤                                               │
│         │ student    │────┤  HTTP                                         │
│         │ ops-console│────┼── REST API                                    │
│         │ ops-dash   │────┘                                               │
│         └────────────┘       │                                            │
│                              │                                            │
│  ┌───────────────────────────▼──────────────────────────────────────┐    │
│  │                     «component»                                    │    │
│  │                   graphnexus-api                                   │    │
│  ├───────────────────────────────────────────────────────────────────┤    │
│  │ «provides» (REST 端点, 按角色分组)                                  │    │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌──────────────────────┐ │    │
│  │  │ /api/admin/*     │ │ /api/teacher/*  │ │ /api/student/*       │ │    │
│  │  │ · Document       │ │ · Attribution   │ │ · SelfService        │ │    │
│  │  │ · Ingestion      │ │ · Class         │ └──────────────────────┘ │    │
│  │  │ · Alignment      │ │ · Visualization │                          │    │
│  │  │ · Knowledge       │ │ · Report        │ ┌──────────────────────┐ │    │
│  │  │ · Strategy       │ │ · PersonalKB    │ │ /api/operations/*    │ │    │
│  │  │ · Model          │ │ · Homework      │ │ · Dashboard          │ │    │
│  │  └─────────────────┘ └─────────────────┘ └──────────────────────┘ │    │
│  │                                                                   │    │
│  │  ┌─────────────────────────────────────────────────────────────┐ │    │
│  │  │ /api/ops/*                                                  │ │    │
│  │  │ · Log · Backup · Task                                       │ │    │
│  │  └─────────────────────────────────────────────────────────────┘ │    │
│  │                                                                   │    │
│  │ «requires»                                                        │    │
│  │  ──▶ graphnexus-application    (同层依赖)                         │    │
│  └───────────────────────────┬───────────────────────────────────────┘    │
│                              │                                            │
│                              │ 调用 (同层 L2)                               │
│                              ▼                                            │
│  ┌───────────────────────────────────────────────────────────────────┐    │
│  │                     «component»                                    │    │
│  │               graphnexus-application                               │    │
│  ├───────────────────────────────────────────────────────────────────┤    │
│  │ «provides» (7 组用例编排接口)                                      │    │
│  │                                                                    │    │
│  │  ┌──────────────────────┐  ┌──────────────────────┐               │    │
│  │  │ IAttributionQuery     │  │ IClassAnalysis        │               │    │
│  │  │ · queryAttribution() │  │ · getOverview()       │               │    │
│  │  │ · autoComplete()     │  │ · drillDownStudents() │               │    │
│  │  │ · getDrillDown()     │  └──────────────────────┘               │    │
│  │  └──────────────────────┘                                         │    │
│  │                                                                    │    │
│  │  ┌──────────────────────┐  ┌──────────────────────┐               │    │
│  │  │ IVisualization        │  │ IReportExport         │               │    │
│  │  │ · getKnowledgeGraph()│  │ · exportReport()      │               │    │
│  │  │ · getWeightTrend()   │  │ · generateShareLink() │               │    │
│  │  │ · compareSnapshots() │  │ · batchExport()       │               │    │
│  │  └──────────────────────┘  └──────────────────────┘               │    │
│  │                                                                    │    │
│  │  ┌──────────────────────┐  ┌──────────────────────┐               │    │
│  │  │ IPersonalKB           │  │ IStudentSelf          │               │    │
│  │  │ · uploadPersonalDoc()│  │ · getMasteryRadar()   │               │    │
│  │  │ · applyForPublic()   │  │ · getWeaknessList()   │               │    │
│  │  └──────────────────────┘  │ · markReviewed()      │               │    │
│  │                             └──────────────────────┘               │    │
│  │                                                                    │    │
│  │  ┌────────────────────────────────────────────────────────────┐   │    │
│  │  │ IOperationsDashboard                                        │   │    │
│  │  │ · getDashboardOverview()  · getDocumentUtilizationView()    │   │    │
│  │  │ · getThroughputView()     · getProcessingLatencyView()     │   │    │
│  │  │ · getCoverageView()                                        │   │    │
│  │  └────────────────────────────────────────────────────────────┘   │    │
│  │                                                                    │    │
│  │ «requires»                                                         │    │
│  │  ──▶ graphnexus-master-data          (权限校验)                    │    │
│  │  ──▶ graphnexus-knowledge-system      (知识点查询)                 │    │
│  │  ──▶ graphnexus-ingestion             (入库调用)                   │    │
│  │  ──▶ graphnexus-graph-core            (图谱查询)                   │    │
│  │  ──▶ graphnexus-ai-analysis           (AI 分析)                    │    │
│  │  ──▶ graphnexus-operations            (运营分析)                   │    │
│  └───────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 五、组件依赖关系全局矩阵

以矩阵形式展示所有 19 个组件之间的允许依赖关系。

```
                        被依赖方
         ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
         │ C  │ I  │ M  │ K  │ I  │ G  │ A  │ O  │ A  │ A  │ O  │ S  │ S  │
         │ O  │ N  │ A  │ N  │ N  │ R  │ I  │ P  │ P  │ P  │ P  │ H  │ H  │
         │ M  │ F  │ S  │ O  │ G  │ A  │ -  │ E  │ P  │ I  │ S  │ A  │ A  │
         │ M  │ R  │ T  │ W  │ E  │ P  │ A  │ R  │ L  │    │    │ R  │ R  │
         │ O  │ A  │ E  │ L  │ S  │ H  │ N  │ A  │ I  │    │    │ E  │ E  │
         │ N  │    │ R  │ E  │ T  │ -  │ A  │ T  │ C  │    │    │ D  │ D  │
         │    │    │ -  │ D  │    │ C  │ L  │ I  │ A  │    │    │ -  │ -  │
         │    │    │ D  │ G  │    │ O  │ Y  │ O  │ T  │    │    │ T  │ C  │
         │    │    │ A  │ E  │    │ R  │ S  │ N  │ I  │    │    │ Y  │ O  │
         │    │    │ T  │ -  │    │ E  │ I  │ S  │ O  │    │    │ P  │ M  │
         │    │    │ A  │ S  │    │    │ S  │    │ N  │    │    │ E  │ P  │
         │    │    │    │ Y  │    │    │    │    │    │    │    │ S  │    │
         │    │    │    │ S  │    │    │    │    │    │    │    │    │    │
  ┌──────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┤
  │ COM  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ INF  │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ MAS  │ ✅ │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ KNW  │ ✅ │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ ING  │ ✅ │ ✅ │ ✅ │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ GRP  │ ✅ │ ✅ │ ✅ │ ✅ │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ AIA  │ ✅ │ ✅ │ —  │ ✅ │ ✅ │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │
  │ OPE  │ ✅ │ ✅ │ ✅ │ ✅ │ ✅ │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │
  │ APP  │ ✅ │ —  │ ✅ │ ✅ │ ✅ │ ✅ │ ✅ │ ✅ │ —  │ —  │ —  │ —  │
  │ API  │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ ✅ │ —  │ —  │ —  │
  │ OPS  │ ✅ │ ✅ │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ SHT  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │
  │ SHC  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ —  │ ✅ │
  └──────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘

  缩写对应:
  COM=common        INF=infrastructure  MAS=master-data    KNW=knowledge-system
  ING=ingestion    GRP=graph-core      AIA=ai-analysis    OPE=operations
  APP=application  API=api             OPS=ops
  SHT=shared-types  SHC=shared-components

  ✅ = 允许依赖   — = 禁止依赖
```

---

## 六、组件接口契约总览

| 组件 | 提供的接口 | 定义层 | 实现层 | 消费者 |
|------|----------|:--:|:--:|--------|
| `graphnexus-shared-types` | 枚举定义、DTO 接口、API 路径常量 | L1 | L1 | 所有 L1 前端 |
| `graphnexus-shared-components` | 图谱可视化、图表、报告渲染、通用 UI | L1 | L1 | 所有 L1 前端 |
| `graphnexus-api` | REST 端点 (/admin, /teacher, /student, /ops, /operations) | L2 | L2 | L1 前端 (HTTP) |
| `graphnexus-application` | IAttributionQuery, IClassAnalysis, IVisualization, IReportExport, IPersonalKB, IStudentSelf, IOperationsDashboard | L2 | L2 | `graphnexus-api` |
| `graphnexus-graph-core` | IGraphFusionService, IPruningService, IWeightService | L3 | L3 | `graphnexus-application`, `graphnexus-ai-analysis`, `graphnexus-operations` |
| `graphnexus-ai-analysis` | IContextBuilderService, ILLMGatewayService, IAnalysisGeneratorService | L3 | L3 | `graphnexus-application` |
| `graphnexus-master-data` | IStudentService, ITeacherService, IClassService, IAuthorizationService | L3 | L3 | `graphnexus-application`, `graphnexus-graph-core`, `graphnexus-ingestion`, `graphnexus-operations` |
| `graphnexus-knowledge-system` | IKnowledgeCategoryService, IKnowledgePointService, IPrerequisiteService, IEntityAlignmentService | L3 | L3 | `graphnexus-application`, `graphnexus-graph-core`, `graphnexus-ai-analysis`, `graphnexus-ingestion`, `graphnexus-operations` |
| `graphnexus-ingestion` | IDocumentIngestionService, IEventIngestionService, IAssociationModelService | L3 | L3 | `graphnexus-application`, `graphnexus-graph-core`, `graphnexus-ai-analysis`, `graphnexus-operations` |
| `graphnexus-operations` | IOperationsAnalyticsService | L3 | L3 | `graphnexus-application` |
| `graphnexus-infrastructure` | IGraphRepository → GraphRepositoryImpl, ILLMClient → LLMClientAdapter, IFileStorage → FileStorageAdapter, IMessageQueue → MessageQueueAdapter, ICache → CacheAdapter | — | L4 | L3 各模块 (通过接口) |
| `graphnexus-ops` | ILogService, IBackupService, IScheduledTaskService, INotificationService | 横切 | 横切 | 所有层 (AOP/中间件) |
| `graphnexus-common` | BaseEntity, ValueObject, DomainEvent, 异常体系, 工具类 | 公共 | 公共 | 所有后端模块 |