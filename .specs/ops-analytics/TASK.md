# TASK: 运营管理 — 系统使用量、文档处理量、图谱节点/边分布统计

- **Change ID**: `ops-analytics`
- **关联**: `@.specs/ops-analytics/REQUIREMENT.md`、`@.specs/ops-analytics/DESIGN.md`、`@.specs/ops-analytics/UI-DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel):    T01[P], T02[P], T03[P], T04[P], T05[P]
                      （L3 数据层 + 前端依赖/Token + 前端 API 模块，互不冲突）

Wave 2 (parallel):    T06, T07, T08[P], T09[P], T10[P]
                      （L2 服务层 + 前端 Store + MetricCard 组件，依赖 Wave 1）

Wave 3 (parallel):    T11, T12, T13[P], T14[P], T15[P], T16[P]
                      （L1 控制器 + 审计埋点 + 前端面板组件 + 路由/菜单 + Security + 定时任务配置）

Wave 4:               T17, T18
                      （仪表盘页面组装 + init.sql 同步，依赖 Wave 3）
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

### Wave 1 — 数据层 + 前端基础（5 任务可并行）

```xml
<task id="T01" parallel="true" status="pending">
  <name>L3 数据层：audit_log + stats_snapshot 表 DDL、DO、Repository</name>
  <read_files>
    infrastructure/mysql/auth/entity/UserAccountDO.java
    infrastructure/mysql/query/entity/QueryTaskDO.java
    infrastructure/mysql/file/entity/TextbookDO.java
  </read_files>
  <write_files>
    infrastructure/mysql/ops/entity/AuditLogDO.java
    infrastructure/mysql/ops/entity/StatsSnapshotDO.java
    infrastructure/mysql/ops/repository/AuditLogRepository.java
    infrastructure/mysql/ops/repository/StatsSnapshotRepository.java
    src/main/resources/db/ops/V001__create_audit_log.sql
    src/main/resources/db/ops/V002__create_stats_snapshot.sql
  </write_files>
  <action>
    创建两张新表：
    1. audit_log：id(BIGINT PK), user_id(BIGINT NOT NULL, INDEX), operation_type(VARCHAR(32) NOT NULL),
       resource_id(VARCHAR(128)), create_time(DATETIME NOT NULL, INDEX)。无逻辑删除（日志类表）。
    2. stats_snapshot：id(BIGINT PK), snapshot_date(DATE NOT NULL, UNIQUE),
       snapshot_data(JSON NOT NULL), status(VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'),
       fail_reason(VARCHAR(512)), create_time(DATETIME NOT NULL)。
    遵循项目规范：表名小写蛇形单数、id + create_time + update_time、无外键约束。
    Flyway 迁移文件放 db/ops/，版本号从 V001 开始。
    DO 类遵循既有模式（@Entity + @Table + @Column + Lombok @Data/@NoArgsConstructor）。
    Repository 接口 extends JpaRepository + 必要的方法名派生查询：
    - AuditLogRepository: countByUserIdAndOperationTypeAndCreateTimeBetween(...)
    - StatsSnapshotRepository: findBySnapshotDateBetweenOrderBySnapshotDateAsc(...)
  </action>
  <verify>cd backend && mvn test -pl . -Dtest="AuditLogRepositoryTest,StatsSnapshotRepositoryTest" -Dspring.profiles.active=dev</verify>
  <done>两张表通过 Flyway 创建成功；DO 字段与 DDL 一致；Repository 基本 CRUD 通过集成测试</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>前端依赖：安装 echarts + vue-echarts</name>
  <read_files>
    frontend/package.json
    frontend/vite.config.ts
  </read_files>
  <write_files>
    frontend/package.json
  </write_files>
  <action>
    npm install echarts vue-echarts（版本按 ADR-050：echarts 5.x + vue-echarts 7.x）。
    仅修改 package.json，不新增其他文件。
  </action>
  <verify>cd frontend && npm ls echarts vue-echarts 2>/dev/null | grep -E "echarts@|vue-echarts@"</verify>
  <done>echarts 和 vue-echarts 出现在 package.json dependencies 中，安装成功</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>前端 Token：tokens.css 新增图表色板 + 趋势指示色 + 图表 token</name>
  <read_files>
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/assets/tokens.css
  </write_files>
  <action>
    在 tokens.css 的 :root 中追加三组新变量（按 UI-DESIGN §3）：
    1. 图表色板 6 色：--chart-c0 ~ --chart-c5，值见 UI-DESIGN §3 图表色板表
    2. 趋势指示 3 色：--color-trend-up / --color-trend-down / --color-trend-flat
    3. 图表容器 token：--chart-axis-color: oklch(0.88 0.005 95)（ECharts 轴线色）
       和 --chart-tooltip-bg: oklch(1 0 0)（tooltip 背景）
    不修改既有变量。
  </action>
  <verify>grep -E "^  --chart-c[0-5]:" frontend/src/assets/tokens.css && grep -E "^  --color-trend-(up|down|flat):" frontend/src/assets/tokens.css && grep "^  --chart-axis-color:" frontend/src/assets/tokens.css</verify>
  <done>tokens.css 新增 11 个 CSS 变量，格式和位置与既有 token 一致</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>前端 API 模块：ops.ts — 运营统计 API 封装</name>
  <read_files>
    frontend/src/api/client.ts
    frontend/src/api/types.ts
    frontend/src/api/system.ts
  </read_files>
  <write_files>
    frontend/src/api/ops.ts
  </write_files>
  <action>
    创建 ops.ts，封装三个 API 调用（沿用 client.ts 的 axios 实例）：
    - getSummary(): GET /api/v1/ops/stats/summary → OpsSummaryResponse
    - getTrend(metric, granularity, range): GET /api/v1/ops/stats/trend?metric=&granularity=&range= → OpsTrendResponse
    - getSubjects(): GET /api/v1/ops/stats/subjects → string[]
    定义响应类型（OpsSummaryResponse / OpsTrendResponse）在本文件内，不放入 types.ts（保持 ops 类型内聚）。
  </action>
  <verify>cd frontend && npx tsc --noEmit src/api/ops.ts 2>&1 | grep -v "node_modules" | wc -l | xargs -I{} sh -c 'test {} -eq 0 && echo PASSED || echo FAILED'</verify>
  <done>ops.ts 导出三个方法，TypeScript 编译通过；请求路径、参数签名与 DESIGN §4 契约一致</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>ECharts 主题初始化：chartTheme.ts — 色板注册 + 默认样式</name>
  <read_files>
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/common/components/chartTheme.ts
  </write_files>
  <action>
    创建 ECharts 主题配置文件 chartTheme.ts：
    1. 从 CSS 变量读取 6 色图表色板（getComputedStyle 或硬编码同一 OKLCH 值——推荐硬编码确保一致性）
    2. 定义默认 ECharts option 覆盖：
       - grid: 去 splitArea，Y 轴虚线（--chart-axis-color），X 轴无线
       - tooltip: 白底 + 1px border + rounded-sm，无透明玻璃效果
       - legend: 底部居中，文字色 = text-secondary
       - animation: 入场 300ms ease-out-quint，更新时 duration: 0
    3. 导出函数 applyChartTheme(): void（用 echarts.registerTheme 或全局 setOption 默认值）
    4. 导出 CHART_COLORS 数组供组件直接引用
  </action>
  <verify>cd frontend && npx tsc --noEmit src/common/components/chartTheme.ts 2>&1 | grep -v "node_modules" | wc -l | xargs -I{} sh -c 'test {} -eq 0 && echo PASSED || echo FAILED'</verify>
  <done>chartTheme.ts 导出 CHART_COLORS 6 色数组和 applyChartTheme()；色值与 UI-DESIGN §3 一致</done>
  <depends_on></depends_on>
</task>
```

---

### Wave 2 — 服务层 + 前端组件基础（5 任务，T06/T07 顺序，其余并行）

```xml
<task id="T06" parallel="false" status="pending">
  <name>L2 服务层：AuditLogService — 异步操作审计日志写入</name>
  <read_files>
    infrastructure/mysql/ops/entity/AuditLogDO.java
    infrastructure/mysql/ops/repository/AuditLogRepository.java
    application/query/chat/config/AsyncConfig.java
    application/auth/service/AuthService.java
  </read_files>
  <write_files>
    application/ops/audit/service/AuditLogService.java
    application/ops/audit/service/impl/AuditLogServiceImpl.java
    application/ops/audit/model/OperationType.java
  </write_files>
  <action>
    创建审计日志 L2 组件（按 DESIGN §2.1 + ADR-051）：
    1. OperationType 枚举：LOGIN / DOCUMENT_UPLOAD / DOCUMENT_PROCESS / QA_ASK
    2. AuditLogService 接口：void record(Long userId, OperationType type, String resourceId)
    3. AuditLogServiceImpl 实现：
       - @Async("queryAsyncExecutor") 复用既有线程池
       - @Transactional(propagation = REQUIRES_NEW) 独立短事务
       - 方法内部 try-catch → 失败写 WARN 日志，不抛异常（AC-8）
       - 构造 AuditLogDO 并 save()
  </action>
  <verify>cd backend && mvn compile -pl . -q 2>&1 | tail -5 | grep -q "BUILD SUCCESS" && echo PASSED || echo FAILED</verify>
  <done>AuditLogService 编译通过；@Async 注解生效（Spring 代理检查）；OperationType 枚举含 4 个值</done>
  <depends_on>T01</depends_on>
</task>

<task id="T07" parallel="false" status="pending">
  <name>L2 服务层：OpsStatsService — 运营统计聚合查询 + SnapshotService — 快照采集</name>
  <read_files>
    infrastructure/mysql/ops/repository/AuditLogRepository.java
    infrastructure/mysql/ops/repository/StatsSnapshotRepository.java
    infrastructure/mysql/file/repository/TextbookRepository.java
    infrastructure/mysql/query/repository/QueryTaskRepository.java
    infrastructure/neo4j/repository/QueryGraphRepository.java
    application/graph/metrics/service/impl/MetricsServiceImpl.java
  </read_files>
  <write_files>
    application/ops/stats/service/OpsStatsService.java
    application/ops/stats/service/impl/OpsStatsServiceImpl.java
    application/ops/stats/model/OpsSummaryResponse.java
    application/ops/stats/model/OpsTrendResponse.java
    application/ops/snapshot/service/SnapshotService.java
    application/ops/snapshot/service/impl/SnapshotServiceImpl.java
    application/ops/snapshot/model/SnapshotData.java
  </write_files>
  <action>
    创建统计聚合 + 快照 L2 组件（按 DESIGN §2.2 + §2.3 + ADR-052）：

    OpsStatsService:
    - getSummary(): 实时查询 —— AuditLogRepo（今日活跃用户数/操作次数）+ TextbookRepo（文档总数/按状态分布）
      + QueryTaskRepo（问答次数）+ Neo4j（节点/边按类型 COUNT + 按学科 COUNT）
    - getTrend(metric, granularity, range): 查 StatsSnapshotRepo.findByDateRange()，
      解析 snapshot_data JSON → 按 granularity 聚合 → 返回时间序列
    - getSubjects(): 从 Neo4j QueryGraphRepository 获取学科列表

    SnapshotService:
    - takeDailySnapshot(): @Scheduled 方法（cron 表达式通过 yml 配置，默认 "0 0 2 * * ?"）
      ——采集全部指标（usage/documents/graph）→ 构造成 SnapshotData BO → Jackson 序列化为 JSON
      → 写入 StatsSnapshotDO。各数据源查询独立 try-catch，单源失败不阻塞其他源（PARTIAL 模式）。
    - buildSnapshotData(): 私有方法，组装 SnapshotData（结构见 ADR-052 JSON Schema）

    SnapshotData BO：POJO 对应 ADR-052 JSON Schema 三层嵌套结构。
  </action>
  <verify>cd backend && mvn compile -pl . -q 2>&1 | tail -5 | grep -q "BUILD SUCCESS" && echo PASSED || echo FAILED</verify>
  <done>OpsStatsService + SnapshotService 编译通过；SnapshotData 序列化/反序列化结构匹配 ADR-052 JSON Schema</done>
  <depends_on>T01</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>前端 Store：opsStore.ts — 运营仪表盘 Pinia 状态管理</name>
  <read_files>
    frontend/src/stores/systemStore.ts
    frontend/src/api/ops.ts
  </read_files>
  <write_files>
    frontend/src/stores/opsStore.ts
  </write_files>
  <action>
    创建 opsStore（Pinia defineStore），参考 systemStore 的模式：
    - state: summary(对象|null), trend(对象|null), granularity('month'|'week'|'day'), subject('全部学科'), loading, error
    - actions: fetchSummary(), fetchTrend(metric), setGranularity(g), setSubject(s)
    - 数据缓存：summary 缓存 60s，避免频繁切换粒度时重复请求
    - error 处理：请求失败设置 error 字段，不抛异常（组件读取 error 展示降级提示）
  </action>
  <verify>cd frontend && npx tsc --noEmit src/stores/opsStore.ts 2>&1 | grep -v "node_modules" | wc -l | xargs -I{} sh -c 'test {} -eq 0 && echo PASSED || echo FAILED'</verify>
  <done>opsStore 导出 useOpsStore；fetchSummary/fetchTrend 签名与 ops.ts API 模块匹配</done>
  <depends_on>T04</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>前端组件：MetricCard.vue — 通用数字统计卡片</name>
  <read_files>
    frontend/src/common/components/BaseCard.vue
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/ops/components/MetricCard.vue
  </write_files>
  <action>
    按 UI-DESIGN §6 MetricCard 规约实现：
    - Props: label(string), value(number|string), trend('up'|'down'|'flat'|null), trendValue(string|number), loading(boolean)
    - 布局：上 label(micro-label + text-tertiary) → 中大数字(display 缩小 + DM Sans 700) → 下趋势行(supporting)
    - 趋势图标：lucide TrendingUp(绿色)/TrendingDown(红色)/Minus(灰色)
    - at rest：白色 surface + 1px border + rounded-lg(8px) + padding 24px，无阴影
    - 无 hover 效果（只读卡片）
    - loading 态：Naive UI NSkeleton（neutral 色调），非纯灰色
    - 无 trend 数据时不渲染趋势行（不显示虚假箭头）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/ops/components/MetricCard.vue 2>&1 | grep -c "error TS" | xargs -I{} sh -c 'test {} -eq 0 && echo PASSED || echo "Type errors found"'</verify>
  <done>MetricCard 组件渲染正确（props 四种趋势态 + loading 态 + 无趋势态）</done>
  <depends_on>T03</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>前端组件：ECharts 图表包装器 — 通用图表容器卡片</name>
  <read_files>
    frontend/src/common/components/BaseCard.vue
    frontend/src/common/components/chartTheme.ts
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/ops/components/OpsChart.vue
  </write_files>
  <action>
    创建 OpsChart.vue 通用图表包装组件（按 UI-DESIGN §6 ECharts 容器规约）：
    - Props: option(EChartsOption), title(string), loading(boolean), height(string, default '320px')
    - 卡片外观：BaseCard 模式（白色 + 1px border + rounded-lg + padding 24px）
    - 标题：.title 层级，左对齐，上方
    - 图表区域：vue-echarts VChart 组件，应用 chartTheme 默认配置，height 可配
    - loading 态：NSkeleton 占位图表区域（320px 高）
    - 空数据：图表区域居中显示 .supporting + text-tertiary 的"暂无数据"
    - resize：监听 window resize → chart.resize()（防抖 200ms）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/ops/components/OpsChart.vue 2>&1 | grep -c "error TS" | xargs -I{} sh -c 'test {} -eq 0 && echo PASSED || echo "Type errors found"'</verify>
  <done>OpsChart 组件正确渲染 ECharts 图表（含标题、loading、空数据三种状态）</done>
  <depends_on>T05</depends_on>
</task>
```

---

### Wave 3 — 控制器 + 埋点 + 前端面板 + 路由/安全/定时配置（6 任务可并行）

```xml
<task id="T11" parallel="true" status="pending">
  <name>L1 控制器：OpsStatsController — 运营统计 API 端点</name>
  <read_files>
    api/system/controller/SystemHealthController.java
    api/graph/controller/MetricsController.java
  </read_files>
  <write_files>
    api/ops/controller/OpsStatsController.java
    api/ops/dto/OpsSummaryResponse.java
    api/ops/dto/OpsTrendResponse.java
    api/ops/dto/OpsTrendRequest.java
  </write_files>
  <action>
    创建 OpsStatsController（L1，按 DESIGN §2.3 + §4 契约）：
    - 类级别 @PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER')") + @RequestMapping("/api/v1/ops/stats")
    - GET /summary → @GetMapping("/summary") → OpsStatsService.getSummary()
    - GET /trend → @GetMapping("/trend") + @Valid OpsTrendRequest（metric, granularity, range）
      → OpsStatsService.getTrend()
    - GET /subjects → @GetMapping("/subjects") → OpsStatsService.getSubjects()
    - DTO 遵循项目命名规范：请求 = XxxRequest，响应 = XxxResponse
    - 构造器注入 OpsStatsService（private final + @RequiredArgsConstructor）
    - 返回 ApiResult<XxxResponse> 包装
  </action>
  <verify>cd backend && mvn compile -pl . -q 2>&1 | tail -5 | grep -q "BUILD SUCCESS" && echo PASSED || echo FAILED</verify>
  <done>Controller 编译通过；三个端点注解正确（路径/参数/权限）；返回类型均为 ApiResult 包装</done>
  <depends_on>T06, T07</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>审计埋点：在现有 Service 中接入 AuditLogService 调用</name>
  <read_files>
    application/auth/service/impl/AuthServiceImpl.java
    application/file/textbook/service/TextbookServiceImpl.java
    application/query/chat/service/impl/QueryServiceImpl.java
    application/ops/audit/service/AuditLogService.java
  </read_files>
  <write_files>
    application/auth/service/impl/AuthServiceImpl.java
    application/file/textbook/service/TextbookServiceImpl.java
    application/query/chat/service/impl/QueryServiceImpl.java
  </write_files>
  <action>
    在三个现有 Service 中各插入一行审计日志调用（DESIGN §2.1 + D4 决策 — 显式调用）：

    1. AuthServiceImpl.login() 成功后：auditLogService.record(userId, OperationType.LOGIN, null)
    2. TextbookServiceImpl.upload() 成功后：auditLogService.record(userId, OperationType.DOCUMENT_UPLOAD, documentId)
       同时 process() 触发后：auditLogService.record(userId, OperationType.DOCUMENT_PROCESS, documentId)
    3. QueryServiceImpl.ask()（同步）LLM 调用完成后：auditLogService.record(userId, OperationType.QA_ASK, taskId)
       异步路径 askAsync() 在 QueryServiceImpl 内部同样加

    注意：
    - @Async 调用在事务外（主方法不加 @Transactional），遵循既有事务边界策略
    - 仅新增注入 auditLogService 字段 + 一行 record() 调用，不修改任何业务逻辑
  </action>
  <verify>cd backend && mvn compile -pl . -q 2>&1 | tail -5 | grep -q "BUILD SUCCESS" && echo PASSED || echo FAILED</verify>
  <done>三个 Service 编译通过；auditLogService.record() 调用位置正确（登录后/上传后/问答后）</done>
  <depends_on>T06</depends_on>
</task>

<task id="T13" parallel="true" status="pending">
  <name>后端：SchedulingConfig + @Scheduled 快照定时任务配置</name>
  <read_files>
    application/query/chat/config/AsyncConfig.java
    application/ops/snapshot/service/SnapshotService.java
  </read_files>
  <write_files>
    application/ops/snapshot/config/SchedulingConfig.java
  </write_files>
  <action>
    创建 SchedulingConfig 配置类（按 ADR-049）：
    - @Configuration + @EnableScheduling
    - 可选：配置 scheduled 线程池（独立于 async 线程池，避免阻塞审计日志写入），
      默认 Spring 单线程足够（仅一个定时任务）
    - 快照 cron 表达式通过 yml 外部化：
      ops.snapshot.cron=0 0 2 * * ?（在 application.yml 新增配置项）
    创建 SnapshotScheduler（或在 SnapshotServiceImpl 上加 @Scheduled）：
    - @Scheduled(cron = "${ops.snapshot.cron}") 调用 SnapshotService.takeDailySnapshot()
  </action>
  <verify>cd backend && mvn compile -pl . -q 2>&1 | tail -5 | grep -q "BUILD SUCCESS" && echo PASSED || echo FAILED</verify>
  <done>SchedulingConfig 编译通过；@EnableScheduling 生效；cron 表达式从 yml 读取</done>
  <depends_on>T07</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>后端：SecurityConfig 新增 /api/v1/ops/** 权限规则</name>
  <read_files>
    common/config/SecurityConfig.java
  </read_files>
  <write_files>
    common/config/SecurityConfig.java
  </write_files>
  <action>
    在 SecurityConfig.securityFilterChain() 的 authorizeHttpRequests 链中新增一行：
    .requestMatchers("/api/v1/ops/**").hasAnyRole("ADMIN", "OPS_MANAGER")
    位置：放在 "/api/v1/system/**" 规则后、"/api/v1/**" 规则前。
    不修改其他规则。
  </action>
  <verify>grep "api/v1/ops" src/main/java/com/graphnexus/common/config/SecurityConfig.java && echo PASSED || echo FAILED</verify>
  <done>SecurityConfig 包含 /api/v1/ops/** → ADMIN + OPS_MANAGER 的规则</done>
  <depends_on></depends_on>
</task>

<task id="T15" parallel="true" status="pending">
  <name>前端面板：UsageStatsPanel + DocumentStatsPanel + GraphStatsPanel</name>
  <read_files>
    frontend/src/views/ops/components/MetricCard.vue
    frontend/src/views/ops/components/OpsChart.vue
    frontend/src/stores/opsStore.ts
    frontend/src/common/components/BaseCard.vue
  </read_files>
  <write_files>
    frontend/src/views/ops/components/UsageStatsPanel.vue
    frontend/src/views/ops/components/DocumentStatsPanel.vue
    frontend/src/views/ops/components/GraphStatsPanel.vue
  </write_files>
  <action>
    实现三个面板子组件（按 UI-DESIGN §6 + REQUIREMENT AC-3/4/5）：

    UsageStatsPanel.vue：
    - 4 个 MetricCard（活跃用户数/登录次数/文档操作次数/问答次数）
    - 1 个 OpsChart 饼图（操作类型分布：LOGIN/DOCUMENT_UPLOAD/DOCUMENT_PROCESS/QA_ASK）
    - 数据从 opsStore.summary.usage 读取

    DocumentStatsPanel.vue：
    - 1 个 MetricCard（文档总量）
    - 1 个 OpsChart 柱状图（按处理状态分布：8 状态）
    - 1 个 OpsChart 饼图（按学科分布）
    - 1 个 OpsChart 折线图（上传趋势）
    - 数据从 opsStore.summary.documents 读取
    - 折线图数据从 opsStore.trend('document_upload') 读取

    GraphStatsPanel.vue：
    - 学科选择器：Naive UI NSelect，options 从 opsStore.subjects 读取，默认"全部学科"
    - 1 个 OpsChart 柱状图（按节点类型分布：Entity/KP/Category/Subject/Student/Exam）
    - 1 个 OpsChart 柱状图（按边类型分布：ALIGNED_TO/PREREQUISITE_OF/TESTED/MASTERS/等）
    - 学科切换后联动更新两个图表数据（opsStore.setSubject() + fetchSummary()）
    - 数据从 opsStore.summary.graph 读取

    三个面板均处理 loading / error / 空数据状态。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/ops/components/UsageStatsPanel.vue src/views/ops/components/DocumentStatsPanel.vue src/views/ops/components/GraphStatsPanel.vue 2>&1 | grep -c "error TS" | xargs -I{} sh -c 'test {} -eq 0 && echo PASSED || echo "Type errors found"'</verify>
  <done>三个面板组件渲染正确（loading/数据/空/error 四种状态均覆盖）</done>
  <depends_on>T08, T09, T10</depends_on>
</task>

<task id="T16" parallel="true" status="pending">
  <name>前端：Router 路由 + AppLayout 导航菜单</name>
  <read_files>
    frontend/src/router/index.ts
    frontend/src/common/components/AppLayout.vue
  </read_files>
  <write_files>
    frontend/src/router/index.ts
    frontend/src/common/components/AppLayout.vue
  </write_files>
  <action>
    1. router/index.ts：新增 /ops 路由
       { path: '/ops', name: 'ops', component: () => import('@/views/ops/OpsDashboardPage.vue'),
         meta: { roles: ['ADMIN', 'OPS_MANAGER'] } }
    2. AppLayout.vue：
       - import { BarChart3 } from '@lucide/vue'
       - navItems 数组加入：
         { path: '/ops', label: '运营管理', icon: BarChart3, roles: ['ADMIN', 'OPS_MANAGER'] }
       位置：在学情诊断之后（作为最后一个主菜单项）
  </action>
  <verify>grep "'/ops'" frontend/src/router/index.ts && grep "'运营管理'" frontend/src/common/components/AppLayout.vue && grep "BarChart3" frontend/src/common/components/AppLayout.vue && echo PASSED || echo FAILED</verify>
  <done>ADMIN/OPS_MANAGER 登录后导航栏出现"运营管理"菜单；TEACHER 登录后不可见；路由 meta.roles 正确</done>
  <depends_on></depends_on>
</task>
```

---

### Wave 4 — 页面组装 + SQL 同步（2 任务）

```xml
<task id="T17" parallel="false" status="pending">
  <name>前端页面：OpsDashboardPage.vue — 运营仪表盘页面组装</name>
  <read_files>
    frontend/src/views/ops/components/UsageStatsPanel.vue
    frontend/src/views/ops/components/DocumentStatsPanel.vue
    frontend/src/views/ops/components/GraphStatsPanel.vue
    frontend/src/views/ops/components/MetricCard.vue
    frontend/src/stores/opsStore.ts
    frontend/src/views/system/SystemHealthPage.vue
  </read_files>
  <write_files>
    frontend/src/views/ops/OpsDashboardPage.vue
  </write_files>
  <action>
    按 UI-DESIGN v0 布局 + §6 规约组装仪表盘页面：
    - 顶部栏：页面标题 h2.headline "运营管理" + 右侧时间粒度切换
      NButtonGroup（天/周/月）+ 最后更新时间戳（supporting + text-tertiary）
    - 三大区域纵向排列，区域间间距 48px（--spacing-2xl）：
      ① 系统使用量（UsageStatsPanel）
      ② 文档处理量（DocumentStatsPanel）
      ③ 图谱分布（GraphStatsPanel）
    - 历史趋势区：2 个 OpsChart 折线图（活跃用户数趋势 + 文档处理量趋势），
      标注 Tag "历史趋势"，基于快照数据
    - 实时区域标注 Tag "当前"（微标签区分，按 UI-DESIGN §6）
    - 粒度切换：NButtonGroup v-model:value 绑定 opsStore.granularity，
      切换时 opsStore.fetchSummary() + opsTrend 重新加载
    - onMounted: opsStore.fetchSummary() + opsStore.fetchTrend()
    - 页面级 error：Naive UI NAlert type="error" 展示
    - 页面级 loading：NSkeleton 整体占位

    参考 SystemHealthPage.vue 的页面结构模式（script setup + template 分区）。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/ops/OpsDashboardPage.vue 2>&1 | grep -c "error TS" | xargs -I{} sh -c 'test {} -eq 0 && echo PASSED || echo "Type errors found"'</verify>
  <done>OpsDashboardPage 渲染完整仪表盘（三区域 + 粒度切换 + loading/error/数据三种状态）</done>
  <depends_on>T15</depends_on>
</task>

<task id="T18" parallel="false" status="pending">
  <name>SQL 同步：init.sql 追加 ops 表 DDL</name>
  <read_files>
    src/main/resources/db/ops/V001__create_audit_log.sql
    src/main/resources/db/ops/V002__create_stats_snapshot.sql
    src/main/resources/db/init.sql
  </read_files>
  <write_files>
    src/main/resources/db/init.sql
  </write_files>
  <action>
    将 db/ops/ 下两个 Flyway 迁移文件的 DDL 追加到 init.sql 末尾。
    保持 init.sql 现有的注释分隔和格式风格。
  </action>
  <verify>grep "audit_log" src/main/resources/db/init.sql && grep "stats_snapshot" src/main/resources/db/init.sql && echo PASSED || echo FAILED</verify>
  <done>init.sql 包含 audit_log 和 stats_snapshot 两张表的完整 DDL</done>
  <depends_on>T01</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中（同时只允许一个非 [P] 任务为此状态）
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞（必须在文件末尾「阻塞日志」记录）

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```