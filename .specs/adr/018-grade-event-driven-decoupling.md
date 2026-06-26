# ADR-018: 成绩模块事件驱动解耦图谱操作

- **日期**: 2026-06-19
- **状态**: accepted
- **来源**: `grade-management-refactor` DESIGN，REQUIREMENT § 架构约束

---

## Context

`grade-management-refactor` REQUIREMENT 明确要求："成绩处理模块应通过 Spring 事件驱动后续图谱操作，而非直接调用图谱业务代码"。当前实现中 `GradeUploadService` 直接注入 `GraphNodeRepository` 并在上传方法内完成 Neo4j 节点和边的创建，`GradeServiceImpl` 直接注入 `GraphNodeRepository` 并在删除方法内完成 Neo4j 节点和边的清理。这违反了模块隔离原则。

备选方案：
- **方案 A**：事件驱动解耦 — 成绩模块发布事件，独立 Listener 处理图谱操作
- **方案 B**：Service 层编排 — 成绩模块调用一个中间的 `GraphBuildService` 接口（而非直接调用 `GraphNodeRepository`）
- **方案 C**：保持现状，`GradeUploadService` 和 `GradeServiceImpl` 继续直接调用 `GraphNodeRepository`

## Decision

采用 **方案 A：事件驱动解耦**。

选择理由：
1. **符合 REQUIREMENT 架构约束**：`application/file/grade/` 模块不注入 `GraphNodeRepository`，图谱操作完全隔离
2. **与既有事件模式一致**：项目已有 `GradeUploadedEvent` + `ApplicationEventPublisher` + `@EventListener` 模式，团队熟悉
3. **同步执行保证一致性**：Spring 事件默认同步（同线程），图谱构建/清理在事件监听器中完成后再返回 HTTP 响应，用户感知不到异步行为
4. **单一职责**：成绩模块负责解析+MySQL，图谱模块负责 Neo4j，职责边界清晰
5. **可扩展**：新增其他"文件→图谱"业务时可复用此事件监听器模式

具体实现：
- `GradeUploadService.upload()` → 解析 → MySQL 写入 → `publishEvent(GradeUploadedEvent)`
- `GradeServiceImpl.deleteByExamNo()` → MySQL 物理删除 → `publishEvent(GradeDeletedEvent)`
- `GradeGraphEventListener` 监听两个事件 ← 注入 `GraphNodeRepository` + `ExamRecordRepository(只读)`
- 图谱构建 Listener（`@Order(1)`）在融合 Listener（`@Order(2)`）之前执行

方案 B（中间接口）虽然也解耦了实现，但 `GradeUploadService` 仍需注入接口，不如事件模式彻底隔离。方案 C 不符合架构约束。

## Consequences

- **正面**：
  - 成绩模块依赖从 5 个（FileParserRegistry + ExamRecordRepository + FileStorageService + GraphNodeRepository + EventPublisher）减为 3 个（FileParserRegistry + ExamRecordRepository + EventPublisher）
  - 删除 MinIO 依赖后成绩模块不再与任何基础设施（存储/图数据库）耦合
  - 未来若需异步化（如大文件上传后异步构建图谱），只需在 Listener 上加 `@Async`，业务代码零改动
- **负面**：
  - `GradeUploadedEvent` 语义变化：原来在 Neo4j 已构建后发布，现在在 MySQL 写入后发布。消费方（融合 Listener）需注意执行顺序
  - `@Order` 引入隐式依赖：图谱构建必须在融合之前完成。若 `@Order` 失效（极低概率），融合会找不到 KP 节点
  - 调试复杂度增加：上传/删除链路从单一方法展开为"Service → Event → Listener"，跨类排查更费时
- **风险缓解**：
  - `@Order` 失效场景：可在 `GradeUploadedEventListener` 中加防御性检查（`MATCH (kp:KnowledgePoint) WHERE kp.subject = $subject RETURN count(kp)` = 0 时打印 WARN 日志并跳过融合），避免静默失败