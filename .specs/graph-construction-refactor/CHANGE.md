# CHANGE: 图谱构建模块重命名 + 逻辑优化

- **Change ID**: `graph-construction-refactor`
- **创建日期**: 2026-06-20
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

> **⚠️ 修订记录（2026-06-21）**：原 What #12 的"三阶段流水线（构建→实体对齐→融合）"已修订为**两阶段流水线（构建→融合）**。独立实体对齐阶段移除——跨文档对齐由融合的 redirectEdges 隐式完成。FileStatus 回退 v2（无 ALIGNING/ALIGNED）。详见 ADR-022。

---

## Why（为什么做）

当前 `GraphController` / `GraphService` 命名过于泛化，无法区分其"图谱构建（抽取 + 子图查询）"职责与同包下的 FusionController（融合）、MetricsController（指标）的边界。此外 `GraphServiceImpl.extract()` 存在以下设计债：

1. **命名模糊**：`GraphController` 字面意思是"图控制器"，但实际上 `api/graph/` 包下同时存在三个子域（构建 / 融合 / 指标），`Graph` 这个名字无法表达"从文档中抽取构建知识图谱"的语义
2. **`extract()` 方法职责过重**：单一方法承担了 7 个步骤（文档验证 → FileNode 构建 → LLM 抽取 → Neo4j 写入 → 统计 → 增量融合 → 事件发布），违反 SRP，难以测试和维护
3. **增量融合失败被静默吞掉**：`fusionService.fuseIncremental()` 抛异常时只记 `log.error`，HTTP 仍然返回 200。调用方无法感知融合失败，数据一致性问题被掩盖
4. **边数统计遗漏**：`totalEdges = extracted.edges().size() + extracted.entities().size()` 只计算了 EXTRACTS 边，遗漏了 `convertToDomain` 中创建的 BELONGS_TO 边，统计不准确
5. **`GraphNodeRepository` 上帝仓库**：666 行代码同时服务 construction / fusion / MASTERS / grade events / intelligent QA / metrics 六个子域，修改任意子域都可能影响其他子域
6. **代码质量问题**：`GraphServiceImpl` 中使用完全限定名 `new com.graphnexus.infrastructure.neo4j.edge.ExtractsEdge(...)` 而非 import；`@Transactional` 仅覆盖 MySQL 但方法大量写 Neo4j，语义误导
7. **文档 KP 与考试 KP 跨源融合缺失**：`GradeGraphEventListener` 创建考试 KP 时使用 `MERGE ON id`（UUID），导致同名同科目的文档 KP（`fusionSource=DOCUMENT`）和考试 KP（`fusionSource=CSV_IMPORT`）成为两个独立节点；全量融合缺少对跨源合并的显式保障（如同名精确匹配前置 pass），考试 KP 创建时产生了不必要的重复节点
8. **融合操作无原子性保障**：`fuseFull()` 按 subject 逐个执行 `merge()` + `recalculateAll()`，每次调用直接提交 Neo4j 事务。若中途失败（如数学成功、物理失败），已融合的 subject 无法回滚。`FusionGroupBuilder.merge()` 内部同样逐 group 提交——group[1] 的源 KP 已删除、边已重定向后 group[2] 失败，group[1] 不可恢复。且失败时 `fusionDetailJson` 未写入，`FusionRollbackService` 无法用于补偿回滚，图谱陷入半融合不一致状态
9. **Subject 作为字符串属性导致一致性问题**：当前学科以 `String subject` 属性存储在各节点（KnowledgePoint / Exam / FileNode / Document），融合分组依赖 `groupBy(subject)` 字符串精确匹配。`"数学"` 与 `"高中数学"` 会被分入不同融合组，即使指同一学科。且一个 KP 只能属于一个学科（属性是 1:1），无法表达跨学科知识点的归属。学科无层级关系（如 `初中数学 → 数学`），无法支持按学科体系向上汇总
10. **图谱构建后的实体对齐与融合未形成显式工作流**：当前 `extract()` 将实体对齐（LLM 抽取中的 entity→KP ALIGNED_TO）和增量融合（`fuseIncremental`）内嵌在同一个方法中。实体对齐仅限单文档内——新抽取的实体只对齐到本次创建的 KP，不会对齐到图谱中已有的同名 KP。融合被作为"附加步骤"嵌入 extract 末尾，而非独立的流水线阶段。构建 → 对齐 → 融合 的完整链路没有显式的编排和状态追踪

## What（做什么）

将 `GraphController` + `GraphService` 重命名为 `ConstructionController` + `ConstructionService`，明确其"图谱构建"模块定位；URL 前缀由 `/api/v1/graph` 变更为 `/api/v1/graph/construction`（与同级的 `/api/v1/graph/fusion`、`/api/v1/graph/metrics` 形成命名空间层次）；同步优化上述 10 个设计债。

1. **类重命名**：`GraphController` → `ConstructionController`，`GraphService` → `ConstructionService`，`GraphServiceImpl` → `ConstructionServiceImpl`
2. **URL 变更**：`/api/v1/graph/extract` → `/api/v1/graph/construction/extract`，`/api/v1/graph/document` → `/api/v1/graph/construction/document`
3. **包移动**：将重命名后的 Service 从 `application/graph/core/service/` 移至 `application/graph/construction/service/`（与 `ExtractionService` 同包）；关联 BO/VO 同步移动
4. **`extract()` 方法拆分**：将 7 步职责拆分为独立方法/组件（文档校验、Neo4j 持久化编排、后处理（融合+事件））
5. **融合失败显式化**：增量融合失败时至少标记文档状态或返回 warning 信息，不再静默吞掉
6. **边数统计修正**：`totalEdges` 纳入 BELONGS_TO 边，使统计准确
7. **`GraphNodeRepository` 按三模块彻底拆分**：将当前 666 行的上帝 Repository 拆为三个独立 Repository，各自对齐一个模块，原 `GraphNodeRepository` 删除：
   - `ConstructionGraphRepository` — 图谱构建 + 成绩事件图谱操作（save/saveEdge/deleteByDocumentId/deleteEdgesByExamNo/deleteExamNode 等）
   - `FusionGraphRepository` — 融合 + MASTERS 操作（findAllKnowledgePoints/redirectEdges/batchUpsertMastersEdges/deleteKnowledgePoints 等）
   - `QueryGraphRepository` — 智能问答 + 指标只读查询（findStudentByName/findPrerequisitesUpstream/findDistinctSubjects 等）
8. **代码质量修复**：消除完全限定名，添加 import；`@Transactional` 语义明确化
9. **跨源 KP 融合显式化**：
   - 考试 KP 创建改为 `MERGE ON (name, subject)` 而非 `MERGE ON id`，避免与文档 KP 重复创建同名节点
   - 全量融合与增量融合均增加同名精确匹配前置 pass（`DOCUMENT` ↔ `CSV_IMPORT` 跨源 KP 在 FuzzyMatch 之前先按 `name + subject` 精确匹配合并），确保两种来源的同名知识点必然融合
10. **融合原子性保障**：
   - 引入 Neo4j 事务边界：将全量/增量融合的 merge + MASTERS 重算包装在同一个 Neo4j 事务中，全部成功则提交，任一失败则回滚
   - 或者采用"先记后做"模式：在执行 merge 之前先完整写入 `fusionDetailJson`（含所有 group 的预计算明细），失败时通过 `FusionRollbackService` 基于完整日志执行补偿回滚
   - 具体方案在 DESIGN 阶段选型
11. **Subject 从字符串属性改为独立节点**：
   - 新增 `SubjectNode`（Label `:Subject`，唯一标识 `name`），新增 `BELONGS_TO_SUBJECT` 边
   - `KnowledgePointNode`、`ExamNode`、`FileNode` 移除 `subject` 字符串属性，改为通过 `BELONGS_TO_SUBJECT` 边指向 SubjectNode
   - 融合分组逻辑：从 `groupBy(subject string)` 改为按 Subject 节点引用分组，消除字符串不一致导致的错误分片
   - 存量数据迁移：将现有节点的 `subject` 属性值提取为 SubjectNode + 创建关系边
   - 多学科支持：一个 KP 可通过多条 `BELONGS_TO_SUBJECT` 边属于多个学科（v2 能力，v1 保持 1:1）
   - 学科层级：后续可扩展 `(:Subject)-[:CHILD_OF]->(:Subject)` 父子关系（本次预留，不实现）
   - **LLM 抽取提示词同步更新**：System Prompt 增加 subject 名称规范化指令（要求使用标准学科名如"数学"而非"高中数学"，优先使用文档元数据中提供的学科名）；Few-shot 示例中 knowledgePoint 的 `subject` 字段保留但明确取值规范；`ExtractionService.convertToDomain()` 不再将 subject 设为 KnowledgePointNode 属性，改为创建/查找 SubjectNode + BELONGS_TO_SUBJECT 边
12. **图谱构建 → 图谱融合 显式两阶段流水线**（修订后，原含独立实体对齐阶段已移除）：
   - 将当前 `extract()` 中的隐式融合拆分为独立阶段，由 `ConstructionService` 编排调用
   - 阶段一「图谱构建」：LLM 抽取 → 创建 EntityNode / KnowledgePointNode / KnowledgeCategoryNode + 单文档内 ALIGNED_TO 边 + SubjectNode + BELONGS_TO_SUBJECT 边，写入 Neo4j
   - 阶段二「图谱融合」：调用增量融合（合并跨源重复 KP + 重算受影响 MASTERS），融合合并 KP 时 `redirectEdges` 自动把 ALIGNED_TO 边重定向到规范 KP，**隐式完成跨文档实体对齐**；融合失败不再静默吞掉，标记 fusionWarning 且状态不进入 COMPLETED
   - 流水线状态追踪：`EXTRACTING → EXTRACTED → FUSING → COMPLETED`

## 影响面

- [x] 影响 `REQUIREMENT.md` — URL 变更（breaking change），extract 行为变更（融合失败不再静默），边数统计修正，跨源 KP 融合策略变更，考试 KP 创建方式变更，融合原子性保障，Subject 节点化数据模型变更，构建→融合两阶段流水线（原三阶段已修订），图指标计算约束为中心节点（KP/Student）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 模块命名规范 ADR、GraphNodeRepository 拆分方案、extract 方法重构方案、跨源 KP 融合策略 ADR、考试 KP MERGE 策略变更、融合原子性方案 ADR（Neo4j 事务 vs 补偿回滚）、Subject 节点化 ADR、构建→融合流水线 ADR-022（原三阶段已修订为两阶段）
- [x] 影响现有 AC — `knowledge-graph-extraction` 的 AC 中 URL 路径需更新；`wide-graph-fusion` 的 AC 中融合分组逻辑变更
- [x] 影响数据模型 / 迁移 — Neo4j 节点属性 `subject` → SubjectNode + BELONGS_TO_SUBJECT 边；MySQL `document.status` 维持 v2（原 ALIGNING/ALIGNED 已回退）；MySQL 中 `document.subject` 和 `exam_record.subject` 字段保留不删
- [x] 影响外部 API 兼容性 — `/api/v1/graph/extract` 和 `/api/v1/graph/document` 两个端点 URL 变更为 `/api/v1/graph/construction/...`，属于 **breaking change**；响应体结构不变
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- ❌ **FusionController / FusionService 重命名**：融合模块不在本次范围
- ❌ **MetricsController / MetricsService 重命名**：指标模块不在本次范围
- ❌ **Neo4j 节点/边实体类重命名**：`GraphNode` / `GraphEdge` 抽象基类及其子类命名不变——它们是图数据抽象，不是"构建模块"专属
- ❌ **前端同步适配**：URL 变更后前端需跟进，但不属于本次后端 refactor 范围
- ❌ **API 版本号变更**：仍然使用 `v1`
- ❌ **LLM 抽取核心逻辑变更**：`ExtractionService` 的 JSON Schema、校验逻辑、重试机制不变；仅 Prompt 中 subject 规范化指令和 Few-shot 取值规范有调整

## 验收线（粗粒度，不是 AC）

1. **重命名一致性**：`ConstructionController` + `ConstructionService` + `ConstructionServiceImpl` 类名、包路径、URL 映射全部一致，`GraphController` / `GraphService` 旧名称在代码库中不再存在
2. **旧 URL 不可用**：`POST /api/v1/graph/extract/{id}` 和 `GET /api/v1/graph/document/{id}` 返回 404，新 URL `/api/v1/graph/construction/...` 正常工作
3. **extract 行为改进**：融合失败时调用方可感知（文档状态标记或响应 warning），不再静默返回 200
4. **Repository 三模块独立**：`ConstructionGraphRepository` / `FusionGraphRepository` / `QueryGraphRepository` 各自独立，原 `GraphNodeRepository` 已删除，所有调用方切换到对应新 Repository，现有测试全部通过
5. **边数统计准确**：`totalEdges` 包含所有实际创建的边类型
6. **跨源 KP 融合**：上传考试数据后再执行全量/增量融合，同名同科目的文档 KP 和考试 KP 合并为一个节点，`fusionSource` 标记为 `"DOCUMENT,CSV_IMPORT"`，旧节点删除，边全部重定向
7. **融合原子性**：融合过程中途失败时，Neo4j 图谱状态要么全部生效、要么全部回滚，不出现半融合状态；失败后可通过融合日志完整恢复到融合前状态
8. **Subject 节点化**：所有 KnowledgePoint / Exam / FileNode 不再使用 `subject` 字符串属性，改为通过 `BELONGS_TO_SUBJECT` 边指向 SubjectNode；融合分组基于 Subject 节点引用，`"数学"` 和 `"高中数学"` 不会错误分入不同组；存量数据迁移后旧 `subject` 属性已清除
9. **图指标中心节点约束**：PageRank/度中心性计算必须以 KnowledgePoint 或 Student 为中心（nodeTypes 必填且仅允许这两者，Student 需同时含 KnowledgePoint），边类型按中心自动收敛（含 KP→PREREQUISITE_OF，含 Student→MASTERS）；传非中心类型或边不匹配返回 A0002
9. **构建→融合流水线**：文档上传后，`ConstructionController.extract` 触发两阶段流水线（构建→融合），各阶段状态在 `document.status` 中可追踪；跨文档实体对齐由融合隐式完成（redirectEdges 重定向 ALIGNED_TO 边）；融合失败时状态不进入 COMPLETED，调用方可感知异常

## 风险与未知

- **Breaking change 影响面**：前端 `frontend-ui` change 使用了 `/api/v1/graph/` 端点，需协调同步更新。旧书签/脚本依赖旧 URL 将直接 404
- **GraphNodeRepository 拆分的调用方更新**：三个模块（构建/融合/指标&问答）的 Service 层和 EventListener 需全部切换到新 Repository，涉及 `GraphServiceImpl`、`FusionServiceImpl`、`FusionRollbackService`、`MastersRecalculationService`、`MetricsServiceImpl`、`GradeGraphEventListener`、`GradeUploadedEventListener` 等多个调用方
- **`core/` 包空壳化**：`GraphService` 移走后 `application/graph/core/` 可能变空或仅剩少量类，DESIGN 阶段需决定保留还是清理
- **存量测试更新**：`knowledge-graph-extraction` 的集成测试中可能硬编码了 `/api/v1/graph/` URL，需全部更新；`wide-graph-fusion` 的融合测试也需适配 Subject 节点化
- **Subject 节点迁移的兼容性**：存量 Neo4j 数据中所有节点的 `subject` 属性需一次性迁移为 SubjectNode + 关系边，迁移脚本必须幂等；迁移前需全量备份 Neo4j 数据
- **MySQL subject 字段保留**：`document.subject` 和 `exam_record.subject` 保留不删，仅 Neo4j 侧改为节点。两端数据来源不同——MySQL 侧是上传时指定的学科标签，Neo4j 侧是融合后的规范学科节点

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。