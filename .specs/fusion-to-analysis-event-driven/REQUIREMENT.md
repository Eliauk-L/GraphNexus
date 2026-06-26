# REQUIREMENT: 图融合迁入图分析模块 + 构建完成事件驱动触发融合

- **Change ID**: `fusion-to-analysis-event-driven`
- **关联**: `@.specs/fusion-to-analysis-event-driven/CHANGE.md`、`@.specs/CONTEXT.md`、`@.specs/wide-graph-fusion/REQUIREMENT.md`、`@.specs/graph-construction-refactor/REQUIREMENT.md`

---

## 用户故事

- **US-1**：作为模块维护者，我想 fusion 全栈（api-dto + application + infrastructure）从 graph 模块迁入 analysis 模块，以便融合（产出规范 KP + `MASTERS` 衍生边，正是剪枝/诊断所消费的图数据）与剪枝、诊断同属"图变换/聚合服务于分析"的范畴，模块职责内聚，graph 模块不再过重。
- **US-2**：作为系统，我想图谱构建完成后通过统一的 `GraphConstructedEvent` 事件触发融合，以便消除文档路径的 `ConstructionServiceImpl` 直接调用与成绩路径的 `@Order` 隐式排序，使"构建完成→融合"成为显式契约，模块依赖方向收敛为 `construction(graph) → 事件 → fusion(analysis)`。
- **US-3**：作为 API 消费者（前端融合管理 UI），我想融合相关 REST 端点迁移到 `/api/v1/analysis/fusion/*`，以便端点归属与 fusion 代码归属一致（同在 analysis 命名空间）。
- **US-4**：作为系统管理员，我想融合触发入口变更后融合行为完全等价（KP 匹配 / MASTERS 计算 / 回滚 / 融合日志不变），以便本次重构是纯结构性搬迁 + 事件重构，不引入功能回归。
- **US-5**：作为教务人员/系统，我想文档上传与成绩上传的同步链路保持不变（一次请求返回 `COMPLETED` 或 `EXTRACTED`+`fusionWarning`；成绩构建+融合串行完成且指标缓存仍失效），以便前端与调用方零行为变更。

---

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · fusion 全栈迁入 analysis 命名空间（无残留）

- **Given** 重构完成
- **When** 在 `src/main/java` 下检索旧 fusion 包引用
- **Then** 不存在 `application.graph.fusion`、`api.graph.dto.fusion`、`api.graph.controller.FusionController`（旧位）的引用；fusion 的 api-dto / application / infrastructure(mysql) 代码均位于 analysis 命名空间（`api/analysis/...fusion`、`application/analysis/fusion/...`、`infrastructure/mysql/fusion/`）；项目编译通过
- **验证方式**: `grep -rn "application.graph.fusion\|api.graph.dto.fusion\|api.graph.controller.FusionController" src/main/java/` 返回空；`mvn -q compile` 输出 `BUILD SUCCESS`

### AC-2 · construction 与 fusion 解耦（文档路径事件驱动）

- **Given** 重构完成
- **When** 查看 `ConstructionServiceImpl`
- **Then** `ConstructionServiceImpl` 不再注入/引用 `FusionService`；阶段一构建完成（节点/边写入 Neo4j 之后）发布 `GraphConstructedEvent(mode=INCREMENTAL, kpNames=affectedKpNames, subject)`；不再直接调用 `fuseIncremental`
- **验证方式**: `grep -n "FusionService\|fuseIncremental" src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java` 返回空；`grep -n "GraphConstructedEvent\|publishEvent" 同文件` 命中发布点

### AC-3 · grade 路径统一事件触发

- **Given** 重构完成
- **When** 查看 `GradeGraphEventListener` 与原 `GradeUploadedEventListener`
- **Then** `GradeGraphEventListener` 在构建完成（KP + `TESTED` 边写入之后）发布 `GraphConstructedEvent(mode=FULL)`；原 `GradeUploadedEventListener` 不再直接调用 `fuseFull`（`@Order(2)` 融合逻辑移除）；新增 `GraphConstructedEventListener`（位于 `application/analysis/fusion/event/`，同步 `@EventListener`）统一消费事件，按 `mode` 调 `fuseFull`/`fuseIncremental`
- **验证方式**: `grep -rn "fuseFull" src/main/java/com/graphnexus/application/analysis/fusion/event/GradeUploadedEventListener.java`（或其旧位）返回空；`grep -rn "GraphConstructedEventListener" src/main/java/` 命中且位于 `application/analysis/fusion/event/`；`GradeGraphEventListener` 内含 `publishEvent(GraphConstructedEvent)`

### AC-4 · 同步链路保留（文档上传）

- **Given** 文档 `documentId=1` 状态为 `PARSED`，文本非空
- **When** 发送 `POST /api/v1/graph/construction/extract/1`
- **Then** 一次 HTTP 请求内构建 + 融合串行完成：成功时 HTTP 200 + `document.status=COMPLETED`；融合失败时 HTTP 200 + 响应含 `fusionWarning` + `document.status=EXTRACTED`（不进入 `COMPLETED`），且构建结果（Neo4j 节点/边）不被回滚
- **验证方式**: 正常抽取断言 `status=COMPLETED`；模拟融合失败（如临时关闭 Neo4j）断言响应含 `fusionWarning` 且 `status=EXTRACTED` 且 Neo4j 抽取节点仍存在

### AC-5 · 同步链路保留（成绩上传）+ 指标缓存失效

- **Given** 系统正常，已有融合后的 KP 与 Student `MASTERS` 边
- **When** 上传新成绩文件（成绩上传端点）
- **Then** 一次请求内构建（Student/Exam/`ATTENDED`/`TESTED`）+ 融合（`fuseFull`）串行完成；融合完成后发布 `GraphChangedEvent`，`MetricsCacheInvalidator` 清空 Caffeine 指标缓存
- **验证方式**: 上传成绩 → 同步返回成功 → Cypher 断言 `MASTERS` 已重算 → 触发一次指标查询缓存后再次变更图谱，断言指标缓存被失效重算（结果反映新图）

### AC-6 · REST 契约迁移 + 前端同步

- **Given** 重构完成，服务运行
- **When** 调用新端点 `POST /api/v1/analysis/fusion/execute`、`GET /api/v1/analysis/fusion/status`、`POST /api/v1/analysis/fusion/rollback/{fusionLogId}`
- **Then** 三个端点均可用（手动全量融合 / 状态查询 / 回滚行为与原 `/api/v1/graph/fusion/*` 等价，响应体结构不变）；前端 `frontend/src/api/graph.ts` 的 3 个融合调用已改为 `/analysis/fusion/*`，融合管理 UI（`FusionManagePage.vue` + `fusionStore.ts`）功能正常
- **验证方式**: `curl` 三个新端点断言 HTTP 200 + 响应结构不变；`grep -rn "/graph/fusion" frontend/src/` 返回空；前端融合管理页手动全量融合 / 状态查询 / 回滚操作通过
- **注**: 旧路径 `/api/v1/graph/fusion/*` 是否保留别名由 DESIGN 锁定；REQUIREMENT 默认假设不保留别名（旧路径 404），DESIGN 若改需同步更新本 AC

### AC-7 · 融合行为等价（既有测试矩阵全绿）

- **Given** `wide-graph-fusion` 与 `graph-construction-refactor` 既有融合相关测试（KP 模糊匹配 / MASTERS 时间衰减 / 缺考不计入 / 回滚幂等 / 跨源合并 / 跨学科不合并 / 融合原子性 / 策略可配置替换等）
- **When** 执行 `mvn test`
- **Then** 上述融合行为测试全部通过，无语义回归；`fusion_log` 记录格式、MASTERS weight 计算、回滚语义、策略配置切换行为不变
- **验证方式**: `mvn test` 输出 `BUILD SUCCESS`，融合相关测试 `Failures: 0, Errors: 0, Skipped: 0`；`wide-graph-fusion` AC-1~AC-5、AC-7~AC-14 关键等价点回归通过

### AC-8 · 模块依赖方向正确

- **Given** 重构完成
- **When** 运行架构依赖校验
- **Then** analysis 模块不反向依赖 graph 模块的 Service（construction/metrics）；construction 不依赖 fusion Service；`GraphConstructedEvent` 载荷自包含（发布方不引用消费方类型）
- **验证方式**: ArchUnit 测试断言 `application.analysis..` 不依赖 `application.graph..service..`（对应包规则）；`grep -rn "FusionService" src/main/java/com/graphnexus/application/graph/construction/` 返回空

### AC-9 · 融合失败不回滚构建（成绩路径等价）

- **Given** 成绩上传后融合因 Neo4j 异常失败
- **When** 上传成绩文件
- **Then** 构建结果（Student/Exam/`ATTENDED`/`TESTED`）保留，融合失败被 `GraphConstructedEventListener` 内 try/catch 吞掉并标记（`fusion_log.status=FAILED` 或对应 warning），不回滚构建；不向上抛出导致上传 HTTP 失败
- **验证方式**: 模拟融合失败 → 上传仍成功返回 → Cypher 断言构建节点存在 → `fusion_log` 记录失败状态

### AC-10 · `GraphConstructedEvent` 为构建完成唯一融合触发源

- **Given** 重构完成
- **When** 检索融合触发入口
- **Then** 不存在除 `GraphConstructedEventListener` 外的 `fuseFull`/`fuseIncremental` 自动触发调用方（手动 `FusionController.execute` 除外）；文档路径与成绩路径均经 `GraphConstructedEvent` 触发
- **验证方式**: `grep -rn "fuseFull\|fuseIncremental" src/main/java/` 仅出现在 `FusionController`（手动）、`GraphConstructedEventListener`、`FusionService`/`FusionServiceImpl` 内部；`ConstructionServiceImpl` 与 `GradeUploadedEventListener` 不再出现

### AC-11 · 存量测试与编译整体通过

- **Given** 重构完成
- **When** 执行 `mvn test`
- **Then** 全量编译 + 测试通过，URL 路径变更 / 包迁移 / Repository 注入切换后的测试已同步更新，无新增 `@Disabled`
- **验证方式**: `mvn test` 输出 `BUILD SUCCESS`，`Tests run: N, Failures: 0, Errors: 0, Skipped: 0`

---

## 范围切分

### v1（本次必做）

- fusion api-dto（`FusionExecuteVO`/`FusionStatusVO`/`FusionRollbackVO`）+ application（`config`/`event`/`model`/`service`/`service/impl`/`strategy` 全部）+ infrastructure(mysql)（`FusionLogDO`/`FusionLogRepository`）迁入 analysis 命名空间
- `FusionController` `@RequestMapping` 由 `/api/v1/graph/fusion` → `/api/v1/analysis/fusion`，迁至 `api/analysis/controller/`
- 新增 `GraphConstructedEvent`（载荷：`source`[DOCUMENT/CSV]、`subject`、`kpNames`、`mode`[FULL/INCREMENTAL]、`documentId`/`examNo`）+ `GraphConstructedEventListener`（同步 `@EventListener`，位于 `application/analysis/fusion/event/`）
- `ConstructionServiceImpl` 移除 `FusionService` 注入与 `phase2_fuse` 直接调用，阶段一构建完成后发布 `GraphConstructedEvent(mode=INCREMENTAL)`
- `GradeGraphEventListener` 构建完成后发布 `GraphConstructedEvent(mode=FULL)`；移除 `GradeUploadedEventListener`(`@Order(2)`) 的 `fuseFull` 直接调用
- 文档状态机 `FUSING→COMPLETED` 流转迁入 fusion 监听器（事务边界由 DESIGN 定）
- 融合失败 try/catch 吞异常 + 标记 `fusionWarning`/`fusion_log.status=FAILED`，不回滚构建
- `GraphChangedEvent` 由 fusion 监听器在融合完成后发布（语义不变，仍供 `MetricsCacheInvalidator` 消费）
- 前端 `frontend/src/api/graph.ts` 3 个融合调用 + `FusionManagePage.vue`/`fusionStore.ts` 改 `/analysis/fusion/*`；API 文档同步
- infra 层 `FusionGraphRepository`/`MastersEdge` 包路径取舍（按 DESIGN 决策执行）
- 既有融合测试等价回归 + 包迁移/路径迁移测试同步更新

### v2（下一轮考虑，不本次）

- **metrics 模块搬迁**：仅 fusion 迁入 analysis，metrics 留在 graph 模块（下次可评估）
- **异步融合 / 消息队列**：同步事件，不引入 MQ / `@Async` / 轮询融合状态（大规模数据异步融合属后续）
- **REST 旧路径别名/双写过渡期**：若 DESIGN 决定保留 `/graph/fusion/*` 别名，双契约维护属后续清理
- **construction 模块搬迁**：construction 留在 graph 模块，本次仅以事件解耦

### out（永远不做）

- **Neo4j / MySQL schema 变更 / 数据迁移**：纯重构，无 DDL，`fusion_log` 既有数据不动，`MASTERS` 边/`EdgeType` 枚举不变
- **`GraphChangedEvent` 语义变更**：仍仅用于指标缓存失效，不承载"构建完成"语义
- **成绩模块事件契约变更**：`GradeUploadedEvent` / `GradeDeletedEvent` 不变，仅消费方迁移
- **fusion 功能性增强**：不新增 KP 匹配策略 / 权重算法 / 回滚能力，保持 `wide-graph-fusion` 既有功能集
- **前端融合管理 UI 视觉重设计**：仅 API 路径同步与调用更新，无视觉/布局变更
- **融合触发模式变更**：手动全量 + 自动增量双触发模式（`CONTEXT.md` L134）不变，仅触发入口由直接调用/`@Order` 改为 `GraphConstructedEvent`

---

## 非功能性需求

- **性能**: 事件驱动重构不引入额外同步开销（`@EventListener` 同线程执行，无队列、无新网络 hop）；文档/成绩上传端到端耗时相对现状波动 ≤ 10%
- **可访问性**: 无（后端重构 + 前端仅路径同步，无视觉变更）
- **安全**: 融合/回滚端点管理员权限（Spring Security `@PreAuthorize`）保持不变；模块迁移不改变鉴权边界
- **兼容性**: `/api/v1/graph/fusion/*` → `/api/v1/analysis/fusion/*` **破坏性**（前端 + API 文档同步）；`fusion_log` 既有数据不动；`MASTERS` 边 / `EdgeType` 枚举不变；响应体结构（`FusionExecuteVO`/`FusionStatusVO`/`FusionRollbackVO` 字段）不变
- **可观测性**: `GraphConstructedEvent` 发布与消费记 INFO 日志（含 `source`/`mode`/`subject`/`kpNames` 数）；融合失败记 WARN/ERROR（含异常 + `fusionWarning`）；保留既有融合操作 INFO/ERROR 日志与 `fusion_log` 审计

---

## 依赖与假设

- **依赖**:
  - 既有 Spring 事件机制（`ApplicationEventPublisher` + `@EventListener`，`CONTEXT.md` L140）
  - `GraphChangedEvent` + `MetricsCacheInvalidator`（`application/graph/metrics/event/`，语义不变）
  - `wide-graph-fusion` 既有 fusion 全栈代码（搬迁源）
  - `frontend-ui` 融合管理 UI（`FusionManagePage.vue` + `fusionStore.ts` + `api/graph.ts`）
  - `graph-construction-refactor` 既有 `ConstructionServiceImpl` / `GradeGraphEventListener` / `GradeUploadedEventListener`（重构源）
- **假设**:
  - `@EventListener` 同步执行（同线程），文档上传单请求仍返回 `COMPLETED`（`CONTEXT.md` L153 同步上传链路不变）
  - `GraphConstructedEvent` 载荷自包含（`source`/`subject`/`kpNames`/`mode`/`documentId`/`examNo`），发布方不依赖消费方类型
  - 发布时机：文档路径在节点/边写入 Neo4j 之后；成绩路径在 `GradeGraphEventListener` 内 KP + `TESTED` 边写入之后（事务边界由 DESIGN 明确，禁止早发导致融合读到不完整图）
  - 文档状态机 `FUSING→COMPLETED` 跨 MySQL 事务边界处理、infra 层（`FusionGraphRepository`/`MastersEdge`）包路径取舍、REST 旧路径别名策略由 DESIGN 锁定
  - 融合行为等价基线 = 当前实现行为（见下方「对既有 AC 的影响」第 3 条）

---

## 对既有 AC 的影响（超越声明）

> 进入 REVIEW 阶段必须回归并显式声明本 change 对以下既有 AC 的超越。

1. **`graph-construction-refactor` AC-5「两阶段流水线—构建→融合」语义变化**：融合不再由 `ConstructionService` 直接编排（`phase2_fuse` 直接调用 `fuseIncremental`），改由 `GraphConstructedEvent` 事件驱动；`document.status` 的 `FUSING→COMPLETED` 流转迁出 `ConstructionServiceImpl`。REVIEW 需回归并显式声明对 AC-5 的超越。
2. **`graph-construction-refactor` AC-6「融合失败显式化」语义保留但实现位置迁移**：`fusionWarning` 标记 + `document.status` 停留 `EXTRACTED` 的契约不变，但由 fusion 监听器（而非 `ConstructionServiceImpl.phase2_fuse`）产出。REVIEW 需回归。
3. **`wide-graph-fusion` AC-6「上传后自动增量融合」措辞与当前实现偏差**：AC-6 原文措辞为"增量"，但当前成绩路径 `GradeUploadedEventListener` 实际调用 `fuseFull()`（全量），此偏差为既有偏差（非本次引入）。本次保持当前 `mode=FULL` 行为不变；REVIEW 阶段回归并显式声明 AC-6 措辞需与实现对齐（建议将"增量"修正为与实现一致的"全量"或澄清触发模式）。
4. **`wide-graph-fusion` AC-1~AC-14 其余条目**（KP 匹配 / MASTERS 计算 / 回滚 / 日志 / 跨源 / 跨学科 / 原子性 / 策略可配置）行为完全等价，由 AC-7 等价回归护航，不受本次触发入口与归属变更影响。

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。
