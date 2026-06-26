# REQUIREMENT — 系统运维功能（健康检测 + 日志查看）

- **Change ID**: `ops-health-log`
- **关联**: `@.specs/ops-health-log/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为运维人员（ADMIN / OPS_MANAGER / OPS_STAFF），我想在管理后台查看 MySQL、Neo4j、Redis、MinIO 各组件的连通状态和响应延迟，以便快速判断系统基础设施是否正常。
- **US-2**：作为运维人员，我想查看 JVM 运行时指标（堆内存使用量/最大堆内存/CPU 使用率/活跃线程数），以便评估应用服务器资源是否充足。
- **US-3**：作为运维人员，我想浏览 `logs/` 目录下所有日志文件的列表（文件名/大小/修改时间），以便快速定位需要查看的日志。
- **US-4**：作为运维人员，我想分页浏览日志文件的内容，以便在不下载文件的情况下快速排查问题。
- **US-5**：作为运维人员，我想下载日志文件到本地，以便离线分析或归档。
- **US-6**：作为系统管理员，我想确保只有运维角色（ADMIN / OPS_MANAGER / OPS_STAFF）能访问运维功能，以便 TEACHER 和 STUDENT 无法接触系统内部信息。

---

## 验收准则（AC）

### AC-1 · 组件健康状态查看

- **Given** 运维角色（ADMIN / OPS_MANAGER / OPS_STAFF）已登录
- **When** 访问系统健康面板页面
- **Then** 展示 4 个组件状态卡片：MySQL、Neo4j、Redis、MinIO，每个卡片显示：
  - 组件名称
  - 状态标识（UP 绿色 / DOWN 红色）
  - 响应延迟（毫秒，UP 时显示；DOWN 时显示错误原因）
- **验证方式**: podman 停掉 Redis 容器后刷新页面，Redis 卡片显示 DOWN + 连接失败原因；重启后恢复 UP。

### AC-2 · JVM 运行时指标查看

- **Given** 运维角色已登录，健康面板已展示
- **When** 查看页面上的 JVM 指标区域
- **Then** 展示至少 4 项 JVM 指标：
  - 堆内存使用量 / 最大堆内存（MB，含百分比进度条）
  - CPU 使用率（百分比，进程级）
  - 活跃线程数
  - GC 暂停时间或 GC 次数（如果 Actuator 默认暴露）
- **验证方式**: 对比 `/actuator/metrics/jvm.memory.used` 返回值与页面显示数值一致。

### AC-3 · 日志文件列表

- **Given** 运维角色已登录
- **When** 访问系统日志页面
- **Then** 展示 `logs/` 目录下所有日志文件列表，每项显示：
  - 文件名（如 `graphnexus-dev.log`）
  - 文件大小（人类可读格式，如 `1.6 MB`）
  - 最后修改时间（`yyyy-MM-dd HH:mm:ss` 格式）
  - 文件按修改时间倒序排列
- **验证方式**: 对比 `ls -lh logs/` 命令输出与页面列表一致。

### AC-4 · 日志内容分页查看

- **Given** 日志文件列表已展示，存在一个非空日志文件
- **When** 点击该文件名
- **Then** 页面右侧展示该文件内容：
  - 默认第 1 页，每页 200 行
  - 顶部显示当前页码 / 总页数
  - 「上一页」「下一页」按钮，首尾页对应按钮禁用
  - 日志行使用等宽字体（monospace）
- **验证方式**: 对比 `tail -n 200 logs/graphnexus-dev.log` 输出与页面第 1 页内容一致；翻到第 2 页后对比 `sed -n '201,400p' logs/graphnexus-dev.log`。

### AC-5 · 日志文件下载

- **Given** 日志文件列表中某文件已选中
- **When** 点击「下载」按钮
- **Then** 浏览器触发文件下载，Content-Type 为 `text/plain` 或 `application/octet-stream`，Content-Disposition 包含原文件名
- **验证方式**: 下载后 `diff` 对比本地文件与服务器 `logs/` 下原文件完全一致。

### AC-6 · 运维 API 权限校验

- **Given** TEACHER 或 STUDENT 角色已登录，持有有效 JWT
- **When** 直接请求 `GET /api/v1/system/health` 或 `GET /api/v1/system/logs`
- **Then** 返回 HTTP 403，响应体含 `errorCode` 和 `userTip`
- **验证方式**: 先用 TEACHER 账号登录获取 Token，`curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/system/health` 返回 403。

### AC-7 · 运维页面路由守卫

- **Given** TEACHER 或 STUDENT 角色已登录
- **When** 在浏览器地址栏直接输入 `/system/health` 或 `/system/logs`
- **Then** 页面不渲染运维内容，重定向到 403 页面或首页，或显示无权限提示
- **验证方式**: TEACHER 账号登录后手动输入 URL，确认无法看到运维页面。

### AC-8 · 运维菜单可见性

- **Given** 不同角色用户已登录
- **When** 查看侧边栏菜单
- **Then** ADMIN / OPS_MANAGER / OPS_STAFF 可见「系统运维」菜单组（含「系统健康」「系统日志」子项）；TEACHER / STUDENT 不可见该菜单组
- **验证方式**: 分别用 ADMIN 和 TEACHER 登录，对比侧边栏菜单项差异。

---

## 范围切分

### v1（本次必做）

- 4 组件健康检查（MySQL / Neo4j / Redis / MinIO）+ 响应延迟
- JVM 堆内存 / CPU / 线程数实时指标
- 日志文件列表 + 分页查看 + 下载
- 运维角色权限收敛（API + 前端路由 + 菜单可见性）
- 前端健康面板 + 日志页面，极简风格继承

### v2（下一轮考虑，不本次）

- 日志文件按日期范围筛选（仅展示某天的日志）
- 实时日志 tail（WebSocket 推送最新日志行，无需手动刷新）
- 健康指标历史趋势图（近 1 小时/24 小时折线图）
- 日志文件分页大小可用户自定义（目前固定 200 行/页）

### out（永远不做）

- 告警通知（邮件/钉钉/企微）— 独立运维平台职责
- 日志全文检索/ELK 集成 — 独立日志平台职责
- 外部监控系统集成（Prometheus/Grafana 对接）— 独立 SRE 项目
- 日志级别运行时动态修改 — 安全风险，禁止生产环境热改日志级别

---

## 非功能性需求

- **性能**: 健康面板 API 响应时间 ≤ 5s（组件健康检查含外部连接超时）；日志分页 API 响应时间 ≤ 2s（10MB 文件单页读取）
- **可访问性**: 无特殊要求（v1 运维人员内部工具）
- **安全**: `/api/v1/system/**` 仅 ADMIN / OPS_MANAGER / OPS_STAFF 可访问；`/actuator/**` 从 `permitAll()` 改为同等角色限制；日志 API 禁止路径遍历攻击（文件名含 `../` 拒绝）
- **兼容性**: 桌面端 ≥1280px（继承项目 V1 目标分辨率）；Chrome/Firefox/Edge 120+
- **可观测性**: 健康 API 调用不产生额外业务日志（避免日志自循环）；日志 API 调用记录 WARN 级审计日志（含操作者 + 操作文件名）

---

## 依赖与假设

- **已有依赖**: `spring-boot-starter-actuator`（已在 pom.xml）、`logstash-logback-encoder`（已在 pom.xml）
- **已有配置**: `application.yml` 已暴露 `/actuator` 端点（health/info/metrics）、`management.endpoint.health.show-details=always`、`logback-spring.xml` 已配置文件持久化
- **已有模块**: Spring Security + JWT 认证链路已就绪，`SecurityConfig` 已有 `hasAnyRole()` 方法级注解支持
- **假设**: `logs/` 目录在应用工作目录下可读写；podman 基础设施容器（MySQL/Neo4j/Redis/MinIO）正常运行；Actuator 的 `/actuator/metrics` 端点暴露的 JVM 指标名称与 Spring Boot 3.3.x 默认一致
- **前端**: Naive UI 组件库已在项目中可用，极简调性已锁定

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。