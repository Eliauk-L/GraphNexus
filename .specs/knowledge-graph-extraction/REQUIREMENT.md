# REQUIREMENT: 知识图谱抽取 — LLM 驱动的文档实体/知识点/关系识别

- **Change ID**: `knowledge-graph-extraction`
- **关联**: `@.specs/knowledge-graph-extraction/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为系统（自动化流程），我想将已解析的文档文本提交给 LLM 进行实体识别、知识点对齐和关系抽取，以便将非结构化文本转化为 Neo4j 中的结构化知识图谱。
- **US-2**：作为下游消费者（图分析模块、智能查询模块、可视化前端），我想通过 REST API 按文档 ID 查询其完整知识子图（包含节点和边），以便获取结构化的知识点、分类层次和前置依赖关系。
- **US-3**：作为模块维护者，我想图节点类型和边类型通过抽象层定义，新增类型只需注册即可被核心链路识别和持久化，以便后续快速接入 QuestionNode、MasteryEdge 等新类型而无需修改抽取和存储逻辑。
- **US-4**：作为系统运维者，我想 LLM 抽取结果在写入 Neo4j 前经过 JSON Schema 校验，以便拦截格式错误或幻觉输出，避免脏数据污染图谱。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 文档图谱抽取端到端

- **Given** 系统中存在一篇已完成解析的文档（`document` 表 `status=COMPLETED` 且 `text_content` 非空），LLM 服务可用
- **When** 发送 `POST /api/v1/graph/extract/{documentId}`
- **Then** HTTP 200，响应体包含抽取摘要（实体数量、知识点数量、分类数量、关系数量）；Neo4j 中存在：
  - 至少 1 个 `DocumentNode`（label 含文档 ID/name）
  - 至少 1 个 `EntityNode`（含 `entityType`、`originalText`、`pageNumber` 等属性）
  - 至少 1 个 `KnowledgePointNode`（含 `name`、`description` 等属性）
  - 至少 1 个 `KnowledgeCategoryNode`（含 `name` 属性）
  - `DocumentNode -[EXTRACTS]-> EntityNode` 边
  - `EntityNode -[ALIGNED_TO]-> KnowledgePointNode` 边
  - `KnowledgePointNode -[BELONGS_TO]-> KnowledgeCategoryNode` 边
  - `KnowledgeCategoryNode -[CHILD_OF]-> KnowledgeCategoryNode` 边（层次树）
  - `KnowledgePointNode -[PREREQUISITE_OF]-> KnowledgePointNode` 边（如有前置依赖）
  - `EntityNode -[DERIVES|CONTAINS|REFERENCES]-> EntityNode` 边（如实体间有关联）
- **验证方式**: 先通过 `document-process-pdf-minimal` 接口上传并解析一个中文教辅 PDF，再 `curl -X POST http://localhost:8080/api/v1/graph/extract/{id}`，最后用 Cypher `MATCH (n) RETURN labels(n), count(*)` 确认各 label 节点数 > 0

### AC-2 · 按文档 ID 查询知识子图

- **Given** AC-1 抽取已完成
- **When** 发送 `GET /api/v1/graph/document/{documentId}`
- **Then** HTTP 200，响应体包含 `nodes` 数组（每项含 `id`、`labels`、`properties`）和 `edges` 数组（每项含 `id`、`type`、`sourceNodeId`、`targetNodeId`、`properties`），节点数 ≥ AC-1 产出的节点数，边数 ≥ AC-1 产出的边数
- **验证方式**: `curl http://localhost:8080/api/v1/graph/document/{id}` 后检查 JSON 结构，断言 `nodes` 和 `edges` 非空数组，用 `jq` 验证字段完整性

### AC-3 · LLM 抽取结果 JSON Schema 校验

- **Given** 定义了抽取结果的 JSON Schema（含 `entities`、`knowledgePoints`、`categories`、`relationships` 等字段，entityType 枚举为 `[DEFINITION, FORMULA, CONCEPT, EXAMPLE, SOLUTION]`，relationshipType 枚举为 `[DERIVES, CONTAINS, REFERENCES]`）
- **When** LLM 返回一段抽取结果 JSON
- **Then** 系统对 JSON 执行 Schema 校验：
  - 合法 JSON（字段齐全、类型匹配、枚举值在范围内）→ 通过，进入 Neo4j 写入
  - 非法 JSON（缺少必填字段 `name`、类型枚举值越界 `entityType: "UNKNOWN"`、或 JSON 解析失败）→ 拒绝写入，返回 HTTP 400 + 具体校验错误描述
- **验证方式**: 单元测试分别传入合法 JSON 字符串和 3 种非法 JSON 字符串（缺字段/枚举越界/非 JSON），断言前者返回通过、后者抛异常含字段名

### AC-4 · LLM 最小可用调用（LLMGateway 落地）

- **Given** `application.yml` 中配置了 LLM API 的 endpoint、apiKey、model 参数，LLM 服务端可用
- **When** L2 Service 调用 `LlmGateway.chat(systemPrompt, userMessage)`
- **Then** 返回 LLM 的原始文本响应（非空字符串）；调用失败（网络超时、API 返回 4xx/5xx）时抛出 `BusinessException`（错误码 `C0001`），由 `GlobalExceptionHandler` 统一处理
- **验证方式**: 集成测试（`@SpringBootTest` + `@TestPropertySource` 指向真实或 mock LLM 端点），断言正常返回字符串；mock 一个返回 500 的端点，断言抛出 `BusinessException` 且错误码为 `C0001`

### AC-5 · 图节点/边抽象层可扩展

- **Given** 定义了图节点抽象基类/接口 `GraphNode` 和图边抽象基类/接口 `GraphEdge`，以及类型注册机制
- **When** 开发者创建一个新的节点子类（如 `TestNode extends GraphNode`，label = `TestLabel`），通过注册机制声明其类型，然后调用通用 `GraphRepository.save(TestNode)` 和 `GraphRepository.findByDocumentId(documentId)`
- **Then** `TestNode` 成功写入 Neo4j 且可通过通用查询方法检索到，期间未修改 `GraphRepository` 或 `GraphService` 的任何代码
- **验证方式**: 单元测试：定义 `TestNode`/`TestEdge` → 注册 → `save` → `findByDocumentId` → 断言查询结果包含 TestNode 实例且 label 为 `TestLabel`

### AC-6 · 空文本/未就绪文档拒绝抽取

- **Given** 一篇文档 `text_content` 为空字符串、纯空白字符或 `null`，或文档 `status != COMPLETED`
- **When** 发送 `POST /api/v1/graph/extract/{documentId}`
- **Then** HTTP 400，响应体 `errorCode` 为 `A0003`（文档文本为空）或 `A0004`（文档状态不允许抽取），`message` 明确说明原因；Neo4j 中不存在与该文档 ID 关联的任何节点或边
- **验证方式**: 单元测试：分别对空文本文档和 `status=UPLOADED` 文档调用抽取，断言抛 `BusinessException` 且 code 对应

### AC-7 · LLM 调用失败时无脏数据

- **Given** 文档文本有效，但 LLM 服务不可用（网络超时或返回 5xx）
- **When** 发送 `POST /api/v1/graph/extract/{documentId}`
- **Then** HTTP 500，响应体 `errorCode` 为 `C0001`（第三方服务异常），日志中包含 traceId、documentId 和 LLM 调用失败原因；Neo4j 中不存在与该文档 ID 关联的任何新写入节点或边（全量覆盖时旧数据也不应被删除——即事务回滚）
- **验证方式**: 集成测试 mock LLM 服务抛异常，断言 Neo4j 中无新数据写入；日志中 grep traceId 能找到对应错误

### AC-8 · 重复抽取为全量覆盖

- **Given** 文档已完成一次抽取（Neo4j 中存在子图），文档 `text_content` 未变更
- **When** 再次发送 `POST /api/v1/graph/extract/{documentId}`
- **Then** HTTP 200，旧的子图节点和边被清除，新的子图重新写入（节点/边 ID 可能不同，但数量级一致）；两次查询 `GET /api/v1/graph/document/{documentId}` 返回的节点/边数量差值 ≤ 20%
- **验证方式**: 对同一文档连续调用两次抽取 → 两次子图查询，断言节点数差异不超 20%（LLM 非确定性导致小幅波动）

---

## 范围切分

### v1（本次必做）

- LLM 实体抽取：EntityNode（DEFINITION / FORMULA / CONCEPT / EXAMPLE / SOLUTION 五种类型）
- LLM 实体间关系抽取：DERIVES / CONTAINS / REFERENCES 三种边
- LLM 知识点对齐：实体 → KnowledgePoint 的 ALIGNED_TO 边
- LLM 知识分类层次推断：KnowledgeCategoryNode + BELONGS_TO + CHILD_OF 树
- LLM 前置依赖识别：KnowledgePoint 间的 PREREQUISITE_OF 边
- DocumentNode 创建 + EXTRACTS 边连接
- 图节点/边抽象层（`GraphNode` / `GraphEdge` 基类 + 类型注册机制）
- Neo4j 持久化：4 类节点 + 8 类边通过 Spring Data Neo4j Repository 写入
- REST API：`POST /api/v1/graph/extract/{documentId}` 触发抽取 + `GET /api/v1/graph/document/{documentId}` 查询子图
- LLM Prompt 设计（System Prompt + Few-shot 示例）+ 抽取结果 JSON Schema
- JSON Schema 校验拦截非法 LLM 输出
- LLMGateway 最小可用实现（`chat(systemPrompt, userMessage)` → `String`，含异常处理）
- 错误处理：空文本、未就绪文档、LLM 调用失败、Schema 校验失败
- 重复抽取全量覆盖（先删旧子图再写新子图，事务保护）
- `common/exception/ErrorCode.java` 新增本次需要的错误码枚举值（`C0001`、`A0003`、`A0004` 等）

### v2（下一轮考虑，不本次）

- 多文档知识融合：同一 KnowledgePoint 被多篇文档的 Entity 对齐时去重合并
- 增量抽取：仅对文档中新增/修改的段落重新抽取，而非全量覆盖
- 异步抽取：通过 RabbitMQ 消息触发抽取，请求立即返回 `202 Accepted` + 轮询进度
- LLM 分块策略（chunking）：超长文档自动分段抽取 + 跨段实体合并
- 抽取置信度评分：LLM 返回每个实体/关系的 confidence，低于阈值标记 `LOW_CONFIDENCE` 供人工审核
- 非 PDF 文档类型适配（Word `.docx`、Markdown `.md`、纯文本 `.txt`）
- 知识分类种子数据预置（如"初中数学→代数→函数"标准分类树作为 LLM 参考）
- 图谱变更历史记录（审计日志：谁在何时触发了抽取，新增/删除了哪些节点）

### out（永远不做）

- 事件图谱（ExamNode / EventNode / StudentNode / MasteryEdge / RELATES_TO / HAS_EVENT）—— 独立 change
- 宽图谱显式融合逻辑（参考文档第五步）—— 独立 change
- 前端知识图谱可视化界面 —— 独立 change
- 抽取结果人工审核/修正工作流 —— 独立 change
- QuestionNode 及相关边类型 —— 独立 change
- 知识点的增删改查管理后台 —— 独立 change
- 知识图谱版本管理/回滚（如 Git 式图谱快照）
- 多语言跨语种知识对齐（中文知识点 ↔ 英文知识点映射）

---

## 非功能性需求

- **性能**: 文档 `text_content` ≤ 50K 字符的单次抽取（含 LLM 调用 + Neo4j 写入）≤ 60s（取决于 LLM API 响应速度）；子图查询 ≤ 500ms（节点数 ≤ 200 时）
- **可访问性**: 无（纯后端 API）
- **安全**: API 端点需通过认证后访问（JWT 由后续 `basic` 模块实现，本次与 `document-process-pdf-minimal` 现状一致）；LLM API Key 不作为请求参数传递，必须通过环境变量或配置文件注入
- **兼容性**: Neo4j 5.x + Spring Data Neo4j 7.x；JDK 17；与 `document-process-pdf-minimal` 产出的 `text_content` 格式兼容
- **可靠性**: LLM 调用失败 → 事务回滚，Neo4j 无脏数据；JSON Schema 校验拦截非法 LLM 输出；重复抽取为原子覆盖（删+写在同一事务内）
- **可观测性**: 全链路 Trace ID 串联抽取请求 → LLM 调用 → Neo4j 写入（已有 `TraceIdFilter`）；日志记录 LLM token 消耗、抽取耗时、实体/关系数量；LLM 调用失败时日志包含 request/response 摘要

## 依赖与假设

- **依赖**: `document-process-pdf-minimal` 已完成的文档上传/解析链路（`text_content`、`page_count`、`DocumentDO`）
- **依赖**: `init-platform` 已完成的公共模块（`BusinessException`、`ErrorCode`、`GlobalExceptionHandler`、`TraceIdFilter`、`ApiResponse`）
- **依赖**: Neo4j 5.x 本地运行（`spring.neo4j.uri` 已配置，数据库 `graphnexus` 可用）
- **依赖**: LLM API 端点可用（如 OpenAI 兼容 API、本地部署的模型服务），API Key 已配置
- **依赖**: `pom.xml` 中已存在的依赖：`spring-boot-starter-data-neo4j`、`spring-ai-openai`（或通过 WebClient 直调 LLM API）
- **假设**: 文档 `text_content` 为中文教辅文本，LLM 模型对中文 NER/RE 有基本能力（如 GPT-4、DeepSeek、Qwen 等）
- **假设**: 单篇文档 `text_content` 长度 ≤ 50K 字符（约 15-20 页 PDF），无需分块即可一次性提交 LLM
- **假设**: LLM 返回的 JSON 格式基本合规（虽可能有字段遗漏，但不会完全不可解析）
- **假设**: 知识分类层次不超过 5 层（如 学科→分支→模块→章节→知识点）

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。