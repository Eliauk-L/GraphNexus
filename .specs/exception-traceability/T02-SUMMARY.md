# SUMMARY: T02 - 预期异常分级 WARN + 响应体回归 + 零基础设施静态校验（AC-2/AC-3/AC-4）

- **Change ID**: `exception-traceability`
- **Task ID**: `T02`
- **完成时间**: 2026-06-21
- **AI 角色**: Dev
- **提交**: `493eab2`

---

## 做了什么（一段话）

在 T01 已有 `@Slf4j` 基础上，为 GlobalExceptionHandler 的 3 个预期异常 handler 各补一条 `log.warn`（不传 throwable → 无堆栈渲染，DESIGN D3）：
- `handleBusinessException`：`业务异常 errorCode={} httpStatus={} errorMessage={}`
- `handleValidationException`：`参数校验失败 fieldErrors={}`（复用已拼接的 fieldErrors）
- `handleAccessDeniedException`：`权限不足 errorCode={} message={}`

在 `GlobalExceptionHandlerLoggingTest`（T01 已建 ListAppender 脚手架）追加 2 个测试方法：
- `业务校验权限异常分级落日志`（AC-2）：分别调三个 handler，断言 3 条 WARN 事件 + 0 ERROR 事件 + 每条 WARN 的 throwableProxy==null + 内容含预期字段
- `响应体字段不变`（AC-3）：用 Jackson ObjectMapper 序列化兜底 ErrorResponse 为 JSON，断言字段集 == {errorCode, errorMessage, userTip, traceId, timestamp} 共 5 字段，且 errorMessage 不含 ex.getMessage() 原文（安全边界）

按 TDD 先写测试跑出干净 RED（`应有 3 条 WARN 事件 ==> expected: <3> but was: <0>`），实现后跑 GREEN 通过。AC-4 零基础设施由 verify 静态检查链覆盖。无偏离原计划。

## 改动文件

| 文件 | 性质 | 说明 |
|---|---|---|
| `src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java` | 修改 | 3 个预期异常 handler 各补一条 `log.warn`（共 +13 行）。响应体构造逐字不变。其余结构无改。 |
| `src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java` | 修改 | 追加 AC-2 + AC-3 测试方法 + 新 import。T01 的 AC-1 测试无改。 |
| `.specs/exception-traceability/TASK.md` | 本地修改 | 修正 verify：`grep -c '<appender'` → `grep -c '<appender '`（原版误匹配 6 个 `<appender-ref>` 导致返回 11≠5；修正后仅匹配 5 个 `<appender name=>` 定义=5）。.specs/ 被 gitignore → 不入提交。 |

## verify 输出（必填）

RED（实现前，3 个 handler 零 log.warn）：

```
 $ mvn test -Dtest=GlobalExceptionHandlerLoggingTest

 [ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE!
 [ERROR] GlobalExceptionHandlerLoggingTest.业务校验权限异常分级落日志:155
         应有 3 条 WARN 事件 ==> expected: <3> but was: <0>
 [INFO] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
```

→ 干净 RED：AC-1/AC-3 已绿，AC-2 因无 WARN 事件失败。

GREEN（实现后，完整验证链）：

```
 $ mvn test -Dtest=GlobalExceptionHandlerLoggingTest -q
 ✅ [1] mvn test 通过（Tests run: 3, Failures: 0, Errors: 0, Skipped: 0）
 $ git diff --quiet -- pom.xml
 ✅ [2] pom.xml 无改动
 $ [ "$(grep -c '<appender ' src/main/resources/logback-spring.xml)" -eq 5 ]
 ✅ [3] appender 定义数=5 (不变)
 $ grep -q '@Slf4j' src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
 ✅ [4] @Slf4j 存在
 $ ! grep -q 'LoggerFactory' src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
 ✅ [5] 无 LoggerFactory 手动声明

 🎉 VERIFY FULL CHAIN PASSED
```

> 控制台输出确认了 3 条 WARN 日志：
> - `WARN ... 业务异常 errorCode=A0001 httpStatus=404 errorMessage=请求的资源不存在，请检查参数`
> - `WARN ... 参数校验失败 fieldErrors=name: 姓名不能为空; email: 邮箱格式不正确`
> - `WARN ... 权限不足 errorCode=A0003 message=Access is denied`

## 6 维自查（生产代码改动必填 · 来自 4-dev 步骤 4）

> brooks-lint 未安装 → AI 内置 R1~R6 快查（路径 B）。

### 🟢 R1 · 认知过载：无问题
**Symptom**：每个 handler 方法 5~18 行（含新增 log.warn），单一职责。嵌套 ≤2 层。无 >50 行函数。
**Source**：`GlobalExceptionHandler.java:42-116`。
**Remedy**：无需。

### 🟢 R2 · 变更传播：无问题
**Symptom**：仅改 T02 声明的 2 个 write_files；未触碰其他模块/文件。
**Source**：`git diff --name-only HEAD`。
**Remedy**：无需。

### 🟢 R3 · 知识重复：无问题
**Symptom**：3 条 `log.warn` 各自有独特的 message 模板和参数签名，非粘贴重复。与 T01 `log.error` 共用 `@Slf4j` + message pattern 约定（`中文标识 key={} value={}`），风格一致。
**Source**：`GlobalExceptionHandler.java:43,60,79,103`。
**Remedy**：无需。

### 🟢 R4 · 偶然复杂：无问题
**Symptom**：仅 3 条 `log.warn` 调用，无投机扩展点 / 多余配置 / "以后可能用到"的代码。
**Source**：本次 diff。
**Remedy**：无需。

### 🟢 R5 · 依赖混乱：无问题
**Symptom**：`GlobalExceptionHandler`（common.exception · L1 边界）依赖 `@Slf4j`/SLF4J/MDC（common 基础设施），方向正确。无业务层反向 import 基础设施实现。
**Source**：`GlobalExceptionHandler.java` import 段。
**Remedy**：无需。

### 🟢 R6 · 领域扭曲：无问题
**Symptom**：日志 message 用领域词（`业务异常` / `参数校验失败` / `权限不足` / `兜底未捕获异常`），变量名清晰（ex / body / fieldErrors）。无 `data` / `info` / `item` 等模糊命名。
**Source**：`GlobalExceptionHandler.java:43,60,79,103`。
**Remedy**：无需。

### 已知接受 + 理由（🟡 Major 项不修的）

无 Major 项。

### 已知小问题（🟢 Minor 项可省的）

无。

## 数据库迁移（涉及 schema 变更必填 · 来自 4-dev 步骤 1.7 / R4.5）

N/A——本任务纯日志调用，无表 / 字段 / 索引 / 外键 / ORM model / 迁移目录 / DDL 变更。1.7 协议未触发。

## 破坏性变更（来自 4-dev 步骤 1.8 / R4.6）

**未触发**。本次 diff：
- 未删除既有代码 ≥5 行（仅新增 3 条 `log.warn` 调用）；
- 未改公共导出签名（4 个 `@ExceptionHandler` 方法签名逐字不变）；
- 未改公共 API（HTTP 路由 / 响应体 5 字段逐字不变，D7）；
- 未删除文件 / 重命名导出符号。

故未走 1.8 grep 引用图 + 反问协议。

## 越界检查（R6.5 / 4-dev 步骤 5）

```
✅ TASK 声明的 write_files（2 项）：
  - src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
  - src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java

✅ 实际 diff 涉及（T02 产物）：
  - src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java (M)
  - src/test/java/com/graphnexus/common/exception/GlobalExceptionHandlerLoggingTest.java (M)

⏸️ .specs/CONTEXT.md / STATE.md / docs/.../layered-architecture.drawio 是会话开始前的既有修改
   （gitStatus 初始快照佐证），非 T02 产物 → 不纳入 T02 提交（R7.2）

→ 0 越界 ✅
```

### verify 命令修正记录

TASK.md `<verify>` 原始 grep `grep -c '<appender'` 过匹配 6 个 `<appender-ref>` 用法（返回 11≠5），导致 AC-4 子检查恒失败。经用户确认（选择方案 1），修正为 `grep -c '<appender '`（仅匹配 5 个 `<appender name=>` 定义=5），保 AC-4 意图「5 appender 定义不变」。TASK.md 已在本地修正（.specs/ 被 gitignore）。

## 沿用既有抽象 grep（R6.4 / 4-dev 步骤 1.4）

```
✅ 沿用既有抽象 grep（R6.4）：
- SLF4J Logger：找到 @Slf4j（T01 已加，0 文件手动 LoggerFactory）→ 沿用
- traceId 关联：找到 TraceIdFilter MDC "traceId" + logback [%X{traceId}] pattern → 沿用
- ERROR 日志归档：找到 logback-spring.xml FILE_ERROR appender → 沿用，不新增
- 异常分级分发：找到 GlobalExceptionHandler 4 个 @ExceptionHandler → 沿用，仅补 log
- 异常→响应映射：找到 ErrorResponse（5 字段 record）→ 沿用，字段逐字不变
- Jackson ObjectMapper：找到 8 文件在用（ExtractionJsonParser 等）、spring-boot-starter-web 自带 → 沿用 new ObjectMapper()（测试内）
- log.warn 约定：项目既有风格「中文摘要 key={} value={}」不带 throwable → 沿用（DESIGN D3）
```

## LESSONS 检查（R1.8 / 4-dev 步骤 1.5）

- 已查阅 `.specs/LESSONS.md`：仅 L-001（Neo4j GDS 2.x 关系投影通配符语法），与本任务（Java 异常日志）无栈交集 → 不适用，无 active 条目命中。

## 是否触发新 fix-plan

否。T02 verify 通过，无 REVIEW/INTEGRATION 介入。

## change 整体状态

`exception-traceability` change 全部 2 个任务已完成：
- ✅ T01：兜底异常 ERROR 完整堆栈 + AC-1 测试（提交 `e1abcc9`）
- ✅ T02：预期异常 WARN 分级 + 响应体回归 + AC-4 静态校验（提交 `493eab2`）

下一步：`@flow-kit/prompts/5-test.md`（测试矩阵 + UAT）或 `@flow-kit/prompts/6-review.md`（双轮审查）。

---

## 自检（4-dev 末尾清单）

- [x] verify 命令真的跑了，RED + GREEN 输出均已贴出
- [x] verify 全链 5 子检查均通过（含修正后的 appender 定义计数）
- [x] TDD 执行：RED（AC-2 `expected: <3> but was: <0>`）→ GREEN（3/0/0）→ REFACTOR（无需）
- [x] 测试与代码同次提交（T02 原子提交 `493eab2`）
- [x] 6 维 self-review 跑了（内置快查，6 项全 🟢）
- [x] 涉及 schema 变更：N/A（1.7 未触发）
- [x] 前端任务 1.6：N/A（纯后端）
- [x] 沿用既有抽象 grep 跑了（R6.4），结果贴入
- [x] 破坏性变更 1.8：未触发，已明示
- [x] 提交前 diff 边界 verify 跑了（R6.5），0 越界 ✅
- [x] SUMMARY 含「6 维自查」+「越界检查」段（含 verify 命令修正记录；无 schema/破坏性变更段，已明示 N/A/未触发）
- [x] 没有改动 REQUIREMENT.md / DESIGN.md
- [x] 没有越界改其他任务的文件
- [x] TASK.md 中 T02 的 `<verify>` grep 已修正（本地，.specs/ gitignored）
