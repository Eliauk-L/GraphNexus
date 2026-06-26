# REQUIREMENT: 全局异常处理器补全可追踪日志

- **Change ID**: `exception-traceability`
- **关联**: `@.specs/exception-traceability/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为后端开发者，我想系统异常被 `GlobalExceptionHandler` 捕获时，完整堆栈 + 异常 message + 根因按 `traceId` 写入日志，以便拿到前端响应里的 `traceId` 就能在 `graphnexus-error.log` 里定位到出错类与行，无需复现。
- **US-2**：作为后端开发者，我想不同类型异常按既定级别落日志（兜底未捕获异常 → ERROR 完整堆栈；业务/校验/权限 → WARN 摘要），以便 `graphnexus-error.log` 不被预期内业务异常污染，又能完整保留真正系统故障的现场。
- **US-3**：作为前端 / API 消费者，我想系统异常响应体与本次变更前逐字段一致（仍只含 `errorCode` / `errorMessage`(类名级) / `userTip` / `traceId` / `timestamp`，不含堆栈 / 异常 message / 根因），以便前端无感知变更且不新增内部信息泄露。
- **US-4**：作为运维，我想异常日志通过既有 `traceId`（MDC）与请求链路关联并落入既有 `graphnexus-error.log`，以便不引入新日志基础设施即可检索，且与现有日志体系一致。

---

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 兜底未捕获异常 — 完整堆栈按 traceId 落日志

- **Given** `GlobalExceptionHandler` 已注册，`traceId` 已由 `TraceIdFilter` 注入 MDC
- **When** 一个 Controller 方法抛出未被其他 `@ExceptionHandler` 匹配的 `Exception`（如 `RuntimeException("bean X not found")`，含 cause 链）
- **Then** 响应为 HTTP 500 + `ErrorResponse(B0001, "系统内部异常: RuntimeException", 泛化 userTip, traceId)`；**同时**后端写入一条 ERROR 级日志，含完整堆栈（异常类名 + 异常 `getMessage()` + cause 链 + 出错行号），且该日志条目携带的 `traceId` == 响应体 `traceId`
- **验证方式**: `mvn test -Dtest=GlobalExceptionHandlerLoggingTest#兜底异常打印完整堆栈` —— 测试用 logback `ListAppender` 捕获日志，断言含 `RuntimeException`、`bean X not found`、cause 链与堆栈帧，且 MDC `traceId` == 响应体 `traceId`

### AC-2 · 异常分级 — 业务/校验/权限不污染 ERROR 日志

- **Given** 系统正常运行
- **When** 分别触发 `BusinessException`、`MethodArgumentNotValidException`、`AccessDeniedException`
- **Then** 三者各自落 WARN 级摘要日志（`BusinessException` 含 errorCode + message；校验含字段错误拼接；权限含异常消息），**不以** ERROR 级打印兜底完整堆栈；`graphnexus-error.log`（ERROR 阈值 appender）不被这三类预期内异常写入
- **验证方式**: `mvn test -Dtest=GlobalExceptionHandlerLoggingTest#业务校验权限异常分级落日志` —— `ListAppender` 断言三者为 WARN 级、无 ERROR 级完整堆栈条目

### AC-3 · 前端响应体逐字段不变 — 回归无破坏

- **Given** 系统正常运行
- **When** 触发兜底系统异常
- **Then** 响应体 JSON 字段集合与变更前完全一致：`{errorCode, errorMessage, userTip, traceId, timestamp}`，无新增字段，无堆栈 / 异常 message / 根因字段；`errorMessage` 仍为 `"系统内部异常: <异常类SimpleName>"`，不含 `ex.getMessage()`
- **验证方式**: `mvn test -Dtest=GlobalExceptionHandlerLoggingTest#响应体字段不变` —— 断言 `ErrorResponse` 序列化字段集 == 上述 5 字段，且 `errorMessage` 不含异常 message 原文

### AC-4 · 零新增基础设施 — 复用既有日志体系

- **Given** 本次变更已实施
- **When** 检查日志相关配置与依赖
- **Then** `logback-spring.xml` 无新增 appender；`pom.xml` 无新增日志依赖；异常日志通过既有 `[%X{traceId}]` pattern + `graphnexus-error.log` appender 落盘，`GlobalExceptionHandler` 无新增日志框架依赖
- **验证方式**: `git diff` 检查 `pom.xml` 无日志相关改动；`grep -c "<appender" src/main/resources/logback-spring.xml` 数量不变；`grep -r "import.*logger\|LoggerFactory" src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java` 仅用既有 SLF4J

---

## 范围切分

### v1（本次必做）

- AC-1 ~ AC-4：在 `GlobalExceptionHandler` 所有 `@ExceptionHandler` 方法中补全分级结构化异常日志，traceId 关联，复用既有日志基础设施，前端响应体不变。

### v2（下一轮考虑，不本次）

- **L2/L3 分层异常日志职责显式落实**：CONTEXT.md 现有「L2 捕获并记日志」约定当前未必 enforced；被 L3 wrap 成 `BusinessException` 的技术异常，其原始堆栈的可溯性需 L2 落实日志，本次不改 L2 行为。
- **启动期异常统一捕获/日志**：早于 `GlobalExceptionHandler` 生效的异常（如启动装配期 `NoSuchBeanDefinitionException`）不在 `@RestControllerAdvice` 覆盖范围，需独立机制。
- **异常日志含请求上下文**：日志增加 method / URI / 参数摘要，进一步增强定位（当前仅 traceId + 堆栈）。

### out（永远不做）

- 不改前端 `ErrorResponse` 字段结构或 `userTip` 内容（安全边界，内部细节只走日志）。
- 不引入 ELK / SkyWalking / Loki 等日志收集聚合基础设施。
- 不新增独立 ADR（日志分级属可逆工程决策，作为既有异常处理流设计的增量）。
- 不修复触发本次提案的具体 `NoSuchBeanDefinitionException` bean 装配问题（后续单独排查）。

---

## 非功能性需求

- **性能**: 异常路径非热路径，完整堆栈打印开销可接受；不设硬性性能指标。
- **可访问性**: 无（后端）。
- **安全**: 异常堆栈 / message / 根因只入后端日志，不回前端响应；`errorMessage` 保持类名级不增强，防内部实现信息泄露。
- **兼容性**: `ErrorResponse` 结构与字段不变，前端 / API 消费者无感；JDK 17 / Spring Boot 3.3 沿用现状。
- **可观测性**: 本 change 核心——异常按 `traceId` 可检索，兜底异常完整堆栈落入 `graphnexus-error.log`（见 AC-1/AC-2）。

## 依赖与假设

- **依赖**：
  - `TraceIdFilter`（MDC `traceId` 注入，已存在）
  - `logback-spring.xml`（`graphnexus-error.log` appender + `[%X{traceId}]` pattern，已存在）
  - `GlobalExceptionHandler` 现有结构与 `ErrorCode`（B0001 等，已存在）
- **假设**：
  - 触发本次的 `NoSuchBeanDefinitionException` 属**运行期 Controller 异常**（已流经 `handleGeneralException`，响应体含其类名可证），非启动期——待 DESIGN 确认覆盖边界。
  - CONTEXT.md「L2 捕获并记日志」约定当前**未必 enforced**；本次不改 L2 行为，L2 与 handler 的日志职责去重界定留 DESIGN。
  - JVM 编译保留调试行号信息（Spring Boot 默认开启），保证堆栈含行号。

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。
