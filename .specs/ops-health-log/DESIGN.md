# DESIGN — 系统运维功能（健康检测 + 日志查看）

- **Change ID**: `ops-health-log`
- **关联**: `@.specs/ops-health-log/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 项目 CONTEXT.md 已有完整「已锁技术决策」，本 change 全部沿用，不引入新技术栈。

- **后端**：Spring Boot 3.3.x + Java 17（已锁定）
- **健康检查**：Spring Boot Actuator（已在 pom.xml，`management.*` 已在 application.yml 配置）
- **日志持久化**：Logback `RollingFileAppender`（已在 logback-spring.xml 配置）
- **安全**：Spring Security 6.x + JWT（已锁定）
- **前端**：Vue 3 + TypeScript + Vite + Naive UI（已锁定）
- **关键依赖**：`spring-boot-starter-actuator`（已有）/ `io.minio:minio`（已有，MinioClient Bean）/ `spring-boot-starter-data-redis`（已有，RedisConnectionFactory Bean）
- **明确排除**：不引入 Micrometer Prometheus registry（虽然 application.yml 已配置 prometheus 端点，若缺少依赖会导致该端点不可用，本次补充该依赖但不对接 Prometheus 采集）

---

## 0.5 既有架构对齐（brownfield 必填）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（修改 · grep 出来的实际清单）：
- src/main/java/com/graphnexus/common/config/SecurityConfig.java（既有 · 修改 /actuator/** 权限 + 新增 /api/v1/system/** 规则）
- src/main/resources/application.yml（既有 · management.health.db.enabled: false → true）
- frontend/src/common/components/AppLayout.vue（既有 · 新增「系统运维」菜单组 + navItems 追加两项）
- frontend/src/router/index.ts（既有 · 新增 /system/health + /system/logs 路由）

新增模块：
- src/main/java/com/graphnexus/api/system/（新 L1 模块 · controller + dto）
- src/main/java/com/graphnexus/application/system/（新 L2 模块 · service + health）
- frontend/src/api/system.ts（新 API 模块）
- frontend/src/views/system/SystemHealthPage.vue（新页面）
- frontend/src/views/system/SystemLogPage.vue（新页面）
- frontend/src/stores/systemStore.ts（新 Pinia store）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- pom.xml（actuator 已在，无需改依赖）
- src/main/resources/logback-spring.xml（文件持久化已就绪，无需改）
- 所有既有业务模块（document / graph / fusion / query / analysis / auth / config）
- frontend/src/stores/authStore.ts（hasRole 已就绪，无需改）
- frontend/src/router/authGuard.ts（meta.roles 守卫已就绪，无需改）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 构造器注入 + Lombok | `@RequiredArgsConstructor` + `private final`（全项目规范） | 沿用 |
| REST API 响应格式 | `common/ApiResult.java` 统一响应体 | 沿用 |
| 分页响应格式 | `common/PageResult.java`（写操作不需要，日志内容分页用自定义 VO） | 不适用（日志分页非传统分页查询，用 LogContentVO） |
| 全局异常处理 | `common/exception/GlobalExceptionHandler.java` | 沿用 |
| 业务异常 | `common/exception/BusinessException.java` | 沿用 |
| 日志文件读取 | 既无 LogService 抽象 | **新建**（理由：项目首次出现文件系统读取需求） |
| 健康检查聚合 | Actuator `HealthIndicator` 接口（Spring Boot 内置） | 沿用 Actuator 的 HealthIndicator 契约，新增自定义实现 |
| JWT 认证 | `JwtAuthenticationFilter` + `SecurityContextHolder` | 沿用 |
| 角色权限校验 | `@PreAuthorize("hasAnyRole(...)")` + `SecurityFilterChain` 规则 | 沿用 |
| 前端 Pinia store | `stores/authStore.ts`（`hasRole()` 方法已存在） | 沿用，新增 `systemStore.ts` 按 Pinia Options API 范式 |
| 前端菜单角色过滤 | `AppLayout.vue` 的 `navItems.filter(i => i.roles.some(...))` | 沿用，追加运维菜单项 |
| 前端路由守卫 | `authGuard.ts` 的 `to.meta.roles` 检查 | 沿用，新路由设 `meta: { roles: [...] }` |
| 前端 API 模块 | `api/*.ts` 的 axios 封装模式 | 沿用，新增 `api/system.ts` |
| Naive UI 组件库 | 已在项目中大量使用 | 沿用（n-card / n-progress / n-tag / n-list / n-pagination / n-button） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：本 change 无数据库访问，不引入 Repository 模式
- 错误处理：**沿用** 既有 GlobalExceptionHandler + BusinessException + ErrorCode 枚举
- API 路由组织：**沿用** 既有 api/<domain>/controller/ 风格（api/system/controller/）
- 认证授权：**沿用** 既有 SecurityFilterChain + @PreAuthorize + JWT 过滤器链
- 前端状态管理：**沿用** Pinia Options API 风格（写 systemStore.ts）
- 前端组件：**沿用** Naive UI 组件库 + 极简调性
- 日志文件读取：**引入新模式**（项目首次文件系统读取需求）→ 理由：既无 LogService 抽象，且与既有 DB/Neo4j/Redis/MinIO 访问模式完全不同
- 健康检查聚合：**沿用** Actuator HealthIndicator 接口（Spring Boot 标准扩展点）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | 健康数据通过**后端聚合 API** 返回（`GET /api/v1/system/health`），前端不直接调 `/actuator` | 前端直接调 `/actuator/health` + `/actuator/metrics/*`（多请求） | 单端点一次返回所有数据，前端逻辑简单；Actuator 数据格式不直接暴露给前端；统一 API 路径便于权限管控（一条 SecurityFilterChain 规则覆盖） | 后端多一次内部调用（`HealthIndicator` bean 方法调用 + `MetricsEndpoint` bean 方法调用，非 HTTP，延迟可忽略） |
| D2 | 自定义 HealthIndicator 实现 `HealthIndicator` 接口（非继承 `AbstractHealthIndicator`） | 继承 `AbstractHealthIndicator` | 项目其他代码偏好接口实现模式；`HealthIndicator` 是纯函数式接口，逻辑简单不需要 `AbstractHealthIndicator` 的模板方法 | 需手动 try-catch 异常并转为 `Health.down()`（约 5 行模板代码，可接受） |
| D3 | 日志文件读取使用 **`Files.lines()` 流式两遍扫描**（一遍 `.count()` 求总行数 + 一遍 `.skip().limit()` 取当前页） | ① `Files.readAllLines()` 全量加载（OOM 风险）；② `RandomAccessFile` 随机访问（实现复杂） | 流式读取 O(1) 内存，10MB 日志文件两遍扫描 <200ms；无需引入行索引缓存 | 每页请求读两次文件（总行数 + 内容），文件巨大（>100MB）时变慢；v2 可用行偏移缓存或 RandomAccessFile 优化 |
| D4 | 日志文件名安全校验：拒绝含 `..`、`/`、`\` 的文件名 + 校验 canonical path 前缀等于 `logs/` 目录 | 仅拒绝 `..` | 双重防护防路径遍历：先做字符级黑名单，再做 OS 级路径解析验证 | 合法文件名不能含路径分隔符（日志文件名本来就不含，无实际代价） |
| D5 | 前端健康面板 **30s 自动轮询** `GET /api/v1/system/health` | ① WebSocket 推送；② 手动刷新（无自动） | 30s 轮询对运维场景足够及时；实现简单无需引入 WebSocket；页面隐藏时 `onUnmounted` 清除定时器 | 30s 内组件状态变化不可见（非实时监控场景，可接受）；每次轮询执行 4 个 HealthIndicator + 4 个 metrics 查询 |
| D6 | SecurityFilterChain 中 `/api/v1/system/**` **放在 `/api/v1/**` 之前**声明 | 用 `@PreAuthorize` 在 Controller 类级别控制 | Spring Security 规则按声明顺序匹配，更具体的规则必须在前；类级别 `@PreAuthorize` 作为第二层防御 | 需注意顺序，误放后面会被 `/api/v1/**` 吞掉（已在此显式标注） |
| D7 | Actuator `MetricsEndpoint` 通过 **直接注入 Bean** 调用获取 JVM 指标（非 HTTP 调用 `/actuator/metrics`） | 用 `RestTemplate`/`WebClient` 调 `http://localhost:8080/actuator/metrics/jvm.memory.used` | 进程内调用零网络延迟；不依赖 HTTP 线程池；不受 SecurityFilterChain 影响 | 耦合 Actuator 内部 API（`MetricsEndpoint.metric(name, tags)` 返回 `MetricsResponse`），Spring Boot 3.x 公开 API 稳定 |
| D8 | 前端日志内容使用 `<pre>` 标签 + monospace 字体渲染（不做语法高亮） | ① 逐行 `<div>` + 正则高亮（如 ERROR 红色）；② 虚拟滚动（vue-virtual-scroller） | 简单可靠，200 行/页 `<pre>` 渲染性能足够；高亮属于 v2 增强 | 无语法高亮（运维人员习惯看纯文本日志，影响小）；超大文件翻到数千页后 DOM 节点 200 行仍可接受 |
| D9 | 新增 Micrometer Prometheus registry 依赖（`micrometer-registry-prometheus`）使 application.yml 中已配置的 prometheus 端点可用；但**不在本次对接任何外部采集器** | 删除 yml 中 prometheus 端点配置 | 补齐依赖使配置一致（yml 已声明 prometheus 端点但缺依赖会导致该端点 404），降低后续对接门槛；不加依赖则需改 yml | 增加一个传递依赖（micrometer-registry-prometheus，约 500KB），对应用启动无影响 |

---

## 2. 数据流 / 架构图

### 2.1 健康检测数据流

```
  SystemHealthPage.vue                 SystemHealthController           SystemHealthService
  ──────────────────                  ──────────────────────           ────────────────────
  │                   │  GET /api/v1/  │                     │         │                    │
  │  30s setInterval ─┼──────────────>│  system/health      │         │                    │
  │                   │               │                     │         │                    │
  │                   │               │  handleHealth() ────┼────────>│ getSystemHealth()  │
  │                   │               │                     │         │        │           │
  │                   │               │                     │         │        │ forEach   │
  │                   │               │                     │         │   HealthIndicator  │
  │                   │               │                     │         │   .health()        │
  │                   │               │                     │         │        │           │
  │                   │               │                     │         │        │ 调用      │
  │                   │               │                     │         │   MetricsEndpoint  │
  │                   │               │                     │         │   .metric(...)     │
  │                   │               │                     │         │        │           │
  │                   │               │                     │         │ 组装 SystemHealthVO │
  │                   │  SystemHealthVO (JSON)               │<────────┼────────────────────│
  │  <────────────────┼───────────────│                     │         │                    │
  │                   │               │                     │         │                    │
  │  渲染四组件卡片    │               │                     │         │                    │
  │  + JVM 仪表盘      │               │                     │         │                    │
  └───────────────────┘               └─────────────────────┘         └────────────────────┘

  HealthIndicator Bean（Spring 自动聚合到 /actuator/health，但不直接暴露给前端）
  ────────────────────────────────────────────────────────────────
  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ DataSource│  │ Neo4j    │  │ Redis    │  │ MinIO    │
  │ Health   │  │ Health   │  │ Health   │  │ Health   │
  │ Indicator│  │ Indicator│  │ Indicator│  │ Indicator│
  │ (内置)   │  │ (内置)   │  │ (新增)   │  │ (新增)   │
  └──────────┘  └──────────┘  └──────────┘  └──────────┘
```

### 2.2 日志查看数据流

```
  SystemLogPage.vue                     LogController                      LogService
  ─────────────────                     ──────────────                     ──────────
  │                   │  GET /api/v1/    │                  │              │          │
  │  页面加载          │  system/logs     │                  │              │          │
  │  ────────────────>│─────────────────>│  listFiles() ────┼─────────────>│ scan     │
  │                   │                  │                  │              │ logs/    │
  │                   │  LogFileVO[]     │                  │              │ 目录     │
  │  <────────────────│<─────────────────│                  │              │          │
  │                   │                  │                  │              │          │
  │  渲染文件列表      │                  │                  │              │          │
  │  点击文件          │  GET /api/v1/    │                  │              │          │
  │  ────────────────>│  system/logs/    │                  │              │          │
  │                   │  {file}?page=1   │                  │              │          │
  │                   │  &size=200        │                  │              │          │
  │                   │─────────────────>│  readContent() ──┼─────────────>│ 路径校验  │
  │                   │                  │                  │              │ + 流式    │
  │                   │                  │                  │              │ 读取      │
  │                   │  LogContentVO    │                  │              │          │
  │  <────────────────│<─────────────────│                  │              │          │
  │                   │                  │                  │              │          │
  │  渲染分页日志      │                  │                  │              │          │
  │  点击下载          │  GET /api/v1/    │                  │              │          │
  │  ────────────────>│  system/logs/    │                  │              │          │
  │                   │  {file}/download  │                  │              │          │
  │                   │─────────────────>│  download() ─────┼─────────────>│ 路径校验  │
  │                   │                  │                  │              │ + 流式    │
  │                   │  StreamingResponseBody              │              │ 传输      │
  │  <────────────────│<─────────────────│                  │              │          │
  └───────────────────┘                  └──────────────────┘              └──────────┘
```

### 2.3 安全过滤链

```
  HTTP Request
       │
       v
  JwtAuthenticationFilter (OncePerRequestFilter)
       │  提取 Header Authorization: Bearer <token>
       │  验证签名 + 有效期 → 构建 Authentication → SecurityContext
       v
  SecurityFilterChain.authorizeHttpRequests (按声明顺序匹配):
       │
       ├── /api/v1/auth/login, /api/v1/auth/refresh  → permitAll
       ├── /actuator/**                                → hasAnyRole(ADMIN, OPS_MANAGER, OPS_STAFF)
       ├── /api/v1/system/**                           → hasAnyRole(ADMIN, OPS_MANAGER, OPS_STAFF)  ← 新增
       ├── /v3/api-docs/**, /swagger-ui/**             → permitAll
       ├── /api/v1/**                                  → authenticated  ← 运维规则必须在这行之前
       └── anyRequest()                                → permitAll
```

---

## 3. 关键状态机

本 change 无状态机。健康检查为实时查询，日志查看为只读操作，不涉及状态流转。

---

## 4. ADR 索引

| ADR | 标题 | 说明 |
|-----|------|------|
| ADR-047 | [Actuator 安全策略](adr/ADR-047-actuator-security-policy.md) | `/actuator/**` 从 permitAll 收敛为运维角色独占；前端通过聚合 API 间接访问 |
| ADR-048 | [日志 API 设计与路径安全](adr/ADR-048-log-api-path-security.md) | 日志文件列表/分页内容/下载三个端点 + 路径遍历双重防护 |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **实现风险**：Redis/MinIO 容器不可用时 HealthIndicator 抛异常导致整个 `/actuator/health` 响应异常 | 健康面板不可用 | 高（测试环境容器可能频繁重启） | `try-catch` 包裹每个 HealthIndicator 调用，异常返回 `Health.down().withDetail("error", e.getMessage())`；Actuator 框架本身已隔离各 Indicator 异常 |
| R2 | **实现风险**：日志文件被 Logback 正在写入时读取导致行不完整 | 日志页面显示截断行 | 中 | `Files.lines()` 按 `\n` 分割，正在写入的末行可能不完整但不会导致异常；极端情况下最后一页少一行（可接受） |
| R3 | **上线风险**：`management.endpoint.health.show-details=always` 暴露组件详情（IP/端口/异常堆栈）给运维角色 | 内部信息泄露 | 中 | 运维角色本身就是授权查看系统状态的角色；HealthIndicator 的 `withDetail` 仅写组件名 + 延迟 + 简化错误消息，不写 IP/密码/完整堆栈 |
| R4 | **长期债务**：Actuator MetricsEndpoint 直接注入依赖 Spring Boot Actuator 内部 API，Spring Boot 大版本升级时 API 可能变更 | 编译失败 | 低 | `MetricsEndpoint.metric()` 是 Actuator 公开 API（非 internal 包），Spring Boot 3.x → 4.x 的迁移风险可控；集成测试覆盖 |
| R5 | **长期债务**：日志文件两遍扫描（count + read）在文件超过 100MB 后响应变慢 | 日志页面加载慢 | 低（v1 日志上限 10MB，30 天滚动） | 日志滚动策略限制单文件 ≤10MB；v2 可引入行偏移缓存或改用 `ReversedLinesFileReader` |
| R6 | **安全风险**：若忘记将 `/api/v1/system/**` 规则放在 `/api/v1/**` 之前，运维端点对所有认证用户开放 | TEACHER/STUDENT 可访问运维 API | 中 | D6 已在 DESIGN 中显式标注顺序要求；TASK 拆解时单独任务验证顺序；集成测试覆盖 AC-6 |

---

## 6. 不在范围

- 日志文件的**实时 tail**（WebSocket 推送新行）— v2 候选
- 日志内容**语法高亮**（ERROR 红色 / WARN 黄色）— v2 候选
- 健康指标**历史趋势图**（近 1h/24h 折线）— v2 候选
- 健康状态异常时**主动告警**（邮件/钉钉）— out（独立运维平台职责）
- 分页大小**用户自定义**（当前固定 200 行/页）— v2 候选
- **多实例聚合**健康检查（仅检查当前实例连接的基础设施）
- `/actuator/loggers` 运行时日志级别修改 — out（安全风险）

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|------|------|---------|---------|
| `application/system/service/LogService.java` | 日志文件列表 + 分页读取 + 安全下载 | 任何需要读取服务器本地日志文件的功能 | 后续若需要读取其他目录文件（如导出文件下载），复用 LogService 的路径安全检查逻辑 |
| `application/system/health/RedisHealthIndicator.java` | Redis 连通性 + 延迟检测 | 任何需要检查 Redis 健康的场景 | 由 Actuator 自动聚合，后续若引入其他 Redis 实例可参考此实现 |
| `application/system/health/MinIOHealthIndicator.java` | MinIO bucket 可达性检测 | 任何需要检查 MinIO 健康的场景 | 同上，Actuator 自动聚合 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|------|------|---------|---------|
| Actuator 端点安全策略 | 从 permitAll 收敛为运维角色独占 | 所有 `/actuator/**` 端点访问 | 低——若后续需要放开给监控系统，在 SecurityFilterChain 中改回 permitAll 或加 IP 白名单即可 |
| 运维 API 命名空间 | `/api/v1/system/**` | 所有运维功能 API | 低——后续运维功能（如系统备份/任务调度）统一放此路径下 |

### 9.3 新增 / 修改的跨模块契约

```
- 新增 GET /api/v1/system/health → SystemHealthVO（组件状态数组 + JVM 指标对象）
- 新增 GET /api/v1/system/logs → LogFileVO[]（文件名/大小/修改时间）
- 新增 GET /api/v1/system/logs/{filename}?page=&size= → LogContentVO（分页行列表 + 分页元数据）
- 新增 GET /api/v1/system/logs/{filename}/download → StreamingResponseBody（application/octet-stream）
- 修改 SecurityFilterChain：/actuator/** 从 permitAll() → hasAnyRole(ADMIN,OPS_MANAGER,OPS_STAFF)
- 修改 SecurityFilterChain：新增 /api/v1/system/** → hasAnyRole(ADMIN,OPS_MANAGER,OPS_STAFF)
- 修改 application.yml：management.health.db.enabled: false → true
```

### 9.4 新增 / 升级的依赖

| 包 | 版本 | 用途 | 是否替换既有 |
|---|---|---|---|
| `micrometer-registry-prometheus` | 跟随 Spring Boot 3.3.x BOM | 使 `/actuator/prometheus` 端点可用（application.yml 已配置） | 否（新增，非替换） |

### 9.5 禁动清单变化

```
- 新增禁动：无（本 change 无新增禁动路径）
- 解禁：无
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。