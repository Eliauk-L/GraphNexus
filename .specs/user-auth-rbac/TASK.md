# TASK: 用户认证与角色权限管理

- **Change ID**: `user-auth-rbac`
- **关联**: `@.specs/user-auth-rbac/REQUIREMENT.md`、`@.specs/user-auth-rbac/DESIGN.md`、`@.specs/user-auth-rbac/UI-DESIGN.md`

---

## 波次划分

```
Wave 1  (3 parallel):  T01[P] DDL     T02[P] pom.xml    T03[P] yml config
Wave 2  (6 parallel):  T04[P] Error   T05[P] DOs        T06[P] Repos
                       T07[P] JWT     T08[P] Principal   T09[P] data.sql
Wave 3  (2 parallel):  T10 Security   T11[P] Filter + TokenService
Wave 4  (3 parallel):  T12[P] AuthSvc  T13[P] Controller  T14[P] Exception
Wave 5  (3 parallel):  T15[P] FileCtl @Pre   T16[P] GraphCtl @Pre   T17[P] Analysis+Query+Llm @Pre
Wave 6  (3 parallel):  T18[P] TestHelper   T19[P] AuthTests   T20 full mvn test
── 后端完成 · 前端开始 ──
Wave 7  (2 parallel):  T21[P] Router+Store+API   T22[P] Guard+Interceptor
Wave 8  (3 parallel):  T23[P] LoginPage   T24[P] AppLayout   T25 UserManagePage
Wave 9  (1 serial):    T26 vue-tsc + smoke test
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。Wave 7 可与 Wave 5–6 并行（前端不依赖后端测试）。

---

## 任务清单

### Wave 1 — 基础设施（3 并行）

```xml
<task id="T01" parallel="true" status="pending">
  <name>DDL — user_account + role + user_role 表 + init.sql 同步</name>
  <read_files>
    src/main/resources/db/init.sql
    .specs/user-auth-rbac/DESIGN.md
    .specs/adr/ADR-038-rbac-role-permission-model.md
  </read_files>
  <write_files>
    src/main/resources/db/init.sql
  </write_files>
  <action>
    在 init.sql 末尾追加三张表的 DDL：
    - user_account: id, username(UNIQUE), password(VARCHAR 255), real_name, status(ENUM 'ENABLED','DISABLED'), create_time, update_time
    - role: id, code(VARCHAR 32 UNIQUE), name(VARCHAR 64), description(VARCHAR 255)
    - user_role: id, user_id(FK→user_account.id), role_id(FK→role.id), UNIQUE(user_id, role_id)
    使用 InnoDB + utf8mb4，COMMENT 中文注释。
    按项目规范：字段小写蛇形、含 create_time/update_time、user_account 不设 is_deleted。
  </action>
  <verify>mysql -u graphnexus -p graphnexus -e "SHOW CREATE TABLE user_account\G" 2>&1 | grep -q "user_account"</verify>
  <done>三张表 DDL 已追加到 init.sql；结构符合 DESIGN §0.5 + ADR-038</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>pom.xml — 新增 spring-boot-starter-data-redis + jjwt 依赖</name>
  <read_files>
    pom.xml
    .specs/user-auth-rbac/DESIGN.md §0
  </read_files>
  <write_files>
    pom.xml
  </write_files>
  <action>
    在 pom.xml dependencies 中新增：
    - spring-boot-starter-data-redis（Lettuce 客户端，版本由 Spring Boot 3.3.5 管理）
    - jjwt-api 0.12.6
    - jjwt-impl 0.12.6（runtime scope）
    - jjwt-jackson 0.12.6（runtime scope）
    在 properties 中加 &lt;jjwt.version&gt;0.12.6&lt;/jjwt.version&gt;。
    不新增其他依赖。不删除任何现有依赖。
  </action>
  <verify>./mvnw dependency:tree -Dincludes=io.jsonwebtoken:jjwt-api 2>&1 | grep -q "jjwt-api"</verify>
  <done>依赖解析成功；jjwt 和 spring-boot-starter-data-redis 出现在 dependency:tree 中</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>yml 配置 — 启用 Security + Redis 连接 + JWT 参数</name>
  <read_files>
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
    .specs/user-auth-rbac/DESIGN.md §9.4
    .specs/adr/ADR-037-jwt-dual-token-rolling-refresh.md
  </read_files>
  <write_files>
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
  </write_files>
  <action>
    application.yml：
    - 移除 autoconfigure.exclude 中的三个 Security 排除项（SecurityAutoConfiguration / UserDetailsServiceAutoConfiguration / ManagementWebSecurityAutoConfiguration）
    - 新增 spring.data.redis 基础配置（lettuce pool 默认）
    - 新增 jwt 配置段：secret（占位 ${JWT_SECRET:}）、access-token-ttl=30m、refresh-token-ttl=7d

    application-dev.yml：
    - 新增 spring.data.redis: host=localhost, port=6379, password=（空，podman 默认无密码）, database=0
    - 新增 jwt.secret: graphnexus-jwt-secret-key-dev-only（开发环境默认值）
    - 新增 jwt.access-token-ttl: 30m / refresh-token-ttl: 7d
  </action>
  <verify>grep -q "SecurityAutoConfiguration" src/main/resources/application.yml && echo "FAIL: exclude not removed" || echo "OK: Security enabled"</verify>
  <done>Security 排除已移除；Redis + JWT 配置段出现在 application-dev.yml 中</done>
  <depends_on></depends_on>
</task>
```

### Wave 2 — 后端基础组件（6 并行 · 依赖 Wave 1）

```xml
<task id="T04" parallel="true" status="pending">
  <name>ErrorCode — 新增认证/权限错误码 A0023–A0030</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    .specs/user-auth-rbac/REQUIREMENT.md（AC-2/3/5/6/8/10/12 的错误码引用）
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </write_files>
  <action>
    在 ErrorCode.java 的 A 段（A0022 之后）新增：
    - A0023: 用户名或密码错误（401）
    - A0024: 账号已被禁用（403）
    - A0025: 未登录或 Token 已过期（401）
    - A0026: Token 无效（401）
    - A0027: 登录已过期，请重新登录（401）
    - A0028: 用户名已存在（409）
    - A0029: 用户不存在（404）
    - A0030: 权限不足（403）
    注意 A0003 已存在"无权限访问"(403)，A0030 用于更明确的"角色权限不足"场景，userTip 区分。
  </action>
  <verify>grep -c "A0023\|A0024\|A0025\|A0026\|A0027\|A0028\|A0029\|A0030" src/main/java/com/graphnexus/common/exception/ErrorCode.java | grep -q "8"</verify>
  <done>8 个新错误码已追加；枚举值不冲突；AC-2/3/5/6/8/10/12 引用的错误码均存在</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>DO — UserAccountDO + RoleDO + UserRoleDO</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/（参考既有 DO 模式）
    .specs/adr/ADR-038-rbac-role-permission-model.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/auth/entity/UserAccountDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/auth/entity/RoleDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/auth/entity/UserRoleDO.java
  </write_files>
  <action>
    创建 infrastructure/mysql/auth/entity/ 包，新建三个 DO：
    - UserAccountDO: @Entity @Table("user_account")，含 id/username(unique)/password/realName/status(ENABLED|DISABLED)/createTime/updateTime
    - RoleDO: @Entity @Table("role")，含 id/code(unique)/name/description
    - UserRoleDO: @Entity @Table("user_role")，含 id/userId/roleId，UNIQUE(user_id, role_id)。
    使用 Lombok @Getter @Setter @NoArgsConstructor @AllArgsConstructor。
    字段命名小写蛇形（@Column），遵循项目规范。
    RoleDO 中定义静态常量：ADMIN/TEACHER/STUDENT/OPS_STAFF/OPS_MANAGER。
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；三个 DO 类字段完整，表名/列名映射正确</done>
  <depends_on>T01</depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>Repository — UserAccountRepository + RoleRepository + UserRoleRepository</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/（参考既有 Repository 模式）
    .specs/user-auth-rbac/DESIGN.md §9.1
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/auth/repository/UserAccountRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/auth/repository/RoleRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/auth/repository/UserRoleRepository.java
  </write_files>
  <action>
    创建 infrastructure/mysql/auth/repository/ 包，新建三个 Spring Data JPA Repository：
    - UserAccountRepository: findByUsername(String) → Optional, existsByUsername(String) → boolean
    - RoleRepository: findByCode(String) → Optional, findByCodeIn(List) → List（查询所有指定角色）
    - UserRoleRepository: findByUserId(Long) → List, deleteByUserId(Long) → void（@Transactional @Modifying）
    使用方法名派生（对齐项目规范），不写 @Query。
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；三个 Repository 接口方法签名可用</done>
  <depends_on>T05</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>JWT 工具 — JwtProperties + JwtTokenProvider</name>
  <read_files>
    src/main/java/com/graphnexus/common/config/OpenApiConfig.java（参考 @ConfigurationProperties 用法）
    .specs/adr/ADR-037-jwt-dual-token-rolling-refresh.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/config/JwtProperties.java
    src/main/java/com/graphnexus/common/security/JwtTokenProvider.java
  </write_files>
  <action>
    JwtProperties: @ConfigurationProperties("jwt")，字段 secret / accessTokenTtl(Duration) / refreshTokenTtl(Duration)，record 或 @Data 类。

    JwtTokenProvider: 构造器注入 JwtProperties。
    - generateAccessToken(UserPrincipal): 用 jjwt HS256 签名 → payload {sub(username), userId, roles, iat, exp}
    - parseAccessToken(String token): 解析 Claims → 提取 userId/username/roles → 构建 UserPrincipal（不含 status，status 由 Filter 从 Redis/MySQL 查）
    - validateToken(String token): 签名+过期校验，抛异常则无效
    - getUserId(Claims): 提取 userId
    不在此类中查数据库/Redis。
  </action>
  <verify>./mvnw test-compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；JwtTokenProvider 可生成和解析 JWT</done>
  <depends_on>T02, T03</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>UserPrincipal + data.sql 角色预置</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    .specs/adr/ADR-038-rbac-role-permission-model.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/security/UserPrincipal.java
    src/main/resources/data.sql
  </write_files>
  <action>
    UserPrincipal: 实现 org.springframework.security.core.userdetails.UserDetails。
    字段：userId(Long), username(String), password(String), realName(String), status(String), roles(List&lt;String&gt;)。
    getAuthorities(): roles 转 GrantedAuthority（"ROLE_" + code）。
    **ADMIN 继承**: 若 roles 含 "ADMIN" → 自动追加 "ROLE_TEACHER"（见 ADR-038 Decision）。
    isEnabled(): return "ENABLED".equals(status)。
    其他 UserDetails 方法按需实现。

    data.sql: INSERT IGNORE INTO role 预置 5 行：
    (1, 'ADMIN', '超级管理员', '...'),
    (2, 'TEACHER', '教师', '...'),
    (3, 'STUDENT', '学生', '...'),
    (4, 'OPS_STAFF', '运维人员', '...'),
    (5, 'OPS_MANAGER', '运营人员', '...')
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；UserPrincipal.getAuthorities() 为 ADMIN 自动包含 ROLE_TEACHER；data.sql 含 5 行角色</done>
  <depends_on>T05</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>GlobalExceptionHandler — 新增认证异常处理</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
  </write_files>
  <action>
    在 GlobalExceptionHandler 中新增：
    - @ExceptionHandler(AccessDeniedException.class) → 403 + ErrorCode.A0030，WARN 日志
    - @ExceptionHandler(AuthenticationException.class) → 401 + ErrorCode.A0025，WARN 日志
    遵循既有分级日志策略（业务异常 WARN 级，不含完整堆栈）。
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；两个新异常处理器已追加</done>
  <depends_on>T04</depends_on>
</task>
```

### Wave 3 — 安全核心配置（2 并行 · 依赖 Wave 2）

```xml
<task id="T10" parallel="false" status="pending">
  <name>SecurityConfig + RedisConfig — Spring Security 过滤器链 + Redis 序列化</name>
  <read_files>
    src/main/java/com/graphnexus/common/config/OpenApiConfig.java（参考 @Configuration 模式）
    .specs/user-auth-rbac/DESIGN.md D3/D8/D9
    .specs/adr/ADR-039-redis-user-cache-fallback.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/config/SecurityConfig.java
    src/main/java/com/graphnexus/common/config/RedisConfig.java
  </write_files>
  <action>
    SecurityConfig:
    - @EnableMethodSecurity(prePostEnabled = true) 开启 @PreAuthorize
    - SecurityFilterChain bean: 所有 /api/v1/** authenticated，/api/v1/auth/login + /api/v1/auth/refresh permitAll，/actuator/** permitAll
    - 注入 JwtAuthenticationFilter，放在 UsernamePasswordAuthenticationFilter 之前
    - 禁用 CSRF、禁用 Session（SessionCreationPolicy.STATELESS）
    - BCryptPasswordEncoder bean（strength=10）
    - CORS 配置允许所有 origin（v1 内网部署）
    - 不配置 formLogin / httpBasic

    RedisConfig:
    - StringRedisTemplate bean
    - RedisSerializer 使用 Jackson2JsonRedisSerializer（或 String 序列化即可，因为值存 JSON 字符串）
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；SecurityFilterChain 配置完整，/login /refresh permitAll，其余需认证</done>
  <depends_on>T07, T08</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>JwtAuthenticationFilter + TokenService — 请求鉴权 + Redis Token 管理</name>
  <read_files>
    .specs/user-auth-rbac/DESIGN.md §2.2（鉴权流）
    .specs/adr/ADR-037-jwt-dual-token-rolling-refresh.md
    .specs/adr/ADR-039-redis-user-cache-fallback.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/security/JwtAuthenticationFilter.java
    src/main/java/com/graphnexus/application/auth/service/TokenService.java
    src/main/java/com/graphnexus/application/auth/service/impl/TokenServiceImpl.java
    src/main/java/com/graphnexus/application/auth/model/TokenPair.java
  </write_files>
  <action>
    JwtAuthenticationFilter (OncePerRequestFilter):
    1. 检查 skip 路径（/login /refresh）→ 放行
    2. 提取 Authorization: Bearer <token>
    3. JwtTokenProvider.parseAccessToken → userId/username/roles
    4. 查 Redis user:auth:<userId> → 命中解析 status + roles；未命中 → 查 MySQL user_account JOIN user_role → 写 Redis
    5. 校验 status==ENABLED（禁用 → 403 A0024）
    6. 构建 UserPrincipal → UsernamePasswordAuthenticationToken → SecurityContext
    7. Redis 异常 catch → WARN 日志 → 回退查 MySQL

    TokenService + Impl:
    - generateTokenPair(UserPrincipal): 生成 AT + RT(UUID) → 写 Redis refresh:<uuid> (值=userId, TTL 7d) → 返回 TokenPair(AT, RT, expiresIn)
    - refreshAccessToken(String refreshToken): Redis GET refresh:<uuid> → DEL 旧 key → 生成新 TokenPair → SETEX 新 key → 更新 user:auth: TTL
    - revokeRefreshToken(String refreshToken): Redis DEL refresh:<uuid>
    - getCachedUser(Long userId): Redis GET user:auth:<userId> → 解析 JSON 或 null
    - cacheUser(Long userId, String json, Duration ttl): Redis SETEX
    - evictUserCache(Long userId): Redis DEL
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；Filter 逻辑完整（鉴权→查缓存/DB→构建SecurityContext）；TokenService 含生成/刷新/撤销/缓存</done>
  <depends_on>T06, T07, T08, T10</depends_on>
</task>
```

### Wave 4 — 认证业务层（3 并行 · 依赖 Wave 3）

```xml
<task id="T12" parallel="true" status="pending">
  <name>AuthService — 登录 + 用户 CRUD + Redis 缓存编排</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/service/impl/（参考 Service 实现模式）
    .specs/user-auth-rbac/DESIGN.md §2.1（登录流）
    .specs/user-auth-rbac/REQUIREMENT.md AC-1~3, AC-11~15
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/auth/service/AuthService.java
    src/main/java/com/graphnexus/application/auth/service/impl/AuthServiceImpl.java
    src/main/java/com/graphnexus/application/auth/model/UserInfo.java
  </write_files>
  <action>
    AuthService 接口：
    - login(LoginRequest): 查 UserAccount → BCrypt 校验 → 查 roles → 校验 status → 缓存到 Redis → 调用 TokenService 生成 TokenPair → 返回 LoginResponse
    - createUser(CreateUserRequest): 校验 username 不重复 → BCrypt 加密密码 → save user_account → 查 role 表 → save user_role 关联
    - updateUser(Long id, UpdateUserRequest): 查用户 → 更新字段 → 如有角色变更则 delete old + save new user_role → evictUserCache
    - getUsers(pageNum, pageSize): 分页查 user_account + 每用户查 roles → 组装 UserVO 列表
    - getRoles(): 查 role 表 → 返回 code+name 列表

    AuthServiceImpl: 构造器注入 UserAccountRepository + RoleRepository + UserRoleRepository + PasswordEncoder + TokenService + StringRedisTemplate。

    UserInfo BO: username, realName, status, roles[]（从缓存/DB 构建）。
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；login/createUser/updateUser/getUsers/getRoles 方法签名完整</done>
  <depends_on>T06, T11</depends_on>
</task>

<task id="T13" parallel="true" status="pending">
  <name>AuthController + DTOs — 认证 API 端点 + 用户管理端点</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java（参考 Controller 模式）
    .specs/user-auth-rbac/REQUIREMENT.md 端点-角色映射矩阵
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/auth/controller/AuthController.java
    src/main/java/com/graphnexus/api/auth/dto/LoginRequest.java
    src/main/java/com/graphnexus/api/auth/dto/LoginResponse.java
    src/main/java/com/graphnexus/api/auth/dto/CreateUserRequest.java
    src/main/java/com/graphnexus/api/auth/dto/UpdateUserRequest.java
    src/main/java/com/graphnexus/api/auth/dto/UserVO.java
    src/main/java/com/graphnexus/api/auth/dto/RefreshRequest.java
  </write_files>
  <action>
    创建 api/auth/ 包，新建 AuthController：
    - POST /api/v1/auth/login → AuthService.login → LoginResponse
    - POST /api/v1/auth/refresh → TokenService.refreshAccessToken → LoginResponse
    - POST /api/v1/auth/logout → TokenService.revokeRefreshToken → ApiResult
    - GET /api/v1/auth/users → @PreAuthorize("hasRole('ADMIN')") → AuthService.getUsers → PageResult
    - POST /api/v1/auth/users → @PreAuthorize("hasRole('ADMIN')") → AuthService.createUser → ApiResult
    - PUT /api/v1/auth/users/{id} → @PreAuthorize("hasRole('ADMIN')") → AuthService.updateUser → ApiResult
    - GET /api/v1/auth/roles → authenticated → AuthService.getRoles → ApiResult

    DTO: LoginRequest(username, password) / LoginResponse(accessToken, refreshToken, expiresIn, userInfo) / CreateUserRequest(username, password, realName, roles[]) / UpdateUserRequest(realName, roles[], status) / UserVO(id, username, realName, roles[], status, createTime) / RefreshRequest(refreshToken)。
  </action>
  <verify>./mvnw compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>编译通过；7 个端点完整，DTO 字段对齐 REQUIREMENT AC</done>
  <depends_on>T12</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>错误响应增强 — 确保 GlobalExceptionHandler 返回正确 errorCode</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
    .specs/user-auth-rbac/REQUIREMENT.md AC-2~10（各错误场景的错误码）
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
  </write_files>
  <action>
    在 GlobalExceptionHandler 中确认（T09 已加）：
    - AccessDeniedException → A0030 403（已加）
    - AuthenticationException / BadCredentialsException → A0023 401
    - DisabledException（Spring Security 内置）→ A0024 403
    - SignatureException / ExpiredJwtException / MalformedJwtException → A0026 401
    确保所有异常处理返回的 ApiResult 中 errorCode 与 REQUIREMENT AC 对齐。
  </action>
  <verify>grep -c "AccessDeniedException\|BadCredentialsException\|DisabledException\|SignatureException\|ExpiredJwtException" src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java</verify>
  <done>所有认证相关异常类型都有对应的 @ExceptionHandler</done>
  <depends_on>T09</depends_on>
</task>
```

### Wave 5 — 全端点权限收敛（3 并行 · 依赖 Wave 4）

```xml
<task id="T15" parallel="true" status="pending">
  <name>@PreAuthorize — TextbookController + GradeController</name>
  <read_files>
    src/main/java/com/graphnexus/api/file/controller/TextbookController.java
    src/main/java/com/graphnexus/api/file/controller/GradeController.java
    .specs/adr/ADR-040-endpoint-role-mapping-full-convergence.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/file/controller/TextbookController.java
    src/main/java/com/graphnexus/api/file/controller/GradeController.java
  </write_files>
  <action>
    TextbookController: 类级 @PreAuthorize("hasRole('TEACHER')")。
    方法级：POST /upload 继承类级 · POST /parse/{id} @PreAuthorize("hasRole('ADMIN')") · DELETE /{id} @PreAuthorize("hasRole('ADMIN')")。
    GradeController: 类级 @PreAuthorize("hasRole('TEACHER')")。
    方法级：POST /upload 继承类级 · DELETE /exam/{examNo} @PreAuthorize("hasRole('ADMIN')")。
    导入 org.springframework.security.access.prepost.PreAuthorize。
  </action>
  <verify>grep -c "@PreAuthorize" src/main/java/com/graphnexus/api/file/controller/TextbookController.java && grep -c "@PreAuthorize" src/main/java/com/graphnexus/api/file/controller/GradeController.java</verify>
  <done>两个 Controller 均含类级 + 方法级 @PreAuthorize；映射对齐 ADR-040</done>
  <depends_on>T10, T13</depends_on>
</task>

<task id="T16" parallel="true" status="pending">
  <name>@PreAuthorize — ConstructionController + MetricsController</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
    .specs/adr/ADR-040-endpoint-role-mapping-full-convergence.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
  </write_files>
  <action>
    ConstructionController: 类级 @PreAuthorize("hasRole('TEACHER')")。
    方法级：POST /extract/{documentId} @PreAuthorize("hasRole('ADMIN')") · GET 类方法继承类级。
    MetricsController: 类级 @PreAuthorize("hasAnyRole('ADMIN','OPS_STAFF','OPS_MANAGER')")。无方法级例外。
  </action>
  <verify>grep -c "@PreAuthorize" src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java && grep -c "@PreAuthorize" src/main/java/com/graphnexus/api/graph/controller/MetricsController.java</verify>
  <done>两个 Controller 权限注解完整；映射对齐 ADR-040</done>
  <depends_on>T10, T13</depends_on>
</task>

<task id="T17" parallel="true" status="pending">
  <name>@PreAuthorize — AnalysisController + FusionController + QueryController + LlmController</name>
  <read_files>
    src/main/java/com/graphnexus/api/analysis/controller/AnalysisController.java
    src/main/java/com/graphnexus/api/analysis/controller/FusionController.java
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
    src/main/java/com/graphnexus/api/llm/controller/LlmController.java
    .specs/adr/ADR-040-endpoint-role-mapping-full-convergence.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/analysis/controller/AnalysisController.java
    src/main/java/com/graphnexus/api/analysis/controller/FusionController.java
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
    src/main/java/com/graphnexus/api/llm/controller/LlmController.java
  </write_files>
  <action>
    AnalysisController: 类级 @PreAuthorize("hasRole('TEACHER')") · 方法 GET /subgraph/{taskId} @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")。
    FusionController: 类级 @PreAuthorize("hasAnyRole('ADMIN','OPS_STAFF')") · POST /rollback/{id} @PreAuthorize("hasRole('ADMIN')")。
    QueryController: 类级 @PreAuthorize("hasRole('TEACHER')") · POST /chat + GET /result/{taskId} @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')") · DELETE /history/{taskId} @PreAuthorize("hasRole('ADMIN')")。
    LlmController: 类级 @PreAuthorize("hasAnyRole('ADMIN','OPS_STAFF')")。无方法级例外。
  </action>
  <verify>for f in AnalysisController FusionController QueryController LlmController; do grep -c "@PreAuthorize" $(find src -name "$f.java") ; done</verify>
  <done>四个 Controller 权限注解完整；映射对齐 ADR-040</done>
  <depends_on>T10, T13</depends_on>
</task>
```

### Wave 6 — 后端测试（3 并行 · 依赖 Wave 5）

```xml
<task id="T18" parallel="true" status="pending">
  <name>JwtTestHelper + TestSecurityConfig — 测试基础设施</name>
  <read_files>
    .specs/user-auth-rbac/DESIGN.md D11
    src/test/java/com/graphnexus/（参考现有测试包结构）
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/common/security/JwtTestHelper.java
    src/test/java/com/graphnexus/common/config/TestSecurityConfig.java
  </write_files>
  <action>
    JwtTestHelper: 静态工具方法
    - generateTestToken(String username, String... roles): 用与生产相同的 JwtTokenProvider 生成测试用 Token
    - createAuthHeader(String token): 返回 "Bearer " + token

    TestSecurityConfig: @TestConfiguration + @Profile("dev")
    - 覆盖 SecurityFilterChain：允许所有请求（保留 SecurityContext 注入但不校验 Token）
    - 注入测试用 UserPrincipal 到 SecurityContext
    - 用于已有集成测试不因加 Security 而失败
  </action>
  <verify>./mvnw test-compile -pl . 2>&1 | grep -q "BUILD SUCCESS"</verify>
  <done>两个测试工具类编译通过；可被其他测试类 import</done>
  <depends_on>T07, T10</depends_on>
</task>

<task id="T19" parallel="true" status="pending">
  <name>AuthService + AuthController 单元/集成测试</name>
  <read_files>
    src/test/java/com/graphnexus/（参考现有测试模式）
    .specs/user-auth-rbac/REQUIREMENT.md AC-1~16
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/auth/service/AuthServiceTest.java
    src/test/java/com/graphnexus/api/auth/controller/AuthControllerTest.java
  </write_files>
  <action>
    AuthServiceTest（单元测试 · Mock Repository + TokenService）：
    - loginSuccess: 正确密码→返回TokenPair
    - loginFailWrongPassword: 错误密码→抛 BusinessException(A0023)
    - loginFailDisabled: disabled用户→抛 BusinessException(A0024)
    - createUserSuccess: 创建成功→save被调用
    - createUserDuplicate: 重复用户名→抛 BusinessException(A0028)
    - updateUserRoleThenCacheEvicted: 更新角色→evictUserCache被调用

    AuthControllerTest（@SpringBootTest + @AutoConfigureMockMvc + TestSecurityConfig）：
    - login 200/401/403 三个场景
    - refresh 200/401
    - getUsers 403（非 ADMIN 访问被拒）
  </action>
  <verify>./mvnw test -Dtest="AuthServiceTest,AuthControllerTest" -pl . 2>&1 | grep -E "Tests run.*Failures: 0"</verify>
  <done>所有认证核心场景测试通过（AC-1~16 覆盖）</done>
  <depends_on>T12, T13, T18</depends_on>
</task>

<task id="T20" parallel="false" status="pending">
  <name>全量回归测试 — mvn test（含权限注解）</name>
  <read_files>
    （无需指定 · 全项目范围）
  </read_files>
  <write_files>
    （不新增文件，可能修改既有测试以适配 Security 上下文）
  </write_files>
  <action>
    运行全量 mvn test：
    1. 检查所有既有测试是否因 Security 开启而失败
    2. 对失败的测试：在测试类上加 @Import(TestSecurityConfig.class) 或使用 @WithMockUser
    3. 确保有一个带真实 Security 链的测试类做权限回归（如 AuthControllerTest）
    4. 修复所有编译错误和测试失败
  </action>
  <verify>./mvnw test -pl . 2>&1 | grep -E "Tests run.*Failures: 0" | head -5</verify>
  <done>全量测试 0 失败；既有测试适配完成；权限回归测试通过</done>
  <depends_on>T15, T16, T17, T19</depends_on>
</task>
```

### Wave 7 — 前端基础（2 并行 · 可提前到 Wave 5 开始）

```xml
<task id="T21" parallel="true" status="pending">
  <name>前端 — router 更新 + authStore + auth API 模块</name>
  <read_files>
    frontend/src/router/index.ts
    frontend/src/common/components/AppLayout.vue
    .specs/user-auth-rbac/UI-DESIGN.md §6、§10
    frontend/src/api/（参考既有 API 模块模式）
  </read_files>
  <write_files>
    frontend/src/router/index.ts
    frontend/src/stores/authStore.ts
    frontend/src/api/auth.ts
  </write_files>
  <action>
    router/index.ts:
    - 新增路由: { path: '/login', component: LoginPage, meta: { guest: true } }
    - 新增路由: { path: '/settings/users', component: UserManagePage, meta: { roles: ['ADMIN'] } }
    - 新增路由: { path: '/403', component: ForbiddenPage }

    authStore.ts (Pinia):
    - state: accessToken, refreshToken, userInfo, isAuthenticated
    - actions: login(), refreshToken(), logout(), restoreSession()（从 localStorage 恢复）
    - getters: hasRole(role), userName, userInitial
    - Token 读写 localStorage

    auth.ts (API):
    - login(username, password) → POST /api/v1/auth/login
    - refresh(refreshToken) → POST /api/v1/auth/refresh
    - logout(refreshToken) → POST /api/v1/auth/logout
    - getUsers(params) → GET /api/v1/auth/users
    - createUser(data) → POST /api/v1/auth/users
    - updateUser(id, data) → PUT /api/v1/auth/users/{id}
    - getRoles() → GET /api/v1/auth/roles
  </action>
  <verify>cd frontend && npx vue-tsc -b --noEmit 2>&1 | grep -c "error" | xargs -I{} sh -c 'test {} -eq 0'</verify>
  <done>vue-tsc 0 错误；router 含 /login /settings/users /403；authStore 含全部 state/actions/getters</done>
  <depends_on></depends_on>
</task>

<task id="T22" parallel="true" status="pending">
  <name>前端 — authGuard + axios interceptor（Token 刷新）</name>
  <read_files>
    frontend/src/router/index.ts
    frontend/src/stores/authStore.ts
    .specs/user-auth-rbac/UI-DESIGN.md §6（路由守卫行为）
    .specs/user-auth-rbac/DESIGN.md §2.3（刷新流）
  </read_files>
  <write_files>
    frontend/src/router/authGuard.ts
    frontend/src/api/interceptor.ts
  </write_files>
  <action>
    authGuard.ts:
    - router.beforeEach: 无 Token + 非 guest 路由 → /login?redirect=原路径
    - router.beforeEach: 有 Token + guest 路由(/login) → /materials
    - router.beforeEach: 有 Token + meta.roles 不匹配 → /403
    - 从 authStore 读取状态，不在守卫中直接读 localStorage

    interceptor.ts:
    - axios request interceptor: 从 authStore 取 accessToken → 设置 Authorization header
    - axios response interceptor: 收到 401 + errorCode=A0025(过期) → 尝试 authStore.refreshToken() → 成功则重放原请求
    - **刷新锁**: 同一时刻仅一个 refresh 请求（用 Promise 单例），其他 401 请求等待锁释放后共用新 Token
    - refresh 也失败 → authStore.logout() → router.push('/login')
    - 在 main.ts 中注册 interceptor
  </action>
  <verify>cd frontend && npx vue-tsc -b --noEmit 2>&1 | grep -c "error" | xargs -I{} sh -c 'test {} -eq 0'</verify>
  <done>vue-tsc 0 错误；路由守卫逻辑完整；axios interceptor 含刷新锁</done>
  <depends_on>T21</depends_on>
</task>
```

### Wave 8 — 前端页面（3 并行 · 依赖 Wave 7）

```xml
<task id="T23" parallel="true" status="pending">
  <name>LoginPage.vue — 登录页面</name>
  <read_files>
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/BaseInput.vue
    frontend/src/assets/tokens.css
    .specs/user-auth-rbac/UI-DESIGN.md §6（LoginPage 规约）
  </read_files>
  <write_files>
    frontend/src/views/auth/LoginPage.vue
  </write_files>
  <action>
    按 UI-DESIGN §6 规约实现。
    全屏居中布局（无 AppLayout）。
    360px 宽卡片：GraphNexus 品牌文字(.display) + NInput 用户名 + NInput 密码(type=password) + BaseButton primary 全宽 "登录" + 条件错误提示(.supporting color error)。
    Enter 键提交；登录中 loading+disabled；失败显示错误提示淡入。
    成功 → authStore.login() → router.push(redirect || '/materials')。
    guest 路由 meta 防止已登录用户访问。
  </action>
  <verify>cd frontend && npx vue-tsc -b --noEmit 2>&1 | grep -c "error" | xargs -I{} sh -c 'test {} -eq 0'</verify>
  <done>vue-tsc 0 错误；登录页 UI 对齐规约；支持 Enter 提交 + loading + 错误提示</done>
  <depends_on>T21, T22</depends_on>
</task>

<task id="T24" parallel="true" status="pending">
  <name>AppLayout.vue — 菜单按角色过滤 + 顶栏用户头像/下拉</name>
  <read_files>
    frontend/src/common/components/AppLayout.vue
    frontend/src/stores/authStore.ts
    .specs/user-auth-rbac/UI-DESIGN.md §6（顶栏用户区规约）
  </read_files>
  <write_files>
    frontend/src/common/components/AppLayout.vue
  </write_files>
  <action>
    菜单过滤：
    - navItems 每项加 roles 字段（如 教材管理→['ADMIN','TEACHER']，学情诊断→['ADMIN','TEACHER','STUDENT']）
    - settingsItems 加角色过滤 + 新增 { path: '/settings/users', label: '用户管理', icon: Users, roles: ['ADMIN'] }
    - 用 authStore.hasRole() 过滤可见菜单项

    顶栏用户区：
    - 替换空白 avatar → 首字母圆形（24×24px, brand-veil 背景 + brand 文字）
    - 右侧显示 username（supporting 类）
    - NPopover: 点击展开 → 显示用户名 + 角色 + "登出" 文字按钮
    - 登出 → authStore.logout() → router.push('/login')
  </action>
  <verify>cd frontend && npx vue-tsc -b --noEmit 2>&1 | grep -c "error" | xargs -I{} sh -c 'test {} -eq 0'</verify>
  <done>vue-tsc 0 错误；菜单按角色显示/隐藏；顶栏用户头像+下拉可用</done>
  <depends_on>T21</depends_on>
</task>

<task id="T25" parallel="false" status="pending">
  <name>UserManagePage.vue + ForbiddenPage.vue — 用户管理页 + 403 页</name>
  <read_files>
    frontend/src/common/components/BaseCard.vue
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/DataTable.vue
    frontend/src/common/components/StatusBadge.vue
    frontend/src/views/file/FileManagePage.vue（参考 NModal + NDataTable 模式）
    .specs/user-auth-rbac/UI-DESIGN.md §6（UserManagePage + 403 规约）
  </read_files>
  <write_files>
    frontend/src/views/auth/UserManagePage.vue
    frontend/src/views/auth/ForbiddenPage.vue
  </write_files>
  <action>
    UserManagePage.vue:
    - BaseCard 包裹 · 顶部 BaseButton "新建用户"
    - NDataTable: 用户名/真实姓名/角色(NTag 彩色)/状态(StatusBadge)/创建时间/操作(编辑▶)
    - 角色 NTag 颜色按 UI-DESIGN §3 映射（ADMIN 蓝 / TEACHER 绿 / STUDENT 灰 / OPS_STAFF 橙 / OPS_MANAGER 紫）
    - 新建/编辑 NModal(480px): 用户名 NInput / 真实姓名 NInput / 密码 NInput(type=password) / 角色 NSelect multiple / 状态 NSwitch · 取消+确认 BaseButton
    - Escape 关闭弹窗；分页器 NPagination

    ForbiddenPage.vue:
    - AppLayout 壳内 · 居中 ShieldOff icon(48px, tertiary) + "403" (.display) + "您没有权限访问此页面" (.body-lead) + BaseButton "返回首页"
  </action>
  <verify>cd frontend && npx vue-tsc -b --noEmit 2>&1 | grep -c "error" | xargs -I{} sh -c 'test {} -eq 0'</verify>
  <done>vue-tsc 0 错误；用户管理页含新建/编辑弹窗；角色标签彩色显示；403 页布局完整</done>
  <depends_on>T21, T24</depends_on>
</task>
```

### Wave 9 — 前端集成验证（1 串行 · 依赖 Wave 8）

```xml
<task id="T26" parallel="false" status="pending">
  <name>前端集成验证 — vue-tsc + vite build + 端到端冒烟</name>
  <read_files>
    （全前端项目范围）
  </read_files>
  <write_files>
    （不新增文件）
  </write_files>
  <action>
    1. vue-tsc 类型检查 0 错误
    2. vite build 无报错
    3. 手动冒烟测试（可选自动化）：
       - 访问 /login → 看到登录页
       - 输入正确密码 → 跳转 /materials
       - 侧边栏含"用户管理"（ADMIN）
       - 访问 /settings/users → 看到用户管理页
       - 创建用户 → 弹窗操作 → 列表刷新
  </action>
  <verify>cd frontend && npx vue-tsc -b --noEmit 2>&1 | tail -1 && npx vite build 2>&1 | tail -5</verify>
  <done>vue-tsc 0 错误；vite build 成功；冒烟测试全通过</done>
  <depends_on>T23, T24, T25</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中
- `status="done"` — 已完成
- `status="blocked"` — 阻塞

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

```xml
<!-- 占位 -->
```