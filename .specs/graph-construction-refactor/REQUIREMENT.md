# REQUIREMENT: 图谱构建模块重命名 + 逻辑优化

- **Change ID**: `graph-construction-refactor`
- **关联**: `@.specs/graph-construction-refactor/CHANGE.md`、`@.specs/CONTEXT.md`、`@.specs/knowledge-graph-extraction/REQUIREMENT.md`、`@.specs/wide-graph-fusion/REQUIREMENT.md`

---

> **⚠️ 修订记录（2026-06-21）**：原 US-4/US-5/AC-5 描述的"三阶段流水线（含独立实体对齐阶段）"已修订为**两阶段流水线（构建→融合）**。跨文档实体对齐改由融合隐式完成（redirectEdges 重定向 ALIGNED_TO 边），不再有独立对齐阶段，FileStatus 无 ALIGNING/ALIGNED 状态。下文相关条目以此修订为准，详见 ADR-022。

---

## 用户故事

- **US-1**：作为 API 消费者，我想使用 `/api/v1/graph/construction/` 端点进行图谱抽取和子图查询，以便与 `/api/v1/graph/fusion`、`/api/v1/graph/metrics` 形成清晰的命名空间层次，一眼就能区分三个子域。
- **US-2**：作为开发者，我想 `GraphController`/`GraphService` 重命名为 `ConstructionController`/`ConstructionService`，并将 Service 移至 `construction/` 包，以便代码中不再有泛化的 "Graph" 命名混淆模块边界。
- **US-3**：作为开发者，我想 `GraphNodeRepository`（666 行）按构建/融合/查询三个模块拆分为独立 Repository，以便修改一个模块的 Neo4j 操作时不会误伤其他模块。
- **US-4**：作为教务人员，我想文档上传后系统自动执行"图谱构建 → 图谱融合"两阶段流水线，文档状态在各阶段可追踪，以便知道当前处理进度以及哪一阶段出了问题。
- **US-5**：作为系统，我想新文档抽取的实体通过图谱融合隐式完成跨文档对齐——融合合并重复 KP 时自动把 ALIGNED_TO 边重定向到规范 KP，以便不同文档中讨论同一知识点的实体最终关联到同一个 KP 节点。
- **US-6**：作为系统，我想全量融合和增量融合均能合并来自文档抽取和考试成绩两种来源的同名知识点，以便 `fusionSource=DOCUMENT` 和 `fusionSource=CSV_IMPORT` 的 KP 融合为统一节点，不再出现两个独立节点代表同一知识点。
- **US-7**：作为系统，我想融合操作具备原子性保障——全部成功则提交、任一失败则回滚——以便中途失败时 Neo4j 图谱不陷入半融合状态。
- **US-8**：作为系统，我想 Subject（学科）从 KnowledgePoint/Exam/FileNode 的字符串属性改为独立的 SubjectNode 节点 + BELONGS_TO_SUBJECT 边，融合分组按 Subject 节点引用而非字符串匹配，以便消除 `"数学"` 与 `"高中数学"` 被错误分入不同融合组的问题。
- **US-9**：作为开发者，我想消除 `GraphServiceImpl` 中的完全限定名、明确 `@Transactional` 语义、修正边数统计遗漏 BELONGS_TO 边的问题，以便代码质量和统计准确性达到项目规范要求。

---

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 重命名一致性 — ConstructionController

- **Given** 系统正常运行
- **When** 查看 Controller 层代码
- **Then** `GraphController` 类不存在，存在 `ConstructionController`，类名、`@RequestMapping`、`@Tag` 注释全部使用 "Construction" 语义。`GraphService` / `GraphServiceImpl` 类不存在，存在 `ConstructionService` / `ConstructionServiceImpl`，位于 `application/graph/construction/service/` 包下（与 `ExtractionService` 同包）
- **验证方式**: `grep -r "GraphController\|GraphService\|GraphServiceImpl" src/main/java/` 返回空；`ls src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java` 存在；`ls src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService*.java` 存在

### AC-2 · URL 变更 — 旧路径 404 + 新路径正常

- **Given** 系统正常运行，已有一个已解析完成的文档（`documentId=1`）
- **When** 发送 `POST /api/v1/graph/extract/1` 和 `GET /api/v1/graph/document/1`
- **Then** 两个旧 URL 均返回 HTTP 404
- **验证方式**: `curl -X POST http://localhost:8080/api/v1/graph/extract/1` 断言 404；`curl http://localhost:8080/api/v1/graph/document/1` 断言 404

### AC-3 · URL 变更 — 新路径正常工作

- **Given** 系统正常运行，文档 `documentId=1` 状态为 `PARSED`，文本内容非空
- **When** 发送 `POST /api/v1/graph/construction/extract/1`
- **Then** HTTP 200，响应体包含 `documentId`、`entityCount`、`knowledgePointCount`、`categoryCount`、`edgeCount`，且 `edgeCount` 包含 EXTRACTS + BELONGS_TO 等所有实际创建的边。同时：
  - Neo4j 中该文档的 EntityNode / KnowledgePointNode / KnowledgeCategoryNode 已创建
  - Neo4j 中 ALIGNED_TO / BELONGS_TO / EXTRACTS 等关系边已创建
- **验证方式**: `curl -X POST http://localhost:8080/api/v1/graph/construction/extract/1` 断言 200；`curl http://localhost:8080/api/v1/graph/construction/document/1` 断言 200 并返回节点+边数据

### AC-4 · Repository 三模块独立

- **Given** 系统代码已重构
- **When** 查看 `infrastructure/neo4j/repository/` 包
- **Then** 原 `GraphNodeRepository` 类已删除。存在三个独立 Repository 文件：`ConstructionGraphRepository`（含 save/saveEdge/deleteByDocumentId/deleteEdgesByExamNo/deleteExamNode 等方法）、`FusionGraphRepository`（含 findAllKnowledgePoints/redirectEdges/batchUpsertMastersEdges/deleteKnowledgePoints 等方法）、`QueryGraphRepository`（含 findStudentByName/findPrerequisitesUpstream/findDistinctSubjects 等方法）。所有原调用方（`ConstructionServiceImpl`、`FusionServiceImpl`、`FusionRollbackService`、`MastersRecalculationService`、`MetricsServiceImpl`、`GradeGraphEventListener`、`GradeUploadedEventListener`）已切换到对应新 Repository
- **验证方式**: `grep -r "GraphNodeRepository" src/main/java/` 返回空（或仅出现在 import 已删除的注释中）；运行 `mvn test` 全部通过

### AC-5 · 两阶段流水线 — 构建→融合（修订后）

- **Given** 文档 `documentId=1` 状态为 `PARSED`，图谱中已有来自其他文档的同名 KP（如 KP"二次函数"已存在）
- **When** 发送 `POST /api/v1/graph/construction/extract/1`
- **Then** HTTP 200，处理过程按两个阶段执行：
  - 阶段一「构建」：LLM 抽取完成，本次文档的 Entity/KP/Category + 单文档内 ALIGNED_TO 边 + SubjectNode 写入 Neo4j，`document.status` 经历 `EXTRACTING → EXTRACTED`
  - 阶段二「融合」：增量融合执行，合并跨源重复 KP（含同名跨文档 KP），`redirectEdges` 自动把 ALIGNED_TO 边重定向到规范 KP，`document.status` 经历 `FUSING → COMPLETED`
- **验证方式**: 先上传两份不同文档（均含"二次函数"相关内容），第一份走完整流水线后 `document.status=COMPLETED`；第二份走完后，Neo4j 查询 `MATCH (e:Entity)-[:ALIGNED_TO]->(kp:KnowledgePoint {name:'二次函数'}) RETURN count(DISTINCT e)` 断言 ≥ 两份文档的实体总数（融合后两份文档的实体都指向规范 KP）

### AC-6 · 融合失败显式化

- **Given** 文档 `documentId=1` 状态为 `PARSED`，但 Neo4j 融合相关操作因某种原因失败（如 Neo4j 连接中断）
- **When** 发送 `POST /api/v1/graph/construction/extract/1`
- **Then** HTTP 200（抽取本身成功），但响应体中包含 `fusionWarning` 字段标记融合未完成及原因，`document.status` 停留在 `EXTRACTED`（不进入 `FUSING`/`COMPLETED`）。**核心约束：调用方可感知融合未完成，不能静默返回 200 + COMPLETED**
- **验证方式**: 模拟融合失败场景（如临时关闭 Neo4j），断言响应中能感知到融合异常，`document.status != COMPLETED`

### AC-7 · 边数统计准确

- **Given** 文档 `documentId=1` 已完成抽取
- **When** 查看 `POST /api/v1/graph/construction/extract/1` 的响应 `edgeCount` 字段
- **Then** `edgeCount` 等于 Neo4j 中该文档实际创建的边总数：EXTRACTS 边数（= entityCount）+ BELONGS_TO 边数（= knowledgePointCount）+ DERIVES/CONTAINS/REFERENCES 边数 + ALIGNED_TO 边数 + CHILD_OF 边数 + PREREQUISITE_OF 边数。不再遗漏任何边类型
- **验证方式**: 抽取后分别查询 Neo4j 各类边数量求总和，断言等于 API 返回的 `edgeCount`

### AC-8 · 跨源 KP 融合 — 同名精确匹配

- **Given** 图谱中已存在文档抽取的 KP `fusionSource=DOCUMENT`，名称为"二次函数顶点坐标"，`subject=数学`。随后上传考试数据，考试中也包含知识点"二次函数顶点坐标"
- **When** 执行全量融合（`POST /api/v1/graph/fusion/execute`）或增量融合（文档抽取后自动触发）
- **Then** 两个来源的同名 KP 合并为一个节点：
  - 合并后节点的 `fusionSource` 包含 `"DOCUMENT,CSV_IMPORT"`（或 `"CSV_IMPORT,DOCUMENT"`）
  - 源 KP 节点已删除
  - 所有入边/出边已重定向到规范 KP
  - 考试 KP 创建时不再通过 `MERGE ON id` 产生重复节点，而是 `MERGE ON (name, subject)` 或通过融合阶段的精确匹配 pass 确保唯一
- **验证方式**: 
  - 上传考试数据后查询 Neo4j：`MATCH (kp:KnowledgePoint {name:'二次函数顶点坐标'}) RETURN count(kp)` 断言 = 1
  - 查询该 KP 的 `fusionSource`：断言包含 `DOCUMENT` 和 `CSV_IMPORT`
  - 查询该 KP 的所有入边：断言同时包含来自 FileNode 的 EXTRACTS 边和来自 ExamNode 的 TESTED 边

### AC-9 · 融合原子性

- **Given** 图谱中存在 3 个学科的 KP（数学、物理、英语），数学有 2 组可融合 KP，物理有 3 组，英语有 1 组
- **When** 执行全量融合，在物理学科融合过程中模拟 Neo4j 异常（如事务回滚）
- **Then** 数学的融合结果**不生效**（全部回滚），或者数学+物理全部回滚。融合日志状态为 `FAILED`。重新查询 Neo4j，KP 节点数与融合前完全一致。**不允许出现数学已融合但物理未融合的半完成状态**
- **验证方式**: 记录融合前各学科 KP 数，模拟失败后重新查询，断言 KP 数无变化。融合日志 `status=FAILED`

### AC-10 · Subject 节点化 — 数据模型变更

- **Given** 系统代码已重构，存量 Neo4j 数据中 KnowledgePoint / Exam / FileNode 节点存在 `subject` 字符串属性
- **When** 执行存量数据迁移脚本
- **Then** 
  - 所有存量节点的 `subject` 属性值被提取为 `(:Subject {name: <subject值>})` 节点
  - 每个原节点创建 `[:BELONGS_TO_SUBJECT]` 边指向对应的 SubjectNode
  - 相同 `subject` 值的多个节点指向同一个 SubjectNode
  - 原节点的 `subject` 属性已移除
  - 迁移脚本幂等：重复执行不产生重复 SubjectNode 或重复边
- **验证方式**: 
  - 迁移后 `MATCH (n) WHERE n.subject IS NOT NULL RETURN count(n)` 断言 = 0
  - `MATCH (s:Subject) RETURN DISTINCT s.name` 返回的学科列表与迁移前 `SELECT DISTINCT subject FROM ...` 一致
  - 重复执行迁移脚本，Neo4j 节点数/边数无变化

### AC-11 · Subject 节点化 — 新数据走节点路径

- **Given** 系统代码已重构，SubjectNode + BELONGS_TO_SUBJECT 边机制已就位
- **When** 上传新文档并执行抽取，或上传新考试数据
- **Then** 新创建的 KnowledgePointNode / ExamNode / FileNode **不设置** `subject` 属性，而是创建/查找 SubjectNode 并创建 BELONGS_TO_SUBJECT 边
- **验证方式**: 上传新文档抽取后，`MATCH (kp:KnowledgePoint) WHERE kp.subject IS NOT NULL RETURN count(kp)` 断言 = 0（仅存量迁移前的旧数据有 subject 属性，迁移后全部为 0）

### AC-12 · 融合分组基于 Subject 节点引用

- **Given** Subject 节点化已完成，存在 `(:Subject {name:'数学'})` 节点。文档 KP 和考试 KP 均通过 BELONGS_TO_SUBJECT 边指向该 Subject 节点
- **When** 执行全量融合
- **Then** 融合分组不再使用 `groupBy(kp.subject)` 字符串匹配，而是按 `BELONGS_TO_SUBJECT` 边指向的 Subject 节点进行分组。指向同一个 `(:Subject {name:'数学'})` 的所有 KP 归入同一融合组，无论其原始文档中 `subject` 字段写的是"数学"还是"高中数学"
- **验证方式**: 创建两个 KP，一个原始 subject="数学"，另一个原始 subject="高中数学"，均通过 BELONGS_TO_SUBJECT 指向同一 `(:Subject {name:'数学'})`，执行融合后断言两者在同一融合组

### AC-13 · LLM 提示词 subject 规范化

- **Given** 系统正常运行
- **When** 查看 `ExtractionPromptBuilder.buildSystemPrompt()` 的 System Prompt 内容
- **Then** Prompt 中包含 subject 名称规范化指令：要求 LLM 使用标准化学科名（如"数学"而非"高中数学"或"初中数学"），并优先使用文档元数据中提供的学科名。Few-shot 示例中 knowledgePoint 的 `subject` 字段取值遵循此规范
- **验证方式**: 阅读 `ExtractionPromptBuilder.java` 源码，断言 System Prompt 中包含 subject 规范化相关指令；读取 `ExtractionService.convertToDomain()`，断言不再设置 KnowledgePointNode 的 `subject` 属性字段，改为创建/查找 SubjectNode + BELONGS_TO_SUBJECT 边

### AC-14 · 代码质量修复

- **Given** 原有代码中存在完全限定名 `new com.graphnexus.infrastructure.neo4j.edge.ExtractsEdge(...)`、`@Transactional` 在 Neo4j 写方法上
- **When** 查看重构后的 `ConstructionServiceImpl`
- **Then** 
  - 所有 Neo4j 边类通过 `import` 导入，不再使用完全限定名
  - `@Transactional` 注解仅用于包含 MySQL 写操作的方法，纯 Neo4j 写方法不加 `@Transactional`（或显式使用 Neo4j 事务）
  - 方法注释明确标注事务边界（"此方法中 Neo4j 写入不在 Spring @Transactional 范围内"）
- **验证方式**: `grep "com.graphnexus.infrastructure" src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java` 断言无匹配

### AC-15 · 存量测试全部通过

- **Given** 已有 `knowledge-graph-extraction`、`wide-graph-fusion`、`grade-management-refactor` 的单元测试和集成测试
- **When** 执行 `mvn test`
- **Then** 所有测试通过（URL 路径变更、Repository 拆分、类重命名后的测试代码已同步更新）。无跳过的测试，无 `@Disabled` 新增
- **验证方式**: `mvn test` 输出 `BUILD SUCCESS`，`Tests run: N, Failures: 0, Errors: 0, Skipped: 0`

---

## 范围切分

### v1（本次必做）

- `GraphController` → `ConstructionController` 重命名 + URL 变更（`/api/v1/graph` → `/api/v1/graph/construction`）
- `GraphService` → `ConstructionService` 重命名 + 包移动（`core/service/` → `construction/service/`）
- 关联 BO/VO（`ExtractionResultBO/VO`、`GraphSubgraphBO/VO` 等）同步移动至 `construction/` 包
- `extract()` 方法拆分为两阶段流水线：构建 → 融合（跨文档对齐由融合隐式完成）
- `GraphNodeRepository` 拆分为 `ConstructionGraphRepository` / `FusionGraphRepository` / `QueryGraphRepository`，原类删除
- 边数统计修正（纳入 BELONGS_TO 边）
- 融合失败显式化（状态标记或响应 warning）
- 跨源 KP 融合显式化（考试 KP MERGE ON name+subject + 融合阶段精确匹配前置 pass）
- 融合原子性保障（Neo4j 事务包装或补偿回滚——DESIGN 阶段选型）
- Subject 从字符串属性改为独立节点（SubjectNode + BELONGS_TO_SUBJECT 边 + 存量迁移脚本）
- LLM 抽取提示词同步更新（subject 规范化指令）
- 代码质量修复（消除 FQN、明确 @Transactional 语义）
- `document.status` 状态机维持 v2（原计划扩展 ALIGNING/ALIGNED 已回退，对齐由融合隐式完成）
- 存量测试更新（URL 变更 + Repository 拆分 + Subject 节点化适配）
- 所有原调用方切换到新 Repository（`ConstructionServiceImpl`、`FusionServiceImpl`、`FusionRollbackService`、`MastersRecalculationService`、`MetricsServiceImpl`、`GradeGraphEventListener`、`GradeUploadedEventListener`）
- `application/graph/core/` 空包清理（如移走后无剩余类则删除）

### v2（下一轮考虑，不本次）

- **Subject 层级关系**：`(:Subject)-[:CHILD_OF]->(:Subject)` 父子学科关系（如 `初中数学 → 数学`）
- **多学科 KP 归属**：一个 KnowledgePoint 通过多条 BELONGS_TO_SUBJECT 边属于多个学科
- **融合对齐精度提升**：跨文档实体对齐目前由融合的 FuzzyMatch 隐式完成，未来可引入向量相似度（embedding）提升 KP 匹配精度
- **FusionController / FusionService 重命名**：融合模块 Controller 和 Service 的命名规范化
- **MetricsController / MetricsService 重命名**：指标模块 Controller 和 Service 的命名规范化
- **前端同步适配**：URL 变更后前端 `frontend-ui` 跟进更新

### out（永远不做）

- **Neo4j 节点/边实体类重命名**：`GraphNode` / `GraphEdge` 抽象基类及其子类（`EntityNode`、`KnowledgePointNode`、`ExtractsEdge` 等）命名不变——它们是图数据抽象层，不属于"构建模块"专属
- **FusionController / MetricsController URL 变更**：`/api/v1/graph/fusion` 和 `/api/v1/graph/metrics` 路径保持不变
- **API 版本号变更**：仍然使用 `v1`，不引入 `v2`
- **GraphNodeRepository 向后兼容层**：原 Repository 删除后不留 `@Deprecated` 转发类，调用方直接切换到新 Repository
- **MySQL 中 subject 字段删除**：`document.subject` 和 `exam_record.subject` 保留不删——MySQL 侧是上传时的学科标签，Neo4j 侧是融合后的规范学科节点，两者语义不同

---

## 非功能性需求

- **性能**: 单文档抽取（含两阶段流水线）处理时间不因拆分而增加 > 10%；存量 Subject 迁移脚本在 10 万节点规模下 ≤ 30s 完成
- **安全**: 无新增安全需求。现有 JWT 认证保持不变
- **兼容性**: 
  - API breaking change：旧 URL 直接 404，不提供 301 重定向（前端需同步更新）
  - 响应体结构不变：`ExtractionResultVO` 和 `GraphSubgraphVO` 字段不变
  - Neo4j 存量数据迁移脚本必须幂等
- **可观测性**: 
  - 两阶段流水线每阶段开始/完成/失败均记日志（含 documentId + 阶段名 + 耗时）
  - 融合原子性回滚时记录完整回滚原因和受影响 KP 列表
  - Subject 迁移脚本记录迁移前后的节点/边数量对比

---

## 依赖与假设

- **依赖**:
  - `infrastructure/neo4j/repository/GraphNodeRepository` — 拆分源，将被删除
  - `infrastructure/neo4j/node/KnowledgePointNode` — 需移除 `subject` 属性字段
  - `infrastructure/neo4j/node/ExamNode` — 需移除 `subject` 属性字段
  - `infrastructure/neo4j/node/FileNode` — 需移除 `subject` 属性字段
  - `infrastructure/neo4j/node/SubjectNode` — **新增**节点类型
  - `infrastructure/neo4j/edge/BelongsToSubjectEdge` — **新增**边类型
  - `application/graph/construction/service/ExtractionPromptBuilder` — LLM 提示词需更新
  - `application/graph/construction/service/ExtractionService` — convertToDomain 逻辑变更
  - `application/graph/construction/listener/GradeGraphEventListener` — KP 创建策略变更
  - `application/graph/fusion/service/impl/FusionGroupBuilder` — 融合分组逻辑变更
  - `application/graph/fusion/service/impl/FusionServiceImpl` — 原子性包装
  - `application/graph/fusion/service/impl/FusionRollbackService` — 可能需要适配原子性方案
  - `application/graph/metrics/service/impl/MetricsServiceImpl` — Repository 切换
  - `api/graph/controller/GraphController` — 重命名源
  - `api/graph/dto/graph/ExtractionResultVO` / `GraphSubgraphVO` — 包移动
  - `application/graph/core/` — Service/BO 移出后可能被删除
  - `infrastructure/mysql/file/entity/FileStatus` — 状态机维持 v2（原 ALIGNING/ALIGNED 扩展已回退）
  - Neo4j 迁移脚本 — 存量 subject 属性 → SubjectNode 迁移

- **假设**:
  - Neo4j 5.x 支持通过 `Neo4jClient` 手动管理事务边界
  - 存量数据中所有 KnowledgePoint / Exam / FileNode 节点的 `subject` 属性非空
  - 同一学科的标准名称唯一（如"数学"不会同时存在 `(:Subject {name:'数学'})` 和 `(:Subject {name:'Mathematics'})`），学科名称规范化由 LLM 提示词 + 上传时的人工输入保证
  - 前端调用方已知晓 URL breaking change，将在 `frontend-ui` change 中跟进
  - 现有测试框架（JUnit 5 + Mockito + Spring Boot Test）支持新 Repository 结构下的测试

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。
