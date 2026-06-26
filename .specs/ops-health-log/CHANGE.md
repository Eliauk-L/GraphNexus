# CHANGE — 系统运维功能（健康检测 + 日志查看）

> Change ID: `ops-health-log`
> 日期: 2026-06-23
> 角色: Planner

---

## Why

业务功能（文档抽取/成绩导入/图谱融合/智能问答/配置管理）已全部实现，系统进入可运维阶段。当前缺乏：
- 运维人员无法快速判断各基础设施组件（MySQL/Neo4j/Redis/MinIO）的健康状态
- 排查线上问题时需要 SSH 到服务器 `tail -f` 日志，效率低下

需要在管理后台新增运维面板，提供**一目了然的系统健康状态**和**无需 SSH 的日志查看能力**。

---

## What

### 1. 系统健康检测（C 级：组件连通性 + JVM 运行时指标）

**已有基础**：
- `spring-boot-starter-actuator` 已在 pom.xml 中
- `application.yml` 已配置 `/actuator` 端点（health/info/metrics/prometheus 已暴露，`show-details=always`）
- Neo4j health 已启用，DB health 已禁用

**本次做**：
- 启用 MySQL 健康检查（`management.health.db.enabled: true`）
- 新增 Redis HealthIndicator（检查连通性 + ping 延迟）
- 新增 MinIO HealthIndicator（检查 bucket 可达性）
- 新增聚合健康 API `GET /api/v1/system/health`（返回统一格式的组件状态 + JVM 内存/CPU/线程指标）
- 限制 Actuator 端点为运维角色可访问（SecurityConfig 调整）
- 前端：新增「系统健康」面板，展示各组件状态卡片 + JVM 指标仪表盘

### 2. 日志查看

**已有基础**：
- `logback-spring.xml` 已配置 RollingFileAppender（FILE_PLAIN / FILE_JSON / FILE_ERROR），日志已持久化到 `logs/` 目录
- `system-log-viewer/DESIGN.md`（2026-06-18）已设计 API 方案，本次复用其设计

**本次做**：
- 新增 `GET /api/v1/system/logs` — 列出日志文件（名称/大小/修改时间）
- 新增 `GET /api/v1/system/logs/{filename}` — 分页读取日志内容（`?page=&size=`）
- 新增 `GET /api/v1/system/logs/{filename}/download` — 下载日志文件
- 前端：新增「系统日志」页面，左侧文件列表 + 右侧日志内容分页浏览 + 下载按钮

---

## 影响面

| 维度 | 判定 |
|------|------|
| 是否新增/修改 REQUIREMENT.md | 是，正式需求拆分 |
| 是否触及架构（需更新 DESIGN.md / ADR） | 是，需 ADR（Actuator 安全策略 + 日志 API 设计） |
| 是否影响现有 AC | 否 |
| 是否新增依赖 | **否**（Actuator 已在 pom.xml） |
| 是否新增模块 | 是：`api/system/` + `application/system/`（运维功能） |
| 是否前端变更 | 是：新增健康面板 + 日志页面 + 路由 + 菜单 |
| 吸收已有工件 | 是：吸收 `system-log-viewer/DESIGN.md` 的 ADR-2/ADR-3（日志 API + 前端设计），其 ADR-1 已实现 |

---

## 范围排除

| 排除项 | 理由 |
|--------|------|
| 告警通知（邮件/钉钉/企微） | Q6 明确排除 |
| 健康指标历史存储/时序图 | Q6 明确排除，仅实时查看 |
| 日志级别动态修改（`/actuator/loggers`） | Q6 明确排除 |
| 日志全文检索/关键词搜索 | Q6 明确排除，仅按文件名选看 + 分页 |
| 外部监控系统集成（Prometheus/Grafana） | Q6 明确排除（Prometheus 端点已配置但本次不做对接） |
| 多环境同步 | v1 仅限当前部署实例 |
| 移动端适配 | v1 桌面端 ≥1280px |

---

## 验收线

1. 运维角色登录后可在管理后台看到「系统运维」菜单区
2. 健康面板实时展示 MySQL / Neo4j / Redis / MinIO 四组件的连通状态 + JVM 内存/CPU/线程数
3. 日志页面可列出 `logs/` 目录下所有日志文件，点击文件名分页查看内容，支持下载
4. 非运维角色（TEACHER / STUDENT）无法访问运维 API 和页面（403）

---

## 视觉调性

继承项目已锁定的 **极简（Minimal）— 参考 Linear/Vercel/Stripe**（`frontend-ui` CHANGE 步骤 0.6 锁定）。

---

## 路径建议

**完整路径**：`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`

理由：
- 涉及新增模块 + 新增端点 + 安全配置 + 前端页面，影响面中等
- 需 ADR 锁定 Actuator 安全策略和日志 API 分页参数
- `system-log-viewer/DESIGN.md` 提供设计输入，减少 DESIGN 阶段工作量但不需要跳过它