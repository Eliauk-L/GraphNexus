# DESIGN: 智能问答 — 图剪枝驱动的 LLM 分析与诊断

- **Change ID**: `intelligent-qa`
- **关联**: `@.specs/intelligent-qa/REQUIREMENT.md`、`@.specs/intelligent-qa/CHANGE.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 技术栈已在 `@.specs/CONTEXT.md`「已锁技术决策」中锁定，本 change 无新增外部依赖。

- **选定**：延续既有 Java 17 + Spring Boot 3.3.x 四层架构
- **后端**：Spring Boot 3.3.x / Spring MVC / Spring Data JPA + Neo4jClient
- **LLM 集成**：LangChain4j 1.0.0-beta1（既有 `Langchain4jLlmGateway`），通过 `LlmGateway.chat()` 接口调用
- **数据库**：MySQL 8.0（新增 `query_task` 日志表）+ Neo4j 5.x（只读查询，无 schema 变更）
- **关键依赖**：无新增 `pom.xml` 依赖。Jackson（JSON 序列化，已有）、Lombok（已有）、Spring Web（REST API，已有）
- **理由**：智能问答是既有图节点/边抽象体系 + LLM 网关上的纯业务逻辑扩展，不引入新框架或库。子图序列化为自定义结构化文本（非 Graphviz/Mermaid），无需模板引擎。Prompt 模板通过 classpath 文件管理，由 Spring `ResourceLoader` 加载
- **明确排除**：不引入 Thymeleaf/FreeMarker（Prompt 组装仅做纯字符串拼接 + 参数替换，不涉及复杂模板逻辑）、不引入 Spring AI 的 ChatClient（`LlmGateway` 接口已封装）、不引入 Redis（v1 不做缓存）

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 确认的实际清单）：
- application/llmgateway/service/LlmGateway.java              （既有 · LLM 调用入口，QA 仅调用 chat()）
- application/analysis/                                       （既有 · 图分析模块包结构已存在，本次新增剪枝策略）
- infrastructure/llm/client/Langchain4jLlmGateway.java        （既有 · LLM 实现，不修改）
- infrastructure/neo4j/repository/GraphNodeRepository.java    （既有 · 新增只读查询方法）
- infrastructure/neo4j/node/StudentNode.java                  （既有 · 只读引用）
- infrastructure/neo4j/node/KnowledgePointNode.java           （既有 · 只读引用）
- infrastructure/neo4j/edge/GraphEdge.java                    （既有 · 只读引用）
- infrastructure/neo4j/edge/MastersEdge.java                  （既有 · 只读引用）
- infrastructure/neo4j/edge/PrerequisiteEdge.java             （既有 · 只读引用）
- infrastructure/mysql/document/ExamRecordRepository.java     （既有 · 新增按 studentNo+subject 查询方法）
- common/exception/ErrorCode.java                             （既有 · 新增 A0019/A0020/A0021）
- common/ApiResponse.java                                     （既有 · 沿用）

新增模块：
- api/query/controller/QueryController.java                      （新 · L1 QA 端点，ask/ask-async/result）
- api/analysis/controller/AnalysisController.java               （新 · L1 子图端点）
- api/query/dto/QueryAskRequest.java                             （新 VO · 请求体）
- api/query/dto/QueryAskResponse.java                            （新 VO · 同步/异步响应）
- api/query/dto/QueryResultResponse.java                         （新 VO · 轮询结果）
- api/analysis/dto/SubgraphResponse.java                         （新 VO · 子图数据）
- application/analysis/strategy/SubgraphPruningStrategy.java       （新接口 · 剪枝策略，归属 analysis 模块）
- application/analysis/strategy/StudentDiagnosisStrategy.java      （新实现 · v1）
- application/analysis/model/PruningRequest.java                   （新 BO · 剪枝请求，归属 analysis 模块）
- application/analysis/model/PrunedSubgraph.java                   （新 BO · 剪枝结果，归属 analysis 模块）
- application/query/service/QueryService.java                       （新接口 · L2 QA 编排）
- application/query/service/impl/QueryServiceImpl.java              （新实现 · 核心链路）
- application/query/prompt/PromptTemplateService.java            （新 · Prompt 组装）
- application/query/model/QueryIntent.java                          （新 BO · 意图枚举）
- application/query/model/QueryResultBO.java                        （新 BO）
- application/query/config/QueryProperties.java                     （新 · yml 配置映射）
- infrastructure/mysql/query/QueryTaskDO.java                       （新 DO）
- infrastructure/mysql/query/QueryTaskRepository.java               （新 Repository）
- infrastructure/mysql/query/QueryTaskStatus.java                   （新枚举 · 任务状态）
- resources/prompts/student-diagnosis-system.md               （新 · Prompt 模板）
- resources/prompts/student-diagnosis-user.md                 （新 · Prompt 模板）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- application/graph/extraction/                              （抽取模块，QA 不触发抽取）
- application/graph/fusion/                                  （融合模块，QA 只读）
- application/document/parser/                               （解析器模块，QA 不解析文件）
- infrastructure/storage/                                    （MinIO，QA 不操作文件）
- infrastructure/llm/client/Langchain4jLlmGateway.java       （LLM 实现，QA 只通过接口调用）
- pom.xml                                                    （禁动清单项，无新增依赖）
- docs/项目规范.md                                           （禁动清单项）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| LLM 调用 | `LlmGateway.chat()` | **沿用**，所有 LLM 调用必须通过此接口 |
| 图节点/边抽象 | `GraphNode` / `GraphEdge` 抽象体系 | **沿用**，子图数据结构直接复用 `GraphNode`/`GraphEdge` |
| Neo4j 查询 | `GraphNodeRepository` + `Neo4jClient` | **沿用**，新增只读查询方法（不修改现有方法签名） |
| MySQL 持久化 | Spring Data JPA + `@Transactional` | **沿用**，query_task 表 CRUD |
| 构造器注入 | `@RequiredArgsConstructor` | **沿用** |
| API 响应包装 | `ApiResponse<T>` | **沿用** |
| 异常处理 | `BusinessException` + `ErrorCode` | **沿用**，新增 A0019（意图识别失败）/ A0020（同名 Student 冲突）/ A0021（query_task 不存在） |
| yml 配置绑定 | `@ConfigurationProperties` | **沿用**，QueryProperties |
| 意图识别 | **没有** | **新建**（理由：项目首次需要用户意图分类能力） |
| 图剪枝 | **没有** | **新建**（理由：项目首次需要任务驱动子图裁剪） |
| Prompt 模板管理 | **没有** | **新建**（理由：项目首次需要多种分析风格的 Prompt 模板） |
| 异步任务状态追踪 | **没有** | **新建**（理由：项目首次需要异步任务生命周期管理） |
| 学生成绩数据查询 | `ExamRecordRepository` | **沿用**，新增 `findByStudentNoAndSubject()` 方法（MASTERS 降级场景用） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 依赖注入：         **沿用** 构造器注入（@RequiredArgsConstructor）
- 分层架构：         **沿用** L1(QueryController+VO) → L2(QueryService+BO) → L3(Repository+DO)
- Neo4j 数据访问：   **沿用** Neo4jClient + 手动 Cypher（MATCH/RETURN/WHERE），只读不写
- 策略模式：         **引入新模式** SubgraphPruningStrategy 放 `application/analysis/strategy/`（理由：图剪枝本质是图分析操作，与既有的 `api/analysis/` 包注释"PageRank、度中心性、指标度量查询"一致。`application/query/` 作为调用方依赖 analysis 模块）
- Prompt 模板：      **引入新模式** classpath 文件 + 字符串参数替换（理由：模板内容较长（角色设定 + 格式约束 + 示例），不适合硬编码在 Java 类中。不用模板引擎，Spring ResourceLoader 加载 + String.replace() 替换参数足够）
- 异步任务追踪：     **引入新模式** query_task 日志表 + 状态机（理由：项目首次需要异步任务状态持久化，模式参考 fusion_log 但不设回滚/快照字段，更轻量）
- 事务管理：         **沿用** @Transactional 仅放 L2，Neo4j 与 MySQL 事务独立（QA 的 Neo4j 只读，无事务冲突）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | **剪枝策略接口**：定义 `SubgraphPruningStrategy` 接口，契约 `PrunedSubgraph prune(PruningRequest)`。`PruningRequest` 含 `intent`（QueryIntent）、`entityId`（如 studentNo）、`subject`、`params`（Map<String, Object> 扩展参数）。v1 首发 `StudentDiagnosisStrategy` | ① 无接口，每种意图独立 Service 方法 ② 接口 + 策略工厂 + Spring Bean 自动发现 | ① 新增意图需改 QueryService 核心逻辑，违反开闭原则；② 过度设计，v1 仅一种意图。选择接口 + 显式注册：`QueryService` 内通过 switch(intent) → 调用对应策略，新增意图加 case 即可，简单直接，策略本身仍可独立单测 | switch-case 对 4~5 种意图尚可维护，若后续意图 > 10 种需重构为策略工厂 + Map<QueryIntent, Strategy> 自动注入。v1 接受此债务 |
| D2 | **StudentDiagnosisStrategy Cypher 剪枝逻辑**：分三步查询：① `MATCH (s:Student {studentNo})` 确认学生存在；② `MATCH (s)-[m:MASTERS]->(kp:KnowledgePoint {subject}) WHERE m.weight < 0.6` 找到薄弱点；③ `MATCH (weakKp)-[:PREREQUISITE_OF*1..2]->(preKp)` 展开前置依赖链（双向：入向找前置、出向找后继）；④ `MATCH (s)-[m2:MASTERS]->(preKp)` 补全前置 KP 的掌握度 | ① 单一巨型 Cypher（4 层 MATCH 嵌套）② 逐跳应用层遍历（Cypher → Java 循环 → Cypher） | ① 巨型 Cypher 难调试且 OPTIONAL MATCH 笛卡尔积可能产生重复行；② N+1 查询，每跳一次 DB round-trip。选择分步查询：3~4 次 Cypher 调用，结果在 Java 中合并去重，每步查询简单可调试，总 round-trip ≤ 4 次，学生诊断场景延迟可接受 | 若前置依赖链很深（> 3 跳），步数增加。v1 固定 ≤ 2 跳，通过 `QueryProperties.maxPrerequisiteHops` 可配置 |
| D3 | **意图识别策略**：v1 使用规则匹配（关键词 + 实体提取），不调 LLM。关键词映射表：`薄弱/加强/掌握/诊断/分析学生` → `STUDENT_DIAGNOSIS`。实体提取：用正则抽取 `studentName`（"分析学生XXX"中的 XXX）+ `subject`（数学/物理/英语 等）。未匹配到任何意图时返回 `A0019` 错误提示 | ① LLM 分类（输入 question → LLM 返回 intent 枚举）② embedding 余弦相似度分类 | ① 每次问答 = 2 次 LLM 调用（意图分类 + 分析），成本翻倍且增加延迟；② v1 只有 1 种意图，无需训练分类器。选择规则匹配：零延迟、零 LLM 成本、对 v1 唯一意图完全够用 | 规则表硬编码在 Java 中，新增意图需改代码。v2 扩展为 LLM fallback（规则未命中时调 LLM 判定，成本可控）或 embedding 匹配 |
| D4 | **子图序列化格式**：将 `PrunedSubgraph` 序列化为结构化文本，格式如下：<br>`## 学生信息`<br>`- 姓名: {name}, 学号: {studentNo}, 班级: {className}`<br><br>`## 薄弱知识点（掌握度 < {threshold}）`<br>`- KP-1: 顶点坐标 (掌握度: 20%)`<br>`- KP-2: 对称轴 (掌握度: 35%)`<br><br>`## 前置依赖关系`<br>`- 对称轴 → 顶点坐标 (依赖强度: 0.85)`<br><br>`## 前置知识点掌握度`<br>`- KP-3: 配方法 (掌握度: 40%)` | ① JSON 格式直接注入 Prompt ② Mermaid/Graphviz 格式 ③ 实体-关系三元组文本 | ① JSON 对 LLM 可读性差（花括号和引号消耗 token 且干扰语义理解），且 LLM 需自行推断关系；② 图描述语言对 LLM 理解成本高，且生成逻辑复杂；③ 三元组太冗长。选择结构化 Markdown：人类可读 ≈ LLM 友好，token 利用率高，LLM 可直接"读懂"节点属性和关系含义 | Markdown 格式对复杂图结构（如多分支依赖网）的线性化表达能力有限。v1 的 ≤2 跳简单依赖链足够，v2 复杂子图可能需要 JSON+Markdown 双格式（JSON 给前端渲染，Markdown 给 LLM） |
| D5 | **Prompt 模板引擎**：classpath `prompts/*.md` 文件，含 `{{variable}}` 占位符。`PromptTemplateService` 通过 `ResourceLoader` 加载模板 → `String.replace()` 替换变量 → 返回组装好的 system/user prompt。变量包括：`{{studentName}}`、`{{studentNo}}`、`{{className}}`、`{{subject}}`、`{{subgraphText}}`（D4 序列化结果）、`{{userQuestion}}`、`{{weakThreshold}}`、`{{maxHops}}`。双文件模式：`student-diagnosis-system.md`（system prompt）+ `student-diagnosis-user.md`（user prompt 模板） | ① 模板硬编码在 Java 字符串中 ② 使用 Thymeleaf/FreeMarker 模板引擎 ③ 单文件模式（system + user 混在一起） | ① 长模板（> 2000 字符）硬编码难以维护和修改；② 模板引擎引入额外依赖，且 prompt 模板不需要条件/循环等高级功能；③ system prompt 和 user prompt 是不同 LLM API 参数，分开管理更清晰。选择 classpath .md 文件 + 简单占位符：零依赖，可直接在 IDE 中编辑预览，`{{variable}}` 是 LLM 训练数据中不常见的模式，不会与 prompt 内容冲突 | 无类型安全——占位符拼写错误要到运行时才能发现。通过 `PromptTemplateServiceTest` 校验所有模板中引用的变量是否在变量 Map 中存在 |
| D6 | **Token 预算控制**：序列化子图后估算 token 数（`estimatedTokens = subgraphText.length() / 3`，保守近似）。若估算值 > `query.token-budget.max-input-tokens`（默认 8000），按优先级截断：① Student 节点（不可截断）→ ② 弱掌握 KP（按 weight 升序，越弱越优先）→ ③ PREREQUISITE_OF 边（按 strength 降序）→ ④ 其他。截断后在 prompt 末尾追加 `⚠️ 因长度限制，以下 {N} 个知识点已省略：{kpName1, kpName2, ...}` | ① LLM API 自身 truncation（依赖 API 的 max_tokens 参数）② 全量输入不加限制 | ① 依赖 API 的自动截断不可控（可能截断关键信息），且不同 LLM 实现行为不一致；② 超出上下文窗口会导致 API 报错或静默截断中间部分。选择应用层显式控制：优先级算法确保最薄弱的知识点优先呈现，省略声明让 LLM 和用户都知道"有信息被截断" | 1 token ≈ 3 字符是粗略估计（中文约 2 字符/token，英文约 4 字符/token），可能高估或低估。以 LLM API 返回的实际 `prompt_tokens` 为准，后续可通过 `query_task.token_usage_json` 字段校准 |
| D7 | **LLM 输出校验与重试**：LLM 返回后校验 3 项：① 非空；② 以 Markdown 标题（`#`）开头（无前导语）；③ 至少含 1 个列表标记（`- ` 或 `1. `）。任意一项不通过 → 重试（≤2 次），每次重试在 system prompt 中追加一条格式约束（如"上次输出包含前导语'根据提供的数据……'，请直接以标题开头，不要有任何前导语"）。重试仍失败 → 返回原始 LLM 输出（降级，不阻塞用户） | ① 不校验，LLM 输出直接返回 ② 校验失败直接返回错误（不重试） | ① 输出质量不稳定，可能含前导语或非 Markdown 格式，违反 AC-4；② 用户体验差（一次格式问题就报错）。选择校验 + 重试 + 降级：2 次重试覆盖大部分格式波动，极端情况降级返回原始文本（比报错好） | 重试增加 LLM 调用次数（最多 +2 次），增加延迟和成本。`query.max-retries` 可配置为 0 完全禁用重试 |
| D8 | **同步/异步双模式**：`POST /api/v1/query/ask` 同步执行完整链路（意图识别 + 剪枝 + LLM 调用），设置 30s 超时。超时后 HTTP 层面返回 202 + taskId，后台继续执行并写入 query_task 表。`POST /api/v1/query/ask-async` 立即返回 202 + taskId，后台 `@Async` 执行，用户通过 `GET /api/v1/query/result/{taskId}` 轮询 | ① 仅同步模式（30s 超时直接报错）② 全部异步（无同步端点） | ① 用户体验差，超时后无恢复路径；② 简单问题也需要轮询，增加客户端复杂度。选择双模式：简单问题同步返回（用户体验好），复杂问题异步轮询（可靠性好），同步超时自动降级为异步（柔性处理） | 双模式增加 API 端点和代码路径。`@Async` 需启用 Spring 异步支持（`@EnableAsync`），线程池需配置（`query.async.core-pool-size` 默认 2, max 5, queue 10） |
| D9 | **query_task 表结构**：MySQL 日志类表，字段如下：<br>- `id` BIGINT AUTO_INCREMENT PK<br>- `task_id` VARCHAR(36) UNIQUE NOT NULL（UUID，API 返回的标识）<br>- `question` TEXT NOT NULL<br>- `student_name` VARCHAR(128)<br>- `student_no` VARCHAR(64)<br>- `subject` VARCHAR(32)<br>- `intent` VARCHAR(32)（`STUDENT_DIAGNOSIS`）<br>- `status` VARCHAR(20) NOT NULL（PENDING/PROCESSING/COMPLETED/FAILED）<br>- `answer` MEDIUMTEXT（Markdown，COMPLETED 时写入）<br>- `subgraph_json` MEDIUMTEXT（剪枝子图 JSON，COMPLETED 时写入）<br>- `token_usage_json` JSON（prunedNodes/prunedEdges/estimatedTokens/promptTokens/completionTokens）<br>- `error_message` TEXT（FAILED 时写入）<br>- `retry_count` INT DEFAULT 0<br>- `elapsed_ms` BIGINT（总耗时）<br>- `create_time` DATETIME NOT NULL<br>- `update_time` DATETIME NOT NULL<br>——不设 is_deleted 字段（日志类表，永久保留，见 CONTEXT.md"日志表除外"规则） | ① 不持久化，仅内存队列 ② 分两张表（query_task + query_subgraph） | ① 无历史查询和审计能力；② 子图数据与任务总是 1:1 查询，拆分增加 JOIN。选择单表 + JSON 列：与 fusion_log 模式一致，MEDIUMTEXT 容纳百级节点子图 JSON 足够（< 1MB） | `subgraph_json` 和 `answer` 均为 MEDIUMTEXT，大文本查询可能影响 MySQL 性能。正常查询（`SELECT * WHERE task_id = ?`）只查单行，无全表扫描。历史列表查询（v2）需 `SELECT id, task_id, question, status, create_time` 避开大字段 |
| D10 | **GraphNodeRepository 新增只读方法**：需要以下新查询方法（均通过 Neo4jClient，不影响现有方法）：<br>① `findStudentByName(String name)` — MATCH (s:Student) WHERE s.name CONTAINS $name RETURN s<br>② `findStudentByNo(String studentNo)` — MATCH (s:Student {studentNo}) RETURN s<br>③ `findMastersByStudentAndSubject(String studentNodeId, String subject)` — MATCH (s:Student {id})-[m:MASTERS]->(kp:KnowledgePoint {subject}) RETURN s,m,kp<br>④ `findPrerequisitesUpstream(List<String> kpIds, int maxHops)` — MATCH (kp)-[:PREREQUISITE_OF*1..{maxHops}]->(pre) WHERE kp.id IN $ids RETURN kp,pre<br>⑤ `findMastersByStudentAndKpIds(String studentNodeId, List<String> kpIds)` — MATCH (s)-[m:MASTERS]->(kp) WHERE s.id=$sid AND kp.id IN $ids RETURN m,kp | ① 在 QueryService 中直接使用 Neo4jClient ② 在 GraphNodeRepository 中合并为一个通用 `query(String cypher, Map params)` 方法 | ① 违反分层架构（L2 直接调 Neo4jClient）；② 过于通用，失去方法名语义和类型安全。选择在 GraphNodeRepository 中新增专有方法：保持 L2 只依赖 Repository 抽象，方法名即文档，单测可控 | GraphNodeRepository 职责从"通用图仓库"逐渐变为"所有图查询的聚集点"。v1 可接受（方法总量 < 20），若后续查询类型持续膨胀（> 30 方法），需拆分为 GraphQueryRepository（只读）+ GraphWriteRepository（读写） |
| D11 | **MASTERS 降级处理**：`StudentDiagnosisStrategy` 先查 MASTERS 边。若返回空（融合从未执行），降级为直接查询 TESTED 路径：`MATCH (s:Student)-[:ATTENDED]->(e:Exam)-[:TESTED]->(kp:KnowledgePoint {subject})` → 获取所有 `(s, kp)` 对 → 查 MySQL `exam_record.score_details` 计算原始得分率（简单算术平均，无时间衰减）→ 以得分率 < 0.6 为薄弱阈值。在剪枝结果的 `PruningMeta` 中标记 `"mastersAvailable": false`，组装 Prompt 时追加提示：`⚠️ 融合数据不可用，以下掌握度为原始考试得分率（未做时间衰减加权），可能与实际掌握水平存在偏差` | ① 融合未执行直接报错（A0022 "请先执行融合"）② 降级但不告知用户 | ① 对用户不友好（"我要分析薄弱点"→"你先去执行融合"，阻断正常使用）；② 用户可能过度信任不准确的数据。选降级 + 明确提示：让用户可先用粗糙数据获得初步诊断，同时知道数据局限性，推动执行融合 | 降级路径增加代码复杂度（两套查询逻辑），且原始得分率未做时间衰减可能导致"半年前考得好"掩盖"最近退步"。提示文案可在 Prompt 模板中调整 |
| D12 | **同名 Student 消歧**：`StudentNode` 无全局唯一索引（仅 `studentNo` 保证唯一，`name` 可能重复）。当 `studentName` 模糊匹配（`CONTAINS`）返回多个 Student 时，返回 HTTP 409 + 错误码 `A0020`，响应体含 `candidates[]` 列表（每个含 `studentNo`、`name`、`className`、`grade`），由客户端让用户选择后再用 `studentNo` 精确查询 | ① 自动选第一个匹配 ② 返回所有匹配学生的合并分析 | ① 可能选错人（如两个"张三"在不同班级）；② 合并分析语义混乱（"两个张三的薄弱点"不是用户意图）。选择 409 消歧：一次额外请求确认身份，保证分析准确性 | 增加前后端交互轮次。v1 前端不在此 change 范围内，409 响应由客户端自行处理 |

---

## 2. 数据流 / 架构图

### 2.1 同步问答主流程（`POST /api/v1/query/ask`）

```
POST /api/v1/query/ask { question, studentName, studentNo?, subject }
        │
        ▼
┌─────────────────────────────────────────────────┐
│ QueryController (L1)                                │
│  - 参数校验（subject 必传，studentName/studentNo │
│    至少传一个）                                    │
│  - 调用 QueryService.ask(question, studentName,     │
│    studentNo, subject)                            │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│ QueryServiceImpl (L2)                               │
│                                                  │
│  1. 意图识别 (recognizeIntent)                    │
│     ─── 规则匹配 → QueryIntent.STUDENT_DIAGNOSIS    │
│                                                  │
│  2. 实体解析 (resolveStudent)                     │
│     ├─ studentNo 不为空 → GraphNodeRepository    │
│     │   .findStudentByNo(studentNo)              │
│     └─ studentName 不为空 → GraphNodeRepository  │
│         .findStudentByName(name)                │
│         └─ 多结果 → throw A0020(409)            │
│                                                  │
│  3. 图剪枝 (prune)                                │
│     subgraphPruningStrategy.prune(               │
│       PruningRequest(studentNodeId, subject)     │
│     )                                            │
│     ─── StudentDiagnosisStrategy：                │
│         ① findMastersByStudentAndSubject()       │
│         ② filter weak (weight < 0.6)            │
│         ③ findPrerequisitesUpstream(≤2 hops)     │
│         ④ findMastersByStudentAndKpIds(preKps)   │
│         → PrunedSubgraph                         │
│                                                  │
│  4. 子图序列化 (serializeSubgraph)                │
│     ─── 结构化 Markdown 文本                       │
│     ─── Token 预算截断（如需要）                    │
│                                                  │
│  5. Prompt 组装 (promptTemplateService)           │
│     ├─ 加载 student-diagnosis-system.md          │
│     ├─ 加载 student-diagnosis-user.md            │
│     ├─ 替换 {{variables}}                         │
│     └─ 返回 systemPrompt + userMessage            │
│                                                  │
│  6. LLM 调用 (llmGateway.chat)                    │
│     ─── 重试逻辑（≤2 次，格式校验）                 │
│                                                  │
│  7. 结果组装                                      │
│     ├─ 写入 query_task 表（同步模式仅 COMPLETED）     │
│     └─ 返回 QueryAskResponse                        │
│         { taskId, question, intent, answer,       │
│           tokenUsage }                           │
└─────────────────────────────────────────────────┘
```

### 2.2 异步问答流程（`POST /api/v1/query/ask-async`）

```
POST /api/v1/query/ask-async {...}
        │
        ▼
QueryController → QueryService.askAsync(...)
        │
        ├─ 1. 生成 taskId (UUID)
        ├─ 2. INSERT query_task (status=PENDING)
        ├─ 3. 返回 202 { taskId, status: "PENDING" }
        │
        └─ 4. @Async 后台线程
             ├─ 更新 status=PROCESSING
             ├─ 执行步骤 1~6（同同步模式）
             ├─ 成功：UPDATE status=COMPLETED, answer, subgraph_json, token_usage_json, elapsed_ms
             └─ 失败：UPDATE status=FAILED, error_message, retry_count

GET /api/v1/query/result/{taskId}
        │
        ▼
QueryController → QueryService.getResult(taskId)
        │
        ├─ SELECT * FROM query_task WHERE task_id = ?
        ├─ 不存在 → A0021 (404)
        ├─ status=PENDING/PROCESSING → 返回 { status, createdAt }
        ├─ status=COMPLETED → 返回 { status, answer, tokenUsage }
        └─ status=FAILED → 返回 { status, errorMessage }
```

### 2.3 子图查询流程（`GET /api/v1/analysis/subgraph/{taskId}`）

```
GET /api/v1/analysis/subgraph/{taskId}
        │
        ▼
AnalysisController → QueryService.getSubgraph(taskId)
        │
        ├─ SELECT subgraph_json, status FROM query_task WHERE task_id = ?
        ├─ 不存在 → A0021 (404)
        ├─ status != COMPLETED → 返回 { taskId, status: "NOT_READY" }
        └─ status = COMPLETED → 反序列化 subgraph_json
             → SubgraphResponse { taskId, nodes[], edges[], pruningMeta }
```

### 2.4 模块依赖图

```
┌──────────────────────────────────────────────────┐
│ L1 api/query/controller/QueryController             │
│    POST /ask   POST /ask-async                    │
│    GET /result/{taskId}                           │
└──────────────────────┬───────────────────────────┘
                       │
┌──────────────────────────────────────────────────┐
│ L1 api/analysis/controller/AnalysisController    │
│    GET /api/v1/analysis/subgraph/{taskId}       │
└──────────────────────┬───────────────────────────┘
                       │
                       │
┌──────────────────────▼───────────────────────────┐
│ L2 application/query/                                │
│  ┌─────────────────────────────────────────────┐ │
│  │ QueryService (接口)                             │ │
│  │  + ask(Request) → QueryAskResponse              │ │
│  │  + askAsync(Request) → QueryAsyncResponse       │ │
│  │  + getResult(taskId) → QueryResultResponse      │ │
│  │  + getSubgraph(taskId) → PrunedSubgraph         │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │ prompt/PromptTemplateService                 │ │
│  │  + loadTemplate(name) → String               │ │
│  │  + assemble(template, vars) → String         │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │ config/QueryProperties (@ConfigurationProperties│ │
│  │  "query")                                       │ │
│  └─────────────────────────────────────────────┘ │
└──┬──────────────────┬───────────────┬────────────┘
   │                  │               │
   │          ┌───────┘               │
   ▼          ▼                       ▼
┌──────────────┐ ┌────────────┐ ┌───────────────────┐
│ L2 analysis  │ │ L3 MySQL   │ │ L2 LLM Gateway    │
│ strategy/    │ │ query_task    │ │ LlmGateway.chat() │
│  Subgraph    │ │  + QueryTaskDO│ │ (只读依赖)         │
│  Pruning     │ │  + Repo    │ │                    │
│  Strategy    │ │ exam_record│ │                    │
│  ◄── Student │ │ (MASTERS降 │ │                    │
│  Diagnosis   │ │  级)       │ │                    │
└──────┬───────┘ └────────────┘ └───────────────────┘
       │
┌──────▼──────────┐
│ L3 Neo4j        │
│ GraphNode       │
│ Repository      │
│ (新增只读查询)  │
└─────────────────┘
```

---

## 3. 关键状态机

### 3.1 query_task 任务状态

```
        创建任务
           │
           ▼
     ┌──────────┐
     │ PENDING  │  ← 异步任务写入后等待 @Async 线程拾取
     └────┬─────┘
          │ @Async 线程开始执行
          ▼
     ┌──────────┐
     │PROCESSING│  ← 意图识别 / 剪枝 / LLM 调用进行中
     └───┬──┬───┘
         │  │
    ┌────┘  └────┐
    ▼            ▼
┌──────────┐ ┌────────┐
│COMPLETED │ │ FAILED │  ← 3 次重试均失败 / Student 不存在
└──────────┘ └────────┘

同步模式（/ask）：PENDING → SKIP → COMPLETED/FAILED（同步执行，状态在内存中流转，完成后一次性写入 COMPLETED）
超时降级：/ask 30s 无响应 → 客户端收到 202 → 后台状态流转同异步模式
```

### 3.2 同步请求超时降级

```
POST /api/v1/query/ask
        │
        ├─ 正常路径（< 30s）
        │    └─ 返回 HTTP 200 + QueryAskResponse { answer, status:"COMPLETED" }
        │
        └─ 超时路径（≥ 30s）
             ├─ Controller 层 Future.get(30, TimeUnit.SECONDS) 超时
             ├─ 返回 HTTP 202 + QueryAsyncResponse { taskId, status:"PROCESSING" }
             └─ 后台 @Async 继续执行 → 完成后写入 query_task
```

---

## 4. ADR 索引

凡 D1~D12 中可逆性低的，单独写 ADR：

- `@.specs/adr/010-subgraph-pruning-strategy.md` — SubgraphPruningStrategy 接口 + StudentDiagnosisStrategy 分步 Cypher 剪枝算法（D1 + D2）
- `@.specs/adr/011-query-prompt-template-engine.md` — Prompt 模板文件化管理 + Markdown 输出约束 + 格式校验重试机制（D4 + D5 + D7）
- `@.specs/adr/012-query-async-dual-mode.md` — 同步/异步双模式 API 设计 + query_task 状态机 + MASTERS 降级策略（D8 + D9 + D11）

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **LLM 输出格式不稳定**：即使 system prompt 明确要求"以 Markdown 标题开头"，LLM 仍可能输出前导语（"根据提供的数据，分析如下……"）或不使用列表格式 | AC-4（Markdown 格式）可能不通过，重试增加延迟和 LLM 成本 | 高 | ① D7 三重校验 + 2 次重试（每次加强格式约束）；② Prompt 中提供正确输出的完整示例（few-shot）；③ 重试仍失败则降级返回原始文本（不阻塞用户）；④ 同一 LLM 模型对格式指令的遵循度可通过 `query_task` 日志统计跟踪 |
| R2 | **剪枝策略"剪多了"**：weight < 0.6 阈值 + ≤ 2 跳 PREREQUISITE_OF 可能遗漏真正根因（如根因在 3 跳外的 KP，或一个 weight=0.62 的边缘薄弱点恰好是关键前置） | 分析报告不完整，漏掉深层根因 | 中 | ① `weakThreshold` 和 `maxPrerequisiteHops` 通过 `application-dev.yml` 可配置（运维调参）；② 子图端点（`GET /subgraph/{taskId}`）供调试剪枝效果；③ Prompt 末尾标注剪枝参数（"阈值: 0.6, 最大跳数: 2"），用户若觉得分析不深入可要求调整参数重新分析；④ v2 加"松弛剪枝"开关（一键 +1 跳、-0.1 阈值） |
| R3 | **LLM 幻觉——编造不存在的知识点或关系**：LLM 可能引用子图中不存在的 KP（如"建议学习一元一次方程"但子图中无此 KP），或编造 PREREQUISITE_OF 关系 | 分析报告可信度降低，用户可能依据虚假建议行动 | 中 | ① System prompt 明确约束"仅基于提供的子图数据回答，不要编造数据中不存在的知识点或关系"；② System prompt 列出子图中所有 KP 名称清单让 LLM 知晓边界；③ v1 不做事后事实校验（代价过高），v2 可引入"引用校验"——提取 LLM 回答中的 KP 名称与子图对比标记"未验证引用"；④ 风险在 prompt 模板中使用"可能不准确"的免责声明 |
| R4 | **大班级/多考试场景下子图过大**：一个学生 3 年积累 50+ 次考试 × 100+ 个知识点，剪枝后仍可能有 30+ 弱掌握 KP × 各 2~5 个前置依赖 = 100+ 节点，超出 token 预算 | Token 截断可能遗漏关键信息，分析不完整 | 中 | ① D6 优先级截断 + 省略声明确保最关键信息优先；② weight < 0.4 的超薄弱点比 weight=0.58 的边缘点更重要，按 weight 升序排列自然实现"最重要信息最先呈现"；③ `estimatedTokens` 记录在 query_task 中，可事后分析分布；④ v2 可按知识模块聚合（如"函数模块薄弱点汇总"而非逐个 KP 列出） |
| R5 | **并发问答任务资源竞争**：多个用户同时提问，每个任务消耗 1 个 @Async 线程 + 1 个 Neo4j 连接 + 1 次 LLM 调用（可能长达 60s） | 线程池耗尽导致异步任务排队/拒绝，LLM API 限流 | 低（v1 单用户/低并发） | ① @Async 线程池配置保守（core=2, max=5, queue=10），超限时 CallerRunsPolicy 降级在 Tomcat 线程执行；② LLM 网关的 Langchain4jLlmGateway 已有 5 分钟超时，v1 无额外限流；③ v2 引入请求队列 + 优先级（VIP 用户优先） + LLM API 并发 license 控制 |
| R6 | **MASTERS 降级数据质量**：融合未执行时，降级为原始考试得分率（简单算术平均），与时间衰减加权平均的结果可能有显著差异（如半年前考满分拉高了当前掌握度估值） | 诊断报告可能高估学生真实掌握水平（未反映遗忘效应） | 高（融合可能未执行） | Prompt 中明确标注"融合数据不可用，以下为原始考试得分率（未做时间衰减加权），可能与实际掌握水平存在偏差，建议执行融合后重新诊断"。这个标注在 Prompt 模板中以⚠️警告形式呈现，LLM 也会在报告开头提醒用户 |
| R7 | **实现风险——Cypher 查询性能**：`PREREQUISITE_OF*1..2` 变长路径匹配在 PREREQUISITE_OF 边密集的场景（一个 KP 有 10+ 个前置依赖）下可能产生组合爆炸 | Cypher 查询超时，问答请求失败 | 低（教育场景前置依赖通常 ≤ 5 个/KP） | ① `maxPrerequisiteHops` 限制在 ≤ 3，为 Cypher 查询加上 `LIMIT 50` 保护；② 在 Neo4jIndexConfig 中为 PREREQUISITE_OF 边类型添加 `lookup` 索引；③ v2 可在应用层做 BFS 遍历替代 Cypher 变长路径匹配，更精确控制访问节点数 |

---

## 6. 不在范围

- ❌ `KP_ANALYSIS`、`CLASS_OVERVIEW`、`PREREQUISITE_CHAIN`、`GENERAL` 意图（v2）
- ❌ LLM 分类意图（v2，替代规则匹配）
- ❌ Embedding 意图分类（v2）
- ❌ 多轮对话上下文 / 会话管理（v2）
- ❌ SSE 流式响应（v2）
- ❌ Redis 缓存问答结果（v2）
- ❌ PageRank / 中心性图算法剪枝（v2，接口预留）
- ❌ 跨学科归因分析（out）
- ❌ LLM Agent 自主选择遍历路径（out）
- ❌ 前端对话 UI / 图可视化渲染（前端 change）
- ❌ 融合触发 API 的调用（QA 不负责触发融合，只读取融合结果）
- ❌ 回答质量评分 / 用户反馈收集
- ❌ 分析结果的历史对比（"与上次诊断的差异"）
- ❌ `query_task` 表的清理策略（日志类表永久保留，后续可加 TTL 定时清理）

---

## 9. 架构沉淀建议（本 change 完成后供 `A-evolve` 同步用）

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/analysis/strategy/SubgraphPruningStrategy.java` | 图剪枝策略接口（`PrunedSubgraph prune(PruningRequest)`） | 任何需要从宽图谱中按任务类型裁剪子图的场景 | 后续新增意图只需实现此接口 + 在 QueryService 中加 1 行 case |
| `application/query/prompt/PromptTemplateService.java` | classpath Prompt 模板加载 + 变量替换 | 任何需要 LLM Prompt 模板管理的场景 | 其他模块（如 extraction 的 Prompt 构建）可复用此服务替代硬编码字符串 |
| `application/query/config/QueryProperties.java` | QA 模块配置绑定（`@ConfigurationProperties("query")`） | 所有 QA 相关可配置参数集中管理 | 按 Spring Boot 惯例放 config 包 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| 意图识别策略 | v1 规则匹配（关键词 + 正则），仅 `STUDENT_DIAGNOSIS` | 所有问答入口 | 低 — v2 切换为 LLM 分类只需替换 `recognizeIntent()` 方法实现 |
| LLM 输出格式 | 中文 Markdown，无前导语，含标题 + 列表 | 所有通过 `LlmGateway` 调用 LLM 生成面向用户文本的场景 | 中 — Prompt 模板依赖此决策，改格式需重写全部模板 |
| 异步任务持久化 | MySQL `query_task` 日志表，非 Redis/内存队列 | 所有需要异步任务状态追踪的模块 | 低 — 表结构可扩展（加字段），但已有数据需兼容 |
| 子图序列化格式 | 自定义结构化 Markdown，非 JSON/Mermaid/三元组 | 所有需要将图数据注入 LLM Prompt 的场景 | 中 — LLM 已"习惯"此格式，改格式需重写 Prompt 模板 |

### 9.3 新增 / 修改的跨模块契约

```
- 新增 POST /api/v1/query/ask — 同步问答，请求 QueryAskRequest，响应 QueryAskResponse
- 新增 POST /api/v1/query/ask-async — 异步问答，请求 QueryAskRequest，响应 QueryAsyncResponse(202)
- 新增 GET /api/v1/query/result/{taskId} — 查询异步结果，响应 QueryResultResponse
- 新增 GET /api/v1/analysis/subgraph/{taskId} — 查询剪枝子图（analysis 模块），响应 SubgraphResponse
- 新增 ErrorCode A0019(意图识别失败) / A0020(同名 Student 冲突) / A0021(query_task 不存在)
- 新增 MySQL 表 query_task（task_id, question, student_name, student_no, subject, intent, status, answer, subgraph_json, token_usage_json, error_message, retry_count, elapsed_ms, create_time, update_time）
- GraphNodeRepository 新增 5 个只读查询方法（findStudentByName/findStudentByNo/findMastersByStudentAndSubject/findPrerequisitesUpstream/findMastersByStudentAndKpIds）
- ExamRecordRepository 新增 findByStudentNoAndSubject() 方法
- classpath prompts/ 目录新增 Prompt 模板文件（student-diagnosis-system.md + student-diagnosis-user.md），文件名 = {intent小写}-{system/user}.md
- application-dev.yml 新增 query 配置块（token-budget, max-retries, async pool, pruning 默认参数）
```

### 9.4 新增 / 升级的依赖

本 change 无新增 pom.xml 依赖。

### 9.5 禁动清单变化

```
- 新增禁动：application/analysis/strategy/SubgraphPruningStrategy 接口签名（PrunedSubgraph prune(PruningRequest)）变更需走独立 CHANGE，影响所有策略实现类
- 新增禁动：Prompt 模板文件中的 {{variable}} 占位符名称变更需同步更新 PromptTemplateService 的变量 Map 构建逻辑和模板文件本身
- 新增禁动：query_task 表的 subgraph_json 的 JSON 结构变更需兼容（PrunedSubgraph 序列化格式影响子图端点消费者）
- 新增禁动：LlmGateway.chat() 接口不可在 QA 模块中绕过——所有 LLM 调用必须通过此接口
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。