# ADR-051: 审计日志异步非阻塞写入策略

- **日期**: 2026-06-23
- **状态**: accepted
- **来源**: `ops-analytics` DESIGN

---

## Context

运营管理模块需要记录用户的关键操作（登录、文档上传、文档处理、智能问答）到 `audit_log` 表，用于系统使用量统计。REQUIREMENT AC-8 明确要求：审计日志写入失败不得阻塞主流程（登录/上传/问答仍正常完成）。

可选方案：
1. **`@Async` + 独立短事务**：主流程显式调用 `auditLogService.record()`，方法标注 `@Async`，在独立线程中执行 INSERT
2. **Spring 事件总线**：主流程发布 `AuditEvent`，由 `@EventListener` 异步消费写入
3. **AOP 切面**：`@Auditable` 注解自动拦截 Controller/Service 方法，切面中异步写日志

## Decision

选择 **`@Async` + 独立短事务**。

理由：
- 与项目既有 `@Async("queryAsyncExecutor")` 模式一致：`AsyncConfig` + `queryAsyncExecutor` 线程池（core=2, max=4, queue=100）已在 `TextbookParsedEventListener` 等中稳定使用
- 显式调用传入业务上下文：`auditLogService.record(userId, OperationType.LOGIN)` 可携带 `userId`、`operationType`、`resourceId`（如 documentId/taskId），为未来审计查询提供丰富上下文
- 天然满足 AC-8「不阻塞主流程」：`@Async` 方法抛异常被 Spring 框架捕获，不传播到调用方；方法内部 `try-catch` + WARN 日志兜底
- 独立短事务：`@Transactional(propagation = Propagation.REQUIRES_NEW)` 确保每条审计日志 INSERT 立即提交，不受主流程事务回滚影响

备选排除理由：
- **事件总线**：增加了一层间接性（发布事件 → 消费者 → 写日志），对简单的"旁路记一条日志"过于复杂；事件需要定义新的事件类和 Listener 类，代码量更大；主流程发布事件后不持有事件 ID，后续需要关联时无 handle
- **AOP 切面**：项目当前未使用 AOP（`@Aspect` 零出现），引入 AOP 会增加隐式行为和调试复杂度；AOP 难以传递业务上下文（如 `documentId`），需要反射解析方法参数，脆弱且不直观

## Consequences

- **正面**：写入链路最短（Service 调 Service），代码显式可追踪；独立线程池 + 短事务，性能影响最小（主流程仅增加一次 `@Async` 代理调用，< 1ms）
- **负面**：每个需要审计的 Service 方法需显式加一行 `auditLogService.record(...)` 调用，侵入式；若某 Service 遗漏调用，该操作不会被统计
- **风险**：`@Async` 方法在极端情况下的数据丢失（进程 crash 时线程池队列中未执行的任务丢失），但 REQUIREMENT AC-8 已接受这种权衡——统计数据允许少量不精确。快照采集基于已有的审计日志记录，丢失个别记录对统计趋势影响可忽略