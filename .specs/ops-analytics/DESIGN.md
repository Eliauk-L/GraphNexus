# DESIGN: 运营管理 — 系统使用量、文档处理量、图谱节点/边分布统计

- **Change ID**: `ops-analytics`
- **关联**: `@.specs/ops-analytics/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 核心技术栈已在 CONTEXT.md 锁定（Java 17 + Spring Boot 3.3.x + Vue 3 + Vite + Naive UI）。本次仅新增两个关键依赖。

- **选定**: 项目既有栈 + Spring `@Scheduled`（定时任务）+ Apache ECharts（图表库）
- **前端**: Vue 3.4+ / TypeScript 5 / Vite（沿用）
- **后端**: Spring Boot 3.3.x / Java 17（沿用）
- **数据库**: MySQL 8.0 + Neo4j 5.26（沿用）
- **新增依赖**:
  - `spring-boot-starter`（已含 `@Scheduled` 支持，无需额外依赖）
  - `echarts` 5.x + `vue-echarts` 7.x（前端图表库）
- **理由**: Spring `@Scheduled` 零新依赖即可满足每日快照采集需求；ECharts 社区最成熟 + Vue 3 集成完善（`vue-echarts`），覆盖饼图/柱状图/折线图全部需求，与现有 Naive UI 不冲突
- **明确排除**:
  - Quartz / XXL-JOB（单机单任务场景过度工程，引入新依赖和运维复杂度）
  - Chart.js（图表类型和交互能力弱于 ECharts，不适合运营仪表盘）
  - @antv/g2（项目已有 G6，但 G2 社区生态和文档远不如 ECharts，且图表库与图可视化的捆绑无实际收益）

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（后端）：
- common/config/SecurityConfig.java（新增 /api/v1/ops/** 规则）
- common/exception/GlobalExceptionHandler.java（运营模块异常由既有处理器统一处理，不改动）
- common/event/（复用 ApplicationEventPublisher，不新增事件类型）
- application/query/chat/config/AsyncConfig.java（复用既有 queryAsyncExecutor 线程池写审计日志）
- infrastructure/mysql/auth/entity/UserAccountDO.java（审计日志引用 userId，不修改 DO）

触碰模块（前端）：
- frontend/src/common/components/AppLayout.vue（navItems 新增「运营管理」菜单项）
- frontend/src/router/index.ts（新增 /ops 路由）
- frontend/src/stores/authStore.ts（复用 hasRole 方法控制菜单可见性，不修改 store）

新增模块（后端）：
- api/ops/controller/OpsStatsController.java（L1 · 运营统计 API）
- api/ops/dto/（请求/响应 DTO）
- application/ops/stats/service/OpsStatsService.java（L2 · 统计聚合服务接口）
- application/ops/stats/service/impl/OpsStatsServiceImpl.java（L2 · 实现）
- application/ops/audit/service/AuditLogService.java（L2 · 审计日志写入接口）
- application/ops/audit/service/impl/AuditLogServiceImpl.java（L2 · 异步写入实现）
- application/ops/snapshot/service/SnapshotService.java（L2 · 快照采集接口）
- application/ops/snapshot/service/impl/SnapshotServiceImpl.java（L2 · 快照采集 + @Scheduled 触发）
- application/ops/snapshot/config/SchedulingConfig.java（定时任务线程池配置）
- infrastructure/mysql/ops/entity/AuditLogDO.java（L3 · audit_log 表 DO）
- infrastructure/mysql/ops/entity/StatsSnapshotDO.java（L3 · stats_snapshot 表 DO）
- infrastructure/mysql/ops/repository/AuditLogRepository.java（L3）
- infrastructure/mysql/ops/repository/StatsSnapshotRepository.java（L3）

新增模块（前端）：
- frontend/src/views/ops/OpsDashboardPage.vue（运营仪表盘页面）
- frontend/src/views/ops/components/UsageStatsPanel.vue（系统使用量子组件）
- frontend/src/views/ops/components/DocumentStatsPanel.vue（文档处理量子组件）
- frontend/src/views/ops/components/GraphStatsPanel.vue（图谱分布子组件）
- frontend/src/views/ops/components/MetricCard.vue（通用数字卡片子组件）
- frontend/src/api/ops.ts（运营统计 API 模块）
- frontend/src/stores/opsStore.ts（运营仪表盘 Pinia 状态管理）

禁动清单（AI 不允许触碰）：
- pom.xml（无新 Maven 依赖）
- application/file/textbook/（文档处理链路不改变）
- application/query/（问答链路不改变，仅审计日志旁路记录）
- application/graph/construction/（图谱构建链路不改变）
- infrastructure/neo4j/（Neo4j 仅做 COUNT 查询，不修改节点/边模型）
- frontend/src/common/components/AppLayout.vue（仅新增一个菜单项，不改布局逻辑）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 异步非阻塞写入 | `@Async("queryAsyncExecutor")` 已在 TextbookParsedEventListener 等使用 | **沿用** queryAsyncExecutor 线程池，审计日志写入复用同一线程池 |
| 事件驱动 | `ApplicationEventPublisher` + `@EventListener` 模式 | **沿用** 但不新增事件类型——审计日志由 Service 直接调用，不走事件总线（AC-8 要求不阻塞主流程，最简单方式是主流程调用 AuditLogService 的 @Async 方法） |
| 角色权限控制 | `@PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER')")` | **沿用** Controller 类级别注解 |
| 安全过滤器链 | `SecurityConfig.securityFilterChain` 的 `requestMatchers` 链 | **沿用** 新增 `/api/v1/ops/**` 规则 |
| HTTP 客户端（前端） | `frontend/src/api/client.ts`（axios 实例） | **沿用** |
| 状态管理（前端） | Pinia（authStore / configStore / systemStore） | **沿用** 新建 opsStore |
| 图表渲染（前端） | 无（仅 AntV G6 用于图可视化，无统计图表库） | **引入新模式** → ECharts（理由：项目首次需要统计图表，既有 G6 不适合饼图/柱状图/折线图） |
| 定时任务（后端） | 无 | **引入新模式** → Spring `@Scheduled`（理由：项目首次需要定时任务，零新依赖） |
| REST API 风格 | 小写下划线分隔，单数资源名 | **沿用** `/api/v1/ops/stats/summary` 等 |
| 分层异常传递 | L3→BusinessException→L2 记日志→L1 GlobalExceptionHandler | **沿用** |
| 构造器注入 | `private final` + `@RequiredArgsConstructor` | **沿用** |
| 审计日志写入 | 无（项目首次需要操作审计） | **引入新模式** → `AuditLogService.record(Async)` 非阻塞写入（理由：项目首次需要操作审计，独立 service 封装） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问（MySQL）：**沿用** Spring Data JPA Repository 模式（JpaRepository + 方法名派生）
- 数据访问（Neo4j）：**沿用** Neo4jClient + 手动 Cypher（与既有 QueryGraphRepository 一致）
- 错误处理：**沿用** BusinessException + GlobalExceptionHandler
- API 路由组织（后端）：**沿用** api/<domain>/controller/ 风格
- API 路由组织（前端）：**沿用** frontend/src/api/<domain>.ts + client.ts axios 实例
- 前端页面组织：**沿用** frontend/src/views/<domain>/ 目录结构
- 前端组件库：**沿用** Naive UI（卡片用 NCard、选择器用 NSelect、日期粒度切换用 NButtonGroup）
- 事务管理：**沿用** 短事务立即提交（审计日志和快照写入均为独立短事务）
- 定时任务调度：**引入新模式** → Spring @Scheduled（理由：项目首次需要定时任务，选 Spring 内置方案零新依赖）
- 统计图表渲染：**引入新模式** → ECharts + vue-echarts（理由：项目首次需要饼图/柱状图/折线图，既有 G6 不适用）
- 审计日志写入：**引入新模式** → @Async 非阻塞写入（理由：项目首次需要操作审计，但 @Async 机制本身已存在，新模式是指"Service 层直接 @Async 调用"而非走事件总线）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | 定时任务框架：Spring `@Scheduled` | Quartz / XXL-JOB | 零新依赖；单机单任务（每日凌晨一次全量快照采集）不需要分布式调度或任务持久化；Spring Boot 原生支持，`@EnableScheduling` + cron 表达式即可 | 无任务失败重试机制（Quartz 有）；无任务执行历史 Dashboard（XXL-JOB 有）；若未来需要多实例部署且任务不可重复执行，需迁移到 Quartz |
| D2 | 前端图表库：Apache ECharts 5.x + vue-echarts 7.x | @antv/g2 / Chart.js | ECharts 社区最成熟（GitHub 60k+ stars），文档完善；vue-echarts 封装成熟，与 Vue 3 Composition API 兼容；内置 pie/bar/line 全部所需类型，交互（tooltip/zoom/legend）开箱即用；数据更新通过 `setOption` 响应式驱动 | 包体积较大（~1MB gzip ~300KB），按需引入可减小；与 G6 不同厂牌，可视化风格需手动统一配色 |
| D3 | 审计日志写入策略：`@Async` + 独立短事务 | 事件总线 / AOP 切面 | 与项目既有 `@Async("queryAsyncExecutor")` 模式一致（`AsyncConfig` 已存在）；`@Async` 失败仅记 WARN 日志不抛异常，天然满足 AC-8「不阻塞主流程」；独立短事务保证单条日志写入原子性，失败不回滚主业务 | 审计日志写入与主流程无事务关联，极端情况下（进程 crash）可能丢失 crash 前最后一条日志；不保证写入顺序（@Async 并发执行） |
| D4 | 审计日志记录方式：各业务 Service 显式调用 `auditLogService.record()` | Spring AOP 切面（`@Auditable` 注解自动拦截） | 显式调用更可控（可传业务上下文如 documentId/taskId）；不引入 AOP 复杂度和隐式行为；与项目既有风格一致（项目未使用 AOP） | 每个需要审计的 Service 方法需加一行调用，侵入式；若未来审计范围扩大，逐个加调用成本高 |
| D5 | 图谱统计查询方式：Cypher COUNT 直查 | GDS `gds.graph.list()` 统计 / APOC 过程 | 查询类型/按学科的节点数用 `MATCH (n:Label) RETURN count(n)` 是 Neo4j 最基础操作；按学科过滤走既有 BELONGS_TO_SUBJECT 边，与 CONS-001「Subject 节点化」一致；不引入新插件依赖 | 全图 COUNT 在大图下（>100 万节点）可能较慢，需索引优化；后续大规模时可加 Caffeine 缓存（复用 graph-metrics 的缓存模式） |
| D6 | 快照存储粒度：每日一条聚合记录（JSON 字段存明细） | 每指标一行（EAV 模型） / 按小时快照 | 每日一条与 AC-9 一致；JSON 字段（`snapshot_data`）存各维度的完整数据，灵活扩展新指标无需改表结构；MySQL JSON 类型支持索引和部分更新 | JSON 字段不便直接 SQL 聚合跨天数据（如"最近 30 天登录总次数"），需在 Java 层解析汇总；查询单指标跨天趋势需反序列化所有天数据 |
| D7 | 前端时间粒度切换方式：Naive UI `NButtonGroup` 三个切换按钮（天/周/月） | NSelect 下拉 / NTab 标签页 | 三种粒度用按钮组最直观，与极简设计语言一致（参考 Linear 的时间范围切换器）；按钮组比下拉少一次点击 | 若未来粒度增加（如季/年），按钮组会变长——但 v1 仅三种，够用 |

---

## 2. 数据流 / 架构图

### 2.1 审计日志写入流（实时）

```
用户登录 ──> AuthController.login()
                  │
                  v
            AuthServiceImpl.login()
                  │
          ┌───────┴───────┐
          v               v
      正常返回Token    auditLogService.record(userId, "LOGIN")
                          │
                          v
                    @Async("queryAsyncExecutor")
                          │
                          v
                    AuditLogDO.save()  ──> MySQL audit_log 表
                    （失败 → WARN 日志，不影响登录）
```

```
用户上传文档 ──> FileController.upload()
                      │
                      v
                TextbookServiceImpl.upload()
                      │
          ┌───────────┴───────────┐
          v                       v
      正常上传返回           auditLogService.record(userId, "DOCUMENT_UPLOAD", documentId)
```

```
用户发起问答 ──> QueryController.ask()
                      │
                      v
                QueryServiceImpl.ask()
                      │
          ┌───────────┴───────────┐
          v                       v
      LLM调用+返回结果      auditLogService.record(userId, "QA_ASK", taskId)
```

### 2.2 统计快照采集流（定时）

```
每日凌晨 2:00（cron: 0 0 2 * * ?）
        │
        v
SnapshotService.takeDailySnapshot()  [@Scheduled]
        │
        ├──> AuditLogRepository.countByTypeAndDateRange(...)  ──> MySQL
        ├──> TextbookRepository.countByStatusAndSubject(...)  ──> MySQL
        ├──> QueryTaskRepository.countByDateRange(...)        ──> MySQL
        ├──> Neo4j: MATCH (n) RETURN labels(n)[0], count(n)   ──> Neo4j
        ├──> Neo4j: MATCH ()-[r]->() RETURN type(r), count(r)  ──> Neo4j
        └──> Neo4j: 按 Subject 分别 COUNT 节点和边            ──> Neo4j
        │
        v
StatsSnapshotDO（snapshot_date + snapshot_data JSON）
        │
        v
MySQL stats_snapshot 表
```

### 2.3 运营仪表盘请求流（实时）

```
用户访问 /ops
        │
        v
OpsDashboardPage.vue（onMounted）
        │
        ├──> opsStore.fetchSummary()         ──> GET /api/v1/ops/stats/summary
        │       │
        │       v
        │   OpsStatsServiceImpl.getSummary()
        │       │
        │       ├──> AuditLogRepository（实时：今日活跃用户数、今日操作次数）
        │       ├──> TextbookRepository（实时：文档总数、按状态分布）
        │       ├──> Neo4j（实时：节点/边类型计数）
        │       └──> 返回 OpsSummaryResponse
        │
        └──> opsStore.fetchTrend(metric, granularity, range)
                │
                v
            GET /api/v1/ops/stats/trend?metric=XXX&granularity=day&range=30
                │
                v
            OpsStatsServiceImpl.getTrend()
                │
                └──> StatsSnapshotRepository.findByDateRange()
                        │
                        v
                    解析 snapshot_data JSON → 返回时间序列
```

### 2.4 模块依赖图（UML 风格）

```
┌──────────────────────────────────────────────────────┐
│  L1 api/ops/                                         │
│  OpsStatsController                                  │
│  @PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER')")   │
└──────────┬───────────────────────────────────────────┘
           │ depends on
           v
┌──────────────────────────────────────────────────────┐
│  L2 application/ops/                                 │
│  ┌─────────────────┐  ┌──────────────────────────┐   │
│  │ OpsStatsService  │  │ SnapshotService           │   │
│  │ + getSummary()   │  │ + takeDailySnapshot()     │   │
│  │ + getTrend()     │  │   [@Scheduled]            │   │
│  └───────┬─────────┘  └──────────┬───────────────┘   │
│          │                       │                    │
│  ┌───────┴───────────────────────┴───────────────┐   │
│  │ AuditLogService                                │   │
│  │ + record(userId, type, resourceId?)  [@Async]  │   │
│  └────────────────────────────────────────────────┘   │
└──────────┬───────────────────────────────────────────┘
           │ depends on
           v
┌──────────────────────────────────────────────────────┐
│  L3 infrastructure/                                  │
│  ┌────────────────────┐  ┌────────────────────────┐  │
│  │ mysql/ops/          │  │ neo4j/repository/       │  │
│  │ AuditLogDO          │  │ QueryGraphRepository    │  │
│  │ StatsSnapshotDO     │  │ (复用，加 COUNT 查询)    │  │
│  │ AuditLogRepository  │  │                         │  │
│  │ StatsSnapshotRepo   │  │                         │  │
│  └────────────────────┘  └────────────────────────┘  │
│  ┌────────────────────┐                              │
│  │ mysql/file/         │  ┌────────────────────────┐  │
│  │ TextbookRepository  │  │ mysql/query/            │  │
│  │ (复用 count 查询)    │  │ QueryTaskRepository     │  │
│  └────────────────────┘  │ (复用 count 查询)        │  │
│                          └────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

---

## 3. 关键状态机

### 3.1 审计日志生命周期

```
[业务操作发生]
      │
      v
auditLogService.record() 被调用
      │
      v
@Async 线程池执行
      │
      ├── 成功 ──> AuditLogDO 持久化 ──> [终态]
      │
      └── 失败 ──> 捕获异常 → log.warn("审计日志写入失败: userId={}, type={}", ...)
                    ──> [丢弃，不影响主流程]
```

- 无重试机制（AC-8 明确要求不阻塞主流程，重试会增加延迟）
- 无终态确认（异步 fire-and-forget）

### 3.2 统计快照状态

```
[凌晨 2:00 cron 触发]
      │
      v
takeDailySnapshot()
      │
      ├── 全部数据源查询成功 ──> StatsSnapshotDO（status=COMPLETED）──> [终态]
      │
      ├── 部分数据源查询失败 ──> StatsSnapshotDO（status=PARTIAL + fail_reason）
      │                         ──> ERROR 日志（含失败的特定数据源）
      │                         ──> [终态：成功的指标仍可查询]
      │
      └── 全部数据源查询失败 ──> StatsSnapshotDO（status=FAILED + fail_reason）
                                ──> ERROR 日志
                                ──> [终态：该日无快照数据]
```

- 无自动重试（每天一次，失败等次日自动重跑）
- 无手动补采端点（v2 考虑）

---

## 4. ADR 索引

| ADR | 标题 | 文件 |
|-----|------|------|
| ADR-049 | 定时任务框架选型：Spring @Scheduled | `@.specs/adr/ADR-049-scheduled-task-spring.md` |
| ADR-050 | 运营仪表盘图表库选型：Apache ECharts | `@.specs/adr/ADR-050-ops-charts-echarts.md` |
| ADR-051 | 审计日志异步非阻塞写入策略 | `@.specs/adr/ADR-051-audit-log-async-write.md` |
| ADR-052 | 统计快照 JSON 聚合存储模型 | `@.specs/adr/ADR-052-stats-snapshot-json-model.md` |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **Neo4j 全图 COUNT 慢查询**：图谱变大（>10 万节点）后 `COUNT(n)` 无索引扫描可能 > 2s，超出 AC 性能要求 | 仪表盘加载超时，用户体验差 | 中 | 短期：在 GdsAdapter 中用 `gds.graph.list()` 或 APOC `apoc.meta.stats()` 统计（O(1) 元数据查询，不走全图扫描）；长期：Caffeine 缓存 + GraphChangedEvent 失效 |
| R2 | **`@Scheduled` 单机锁缺失**：未来多实例部署时，定时任务会在每个实例各执行一次，产生重复快照 | 数据重复，浪费存储 | 低（v1 单实例） | 短期：设计快照采集的幂等性（按 snapshot_date 唯一约束，重复插入被忽略）；长期：引入 ShedLock 或迁移到 Quartz |
| R3 | **审计日志表膨胀**：每天 N 次操作 × 长期运行，`audit_log` 表无界增长 | MySQL 表空间占满，查询变慢 | 高 | 设计阶段明确：`create_time` 加 BTREE 索引以加速范围查询；v1 不做自动清理，运维文档注明定期归档策略（如保留 6 个月，定时 DELETE 旧数据） |
| R4 | **ECharts 包体积影响首屏**：ECharts 完整包 ~1MB（gzip ~300KB），可能拖慢仪表盘首屏加载 | 首屏 LCP 超 3s | 低 | 按需引入（仅 pie/bar/line 三种图表类型）；ECharts 5 支持 tree-shaking；Vite code-splitting 将图表代码独立 chunk，不影响其他页面 |
| R5 | **`@Async` 线程池耗尽**：高峰时段大量审计日志并发写入，queryAsyncExecutor 线程池达到 maxPoolSize + queueCapacity 上限 | 审计日志写入被 CallerRunsPolicy 回退到调用方线程执行，轻微拖慢主流程 | 低 | 复用既有 thread pool（core=2, max=4, queue=100），审计日志写入是极短操作（单条 INSERT <5ms），Pool 耗尽概率极低；另可新增独立 opsAsyncExecutor 线程池隔离 |
| R6 | **快照采集耗时长**：凌晨 2:00 快照采集需跨 MySQL + Neo4j 多次查询，可能 > 30s | 采集期间占用数据库连接，低峰期影响小 | 低 | 采集在凌晨低峰期执行；各查询独立超时控制（JDBC `queryTimeout` + Neo4j `transactionTimeout`）；单查询失败不影响其他指标采集（PARTIAL 模式） |

---

## 6. 不在范围

- **手动快照补采 API**：快照缺失某天后，v1 不支持管理员手动触发补采，等次日自动重跑。v2 考虑 `POST /api/v1/ops/stats/snapshot/backfill?date=2026-06-22`
- **审计日志归档/清理策略**：v1 不设计自动清理定时任务，运维文档标注手动清理命令。v2 引入自动归档
- **统计 API 限流**：v1 运营仪表盘仅 ADMIN/OPS_MANAGER 使用（≤ 5 人），不做 API 限流。v2 若开放给更多角色需加
- **图表导出为图片**：仪表盘图表不提供"导出为 PNG/SVG"按钮，v2 考虑
- **个性化仪表盘布局**：用户不可拖拽/自定义卡片位置，v1 固定三区纵向布局
- **多实例部署的快照去重**：v1 假设单实例部署，快照不做分布式锁。CONTEXT.md 默认行为中"本地开发环境 podman 容器化"表明单机部署模式

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/ops/audit/service/AuditLogService.java` | 通用异步操作审计日志写入接口 | 任何需要记录"谁在什么时候做了什么"的场景 | 后续任何业务模块需要操作审计时，直接注入此 Service 调用 `record()`，无需各自实现 |
| `application/ops/snapshot/service/SnapshotService.java` | 统计快照采集与查询接口 | 任何需要"定时采集指标快照 + 历史趋势查询"的统计场景 | 后续新增统计指标时扩展 `takeDailySnapshot()` 方法，复用既有定时调度和存储 |
| `frontend/src/views/ops/components/MetricCard.vue` | 通用数字统计卡片组件（标题 + 大数字 + 变化趋势箭头） | 任何 Dashboard 类页面的数字指标展示 | 后续如果新增其他仪表盘（如文档处理 Dashboard），直接用此组件 |
| `frontend/src/stores/opsStore.ts` | 运营仪表盘 Pinia store（Summary + Trend 加载 + 粒度切换 + 缓存策略） | 后续任何统计看板的状态管理 | 模式可复制（fetchSummary → fetchTrend → granularity reactive），后续统计类 store 可参考 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| 定时任务框架 | Spring `@Scheduled`（首次引入） | 所有需要定时执行的业务逻辑 | 低：迁移到 Quartz 只需改注解 + 加 Quartz 依赖，业务逻辑不变 |
| 前端统计图表库 | Apache ECharts 5.x + vue-echarts 7.x（首次引入） | 所有需要统计图表的前端页面 | 中：已渲染的图表组件需重写为新的图表库 API |
| 审计日志写入模式 | `@Async` + 独立短事务，fire-and-forget（首次引入此模式用于业务审计） | 所有需要操作审计的业务模块 | 低：改为事件总线或 AOP 只需修改 AuditLogService 内部实现，调用方不变 |
| 统计快照存储模式 | MySQL JSON 字段存储聚合快照（首次引入此模式） | 所有需要历史统计趋势的查询 | 中：若改为 EAV 模型需迁移 JSON → 行数据 |

### 9.3 新增 / 修改的跨模块契约

```
后端 API：
- 新增 GET /api/v1/ops/stats/summary（运营仪表盘摘要数据）
- 新增 GET /api/v1/ops/stats/trend?metric=&granularity=&range=（历史趋势时间序列）
- 新增 GET /api/v1/ops/stats/subjects（学科列表，用于图表学科筛选器）

安全规则：
- SecurityConfig 新增 .requestMatchers("/api/v1/ops/**").hasAnyRole("ADMIN", "OPS_MANAGER")

前端路由：
- 新增 /ops → OpsDashboardPage.vue（meta: { roles: ['ADMIN', 'OPS_MANAGER'] }）

前端导航：
- AppLayout.vue navItems 新增 { path: '/ops', label: '运营管理', icon: BarChart3, roles: ['ADMIN', 'OPS_MANAGER'] }
```

### 9.4 新增的依赖

| 包 | 版本 | 用途 | 是否替换既有 |
|---|---|---|---|
| `echarts` | 5.x | 前端统计图表渲染引擎 | 否（新增能力） |
| `vue-echarts` | 7.x | ECharts 的 Vue 3 封装组件 | 否（新增能力） |
| （无后端新增依赖） | — | Spring `@Scheduled` 由 `spring-boot-starter` 自带，`@EnableAsync`/`ThreadPoolTaskExecutor` 已存在 | — |

### 9.5 禁动清单变化

```
新增禁动：
- pom.xml（无新 Maven 依赖，不修改）
- application/file/textbook/parser/（文档解析链路不改变）
- application/graph/construction/extract/（图谱抽取链路不改变）
- application/query/chat/（问答链路不改变，仅旁路加审计日志调用）
- frontend/src/common/components/AppLayout.vue（仅加一个 menu item，不改布局逻辑）
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。