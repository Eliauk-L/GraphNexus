# CHANGE: 图融合迁入图分析模块 + 构建完成事件驱动触发融合

- **Change ID**: `fusion-to-analysis-event-driven`
- **创建日期**: 2026-06-21
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: confirmed
- **用户决策**:
  - Q1 融合时序: A — 同步事件（`@EventListener` 同步执行，保留 `CONTEXT.md` L151 单请求 COMPLETED 同步链路，前端零改动）
  - Q2 事件模型: 新增 `GraphConstructedEvent` — 文档+成绩两条构建路径完成后统一发布，fusion 模块单一监听器消费；替换 `ConstructionServiceImpl` 直接调用 + grade `@Order(2)` 监听器
  - Q3 REST 路径: 迁移到 `/api/v1/analysis/fusion/*` — 前端融合管理 UI + API 文档同步改

---

## Why（为什么做）

当前 `application/graph/`（图处理模块）承载三个子域：`construction`（构建）+ `fusion`（融合）+ `metrics`（指标），职责过重；而 `application/analysis/`（图分析模块）仅有 `SubgraphPruningStrategy` + `StudentDiagnosisStrategy` + 1 个子图查询端点，功能单一。

从职责内聚看，**fusion 概念上属于"为分析准备统一宽图谱"的聚合层**——它产出规范 KP 节点与 `MASTERS` 衍生边（掌握度聚合），正是分析层剪枝/诊断所消费的图数据。把 fusion 归入 analysis 模块，与剪枝、诊断同属"图变换/聚合服务于分析"的范畴，比留在以"写入原始图"为主的 graph 模块更内聚，同时缓解 graph 模块过重。

但单纯搬包会恶化耦合：当前 `ConstructionServiceImpl`（graph 模块）**直接注入并调用** `FusionService` 作为"阶段二融合"（`@.specs/CONTEXT.md` L164 两阶段流水线）。若 fusion 迁到 analysis 后仍保留这层直接调用，将形成 graph→analysis 的跨模块 Service 直接依赖，违背 `ADR-018`（成绩模块事件驱动解耦）确立的"模块间通过事件解耦、不直接注入对方 Service"精神。因此搬迁必须**同步以事件解耦**：构建完成发布事件 → fusion 监听器消费，使依赖方向收敛为 `construction(graph) → 事件 → fusion(analysis)`。

此外，当前融合触发有两条不一致的路径：
1. **文档路径**：`ConstructionServiceImpl.extract()` 阶段二**直接调用** `fuseIncremental()`（紧耦合）
2. **成绩路径**：`GradeUploadedEventListener`(@Order(2)) **直接监听** `GradeUploadedEvent` 调 `fuseFull()`，与构建监听器 `GradeGraphEventListener`(@Order(1)) 靠 `@Order` 隐式排序串联——并非真正的"构建完成"事件驱动，顺序依赖 `@Order` 数值，脆弱

引入 `GraphConstructedEvent` 统一两条路径的融合触发入口，消除 `@Order` 隐式排序，使"构建完成→融合"成为显式契约。

## What（做什么）

### 1. fusion 全栈从 graph 模块迁入 analysis 模块

- **api 层**：`api/graph/dto/fusion/*`（`FusionExecuteVO`/`FusionStatusVO`/`FusionRollbackVO`）→ `api/analysis/dto/fusion/*`；`FusionController` 从 `api/graph/controller/` → `api/analysis/controller/`，`@RequestMapping` 由 `/api/v1/graph/fusion` → `/api/v1/analysis/fusion`
- **application 层**：`application/graph/fusion/*`（`config`/`event`/`model`/`service`/`service/impl`/`strategy` 全部）→ `application/analysis/fusion/*`
- **infrastructure 层**：`infrastructure/mysql/fusion/*`（`FusionLogDO`/`FusionLogRepository`）随之归入 analysis 子域；`infrastructure/neo4j/repository/FusionGraphRepository` 与 `infrastructure/neo4j/edge/MastersEdge` 当前位于 neo4j 共享平铺目录（与其它 edge/repository 并列），**是否随之迁 analysis 子目录由 DESIGN 阶段定**（权衡 ADR-021 repository 按子域拆分原则 vs neo4j edge/repository 平铺组织惯例）
- **前端**：`frontend/src/api/graph.ts` 中 3 个融合调用（`/graph/fusion/execute`、`/graph/fusion/status`、`/graph/fusion/rollback/{id}`）→ `/analysis/fusion/*`；`frontend/src/views/fusion/`（`FusionManagePage.vue` + `fusionStore.ts`）调用同步更新

### 2. 构建完成事件驱动触发融合（同步）

- **新增 `GraphConstructedEvent`**（载荷：`source`[DOCUMENT/CSV]、`subject`、`kpNames`、`mode`[FULL/INCREMENTAL]、`documentId`/`examNo`），发布点在构建阶段**全部完成之后**
- **文档路径**：`ConstructionServiceImpl.extract()` 阶段一构建完成后发布 `GraphConstructedEvent(mode=INCREMENTAL, kpNames=affectedKpNames)`；**移除** `phase2_fuse` 直接调用 `fusionService.fuseIncremental()` 与 `FusionService` 注入
- **成绩路径**：`GradeGraphEventListener`(@Order(1)) 构建完成后发布 `GraphConstructedEvent(mode=FULL)`；**移除** 原 `GradeUploadedEventListener`(@Order(2)) 中直接 `fuseFull()` 调用
- **新增 `GraphConstructedEventListener`**（位于 `application/analysis/fusion/event/`，同步 `@EventListener`）：消费事件 → 按 `mode` 调 `fuseFull`/`fuseIncremental` → 发布 `GraphChangedEvent`（保留指标缓存失效链路）；内部 try/catch 吞异常标 `fusionWarning`，**不回滚构建**（对齐 `ConstructionServiceImpl.phase2_fuse` 现状与 `GradeUploadedEventListener` 现状）
- **同步语义保留**：`@EventListener` 同步执行，文档上传一次 HTTP 请求仍返回 `COMPLETED`（融合成功）或 `EXTRACTED`+`fusionWarning`（融合失败），不引入异步队列/MQ（遵守 `CONTEXT.md` L151）
- **文档状态机**：`FUSING→COMPLETED` 状态流转从 `ConstructionServiceImpl` 迁移到 fusion 监听器内；发布者 `extract()` 为 `@Transactional`(MySQL)，监听器同线程执行但事务边界需 DESIGN 明确（`@TransactionalEventListener AFTER_COMMIT` vs plain `@EventListener` + `REQUIRES_NEW`）

### 3. REST 契约迁移

- `/api/v1/graph/fusion/{execute,status,rollback}` → `/api/v1/analysis/fusion/{execute,status,rollback}`（破坏性变更）
- 前端融合管理 UI 调用路径同步；API 文档同步
- 是否保留旧路径 `/graph/fusion/*` 别名作过渡期（双写或 301）由 DESIGN 决定

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增模块搬迁 + 事件驱动触发的 AC；fusion 功能行为等价（仅改触发入口与归属 + 路径迁移）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① `GraphConstructedEvent` 事件契约（载荷/发布点/消费语义）；② fusion 归属 analysis 的模块边界决策（新 ADR，明确依赖方向 construction→事件→fusion）；③ 同步事件下文档状态机 `FUSING→COMPLETED` 跨 MySQL 事务边界处理；④ infra 层（`FusionGraphRepository`/`MastersEdge`/`FusionLogDO`）包路径取舍；⑤ REST 路径迁移与旧路径别名策略
- [x] 影响现有 AC — `graph-construction-refactor` REQUIREMENT 中"ConstructionService 编排两阶段流水线（构建→融合）"语义变化（融合改由事件触发，不再由 ConstructionService 直接编排）→ REVIEW 阶段需回归并显式声明本 change 对其的超越
- [ ] 影响数据模型 / 迁移 — 无 Neo4j/MySQL schema 变更，`fusion_log` 既存数据不动，`MASTERS` 边/`EdgeType` 枚举不变（纯代码搬迁 + 事件重构）
- [x] 影响外部 API 兼容性 — `/api/v1/graph/fusion/*` → `/api/v1/analysis/fusion/*` **破坏性**，前端融合管理 UI + API 文档需同步（frontend-ui 近期刚落地融合管理 UI）
- [ ] 仅修复 bug，无范围变化
- [ ] 依赖新模块 — 无新增 `pom.xml` 依赖，复用既有 Spring 事件机制（`ApplicationEventPublisher` + `@EventListener`）

## 核心设计约束（进入 DESIGN 前必须遵守）

- **fusion 行为等价**：搬迁 + 事件重构不改变融合语义（KP 匹配 / MASTERS 计算 / 回滚 / 融合日志），仅改触发入口与模块归属。必须有从既有 AC 派生的等价回归测试护航（R5.1），禁止从实现派生测试
- **同步链路不变**：`@EventListener` 同步执行，保留 `CONTEXT.md` L151 单请求 `COMPLETED`；不引入异步队列 / MQ / `@Async`
- **融合失败不回滚构建**：fusion 监听器内 try/catch 吞异常，标 `fusionWarning`，对齐 `ConstructionServiceImpl.phase2_fuse` 与 `GradeUploadedEventListener` 现状；`fusion_log.status=FAILED` 时图谱构建结果保留
- **`GraphConstructedEvent` 为构建完成唯一融合触发源**；`GraphChangedEvent` 语义不变（仍为"图谱变更→指标缓存失效"），由 fusion 监听器在融合完成后发布，供 `MetricsCacheInvalidator` 消费
- **模块依赖方向**：`construction(graph) → 事件 → fusion(analysis)`，**禁止** analysis 反向注入 graph 的 Service；**禁止** construction 再注入 `FusionService`。事件载荷自包含，发布方不依赖消费方
- **发布时机**：`GraphConstructedEvent` 必须在构建阶段全部完成后发布（文档路径：节点/边写入 Neo4j 之后；成绩路径：`GradeGraphEventListener` 内 ④ 步 KP+TestedEdge 写入之后），禁止早发导致融合读到不完整图
- **既有抽象沿用**：`FusionGraphRepository`、`EdgeType.MASTERS`、`MastersEdge`、`fusion_log`、`KpMatchingStrategy`/`WeightCalculationStrategy` 接口与实现均不变；遵循既有四层架构 + 构造器注入 + `ADR-021` repository 按子域拆分原则
- **遵循全局删除约束**：本 change 无多存储删除操作，不涉及 C1-C5

## 范围排除（这次不做）

- ❌ **metrics 模块搬迁**：仅 fusion 迁入 analysis，`metrics` 留在 graph 模块（图处理模块仍保留 construction + metrics 两子域）
- ❌ **异步融合 / 消息队列**：同步事件，不引入 MQ / `@Async` / 轮询融合状态
- ❌ **fusion 功能性增强**：不新增 KP 匹配策略 / 权重算法 / 回滚能力，保持 `wide-graph-fusion` 既有功能集
- ❌ **`GraphChangedEvent` 语义变更**：仍仅用于指标缓存失效，不承载"构建完成"语义
- ❌ **成绩模块事件契约变更**：`GradeUploadedEvent` / `GradeDeletedEvent` 不变，仅消费方迁移
- ❌ **Neo4j / MySQL schema 变更**：纯重构，无 DDL / 数据迁移
- ❌ **前端融合管理 UI 视觉重设计**：仅 API 路径同步与调用更新，无视觉/布局变更（若 DESIGN 确认无视觉改动，前端任务可跳过 `UI-DESIGN.md`，由 REQUIREMENT/DESIGN 阶段确认）
- ❌ **construction 模块搬迁**：construction 留在 graph 模块，仅以事件与 fusion 解耦
- ❌ **融合触发模式变更**：手动全量 + 自动增量双触发模式（`CONTEXT.md` L132）不变，仅触发入口由直接调用/`@Order` 改为 `GraphConstructedEvent`

## 验收线（粗粒度，不是 AC）

1. **fusion 全栈搬迁**：fusion 的 api-dto + application + infrastructure 代码从 graph 命名空间迁至 analysis 命名空间，编译通过，无残留 `application.graph.fusion` / `api.graph.dto.fusion` 包引用
2. **construction 解耦**：`ConstructionServiceImpl` 不再注入/调用 `FusionService`；阶段一构建完成后发布 `GraphConstructedEvent(mode=INCREMENTAL)`
3. **grade 路径统一**：`GradeGraphEventListener` 构建完成后发布 `GraphConstructedEvent(mode=FULL)`；原 `GradeUploadedEventListener`(@Order(2)) 直接 `fuseFull` 逻辑移除，由 `application/analysis/fusion/event/GraphConstructedEventListener` 统一消费
4. **同步链路保留（文档）**：文档上传一次 HTTP 请求返回 `COMPLETED`（融合成功）或 `EXTRACTED`+`fusionWarning`（融合失败），融合失败不回滚构建结果
5. **同步链路保留（成绩）**：成绩上传构建+融合串行完成，`GraphChangedEvent` 仍触发指标缓存失效
6. **REST 迁移**：`/api/v1/analysis/fusion/{execute,status,rollback}` 可用，前端融合管理 UI 调用新路径正常（手动全量融合 / 状态查询 / 回滚）
7. **融合行为等价**：`wide-graph-fusion` 既有测试矩阵全部通过，无语义回归（KP 匹配 / MASTERS 计算 / 回滚 / 融合日志行为不变）
8. **依赖方向正确**：analysis 不反向依赖 graph Service；construction 不依赖 fusion Service；事件载荷自包含

## 风险与未知

- **同步事件下文档状态机跨事务边界**：`ConstructionServiceImpl.extract()` 为 `@Transactional`(MySQL)，原 `phase2_fuse` 中 `FUSING→COMPLETED` 状态保存在同事务内；迁到 `@EventListener` 后监听器同线程但事务上下文可能不同，状态保存的事务边界需 DESIGN 明确。若处理不当，融合失败时文档状态可能与 Neo4j 实际状态不一致（状态显示 COMPLETED 但融合未完成，或反之）
- **`@Order` 隐式排序消除后的顺序保证**：原 grade 路径靠 `@Order(1)` 构建 / `@Order(2)` 融合保证构建先于融合；改 `GraphConstructedEvent` 后顺序由"发布点在构建完成后"显式保证，需确认 `GradeGraphEventListener` 内 ④ 步 KP+TestedEdge 全部写入后再发布事件，避免事件早发导致融合读到不完整图
- **REST 路径破坏性变更**：前端融合管理 UI（`frontend-ui` 近期落地）3 个调用点需同步，遗漏导致 404；是否保留旧路径 `/graph/fusion/*` 别名过渡期由 DESIGN 决定（建议 v1 直接迁移 + 前端同步，不保留别名，避免长期双契约维护）
- **超越 graph-construction-refactor 既有 AC**：其 REQUIREMENT 中"ConstructionService 编排两阶段流水线"AC 语义变化，REVIEW 阶段需回归并显式声明本 change 对其的超越（融合不再由 ConstructionService 编排，改由事件驱动）
- **infrastructure 包路径取舍**：`FusionGraphRepository`（`infrastructure/neo4j/repository/` 平铺）与 `MastersEdge`（`infrastructure/neo4j/edge/` 平铺）若迁 analysis 子目录会破坏 neo4j 层平铺组织惯例；若保留原位则 infra 与 application 模块归属不一致。DESIGN 需权衡并给出统一原则
- **融合失败语义一致性**：文档路径原 `phase2_fuse` 失败标 `fusionWarning` 且文档不进 `COMPLETED`（留在 `EXTRACTED`）；事件驱动后此语义需在监听器内复刻，DESIGN 需明确监听器如何回写文档状态（监听器需注入 `TextbookRepository` 或通过事件回传结果）

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。
