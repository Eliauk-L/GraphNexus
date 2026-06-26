# DESIGN: 全局异常处理器补全可追踪日志，使后端可凭 traceId 定位根因

- **Change ID**: `exception-traceability`
- **关联**: `@.specs/exception-traceability/CHANGE.md`、`@.specs/exception-traceability/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review
- **阶段**: DESIGN（增量式 · 完整路径）
- **项目类型**: 后端 API（非前端，跳过 2a-ui-design）

---

## 0. 技术栈选定

> 技术栈已在 `@.specs/CONTEXT.md`「已锁技术决策」段锁定，本 change 为既有栈上的增量，直接读用，不变栈（变栈视为开新 CHANGE · R7.1）。

- **选定**: 既有 Java 后端栈（锁定，可调整但本 change 不调）
- **后端**: Spring Boot 3.3.x / Java 17 LTS（`@.specs/CONTEXT.md` 已锁）
- **日志门面**: SLF4J + Logback + Lombok `@Slf4j`（项目统一约定，42 文件在用，0 文件手动 `LoggerFactory.getLogger`）
- **日志归档**: 既有 `logback-spring.xml`——`FILE_ERROR` appender（`ThresholdFilter=ERROR`）→ `logs/graphnexus-error.log`；`CONSOLE_PLAIN/FILE_PLAIN`（dev）/ `CONSOLE_JSON/FILE_JSON`（非 dev）均含 `[%X{traceId}]` pattern
- **traceId 基础设施**: 既有 `TraceIdFilter`（`OncePerRequestFilter`，MDC key `"traceId"`，`finally` 清理）
- **关键依赖**: 无新增（Lombok / SLF4J / Logback 均已在 `pom.xml`，属禁动清单，本 change 不触碰）
- **理由**: REQUIREMENT AC-4 强制「零新增基础设施」；既有 logback + MDC + TraceIdFilter 链路已通，异常处理器只是没往里写日志（`handleGeneralException` 当前零 `log` 调用，已 grep 验证）。
- **明确排除**: 不引入 ELK / SkyWalking / Loki（REQUIREMENT out）；不引 Logback 之外的结构化日志库（如自定义 JSON encoder）——既有 `LogstashEncoder` 已覆盖生产 JSON 需求。

> 步骤 0₋ 架构级变更预检：本 change 仅在既有 `GlobalExceptionHandler` 内补日志调用，不拆模块、不换数据库、不改鉴权——**未命中**架构级变更条件，无需 `A-architect` 基线，不打扰用户。

---

## 0.5 既有架构对齐（brownfield 必填）

### 0.5.1 本次 change 触碰的既有模块

```
触碰（既有 · 仅补日志调用，不改方法签名 / 不改响应体）：
- src/main/java/com/graphnexus/common/exception/GlobalExceptionHandler.java
    · 唯一改动点：4 个 @ExceptionHandler 方法各补一条分级 log；新增 @Slf4j 注解
    · 已验证：全仓库唯一 @RestControllerAdvice，兜底覆盖完整，无 advice 顺序问题

复用（既有 · 不改一行）：
- src/main/java/com/graphnexus/common/logging/TraceIdFilter.java（MDC "traceId" 注入 + finally 清理）
- src/main/resources/logback-spring.xml（FILE_ERROR appender + [%X{traceId}] pattern，0 新增 appender）
- src/main/java/com/graphnexus/common/exception/{ErrorResponse,BusinessException,ErrorCode}.java（结构逐字不变）

新增模块：无

禁动（与本次无关 · AI 不许"顺手"碰）：
- pom.xml（禁动清单 · 不新增日志依赖，AC-4）
- docs/项目规范.md / docs/tech-stack-java.md（禁动清单）
- src/main/java/com/graphnexus/application/** 各 Service/Parser 的 L2 日志行为（REQUIREMENT v2 · 本次不改 L2）
- 前端 ErrorResponse 消费侧（响应体不变，前端无感）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| SLF4J Logger | 有 · `@Slf4j`（Lombok），`TraceIdFilter` 同包示例，42 文件在用 | **沿用** `@Slf4j` |
| traceId 关联 | 有 · `TraceIdFilter` → MDC `"traceId"` + logback `[%X{traceId}]` | **沿用**，不新增 |
| ERROR 日志归档 | 有 · `logback-spring.xml` `FILE_ERROR` appender → `graphnexus-error.log`（`ThresholdFilter=ERROR`） | **沿用**，不新增 appender |
| 异常分级分发 | 有 · `GlobalExceptionHandler` 4 个 `@ExceptionHandler` | **沿用**结构，仅补 log |
| 异常→响应映射 | 有 · `ErrorResponse`（5 字段 record）+ `ErrorCode` 枚举 | **沿用**，字段逐字不变 |

### 0.5.3 沿用模式 vs 引入新模式

```
- 日志门面：**沿用** SLF4J + Lombok @Slf4j（项目统一约定，0 文件手动 LoggerFactory）
- traceId 关联：**沿用** MDC + logback pattern（既有，不改）
- 错误日志归档：**沿用** FILE_ERROR appender（既有，0 新增 appender / 0 新依赖）
- 异常分级落日志：**引入新模式**——在既有 4 个 @ExceptionHandler 内按异常类型定级落日志
    → 理由：现状 4 个 handler 零 log 调用（已 grep 验证），无既有日志抽象可复用，本次核心目的即补全
- 防重复日志：**引入新模式**——级别去重约定（业务/校验/权限 WARN · 兜底 ERROR），不引新基础设施
    → 理由：既有无去重机制；靠 logback ThresholdFilter(ERROR) + 分级天然实现 WARN 不入 error 日志
```

---

## 1. 决策清单

> 覆盖 REQUIREMENT/CHANGE 三个待定项：① 日志分级具体内容（D2/D3/D5）· ② 覆盖边界（D6）· ③ L2 vs handler 防重复职责（D4）。

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| **D1** | Logger 引入方式：`@Slf4j`（Lombok） | 手动 `LoggerFactory.getLogger` / 引新日志框架 | 项目统一约定（42 文件 `@Slf4j` / 0 手动），零新增依赖（Lombok 已在 pom），满足 AC-4「仅用既有 SLF4J」 | 类加一个注解（极小） |
| **D2** ① | 兜底 `handleGeneralException`：**ERROR + 完整堆栈**（`ex` 作 SLF4J throwable 末参） | WARN 摘要 / 不打日志（现状） | 兜底=未被预期、未被上游记录的系统故障，完整堆栈（类名+`getMessage()`+cause 链+行号）是定位根因的唯一现场；ERROR 级经 `ThresholdFilter` 入 `graphnexus-error.log`（AC-1） | 完整堆栈增大 error 日志体积——既有 10MB×30 天×500MB 滚动可承接（CHANGE 风险段已评估），TEST 观测 |
| **D3** ① | 业务/校验/权限：**WARN 摘要**（不传 throwable，无堆栈） | ERROR 完整堆栈 / 不打日志 | ① 预期内异常不应污染 error 日志（AC-2）；② WARN 经 `ThresholdFilter(ERROR)` 阻断，不入 `graphnexus-error.log`；③ `BusinessException` 构造器不接收 `Throwable cause`（已读源码验证），结构上无 cause 堆栈可打，摘要（errorCode+httpStatus+errorMessage）已足够 | 若 L2 未按约定记录被 wrap 的技术 cause，该 cause 堆栈不在日志——v2 由 L2 enforcement 解决，本次 out-of-scope（D4） |
| **D4** ③ | 防重复日志：**级别去重 + 职责分层**——handler 作响应边界唯一落日志点（按 AC 落 WARN/ERROR）；L2 既有 `log.error(cause,e)` 作技术根因记录**保留不改** | ① handler 不落日志只靠 L2（违反 AC-1/AC-2，兜底无人记录）；② handler 落日志且强制 L2 停止记录（改 L2，超范围） | 现状无「同级别同内容」真重复：grep 证实 L2 的 `log.error(...,e)` 必配 `throw new BusinessException(...)`（raw cause 被 wrap 消耗，不入兜底），故**兜底 ERROR 与 L2 ERROR 不重叠**；BusinessException 经 handler WARN 落摘要，与 L2 ERROR 根因级别/内容不同，互补非重复。详见 §2.3 | 个别 L2 已落 WARN 又被 handler 落 WARN 的 BusinessException 有 ≤2 行 WARN（内容不同），可接受；v2 L2 normalization 清理 |
| **D5** ① | traceId 关联：**复用 MDC `[%X{traceId}]` pattern**，不在 message 体重复写 traceId | 在 message 里拼 traceId | logback pattern 已为每行注入 traceId（单一来源），response body traceId 同源（`MDC.get("traceId")`），保证 AC-1「日志 traceId == 响应 traceId」且无双值漂移 | 无（message 不自含 traceId，但每行已有） |
| **D6** ② | 覆盖边界：**仅运行期 Controller 异常**（`@RestControllerAdvice` 捕获范围 = `DispatcherServlet` 调度链内） | 增加启动期异常捕获机制 | `@RestControllerAdvice` 由 `DispatcherServlet` 内 `ExceptionHandlerExceptionResolver` 调用；启动装配期（`ApplicationContext.refresh()`）未就绪、Filter 链 `DispatcherServlet` 之前均不在覆盖范围。CHANGE 触发实例类名出现在 500 响应体 `errorMessage`（`"系统内部异常: NoSuchBeanDefinitionException"`，只能由 `handleGeneralException` 产生）= **运行期流经 handler 的铁证**，IN scope；启动期异常不产生 HTTP 响应（应用启动失败），out-of-scope | 启动期 `NoSuchBeanDefinitionException` 仍无结构化日志——v2 独立机制处理（见 §6） |
| **D7** | 响应体逐字不变：`ErrorResponse` 5 字段不变，`errorMessage` 仍 `系统内部异常: <SimpleName>` | 增强 `errorMessage` 带 `ex.getMessage()` | 安全边界——内部细节（message/堆栈/根因）只走日志不回前端（AC-3 / REQUIREMENT out） | 前端仍需凭 traceId 查日志——本次正是补全此链路 |

### 各 handler 日志内容规格（D2/D3 落地 · 伪代码，非完整方法体 · R3.1）

> traceId 由 logback `[%X{traceId}]` pattern 自动注入每行，下述 message 不含 traceId（D5）。

| handler | 级别 | message 字段（`{}` 占位） | throwable 末参 | 落 `graphnexus-error.log`？ |
|---|---|---|---|---|
| `handleGeneralException`（兜底） | **ERROR** | `兜底未捕获异常 errorCode={} httpStatus={} exception={}` ← `B0001` / `500` / `ex.getClass().getSimpleName()` | **传 `ex`** → 完整堆栈（类名+getMessage+cause 链+行号） | ✅ 是（AC-1） |
| `handleBusinessException` | **WARN** | `业务异常 errorCode={} httpStatus={} errorMessage={}` ← `ex.getErrorCode()` / `ex.getHttpStatus()` / `ex.getErrorMessage()` | **不传**（无堆栈） | ❌ 否（AC-2） |
| `handleValidationException` | **WARN** | `参数校验失败 fieldErrors={}` ← 已拼接的 `fieldErrors` | **不传** | ❌ 否（AC-2） |
| `handleAccessDeniedException` | **WARN** | `权限不足 errorCode={} message={}` ← `A0003` / `ex.getMessage()` | **不传** | ❌ 否（AC-2） |

> SLF4J 约定：throwable 作为**最后一个参数**且**不对应 `{}`** 时，logback 打印其完整堆栈；不传 throwable 则只打 message。兜底靠此机制输出 cause 链与行号（D2）。

---

## 2. 数据流 / 架构图

### 2.1 异常→日志→响应 数据流

```
HTTP Request
   │
   ▼
TraceIdFilter (OncePerRequestFilter)
   │  生成/提取 traceId → MDC.put("traceId")   ← logback [%X{traceId}] 数据源
   ▼
DispatcherServlet → Controller(L1) → Service(L2) → Repository(L3)
   │
   │  异常抛出路径（既有，不改）：
   │  ├─ L3 技术异常 → L2 捕获 → log.error("...", cause)【可选·既有·作根因记录】
   │  │                                → wrap BusinessException → throw   ← raw cause 被 wrap 消耗，不入兜底
   │  ├─ L2 业务异常 → throw BusinessException【多数站点无前置 log，已 grep 证实】
   │  └─ 未被任何层 wrap 的 raw Exception → 直达兜底【上游无人记录】
   ▼
GlobalExceptionHandler (@RestControllerAdvice)   ← ★ 本次唯一变更点
   │  MDC "traceId" 仍在（filterChain.doFilter 内，finally 尚未执行）
   │
   ├─ BusinessException            → log.warn(errorCode, httpStatus, errorMessage)   【无堆栈】
   ├─ MethodArgumentNotValidExc.   → log.warn(fieldErrors)                            【无堆栈】
   ├─ AccessDeniedException        → log.warn(errorCode, message)                     【无堆栈】
   └─ Exception (兜底)              → log.error(errorCode, httpStatus, SimpleName, ex) 【完整堆栈+cause+行号】
   │
   ▼
ErrorResponse(errorCode, errorMessage=类名级, userTip=泛化, traceId, timestamp)   ← 5 字段逐字不变(D7)
   │
   ▼
logback appenders（均含 [%X{traceId}] pattern）
   ├─ CONSOLE / FILE_PLAIN(dev) / FILE_JSON(非dev)  ← WARN + ERROR 全入，每行带 traceId
   └─ FILE_ERROR (ThresholdFilter=ERROR)            ← 仅 ERROR 入 → graphnexus-error.log
                                                         ▲
              兜底 ERROR 完整堆栈入此 ──────────────────┘   业务/校验/权限 WARN 被阻断不入此（AC-2）
```

### 2.2 traceId 一致性链路（AC-1 核心）

```
TraceIdFilter.MDC.put("traceId", T)
        │
        ├─(logback pattern)─→ 每条日志行 [T]            ┐
        │                                                  │  同源同值
        └─(getTraceId())──→ ErrorResponse.traceId = T    ┘

 ⇒ grep "T" logs/graphnexus-error.log  →  命中兜底 ERROR 完整堆栈  →  定位出错类与行（无需复现）
```

### 2.3 防重复日志职责界定（待定项 ③ · D4 详述）

```
场景 A：L2 已 log.error(cause,e) 后 wrap 抛 BusinessException
   L2   : ERROR  [T] 技术根因 + 完整堆栈 + 业务参数上下文   → 入 graphnexus-error.log
   handler: WARN  [T] 业务摘要（errorCode/httpStatus/errorMessage）→ 不入 error 日志
   ⇒ 互补非重复：不同级别 / 不同内容 / 回答不同问题（根因 vs 客户端收到了什么）

场景 B：L2 throw BusinessException 无前置 log（多数站点）
   handler: WARN  [T] 业务摘要                            ← 唯一记录
   ⇒ 无重复

场景 C：raw Exception 未被任何层 wrap，直达兜底
   handler: ERROR [T] 完整堆栈                            ← 唯一记录（上游无人 log）
   ⇒ 无重复；这正是本次修复的核心场景（NoSuchBeanDefinitionException 触发实例属此）
```

> 关键事实（已 grep + 读源码验证）：① L2 的 `log.error(...,e)` 站点**必配** `throw new BusinessException(...)`，raw cause 被 wrap 消耗 → 兜底不会二次记录同一 cause；② `BusinessException` 构造器**不接收** `Throwable cause` → 结构上无 cause 堆栈可打，handler WARN 摘要是唯一合理选项。两者共同保证「无同级别同内容真重复」。

---

## 3. 关键状态机

N/A——本 change 无状态机。异常处理为无状态的分发+落日志+响应映射。

---

## 4. ADR 索引

**本 change 不新增 ADR**（约束：CHANGE.md「影响面」+ REQUIREMENT「out」明确——日志分级属**可逆工程决策**，非不可逆架构决策）。

- 日志分级 / 防重复 / 覆盖边界约定已作为既有「异常处理流」设计的增量，记入 `@.specs/CONTEXT.md`「已锁技术决策」段（`异常日志分级` / `系统异常日志策略` / `前端 ErrorResponse 不增强` / `异常日志零新增基础设施` / `后端异常定位路径`，REQUIREMENT 阶段已写入）。
- 现有 ADR 目录 `.specs/adr/001~023` 无与本 change 冲突项（已 ls 核对）；本 change 不 supersede 任何既有 ADR。

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| **R1** | 完整堆栈无行号（JVM 调试信息被剥离）→ AC-1「出错行号」落空 | 兜底堆栈缺行号，定位降级到类级 | 低 | 已核实 `pom.xml` `maven-compiler-plugin` 无 `<debug>false</debug>`，Maven 默认 `debug=true` 保留行号；TEST 阶段实际触发一次兜底异常，断言堆栈含行号 |
| **R2** | 未来 L2 新增「`log.error(cause,e)` 后直接 throw raw Exception（不 wrap）」→ raw cause 既被 L2 记 ERROR 又入兜底被 handler 记 ERROR，双写完整堆栈入 error 日志 | `graphnexus-error.log` 出现重复完整堆栈，体积膨胀 | 中（依赖未来编码纪律） | D4 已声明约定「L2 `log.error` 必配 wrap」；v2 L2 enforcement 固化；本次 TEST 覆盖兜底单写场景 |
| **R3** | L2 日志「未必 enforced」——存在 `throw BusinessException` 不记 cause 的站点，被 wrap 的技术异常 cause 堆栈丢失（handler WARN 摘要无法补） | 个别业务异常的根因不可溯 | 中（现状即如此） | 列入 v2「L2 日志职责 enforcement」（REQUIREMENT v2）；本次不改 L2（out-of-scope）。**已知债务，非本次引入** |
| **R4** | MDC 时序：异常日志须在 `TraceIdFilter.finally { MDC.remove }` 之前输出，否则日志无 traceId | 日志缺 traceId，AC-1 检索链路断 | 低 | `@RestControllerAdvice` 在 `filterChain.doFilter` 内执行，MDC 此时仍在，时序正确；TEST 用 `ListAppender` 断言日志 traceId 非空且 == 响应 traceId（AC-1 验证方式） |
| **R5** | 兜底完整堆栈增大 error 日志体积 | `graphnexus-error.log` 增长加快 | 低（兜底非热路径，仅系统故障时） | 既有 500MB `totalSizeCap` + 30 天 `maxHistory` + 10MB 滚动可承接；TEST 阶段观测实际增长 |

> 含实现风险（R1/R4）/ 上线风险（R2/R5）/ 长期债务（R3）各一条，满足自检。

---

## 6. 不在范围

> 「这次设计不解决但未来需要」——避免 TASK 阶段误以为遗漏。

- **启动装配期异常**（`NoSuchBeanDefinitionException` 等发生在 `ApplicationContext.refresh()` 阶段）→ `@RestControllerAdvice` 未就绪，应用启动失败不产生 HTTP 响应；v2 独立机制处理（REQUIREMENT v2）。
- **Filter 链 / `DispatcherServlet` 之前的异常**（如安全过滤器抛出）→ 不在 `@RestControllerAdvice` 覆盖范围。
- **L2/L3 分层异常日志职责 enforcement**（`CONTEXT.md`「L2 捕获并记日志」约定的落实与归一化）→ v2，本次不改 L2。
- **异常日志含请求上下文**（method / URI / 参数摘要）→ v2（当前仅 traceId + 堆栈/摘要）。
- **修复触发本次的具体 `NoSuchBeanDefinitionException` bean 装配问题**→ 后续单独排查（CHANGE 范围排除）；有了新日志反而更易定位。
- **新增 `@ExceptionHandler`**（如 `ConstraintViolationException` for `@Validated` 路径参数、`HttpMessageNotReadableException` 等）→ 不新增，仍走兜底；新增 handler 超出本次范围（R7）。
- **前端 `ErrorResponse` 字段结构或 `userTip` 增强**→ out（永远不做，安全边界）。

---

## 9. 架构沉淀建议（供 `A-evolve` 同步用 · 软约束）

> 本 change 不新增可复用抽象 / 不改跨模块契约 / 不动依赖 / 不动禁动清单。日志分级与防重复的高层约定已在 REQUIREMENT 阶段写入 `CONTEXT.md`「已锁技术决策」。仅一条**项目级技术决策**值得 `A-evolve` review 后补入 CONTEXT，使「级别去重」成为后续所有异常日志的成文准则。

### 9.2 项目级技术决策（建议 append 到 CONTEXT「已锁技术决策」段）

| 决策 | 取值 | 来源 |
|---|---|---|
| 异常日志级别去重约定 | `GlobalExceptionHandler` 对 `BusinessException`/校验/权限用 **WARN**（不与 L2 既有 `log.error(cause)` 重复入 `graphnexus-error.log`）；兜底 `Exception` 用 **ERROR**（上游无人记录时的唯一完整现场）。防重复靠 logback `ThresholdFilter(ERROR)` + 分级天然实现，不引新基础设施 | `exception-traceability` DESIGN D4 |

### 9.1 / 9.3 / 9.4 / 9.5

- 9.1 新增可复用抽象：N/A（无新建 `lib/util/service` 类）
- 9.3 跨模块契约：N/A（`ErrorResponse` 契约不变）
- 9.4 依赖变动：N/A（`pom.xml` 禁动，0 改动）
- 9.5 禁动清单变动：N/A

---

## 自检

- [x] 技术栈已锁定（§0 引用 CONTEXT 已锁决策，不变栈）
- [x] 既有架构对齐已写入（§0.5 含触碰模块清单 + 沿用对照表 + 沿用 vs 引新决策 + 禁动清单）
- [x] 每条决策都有「备选 + 理由 + 代价」（§1 D1~D7）
- [x] 至少一张数据流 / 架构图（§2.1/2.2/2.3）
- [x] 风险 ≥ 3 条且每条有缓解（§5 R1~R5）
- [x] 大的或可逆性低的决策都有对应 ADR——本 change 无不可逆决策，不新增 ADR（§4，符合 CHANGE/REQUIREMENT 约束）
- [x] 不含完整代码实现（§1 日志内容规格为伪代码/签名级，R3.1）
- [x] §9 架构沉淀建议已写（1 条项目级决策提名，不凑数）
- [x] 三个待定项均已落地：① 日志分级内容（D2/D3 + §1 规格 + §2.1）· ② 覆盖边界（D6 + §6）· ③ 防重复职责（D4 + §2.3）

---

> 下一步：后端 / lib 项目 → `@flow-kit/prompts/3-task.md`（拆原子任务，每任务含可执行 verify）。
