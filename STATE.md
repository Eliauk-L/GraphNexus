# STATE — GraphNexus 跨会话状态

> 每次阶段切换或清窗时更新，供下一个会话快速恢复上下文。

---

## 当前活动

- **Change ID**: `fusion-to-analysis-event-driven`
- **当前阶段**: DEV ✅ → 下一步 TEST / REVIEW
- **当前角色**: Dev（全部任务完成）→ 下一步 Reviewer
- **最后更新**: 2026-06-21
- **路径建议**: 完整 — REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION
- **关键决策（REQUIREMENT 级）**：fusion 全栈（api-dto + application + infrastructure + 前端）从 graph 模块迁入 analysis 模块；构建完成改用同步事件 `GraphConstructedEvent` 触发融合（替换 `ConstructionServiceImpl` 直接调用 + grade `@Order(2)` 监听器，统一文档/成绩两路径触发入口）；REST `/api/v1/graph/fusion/*` → `/api/v1/analysis/fusion/*`；保留 `CONTEXT.md` L153 同步链路（`@EventListener` 同步，单请求 COMPLETED）；fusion 行为等价，不引异步/MQ/schema 变更。
- **关键决策（DESIGN 级 · D1–D11）**：
  - D1 事件机制 = plain 同步 `@EventListener`（非 `AFTER_COMMIT`/`@Async`，AC-4 同步响应契约排除）
  - D2 监听器 join 既有 `@Transactional`（PROPAGATION_REQUIRED，非 `REQUIRES_NEW`）
  - D3 `GraphConstructedEvent` 纯不可变载荷（无 holder）；DOCUMENT 路径结果经文档实体 `doc.status`+`failReason` 回传（同 tx JPA 一级缓存）
  - D4 文档状态机 `FUSING→{COMPLETED, EXTRACTED+failReason}` 迁入 `GraphConstructedEventListener`（仅 DOCUMENT 路径）；**失败→EXTRACTED+failReason**（用户确认，满足 AC-4 + 修复 `ConstructionServiceImpl.java:154-157` 卡 FUSING 的 latent bug）；CSV 路径无文档状态机
  - D5 `@Order` 消除：删 `GradeUploadedEventListener`(@Order2) 整类；移除 `GradeGraphEventListener` `@Order(1)`；新监听器无 `@Order`
  - D6 `GraphChangedEvent` 留 `FusionServiceImpl`（手动+事件驱动两入口均失效缓存，无回归）；移除 `ConstructionServiceImpl:87` + `GradeUploadedEventListener:46` 冗余发布
  - D7 infra 不迁移（`mysql/fusion/` 已满足 AC-1；neo4j flat 沿用 ADR-021）
  - D8 REST 不保留别名（旧路径 404）
  - D9 前端新建 `frontend/src/api/fusion.ts`，`fusionStore.ts` 改 import
  - D10 `GraphConstructedEvent` 类归发布方 graph 模块 `application/graph/construction/event/`（沿用 ADR-018 模式，满足"发布方不引用消费方类型"+ ArchUnit analysis→graph.event 允许）
  - D11 **项目级 ADR-024**：依赖方向 `construction(graph)→事件→fusion(analysis)`，禁 analysis→graph Service / construction→FusionService
- **待确认未知（DESIGN 已全部锁定）**：① 跨事务边界→plain `@EventListener` join 既有 tx（D1/D2）；② 时机保证→发布点在构建循环后显式编码（D5）；③ infra 包路径→不迁移（D7）；④ REST 别名→不保留（D8）；⑤ 失败状态→EXTRACTED+failReason（用户确认，D4）。**残留待 REVIEW 确认**：D4 失败语义偏离当前实现（FUSING→EXTRACTED）、D6 偏离 CHANGE 字面"监听器发布"措辞——均 DESIGN 显式标注，REVIEW 须回归。
- **REQUIREMENT 阶段产出**：11 条 AC（AC-1~AC-11），覆盖 fusion 全栈搬迁 / construction 解耦 / grade 路径统一事件 / 文档+成绩同步链路保留 / REST 迁移+前端同步 / 融合行为等价回归 / 依赖方向 / 融合失败不回滚 / `GraphConstructedEvent` 唯一触发源 / 整体编译测试通过；v2/out 各 ≥1 条；非功能性 5 类显式列出。已新增域语言 `GraphConstructedEvent` + 2 条已锁决策入 `CONTEXT.md`。
- **DESIGN 阶段产出**：`DESIGN.md`（§0 栈锁定 + §0.5 既有架构对齐 + §1 D1–D11 决策 + §2 四张数据流图 + §3 状态机 + §4 ADR 索引 + §5 六条风险 + §6 不在范围 + §9 架构沉淀建议）+ `ADR-024`（项目级模块依赖方向，泛化 ADR-018）。0₋ 架构基线门：用户选定嵌入 ADR-024，不新建 ARCHITECTURE.md。前端无视觉改动 → 前端任务可跳过 `UI-DESIGN.md`（R2.10）。
- **REVIEW 需回归的既有 AC**（REQUIREMENT 已声明超越）：① `graph-construction-refactor` AC-5「两阶段流水线」语义变化（融合改事件驱动）；② `graph-construction-refactor` AC-6「融合失败显式化」实现位置迁移；③ `wide-graph-fusion` AC-6「上传后自动增量」措辞与当前 `fuseFull` 实现偏差（既有偏差，本次保持 FULL，REVIEW 对齐措辞）；④ ADR-009 自动增量融合触发位置语义迁移（DESIGN §4 声明沿用不 supersede，REVIEW 对齐措辞）。
- **TASK 阶段产出**：`TASK.md`（8 个原子任务 · 3 波次 · Wave1 3 并行 + Wave2 4 并行 + Wave3 终验）。每任务配对 impl+test（R4.2）。前端任务（T02/T07）无视觉变更，跳过 UI-DESIGN.md（R2.10 例外）。
- **上游工件**: `@.specs/fusion-to-analysis-event-driven/CHANGE.md` + `REQUIREMENT.md` + `DESIGN.md` + `TASK.md` + `@.specs/adr/024-fusion-event-driven-decoupling.md`（DEV 唯一上下文来源，R1.2）

> ⏸️ `exception-traceability`：CHANGE ✅ → REQUIREMENT ✅ → DESIGN ✅ → TASK ✅ → DEV ✅。后端全局异常处理器补全分级结构化日志 + traceId 关联，复用既有 logback/MDC，响应体不变。TASK 拆 2 个串行任务：**T01 ✅ 兜底 ERROR 完整堆栈 + AC-1 测试（提交 `e1abcc9`）**；**T02 ✅ 业务/校验/权限 WARN 分级 + 响应体回归 + AC-4 零基础设施静态校验（提交 `493eab2`）**。change 全部任务完成，下一步 `@flow-kit/prompts/5-test.md`。工件：`.specs/exception-traceability/TASK.md` + `T01-SUMMARY.md` + `T02-SUMMARY.md` + `{CHANGE,REQUIREMENT,DESIGN}.md` + `CONTEXT.md`。
> ⏸️ `graph-construction-refactor` 暂停于 DEV ✅ / 下一步 TEST·REVIEW，待本 change 收尾后恢复。其 DEV 执行记录见下方。

## DEV 执行记录 (fusion-to-analysis-event-driven)

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | GraphConstructedEvent 新建 | (待提交) | ✅ |
| T02 | frontend fusion.ts 新建 | (待提交) | ✅ |
| T03 | fusion 全栈搬迁 + 删除 GradeUploadedEventListener | (待提交) | ✅ |
| T04 | GraphConstructedEventListener 新建 | (待提交) | ✅ |
| T05 | ConstructionServiceImpl 重构 | (待提交) | ✅ |
| T06 | GradeGraphEventListener 重构 | (待提交) | ✅ |
| T07 | 前端 fusionStore import + graph.ts 清理 | (待提交) | ✅ |
| T08 | 测试搬迁 + import 更新 + 全量 mvn test | (待提交) | ✅ |

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | SQL DDL — DROP csv columns + index | `905542e` | ✅ |
| T02 | pom.xml — Apache POI 5.2.5 | `905542e` | ✅ |
| T03 | ErrorCode A0022 exam_no duplicate | `905542e` | ✅ |
| T04 | GradeFileType CSV + EXCEL split | `ea04746` | ✅ |
| T05 | CsvParsePayload → GradeParsePayload | `ea04746` | ✅ |
| T06 | ExamRecordDO csv field removal | `ea04746` | ✅ |
| T07 | Grade BO/VO csv field cleanup | `ea04746` | ✅ |
| T08 | CsvGradeParser adapt new enums | `0a08d13` | ✅ |
| T09 | ExcelGradeParser new | `0a08d13` | ✅ |
| T10 | GradeUploadedEvent + GradeDeletedEvent | `0a08d13` | ✅ |
| T11 | ExamRecordRepository refactor | `0a08d13` | ✅ |
| T12 | GradeUploadService event-driven | `0a08d13` | ✅ |
| T13 | GradeServiceImpl spec query + event | `0a08d13` | ✅ |
| T14 | GradeGraphEventListener new | `0a08d13` | ✅ |
| T15 | GradeUploadedEventListener @Order(2) | `0a08d13` | ✅ |
| T16 | GradeController conditional query | `0a08d13` | ✅ |
| T17 | CsvGradeParserTest adapt | `0a08d13` | ✅ |
| T18 | ExcelGradeParserTest new | `9b9ba7b` | ✅ |
- **最后更新**: 2026-06-19

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | TextbookRepository 5 条 @Query → 方法名派生 | `98fd00d` | ✅ |
| T02 | ExamRecordRepository 5 替换 + 1 改名 | `98fd00d` | ✅ |
| T03 | TextbookRepository 调用方更新（3 Service + 3 Test） | `396b61a` | ✅ |
| T04 | ExamRecordRepository 调用方更新（3 Service + 1 Strategy） | `396b61a` | ✅ |
| T05 | 全量测试验证 + AC 核验 | `396b61a` | ✅ |

## 上一个活动

- **Change ID**: `intelligent-qa`
- **当前阶段**: TASK → 等待 DEV
- **最后更新**: 2026-06-17

## 并行 Change

| Change ID | 阶段 | 状态 |
|-----------|------|------|
| `init-platform` | DEV → TEST | ✅ 9/9 任务完成 |
| `document-process-pdf-minimal` | DEV | ✅ 10/10 任务完成 |
| `knowledge-graph-extraction` | DEV | ✅ 11/11 任务完成，集成测试全通过 |
| `csv-grade-import` | DEV ✅ → TEST | 🔄 进行中（17/17 单测 + 6/6 集成通过） |
| `intelligent-qa` | TASK | 🔄 等待确认，17 任务 6 波次，下一步 DEV |
| `fix-extraction-json-parsing` | DEV | ✅ T01 完成 |
| `package-restructure` | DEV ✅ | 🔄 15+5 任务完成，T01-T20 全部 done，下一步 TEST/REVIEW |
| `refine-package-structure` | DONE | ✅ 7/7 任务完成（本次将 supersede） |
| `frontend-ui` | DEV | ✅ 18/18 任务完成，5 波次全部通过，vue-tsc 0 错误 |

## DEV 执行记录

| Task ID | 名称 | 提交 | 状态 |
|---------|------|------|:--:|
| T01 | ExtractionJsonParser + Jackson 宽松解析 | — | ✅ |
| T01 | ErrorCode 新增 A0008/A0009/A0010 | `51626a0` | ✅ |
| T02 | GraphNode/GraphEdge 抽象 + NodeType/EdgeType 枚举 | `63840a8` | ✅ |
| T03 | LlmGateway + SpringAiLlmGateway + yml 配置 | `fd15560` | ✅ |
| T04 | DocumentNode/EntityNode/KnowledgePointNode/KnowledgeCategoryNode | `e599eb1` | ✅ |
| T05 | 6 类图边（Extracts/References/Derives/Contains/AlignedTo/BelongsTo/ChildOf/Prerequisite） | `6d03eab` | ✅ |
| T06 | GraphNodeRepository 通用图仓库 | `c7aba8e` | ✅ |
| T07 | ExtractionService Prompt+JSON Schema+校验 | `c90c4f4` | ✅ |
| T08 | GraphService 接口+实现 | `38c7e61` | ✅ |
| T09 | GraphController + VO/DTO | `976f1ac` | ✅ |
| T10 | 单元测试（16 tests, 0 failures） | `989a40d` | ✅ |
| T11 | 集成测试（4/4 通过，直连 podman + DeepSeek API） | `a0b1ef5` | ✅ |

## 中断任务

暂无。

## 待完成事项

- 进入 `@flow-kit/prompts/5-test.md` 测试矩阵 + UAT
- 进入 `@flow-kit/prompts/6-review.md` 双轮审查

---

> 完整流程见 `.specs/knowledge-graph-extraction/` 下各工件。