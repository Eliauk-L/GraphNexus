# DESIGN: 用户认证与角色权限管理

- **Change ID**: `user-auth-rbac`
- **关联**: `@.specs/user-auth-rbac/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 技术栈已在 CONTEXT.md 锁定，本次直接引用，不重新选卡。

- **选定**：既有栈扩展（Spring Boot 3.3.5 + Spring Security 6.x + Redis 7.x + Vue 3）
- **后端**：Spring Boot 3.3.5 / Spring Security 6.3（已含） / Spring Data Redis 3.3.x（新增）/ jjwt 0.12.6（新增）
- **前端**：Vue 3.5 + TypeScript 5.6 + Pinia 2.2 + Vue Router 4.4 + Naive UI 2.39（均已有）
- **数据库**：MySQL 8.0（已有）+ Redis 7.x via Lettuce（新增）
- **关键依赖**：
  - `spring-boot-starter-data-redis`（新增 · Redis 客户端 + Spring Cache 抽象）
  - `jjwt-api` + `jjwt-impl` + `jjwt-jackson`（新增 · JWT 生成/解析）
  - `spring-boot-starter-security`（已有 · 移除 autoconfigure exclude）
  - `spring-security-test`（已有 · 测试用）
- **理由**：既有技术栈已预留 Spring Security + Redis + JWT，仅需按计划实现。无新增框架/语言切换
- **明确排除**：Spring Session Redis（不引入分布式 session，JWT 天然无状态）；Sa-Token（不引入第三方权限框架，Spring Security 原生够用）

---

## 0.5 既有架构对齐（brownfield · 基于 grep 实测）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep/ls 验证）：
- src/main/resources/application.yml          — 移除 Security exclude + 新增 redis/jwt 配置
- src/main/resources/application-dev.yml      — 新增 Redis 连接配置 + jwt 配置
- src/main/resources/db/init.sql              — 新增 user_account/role/user_role DDL
- pom.xml                                      — 新增 redis + jjwt 依赖
- common/exception/ErrorCode.java              — 新增 A0023~A0030 认证错误码
- common/exception/GlobalExceptionHandler.java — 新增 AccessDeniedException / AuthenticationException 处理
- common/config/                               — 新增 SecurityConfig / RedisConfig / JwtProperties
- api/graph/controller/                        — 加 @PreAuthorize 注解（不改方法体）
- api/file/controller/                         — 同上
- api/analysis/controller/                     — 同上
- api/query/controller/                        — 同上
- api/llm/controller/                          — 同上
- frontend/src/router/index.ts                 — 新增路由 /login、/settings/users + 路由守卫
- frontend/src/common/components/AppLayout.vue — 侧边菜单按角色过滤

新增模块：
- common/security/                             — JwtAuthenticationFilter / JwtTokenProvider / UserPrincipal
- api/auth/                                    — AuthController + DTO
- application/auth/                            — AuthService + TokenService + model
- infrastructure/mysql/auth/                   — UserAccountDO / RoleDO / UserRoleDO + Repository
- frontend/src/views/auth/                     — LoginPage.vue / UserManagePage.vue
- frontend/src/stores/authStore.ts             — Pinia 认证状态
- frontend/src/api/auth.ts                     — 认证 API 调用
- frontend/src/router/authGuard.ts             — 路由守卫逻辑

禁动清单（AI 禁止"顺手"碰的）：
- application/graph/**                         — 图谱构建/融合/指标逻辑（仅给 Controller 加注解）
- application/file/**                          — 文件处理管线逻辑（仅给 Controller 加注解）
- application/query/**                         — 问答逻辑（仅给 Controller 加注解）
- infrastructure/neo4j/**                      — Neo4j 持久化层（与认证无关）
- infrastructure/storage/**                    — MinIO 存储层（与认证无关）
- infrastructure/llm/**                        — LLM 网关（与认证无关）
- docs/**                                      — 设计文档（非代码变更）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| HTTP 安全过滤 | Spring Security 已在 pom.xml，仅被 exclude | **启用**既有 Security + 自定义 JwtAuthenticationFilter |
| 全局异常处理 | `common/exception/GlobalExceptionHandler.java` | **沿用** · 追加异常处理 |
| 错误码 | `common/exception/ErrorCode.java`（A/B/C 三段） | **沿用** · A 段追加 A0023~A0030 |
| 统一响应体 | `common/ApiResult.java` | **沿用** · 不做修改 |
| 构造器注入 | `@RequiredArgsConstructor` + `private final` | **沿用** |
| 密码加密 | 无既有 | **新建** BCryptPasswordEncoder Bean |
| Redis 连接 | 无既有 | **新建** Lettuce（Spring Data Redis 自动配置） |
| 前端状态管理 | Pinia 2.2（package.json） | **沿用** · 新建 `authStore.ts` |
| 前端 HTTP 客户端 | axios（package.json） | **沿用** · 追加 interceptor |
| 前端路由守卫 | vue-router beforeEach（当前未用守卫） | **沿用** · 在 `authGuard.ts` 中注册 |
| 前端 UI 组件库 | Naive UI 2.39 | **沿用** · 不引入新组件库 |
| 前端 CSS 框架 | Tailwind CSS 4 | **沿用** · 登录页/用户管理页用 Tailwind |

### 0.5.3 沿用模式 vs 引入新模式

```
- 依赖注入：    **沿用** 构造器注入 + @RequiredArgsConstructor（项目规范 §1.4.3）
- 分层架构：    **沿用** L1(api) → L2(application) → L3(infrastructure) 四层
- 数据对象：    **沿用** DO/BO/DTO/VO 后缀（项目规范 §1.4.1）
- 异常传递：    **沿用** L3 抛异常 → L2 记日志 → L1 GlobalExceptionHandler 统一处理
- 事务管理：    **沿用** @Transactional 仅放 L2 Service public 方法
- REST 命名：   **沿用** 小写下划线分隔，单数资源名
- JWT 认证：    **引入新模式**（项目首次）→ OncePerRequestFilter 过滤器链 + @PreAuthorize 注解
- Redis 缓存：  **引入新模式**（项目首次）→ StringRedisTemplate + 降级兜底
- 前端全局守卫：**引入新模式**（项目首次）→ router.beforeEach + Pinia store
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| **D1** | JWT 实现：jjwt 0.12.x 库，HS256 签名，Access Token 30min / Refresh Token 7d | Nimbus JOSE / auth0-java-jwt | jjwt API 简洁，与 Spring Security 集成文档丰富，0.12.x 是 jjwt 最新稳定线 | Access Token 无法主动撤销（需等过期），安全性依赖短有效期 + Refresh Token 滚动刷新控制 |
| **D2** | Access Token payload：`{sub(用户名), userId, roles([ADMIN,TEACHER]), iat, exp}`。roles 写入 JWT 避免每次查库 | 仅放 userId 每次查库获取角色 | 减少 Redis/MySQL 查询，JWT 本身即承载权限信息；角色变更时旧 Token 在 TTL 内仍有旧角色，依赖 JwtAuthenticationFilter 二次校验（查 Redis 用户 status） | 角色变更后最长 30min 延迟生效（Access Token TTL）；可接受——短 TTL + 禁用用户即时生效 |
| **D3** | Spring Security 配置：单一 `SecurityFilterChain` bean，`JwtAuthenticationFilter` 插入 `UsernamePasswordAuthenticationFilter` 之前，所有 `/api/v1/**` authenticated，`/api/v1/auth/login` + `/api/v1/auth/refresh` permitAll | 多条 SecurityFilterChain 按路径分组 | 单一链够用且易维护；按路径分组过度复杂 | 认证端点路径固定，无法运行时动态调整 permitAll 列表 |
| **D4** | 密码：BCryptPasswordEncoder，strength=10 | SCrypt / Argon2 / PBKDF2 | BCrypt 是 Spring Security 默认方案，强度 10 在安全性与登录延迟间平衡（~100ms/次） | 不支持密码历史/复杂度策略（v1 不做） |
| **D5** | 角色模型：`role` 表预置 5 行（ADMIN/TEACHER/STUDENT/OPS_STAFF/OPS_MANAGER），通过 `data.sql` 初始化，不允许运行时新增/删除角色 | 角色完全动态可配置 | 5 类角色已经在用例视图和 User Story 中明确定义，预置 + 代码枚举保证一致性；动态角色需要配套的角色管理 UI + 代码映射，v1 无此需求 | 未来新增角色需 DDL 加枚举值 + data.sql 加行 + 代码改映射 |
| **D6** | ADMIN 继承 TEACHER：Security 层通过授予 ADMIN 同时拥有 `ROLE_ADMIN` 和 `ROLE_TEACHER` 两个 GrantedAuthority 实现，而非在 `@PreAuthorize` 中写 `hasAnyRole('ADMIN','TEACHER')` 到处重复 | 在 `@PreAuthorize` 中显式写 `hasAnyRole` | 角色继承关系集中在一处管理（创建用户时自动赋予继承角色），注解中只需写目标角色；减少了矩阵维护成本 | 角色继承逻辑隐式在数据层，需在 ADR 中明确记录；排查权限问题时需知道 ADMIN 同时拥有 TEACHER 权限 |
| **D7** | Refresh Token：UUID 格式，存 Redis key `refresh:<uuid>`，value 为 `userId`，TTL 7d。刷新时校验存在性→删除旧 key→生成新 UUID→写入新 key（滚动刷新） | JWT 格式 Refresh Token 自包含 | UUID 方式支持主动撤销（删除 Redis key），且 Refresh Token 不暴露用户信息在外；滚动刷新防止 Refresh Token 被盗后长期滥用 | 必须依赖 Redis 才能校验 Refresh Token；Redis 不可用时 Refresh 降级失败（需重新登录） |
| **D8** | Redis 用户缓存：key `user:auth:<userId>`，value JSON（`{"username":"...","realName":"...","status":"ENABLED","roles":["ADMIN","TEACHER"]}`），TTL 30min。写入时机=登录成功 + 权限校验缓存未命中；删除时机=用户禁用/角色变更 | Spring Cache `@Cacheable` 注解 | 手动控制 StringRedisTemplate 更灵活：可精确控制 key 格式/TTL/序列化，避免 Spring Cache 默认序列化的反序列化风险；与现有 Caffeine 缓存模式互补 | 需手写缓存读写逻辑（~20 行），不如 @Cacheable 简洁 |
| **D9** | Redis 不可用降级：try-catch Redis 操作 → WARN 日志 → 回退直查 MySQL `user_account` JOIN `user_role` | 启动时校验 Redis 连接，不可用则拒绝启动 | 降级优先于强依赖：Redis 是缓存加速而非唯一数据源，宕机不应阻塞业务 | 降级时每次请求查 MySQL，性能下降但功能正常；Redis 恢复后自动恢复缓存 |
| **D10** | 权限注解：Controller 类级别 `@PreAuthorize`（默认角色）+ 方法级别覆盖更宽松/更严格。例：`@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")` 在类上，`extract` 方法加 `@PreAuthorize("hasRole('ADMIN')")` 收紧 | 仅方法级别注解 / 仅 Filter 级别 URL 拦截 | 类+方法两级：类级覆盖大部分方法的方法级覆盖，减少重复注解；Filter 级 URL 拦截粒度不够（同一 Controller 内不同方法权限不同） | 需在 DESIGN 中明确列出每个 Controller 的类级+方法级注解矩阵（见 § 2.1） |
| **D11** | 测试 JWT 方案：`JwtTestHelper` 工具类（`static String generateTestToken(String username, String... roles)`），测试基类 `@SpringBootTest` + `TestSecurityConfig` 内嵌静态类覆盖 SecurityFilterChain 允许所有请求（保留 SecurityContext 注入但不校验 Token），或使用 `@WithMockUser` | Testcontainers Redis + 真实 JWT | JwtTestHelper 轻量无外部依赖；测试中 Token 直接注入 `Authorization` header 模拟真实链路。集成测试可选择性开启完整 Security 链 | TestSecurityConfig 覆盖后权限注解不会生效——需额外一个带真实 Security 链的测试类做权限回归 |
| **D12** | 前端 Token 存储：localStorage `accessToken` + `refreshToken` | sessionStorage / Cookie | localStorage 跨标签页共享（用户开新标签页已登录），且不随浏览器关闭丢失（除非手动登出）。Cookie 方案需 CSRF 防护，复杂度更高 | XSS 攻击可窃取 localStorage Token——缓解：CSP header + DOMPurify（已有）+ Token 短有效期 |

---

## 2. 数据流 / 架构图

### 2.1 登录认证流（完整）

```
  浏览器                          Spring Security              AuthService           MySQL/Redis
  ────                           ──────────────               ────────────           ──────────
    │                                  │                           │                     │
    │ POST /api/v1/auth/login          │                           │                     │
    │ {username, password}             │                           │                     │
    │ ─────────────────────────────>   │                           │                     │
    │                                  │ (permitAll · 不进JWT过滤器)                      │
    │                                  │ ──────────────────────>  │                     │
    │                                  │                           │ login(username,pwd) │
    │                                  │                           │ ───────────────>    │
    │                                  │                           │  SELECT * FROM      │
    │                                  │                           │  user_account       │
    │                                  │                           │  WHERE username=?   │
    │                                  │                           │  <───────────────   │
    │                                  │                           │  UserAccountDO      │
    │                                  │                           │                     │
    │                                  │                           │ BCrypt.matches(pwd) │
    │                                  │                           │ status==ENABLED?    │
    │                                  │                           │                     │
    │                                  │                           │ SELECT r.code       │
    │                                  │                           │ FROM role r         │
    │                                  │                           │ JOIN user_role ur   │
    │                                  │                           │ <───────────────    │
    │                                  │                           │ roles[]             │
    │                                  │                           │                     │
    │                                  │                           │ 缓存 user:auth:<id> │
    │                                  │                           │ ───────────────>    │
    │                                  │                           │   SETEX JSON (Redis)│
    │                                  │                           │                     │
    │                                  │                           │ 生成 Access Token   │
    │                                  │                           │ (jjwt HS256)        │
    │                                  │                           │ 生成 Refresh UUID   │
    │                                  │                           │ SETEX refresh:<uuid>│
    │                                  │                           │ ───────────────>    │
    │                                  │                           │   (Redis)           │
    │                                  │                           │                     │
    │                                  │  <──────────────────────  │                     │
    │                                  │  LoginResponse:           │                     │
    │                                  │  {accessToken,            │                     │
    │                                  │   refreshToken,           │                     │
    │                                  │   expiresIn, userInfo}    │                     │
    │  <─────────────────────────────  │                           │                     │
    │  HTTP 200                        │                           │                     │
```

### 2.2 请求鉴权流（每次 API 调用）

```
  浏览器                          JwtAuthFilter             SecurityContext         Controller
  ────                           ──────────────              ──────────────          ──────────
    │                                  │                           │                     │
    │ GET /api/v1/file/textbooks       │                           │                     │
    │ Authorization: Bearer <AT>       │                           │                     │
    │ ─────────────────────────────>   │                           │                     │
    │                                  │ 1. 提取 Header Bearer    │                     │
    │                                  │ 2. jjwt 验证签名+有效期   │                     │
    │                                  │ 3. 提取 claims           │                     │
    │                                  │ 4. GET user:auth:<userId>│                     │
    │                                  │    ──────────> Redis     │                     │
    │                                  │    <──────── hit/fallback│                     │
    │                                  │ 5. status==ENABLED?      │                     │
    │                                  │    (disabled → 403)       │                     │
    │                                  │ 6. 构建 UserPrincipal    │                     │
    │                                  │ 7. UsernamePasswordAuth  │                     │
    │                                  │    Token → SecurityCtx   │                     │
    │                                  │ ───────────────────────>│                      │
    │                                  │                           │                     │
    │                                  │                           │ ────── doFilter ───>│
    │                                  │                           │                     │ @PreAuthorize检查
    │                                  │                           │                     │ hasAnyRole匹配→放行
    │                                  │                           │                     │ 不匹配→403
    │                                  │                           │  <──────────────────│
    │  <─────────────────────────────  │                           │                     │
    │  HTTP 200 + 分页数据              │                           │                     │
```

### 2.3 Token 刷新流

```
  前端 axios interceptor          /api/v1/auth/refresh          TokenService           Redis
  ────                           ──────────────────             ────────────           ────
    │                                  │                           │                     │
    │ 原请求 → 401 A0102(Token过期)     │                           │                     │
    │ 自动调 refresh                   │                           │                     │
    │ ─────────────────────────────>   │                           │                     │
    │                                  │ validateRefreshToken     │                     │
    │                                  │ ──────────────────────>  │                     │
    │                                  │                           │ GET refresh:<uuid>  │
    │                                  │                           │ ───────────────>    │
    │                                  │                           │ <──── 存在+未过期── │
    │                                  │                           │ DEL refresh:<uuid>  │
    │                                  │                           │ ───────────────>    │
    │                                  │                           │ (删除旧的)          │
    │                                  │                           │                     │
    │                                  │                           │ 生成新AT + 新UUID   │
    │                                  │                           │ SETEX refresh:<new> │
    │                                  │                           │ ───────────────>    │
    │                                  │                           │ 更新 user:auth:缓存  │
    │                                  │                           │ (刷新TTL)           │
    │  <─────────────────────────────  │                           │                     │
    │  {newAccessToken, newRefresh,    │                           │                     │
    │   expiresIn}                     │                           │                     │
    │                                  │                           │                     │
    │ 用新AT重放原请求 ──────────────────────────────────────────────────────────────>   │
```

### 2.4 前端认证架构

```
  localStorage                  Pinia authStore              axios interceptor        vue-router
  ────────────                  ──────────────                ────────────────        ──────────
       │                              │                             │                      │
  accessToken ──────────────> login() 写入 store ─────> 设置 Authorization header      │
  refreshToken ──────────────>                                                          │
  userInfo ──────────────────>                                                          │
       │                              │                             │                      │
       │                         isAuthenticated                    │                  beforeEach:
       │                         hasRole('ADMIN')                    │                  ① 无Token → /login
       │                              │                             │                  ② 有Token无权限 → 403
       │                              ▼                             │                  ③ OK → next()
       │                         AppLayout.vue                      │                      │
       │                         (菜单按角色过滤)                     │                      │
       │                              │                             │                      │
       │                              │                             │ response 401        │
       │                              │                      <──────│ A0102(Token过期)    │
       │                              │                             │                      │
       │                              │                      ──────│ POST /refresh        │
       │                              │                             │ 成功→更新store+local │
       │                     <─────── refreshSuccess()              │ 重放原请求           │
       │                     <─────── refreshFailed()               │ 失败→清local        │
       │                              │                             │ → router.push(/login)│
       │                     logout() 清 store + local              │                      │
       │                              │                             │                      │
```

---

## 3. 关键状态机

### 3.1 用户账号状态

```
          管理员创建
              │
              ▼
          ┌───────┐    管理员禁用     ┌──────────┐
          │ENABLED│ ────────────────> │ DISABLED │
          └───────┘  <────────────────└──────────┘
              │        管理员重新启用       │
              │                            │
              ▼                            ▼
         可正常登录              已登录Token下次请求被拒
         Token可刷新               不可登录/不可刷新
```

### 3.2 Token 生命周期

```
  ┌─────────┐   过期/Refresh成功    ┌─────────┐   Refresh过期/主动登出   ┌──────────┐
  │ ACTIVE  │ ────────────────────> │REFRESHED│ ──────────────────────> │ REVOKED  │
  │(可用状态)│  <────────────────────│(滚动刷新)│                         │(需重新登录)│
  └─────────┘                      └─────────┘                         └──────────┘
       │                                                                     ▲
       │ 用户被禁用/Redis DEL refresh key                                       │
       └─────────────────────────────────────────────────────────────────────┘
```

---

## 4. ADR 索引

凡可逆性低的决策，单独 ADR：

- `@.specs/adr/ADR-037-jwt-dual-token-rolling-refresh.md` — JWT 双 Token 滚动刷新方案
- `@.specs/adr/ADR-038-rbac-role-permission-model.md` — RBAC 角色权限模型：5 类角色 + 继承 + 预置
- `@.specs/adr/ADR-039-redis-user-cache-fallback.md` — Redis 用户缓存策略与降级方案
- `@.specs/adr/ADR-040-endpoint-role-mapping-full-convergence.md` — 全端点权限收敛方案与端点-角色映射矩阵

---

## 5. 风险

| # | 风险 | 类型 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|---|
| **R1** | 全端点加权限注解后，大量现有集成测试因缺少 Token 而失败 | 实现风险 | 测试回归工作量大 | 高 | D11：`JwtTestHelper` + `TestSecurityConfig` 覆盖类，所有测试基类注入 Test Security 配置；新增一个带真实 Security 链的测试类做权限回归 |
| **R2** | Redis 首次引入，序列化/连接池/Lettuce 版本兼容性可能出问题 | 实现风险 | 认证链路不可用（降级回 MySQL） | 中 | D9：try-catch 降级；启动时不强校验 Redis 连接；`application-dev.yml` 中 Redis 连接参数已在 podman 中验证 |
| **R3** | 用户禁用后，已签发的 Access Token 在 30min 内仍有效 | 上线风险 | 被禁用用户在短时间内仍可操作 | 中 | `JwtAuthenticationFilter` 每次请求从 Redis/MySQL 校验 `status==ENABLED`；结合短 TTL（30min）最大延迟窗口可控 |
| **R4** | Refresh Token 滚动刷新并发竞争：用户快速发起多个请求，同时触发 refresh，可能导致多个新 Refresh Token 生成，旧 Token 被删除导致竞争失败的请求 refresh 失败 | 上线风险 | 用户偶尔遇到"请重新登录" | 低 | 前端在 axios interceptor 中使用"刷新锁"（Promise 单例）：同一时刻只有一个 refresh 请求进行中，其他 401 请求等待锁释放后共用新 Token |
| **R5** | pom.xml 新增 `spring-boot-starter-data-redis` 可能触发 Spring Boot 自动配置冲突（如 RedisAutoConfiguration 与 Caffeine 的 CacheAutoConfiguration） | 实现风险 | 启动失败或缓存行为异常 | 低 | Spring Boot 3.3 支持多个 CacheManager 共存（Redis + Caffeine），通过 `spring.cache.type=none` 禁用默认 CacheAutoConfiguration，Redis 仅用于 `StringRedisTemplate` 手动操作不注册为 CacheManager |
| **R6** | 前端 Token 存 localStorage 在 XSS 场景下可被窃取 | 长期债务 | 账号被盗用 | 低 | HTTP-only Cookie 方案需 CSRF 防护且同域限制；v1 接受 localStorage 风险，依赖 CSP header + DOMPurify（已有）+ 短 Token TTL 缓解。v2 可评估升级到 BFF（Backend For Frontend）模式 |

---

## 6. 不在范围

本次设计不解决但未来需考虑的：

- **数据级权限隔离**：教师仅看任教班级、学生仅看自己数据——v2
- **密码策略**：复杂度要求、过期、历史记录——v2
- **登录审计与异常检测**：登录时间/IP/User-Agent 日志，异地登录告警——v2
- **Token 黑名单/全局撤销**：管理员"踢出所有在线用户"功能——v2
- **Spring Security 方法级权限注解性能优化**：当前每个请求都执行 SpEL 表达式，万级 QPS 时可能成为瓶颈（v1 QPS 很低不受影响）
- **BFF 层**：将 JWT 从前端剥离到 BFF 层，前端仅使用 Session Cookie——v2+ 架构演进

---

## 9. 架构沉淀建议

> 本 change 引入了项目级可复用基础设施，以下条目供 `A-evolve` 批量同步到 CONTEXT.md。

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `common/security/JwtTokenProvider.java` | JWT 生成/解析/验证工具 | 任何需要签发或验证 JWT 的场景 | 项目唯一 JWT 操作入口，禁止 Controller/Service 直接调 jjwt API |
| `common/security/JwtAuthenticationFilter.java` | HTTP 请求 JWT 提取→验证→注入 SecurityContext | 所有受保护 REST API 请求 | OncePerRequestFilter，新增端点自动受保护 |
| `common/security/UserPrincipal.java` | 实现 UserDetails 的认证主体 | SecurityContext.getAuthentication().getPrincipal() | Controller/Service 获取当前用户信息的标准入口 |
| `common/config/JwtProperties.java` | JWT 配置属性（TTL/Secret/Issuer） | `@ConfigurationProperties("jwt")` | 所有 JWT 相关配置统一从 yml 注入 |
| `frontend/src/stores/authStore.ts` | 前端认证状态管理（Pinia） | 登录/登出/Token管理/角色判断 | 所有需要"知道当前用户是谁"的前端组件 |
| `frontend/src/router/authGuard.ts` | 前端路由守卫 | router.beforeEach | 新增路由自动触发认证检查 |
| `infrastructure/mysql/auth/` | 用户-角色持久化抽象 | 任何需要查用户信息的模块 | 通过 AuthService 接口访问，禁止直接注入 UserAccountRepository |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| JWT 认证 | HS256 / Access 30min + Refresh UUID 7d / jjwt 0.12 | 全栈：所有 API 请求 + 前端 axios | 高：替换需改 Security 配置 + 前端拦截器 + 所有测试 |
| RBAC 角色模型 | 5 类角色 code + ADMIN 继承 TEACHER + 预置不可运行时增删 | 全栈：API 权限注解 + 前端菜单 + 测试 | 中：新增角色需改代码（枚举/DDL/data.sql/@PreAuthorize） |
| Redis 缓存策略 | StringRedisTemplate 手动管理 / key `user:auth:` / 降级查 MySQL | 所有需要用户权限校验的请求 | 低：封装在 AuthService 内部，外部无感知 |
| 端点权限收敛 | @PreAuthorize 类级 + 方法级 / 矩阵映射 23 个端点 | 所有 Controller + 测试 | 高：移除权限注解需逐个 Controller 改 |

### 9.3 新增 / 修改的跨模块契约

```
API 新增:
- POST /api/v1/auth/login      → LoginResponse {accessToken, refreshToken, expiresIn, userInfo}
- POST /api/v1/auth/refresh    → 同上（不返回 userInfo）
- POST /api/v1/auth/logout     → ApiResult<Void>
- GET  /api/v1/auth/users      → PageResult<UserVO>（仅 ADMIN）
- POST /api/v1/auth/users      → ApiResult<UserVO>（仅 ADMIN）
- PUT  /api/v1/auth/users/{id} → ApiResult<UserVO>（仅 ADMIN）
- GET  /api/v1/auth/roles      → ApiResult<List<String>>（已登录即可）

API 修改（所有现有端点）:
- 请求 Header 必需: Authorization: Bearer <accessToken>
- 401 响应新增 errorCode: A0102（未登录/A0103 Token无效）
- 403 响应新增 errorCode: A0200（权限不足）
- Actuator /actuator/** 保持不受保护（内部使用）

数据库新增:
- user_account 表 (id, username, password, real_name, status, create_time, update_time)
- role 表 (id, code, name, description)
- user_role 表 (id, user_id, role_id)
- init.sql 中以上三表 + 5 行 role 预置数据
- pom.xml 新增 spring-boot-starter-data-redis + jjwt-api/impl/jackson

前端新增:
- /login 路由 → LoginPage.vue
- /settings/users 路由 → UserManagePage.vue（仅 ADMIN 菜单可见）
- authStore.ts（Pinia）
- authGuard.ts（全局路由守卫）
```

### 9.4 新增 / 升级的依赖

| 包 | 版本 | 用途 | 是否替换既有 |
|---|---|---|---|
| `spring-boot-starter-data-redis` | (Spring Boot 3.3.5 管理) | Redis 客户端 (Lettuce) | 否 · 首次引入 |
| `jjwt-api` | 0.12.6 | JWT API（生成/解析/验证） | 否 · 首次引入 |
| `jjwt-impl` | 0.12.6 | JWT 实现（runtime） | 否 · 首次引入 |
| `jjwt-jackson` | 0.12.6 | JWT JSON 序列化（Jackson） | 否 · 首次引入 |

### 9.5 禁动清单变化

```
- 新增禁动：禁止绕过 JwtTokenProvider 直接使用 jjwt API
- 新增禁动：禁止在 Controller 方法参数中手动解析 JWT Header（使用 SecurityContextHolder 或 @CurrentUser）
- 新增禁动：禁止在 Service 层注入 UserAccountRepository（须通过 AuthService 接口）
- 新增禁动：禁止在前端组件中直接读取 localStorage Token（须通过 authStore）
- 新增禁动：application.yml 中 Security exclude 禁止重新添加（安全红线）
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。