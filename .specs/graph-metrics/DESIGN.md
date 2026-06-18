# DESIGN: 图谱节点指标度量（PageRank + 度中心性）

- **Change ID**: graph-metrics
- **关联**: `@.specs/graph-metrics/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 2-design 步骤 0 处理。CONTEXT.md 已有「已锁技术决策」覆盖全部关键选型，跳过卡片直接锁定。

- **选定**：既有栈（Java 17 + Spring Boot 3.3.x + Neo4j 5.x + Neo4jClient + Caffeine）
- **后端**：Spring Boot 3.3.x + Spring Data Neo4j 7.x（Neo4jClient 模式，非 SDN Template）
- **图算法**：Neo4j GDS 5.x（通过 Cypher `CALL gds.*.stream` 过程调用）
- **缓存**：Caffeine 3.x（已在 pom.xml）
- **事件机制**：Spring `ApplicationEventPublisher` + `@EventListener`（本次首次引入，见 §1 D4 理由）
- **理由**：所有关键依赖已在项目中，无需新增 Maven 依赖或基础设施变更。GDS 插件已在 podman-compose 声明，Caffeine 可用，Spring 事件是框架内置机制不增加外部依赖
- **明确排除**：不引入 Neo4j GDS Java Client 库（无官方稳定版本，Cypher 过程调用足够）；不使用 Redis 做指标缓存（指标结果非共享数据，本地 Caffeine 足够且延迟更低）

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 出来的实际清单）：
- src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java（既有 · L3 Neo4j 访问）
- src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java（既有 · 节点类型枚举，用于参数校验）
- src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java（既有 · 边类型枚举，用于参数校验）
- src/main/java/com/graphnexus/api/graph/controller/（既有 · 图 API 分组，新增 MetricsController 加入此包）
- src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java（既有 · 融合后需要发布事件）
- src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java（既有 · 抽取后需要发布事件）
- src/main/java/com/graphnexus/application/document/service/impl/GradeServiceImpl.java（既有 · CSV 导入后需要发布事件）
- src/main/java/com/graphnexus/podman-compose.yml（既有 · GDS 插件已声明，无需改动）

新增模块：
- src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java（L3 · GDS 过程调用适配器）
- src/main/java/com/graphnexus/application/graph/metrics/model/MetricResultBO.java（L2 · 指标结果 BO）
- src/main/java/com/graphnexus/application/graph/metrics/model/MetricsQuery.java（L2 · 查询参数对象）
- src/main/java/com/graphnexus/application/graph/metrics/service/MetricsService.java（L2 · 接口）
- src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java（L2 · 实现）
- src/main/java/com/graphnexus/application/graph/metrics/event/GraphChangedEvent.java（L2 · Spring 事件）
- src/main/java/com/graphnexus/application/graph/metrics/event/MetricsCacheInvalidator.java（L2 · 事件监听器）
- src/main/java/com/graphnexus/application/graph/metrics/config/MetricsProperties.java（配置 · yml 绑定）
- src/main/java/com/graphnexus/api/graph/controller/MetricsController.java（L1 · API 端点）
- src/main/java/com/graphnexus/api/graph/dto/MetricResultVO.java（L1 · 响应 VO）
- src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java（L1 · 请求 Query 参数 POJO）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java — 不新增方法，GdsAdapter 独立管理 GDS 调用
- src/main/java/com/graphnexus/application/graph/extraction/ — 与抽取无关
- src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java — 融合逻辑不受影响
- src/main/java/com/graphnexus/application/qa/ — 智能问答不受影响
- pom.xml — 无新增依赖（GDS 走 Cypher，Caffeine 已有，Spring 事件框架内置）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| Neo4j 查询执行 | `GraphNodeRepository` 用 `Neo4jClient` | **沿用模式** — `GdsAdapter` 同样注入 `Neo4jClient`，不走 SDN Template |
| 节点类型枚举 | `NodeType` 枚举 | **沿用** — 参数校验时用 `NodeType.fromLabel()` 验证输入合法性 |
| 边类型枚举 | `EdgeType` 枚举 | **沿用** — 参数校验时用 `EdgeType.fromType()` 验证输入合法性 |
| 本地缓存 | Caffeine 已在 pom.xml | **沿用** — 构造 `com.github.benmanes.caffeine.cache.Cache` bean |
| 统一响应体 | `ApiResponse<T>` record | **沿用** — Controller 返回 `ApiResponse<List<MetricResultVO>>` |
| 配置绑定 | 既有 `@ConfigurationProperties` 模式（FusionProperties 等） | **沿用** — 新增 `MetricsProperties` |
| 事件发布 | 无（首次） | **引入新模式** → 理由：Spring 框架内置，零依赖，标准解耦方式。项目规模已需要（3 个 service 都要通知缓存失效） |
| 构造器注入 | `@RequiredArgsConstructor` + `private final` | **沿用** |

### 0.5.3 沿用模式 vs 引入新模式

```
- Neo4j 访问：**沿用** Neo4jClient + 手动 Cypher 模式（既有 GraphNodeRepository 风格）
- API 响应：**沿用** ApiResponse<T> record 包装
- 配置管理：**沿用** @ConfigurationProperties + yml 绑定
- 包结构：**沿用** 四层架构 L1(api) → L2(application) → L3(infrastructure) + common
- 事件通知：**引入新模式** → Spring ApplicationEventPublisher + @EventListener
  理由：既有代码用直接 try-catch 调用实现触发（如 fusionService.fuseIncremental），
  但指标缓存的失效是典型的"一对多通知"场景（3 个变更源 → 1 个缓存消费者），
  Spring 事件是框架内置的标准解耦方案，不增加外部依赖。
  初次引入，为后续其他场景（如审计日志、埋点）建立可复用模式。
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | GDS 调用方式：Neo4jClient 执行 `CALL gds.*.stream` Cypher 过程 | ① Neo4j GDS Java Client 库 ② Neo4j Java Driver 直接 `session.run()` | 项目全链路统一使用 `Neo4jClient`，GDS 过程本质是 Cypher `CALL` 语句，`Neo4jClient.query()` 完全支持。不走 Driver 裸调保持一致性 | 需要手动拼接 GDS Cypher 参数（nodeProjection/relationshipProjection），但 GDS stream 模式参数简单，可接受 |
| D2 | 图投影策略：transient 命名图（每次计算创建 + 结束后 `gds.graph.drop`） | ① 持久化命名图（`gds.graph.project` 常驻）② 匿名图（`gds.pageRank.stream` 直接传投影参数） | 匿名图最简单但 GDS 5.x 的 stream 模式下每次都要序列化投影参数（冗长）。transient 命名图一次 `project` 后可重复用于多种指标计算（PageRank + degree 共享），结束时显式 drop 避免内存泄漏 | 需要管理命名图生命周期（try-finally drop），增加 GdsAdapter 的复杂度 |
| D3 | 缓存策略：Caffeine 本地缓存，按投影参数 Hash 做 key，图变更时全清 | ① Redis 集中缓存 ② 按变更影响范围精准失效部分 key ③ 不缓存每次实时算 | 指标查询场景单纯（读多写少），本地 Caffeine 延迟最低。全清比精准失效简单且安全——图变更后任何投影参数的结果都可能不同，精准追踪成本高收益低。Caffeine 已在 pom.xml，无需新增依赖 | 图变更后首次查询需等待重算（<2s），后续命中缓存毫秒级。多实例部署时各实例独立缓存会各算一次（可接受，v2 上 Redis 解决） |
| D4 | 事件机制：Spring `ApplicationEventPublisher` + `@EventListener` 通知缓存失效 | ① 直接在既有 try-catch 块中加 `metricsService.clearCache()` ② RabbitMQ 异步消息 | 事件是 Spring 标准解耦方式，无外部依赖。当前 3 个变更源（抽取/CSV导入/融合）→ 1 个消费者（缓存失效），事件比直接调用更清晰。比 RabbitMQ 更轻量，无需额外运维 | 事件在调用线程同步执行（默认 `@EventListener`），缓存清空是 O(1) 操作不阻塞主流程。若未来需要异步（如重算本身耗时），改 `@Async` + `@EnableAsync` 即可 |
| D5 | API 端点放置：`MetricsController` 加入 `api/graph/controller/` 包，路径 `/api/v1/graph/metrics/*` | ① 新建独立 `api/metrics/controller/` ② 放在 `GraphController` 中加方法 | 指标是图领域的子功能，挂在 graph 分组下语义清晰（`/graph/metrics/pagerank`），避免顶层 API 膨胀。不与 GraphController 合并——职责不同（抽取/查询 vs 指标计算），拆开符合单一职责 | 无 |
| D6 | 参数校验：利用 `NodeType.fromLabel()` / `EdgeType.fromType()` 已有枚举方法 | ① 新建独立的校验枚举/类 ② 不做校验直接传 Neo4j（让 GDS 报错） | 复用既有枚举是最小改动。`fromLabel()`/`fromType()` 已存在，校验失败返回 HTTP 400 比让 GDS 抛异常体验更好（GDS 错误信息晦涩） | 新增边类型/节点类型时需同步更新枚举——但这已是项目的既有约束 |
| D7 | 指标触发点位置：在既有 try-catch 融合钩子后发布 `GraphChangedEvent` | ① 改造为 AOP 切面 ② 在 Neo4jClient 层拦截所有写操作 | 不改既有代码结构——仅在 ADR-009 钩子（`GraphServiceImpl.extract()` 末尾、`GradeServiceImpl.uploadGradeCsv()` 末尾、`FusionServiceImpl` 融合方法末尾）的 try-catch 块之后各加 1 行 `eventPublisher.publishEvent(new GraphChangedEvent(this))`。AOP 太重，Neo4jClient 拦截太底层且会误触发索引创建等非业务写操作 | 新增事件源时需记得发布事件——这是约定而非强制，通过 DESIGN 文档传播 |

---

## 2. 数据流 / 架构图

### 2.1 查询流程（读路径）

```
  Client
    │  GET /api/v1/graph/metrics/pagerank?nodeTypes=KnowledgePoint&edgeTypes=PREREQUISITE_OF
    v
  MetricsController (L1)
    │  MetricsQueryRequest 封装参数
    v
  MetricsServiceImpl (L2)
    │
    ├── 1. 校验参数：NodeType.fromLabel() / EdgeType.fromType() → 400 if invalid
    │
    ├── 2. 构建 cacheKey = MetricsQuery.hash()
    │       │
    │       ├── cache hit → 直接返回 List<MetricResultBO>
    │       │
    │       └── cache miss ↓
    │
    ├── 3. 委托 GdsAdapter.calculate() (L3)
    │       │
    │       ├── 3a. 构建命名图投影 Cypher：
    │       │      CALL gds.graph.project(
    │       │        'metrics-temp-{uuid}',
    │       │        [nodeType1, nodeType2, ...],
    │       │        {edgeType1: {orientation: 'NATURAL'}, ...}
    │       │      )
    │       │
    │       ├── 3b. 执行算法 Cypher：
    │       │      CALL gds.pageRank.stream('metrics-temp-{uuid}', {maxIterations: 20, dampingFactor: 0.85})
    │       │      YIELD nodeId, score
    │       │
    │       ├── 3c. 结果映射：nodeId → MetricResultBO(nodeId, nodeType, metricName, score)
    │       │
    │       └── 3d. finally: CALL gds.graph.drop('metrics-temp-{uuid}')  // 释放内存
    │
    ├── 4. 写入缓存（TTL 5min）
    │
    └── 5. 返回 List<MetricResultBO>
         │
         v
  MetricsController → ApiResponse.success(MetricResultVO.from(bo))
```

### 2.2 缓存失效流程（写路径 · 事件驱动）

```
  GraphServiceImpl.extract()          GradeServiceImpl.uploadGradeCsv()       FusionServiceImpl.fuseFull()
       │                                       │                                     │
       ├── fusionService.fuseIncremental()     ├── fusionService.fuseIncremental()   ├── 融合完成（自身）
       │    (已有 · ADR-009)                     │    (已有 · ADR-009)                    │
       │                                       │                                     │
       └── eventPublisher.publishEvent(        └── eventPublisher.publishEvent(      └── eventPublisher.publishEvent(
            new GraphChangedEvent(this))            new GraphChangedEvent(this))           new GraphChangedEvent(this))

                           │                          │                          │
                           └──────────────────────────┼──────────────────────────┘
                                                      │
                                                      v
                                          MetricsCacheInvalidator (L2)
                                          @EventListener
                                                      │
                                                      ├── metricsCache.invalidateAll()
                                                      │    （Caffeine Cache.invalidateAll()，O(1)）
                                                      │
                                                      └── log.debug("Metrics cache cleared due to graph change")
```

### 2.3 GDS 投影方向处理

GDS 投影时边的方向（orientation）需要根据边类型语义决定：

| 边类型 | GDS orientation | 理由 |
|--------|----------------|------|
| PREREQUISITE_OF | NATURAL | 明确有向边：前置 KP → 后置 KP |
| ALIGNED_TO | UNDIRECTED | Entity → KP 对齐，双向语义对等（Entity 和 KP 的重要性应互相传递） |
| BELONGS_TO | NATURAL | KP → Category 归属方向明确 |
| CHILD_OF | NATURAL | 子分类 → 父分类方向明确 |
| TESTED | NATURAL | Exam → KP 考查方向明确 |
| ATTENDED | NATURAL | Student → Exam 参加方向明确 |
| MASTERS | UNDIRECTED | Student → KP 掌握，双向传递（学生掌握的知识点同样定义了知识点的重要性） |
| EXTRACTS | NATURAL | Document → Entity |
| REFERENCES/DERIVES/CONTAINS | NATURAL | 实体间关系 |

**默认行为**：未指定 `edgeTypes` 参数时，全部边参与投影，按上表 orientation。用户可覆盖（v1 不支持，v2 可加入 `orientation` 参数）。

---

## 3. 关键状态机

本次无复杂状态机。GDS 命名图生命周期简单：

```
  [不存在] ──gds.graph.project()──> [ACTIVE] ──gds.*.stream()──> [ACTIVE] ──gds.graph.drop()──> [不存在]
                                       │
                                       └── 异常 → gds.graph.drop()（finally 块确保释放）
```

Cypher 参数：`gds.graph.project('metrics-temp-{randomUUID}', nodeProjection, relationshipProjection)` 确保命名图名称不冲突（并发请求各自独立命名图）。

---

## 4. ADR 索引

| ADR | 标题 | 状态 |
|-----|------|------|
| ADR-013 | [GDS 图指标计算引擎](adr/013-gds-metrics-engine.md) | proposed |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | GDS 插件未安装或版本不兼容 | 所有指标 API 不可用，返回 500 | 低 | podman-compose 已声明 `NEO4J_PLUGINS=["graph-data-science"]`，首次启动会自动下载。GdsAdapter 启动时执行 `CALL gds.version()` 健康检查，失败则日志 FATAL + MetricsService 降级返回明确错误码 |
| R2 | 全图 PageRank 在大数据量下超 2s SLA（10 万+ 节点） | API 超时，用户体验差 | 中 | v1 默认 `maxIterations=20`（GDS 默认），`concurrency=4`。可配置降级：在 `application.yml` 中调小 `maxIterations` 或增大 `concurrency`。未来若数据量增长，v2 引入预处理（定时 Cron 预计算 + 持久化） |
| R3 | 并发请求各自创建命名图，Neo4j 内存不足 | OOM、Neo4j 崩溃 | 低 | 每个命名图用完即 drop（finally 块），内存峰值 = 单个投影大小 × 并发请求数。GDS 命名图支持并发投影，当前 10 万节点规模投影内存约 50-100MB，4 并发可承受。Caffeine 缓存进一步降低并发投影概率 |
| R4 | 子图投影过滤参数错误导致图投影为空 | GDS 报错 | 低 | 投影前先 COUNT 节点数，为 0 直接返回空结果不调 GDS。参数校验（NodeType/EdgeType 枚举）在入口层拦截无效输入 |
| R5 | 缓存全清后高并发查询全部 miss → 同时触发多个 GDS 投影 | 瞬时计算压力 | 中 | Caffeine `LoadingCache` 或 `synchronized` 块确保同 key 仅执行一次计算（`Cache.get(key, k -> compute())`），其他请求阻塞等待首个完成 |
| R6 | Spring 事件引入后，若新增图谱变更源忘记发布事件 | 缓存不失效，指标结果过时 | 中 | 在 DESIGN.md 和 ADR-013 中明确写入"所有图谱写操作必须发布 GraphChangedEvent"的约定。`MetricsServiceImpl` 在 cache miss 时重新计算会自然拿到最新数据（缓存 TTL 5 分钟兜底），最坏情况 = 5 分钟过期窗口 |

---

## 6. 不在范围

- 不提供 `POST /compute` 手动触发端点（自动触发 + 缓存透明刷新已覆盖需求）
- 不做指标历史版本存储和对比
- 不将指标集成到智能问答剪枝逻辑中（这是独立 change）
- 不做前端图可视化
- 不做 GDS 的 `write` 模式（写回节点属性）
- 不做多实例缓存同步（当前单实例部署）
- 不做指标计算的异步任务管理（图变更后只清缓存，真正的计算在用户下次查询时触发）

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `infrastructure/neo4j/gds/GdsAdapter.java` | GDS 过程调用通用适配器——命名图投影 + 算法执行 + 结果映射 | PageRank/度中心性计算 | 后续新增 GDS 算法（介数中心性/社区发现）只需在 GdsAdapter 加方法，投影管理逻辑复用 |
| `application/graph/metrics/event/GraphChangedEvent.java` | 图谱变更通知事件 | 任何修改 Neo4j 图数据的操作完成后 | 后续任何需要感知图变更的消费者（审计/监控/通知）可监听此事件；这是一个项目级事件契约 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| 首次引入 Spring 事件机制 | `ApplicationEventPublisher` + `@EventListener` 用于模块间解耦通知 | 所有 L2 服务层需要广播变更的场景 | 低——Spring 框架内置，切换为直接调用或 MQ 只需改发布/消费两处 |
| GDS 调用模式 | transient 命名图 + Neo4jClient Cypher | 所有需要图算法的功能 | 低——若未来需持久化命名图或换用 `write` 模式，只需改 GdsAdapter 内部实现 |

### 9.3 新增 / 修改的跨模块契约

```
- 新增 Spring 事件：GraphChangedEvent（包：application.graph.metrics.event）
  - 发布方：GraphServiceImpl.extract() / GradeServiceImpl.uploadGradeCsv() / FusionServiceImpl（所有融合方法）
  - 消费方：MetricsCacheInvalidator（@EventListener）
  - 载荷：无字段（仅信号事件），消费方自行决定响应逻辑
- 新增 API 端点组：GET /api/v1/graph/metrics/*
  - GET /api/v1/graph/metrics/pagerank?nodeTypes=...&edgeTypes=...
  - GET /api/v1/graph/metrics/degree?nodeTypes=...&edgeTypes=...
  - 认证：沿用既有 Spring Security JWT 链
  - 响应格式：ApiResponse<List<MetricResultVO>>
- 新增配置绑定：MetricsProperties（前缀：graph.metrics）
  - cache.ttl-minutes（默认 5）
  - page-rank.max-iterations（默认 20）
  - page-rank.damping-factor（默认 0.85）
```

### 9.4 新增 / 升级的依赖

| 包 | 版本 | 用途 | 是否替换既有 |
|---|---|---|---|
| 无 | — | — | 本次零新增依赖（GDS 走 Cypher，事件走 Spring 内置，缓存用已有 Caffeine） |

### 9.5 禁动清单变化

```
- 新增禁动：src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java 禁止在 GDS 调用之外直接操作 Neo4j 数据（GDS 只读，不写）
- 新增禁动：所有 Neo4j 写操作（抽取/CSV导入/融合）完成后必须发布 GraphChangedEvent，不得遗漏
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。
