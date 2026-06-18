# REQUIREMENT: 智能问答 — 图剪枝驱动的 LLM 分析与诊断

- **Change ID**: `intelligent-qa`
- **关联**: `@.specs/intelligent-qa/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师/家长，我想输入学生的姓名或学号并指定学科，以便获得该学生在指定学科上的薄弱知识点诊断报告（含根因分析和学习建议），报告以 Markdown 格式呈现。
- **US-2**：作为开发者，我想通过独立的子图端点获取剪枝后的结构数据，以便在前端做图可视化渲染或调试剪枝策略效果。
- **US-3**：作为系统（自动化流程），我想在问答涉及复杂子图遍历导致 LLM 调用超时时自动降级为异步模式，以便用户可以通过 taskId 轮询获取最终结果，而非长时间阻塞等待。
- **US-4**：作为模块维护者，我想剪枝策略通过接口定义、Prompt 模板通过文件管理，以便后续新增诊断意图（如班级概览、知识点分析）或调整分析风格时无需修改核心调用链路代码。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 学生薄弱点诊断 — 同步问答端到端

- **Given** Neo4j 宽图谱中存在：
  - Student `S1`（studentNo="S2024001", name="张三", className="初三(1)班", grade="9"）
  - KnowledgePoint `KP-对称轴`（name="对称轴", subject="数学"）、`KP-顶点坐标`（name="二次函数顶点坐标", subject="数学"）、`KP-判别式`（name="判别式", subject="数学"）、`KP-配方法`（name="配方法", subject="数学"）
  - PREREQUISITE_OF 边：`KP-配方法 → KP-顶点坐标`（strength=0.9）、`KP-对称轴 → KP-顶点坐标`（strength=0.85）
  - MASTERS 边：`S1 → KP-对称轴`（weight=0.35）、`S1 → KP-顶点坐标`（weight=0.20）、`S1 → KP-判别式`（weight=0.75）、`S1 → KP-配方法`（weight=0.40）
  - MySQL `exam_record` 中有对应的历史考试记录
- **When** 发送 `POST /api/v1/qa/ask`：
  ```json
  {
    "question": "分析学生张三的数学薄弱点",
    "studentName": "张三",
    "subject": "数学"
  }
  ```
- **Then** HTTP 200，响应体含：
  - `taskId`（同步模式仍生成 taskId 用于后续子图查询）
  - `question`（回显）
  - `intent`: `"STUDENT_DIAGNOSIS"`
  - `answer`（Markdown 格式字符串）：包含以下结构的分析报告：
    - **学生基本信息**（姓名、班级、学科）
    - **薄弱知识点列表**：按掌握度从低到高排列（weight < 0.6 的知识点），含掌握度百分比
    - **根因分析**：利用 PREREQUISITE_OF 链追溯（如"顶点坐标得分低可能与前置知识点 对称轴(35%) 和 配方法(40%) 未掌握有关"）
    - **学习建议**：基于依赖链的优先级建议（如"建议优先复习配方法，再巩固对称轴，最后突破顶点坐标"）
  - `tokenUsage`：`{ "prunedNodes": <N>, "prunedEdges": <M>, "estimatedTokens": <T> }`
  - LLM 结论中引用的知识点和前置依赖关系必须与子图数据一致，**禁止编造不存在的知识点或关系**
- **验证方式**: 准备上述 Neo4j + MySQL 数据 → `curl -X POST http://localhost:8080/api/v1/qa/ask -H 'Content-Type: application/json' -d '{"question":"分析学生张三的数学薄弱点","studentName":"张三","subject":"数学"}'` → 断言 HTTP 200，`answer` 非空且含 Markdown 标题（`#` 或 `##`）、断言 `answer` 中出现"对称轴"和"顶点坐标"（薄弱点）、断言 `tokenUsage.prunedNodes >= 4`（至少含 Student + 3 个薄弱 KP）

### AC-2 · 异步问答 — 提交任务 + 轮询结果

- **Given** 同 AC-1 的 Neo4j 数据
- **When** 发送 `POST /api/v1/qa/ask-async`：
  ```json
  {
    "question": "分析学生张三的数学薄弱点",
    "studentName": "张三",
    "subject": "数学"
  }
  ```
- **Then** HTTP 202，响应体含：
  - `taskId`（UUID）
  - `status`: `"PENDING"`
  - `createdAt`（ISO 8601）
- **When** 随后轮询 `GET /api/v1/qa/result/{taskId}`
- **Then**：
  - 若任务仍在处理中：HTTP 200，`status`: `"PROCESSING"`
  - 若任务已完成：HTTP 200，`status`: `"COMPLETED"`，响应体含 `answer`（Markdown）、`tokenUsage`
  - 若任务失败：HTTP 200，`status`: `"FAILED"`，响应体含 `errorMessage`
- **验证方式**: 发送 async 请求 → 获取 `taskId` → 轮询 `GET /api/v1/qa/result/{taskId}`（最多轮询 30 次，间隔 1s）→ 断言最终 `status=COMPLETED` 且 `answer` 非空 → 查询 MySQL `qa_task` 表断言记录存在且 `status='COMPLETED'`

### AC-3 · 独立子图端点 — 返回剪枝后的结构化图数据

- **Given** AC-1 的同步问答已完成（taskId="T-001"）
- **When** 发送 `GET /api/v1/qa/subgraph/T-001`
- **Then** HTTP 200，响应体含：
  - `taskId`: `"T-001"`
  - `nodes`（数组）：每个节点含 `id`、`nodeType`（如 `Student`、`KnowledgePoint`）、`properties`（节点特有属性，如 Student 的 `name`/`studentNo`、KnowledgePoint 的 `name`/`subject`）
  - `edges`（数组）：每条边含 `sourceNodeId`、`targetNodeId`、`edgeType`（如 `MASTERS`、`PREREQUISITE_OF`）、`weight`（如有）
  - `pruningMeta`：`{ "strategy": "STUDENT_DIAGNOSIS", "maxHops": 2, "weightThreshold": 0.6 }`
- **验证方式**: `curl http://localhost:8080/api/v1/qa/subgraph/T-001` → 用 `jq` 断言 `.nodes | length >= 4`、`.edges | length >= 3`（至少 MASTERS × 3 + PREREQUISITE_OF）、`.pruningMeta.strategy == "STUDENT_DIAGNOSIS"`

### AC-4 · LLM 输出为合法 Markdown 格式

- **Given** 任意有效的学生诊断请求
- **When** LLM 返回分析结果
- **Then** `answer` 字段内容满足：
  - 至少包含 1 个 Markdown 标题（`#`、`##` 或 `###`）
  - 至少包含 1 个列表（有序 `1.` 或无序 `- `）
  - **不包含** LLM 的元对话文本（如"根据提供的子图数据……"、"以下是分析报告……"等非分析内容的前导语），即 LLM 应直接输出分析报告正文
  - 若 LLM 输出不符合上述格式，系统自动重试（最多 2 次），每次重试在 system prompt 中加强格式约束
- **验证方式**: 发送 QA 请求 → 提取 `answer` → 断言 `answer.startsWith("#")`（以标题开头，无前导语）→ 断言 `answer.contains("- ")` 或 `answer.matches(".*\\d\\.\\s.*")`（含列表）→ 断言 `!answer.contains("根据提供的子图数据")` 等禁用短语

### AC-5 · 图剪枝 — 薄弱点 + 前置依赖链的正确范围

- **Given** Neo4j 数据同 AC-1（S1 有 4 个 KP 的 MASTERS 边，weight 分别为 0.35/0.20/0.75/0.40），剪枝配置为弱掌握阈值 = 0.6、PREREQUISITE_OF 最大跳数 = 2
- **When** 系统执行 STUDENT_DIAGNOSIS 剪枝策略
- **Then** 剪枝出的子图：
  - **包含** S1 节点
  - **包含** 弱掌握 KP：对称轴(0.35)、顶点坐标(0.20)、配方法(0.40) — weight < 0.6
  - **排除** 判别式(0.75) — weight ≥ 0.6，已掌握，不纳入薄弱分析
  - **包含** 前置依赖边：`配方法 → 顶点坐标`、`对称轴 → 顶点坐标`（≤2 跳）
  - **包含** 前置 KP 节点：配方法、对称轴（作为顶点坐标的前置依赖被引入），即使它们本身已在弱掌握列表中
  - `pruningMeta.strategy` = `"STUDENT_DIAGNOSIS"`
- **验证方式**: 调用 `GET /api/v1/qa/subgraph/{taskId}` → 断言返回的节点列表中无判别式节点 → 断言有配方法、对称轴、顶点坐标节点 → 断言边列表含 `PREREQUISITE_OF` 类型的边

### AC-6 · 意图识别 — 正确识别 STUDENT_DIAGNOSIS

- **Given** 系统启动，v1 意图识别基于规则（关键词 + 实体提取）
- **When** 发送包含以下关键词组合的请求：
  - `"分析学生张三的数学薄弱点"` → 预期意图 `STUDENT_DIAGNOSIS`
  - `"李四数学哪些知识点需要加强"` → 预期意图 `STUDENT_DIAGNOSIS`
  - `"王五的数学掌握情况怎么样"` → 预期意图 `STUDENT_DIAGNOSIS`
  - `"帮我诊断一下赵六的数学学习问题"` → 预期意图 `STUDENT_DIAGNOSIS`
- **Then** 每个请求的响应中 `intent` 字段均为 `"STUDENT_DIAGNOSIS"`
- **验证方式**: 分别发送上述 4 个请求 → 断言每个响应的 `intent == "STUDENT_DIAGNOSIS"`

### AC-7 · Token 预算控制 — 子图过大时截断

- **Given** 配置 `qa.token-budget.max-input-tokens = 8000`（LLM 输入上限），剪枝出的子图在序列化后估算为 12000 tokens（模拟大班级或深依赖链场景）
- **When** 系统序列化子图为 LLM prompt
- **Then**：
  - 序列化结果 ≤ 8000 tokens（按 1 token ≈ 3 字符粗略估算）
  - 节点按优先级排序后截断（优先级：Student > 弱掌握 KP（按 weight 升序，越弱越优先）> PREREQUISITE_OF 目标 KP > 其他）
  - 被截断的部分在 prompt 末尾标注：`"⚠️ 以下 {N} 个节点因长度限制已省略：{节点名称列表}"`
  - LLM 正常返回分析结果（不因截断而报错）
- **验证方式**: 构造大子图场景（或调低 `max-input-tokens` 到 500 强制截断）→ 发送 QA 请求 → 断言 LLM prompt 字符串长度 ≤ 8000 × 3 字符 → 断言 prompt 末尾含 `"因长度限制已省略"` 文本 → 断言仍返回 HTTP 200 且 `answer` 非空

### AC-8 · LLM 调用失败 — 错误处理与重试

- **Given** LLM 网关 `LlmGateway.chat()` 调用失败（模拟网络超时或 API 返回 5xx）
- **When** 系统尝试调用 LLM 生成分析
- **Then**：
  - 自动重试最多 2 次（共 3 次尝试），每次间隔递增（1s / 2s）
  - 3 次均失败后：
    - 同步模式（`POST /api/v1/qa/ask`）：返回 HTTP 500，错误码 `C0001`（LLM 调用失败），`errorMessage` 含"LLM 分析调用失败，已重试 3 次"
    - 异步模式（`POST /api/v1/qa/ask-async`）：`qa_task` 状态更新为 `FAILED`，`error_message` 字段记录失败原因
  - 重试次数记录在日志中（INFO 级别）
- **验证方式**: 断开 LLM API 网络（或配置错误的 API key）→ 发送 QA 请求 → 断言 HTTP 500 + 错误码 C0001 → 断言日志中含 3 次重试记录

### AC-9 · MySQL 任务持久化 — qa_task 表记录完整生命周期

- **Given** 发送一个异步问答请求（AC-2）
- **When** 任务经历 `PENDING → PROCESSING → COMPLETED` 完整生命周期
- **Then** MySQL `qa_task` 表中存在对应记录，包含：
  - `id`（自增主键）、`task_id`（UUID，与 API 返回一致）
  - `question`、`student_name`、`subject`（请求参数原样存储）
  - `status`（PENDING → PROCESSING → COMPLETED 依次更新）
  - `answer`（Markdown 文本，COMPLETED 时写入）
  - `subgraph_json`（MEDIUMTEXT，剪枝子图的 JSON 序列化，COMPLETED 时写入）
  - `intent`（`STUDENT_DIAGNOSIS`）
  - `token_usage_json`（JSON：prunedNodes/prunedEdges/estimatedTokens/promptTokens/completionTokens）
  - `error_message`（NULL，成功时）
  - `create_time`、`update_time`
- **验证方式**: 异步问答请求完成 → 查询 MySQL `SELECT * FROM qa_task WHERE task_id = '{taskId}'` → 断言以上字段均非空（除 error_message）

### AC-10 · 学生不存在 — 友好错误提示

- **Given** Neo4j 中不存在 `studentName="不存在的学生"` 的 Student 节点
- **When** 发送 `POST /api/v1/qa/ask`：
  ```json
  {
    "question": "分析学生不存在学生的数学薄弱点",
    "studentName": "不存在的学生",
    "subject": "数学"
  }
  ```
- **Then** HTTP 404，错误码 `A0006`（资源不存在），`errorMessage` 含"未找到学生：不存在的学生"
- **验证方式**: 发送上述请求 → 断言 HTTP 404 + 错误码 A0006

### AC-11 · 学生无薄弱点 — 全部掌握的优雅处理

- **Given** Neo4j 中 Student `S3` 的所有 MASTERS 边 weight ≥ 0.8（全部掌握良好），无 weight < 0.6 的薄弱点
- **When** 发送学生诊断请求（studentName="S3", subject="数学"）
- **Then** HTTP 200，`answer` 中：
  - LLM 应生成"该学生在数学学科上掌握良好，无显著薄弱知识点"的正面评价
  - 列出各知识点的掌握度概览（可作为优势展示）
  - **不**出现编造的薄弱点或虚假建议
  - `tokenUsage.prunedNodes` 仍 ≥ 1（至少含 Student 节点 + 已掌握的 KP 节点）
- **验证方式**: 构造全高 weight MASTERS 的学生 → 发送 QA 请求 → 断言 HTTP 200 → 断言 `answer` 含"掌握良好"或"无显著薄弱"等正面表述 → 断言子图中无 weight < 0.6 的 MASTERS 边

### AC-12 · 空图谱 — 无数据场景的错误处理

- **Given** Neo4j 中 Student `S4` 存在，但该学生在指定 subject="数学" 下无任何 MASTERS 边（未参加过考试或无成绩数据）
- **When** 发送学生诊断请求（studentName="S4", subject="数学"）
- **Then** HTTP 200，`answer` 中 LLM 应生成"该学生在数学学科暂无成绩数据，无法进行薄弱点分析"的提示
- **验证方式**: 构造无 MASTERS 边的 Student → 发送 QA 请求 → 断言 HTTP 200 → 断言 `answer` 含"暂无成绩数据"或类似表述 → 断言 `tokenUsage.prunedNodes <= 2`（仅 Student + 可能无 KP）

---

## 范围切分

### v1（本次必做）

- STUDENT_DIAGNOSIS 意图识别（规则 + 关键词匹配）
- 单学科学生薄弱点诊断（沿 MASTERS + PREREQUISITE_OF ≤2 跳剪枝）
- 同步问答 API（`POST /api/v1/qa/ask`）
- 异步问答 API（`POST /api/v1/qa/ask-async` + `GET /api/v1/qa/result/{taskId}`）
- 独立子图端点（`GET /api/v1/qa/subgraph/{taskId}`）
- 子图序列化 + LLM Prompt 组装（Markdown 格式输出约束）
- LLM 调用失败重试（最多 3 次）
- Token 预算控制（按优先级截断 + 省略标注）
- MySQL `qa_task` 表持久化（含完整生命周期状态 + 子图 JSON + token 用量）
- `SubgraphPruningStrategy` 接口定义 + `StudentDiagnosisStrategy` v1 实现
- Prompt 模板文件化管理（classpath 下 `.md` 文件）
- LLM 输出 Markdown 格式校验 + 重试

### v2（下一轮考虑，不本次）

- `KP_ANALYSIS` 意图（知识点班级掌握度分析）
- `CLASS_OVERVIEW` 意图（班级整体概览）
- `PREREQUISITE_CHAIN` 意图（前置依赖链追溯）
- `GENERAL` 兜底意图（LLM 自主判断剪枝路径）
- 多轮对话上下文（会话历史管理）
- LLM streaming 响应（SSE）
- 相似问题 Redis 缓存命中
- embedding 意图分类（替代规则匹配）
- PageRank/中心性图算法剪枝
- 子图聚合节点（班级分析时预聚合而非展开全量学生）
- 分析结论自动翻译（中→英 / 英→中）
- 学生历史分析趋势对比（"与上次诊断相比进步/退步"）
- 教学资源推荐（基于薄弱点反向索引到文档中的例题/定义）

### out（永远不做）

- 前端对话 UI / 图可视化渲染（属于前端 change）
- 跨学科归因分析（如"数学薄弱导致物理受影响"）
- LLM 自主选择遍历路径的 Agent 模式（安全风险 + 不确定性）
- QuestionNode 细粒度试题节点
- 多模型 A/B 对比评分
- 实时成绩推送触发分析更新（用户需主动提问）
- 语音输入/输出
- 融合操作触发自动分析（分析与写入链路解耦）

---

## 非功能性需求

- **性能**: 同步问答（`POST /api/v1/qa/ask`）在子图节点 ≤ 50、边 ≤ 100 的规模下，端到端响应时间 ≤ 30 秒（含 Neo4j 查询 + 子图序列化 + LLM 调用）；超时自动降级为 HTTP 202 异步模式。异步任务在 120 秒内完成（含 LLM 调用 + 可能的重试）
- **可访问性**: 无（纯后端 API）
- **安全**: QA 端点需认证用户身份（Spring Security 已认证用户），但无需管理员权限（与融合/抽取端点区分）。LLM prompt 注入防护——用户输入的 `question` 参数在拼入 prompt 前做基本清洗（移除 `[system]`、`[/system]`、`<|im_start|>` 等特殊标记）。`qa_task` 表中的 `answer` 和 `subgraph_json` 字段不做 SQL 注入防护（由 JPA 参数化查询保障）
- **兼容性**: 不破坏已有 `knowledge-graph-extraction`、`csv-grade-import`、`wide-graph-fusion` 的任何 API 行为；`LlmGateway.chat()` 接口不变；`GraphNodeRepository` 仅新增只读查询方法，不修改现有方法签名
- **可观测性**: 
  - 每次问答请求记录 INFO 日志（含 taskId、intent、studentName、subject、prunedNodes/prunedEdges、LLM 调用耗时、总耗时）
  - LLM 调用失败记录 ERROR 日志（含 taskId、重试次数、最后一次异常信息）
  - Token 预算截断记录 WARN 日志（含 taskId、原始 token 估计值、截断后值、省略节点数）
  - `qa_task` 表提供完整问答历史（可审计：谁在什么时间问了什么问题、LLM 给出了什么答案）
  - Prometheus metrics：问答请求计数（按 intent + status 分桶）、LLM 调用耗时分布（P50/P95/P99）、子图大小分布

## 依赖与假设

- **依赖**:
  - `wide-graph-fusion` 产出的 MASTERS 聚合边 + PREREQUISITE_OF 依赖链（无融合则无 MASTERS，剪枝策略需降级处理——仅基于 TESTED 边的直接得分率，不做时间衰减聚合，在 prompt 中标注"融合数据不可用"）
  - `knowledge-graph-extraction` 的 Neo4j 图节点/边抽象体系（`GraphNode`/`GraphEdge`/`GraphNodeRepository`）
  - `csv-grade-import` 的 Student/Exam/KnowledgePoint 节点 + ATTENDED/TESTED 边 + MySQL `exam_record` 表
  - `LlmGateway.chat()` — v1 最小契约接口（`String chat(String systemPrompt, String userMessage)`）
  - `.specs/CONTEXT.md` 中定义的四层架构 + 构造器注入 + 分层异常传递规范
- **假设**:
  - MASTERS 边存在于 Neo4j 中（由 `wide-graph-fusion` 触发融合后产生）。若融合从未执行，MASTERS 边不存在，系统降级为直接查询 TESTED 路径并计算原始得分率（不做时间衰减），在 prompt 中标注"融合数据不可用，以下为原始考试得分率"
  - PREREQUISITE_OF 边已由 `knowledge-graph-extraction` 的 LLM 抽取产生，v1 不校验依赖链的完整性和正确性（那是抽取阶段的职责）
  - Student 节点唯一标识为 `studentNo`（学号），用户可通过 `studentName`（姓名模糊匹配）或 `studentNo`（精确匹配）指定目标学生。若 `studentName` 匹配到多个 Student 节点（同名），返回 409 Conflict 并列出所有匹配的 `(studentNo, name, className)` 供用户选择
  - subject 参数为必传（不从 Student 节点推断），简化 v1 实现。subject 必须在 Neo4j KnowledgePoint 节点的 `subject` 属性中存在
  - LLM 输出能稳定解析为 Markdown 格式——若 LLM 返回纯文本无格式，系统不将其视为错误（AC-4 的重试是格式增强，非硬性阻断）
  - 子图序列化格式为自定义结构化文本（节点列表 + 边列表 + 统计摘要），非 Graphviz DOT 或 Mermaid 格式。LLM 能理解此自定义格式
  - `qa_task` 表不设置 `is_deleted` 逻辑删除（日志类表遵循 CONTEXT.md"日志表除外"规则），记录永久保留
  - Token 估算公式为保守近似（1 token ≈ 3 字符），实际 token 数以 LLM API 返回的 `usage` 字段为准，估算仅用于截断判断

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。