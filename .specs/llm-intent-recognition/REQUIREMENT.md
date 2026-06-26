# REQUIREMENT: LLM 意图识别 + HTML/SVG 输出

- **Change ID**: `llm-intent-recognition`
- **关联**: `@.specs/llm-intent-recognition/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师/家长，我想用自然语言自由提问（无需刻意使用"薄弱""诊断"等关键词），以便系统能理解我的意图并给出诊断报告，不管我换什么说法。
- **US-2**：作为教师/家长，我想诊断报告包含可视化图表（柱状图、知识点依赖关系图、数学公式），以便一眼看懂学生的薄弱点分布和根因链路，不必逐行阅读文本。
- **US-3**：作为系统运维者，我想通过一个 yml 配置项就能把 LLM 输出格式从 HTML+SVG 切回 Markdown，以便 HTML+SVG 方案出问题时能立即回滚到已验证的稳定路径，无需重新部署或改代码。
- **US-4**：作为模块维护者，我想意图识别和剪枝策略通过独立服务/注册机制解耦，以便后续新增意图类型或剪枝策略时只需添加新类 + 注册，不修改 `QueryServiceImpl` 核心调用代码。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · LLM 意图识别 — 无关键词命中

- **Given** Neo4j 宽图谱中存在 Student `S1`（studentNo="S2024001", name="张三", className="初三(1)班"）及其 MASTERS 边数据。MySQL `exam_record` 中有对应记录。`query.output-format` 为任意值。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "帮我看看李四数学怎么样"}
  ```
  （注意："帮我看看...怎么样"不含"薄弱""诊断""加强""掌握""分析学生"任意一个现有关键词）
- **Then** HTTP 200，响应体含：
  - `intent`: `"STUDENT_DIAGNOSIS"`
  - `answer` 非空，包含该学生的诊断分析内容
  - 系统日志中可见 `"LLM 意图识别成功: intent=STUDENT_DIAGNOSIS"`（INFO 级别）
- **验证方式**: 准备上述 Neo4j + MySQL 数据 → 清空 `logs/graphnexus.log` 末尾 → `curl -X POST http://localhost:8080/api/v1/query/chat -H 'Content-Type: application/json' -d '{"question":"帮我看看李四数学怎么样"}'` → 断言 HTTP 200、`intent=STUDENT_DIAGNOSIS` → `grep "LLM 意图识别成功" logs/graphnexus.log` 有输出

### AC-2 · LLM 意图识别 — 规则 fallback

- **Given** 同 AC-1 的数据。LLM 网关被临时配置为不可用（如 `llm-gateway.provider` 指向无效 endpoint），或 LLM 返回无法解析的非 JSON 文本。
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "分析学生张三的数学薄弱点"}
  ```
  （注意：含关键词"薄弱"和"分析学生"）
- **Then** HTTP 200，响应体含：
  - `intent`: `"STUDENT_DIAGNOSIS"`
  - `answer` 非空
  - 系统日志中可见 `"LLM 意图识别失败，降级为规则匹配"`（WARN 级别）及后续 `"规则意图识别成功: intent=STUDENT_DIAGNOSIS"`（INFO 级别）
- **验证方式**: 临时修改 `application-dev.yml` 中 LLM endpoint 为无效 URL → 发送 curl 请求 → 断言 HTTP 200、`intent=STUDENT_DIAGNOSIS` → 恢复 LLM 配置

### AC-3 · LLM 意图识别 — 双重失败抛错

- **Given** 同 AC-2（LLM 不可用）
- **When** 发送 `POST /api/v1/query/chat`：
  ```json
  {"question": "帮我看看李四数学怎么样"}
  ```
  （注意：不含任何关键词，LLM 也不可用）
- **Then** HTTP 400，错误码 `A0019`，`errorMessage` 含"无法识别查询意图"
- **验证方式**: LLM 不可用时 → curl 发送不含关键词的问题 → 断言 HTTP 400、`errorCode=A0019`

### AC-4 · HTML+SVG 输出 — 端到端

- **Given** `application-dev.yml` 中 `query.output-format=html-svg`。Neo4j 数据同 AC-1。
- **When** 发送 `POST /api/v1/query/ask`：
  ```json
  {
    "question": "分析学生张三的数学薄弱点",
    "studentName": "张三",
    "subject": "数学"
  }
  ```
- **Then** HTTP 200，响应体含：
  - `outputFormat`: `"html-svg"`
  - `answer`（字符串）：以 HTML 标签开头（如 `<div>`、`<h2>`、`<table>` 之一），**不**以 Markdown 标记（`##` 或 `# `）开头
  - `answer` 内部包含至少 1 个 `<svg ...>` 元素（含 `xmlns` 和 `viewBox` 属性），且该 SVG 在浏览器中渲染为可见图形
  - `tokenUsage` 含 `prunedNodes`、`prunedEdges`、`estimatedTokens`
  - LLM 分析结论中引用的知识点名称和依赖关系与子图数据一致（不编造）
- **验证方式**: 准备 Neo4j 数据 → 确保 `query.output-format=html-svg` → curl 发送 POST → 断言 HTTP 200、`outputFormat=html-svg`、`answer` 以 `<` 开头且不以 `#` 开头 → 将 `answer` 内容保存为 `.html` 文件 → 浏览器打开 → 肉眼验证可见 SVG 图形（柱状图/依赖图/公式至少一种）→ 且内容中出现的知识点名称与子图数据一致

### AC-5 · HTML+SVG 格式校验 + 重试

- **Given** `query.output-format=html-svg`。LLM 首次调用返回不符合规范的文本（如纯文本无 HTML 标签、或有 `<script>` 标签）。
- **When** 发送问答请求
- **Then** 系统自动重试 ≤ 2 次（重试时 prompt 中注入格式修正提示）。若重试耗尽仍未通过校验，降级返回最后一次原始文本，日志 WARN 级别记录
- **验证方式**: 通过测试用 Mock LLM 或 LLM 记录/回放工具 → 注入不合规输出 → 观察日志确认重试发生 → 断言最终仍有 HTTP 200（降级返回）且日志含 WARN "HTML+SVG 格式校验重试耗尽"

### AC-6 · Markdown 回滚 — 配置切换后行为完全一致

- **Given** `application-dev.yml` 中 `query.output-format=markdown`
- **When** 发送与 [intelligent-qa AC-1](.specs/intelligent-qa/REQUIREMENT.md) 相同的请求：
  ```json
  {"question": "分析学生张三的数学薄弱点", "studentName": "张三", "subject": "数学"}
  ```
- **Then** HTTP 200，响应体含：
  - `outputFormat`: `"markdown"`
  - `answer` 以 `## ` 或 `# ` 开头（Markdown 标题）
  - `intent`: `"STUDENT_DIAGNOSIS"`
  - 行为与现存 `intelligent-qa` 实现完全一致（同一套 prompt 模板、同一套校验规则、同一套前端 marked 渲染）
- **验证方式**: 配置 `output-format=markdown` → 发送 curl → 断言 HTTP 200、`outputFormat=markdown`、`answer` 以 `#` 开头 → 对比配置切回前（`intelligent-qa` 基线）的响应结构一致

### AC-7 · 前端 HTML/SVG 安全渲染

- **Given** 后端返回 `outputFormat=html-svg` 且 `answer` 包含 `<h2>诊断报告</h2><svg ...>...</svg><script>alert('xss')</script>`
- **When** 前端 `HtmlSvgViewer.vue` 渲染该内容
- **Then**：
  - `<h2>诊断报告</h2>` 正常显示
  - `<svg>` 元素被保留并正常渲染
  - `<script>alert('xss')</script>` 被 DOMPurify 移除，不执行
  - `<foreignObject>` 标签（如存在）被移除
  - `<use>` 标签的 `xlink:href` 属性被移除（如有）
  - 页面不弹 alert、不发送网络请求、无 console 报安全错误
- **验证方式**: 通过浏览器 DevTools 手动注入含 `<script>` 的 `answer` 到 store → `HtmlSvgViewer` 渲染 → 检查 DOM：`<h2>` 和 `<svg>` 存在、`<script>` 不存在 → 检查 console 无 JS 执行报错

### AC-8 · 前端格式自适应渲染

- **Given** 后端返回 `outputFormat=html-svg`
- **When** 前端 `IntelligentQAPage.vue` 接收响应
- **Then** 使用 `HtmlSvgViewer` 渲染 `answer`（而非 `MarkdownViewer`）
- **Given** 后端返回 `outputFormat=markdown`
- **When** 前端 `IntelligentQAPage.vue` 接收响应
- **Then** 使用 `MarkdownViewer`（marked 渲染）渲染 `answer`
- **验证方式**: 分别在两种配置下发送请求 → 检查前端 Network 面板中响应 `outputFormat` 字段 → 检查渲染结果 DOM：`html-svg` 时无 `.markdown-viewer` CSS class，`markdown` 时有 `.markdown-viewer` CSS class

### AC-9 · SVG 内容 — 数据图表

- **Given** `query.output-format=html-svg`。Neo4j 中学生有 ≥ 3 个薄弱知识点（MASTERS weight < 0.6），掌握度数值可区分（如 20%/35%/50%）。
- **When** 完成问答后检查 `answer` 内容
- **Then** `answer` 中包含的 `<svg>` 元素内含有表示数据对比的图形结构：至少含 `<rect>`（柱状图）或 `<polygon>`/`<polyline>`（雷达图/折线图），且图形元素旁有对应知识点名称的 `<text>` 标签
- **验证方式**: curl 请求 → 提取 `answer` 中的 `<svg>` 片段 → 用 `xmllint --html` 解析 → 断言 `//svg:rect` 或 `//svg:polygon` 存在 → 断言 `//svg:text` 中存在至少 1 个已知 KP 名称

### AC-10 · SVG 内容 — 知识图谱子图（前置依赖链）

- **Given** `query.output-format=html-svg`。Neo4j 中存在 PREREQUISITE_OF 链（≥ 2 条边，如 配方法→顶点坐标、对称轴→顶点坐标）。
- **When** 完成问答后检查 `answer` 内容
- **Then** `answer` 中包含表示知识点依赖关系的 `<svg>` 元素：含 `<circle>` 或 `<ellipse>`（节点）+ `<line>` 或 `<path>`（边）+ `<text>`（知识点标签），形成可辨识的拓扑图
- **验证方式**: curl 请求 → 提取 `answer` 中 SVG 片段 → `xmllint` 解析 → 断言至少含 3 个 `<circle>` + 2 条 `<line>` + 3 个 `<text>`（分别对应依赖链上的几个知识点）

### AC-11 · API 向后兼容

- **Given** 前端使用旧版 `query.ts` API 客户端（不含 `outputFormat` 字段的 TypeScript 类型定义）
- **When** 发送 `POST /api/v1/query/ask`（`output-format=html-svg`）→ 后端返回含 `outputFormat` 字段的 JSON
- **Then** 旧客户端：
  - 仍能正常解析 `taskId`、`question`、`intent`、`answer`、`tokenUsage`、`errorMessage` 字段（JSON 反序列化忽略未知字段）
  - `answer` 仍为字符串，旧客户端可继续用 marked 渲染（虽然 HTML+SVG 内容在 marked 下显示异常，但不会导致 JS 报错或白屏）
- **验证方式**: 用旧版 TypeScript 类型构建前端 → 发送请求 → 断言无 console error、页面不崩溃、`answer` 原始字符串可见

### AC-12 · 策略路由解耦

- **Given** Spring 容器中存在 `PruningStrategyRegistry`（含至少 1 个已注册策略 `StudentDiagnosisStrategy`）
- **When** `QueryServiceImpl.ask()` 执行到剪枝步骤，调用 `pruningStrategyRegistry.get("STUDENT_DIAGNOSIS")`
- **Then**：
  - 返回 `StudentDiagnosisStrategy` 实例
  - `QueryServiceImpl` 的字段中**不存在** `StudentDiagnosisStrategy diagnosisStrategy` 直接注入（仅注入 `PruningStrategyRegistry`）
  - `StudentDiagnosisStrategy` 类上的 `@Component` 或注册注解使其被 Registry 自动发现
- **验证方式**: 读 `QueryServiceImpl.java` 源码 → 断言 `private final.*StudentDiagnosisStrategy` 不存在 → 断言 `private final.*PruningStrategyRegistry` 存在 → 运行 `QueryServiceImplTest`（如有）或集成测试断言策略路由正常

---

## 范围切分

### v1（本次必做）

- 新增 `IntentRecognitionService`，LLM-first + 关键词 fallback 双重意图识别链路
- 新增 `PruningStrategyRegistry` 策略注册与路由机制，`QueryServiceImpl` 不再直接注入具体策略
- `QueryProperties` 新增 `output-format` 配置项（`html-svg` / `markdown`）
- 新增 HTML+SVG 版 prompt 模板（`student-diagnosis-system-html.md`、`student-diagnosis-user-html.md`）
- 新增 `intent-classification-system.md` 意图分类 prompt 模板
- 后端 HTML+SVG 格式校验逻辑（替换 Markdown 校验，按配置切换）
- 前端新增 `HtmlSvgViewer.vue`（DOMPurify 净化 + v-html 渲染）
- 前端 `IntelligentQAPage.vue` 按 `outputFormat` 选择渲染器
- API 响应新增 `outputFormat` 字段
- `dompurify` + `@types/dompurify` npm 依赖

### v2（下一轮考虑，不本次）

- 启用注释中预留的 v2 意图（`KP_ANALYSIS`、`CLASS_OVERVIEW`、`PREREQUISITE_CHAIN`、`GENERAL`）及对应剪枝策略实现
- 意图分类用轻量模型（如 Haiku）降成本
- 前端 SVG 交互增强（tooltip、节点点击展开、缩放拖拽）
- MathJax/KaTeX 服务端渲染复杂公式（替代 LLM 手写 MathML）
- `output-format` 运行时动态切换（无需重启）
- LLM 意图分类结果缓存（同 session 内相同问题复用分类结果）

### out（永远不做）

- 意图识别的 embedding/向量相似度匹配方案（LLM 分类已满足需求，引入向量数据库成本过高）
- 后端 SVG 预渲染为图片文件（SVG markup 由 LLM 直接生成，前端 DOM 嵌入即可，无需服务端渲染引擎）
- 意图识别的纯本地模型（不引入 Ollama/Llama.cpp 等本地推理框架）
- HTML+SVG 输出的流式 SSE 推送（v1 同步 + 异步轮询已满足需求）

---

## 非功能性需求

- **性能**: 意图分类 LLM 调用额外增加 ≤ 3s 延迟（轻量 prompt，预期 1-2s）。整体同步问答端到端 ≤ 30s（不变，已有超时配置）
- **可访问性**: HTML+SVG 输出中 `<svg>` 元素必须含 `<title>` 和 `<desc>` 子元素（可被屏幕阅读器读取），纯装饰 SVG 除外。文字内容颜色对比度 ≥ 4.5:1
- **安全**: 前端 `v-html` 渲染前**必须**经 DOMPurify 净化（白名单模式：HTML 结构标签 + SVG 图形标签 + MathML 标签）。**禁止**直接渲染 LLM 原始输出字符串。后端不向客户端暴露 LLM 原始响应的任何内部错误信息
- **兼容性**: 前端 `HtmlSvgViewer` 需支持 Chrome/Firefox/Edge 120+（与 G6 v5 WebGL 要求一致）。`outputFormat` 字段为响应新增字段，旧客户端忽略即可
- **可观测性**: 意图识别链路每一步（LLM 调用 → JSON 解析 → fallback 触发）均需 INFO/WARN 级日志 + traceId 关联。LLM 意图分类的 token 消耗记录到 `query_task.token_usage_json`

## 依赖与假设

- **依赖 `LlmGateway.chat()`** — 意图分类和 HTML+SVG 分析各调用一次，复用既有网关，不引入新 LLM 客户端
- **依赖 `PromptTemplateService`** — HTML+SVG 版模板和意图分类模板均通过既有 `{{var}}` 模板引擎加载
- **依赖 `QueryGraphRepository`** — 只读查询不变，无新增 Neo4j Cypher
- **假设 LLM 能生成合法 SVG** — 通过在 system prompt 中提供 SVG 布局约束（最小尺寸/间距/字号）+ few-shot 示例来引导，实际质量需上线后迭代
- **假设 DOMPurify 配置充分** — 白名单覆盖标准 HTML + SVG + MathML 标签，同时阻断 `<script>`/`<foreignObject>`/事件属性等 XSS 向量
- **假设 `output-format` 切换重启生效可接受** — v1 不做运行时热切换，运维人员修改 yml + 重启服务的操作窗口在可接受范围内
- **前端依赖新增** — `npm install dompurify @types/dompurify`（dompurify 约 20KB gzipped，不引入重型图表库）

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。