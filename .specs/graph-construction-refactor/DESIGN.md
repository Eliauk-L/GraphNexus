# DESIGN: 图谱构建模块重命名 + 逻辑优化

- **Change ID**: `graph-construction-refactor`
- **关联**: `@.specs/graph-construction-refactor/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

> **⚠️ 修订记录（2026-06-21）**：原设计为三阶段流水线（构建→实体对齐→融合），实现后发现独立"实体对齐"阶段与融合的 `redirectEdges` 重复（融合合并 KP 时已自动重定向 ALIGNED_TO 边到规范 KP），且 `entity.name`↔`kp.name` 模糊匹配语义不成立。**已修订为两阶段流水线（构建→融合），移除 ALIGNING/ALIGNED 状态，FileStatus 回退 v2。** 详见 ADR-022 修订记录。下文凡提及"三阶段/阶段二实体对齐/ALIGNING/ALIGNED"处均以此修订为准。

---

## 0. 技术栈选定

> 所有技术栈已在 CONTEXT.md 锁定，本次不引入新组件。仅复用既有。

- **语言**: Java 17 LTS
- **框架**: Spring Boot 3.3.x
- **图数据库**: Neo4j 5.x + Spring Data Neo4j 7.x（Neo4jClient 手动 Cypher）
- **关系数据库**: MySQL 8.0 + Spring Data JPA
- **依赖注入**: 构造器注入（`@RequiredArgsConstructor`）
- **测试**: JUnit 5 + Mockito + Spring Boot Test
- **明确排除**: 不引入新的 Neo4j 驱动、不引入新的 ORM、不引入新的消息队列/缓存

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
修改文件：
├── api/graph/controller/GraphController.java          → 重命名为 ConstructionController
├── api/graph/dto/graph/ExtractionResultVO.java        → 移至 construction/dto/
├── api/graph/dto/graph/GraphSubgraphVO.java           → 移至 construction/dto/
├── application/graph/core/service/GraphService.java   → 重命名+移至 construction/service/
├── application/graph/core/service/impl/GraphServiceImpl.java → 重命名+移至 construction/service/impl/
├── application/graph/core/model/ExtractionResultBO.java → 移至 construction/model/
├── application/graph/core/model/GraphSubgraphBO.java  → 移至 construction/model/
├── application/graph/core/model/GraphNodeData.java    → 移至 construction/model/
├── application/graph/core/model/GraphEdgeData.java    → 移至 construction/model/
├── application/graph/core/model/GraphDataConverter.java → 移至 construction/model/
├── application/graph/construction/service/ExtractionService.java → 更新 convertToDomain
├── application/graph/construction/service/ExtractionPromptBuilder.java → 更新 prompt
├── application/graph/construction/listener/GradeGraphEventListener.java → 切换 repo + KP 创建策略
├── application/graph/fusion/service/impl/FusionServiceImpl.java → 切换 repo + 原子性
├── application/graph/fusion/service/impl/FusionGroupBuilder.java → 切换 repo + 分组逻辑
├── application/graph/fusion/service/impl/FusionRollbackService.java → 切换 repo
├── application/graph/fusion/service/impl/MastersRecalculationService.java → 切换 repo
├── application/graph/metrics/service/impl/MetricsServiceImpl.java → 切换 repo
├── infrastructure/neo4j/repository/GraphNodeRepository.java → 拆分后删除
├── infrastructure/neo4j/node/KnowledgePointNode.java  → remove subject property
├── infrastructure/neo4j/node/ExamNode.java            → remove subject property
├── infrastructure/neo4j/node/FileNode.java            → remove subject property
├── infrastructure/neo4j/config/Neo4jIndexConfig.java  → update indices
├── infrastructure/mysql/file/entity/FileStatus.java   → add ALIGNING/ALIGNED
├── application/analysis/strategy/StudentDiagnosisStrategy.java → update imports
├── application/analysis/model/PrunedSubgraph.java → update imports
├── application/query/chat/service/impl/QueryServiceImpl.java → update imports
├── application/file/textbook/service/TextbookServiceImpl.java → 切换 repo

新增文件：
├── api/graph/controller/ConstructionController.java   (new)
├── api/graph/dto/construction/ExtractionResultVO.java (new)
├── api/graph/dto/construction/GraphSubgraphVO.java    (new)
├── application/graph/construction/service/ConstructionService.java (new)
├── application/graph/construction/service/impl/ConstructionServiceImpl.java (new)
├── application/graph/construction/model/*.java        (new, moved 5 files)
├── infrastructure/neo4j/node/SubjectNode.java         (new)
├── infrastructure/neo4j/edge/BelongsToSubjectEdge.java (new)
├── infrastructure/neo4j/repository/ConstructionGraphRepository.java (new)
├── infrastructure/neo4j/repository/FusionGraphRepository.java (new)
├── infrastructure/neo4j/repository/QueryGraphRepository.java (new)

删除：
├── api/graph/controller/GraphController.java
├── application/graph/core/                            (empty package, delete)

测试更新：
├── GraphControllerIntegrationTest.java → ConstructionControllerIntegrationTest.java
├── GraphServiceTest.java → ConstructionServiceTest.java
├── GraphNodeAbstractionTest.java → SubjectNode model test adaptation
├── TextbookServiceTest.java → mock 切换

禁动清单（与本次无关，AI 不许"顺手"碰）：
├── FusionController.java / MetricsController.java — 不在重命名范围
├── FusionService.java (接口) — 接口签名不变
├── GraphNode.java / GraphEdge.java — 抽象基类不变
├── LlmGateway / SpringAiLlmGateway — LLM 调用层不变
├── ExtractionValidator / ExtractionJsonParser — 校验/解析不变
├── All MySQL DO/Repository — 关系数据库层不变
├── MinIO / storage — 文件存储层不变
├── pom.xml — 依赖禁动
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| Neo4j 节点基类 | `infrastructure/neo4j/node/GraphNode.java` | **沿用**，SubjectNode extends GraphNode |
| Neo4j 边基类 | `infrastructure/neo4j/edge/GraphEdge.java` | **沿用**，BelongsToSubjectEdge extends GraphEdge |
| Neo4j 数据访问 | `Neo4jClient` + `Neo4jTemplate` | **沿用**，新 Repository 复用相同模式 |
| KP 匹配策略 | `application/graph/fusion/strategy/KpMatchingStrategy` | **沿用**，跨文档实体对齐复用 FuzzyMatchStrategy |
| 权重计算策略 | `application/graph/fusion/strategy/WeightCalculationStrategy` | **沿用**，不变 |
| LLM 调用 | `common/LlmGateway.java` | **沿用**，不变 |
| 事件发布 | `ApplicationEventPublisher` + `GraphChangedEvent` | **沿用**，不变 |
| 文档状态机 | `infrastructure/mysql/file/entity/FileStatus.java` | **扩展**，新增 ALIGNING/ALIGNED |
| 统一响应体 | `common/ApiResult.java` | **沿用**，响应结构不变 |
| Controller 层模式 | `@RestController` + `@RequestMapping` | **沿用**，ConstructionController 复用相同模式 |
| Service 层模式 | `@Service` + 构造器注入 | **沿用** |

### 0.5.3 沿用模式 vs 引入新模式

```
- Controller 层：    **沿用** 既有 RestController + ApiResult 模式
- Service 层：       **沿用** 既有 @Service + 构造器注入 + @Transactional 模式
- Repository 层：    **沿用** 既有 Neo4jClient 手动 Cypher 模式（非 SDN Repository 接口）
                      拆分后每个新 Repository 继承同一模式
- Neo4j 节点/边：    **沿用** 既有 GraphNode/GraphEdge 抽象基类 + @Node 注解
- 事件机制：         **沿用** 既有 ApplicationEventPublisher + @EventListener
- 融合策略：         **沿用** 既有策略模式（KpMatchingStrategy / WeightCalculationStrategy）
- 流水线编排：       **引入新模式** → 理由：当前无显式流水线抽象，构建→融合需状态追踪
                      采用**方法级编排**（非独立的 Pipeline 接口）——在 ConstructionService 内
                      按顺序调用两阶段方法（phase1_build → phase2_fuse），每阶段负责自己的事务 + 状态转换
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | 命名：GraphController → ConstructionController，`/api/v1/graph` → `/api/v1/graph/construction` | A) 改为 `/api/v1/construction` 独立路径 | 选 `/api/v1/graph/construction` 保持 graph 命名空间层次统一（`/graph/construction`、`/graph/fusion`、`/graph/metrics` 互为兄弟姐妹），不破坏已有 URL 层级认知 | 路径比独立 `/construction` 长一级，但对 RESTful 设计无负面影响 |
| D2 | 包移动：`core/service/` → `construction/service/`，关联 BO/VO 同步移动 | A) 保留 core 包，仅改类名 | 选移动。`core/` 仅含 construction 相关类，独立无价值，与 ExtractionService 同包便于内聚 | `core/` 包可能变空需删除；`analysis/` 和 `query/` 模块的 import 需更新（从 core/model → construction/model） |
| D3 | Repository 拆分：按三模块创建 ConstructionGraphRepository / FusionGraphRepository / QueryGraphRepository | A) 拆为 6 个微 Repository（每种节点类型一个）；B) 不拆，仅重命名 | 选三模块拆分。粒度对齐三个 Controller 的业务边界，修改一个模块的 Neo4j 查询不会影响其他模块。6 个过细导致调用方需注入多个 Repository；不拆则上帝类问题持续恶化 | 每个 Repository 方法数不均衡：FusionGraphRepository（~20 方法）> QueryGraphRepository（~8）> ConstructionGraphRepository（~6）。Fusion 最重是合理的（融合操作复杂度天然更高） |
| D4 | Subject 节点化：新增 SubjectNode（Label `:Subject`）+ BELONGS_TO_SUBJECT 边。KPoint/Exam/FileNode 移除 `subject` 属性 | A) 保留 subject 为属性，仅做名称规范化；B) Subject 作为 KP 的属性 + 独立节点双重存在 | 选完全节点化。节点引用分组从根本上消除字符串不一致风险，预留层级扩展（CHILD_OF），支持 v2 多学科归属。不允许双重存在（一致性噩梦） | 存量迁移是 one-shot 成本；所有 subject 的 Cypher 查询需改写为关系遍历，查询复杂度略增 |
| D5 | ~~三阶段流水线~~ → **两阶段流水线（修订）**：ConstructionServiceImpl 内部按 构建→融合 顺序调用。原设计的独立"实体对齐"阶段已移除——跨文档对齐由融合的 redirectEdges 隐式完成 | A) 引入独立 Pipeline 接口；B) 保持 extract() 单体方法 | 选方法级编排，两阶段（phase1_build + phase2_fuse）。融合合并重复 KP 时自动重定向 ALIGNED_TO 边，无需独立对齐阶段 | 见 ADR-022 修订记录 |
| D6 | 融合原子性：**Neo4j 事务包装**——使用 Neo4j `TransactionTemplate` 将全量/增量融合的全部 merge + MASTERS 重算包装在一个 Neo4j 事务中 | B) 先记后做补偿回滚（先写完整 fusionDetailJson，失败后通过日志回滚） | 选 Neo4j 事务包装。当前 Neo4jClient 底层是 Bolt 协议，支持事务。优点：全部成功提交/失败回滚，无中间状态；实现简单，不依赖 fusionDetailJson 的预计算。补偿回滚方案的代价是"回滚本身可能失败"，且需维护两套逻辑 | 大事务可能导致 Neo4j 锁时间较长（全量融合 1000+ KPs 时）；需设置事务超时。如未来性能不可接受可降级为 B 方案 |
| ~~D7~~ | ~~实体对齐（跨文档）~~ → **已移除（修订）**：原计划用 FuzzyMatch(entity.name, kp.name) 做跨文档对齐。移除原因：① Entity 名称（片段名）与 KP 名称（标准概念）语义维度不同，直接模糊匹配易误匹配；② 与融合 redirectEdges 重复，融合后产生冗余 ALIGNED_TO 边。跨文档对齐改由融合隐式完成 | — | — | 见 ADR-022 修订记录 |
| D8 | 考试 KP 创建策略：`GradeGraphEventListener` 中改为先查询再决定——同名同 Subject 的 KP 已存在则复用节点（MERGE ON name + SubjectNode 引用），否则创建新 KP | A) 保持当前 UUID 创建 + 靠融合合并；B) 考试 KP 完全独立不参与融合 | 选先查后决。从源头避免冗余节点创建，减少融合压力。与跨源 KP 精确匹配前置 pass 配合：即使考试侧创建了独立 KP，融合阶段也能保证合并 | 每次成绩上传需多一次 Neo4j 查询（按 name + SubjectNode 查已有 KP），延迟极小（索引查找） |
| ~~D9~~ | ~~文档状态机扩展 v3（ALIGNING/ALIGNED）~~ → **回退 v2（修订）**：移除 ALIGNING/ALIGNED，FileStatus 回退到 v2（8 状态）。成功路径 `EXTRACTING→EXTRACTED→FUSING→COMPLETED` | — | 独立对齐阶段已移除，对应状态无意义 | 见 ADR-022 修订记录 |
| D10 | LLM Prompt：System Prompt 增加 subject 规范化指令；`buildUserMessage` 不需要改（已传 subject 参数） | A) 不做提示词约束，仅靠代码侧统一 | 选提示词约束。LLM 是最初的 subject 名称生产者，必须在源头规范，代码侧统一是兜底。提示词修改成本低，且与 Few-shot 示例形成一致 | LLM 仍可能不遵守指令（概率低但与学科相关），代码侧以 SubjectNode MERGE ON name 兜底确保节点唯一 |

---

## 2. 数据流 / 架构图

### 2.1 两阶段流水线（构建→融合）— 修订后

> 原设计含独立"实体对齐"阶段，已移除（见 ADR-022 修订）。下图阶段二「实体对齐」已废弃，跨文档对齐由阶段三融合的 redirectEdges 隐式完成。

```
POST /api/v1/graph/construction/extract/{docId}
  │
  v
ConstructionController.extract(docId)
  │
  v
ConstructionServiceImpl.extract(docId)
  │
  ├── 阶段一「图谱构建」
  │   ├── TextbookRepository.findById(docId)              [MySQL]
  │   ├── 校验 status ∈ {PARSED, EXTRACTED, COMPLETED}
  │   ├── FileStatus → EXTRACTING                          [MySQL UPDATE]
  │   ├── ExtractionService.extract(textContent, ...)
  │   │   ├── promptBuilder.buildSystemPrompt()             (subject 规范化指令)
  │   │   ├── llmGateway.chat(system, user)                  [LLM API]
  │   │   ├── jsonParser.parse(response)
  │   │   ├── validator.validate(rawResult)
  │   │   └── convertToDomain(rawResult, docId)
  │   │       ├── new EntityNode / KnowledgePointNode / KnowledgeCategoryNode
  │   │       ├── **new SubjectNode + BELONGS_TO_SUBJECT 边**    (新增)
  │   │       └── 单文档内边: ALIGNED_TO / BELONGS_TO_CATEGORY / ...
  │   ├── constructionGraphRepository.deleteByDocumentId(docId)  [Neo4j]
  │   ├── constructionGraphRepository.save(fileNode)             [Neo4j]
  │   ├── constructionGraphRepository.saveAll(entities)          [Neo4j]
  │   ├── constructionGraphRepository.saveAll(knowledgePoints)   [Neo4j]
  │   ├── constructionGraphRepository.saveAll(categories)        [Neo4j]
  │   └── constructionGraphRepository.saveAllEdges(allEdges)     [Neo4j]
  │   └── FileStatus → EXTRACTED                           [MySQL UPDATE]
  │
  ├── 阶段二「实体对齐」
  │   ├── FileStatus → ALIGNING                            [MySQL UPDATE]
  │   ├── 查询图谱中已有 KPs（同 Subject）                   [Neo4j READ]
  │   │   └── queryGraphRepository.findKPsBySubject(subjectNode)
  │   ├── 对每个新 Entity：
  │   │   ├── FuzzyMatch 匹配已有 KP 名称（KpMatchingStrategy）
  │   │   └── 匹配命中 → 创建 ALIGNED_TO 边 (Entity → 已有KP) [Neo4j WRITE]
  │   └── FileStatus → ALIGNED                             [MySQL UPDATE]
  │
  ├── 阶段三「图谱融合」
  │   ├── FileStatus → FUSING                              [MySQL UPDATE]
  │   ├── 在 Neo4j 事务内：
  │   │   ├── fusionService.fuseIncremental(kpNames, subject)
  │   │   │   ├── fusionGraphRepository.findKPsByNamesAndSubject(...)
  │   │   │   ├── **跨源精确匹配前置 pass** (name + SubjectNode)
  │   │   │   ├── FusionGroupBuilder.build(kps, matcher, threshold)
  │   │   │   ├── FusionGroupBuilder.merge(groups)            [Neo4j WRITE]
  │   │   │   └── MastersRecalculationService.recalculate(...) [Neo4j WRITE]
  │   │   └── 事务提交 / 异常回滚                              [Neo4j TX]
  │   ├── fusion 失败？→ FileStatus 回退到 ALIGNED           [MySQL UPDATE]
  │   └── fusion 成功？→ FileStatus → COMPLETED              [MySQL UPDATE]
  │
  └── eventPublisher.publishEvent(GraphChangedEvent)
```

### 2.2 Repository 拆分后的依赖关系

```
L1 Controller:
  ConstructionController → ConstructionService
  FusionController       → FusionService
  MetricsController      → MetricsService

L2 Service → L3 Repository:
  ConstructionServiceImpl → ConstructionGraphRepository  (save/saveEdge/delete...)
                          → TextbookRepository           (MySQL, 不变)
                          → ExtractionService
  FusionServiceImpl       → FusionGraphRepository  (redirectEdges/batchUpsertMasters...)
                          → FusionLogRepository    (MySQL, 不变)
  MetricsServiceImpl      → QueryGraphRepository   (findStudentByName/findMasters...)
                          → GdsAdapter             (GDS, 不变)

EventListener → Repository:
  GradeGraphEventListener → ConstructionGraphRepository  (save exam node/edges)
  GradeUploadedEventListener → FusionService             (不变)
```

### 2.3 Subject 节点数据模型变更

```
BEFORE:
  (kp:KnowledgePoint {name:"二次函数", subject:"数学"})
  (e:Exam {examNo:"E001", subject:"数学"})
  (f:FileNode {name:"教材.pdf", subject:"数学"})

AFTER:
  (s:Subject {name:"数学"})
  (kp:KnowledgePoint {name:"二次函数"}) -[:BELONGS_TO_SUBJECT]-> (s)
  (e:Exam {examNo:"E001"})              -[:BELONGS_TO_SUBJECT]-> (s)
  (f:FileNode {name:"教材.pdf"})         -[:BELONGS_TO_SUBJECT]-> (s)

  融合分组: groupBy(BELONGS_TO_SUBJECT → Subject 节点引用)
  同 Subject 节点 → 同融合组（无论原始 subject 字符串值）
```

---

## 3. 关键状态机

### 3.1 文档状态机 v2（修订后，回退自 v3）

```
成功路径:
  UPLOADED → PARSING → PARSED → EXTRACTING → EXTRACTED
    → ALIGNING → ALIGNED → FUSING → COMPLETED

失败回退:
  PARSING   →(失败)→ PARSED(failReason)  或 FAILED
  EXTRACTING →(失败)→ PARSED(failReason)  或 FAILED
  ALIGNING  →(失败)→ EXTRACTED(failReason) 或 FAILED  [NEW]
  FUSING    →(失败)→ ALIGNED(failReason)   或 FAILED  [CHANGED: 原回退到 EXTRACTED]

手动重处理:
  COMPLETED → EXTRACTING | ALIGNING | FUSING  [CHANGED: 新增 ALIGNING 入口]
  FAILED    → PARSING

删除:
  UPLOADED | PARSED | EXTRACTED | ALIGNED | COMPLETED | FAILED → DELETING
  DELETING → (terminal)

新增状态转换规则:
  ALIGNING.allowedTargets  = {ALIGNED, FAILED, EXTRACTED}
  ALIGNED.allowedTargets   = {FUSING, DELETING}
  EXTRACTED.allowedTargets = {ALIGNING, DELETING}          [CHANGED: 原 FUSING → ALIGNING]
  FUSING.allowedTargets    = {COMPLETED, FAILED, ALIGNED}   [CHANGED: 原 EXTRACTED → ALIGNED]
  COMPLETED.allowedTargets = {EXTRACTING, ALIGNING, FUSING, DELETING} [CHANGED: 新增 ALIGNING]
```

---

## 4. ADR 索引

| ADR | 主题 | 说明 |
|-----|------|------|
| ADR-019 | Subject Node Data Model | Subject 属性→节点迁移策略、索引、MERGE 键、迁移脚本 |
| ADR-020 | Fusion Atomicity | Neo4j 事务包装 vs 补偿回滚——选型定案 |
| ADR-021 | Repository Split | 按模块拆分的边界、方法分配、迁移策略 |
| ADR-022 | Three-Stage Pipeline | 流水线阶段定义、状态机变更、错误处理策略 |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **存量迁移脚本执行时 Neo4j 宕机** | Subject 迁移中断，部分节点有 subject 属性、部分有 BELONGS_TO_SUBJECT 边，数据不一致 | 低 | 迁移脚本幂等设计：每步先检查目标状态再执行。迁移前全量 Neo4j backup（`neo4j-admin dump`）。分批次执行（每批 1000 节点），失败可续跑 |
| R2 | **Neo4j 大事务超时**（全量融合 1000+ KPs 在一个事务中 merge + MASTERS 重算） | 全量融合超时失败，用户需重试 | 中 | ① 设置合理的事务超时（如 30s）；② 如果全量融合因大事务频繁超时，降级为 B 方案（先记后做补偿回滚）。监控首次全量融合的 Neo4j 事务耗时 |
| R3 | **前端 URL 更新滞后** | 旧 URL 返回 404 后前端功能不可用（图谱抽取按钮/子图展示页面） | 高 | 与 `frontend-ui` change 协调发布顺序：先部署后端（新旧 URL 共存过渡期？—本次不做过渡，直接 breaking），再部署前端。发布说明中明确标注 URL 变更 |
| R4 | **Entity→KP 跨文档对齐误匹配**（FuzzyMatch 将"二次函数定义"误对齐到"一次函数定义"） | 错误的 ALIGNED_TO 边污染图谱，影响后续查询和 QA 准确性 | 中 | ① 使用较高的 FuzzyMatch 阈值（≥0.85）；② 仅在相同 Subject 节点下的 KP 间匹配；③ 如果 Alignment 置信度低（<0.9），记录 warn 日志供人工审查 |
| R5 | **`core/` 包移除后 `analysis/` 和 `query/` 模块编译失败** | import 路径从 `core/model/` → `construction/model/`，遗漏更新导致编译错误 | 低 | grep 全覆盖 + `mvn compile` 验证。在 DEV 阶段逐模块确认编译通过 |
| R6 | **考试 KP MERGE ON (name, subject) 迁移后 Subject 节点化未就绪** | 如果 Subject 节点化先部署，考试 KP MERGE 还在用 `MERGE ON id`（UUID），继续产生冗余节点 | 低 | Subject 节点化和考试 KP MERGE 策略变更在同一 TASK 批次中完成，不分开部署 |

---

## 6. 不在范围

- **FusionController / FusionService 重命名**：本次仅处理 Construction 侧命名和逻辑
- **MetricsController / MetricsService 重命名**：同上
- **Subject 层级关系** (`(:Subject)-[:CHILD_OF]->(:Subject)`)：预留扩展但不实现
- **多学科 KP 归属**（一个 KP 多 BELONGS_TO_SUBJECT 边）：v2
- **前端适配**：URL breaking change 的前端跟进由 `frontend-ui` change 处理
- **Pipeline 接口抽象**：当前两阶段固定，不需要策略模式；待未来流水线变体需求出现时再抽象
- **GDS 投影参数中的 subject 过滤**：从字符串→节点引用，属于 metrics 模块后续优化
- **Neo4j 存量自动迁移工具**：手动执行 Cypher 迁移脚本，不做自动化 Flyway/Liquibase 集成（Neo4j 侧无迁移框架）

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `infrastructure/neo4j/node/SubjectNode.java` | 学科标准化节点 | 任何需要按学科分类/过滤/分组的 Neo4j 查询 | 后续所有与学科相关的节点（如未来 EventNode）都应通过 BELONGS_TO_SUBJECT 边关联而非字符串属性 |
| `infrastructure/neo4j/edge/BelongsToSubjectEdge.java` | 学科归属关系边 | Subject 节点与其他节点的关联 | N/A（标准 Neo4j 边类） |
| `infrastructure/neo4j/repository/ConstructionGraphRepository.java` | 构建模块专属 Neo4j 操作 | 文档抽取后的节点/边写入、子图查询、子图删除 | 构建模块内所有 Neo4j 操作均应通过此 Repository |
| `infrastructure/neo4j/repository/FusionGraphRepository.java` | 融合模块专属 Neo4j 操作 | 融合 KP 查询/合并/MASTERS、回滚 | 融合模块内所有 Neo4j 操作均应通过此 Repository |
| `infrastructure/neo4j/repository/QueryGraphRepository.java` | 查询模块专属 Neo4j 只读操作 | 智能问答、指标计算中查询 Student/KP/MASTERS/前置依赖 | QA 和 Metrics 模块的只读 Neo4j 查询均走此 Repository |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| Subject 节点化 | Neo4j 学科信息统一为 SubjectNode + BELONGS_TO_SUBJECT 边 | 所有 KnowledgePoint/Exam/FileNode 的创建和查询；融合分组逻辑 | 高——涉及存量迁移+所有 subject 查询改写，推翻需回迁属性+删节点 |
| Repository 按模块拆分 | `*GraphRepository` 命名规范，一个模块一个 Repository | 所有 graph L2 Service 和 EventListener 的依赖注入 | 中——推翻需合并回单 Repository + 更新所有调用方 import |
| 两阶段流水线 | 构建→融合显式编排 + 状态追踪（跨文档对齐由融合隐式完成） | ConstructionService + FileStatus v2 状态机 | 低——回退到单体 extract() 即可 |
| 融合原子性（Neo4j 事务） | 全量/增量融合操作包装在一个 Neo4j 事务中 | FusionServiceImpl + FusionGroupBuilder | 中——如性能不可接受可降级为补偿回滚方案 |

### 9.3 新增 / 修改的跨模块契约

```
- URL breaking change: /api/v1/graph/extract → /api/v1/graph/construction/extract
                        /api/v1/graph/document → /api/v1/graph/construction/document
  旧 URL 直接 404，不提供重定向

- FileStatus 枚举新增: ALIGNING, ALIGNED
  (在 EXTRACTED 和 FUSING 之间插入)

- GraphNodeRepository 拆分: 原类删除，无向后兼容
  调用方必须切换到 ConstructionGraphRepository / FusionGraphRepository / QueryGraphRepository

- KnowledgePointNode 构造器变更:
  new KnowledgePointNode(name, description, subject, gradeLevel, documentId) 废弃
  → new KnowledgePointNode(name, description, gradeLevel, documentId)  // subject 移出

- ExamNode 构造器变更:
  new ExamNode(examNo, name, examDate, subject) → new ExamNode(examNo, name, examDate)

- FileNode 构造器变更:
  new FileNode(name, subject, pageCount, documentId) → new FileNode(name, pageCount, documentId)
```

### 9.4 新增 / 升级的依赖

本 change 无新增 maven 依赖。

### 9.5 禁动清单变化

```
- 新增禁动: 禁止在 Neo4j 节点类中直接使用 String subject 属性
  (必须通过 BELONGS_TO_SUBJECT 边关联到 SubjectNode)

- 新增禁动: 禁止直接实例化 GraphNodeRepository（已删除）
  (必须使用 ConstructionGraphRepository / FusionGraphRepository / QueryGraphRepository)

- 解禁: application/graph/core/ 包（移空后可安全删除）

- 解禁: 原 GraphServiceImpl 中的完全限定名 new com.graphnexus.infrastructure.neo4j.edge.ExtractsEdge(...)
  → 改为正常 import
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。
