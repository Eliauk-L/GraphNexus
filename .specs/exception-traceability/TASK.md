# TASK: 全局异常处理器补全可追踪日志

- **Change ID**: `exception-traceability`
- **关联**: `@.specs/exception-traceability/REQUIREMENT.md`、`@.specs/exception-traceability/DESIGN.md`、`@.specs/CONTEXT.md`

---

## Artifact Preflight（R2.7）

- `REQUIREMENT.md` ✅（已确认）
- `DESIGN.md` ✅（已确认）
- `UI-DESIGN.md` N/A——纯后端 API change（CHANGE/DESIGN 均声明非前端，跳过 2a）
- LESSONS 扫描（R1.8）：仅 L-001（Neo4j GDS 投影语法），与本 change（异常日志）无交集，**不适用**

---

## 波次划分

```
Wave 1: T01                        （兜底异常日志 · AC-1 · 核心修复）
Wave 2: T02  (depends on T01)      （预期异常分级 + 响应体回归 + 零基础设施 · AC-2/AC-3/AC-4）
```

> **全串行说明**：T01 与 T02 共写同一对文件（`GlobalExceptionHandler.java` + `GlobalExceptionHandlerLoggingTest.java`），按文件冲突切必须顺序执行，无并行波次（满足自检「除非确实全是串行」例外）。每个任务**配对 impl + test**（R4.2：代码改动必须伴随测试改动），且各自 verify 覆盖自身 impl（R2.4/R6.3）。

---

## 任务清单

```xml
<task id="T01" parallel="false" status="done" done-at="2026-06-21">
  <name>兜底异常补全 ERROR 完整堆栈日志 + AC-1 测试（核心修复）</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
    src/main/java/com/graphnexus/common/exception/ErrorResponse.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/logging/TraceIdFilter.java
    src/main/resources/logback-spring.xml
    src/test/java/com/graphnexus/common/util/Md5UtilsTest.java
    .specs/exception-traceability/REQUIREMENT.md
    .specs/exception-traceability/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
    src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java
  </write_files>
  <action>
    在 GlobalExceptionHandler 上加 `@Slf4j`（沿用项目既有约定，42 文件在用；Lombok 已在 pom，零新增依赖，DESIGN D1）。
    在 `handleGeneralException` 兜底分支补一条 `log.error`：
      - message 占位 `兜底未捕获异常 errorCode={} httpStatus={} exception={}`，依次填 `ErrorCode.B0001.getErrorCode()` / `500` / `ex.getClass().getSimpleName()`；
      - `ex` 作为 SLF4J **最后一个参数**（不对应 `{}`）→ logback 自动渲染完整堆栈（异常类名 + getMessage() + cause 链 + 行号）；
      - traceId 不写入 message，复用 logback `[%X{traceId}]` pattern（DESIGN D5）。
    响应体构造保持逐字不变（errorMessage 仍 `系统内部异常: <SimpleName>`，DESIGN D7）。
    新建 `GlobalExceptionHandlerLoggingTest`（plain JUnit 5 单元测试，`new GlobalExceptionHandler()` 直调，无 Spring 上下文），
    用 logback `ListAppender<ILoggingEvent>` 挂到 `LoggerFactory.getLogger(GlobalExceptionHandler.class)` 捕获日志。
    测试方法 `兜底异常打印完整堆栈`（AC-1，名字取自 REQUIREMENT 验证方式）：
      - MDC.put("traceId", "<固定值>")；构造 `new RuntimeException("bean X not found", new IllegalStateException("root cause"))`（带 cause 链）；
      - 调 `handler.handleGeneralException(ex)`；
      - 断言①：响应 HTTP 500 + ErrorResponse(B0001, "系统内部异常: RuntimeException", B0001 默认 tip, <固定 traceId>, timestamp)；
      - 断言②：ListAppender 捕获到 ≥1 条 ERROR 事件，其格式化输出含 `RuntimeException`、`bean X not found`、`root cause`（cause 链）与堆栈帧（行号）；
      - 断言③：该 ERROR 事件 MDC `traceId` == 响应体 `traceId`（event.getMDCPropertyMap().get("traceId")）。
    沿用既有抽象：`@Slf4j`、MDC key "traceId"（TraceIdFilter）、ErrorCode.B0001。
  </action>
  <verify>mvn test -Dtest='GlobalExceptionHandlerLoggingTest#兜底异常打印完整堆栈'</verify>
  <done>兜底未捕获 Exception 被 handleGeneralException 捕获后写入一条 ERROR 完整堆栈日志，且日志条目 MDC traceId == 响应体 traceId（AC-1）。</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="false" status="done" done-at="2026-06-21">
  <name>预期异常分级 WARN + 响应体回归 + 零基础设施静态校验（AC-2/AC-3/AC-4）</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
    src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java
    src/main/java/com/graphnexus/common/exception/ErrorResponse.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/resources/logback-spring.xml
    pom.xml
    .specs/exception-traceability/REQUIREMENT.md
    .specs/exception-traceability/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
    src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java
  </write_files>
  <action>
    在 GlobalExceptionHandler 既有 `@Slf4j`（T01 已加）基础上，为 3 个预期异常 handler 各补一条 `log.warn`（**不传 throwable，无堆栈**，DESIGN D3）：
      - `handleBusinessException`：`业务异常 errorCode={} httpStatus={} errorMessage={}` ← ex.getErrorCode()/getHttpStatus()/getErrorMessage()；
      - `handleValidationException`：`参数校验失败 fieldErrors={}` ← 已拼接的 fieldErrors；
      - `handleAccessDeniedException`：`权限不足 errorCode={} message={}` ← A0003 / ex.getMessage()。
    在 `GlobalExceptionHandlerLoggingTest`（T01 已建，含 ListAppender 脚手架）追加 2 个测试方法：
      - `业务校验权限异常分级落日志`（AC-2）：分别调三个 handler，断言各自 ListAppender 捕获 WARN 级事件、且无 ERROR 级完整堆栈条目（WARN 经 logback ThresholdFilter(ERROR) 不入 graphnexus-error.log 的语义由「级别==WARN 且无 throwable 渲染」体现）。
      - `响应体字段不变`（AC-3）：用 Jackson `ObjectMapper`（spring-boot 已带）序列化兜底路径的 ErrorResponse 为 JSON，`readTree` 断言字段集 == {errorCode, errorMessage, userTip, traceId, timestamp} 共 5 个，且 `errorMessage` == "系统内部异常: RuntimeException"（不含 ex.getMessage() 原文）。
    AC-4 零新增基础设施：本任务不改 pom.xml / logback-spring.xml，仅用既有 SLF4J（@Slf4j）；由 verify 中的静态检查断言。
    沿用既有抽象：`@Slf4j`、ErrorCode 枚举、Jackson ObjectMapper（既有）。
  </action>
  <verify>mvn test -Dtest=GlobalExceptionHandlerLoggingTest && git diff --quiet -- pom.xml && [ "$(grep -c '&lt;appender ' src/main/resources/logback-spring.xml)" -eq 5 ] && grep -q '@Slf4j' src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java && ! grep -q 'LoggerFactory' src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java</verify>
  <done>业务/校验/权限异常各自落 WARN 摘要且无 ERROR 完整堆栈（AC-2）；兜底响应体 JSON 字段集 == 5 字段且 errorMessage 不含异常 message（AC-3）；pom 无改动、logback appender 数不变(5)、handler 仅用既有 SLF4J（AC-4）。</done>
  <depends_on>T01</depends_on>
</task>
```

> **write_files 边界（B3 护栏）**：两任务 write_files 均严格在 DESIGN `## 0.5.1`「触碰模块」（`GlobalExceptionHandler.java`）+「新增模块」（测试文件）范围内；**不含**禁动清单（`pom.xml` / `docs/项目规范.md` / `docs/tech-stack-java.md`），亦不改 L2（`application/**`，REQUIREMENT v2 out-of-scope）。

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中（同时只允许一个非 [P] 任务为此状态）
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞（必须在下方「阻塞日志」记录）

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```

---

## 自检

- [x] 每个任务都有完整 7 字段（id / name / read_files / write_files / action / verify / done + depends_on）
- [x] 每个 `write_files` 严格在 DESIGN「触碰模块 + 新增模块」范围内（B3 护栏）
- [x] 任何任务的 `write_files` 不含 DESIGN「禁动清单」文件
- [x] 每个任务的 `verify` 都是可执行命令（T01 单测；T02 全测 + AC-4 静态检查链）
- [x] 并行标记：本 change 全串行（共写同一对文件），适用「除非确实全是串行」例外
- [x] 波次划分清晰、无环依赖（T01 → T02 单链）
- [x] 任务编号连续（T01 → T02）
- [x] 每任务配对 impl + test（R4.2），verify 覆盖自身 impl（R2.4/R6.3）

---

> 下一步：`@flow-kit/prompts/4-dev.md`（按波次逐个执行；Wave 1 → T01，Wave 2 → T02）。进入 DEV 需切 Dev 角色（R3，清窗）。
