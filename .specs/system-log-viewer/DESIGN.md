# DESIGN — 系统日志查看与持久化

> Change ID: `system-log-viewer`
> 日期: 2026-06-18
> 角色: Architect

---

## 需求概述

实现系统日志的**持久化保存**（文件存储）和**前端可视化查看**（打印/显示/下载）。

当前状态：
- 后端日志仅输出到控制台（ConsoleAppender），无文件持久化
- 前端无日志查看页面
- 已有 `logstash-logback-encoder` 依赖和 `TraceIdFilter`

---

## 架构决策

### ADR-1: 日志持久化方案

**决策**: 使用 Logback `RollingFileAppender` 按时间+大小滚动，存储到 `logs/` 目录。

**理由**:
- Spring Boot 默认日志框架就是 Logback，项目已有 `logback-spring.xml`
- 无需引入额外依赖
- 滚动策略防止磁盘爆满

**备选方案**:
- ELK Stack (Elasticsearch + Logstash + Kibana): 过重，不适合当前阶段
- 数据库存储: 日志量大时性能差，查询不如 ELK 灵活

### ADR-2: 日志查看 API 设计

**决策**: 提供 REST API 直接读取日志文件内容，前端通过 API 获取并展示。

**API 设计**:
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/system/logs` | 列出所有日志文件（名称、大小、修改时间） |
| GET | `/api/v1/system/logs/{filename}` | 读取指定日志文件内容（支持分页） |
| GET | `/api/v1/system/logs/{filename}/download` | 下载日志文件 |

**安全考虑**: 日志 API 放在 `/api/v1/system/` 下，后续可加权限控制。生产环境建议限制访问。

### ADR-3: 前端展示方案

**决策**: 新增侧边栏菜单项「系统日志」，使用现有 DataTable 组件展示日志文件列表，点击查看内容。

---

## 文件变更清单

### 后端（4 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/resources/logback-spring.xml` | 修改 | 添加 RollingFileAppender（按日期+大小滚动） |
| `src/main/java/.../api/system/controller/LogController.java` | 新增 | 日志查看 REST API |
| `src/main/java/.../api/system/dto/LogFileVO.java` | 新增 | 日志文件元数据 VO |
| `src/main/java/.../api/system/dto/LogContentVO.java` | 新增 | 日志内容 VO（含分页） |
| `src/main/java/.../api/system/service/LogService.java` | 新增 | 日志文件读取服务 |

### 前端（5 个文件）

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/api/log.ts` | 新增 | 日志 API 调用 |
| `frontend/src/api/types.ts` | 修改 | 新增 LogFileVO、LogContentVO 类型 |
| `frontend/src/views/log/logStore.ts` | 新增 | 日志状态管理 |
| `frontend/src/views/log/SystemLogPage.vue` | 新增 | 日志查看页面 |
| `frontend/src/router/index.ts` | 修改 | 新增 `/logs` 路由 |
| `frontend/src/common/components/AppLayout.vue` | 修改 | 侧边栏新增「系统日志」菜单 |

---

## 风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 日志文件过大导致前端卡顿 | 中 | API 分页读取 + 前端虚拟滚动 |
| 生产环境日志泄露敏感信息 | 高 | 后续加 Spring Security 权限控制；当前 dev 环境无此风险 |
| 磁盘空间被日志占满 | 中 | 滚动策略 maxHistory=30天 + maxFileSize=10MB |

---

## 实现顺序

1. **后端 logback-spring.xml** — 添加文件持久化
2. **后端 LogService + LogController** — 日志读取 API
3. **后端 DTO/VO** — 数据传输对象
4. **前端 API 层** — api/log.ts + types.ts 更新
5. **前端 Store + Page** — 状态管理 + 页面
6. **前端路由 + 导航** — router + AppLayout