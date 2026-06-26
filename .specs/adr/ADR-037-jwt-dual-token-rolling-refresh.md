# ADR-037: JWT 双 Token 滚动刷新方案

- **状态**: accepted
- **日期**: 2026-06-22
- **决策者**: AI（Architect 角色）+ 人工 review
- **关联**: `@.specs/user-auth-rbac/DESIGN.md` D1/D2/D7

---

## Context

系统需要实现无状态认证。方案需要满足：
1. 用户登录后获得访问凭证，后续请求携带凭证访问
2. 凭证过期后可通过"刷新凭证"获取新凭证，避免频繁重新登录
3. 管理员禁用用户或修改角色后，用户权限应尽快失效
4. 支持用户主动登出（使 Refresh Token 失效）

备选方案：单 Access Token（长有效期）、Session（Redis 有状态）、双 Token（Access + Refresh）。

## Decision

**选择 JWT 双 Token 滚动刷新方案**。

### Token 规格

| 属性 | Access Token | Refresh Token |
|------|-------------|---------------|
| **格式** | JWT（jjwt HS256 签名） | UUID v4 |
| **有效期** | 30 分钟 | 7 天 |
| **Payload** | `{sub(用户名), userId, roles([...]), iat, exp}` | 无（仅作为 Redis key） |
| **存储位置（前端）** | localStorage `accessToken` | localStorage `refreshToken` |
| **存储位置（后端）** | 无状态（自包含验证） | Redis `refresh:<uuid>` → value=`userId`，TTL=7d |
| **验证方式** | jjwt 签名 + 过期时间校验 | Redis GET → 存在且未过期 |

### 滚动刷新流程

```
登录 → 签发 AT + RT（UUID1 存入 Redis）
AT 过期 → 前端调 POST /refresh 携带 UUID1
         → 后端 Redis GET UUID1 → 存在 → DEL UUID1（旧 RT 失效）
         → 签发新 AT + 新 UUID2 → SETEX UUID2（滚动刷新）
         → 前端更新 localStorage
UUID1 再次使用 → Redis GET → 不存在 → 401 "登录已过期"
```

### 安全特性

- **Refresh Token 不可预测**：UUID v4，128 位随机
- **Refresh Token 不可重放**：每次使用后立即删除并签发新 Key
- **Access Token 短 TTL**：即使被盗，30min 窗口内有效，且可通过 Redis `user:auth:<userId>` status 二次校验拦截
- **登出即时生效**：`POST /logout` → DEL Redis `refresh:<uuid>` → Refresh Token 立即失效
- **用户禁用即时拦截**：`JwtAuthenticationFilter` 每次从 Redis/MySQL 查 `status`，DisABLED → 403

## Consequences

### 优势
- 无状态 API（Access Token 不依赖 Redis），Redis 宕机不影响已登录用户访问（仅影响 Refresh）
- 滚动刷新天然防重放：Refresh Token 用一次就换
- Refresh Token 不暴露用户信息（UUID 不可解读）

### 代价
- Refresh 必须依赖 Redis：Redis 不可用时用户 Access Token 过期后无法刷新，需重新登录
- 并发 401 刷新竞争：需前端"刷新锁"防并发（axios interceptor 中 Promise 单例）
- 前端 localStorage 存 Token 面临 XSS 风险（缓解：短 TTL + CSP + DOMPurify）

### 排除了什么
- **单 Token 长有效期**：无法主动撤销/刷新，安全性差
- **JWT 格式 Refresh Token**：自包含意味着无法主动撤销（除非引入黑名单，增加复杂度）
- **Session 方案**：有状态，水平扩展需 Redis Session 共享，且前端需处理 Cookie 跨域