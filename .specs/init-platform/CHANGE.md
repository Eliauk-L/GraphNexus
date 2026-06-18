# CHANGE: 搭建可运行的项目框架

- **Change ID**: `init-platform`
- **创建日期**: 2026-06-11
- **路径建议**: 最短（`TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

项目已具备完整的技术规范文档（[项目规范.md](../../docs/项目规范.md)）、技术选型方案（[tech-stack-java.md](../../docs/tech-stack-java.md)）、设计视图（[design-view/](../../docs/design-view/)）以及全量 Maven 依赖配置（[pom.xml](../../pom.xml)），但缺少一个可编译、可启动、分层清晰的项目骨架。没有骨架代码，后续任何业务功能开发（图谱构建、文档解析、LLM 集成等）都无法开始。

需要先将 `docs/项目规范.md` §1.4.2 定义的包结构和 §2.4 定义的公共模块落地为实际代码，形成可运行的项目框架。

## What（做什么）

基于 `docs/项目规范.md` 已定义的技术方案，搭建一个 **可编译、可启动、可测试** 的 Spring Boot 项目骨架：

1. **四层包结构**：创建 `api/`、`application/`、`infrastructure/`、`common/` 及其子包目录（不含业务代码）
2. **公共模块实现**：`common/` 下的统一异常体系（BusinessException、ErrorCode、ErrorResponse、GlobalExceptionHandler）、全链路 Trace ID（TraceIdFilter）、统一 API 响应体（ApiResponse）、统一分页响应体（PageResult）
3. **启动类调整**：确保 `GraphNexusApplication` 在无外部基础设施（Neo4j/MySQL/Redis/RabbitMQ）时可正常启动
4. **架构约束测试**：使用 ArchUnit 编写分层依赖校验测试，确保 L1→L2→L3 调用规则不被打破

## 影响面

- [ ] 影响 `REQUIREMENT.md` — 否，不涉及功能需求变更
- [ ] 影响 `DESIGN.md` / 引入新 ADR — 否，设计决策已在 `docs/项目规范.md` 中完成
- [ ] 影响现有 AC — 否，无已有 AC
- [ ] 影响数据模型 / 迁移 — 否
- [ ] 影响外部 API 兼容性 — 否
- [x] 仅框架搭建，无业务范围变化

## 范围排除（这次不做）

- ❌ **任何业务代码**：不写 Controller、Service 接口/实现、Repository（含 Neo4j 和 MySQL）、Entity/Node/Edge 模型
- ❌ **数据库表结构**：不创建 MySQL DDL、Neo4j 约束/索引
- ❌ **LLM 集成逻辑**：不实现 LLMGateway Service（框架已预留包路径）
- ❌ **前端界面 / Dashboard**
- ❌ **对接真实教务系统**
- ❌ **多租户 / SaaS 化**
- ❌ **视频/音频等非 PDF 多模态数据**
- ❌ **CI/CD 流水线配置**：不创建 GitHub Actions / Jenkinsfile（已有 Podman 部署方案，本次不纳入）

## 验收线（粗粒度，不是 AC）

1. **可编译**：`mvn compile` 零错误通过
2. **可启动**：`mvn spring-boot:run` 成功启动，Actuator `/actuator/health` 返回 UP
3. **架构约束可验证**：`mvn test` 中 ArchUnit 测试通过，校验 L1→L2→L3 分层依赖方向
4. **公共模块可复用**：`common/` 下的异常体系、Trace ID、统一响应体可在后续 task 中直接 import 使用

## 风险与未知

- **Spring Boot 自动配置冲突**：`pom.xml` 引入了 Neo4j/JPA/Redis/RabbitMQ 的 Starter，但本地开发无对应容器时启动会失败。需在 `application-dev.yml` 中排除对应自动配置或设为 lazy init
- **Spring AI Milestone 仓库可用性**：`spring-ai-openai-spring-boot-starter:1.0.0-M4` 依赖 Spring Milestones 仓库，需验证 Maven 可正常拉取
- **ArchUnit 版本兼容性**：需确认 ArchUnit 1.3.0 与 JDK 17 / Spring Boot 3.3.x 兼容

---

> 后续架构约束与任务拆解进入 `TASK.md`，本文件不再扩展。