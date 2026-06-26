# SUMMARY: T01 - 兜底异常补全 ERROR 完整堆栈日志 + AC-1 测试（核心修复）

- **Change ID**: `exception-traceability`
- **Task ID**: `T01`
- **完成时间**: 2026-06-21
- **AI 角色**: Dev

---

## 做了什么（一段话）

在 `GlobalExceptionHandler` 上加 Lombok `@Slf4j`（沿用项目既有约定，42 文件在用、0 文件手动 `LoggerFactory`），在兜底 `handleGeneralException` 内补一条 `log.error`：message 用 3 个 `{}` 占位（errorCode / httpStatus / exception SimpleName），`ex` 作为 SLF4J **最后一个参数**（不对应占位）→ logback 自动渲染完整堆栈（类名 + getMessage + cause 链 + 行号）；traceId 不写入 message，复用既有 `[%X{traceId}]` pattern（DESIGN D5）。**响应体构造逐字不变**（errorMessage 仍 `系统内部异常: <SimpleName>`，D7）。新建 `GlobalExceptionHandlerLoggingTest`（纯 JUnit 5，`new GlobalExceptionHandler()` 直调无 Spring 上下文），用 logback `ListAppender<ILoggingEvent>` 挂到 handler 的 logger，测试方法 `兜底异常打印完整堆栈`（AC-1）：构造带 cause 链的 `RuntimeException("bean X not found", IllegalStateException("root cause"))`，断言①响应 HTTP 500 + ErrorResponse(B0001/类名级 errorMessage/B0001 tip/固定 traceId/timestamp)；断言②用 `PatternLayout(%msg%n%ex)` 渲染 ERROR 事件，含 RuntimeException / bean X not found / Caused by: / IllegalStateException / root cause / 堆栈帧 / `.java:` 行号；断言③该 ERROR 事件 MDC traceId == 响应体 traceId。按 TDD 先写测试跑出干净 RED（`应捕获到 ERROR 级日志事件`），实现后跑 GREEN 通过。无偏离原计划。

## 改动文件

| 文件 | 性质 | 说明 |
|---|---|---|
| `src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java` | 修改 | 加 `@Slf4j` + import；`handleGeneralException` 补 `log.error`（ex 作 throwable 末参 → 完整堆栈）。响应体构造逐字不变。其余 3 个 handler 不动（T02 范围）。 |
| `src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java` | 新增 | AC-1 单元测试 + ListAppender 脚手架（@BeforeEach/@AfterEach，T02 可复用追加方法）。 |

## verify 输出（必填）

RED（实现前，对当前零 log 的 handler）：

```text
$ mvn test -Dtest='GlobalExceptionHandlerLoggingTest#兜底异常打印完整堆栈' -q

[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.036 s <<< FAILURE! -- in com.graphnexus.common.exception.GlobalExceptionHandlerLoggingTest
[ERROR] com.graphnexus.common.exception.GlobalExceptionHandlerLoggingTest.兜底异常打印完整堆栈 -- Time elapsed: 0.023 s <<< FAILURE!
java.lang.AssertionError: 应捕获到 ERROR 级日志事件（兜底异常须落 ERROR 完整堆栈）
	at ...GlobalExceptionHandlerLoggingTest.lambda$兜底异常打印完整堆栈$1(GlobalExceptionHandlerLoggingTest.java:90)
	at java.base/java.util.Optional.orElseThrow(Optional.java:403)
	...
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

→ 干净 RED：断言①（响应体）已过，断言②因无 ERROR 事件失败。surefire 3.2.5 成功匹配中文方法名（Unicode 匹配风险消除）。

GREEN（实现后）：

```text
$ mvn test -Dtest='GlobalExceptionHandlerLoggingTest#兜底异常打印完整堆栈'

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.044 s -- in com.graphnexus.common.exception.GlobalExceptionHandlerLoggingTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

> 注：GREEN 运行期控制台会打印一条 `log.error` 完整堆栈（含 `Caused by: java.lang.IllegalStateException: root cause at ...GlobalExceptionHandlerLoggingTest.兜底异常打印完整堆栈(...:72)`），这是 AC-1 期望的兜底 ERROR 完整堆栈落盘到控制台的正常输出，非测试失败。

## 6 维自查（生产代码改动必填 · 来自 4-dev 步骤 4）

> brooks-lint 未安装 → AI 内置 R1~R6 快查（路径 B）。

### 🟢 R1 · 认知过载：无问题
**Symptom**：`handleGeneralException` 实现约 18 行，单一职责（落 ERROR 日志 + 构响应 + 返回）。
**Source**：`GlobalExceptionHandler.java:94-111`。
**Consequence**：无。
**Remedy**：无需。无 >50 行函数，嵌套 ≤2 层。

### 🟢 R2 · 变更传播：无问题
**Symptom**：仅改 T01 声明的 `GlobalExceptionHandler.java` + 新建测试；未触碰其他任务文件。
**Source**：`git diff --name-only HEAD` + `git status --short`。
**Consequence**：无越界。
**Remedy**：无需。

### 🟢 R3 · 知识重复：无问题
**Symptom**：单条 `log.error` 调用，无粘贴重复逻辑。
**Source**：`GlobalExceptionHandler.java:98-102`。
**Consequence**：无。
**Remedy**：无需。

### 🟢 R4 · 偶然复杂：无问题
**Symptom**：仅一注解 + 一条 log 调用，无投机扩展点 / 多余抽象层。
**Source**：本次 diff。
**Consequence**：无。
**Remedy**：无需。

### 🟢 R5 · 依赖混乱：无问题
**Symptom**：`GlobalExceptionHandler`（common.exception · L1 边界）依赖 `@Slf4j`/SLF4J/MDC（common 基础设施），方向正确，无业务层反向 import 基础设施实现。
**Source**：`GlobalExceptionHandler.java` import 段。
**Consequence**：无。
**Remedy**：无需。

### 🟢 R6 · 领域扭曲：无问题
**Symptom**：日志 message 用领域词（`兜底未捕获异常` / errorCode / httpStatus / exception），变量名（ex / body）清晰。
**Source**：`GlobalExceptionHandler.java:98`。
**Consequence**：无。
**Remedy**：无需。

### 已知接受 + 理由（🟡 Major 项不修的）

无 Major 项。

### 已知小问题（🟢 Minor 项可省的）

无。

## 数据库迁移（涉及 schema 变更必填 · 来自 4-dev 步骤 1.7 / R4.5）

N/A——本任务纯日志调用，无表 / 字段 / 索引 / 外键 / ORM model / 迁移目录 / DDL 变更。1.7 协议未触发。

## 破坏性变更（来自 4-dev 步骤 1.8 / R4.6）

**未触发**。本次 diff：
- 未删除既有代码 ≥5 行（仅新增 import / 注解 / 一条 log 调用 + 注释）；
- 未改公共导出签名（4 个 `@ExceptionHandler` 方法签名逐字不变）；
- 未改公共 API（HTTP 路由 / 响应体 5 字段逐字不变，D7）；
- 未删除文件 / 重命名导出符号。

故未走 1.8 grep 引用图 + 反问协议。响应体不变已由 AC-1 测试断言①覆盖（errorMessage 仍 `系统内部异常: RuntimeException`，不含 ex.getMessage() 原文）。

## 越界检查（R6.5 / 4-dev 步骤 5）

```
✅ TASK 声明的 write_files（2 项）：
  - src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
  - src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java

✅ 实际 diff 涉及（T01 产物）：
  - src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java（M）
  - src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java（?? 新增）

⏸️ 工作树另有 3 个预存修改文件（.specs/CONTEXT.md / STATE.md / docs/.../layered-architecture.drawio），
   会话开始前即 M（gitStatus 佐证），非 T01 产物 → 不纳入 T01 提交（R7.2）。

→ 0 越界 ✅
```

## 沿用既有抽象 grep（R6.4 / 4-dev 步骤 1.4）

```
✅ 沿用既有抽象 grep（R6.4）：
- SLF4J Logger：找到 42 文件用 @Slf4j、0 文件手动 LoggerFactory.getLogger → 沿用 @Slf4j（DESIGN D1）
- traceId 关联：找到 TraceIdFilter MDC "traceId" + logback [%X{traceId}] pattern → 沿用，不新增
- ERROR 日志归档：找到 logback-spring.xml FILE_ERROR appender（ThresholdFilter=ERROR → graphnexus-error.log，共 5 个 <appender>）→ 沿用，不新增 appender
- 异常分级分发：找到 GlobalExceptionHandler 4 个 @ExceptionHandler（全仓库唯一 @RestControllerAdvice）→ 沿用结构，仅补 log
- 异常→响应映射：找到 ErrorResponse（5 字段 record）+ ErrorCode 枚举 → 沿用，字段逐字不变
- 测试日志捕获抽象：grep src/test/java 无既有 ListAppender 用例 → 新建（标准 logback 测试模式，DESIGN 已批准脚手架）
```

## LESSONS 检查（R1.8 / 4-dev 步骤 1.5）

- 已查阅 `.specs/LESSONS.md`：仅 L-001（Neo4j GDS 2.x 关系投影通配符语法，`lib`/`data`），与本任务（Java 异常日志）无栈交集 → 不适用，无 active 条目命中。

## 是否触发新 fix-plan

否。T01 verify 通过，无 REVIEW/INTEGRATION 介入。下一步：清窗进入 `@flow-kit/prompts/4-dev.md` 跑 **T02**（depends T01，预期异常 WARN 分级 + 响应体回归 + AC-4 零基础设施静态校验）。

---

## 自检（4-dev 末尾清单）

- [x] verify 命令真的跑了，RED + GREEN 输出均已贴出
- [x] 测试与代码同次提交（T01 原子提交）
- [x] 6 维 self-review 跑了（内置快查，6 项全 🟢）
- [x] 涉及 schema 变更：N/A（1.7 未触发）
- [x] 前端任务 1.6：N/A（纯后端）
- [x] 沿用既有抽象 grep 跑了（R6.4），结果贴入
- [x] 破坏性变更 1.8：未触发，已明示
- [x] 提交前 diff 边界 verify 跑了（R6.5），0 越界 ✅
- [x] SUMMARY 含「6 维自查」+「越界检查」段（无 schema/破坏性变更段，已明示 N/A/未触发）
- [x] 没有改动 REQUIREMENT.md / DESIGN.md
- [x] 没有越界改其他任务的文件（其余 3 handler 留给 T02）
