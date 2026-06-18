# REQUIREMENT: 搭建可运行的项目框架

- **Change ID**: `init-platform`
- **关联**: `@.specs/init-platform/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为开发者，我想有一个符合 `docs/项目规范.md` 定义的四层包结构的 Maven 项目，以便我可以直接在新包下创建业务代码。
- **US-2**：作为开发者，我想使用统一的异常体系（BusinessException + ErrorCode + GlobalExceptionHandler），以便在后续写业务代码时直接 throw 异常而不需要每次造轮子。
- **US-3**：作为开发者，我想每个 HTTP 请求自动携带 Trace ID 注入 MDC，以便排查问题时可按 traceId 串联日志。
- **US-4**：作为开发者，我想有统一的 API 响应体（ApiResponse）和分页响应体（PageResult），以便所有 Controller 返回格式一致。
- **US-5**：作为开发者，我想在无外部基础设施（Neo4j/MySQL/Redis/RabbitMQ）的环境下也能启动应用，以便本地开发调试不依赖容器。
- **US-6**：作为架构师，我想有 ArchUnit 测试自动校验分层依赖规则（L1→L2→L3），以便后续开发不会无意中打破架构约束。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 项目可编译

- **Given** 已配置 `pom.xml`（含全部依赖）和 `src/` 目录结构
- **When** 执行 `mvn compile`
- **Then** BUILD SUCCESS，零错误零警告
- **验证方式**: `mvn compile`

### AC-2 · 应用可启动且 Actuator 健康检查通过

- **Given** 无 Neo4j/MySQL/Redis/RabbitMQ 容器运行
- **When** 执行 `mvn spring-boot:run`
- **Then** 应用成功启动，`curl http://localhost:8080/actuator/health` 返回 `{"status":"UP"}`
- **验证方式**: `curl -s http://localhost:8080/actuator/health | jq '.status'` 输出 `"UP"`

### AC-3 · 包结构完整

- **Given** 项目根目录
- **When** 检查 `src/main/java/com/graphnexus/` 目录树
- **Then** 存在 `api/`、`application/`、`infrastructure/`、`common/` 四层及其子包目录，与 `docs/项目规范.md` §1.4.2 一致
- **验证方式**: `find src/main/java/com/graphnexus -type d | sort`

### AC-4 · 统一异常体系可用

- **Given** 已实现 `BusinessException`、`ErrorCode`、`ErrorResponse`、`GlobalExceptionHandler`
- **When** 在任意 Controller 中 `throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "文件不存在", "请检查上传记录")`
- **Then** HTTP 响应返回 `{"errorCode": "...", "errorMessage": "文件不存在", "userTip": "请检查上传记录", "traceId": "...", "timestamp": "..."}`，HTTP 状态码按 errorCode 映射
- **验证方式**: `mvn test` 中针对 GlobalExceptionHandler 的单元测试通过

### AC-5 · Trace ID 全链路注入

- **Given** 应用已启动，`TraceIdFilter` 已注册
- **When** 发送任意 HTTP 请求（含/不含 `X-Trace-Id` header）
- **Then** 响应体/响应头/日志中可见同一 traceId；不含 header 时自动生成 UUID
- **验证方式**: `curl -v http://localhost:8080/actuator/health 2>&1 | grep -i trace` 或日志中可见 `[traceId]`

### AC-6 · 统一 API 响应体可用

- **Given** 已实现 `ApiResponse<T>` record
- **When** Controller 返回 `ApiResponse.success(data)` 或 `ApiResponse.error(errorResponse)`
- **Then** 响应 JSON 包含 `code`、`message`、`data`、`traceId`、`timestamp` 五个字段
- **验证方式**: 单元测试验证 `ApiResponse.success("test").data()` 等于 `"test"`

### AC-7 · 统一分页响应体可用

- **Given** 已实现 `PageResult<T>` record
- **When** Service 返回一个 `Page<T>` 对象，Controller 调用 `PageResult.of(page)`
- **Then** 响应 JSON 包含 `list`、`total`、`pageNum`、`pageSize` 四个字段
- **验证方式**: 单元测试验证 `PageResult.of(mockPage)` 字段值正确

### AC-8 · 分层架构约束自动校验

- **Given** 已配置 ArchUnit 测试规则
- **When** 执行 `mvn test`
- **Then** 架构测试通过：L1(api) 只依赖 L2(application) 和 common，不依赖 L3(infrastructure)；L2 只依赖 L3 和 common，不依赖 L1
- **验证方式**: `mvn test -pl .` 中 ArchUnit 测试用例全部通过

---

## 范围切分

### v1（本次必做）

- 四层包目录结构（含 `.gitkeep` 或 `package-info.java`）
- `common/` 模块全部类：`BusinessException`、`ErrorCode`、`ErrorResponse`、`GlobalExceptionHandler`、`TraceIdFilter`、`ApiResponse`、`PageResult`
- `GraphNexusApplication` 启动类调整（排除非必要自动配置，允许无基础设施启动）
- `application-dev.yml` 中基础设施连接设为 lazy/optional
- ArchUnit 分层架构约束测试
- Logback 配置中 Trace ID 占位符

### v2（下一轮考虑，不本次）

- 统一的 Jackson 配置（日期格式、null 值策略）
- CORS 跨域配置
- Actuator 自定义健康指示器（Neo4j/MySQL/Redis/RabbitMQ 可用性检测）
- API 文档 Swagger UI 可见端点配置
- 统一的分页参数解析器（HandlerMethodArgumentResolver）

### out（永远不做）

- 自动生成 Controller/Service/Repository 脚手架代码（代码生成器）
- 热部署/热加载（DevTools），团队决定不使用
- 业务健康检查端点（如"检查学生表是否有数据"），由业务模块自行实现

---

## 非功能性需求

- **性能**: 应用冷启动时间 ≤ 10s（无基础设施连接等待）
- **可访问性**: 无（纯后端 API）
- **安全**: 本次仅引入 Spring Security 依赖，不配置任何安全规则（所有端点开放）。安全规则在后续业务 change 中配置
- **兼容性**: JDK 17，macOS / Linux 均可运行
- **可观测性**: 全链路 Trace ID（MDC + 响应体），Actuator 暴露 `/actuator/health`、`/actuator/info`、`/actuator/metrics`、`/actuator/prometheus`

## 依赖与假设

- **依赖**: Java 17、Maven 3.9.x、Spring Boot 3.3.5（由 `pom.xml` <parent> 管控）
- **依赖**: Spring Milestones 仓库（`https://repo.spring.io/milestone`）可访问，用于拉取 `spring-ai-openai-spring-boot-starter:1.0.0-M4`
- **假设**: 开发者本地已安装 JDK 17 和 Maven 3.9+
- **假设**: `pom.xml` 中已配置的依赖版本均可用，无需调整

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。