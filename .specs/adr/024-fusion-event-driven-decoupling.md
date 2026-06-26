# ADR-024: fusion 事件驱动解耦与模块依赖方向（construction → 事件 → fusion）

- **日期**: 2026-06-21
- **状态**: accepted
- **来源**: `fusion-to-analysis-event-driven` DESIGN（§1 D11），泛化 `@.specs/adr/018-grade-event-driven-decoupling.md`

---

## Context

`fusion-to-analysis-event-driven` 将 fusion 全栈从 `application/graph/` 迁入 `application/analysis/`，使 fusion（产出规范 KP + `MASTERS` 衍生边，正是剪枝/诊断消费的图数据）与剪枝、诊断同属"图变换/聚合服务于分析"范畴，缓解 graph 模块过重。

但单纯搬包会恶化耦合：当前 `ConstructionServiceImpl`（graph 模块）**直接注入并调用** `FusionService` 作为"阶段二融合"（`ConstructionServiceImpl.java:54,85,150` `fuseIncremental` 直接调用）；成绩路径 `GradeUploadedEventListener`（`@Order(2)`）**直接监听** `GradeUploadedEvent` 调 `fuseFull`（`GradeUploadedEventListener.java:39`），与构建监听器 `GradeGraphEventListener`（`@Order(1)`）靠 `@Order` 隐式排序串联。若 fusion 迁到 analysis 后仍保留这层直接调用，将形成 graph→analysis 的跨模块 Service 直接依赖，违背 ADR-018 确立的"模块间通过事件解耦、不直接注入对方 Service"精神。

此外当前融合触发有两条不一致路径（文档路径直接调用、成绩路径 `@Order` 隐式排序），顺序依赖 `@Order` 数值，脆弱（ADR-018 Consequences 已记此隐患）。

备选方案：
- **方案 A**：事件驱动解耦 — 构建完成发布 `GraphConstructedEvent`，fusion 模块单一监听器同步消费触发融合；依赖方向收敛为 `construction(graph) → 事件 → fusion(analysis)`
- **方案 B**：中间接口编排 — construction 注入 analysis 暴露的 `FusionTriggerPort` 接口（而非直接 `FusionService`），仍为同步直接调用
- **方案 C**：保持现状直接调用 + `@Order`，仅搬包不改触发入口

## Decision

采用 **方案 A：事件驱动解耦**，并确立项目级模块依赖方向规则。

### 核心规则（项目级）

1. **依赖方向**：`construction(graph) → 事件 → fusion(analysis)`。构建方发布事件，聚合方（fusion）消费事件，二者不直接注入对方 Service。
2. **禁止 analysis 反向注入 graph 的 Service**（construction/metrics 等）。ArchUnit 强制：`application.analysis..` 不依赖 `application.graph..service..`。
3. **禁止 construction 注入 `FusionService`**。构建与融合仅经 `GraphConstructedEvent` 通信。
4. **事件载荷自包含**：`GraphConstructedEvent` 载荷（`source`/`mode`/`subject`/`kpNames`/`documentId`/`examNo`）为纯数据，发布方不引用消费方类型。
5. **事件契约归发布方模块**：`GraphConstructedEvent` 类位于 `application/graph/construction/event/`（发布方 graph 模块），消费方 `GraphConstructedEventListener`（analysis）import 之。沿用 ADR-018 模式（`GradeUploadedEvent` 归发布方 file 模块）。运行时流 graph→analysis 与编译时依赖 analysis→graph（消费方→发布方事件类型）的标准事件倒置。

### 选择理由

1. **符合 REQUIREMENT 架构约束**（AC-2/AC-8/AC-10）：construction 不注入 `FusionService`；analysis 不反向依赖 graph Service；`GraphConstructedEvent` 为构建完成唯一融合触发源（手动 `FusionController.execute` 除外）。
2. **与既有事件模式一致**：项目已有 `GradeUploadedEvent` + `GraphChangedEvent` + `ApplicationEventPublisher` + 同步 `@EventListener` 模式（ADR-018/`MetricsCacheInvalidator`），团队熟悉。
3. **同步执行保证一致性**：Spring 事件默认同步（同线程），`@EventListener` join 发布方 `@Transactional`，融合在监听器内完成后再返回 HTTP 响应，文档上传单请求仍返回 `COMPLETED`/`EXTRACTED+fusionWarning`（AC-4/AC-5，对齐 `CONTEXT.md` L151 同步链路）。
4. **消除 `@Order` 隐式排序**：把"构建完成→发事件→融合"编码进显式事件链（`GradeGraphEventListener` 在 KP+TESTED 写入循环后 `publishEvent`），顺序由代码结构保证而非 `@Order` 数值。
5. **泛化 ADR-018**：ADR-018 为 grade→graph 单例方向规则；本 ADR 把"构建方→事件→聚合方"确立为项目级方向规则，未来跨模块协作循此。

方案 B（中间接口）虽解耦实现，但 construction 仍需注入接口，不如事件模式彻底隔离，且不消除 `@Order` 隐式排序。方案 C 不符合架构约束，耦合随搬迁恶化。

### 具体实现（契约级，非代码实现 · R3.1）

- `ConstructionServiceImpl.extract()` 移除 `FusionService` 注入与 `phase2_fuse` 直接调用；phase1 构建完成后 `publishEvent(GraphConstructedEvent(source=DOCUMENT, mode=INCREMENTAL, ...))`。
- `GradeGraphEventListener`（移除 `@Order(1)`）构建完成后 `publishEvent(GraphConstructedEvent(source=CSV, mode=FULL, ...))`。
- 新增 `GraphConstructedEventListener`（`application/analysis/fusion/event/`，同步 `@EventListener`，无 `@Order`）：按 `source`/`mode` 分支调 `fuseFull`/`fuseIncremental`；DOCUMENT 路径推进文档状态机 `FUSING→{COMPLETED, EXTRACTED+failReason}`（失败 try/catch 吞异常不回滚构建）；融合经 `FusionServiceImpl` 发 `GraphChangedEvent`（缓存失效链路保留）。
- 删除 `GradeUploadedEventListener`（`@Order(2)`）整类，fuseFull 逻辑迁入 `GraphConstructedEventListener`。
- 融合原子性沿用 ADR-020（`FusionServiceImpl` 内 `TransactionTemplate`），不引 Spring `@Transactional` 包融合。

## Consequences

- **正面**：
  - construction 依赖从含 `FusionService` 减为不含；analysis 不反向依赖 graph Service。模块边界清晰，graph 模块职责减轻（仅 construction + metrics）。
  - "构建完成→融合"从两条不一致路径（直接调用 + `@Order`）统一为单一显式事件契约，消除 `@Order` 脆弱排序。
  - 未来若需异步融合（大规模数据），只需在 `GraphConstructedEventListener` 加 `@Async`，发布方零改动（与 ADR-018 一致的扩展点）。
  - ADR-018 的方向规则被泛化为项目级，后续跨模块协作有规可循。
- **负面**：
  - 调试复杂度增加：extract/上传链路从"Service 直接调 phase2"展开为"Service → Event → Listener → FusionService"，跨类排查更费时。
  - analysis-fusion 监听器注入 `TextbookRepository` 跨文档域写文档状态（DOCUMENT 路径），属模块边界软违反（DESIGN §5 R3）——由同步耦合需求（AC-4 同步响应携带 fusionWarning）正当化，长期可经 `DocumentStatusPort` 接口解耦（v2）。
  - 事件类归发布方 graph 模块（D10），analysis 编译时依赖 graph 的 `construction.event` 包（仅事件类型）——事件驱动固有的依赖倒置，ADR-018 已先例。
  - `EXTRACTED-on-failure` 修正改变可观测行为（当前卡 FUSING → 新 EXTRACTED+failReason），REVIEW 须回归并显式确认（DESIGN §5 R5）。
- **风险缓解**：
  - 嵌套同步事件（grade 路径 `GradeUploadedEvent` 内 `publishEvent(GraphConstructedEvent)`）：`GraphConstructedEventListener` 全程 try/catch 吞融合异常不抛出，避免冒泡回滚上传事务（DESIGN §5 R1）。
  - 同 tx 结果回传依赖 JPA 一级缓存：监听器 `findById` 同一托管实例后 mutate+save，`extract()` 重读 `doc.status`+`failReason` 填 `fusionWarning`；AC-4 测试覆盖（DESIGN §5 R6/D3）。
  - ArchUnit 守护依赖方向：`application.analysis..` 不依赖 `application.graph..service..`（AC-8），防回退。
