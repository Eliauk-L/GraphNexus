# REQUIREMENT: 用户认证与角色权限管理

- **Change ID**: `user-auth-rbac`
- **关联**: `@.specs/user-auth-rbac/CHANGE.md`、`@.specs/CONTEXT.md`、`@docs/design-view/use-case-view/use-case-view.md` §2
- **角色**: 5 类（超级管理员 ADMIN / 教师 TEACHER / 学生 STUDENT / 运维人员 OPS_STAFF / 运营人员 OPS_MANAGER）
- **管理员继承**: ADMIN 泛化继承 TEACHER 全部用例（对齐用例视图 §1.3）

---

## 用户故事

- **US-1（认证）**：作为系统用户，我想使用用户名和密码登录系统获取 JWT Token，以便后续请求被系统识别身份
- **US-2（用户管理）**：作为超级管理员，我想在后台创建、编辑、启用/禁用用户账号，以便控制谁能访问系统
- **US-3（角色分配）**：作为超级管理员，我想为用户分配一个或多个角色，以便不同用户获得不同的功能访问权限
- **US-4（Token 刷新）**：作为已登录用户，我期望 Access Token 过期时系统自动用 Refresh Token 续期，以便我不必频繁重新登录
- **US-5（Redis 缓存）**：作为系统，我想将用户信息与角色权限缓存到 Redis，以便权限校验不每次都查 MySQL
- **US-6（端点权限）**：作为系统，我想根据请求携带的 JWT 中角色信息限制 API 访问，以便无权限用户无法调用敏感接口
- **US-7（前端认证闭环）**：作为前端用户，未登录时自动跳转登录页，登录后按角色看到不同菜单，Token 过期自动刷新或跳回登录页

---

## 验收准则（AC）

### AC-1 · 用户登录成功

- **Given** 数据库中存在已启用的用户 `test_admin`，密码为 `Pass@123`，角色为 `ADMIN`
- **When** 客户端 `POST /api/v1/auth/login` 携带 `{"username":"test_admin","password":"Pass@123"}`
- **Then** 返回 HTTP 200，响应体含 `accessToken`（JWT）、`refreshToken`、`expiresIn`（秒）、`userInfo`（用户名 + 角色列表）
- **验证方式**: `curl -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"test_admin","password":"Pass@123"}' | jq .`

### AC-2 · 登录失败（密码错误）

- **Given** 用户 `test_admin` 存在且密码为 `Pass@123`
- **When** 客户端 `POST /api/v1/auth/login` 携带错误密码 `WrongPass`
- **Then** 返回 HTTP 401，`errorCode=A0100`，`userTip="用户名或密码错误"`
- **验证方式**: curl 验证响应码 401 + errorCode

### AC-3 · 登录失败（用户已禁用）

- **Given** 用户 `disabled_user` 存在但 `status=DISABLED`
- **When** 客户端 `POST /api/v1/auth/login` 携带正确密码
- **Then** 返回 HTTP 403，`errorCode=A0101`，`userTip="账号已被禁用，请联系管理员"`
- **验证方式**: curl 验证响应码 403 + errorCode

### AC-4 · Access Token 访问受保护端点

- **Given** 用户已登录并获得 `accessToken`
- **When** 客户端 `GET /api/v1/file/textbooks` 携带 `Authorization: Bearer <accessToken>`
- **Then** 返回 HTTP 200 + 正常分页数据
- **验证方式**: curl 验证正常访问

### AC-5 · 无 Token 访问受保护端点

- **Given** 客户端未携带 Authorization header
- **When** `GET /api/v1/file/textbooks`
- **Then** 返回 HTTP 401，`errorCode=A0102`，`userTip="未登录或 Token 已过期"`
- **验证方式**: curl 验证 HTTP 401（所有 `/api/v1/*` 端点统一行为）

### AC-6 · 无效 Token 被拒绝

- **Given** 客户端携带伪造/篡改的 JWT
- **When** `GET /api/v1/file/textbooks` 携带 `Authorization: Bearer <invalid>`
- **Then** 返回 HTTP 401，`errorCode=A0103`，`userTip="Token 无效"`
- **验证方式**: curl 携带 `Bearer invalidtokenstring` 验证 401

### AC-7 · Refresh Token 刷新 Access Token

- **Given** 用户已登录，持有的 `accessToken` 已过期（或即将过期），但 `refreshToken` 仍有效
- **When** 客户端 `POST /api/v1/auth/refresh` 携带 `{"refreshToken":"<refresh_token>"}`
- **Then** 返回 HTTP 200，响应体含新的 `accessToken`、`refreshToken`（滚动刷新）、`expiresIn`
- **验证方式**: 等待 accessToken 过期后调用 refresh 端点验证新 token 可用

### AC-8 · Refresh Token 过期/无效

- **Given** `refreshToken` 已过期或被篡改
- **When** `POST /api/v1/auth/refresh` 携带无效 refreshToken
- **Then** 返回 HTTP 401，`errorCode=A0104`，`userTip="登录已过期，请重新登录"`
- **验证方式**: curl 携带过期 token 验证 401

### AC-9 · 用户登出

- **Given** 用户已登录
- **When** 客户端 `POST /api/v1/auth/logout` 携带 `Authorization: Bearer <accessToken>` + body `{"refreshToken":"<refresh_token>"}`
- **Then** 返回 HTTP 200，Refresh Token 在 Redis 中被标记失效，后续 refresh 请求被拒绝
- **验证方式**: 登出后调用 refresh 端点验证 401

### AC-10 · 角色无权访问端点

- **Given** 用户角色为 `STUDENT`（无文件管理权限）
- **When** `POST /api/v1/file/textbooks/upload` 携带有效 Token
- **Then** 返回 HTTP 403，`errorCode=A0200`，`userTip="权限不足，无法访问此资源"`
- **验证方式**: 学生 Token 调用管理员端点验证 403

### AC-11 · 管理员创建用户

- **Given** 管理员已登录
- **When** `POST /api/v1/auth/users` 携带 `{"username":"new_teacher","password":"Temp@123","realName":"张老师","roles":["TEACHER"]}`
- **Then** 返回 HTTP 200，用户创建成功，`user_account` 表新增记录（密码 BCrypt 加密存储），`user_role` 表新增对应关联
- **验证方式**: curl 创建后查数据库验证 + 新用户可登录

### AC-12 · 创建用户时用户名重复

- **Given** 用户 `existing_user` 已存在
- **When** 管理员 `POST /api/v1/auth/users` 携带 `{"username":"existing_user",...}`
- **Then** 返回 HTTP 409，`errorCode=A0105`，`userTip="用户名已存在"`
- **验证方式**: curl 验证 409

### AC-13 · 管理员编辑用户

- **Given** 用户 `target_user` 存在
- **When** 管理员 `PUT /api/v1/auth/users/{id}` 携带 `{"realName":"新名字","roles":["TEACHER","OPS_MANAGER"],"status":"ENABLED"}`
- **Then** 返回 HTTP 200，用户信息更新，旧角色关联清除替换为新角色，Redis 中该用户缓存立即失效
- **验证方式**: 更新后查数据库 + 旧缓存 key 不存在于 Redis

### AC-14 · 管理员禁用用户

- **Given** 用户 `target_user` 当前为 `ENABLED` 状态
- **When** 管理员将其 `status` 改为 `DISABLED`
- **Then** 该用户已有的有效 Token 在下一次请求时被拒绝（JWT 中 status 校验失败），返回 HTTP 403 `A0101`
- **验证方式**: 禁用后立即用该用户 Token 访问 API 验证 403

### AC-15 · 管理员查看用户列表

- **Given** 系统中存在多个用户
- **When** 管理员 `GET /api/v1/auth/users?pageNum=1&pageSize=10`
- **Then** 返回分页列表，每项含 `id`、`username`、`realName`、`roles`（角色名数组）、`status`、`createTime`，不返回 `password`
- **验证方式**: curl 分页查询验证返回结构

### AC-16 · 非管理员无法访问用户管理 API

- **Given** 用户角色为 `TEACHER`（非 ADMIN）
- **When** `GET /api/v1/auth/users` 或 `POST /api/v1/auth/users`
- **Then** 返回 HTTP 403
- **验证方式**: 教师 Token 验证所有 `/api/v1/auth/users*` 端点 403

### AC-17 · Redis 缓存用户权限

- **Given** 用户已登录，Redis 服务正常运行
- **When** 用户首次访问任意受保护 API
- **Then** Redis 中出现 key 为 `user:auth:<userId>` 的缓存条目，含用户基本信息 + 角色列表，TTL = 配置值（默认 30min）；后续请求命中缓存不查 MySQL `user_account`/`user_role` 表
- **验证方式**: `redis-cli GET user:auth:<userId>` 有值 + 第二次请求时 MySQL 慢查询日志无相关 SELECT

### AC-18 · 用户信息变更时缓存失效

- **Given** Redis 中存在 `user:auth:<userId>` 缓存
- **When** 管理员修改该用户的角色或禁用该用户
- **Then** Redis 中 `user:auth:<userId>` key 被删除（或标记失效），下次该用户请求触发重新查库
- **验证方式**: 修改后 `redis-cli EXISTS user:auth:<userId>` 返回 0

### AC-19 · Redis 不可用时系统降级可用

- **Given** Redis 服务不可达（宕机/网络不通）
- **When** 用户携带有效 Token 访问受保护 API
- **Then** 系统降级为**仅查 MySQL**（不抛异常），功能正常但性能下降，日志 WARN 级别记录 Redis 不可用
- **验证方式**: `podman stop graphnexus-redis` 后 curl 验证 API 仍正常 200

### AC-20 · 前端路由守卫（未登录）

- **Given** 用户未登录（localStorage 无 Token）
- **When** 用户在浏览器地址栏直接输入任意页面 URL（如 `/knowledge-graph`）
- **Then** 自动跳转到 `/login`，不闪现目标页内容
- **验证方式**: 清空 localStorage → 访问 `/knowledge-graph` → 检查 URL 变为 `/login`

### AC-21 · 前端路由守卫（已登录）

- **Given** 用户已登录（localStorage 有有效 Token）
- **When** 用户访问 `/login`
- **Then** 自动跳转到 `/materials`（默认首页）
- **验证方式**: 已登录状态访问 `/login` → 跳转到 `/materials`

### AC-22 · 登录成功后回到目标页

- **Given** 用户未登录，正试图访问 `/diagnosis`
- **When** 被跳转到 `/login` 后输入正确用户名密码并登录成功
- **Then** 自动跳转回 `/diagnosis`（而非 `/materials`）
- **验证方式**: 未登录 → 访问 `/diagnosis` → 登录 → URL 变为 `/diagnosis`

### AC-23 · 前端菜单按角色过滤

- **Given** 用户角色为 `STUDENT`
- **When** 登录成功后进入主界面
- **Then** 侧边栏仅显示学情诊断（`/diagnosis`），不显示教材管理、成绩管理、融合管理、图指标
- **验证方式**: 学生账号登录后截图侧边栏

### AC-24 · 前端无权限页面

- **Given** 用户角色为 `STUDENT`，已登录
- **When** 在浏览器地址栏手动输入 `/settings/fusion`
- **Then** 页面显示 403 禁止访问提示（不是空白页），侧边栏保持可见但无高亮项
- **验证方式**: 学生登录后访问 `/settings/fusion` → 看到 403 提示

### AC-25 · Token 自动刷新（前端）

- **Given** 用户已登录，`accessToken` 即将过期
- **When** 前端发起任意 API 请求，收到 HTTP 401 + `errorCode=A0102`（Token 过期）
- **Then** axios interceptor 自动调用 `/api/v1/auth/refresh` 获取新 Token，用新 Token 重放原请求；用户无感知，页面正常加载
- **验证方式**: 缩短 accessToken 有效期 → 等待过期 → 页面操作不中断

### AC-26 · Token 刷新失败回登录页

- **Given** `accessToken` 过期且 `refreshToken` 也过期
- **When** axios interceptor 刷新请求返回 401
- **Then** 清除 localStorage Token，跳转 `/login`，提示"登录已过期，请重新登录"
- **验证方式**: 两个 Token 都过期后页面操作 → 跳转登录页

---

## 端点-角色映射矩阵

> v1 按「用例范围」映射。ADMIN 继承 TEACHER 全部端点。

| 端点路径 | 方法 | ADMIN | TEACHER | STUDENT | OPS_STAFF | OPS_MANAGER |
|----------|------|:-----:|:-------:|:-------:|:---------:|:-----------:|
| `/api/v1/auth/login` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/refresh` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/logout` | POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/users` | GET | ✅ | — | — | — | — |
| `/api/v1/auth/users` | POST | ✅ | — | — | — | — |
| `/api/v1/auth/users/{id}` | PUT | ✅ | — | — | — | — |
| `/api/v1/auth/roles` | GET | ✅ | — | — | — | — |
| `/api/v1/file/textbooks/*` | ALL | ✅ | ✅ | — | — | — |
| `/api/v1/file/grades/*` | ALL | ✅ | ✅ | — | — | — |
| `/api/v1/graph/construction/*` | GET | ✅ | ✅ | — | — | — |
| `/api/v1/graph/construction/extract/*` | POST | ✅ | — | — | — | — |
| `/api/v1/graph/construction/subjects` | GET | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/graph/metrics/*` | GET | ✅ | — | — | ✅ | ✅ |
| `/api/v1/analysis/fusion/*` | ALL | ✅ | — | — | ✅ | — |
| `/api/v1/analysis/subgraph/{taskId}` | GET | ✅ | ✅ | ✅ | — | — |
| `/api/v1/query/ask` | POST | ✅ | ✅ | — | — | — |
| `/api/v1/query/ask-async` | POST | ✅ | ✅ | — | — | — |
| `/api/v1/query/chat` | POST | ✅ | ✅ | ✅ | — | — |
| `/api/v1/query/result/{taskId}` | GET | ✅ | ✅ | ✅ | — | — |
| `/api/v1/query/history` | GET | ✅ | ✅ | — | — | — |
| `/api/v1/query/history/{taskId}/export` | GET | ✅ | ✅ | — | — | — |
| `/api/v1/query/history/{taskId}` | DELETE | ✅ | — | — | — | — |
| `/api/v1/llm/*` | ALL | ✅ | — | — | ✅ | — |

> 注：STUDENT 在 `/api/v1/query/chat` 和 `/api/v1/query/result/{taskId}` 仅能访问**自己的数据**（数据级权限隔离 → v2），v1 通过 API 校验 `studentNo` 参数与 Token 中用户绑定关系实现。

---

## 范围切分

### v1（本次必做）

1. **认证基础设施**：Spring Security 启用 + JWT（Access + Refresh Token）认证链路 + 登录/登出/刷新端点
2. **数据模型**：MySQL `user_account` + `role` + `user_role` 表 DDL + DO/Repository
3. **5 类角色定义**：ADMIN / TEACHER / STUDENT / OPS_STAFF / OPS_MANAGER，ADMIN 继承 TEACHER
4. **用户管理 API**：超级管理员 CRUD 用户 + 分配角色 + 启用/禁用
5. **全端点 JWT 过滤器**：所有 `/api/v1/**` 请求携带有效 JWT 才能访问（`/api/v1/auth/login` 和 `/api/v1/auth/refresh` 除外）
6. **全端点角色权限注解**：按「端点-角色映射矩阵」为每个端点标注 `@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")` 等
7. **Redis 缓存**：添加依赖 + 配置连接 + 用户权限缓存（TTL 30min） + 变更主动失效 + 降级兜底
8. **前端登录页**：极简风格，用户名 + 密码表单，登录失败提示
9. **前端路由守卫**：未登录跳转 `/login`，登录后回到目标页，无权限页显示 403
10. **前端菜单权限**：侧边导航按角色过滤显示
11. **前端 Token 管理**：localStorage 存储 + axios interceptor 自动刷新 + 刷新失败跳登录页
12. **前端用户管理页**：管理员可查看用户列表 + 创建用户 + 编辑角色 + 启用/禁用
13. **pom.xml**：新增 `spring-boot-starter-data-redis` + `jjwt-api`/`jjwt-impl`/`jjwt-jackson` + 移除 Security exclude

### v2（下一轮考虑）

- **修改密码**：已登录用户在个人信息页修改密码（需验证旧密码）
- **忘记密码/重置密码**：通过管理员重置临时密码
- **数据级权限隔离**：如"教师只能看到自己任教班级的学生数据"、"学生只能看自己的诊断记录"
- **登录审计日志**：记录每次登录/登出/Token 刷新（时间/IP/User-Agent）
- **用户自助注册**：学生自行注册 + 管理员审批激活
- **STUDENT 角色真实可用**：当前 STUENT 端点为 `/api/v1/query/chat` 仅自己数据，v2 补齐学生个人知识掌握总览等 UC-37~42

### out（永远不做）

- **OAuth/SSO 第三方登录**：不接微信/钉钉/Google 等第三方登录
- **多因素认证（MFA）**：不实现短信/邮箱验证码二次认证
- **组织/租户多级管理**：不做学校→年级→班级的多级组织架构（班级字段仅字符串属性）
- **细粒度操作权限（按钮/字段级）**：不做"只读用户不能点删除按钮"这种前端按钮级控制

---

## 非功能性需求

- **性能**: 登录接口响应时间 ≤ 500ms（P95）；权限校验（含 Redis 缓存命中）≤ 5ms；Redis 不可用时降级查 MySQL ≤ 50ms
- **安全**: 密码 BCrypt 加密存储（强度 ≥ 10）；JWT 使用 RS256 或 HS256 签名；Access Token 有效期 ≤ 30min；Refresh Token 有效期 ≤ 7d；Token 不在 URL 参数中传递
- **可访问性**: 登录页支持键盘 Enter 提交表单，表单控件有 label，错误提示有 aria 属性
- **兼容性**: 桌面端 ≥ 1280px（与现有管理后台一致），Chrome/Firefox/Edge 120+
- **可观测性**: 登录成功/失败记 INFO/WARN 日志；Token 校验失败记 DEBUG 日志；Redis 不可用记 WARN 日志；用户 CRUD 操作记 INFO 日志（含操作人）

## 依赖与假设

- **依赖**: Redis 7.x 容器已通过 podman 部署（与现有 Neo4j/MySQL/MinIO 同级）；Redis 连接参数在 `application-dev.yml` 中配置
- **依赖**: `spring-boot-starter-security` 已在 pom.xml，需移除 `application.yml` 中的 `SecurityAutoConfiguration` exclude
- **依赖**: `spring-boot-starter-data-redis` + `jjwt` 需新增到 pom.xml
- **依赖**: 前端 `axios` 拦截器 + `vue-router` 导航守卫 + `lucide-vue` 图标库已在项目中
- **假设**: 现有集成测试在 dev profile 下运行，连接 podman 中真实 Redis；测试需新增 `@WithMockUser` 或 Test Security 配置以携带 Mock JWT
- **假设**: `textbook.uploaded_by` 字段（FK→user_account.id）暂不强制外键约束，应用层维护关联；未来上传文件时写入当前用户 ID
- **假设**: Neo4j bolt 协议不经过 Spring Security 过滤器链，Neo4j 自身用户名密码认证已足够，本次不改动

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。