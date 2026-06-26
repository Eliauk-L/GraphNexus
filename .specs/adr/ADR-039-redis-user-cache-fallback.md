# ADR-039: Redis 用户缓存策略与降级方案

- **状态**: accepted
- **日期**: 2026-06-22
- **决策者**: AI（Architect 角色）+ 人工 review
- **关联**: `@.specs/user-auth-rbac/DESIGN.md` D8/D9

---

## Context

每次 API 请求的 `JwtAuthenticationFilter` 都需要获取用户信息（status + roles）来验证用户是否被禁用以及构建 `SecurityContext`。如果每次都查 MySQL `user_account JOIN user_role`，会产生不必要的数据库负载。

系统技术栈已锁定 Redis 7.x + Lettuce，需要决定：
1. Redis 缓存什么？Key 设计？TTL？
2. 缓存何时写入/失效？
3. Redis 不可用时系统行为？

备选方案：Spring Cache `@Cacheable` 声明式缓存、Caffeine 本地缓存、不缓存直接查 MySQL。

## Decision

### 选择手动 StringRedisTemplate + 降级查 MySQL

#### Key 设计

| 用途 | Key 模式 | Value | TTL | 写入时机 | 删除时机 |
|------|---------|-------|-----|---------|---------|
| 用户权限缓存 | `user:auth:<userId>` | JSON `{"username":"...","realName":"...","status":"ENABLED","roles":["ADMIN","TEACHER"]}` | 30 min | 登录成功 + 缓存未命中（Lazy） | 用户禁用/角色变更/登出 |
| Refresh Token | `refresh:<uuid>` | `userId`（纯字符串） | 7 d | 登录成功 + Refresh 成功 | 登出/Refresh 滚动（DEL旧Key） |

#### 缓存读写模式

```
读（JwtAuthenticationFilter.doFilterInternal）：
  1. try { redisTemplate.opsForValue().get("user:auth:" + userId) }
  2. 命中 → 解析 JSON → 构建 UserPrincipal
  3. 未命中 → 查 MySQL user_account JOIN user_role → 构建 UserPrincipal
              → 异步写 Redis SETEX（不影响本次请求响应时间）
  4. Redis 异常 → catch → WARN 日志 → 回退步骤 3 查 MySQL

写/更新（AuthService.login / updateUser）：
  1. 构建 JSON
  2. redisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES)
  
删（AuthService.updateUser 禁用/改角色 / logout）：
  1. redisTemplate.delete("user:auth:" + userId)
  2. 同时删除所有该用户的 refresh token keys（SCAN refresh:* → 匹配 userId → DEL）
```

#### 降级策略

```
Redis 不可用：
  - 启动：不强制校验 Redis 连接（允许 Redis 后启动）
  - 读缓存：catch RedisException → WARN 日志 → 直查 MySQL → 正常返回
  - 写缓存：catch RedisException → WARN 日志 → 跳过（下次查 MySQL 即可）
  - Refresh Token：catch RedisException → 返回 503 "服务暂不可用，请稍后重试"
                   （Refresh 强依赖 Redis，无法降级）
  - 日志：每分钟最多打 1 条 WARN（防日志风暴）
```

### 为什么不用 Spring Cache @Cacheable

Spring Cache 注解方案（`@Cacheable("userAuth")`）的优势是代码简洁，但：
1. 默认 JDK 序列化导致 Redis 中数据不可读、不可跨语言
2. 缓存清除粒度控制不如手动精确（需 `@CacheEvict` 注解分散在各方法）
3. 与既有 Caffeine 的 CacheManager 可能产生冲突
4. 手动控制更灵活：可自定义 key 格式、TTL、序列化

### 为什么不用 Caffeine 本地缓存

Caffeine 已在项目中用于 GDS 图指标缓存（本地、短 TTL、单机）。但用户权限缓存不适合本地：
1. 用户禁用/角色变更需要跨实例即时失效——本地缓存无法广播失效通知
2. Refresh Token 必须集中存储——本地缓存无法跨实例共享

## Consequences

### 优势
- Redis 不可用时认证链路仍可用（Access Token 验证 + 查 MySQL）
- Key 格式可读、可运维（`redis-cli GET user:auth:1` 直接看到 JSON）
- 缓存失效精确（用户禁用 → 即时 DEL key → 下次请求必查 MySQL 发现 status=DISABLED）

### 代价
- 手写缓存读写逻辑（~30 行），不如 `@Cacheable` 简洁
- Redis 不可用时 Refresh Token 完全不可用（强依赖），用户需重新登录
- 首次引入 Redis，涉及序列化配置 + 连接池调优

### 排除了什么
- **Spring Session Redis**：引入分布式 Session，但 JWT 天然无状态无需 Session
- **Redisson**：功能丰富（分布式锁/RMap/RQueue），但 v1 仅需 String 缓存，Lettuce + StringRedisTemplate 够用