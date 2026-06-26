# ADR-047 · Actuator 安全策略

> **状态**: proposed
> **日期**: 2026-06-23
> **Change**: `ops-health-log`
> **关联**: DESIGN.md § D1, D5, D6

---

## Context

项目已引入 `spring-boot-starter-actuator`，`application.yml` 配置了 `/actuator` 端点暴露（health/info/metrics/prometheus）。当前 `SecurityConfig` 中 `/actuator/**` 为 `permitAll()`——任何能访问服务器的人都可以查看健康状态和指标。

随着 `ops-health-log` 引入运维功能，需要：
1. 限制 Actuator 端点的访问权限
2. 决定前端是否直接调用 `/actuator`，还是通过聚合 API

---

## Decision

**1. Actuator 端点权限收敛**

`SecurityConfig.securityFilterChain` 中 `/actuator/**` 从 `permitAll()` 改为 `hasAnyRole('ADMIN', 'OPS_MANAGER', 'OPS_STAFF')`。

**2. 前端通过聚合 API 访问健康数据**

前端 `SystemHealthPage.vue` 不直接调用 `/actuator/health` 或 `/actuator/metrics/*`，而是调用 `GET /api/v1/system/health`。该端点由 `SystemHealthController` + `SystemHealthService` 内部调用：
- 所有 `HealthIndicator` bean 的 `health()` 方法（进程内方法调用，非 HTTP）
- `MetricsEndpoint.metric()` 方法（进程内方法调用）

**3. `/api/v1/system/**` 的安全规则声明位置**

```java
// SecurityFilterChain 中：
.requestMatchers("/actuator/**").hasAnyRole("ADMIN", "OPS_MANAGER", "OPS_STAFF")
.requestMatchers("/api/v1/system/**").hasAnyRole("ADMIN", "OPS_MANAGER", "OPS_STAFF")  // 必须在 /api/v1/** 之前
.requestMatchers("/api/v1/**").authenticated()
```

---

## Consequences

**正面**：
- 运维 API 与业务 API 同用一套 JWT 认证，无需额外配置
- 聚合 API 统一响应格式（`ApiResult<SystemHealthVO>`），前端处理逻辑简单
- Actuator 内部 API 细节不泄露给前端（如 `/actuator/health` 的 `components.*.details.*` 嵌套结构）
- 两条 SecurityFilterChain 规则覆盖所有运维端点（`/actuator/**` + `/api/v1/system/**`）

**负面**：
- `SystemHealthService` 直接依赖 `MetricsEndpoint`（Actuator 内部 Bean），Spring Boot 大版本升级时有 API 变更风险（概率低，`MetricsEndpoint` 是公开 API）
- 新增聚合 API 增加了一层间接调用（但进程内方法调用的延迟可忽略）

**约束**：
- SecurityFilterChain 规则顺序敏感：`/api/v1/system/**` 必须在 `/api/v1/**` 之前声明
- 新增运维 API 端点必须统一放在 `/api/v1/system/` 路径下