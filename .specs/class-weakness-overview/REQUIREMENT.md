# REQUIREMENT: 班级薄弱概览 — 智能问答新增第二意图

- **Change ID**: `class-weakness-overview`
- **关联**: `@.specs/class-weakness-overview/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师，我想用自然语言询问"某班某学科的整体薄弱情况"，以便快速定位全班共性问题，决定课堂教学重点，无需逐个学生诊断。
- **US-2**：作为教师，我想班级概览报告包含可视化图表（薄弱知识点排行柱状图、班级掌握度分布、依赖链拓扑图），以便一眼看懂班级整体薄弱结构，不必逐行阅读文本。
- **US-3**：作为模块维护者，我想新增意图只需实现已有机口 + 注册即可接入，以便后续继续扩展新意图时不改动 `QueryServiceImpl` 核心调用代码和意图识别编排逻辑。

---

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · LLM 意图识别 — 班级概览

- **Given** Neo4j 宽图谱中存在 `className="初三(1)班"` 的 Student 节点及其 MASTERS 边数据。MySQL `exam_record` 中有对应班级的学生记录。`query.output-format` 为任意值。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** HTTP 200，响应体含：
  - `intent`: `"CLASS_WEAKNESS_OVERVIEW"`
  - `answer` 非空，包含该班级的薄弱知识点聚合分析内容
  - 系统日志中可见 `"LLM 意图识别成功: intent=CLASS_WEAKNESS_OVERVIEW"`（INFO 级别）
- **验证方式**: 准备 Neo4j + MySQL 数据 → `curl -X POST http://localhost:8080/api/v1/query/chat -H 'Content-Type: application/json' -d '{"question":"分析初三(1)班数学薄弱知识点"}'` → 断言 HTTP 200、`intent=CLASS_WEAKNESS_OVERVIEW` → `grep "LLM 意图识别成功.*CLASS_WEAKNESS_OVERVIEW" logs/graphnexus.log` 有输出

### AC-2 · LLM 意图识别 — 班级概览其他说法

- **Given** 同 AC-1 的数据。
- **When** 发送以下问题（各一次）：
  - `"初三(1)班数学哪些知识点需要加强"`
  - `"帮我看看初三(1)班全班数学掌握情况"`
  - `"初三(1)班数学哪里比较薄弱"`
- **Then** 三次请求均 HTTP 200，`intent` 均为 `"CLASS_WEAKNESS_OVERVIEW"`
- **验证方式**: 三次 curl → 全部断言 `intent=CLASS_WEAKNESS_OVERVIEW`

### AC-3 · 意图区分 — 班级 vs 学生

- **Given** 同 AC-1 的数据。Neo4j 中存在学生"张三"（`className="初三(1)班"`）。
- **When** 发送：
  ```json
  {"question": "分析张三数学薄弱点"}
  ```
- **Then** HTTP 200，`intent`: `"STUDENT_DIAGNOSIS"`（不被误判为 CLASS_WEAKNESS_OVERVIEW）
- **When** 发送：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** HTTP 200，`intent`: `"CLASS_WEAKNESS_OVERVIEW"`（不被误判为 STUDENT_DIAGNOSIS）
- **验证方式**: 两次 curl 请求 → 断言 intent 各自正确

### AC-4 · 关键词 fallback — 班级概览

- **Given** 同 AC-1 的数据。LLM 网关被临时配置为不可用（或 mock 抛异常）。
- **When** 发送：
  ```json
  {"question": "分析初三(1)班全班数学薄弱点"}
  ```
  （含关键词"班级"或"全班"）
- **Then** HTTP 200，响应体含：
  - `intent`: `"CLASS_WEAKNESS_OVERVIEW"`
  - `answer` 非空
  - 系统日志中可见 `"LLM 意图识别失败，降级为规则匹配"`（WARN 级别）及后续 `"规则意图识别成功: intent=CLASS_WEAKNESS_OVERVIEW"`（INFO 级别）
- **验证方式**: 临时修改 LLM endpoint 为无效 URL → curl 发送含关键词"全班"的问题 → 断言 HTTP 200、`intent=CLASS_WEAKNESS_OVERVIEW` → 恢复 LLM 配置

### AC-5 · 双重失败 — 班级概览意图失败抛错

- **Given** LLM 不可用。
- **When** 发送：
  ```json
  {"question": "初三(1)班数学怎么样"}
  ```
  （不含"班级""全班""薄弱"等任何关键词，LLM 也不可用）
- **Then** HTTP 400，错误码 `A0019`，`errorMessage` 含"无法识别查询意图"
- **验证方式**: LLM 不可用时 → curl 发送不含关键词的问题 → 断言 HTTP 400、`errorCode=A0019`

### AC-6 · 班级剪枝 — 正常路径（MASTERS 可用）

- **Given** Neo4j 中存在 `className="初三(1)班"` 的 Student 节点 ≥ 5 个，且均存在 MASTERS 边（覆盖 ≥ 3 个不同 KP，其中 ≥ 2 个 KP 的平均 weight < 0.6）。MySQL `exam_record` 中有对应记录。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** HTTP 200，响应体含：
  - `intent`: `"CLASS_WEAKNESS_OVERVIEW"`
  - `tokenUsage.prunedNodes` ≥ 6（1 个虚拟班级节点 + ≥ 3 个 KP + ≥ 2 个前置 KP）
  - `tokenUsage.prunedEdges` ≥ 3（≥ 3 条聚合 MASTERS 边）
  - 子图数据中 MASTERS 边的 `weight` 包含聚合信息（非单个学生的 weight）
- **验证方式**: 准备 Neo4j 数据 → curl 发送 POST → 断言 HTTP 200、`prunedNodes ≥ 6`、`prunedEdges ≥ 3`

### AC-7 · 班级剪枝 — MASTERS 降级路径

- **Given** 同 AC-6 的数据，但 Neo4j 中无 MASTERS 边（融合从未执行）。存在 TESTED 路径（`Student→ATTENDED→Exam→TESTED→KP`）。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** HTTP 200，响应体含：
  - `intent`: `"CLASS_WEAKNESS_OVERVIEW"`
  - `answer` 中包含降级提示（如"融合数据不可用，以下为原始考试得分率统计"）
  - `tokenUsage.prunedNodes` ≥ 3
- **验证方式**: 确认 Neo4j 无 MASTERS 边 → curl 发送 → 断言 HTTP 200 → 断言 answer 含降级提示关键词

### AC-8 · 班级剪枝 — 班级不存在

- **Given** MySQL `exam_record` 中无 `className="不存在的班级"` 的任何记录。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析不存在的班级数学薄弱知识点"}
  ```
- **Then** HTTP 400，错误码 `A0006`，`errorMessage` 含"未找到班级"
- **验证方式**: curl 发送不存在的班级名 → 断言 HTTP 400、`errorCode=A0006`

### AC-9 · HTML+SVG 输出 — 班级概览端到端

- **Given** `query.output-format=html-svg`。Neo4j 数据同 AC-6。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** HTTP 200，响应体含：
  - `outputFormat`: `"html-svg"`
  - `answer`（字符串）：以 HTML 标签开头（如 `<h2>`、`<div>`），**不**以 Markdown 标记（`##` 或 `# `）开头
  - `answer` 内部包含至少 1 个 `<svg ...>` 元素（含 `xmlns` 和 `viewBox` 属性）
  - 报告内容含班级名称"初三(1)班"
- **验证方式**: 确保 `query.output-format=html-svg` → curl 发送 → 断言 HTTP 200、`outputFormat=html-svg`、`answer` 以 `<` 开头 → 将 `answer` 保存为 `.html` 文件 → 浏览器打开 → 肉眼验证含班级名和可见 SVG 图形

### AC-10 · Markdown 输出 — 班级概览

- **Given** `query.output-format=markdown`。Neo4j 数据同 AC-6。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** HTTP 200，响应体含：
  - `outputFormat`: `"markdown"`
  - `answer` 以 `## ` 或 `# ` 开头（Markdown 标题）
  - `intent`: `"CLASS_WEAKNESS_OVERVIEW"`
- **验证方式**: 配置 `output-format=markdown` → curl 发送 → 断言 HTTP 200、`outputFormat=markdown`、`answer` 以 `#` 开头

### AC-11 · chat() 实体提取 — 班级名识别

- **Given** 同 AC-6 的数据。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** 系统从问题中提取出 `className="初三(1)班"` 和 `subject="数学"`，并成功完成班级概览分析。
  - 系统日志中可见提取到的 className 和 subject（DEBUG 或 INFO 级别）
- **验证方式**: curl 发送 → 断言 HTTP 200 → `grep "初三(1)班" logs/graphnexus.log` 可见提取日志

### AC-12 · chat() 实体提取 — 班级名正则 fallback

- **Given** 同 AC-6 的数据。LLM 实体提取被 mock 为返回 null（模拟 LLM 提取失败）。
- **When** 发送：
  ```json
  {"question": "分析初三(1)班数学薄弱知识点"}
  ```
- **Then** 正则兜底提取到 `className="初三(1)班"` 和 `subject="数学"`，班级概览分析正常完成。
- **验证方式**: mock LLM 实体提取返回 null → curl 发送 → 断言 HTTP 200 → 日志中可见"LLM 实体提取失败，降级为规则提取"

### AC-13 · 学生诊断不退化（回归）

- **Given** 同 `llm-intent-recognition` AC-1 的数据和配置。
- **When** 发送与 AC-1 相同的请求：
  ```json
  {"question": "帮我看看李四数学怎么样"}
  ```
- **Then** HTTP 200，响应体含：
  - `intent`: `"STUDENT_DIAGNOSIS"`
  - `answer` 非空
  - 行为与 `class-weakness-overview` 变更前完全一致
- **验证方式**: `llm-intent-recognition` AC-1 的 curl 命令 → 断言结果与变更前一致

### AC-14 · 策略注册表路由 — 新意图

- **Given** Spring 容器中 `PruningStrategyRegistry` 已含 `StudentDiagnosisStrategy`（bean name="STUDENT_DIAGNOSIS"）和 `ClassWeaknessOverviewStrategy`（bean name="CLASS_WEAKNESS_OVERVIEW"）。
- **When** 调用 `pruningStrategyRegistry.get("CLASS_WEAKNESS_OVERVIEW")`
- **Then** 返回 `ClassWeaknessOverviewStrategy` 实例。
- **When** 调用 `pruningStrategyRegistry.get("STUDENT_DIAGNOSIS")`
- **Then** 返回 `StudentDiagnosisStrategy` 实例。
- **验证方式**: 启动 Spring 容器 → 检查启动日志中 Registry 打印的已注册策略清单含两个 bean name

### AC-15 · 前端渲染 — 班级概览报告

- **Given** 后端返回 `outputFormat=html-svg` 且 `answer` 为班级概览 HTML+SVG 内容。
- **When** 前端 `IntelligentQAPage.vue` 接收响应并渲染。
- **Then**：
  - 报告以 `HtmlSvgViewer` 渲染（非 `MarkdownViewer`）
  - 页面无 console 报错
  - SVG 图形可见（不要求交互，纯静态即可）
  - 班级名称"初三(1)班"在报告文本中可见
- **验证方式**: 前端正常发起班级概览请求 → 检查渲染结果 DOM → 无 `.markdown-viewer` CSS class → svg 元素存在

---

## 范围切分

### v1（本次必做）

- `QueryIntent` 枚举新增 `CLASS_WEAKNESS_OVERVIEW` 值（取消注释并调整 v2 预留的 `CLASS_OVERVIEW`）
- 新增 `ClassWeaknessOverviewStrategy`（`@Component("CLASS_WEAKNESS_OVERVIEW")`），实现班级级 KP 聚合剪枝
- `QueryGraphRepository` 新增 `findStudentsByClassName()` 查询
- `ExamRecordRepository` 新增 `findDistinctStudentsByClassName()` 查询
- `QueryServiceImpl` 适配班级级查询：`resolveClass()` + `serializeClassSubgraph()` + `buildClassTemplateVars()` + `ask()` 意图分支
- `QueryServiceImpl.chat()` 实体提取扩展：LLM prompt 新增 `className` 字段 + 正则新增班级名模式
- 新增 4 个班级概览 Prompt 模板（`class-weakness-overview-{system,user}.md` + `-html.md` 变体）
- 更新 `intent-classification-system.md`：新增 `CLASS_WEAKNESS_OVERVIEW` 的 few-shot 示例
- `KeywordIntentRecognitionStrategy.buildKeywordMap()` 新增班级相关关键词（"班级""全班""某班"）
- `PruningRequest` Javadoc 更新 `entityId` 语义说明（studentNo 或 className）
- 单元测试：`ClassWeaknessOverviewStrategyTest` + `IntentRecognitionServiceTest` 扩展 + `KeywordIntentRecognitionStrategyTest` 扩展
- 集成测试：`QueryControllerIntegrationTest` 扩展（班级概览 AC-1/AC-4/AC-6/AC-9/AC-10）

### v2（下一轮考虑，不本次）

- 跨学科班级概览（"初三(1)班全科薄弱分析"）
- 多班级对比（"对比初三(1)班和初三(2)班数学"）
- 班级内学生分群（按掌握度将学生分为"严重薄弱组/中等组/良好组"分别输出教学建议）
- 班级概览异步模式（`POST /api/v1/query/ask-async` 支持 className 参数）
- 班级名模糊匹配与候选列表（输入"初三一班"也能匹配"初三(1)班"）
- 班级概览历史记录专属筛选维度（按 className 筛选历史）
- 班级薄弱趋势对比（同一班级不同时间段的薄弱点变化）

### out（永远不做）

- 纯手动班级选择器 UI（Q2=C 已排除）
- 班级概览的独立前端页面/路由（复用现有智能问答页）
- 班级概览的导出格式定制（复用现有 HTML 导出端点，report 内容本身已有班级信息）
- 跨学校/跨学区级别的聚合分析（超出产品定位）
- 实体解析策略接口（`EntityResolutionStrategy`）——仅 2 种实体类型，if-else 足够，接口是过度工程

---

## 非功能性需求

- **性能**: 班级剪枝聚合计算（Java 层 groupBy）在班级 ≤ 60 人时耗时 ≤ 500ms。整体同步问答端到端 ≤ 30s（不变）
- **可访问性**: HTML+SVG 输出中 `<svg>` 元素含 `<title>` 和 `<desc>`（同 `llm-intent-recognition` 非功能需求）。文字颜色对比度 ≥ 4.5:1
- **安全**: 前端渲染经 DOMPurify 净化（同既有机制，无新增安全面）
- **兼容性**: `QueryAskResponse.intent` 新增 `"CLASS_WEAKNESS_OVERVIEW"` 值，旧客户端忽略未知值不报错。Chrome/Firefox/Edge 120+（不变）
- **可观测性**: 班级剪枝各步骤（班级验证 → 学生查询 → MASTERS 聚合 → 前置依赖展开）均需 INFO/DEBUG 级日志 + traceId 关联。LLM 意图分类为 `CLASS_WEAKNESS_OVERVIEW` 时记录 INFO 日志

## 依赖与假设

- **依赖 `IntentRecognitionService`** — 不修改策略链编排逻辑，仅新增 `CLASS_WEAKNESS_OVERVIEW` 到 LLM prompt 的意图列表（由 `buildIntentList()` 自动生成）和关键词 Map
- **依赖 `PruningStrategyRegistry`** — 新策略通过 `@Component("CLASS_WEAKNESS_OVERVIEW")` 自动注册，Registry 零改动
- **依赖 `PromptTemplateService`** — `buildPrompt(intent, vars, format)` 自动按 intent name 拼接模板文件名（`class-weakness-overview-system.md`），方法签名不变
- **依赖 `QueryGraphRepository`** — 新增 1 个只读查询（`findStudentsByClassName`），不影响现有查询
- **依赖 `ExamRecordRepository`** — 新增 1 个查询（`findDistinctStudentsByClassName`），不影响现有查询
- **依赖 `LlmGateway.chat()`** — 不变。意图分类和实体提取各调用一次，班级分析调用一次（与 `STUDENT_DIAGNOSIS` 调用次数相同）
- **假设 `className` 在 MySQL 和 Neo4j 中格式一致** — 上传成绩时 `className` 由 CSV/Excel 文件直接提供，格式与用户自然语言中的班级名一致（如"初三(1)班"）
- **假设班级规模 ≤ 60 人** — 超过 60 人的班级在聚合时截断并标注（prompt 中说明"班级人数过多，以下为前 60 人统计"）
- **假设 LLM 能正确区分含学生名的班级问题和纯班级问题** — 通过 few-shot 示例明确优先级：含明确学生姓名 → STUDENT_DIAGNOSIS；仅含班级名不含学生名 → CLASS_WEAKNESS_OVERVIEW

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。