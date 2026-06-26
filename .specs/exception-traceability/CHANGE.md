# CHANGE: 全局异常处理器补全可追踪日志，使后端可凭 traceId 定位根因

- **Change ID**: exception-traceability
- **创建日期**: 2026-06-21
- **路径建议**: 完整（增量式）
- **状态**: active

---

## Why（为什么做）

系统异常时前端只收到泛化响应（`errorCode=B0001` + 异常类名 + 泛化 `userTip` + `traceId`），但后端开发拿到 `traceId` 后却无法在日志中定位根因。根因有三：

1. `GlobalExceptionHandler.handleGeneralException`（兜底）**完全不打日志**——没有 `log.error(..., ex)`，完整堆栈、异常 message、根因（cause 链）从未写入任何日志文件。
2. `errorMessage` 只带 `ex.getClass().getSimpleName()`，真正的异常信息（如「No qualifying bean of type 'X'…」）被丢弃。
3. 基础设施其实已就绪却未被使用：`logback-spring.xml` 已有 `[%X{traceId}]` 模式、独立 `graphnexus-error.log`、`TraceIdFilter` 已注入 MDC——traceId 链路是通的，只是异常处理器没往里写。

触发实例：`NoSuchBeanDefinitionException` 仅以类名形式返回前端，无法判断是哪个 bean、在哪个装配/调用环节缺失，只能盲查。本次把「异常不可定位」这个通用问题修掉。

## What（做什么）

在 `GlobalExceptionHandler` 的所有 `@ExceptionHandler` 方法中补全结构化异常日志，按异常类型分级落日志（具体级别与内容在 DESIGN 定），日志通过既有 `traceId`（MDC）关联，落入既有 `graphnexus-error.log`。覆盖所有异常类型（兜底 Exception / BusinessException / MethodArgumentNotValidException / AccessDeniedException）。前端 `ErrorResponse` 响应体保持不变——`userTip` 仍泛化、`errorMessage` 仍只带类名，不向客户端泄露内部实现。

## 视觉调性（前端项目必填，由 0-change 步骤 0.6 预选填入）

N/A——后端 API 项目，0-change 步骤 0.5 判定非前端项目，跳过 0.6。

## 影响面

- [x] 影响 `REQUIREMENT.md`（增量：新增「日志可追踪性」AC；现有 ErrorResponse 响应格式 AC 不变）
- [x] 影响 `DESIGN.md` / 引入新 ADR（增量扩展既有「异常处理流」设计段的日志策略：分级、内容、覆盖边界、防重复日志；**不引入新 ADR**——日志分级属可逆工程决策，非不可逆架构决策）
- [ ] 影响现有 AC（现有 ErrorResponse 格式/字段 AC 保持不变，本次只新增不修改）
- [ ] 影响数据模型 / 迁移
- [ ] 影响外部 API 兼容性（`ErrorResponse` 结构与字段逐项不变，前端无感）
- [ ] 仅修复 bug，无范围变化（系统性可观测性增强，非单点 bug 修复）

## 范围排除（这次不做）

- **不改前端 `ErrorResponse` 字段结构或 `userTip` 内容**——前端保持泛化，不向客户端暴露异常 message / 堆栈 / 根因（安全边界）。
- **不修复触发本次提案的具体 `NoSuchBeanDefinitionException`**——该 bean 装配问题后续单独排查；有了新日志反而更好定位。
- **不引入新日志收集/聚合基础设施**（ELK / SkyWalking / Loki 等）——仅复用既有 logback 配置与 `graphnexus-error.log`。
- **不增强 `errorMessage` 为带异常 message**——保持 `系统内部异常: <SimpleName>`，细节走日志而非响应。
- **不新增独立 ADR**——日志分级策略作为既有异常处理流设计的增量记录。
- **不覆盖 Spring 启动期异常**（待 DESIGN 确认边界）——`@RestControllerAdvice` 只覆盖 Controller 层抛出的运行期异常；若 `NoSuchBeanDefinitionException` 发生在启动装配期，不在本次捕获范围，需在 DESIGN 显式界定。

## 验收线（粗粒度，不是 AC）

- 任意异常被 `GlobalExceptionHandler` 捕获后，后端日志（`graphnexus-error.log` / 控制台）中可凭**响应返回的 `traceId`** 检索到完整堆栈 + 异常 message + 根因（cause 链），开发无需复现即可定位到出错类与行。
- 不同异常类型按既定级别落日志且不污染正常业务流程日志；前端响应体与变更前逐字段一致（回归无破坏）。

## 风险与未知

- **覆盖边界未知**：`NoSuchBeanDefinitionException` 可能发生在 Spring 启动装配期（早于 `DispatcherServlet` / `GlobalExceptionHandler` 生效），这类异常本次变更捕获不到。需在 DESIGN 确认：本次仅覆盖运行期 Controller 异常，启动期异常另行处理或显式声明 out-of-scope。
- **重复日志风险**：若上游（Filter / Interceptor / 业务层）已自行 `log` 过同一异常，兜底再 `log` 会双写。DESIGN 需 grep 现有日志点确认，避免重复。
- **日志量增长**：兜底异常打完整堆栈会增大 error 日志体积；既有滚动策略（10MB / 30 天 / 500MB cap）可承接，但生产异常频率未知，TEST 阶段观察。
- **触发实例的可达性待确认**：当前示例响应里出现 `NoSuchBeanDefinitionException` 类名，说明它确实流经了 `handleGeneralException`（运行期 bean 查找失败），属本次覆盖范围；但仍需 DESIGN 确认是运行期而非启动期。

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。
