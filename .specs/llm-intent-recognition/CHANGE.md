# CHANGE: LLM 意图识别 + HTML/SVG 输出

- **Change ID**: `llm-intent-recognition`
- **创建日期**: 2026-06-22
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: confirmed
- **用户决策**:
  - Q1 意图范围: A — 仅替换识别机制，仍只支持 `STUDENT_DIAGNOSIS`
  - Q2 HTML+SVG 输出范围: A — 完全替换 LLM 输出为 HTML+SVG，但保留 Markdown 实现路径便于回滚
  - Q3 SVG 内容: D — 综合（数据图表 + 知识图谱子图 + 数学公式）
  - Q4 Fallback 策略: A — LLM 意图识别失败时规则兜底

---

## Why（为什么做）

`intelligent-qa` 已落地了智能问答核心链路（意图识别 → 图剪枝 → LLM 分析 → Markdown 输出），但两个设计债逐渐成为瓶颈：

### 1. 意图识别：硬编码关键词，无扩展性

当前 `QueryServiceImpl.recognizeIntent()` 用 5 个中文关键词（薄弱/加强/掌握/诊断/分析学生）硬匹配，存在明显问题：

- **覆盖盲区**：用户说"张三数学怎么样"、"帮我看看李四的代数"——不含关键词 → 直接抛错 `A0019`，用户体验差
- **无法区分语义**："分析学生张三数学掌握情况" → 关键词"掌握"命中 → 路由到薄弱点诊断，但用户本意可能是"全局掌握概览"而非"薄弱点"
- **扩展需改代码**：每增一种意图都要改关键词 Map + 重新部署。原 CHANGE 已预留 LLM 分类扩展点（"v1 规则匹配...后续 change 可扩展为 LLM 分类或 embedding 匹配"）

### 2. 输出格式：纯 Markdown 表达能力有限

当前约束 LLM 输出 Markdown，前端用 `marked` 渲染。但教育诊断场景天然需要可视化：

- **数据图表**：掌握度分布、薄弱点对比——纯文字不如柱状图/雷达图直观
- **知识图谱子图**：前置依赖链文本罗列可读性差，SVG 图谱可视化一目了然
- **数学公式**：`marked` 不处理 LaTeX，当前 prompt 约束 LLM 用中文描述代替公式（如"对称轴公式 x=-b/(2a)"），精度和可读性均受损

综合以上，本 change 将意图识别从硬编码升级为 LLM 分类，同时将 LLM 输出格式从纯 Markdown 替换为 HTML+SVG，保留 Markdown 路径作为可配置回滚选项。

## What（做什么）

### 1. LLM 意图识别（LLM-first + 规则 fallback）

```
用户问题 → LLM 意图分类（prompt 约束输出 JSON）→ 解析成功? 
  ├─ 是 → QueryIntent 枚举值 → 路由剪枝策略
  └─ 否 → 规则关键词兜底（保留现有逻辑）→ 仍失败 → 抛错 A0019
```

- 新增 `IntentRecognitionService`（L2），封装 LLM 分类 prompt 构建 + JSON 解析 + fallback 编排
- `QueryServiceImpl.recognizeIntent()` 委托给 `IntentRecognitionService`，不保留内联关键词 Map
- LLM 分类 prompt 模板外置到 `classpath:/prompts/intent-classification-system.md`
- 仅输出 `STUDENT_DIAGNOSIS`，预留其他枚举值在 prompt 中作为 negative example 防止误分类
- 与现有 `extractViaLlm` → `extractViaRegex` fallback 模式一致

### 2. HTML+SVG 输出格式（可配置，保留 Markdown 路径）

```
LLM 分析调用 → 按 yml 配置选择输出格式：
  ├─ query.output-format=html-svg（新默认）→ HTML+SVG prompt 模板 → HTML+SVG 校验 → 前端 HTML 渲染
  └─ query.output-format=markdown（回滚配置）→ 现有 Markdown prompt 模板 → 现有校验 → 现有 marked 渲染
```

- `application-dev.yml` 新增 `query.output-format` 配置项（默认 `html-svg`），切回 `markdown` 即可回滚
- 新增 HTML+SVG 版 prompt 模板（如 `student-diagnosis-system-html.md`），约束 LLM 输出纯 HTML+SVG 片段（无 `<html>/<body>` 包裹）
- `QueryServiceImpl.callLlmWithRetry()` 按配置选择模板 + 校验逻辑
- HTML+SVG 校验规则（替换现有 Markdown 校验）：必须以合法 HTML 标签开头 / 不含 Markdown 前导语 / 至少含 1 个 SVG 元素或 HTML 表格
- 保留全部现有 Markdown prompt 模板和校验代码不变，仅通过配置切换

### 3. SVG 内容生成范围

LLM 在 system prompt 中被指导生成以下 SVG（按需，不强制全量）：

| SVG 类型 | 用途 | 示例 |
|----------|------|------|
| **数据图表** | 掌握度柱状图、薄弱点雷达图、得分率饼图 | `<svg>` 手绘柱状图（非 ECharts，LLM 直接生成 SVG markup） |
| **知识图谱子图** | 前置依赖链可视化，节点-边拓扑图 | `<svg>` 含 `<circle>` 节点 + `<line>` 边 + `<text>` 标签 |
| **数学公式** | LaTeX 语义 → SVG 呈现 | `<svg>` 嵌入 MathML 或纯 SVG 路径绘制 |

**约束**：LLM 生成的 SVG 必须是自包含的 `<svg>` 标签（含 `xmlns`、`viewBox`），不依赖外部 JS 库（ECharts/D3/G6），前端仅做渲染不做计算。

### 4. 前端变更

- 新增 `HtmlSvgViewer.vue` 组件，用 `v-html` 渲染 HTML+SVG 内容，**必须**经 DOMPurify 净化（防 XSS）
- `MarkdownReport.vue` → 改为按 `outputFormat` 选择 `MarkdownViewer` 或 `HtmlSvgViewer`
- API 响应体 `QueryAskResponse` 新增 `outputFormat` 字段（`"markdown"` | `"html-svg"`），前端据此选择渲染器
- 添加 `dompurify` npm 依赖

### 5. 策略路由重构（顺带）

`QueryServiceImpl.ask()` 当前硬编码 `diagnosisStrategy.prune()`。为后续扩展意图做准备（虽然 Q1 选择仅 STUDENT_DIAGNOSIS），本次顺带引入策略路由：

- 新增 `PruningStrategyRegistry`（Map<String, SubgraphPruningStrategy>），按 intent name 路由
- `QueryServiceImpl` 注入 `PruningStrategyRegistry` 替代直接注入 `StudentDiagnosisStrategy`
- 不引入新策略实现，仅做路由抽象

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增 LLM 意图识别 + HTML/SVG 输出的 AC
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① LLM 意图分类 prompt 结构 + fallback 编排；② HTML+SVG 输出格式约束 + 校验规则；③ 输出格式可配置切换机制；④ 前端 HTML/SVG 安全渲染方案（DOMPurify）；⑤ 策略路由注册机制
- [x] 影响现有 AC — 无已有 AC 冲突。现有 Markdown 路径完整保留，通过配置切换不破坏
- [ ] 影响数据模型 / 迁移 — `query_task` 表 `answer` 字段存储 HTML+SVG 内容（原存储 Markdown），类型不变（TEXT），无 schema 变更
- [x] 影响外部 API 兼容性 — `QueryAskResponse` / `QueryResultResponse` 新增 `outputFormat` 字段，旧客户端忽略新字段不报错，**向后兼容**
- [x] 影响前端依赖 — 新增 `dompurify` + `@types/dompurify` npm 包
- [ ] 影响 prompt 模板 — 新增 3 个文件：`intent-classification-system.md`、`student-diagnosis-system-html.md`、`student-diagnosis-user-html.md`。现有 Markdown 模板不变
- [ ] 仅修复 bug，无范围变化

## 核心设计约束（进入 DESIGN 前必须遵守）

- **LLM-first + 规则 fallback**：意图识别 LLM 调用失败或 JSON 解析失败 → 降级到现有关键词匹配（保留 `INTENT_KEYWORDS` Map 在 fallback 路径中），与 `extractViaLlm` → `extractViaRegex` 模式一致
- **输出格式可切换**：通过 `query.output-format` yml 配置切换 `html-svg` / `markdown`，切换后重启生效。不设计运行时动态切换（避免同一任务内格式不一致）
- **HTML/SVG 安全**：前端 `v-html` 渲染前必须经 DOMPurify 净化（白名单模式：允许 HTML 标签 + SVG 标签 + 常用属性），禁止直接渲染 LLM 原始输出
- **SVG 自包含**：LLM 生成的 SVG 必须是独立的 `<svg>` 元素，含 `xmlns="http://www.w3.org/2000/svg"` 和 `viewBox`，不引用外部资源（字体/图片/JS/CSS）
- **策略路由解耦**：`QueryServiceImpl` 通过 `PruningStrategyRegistry` 获取策略，不直接注入具体策略实现
- **遵循既有四层架构 + 构造器注入**。新代码放 `application/query/chat/intent/`（意图识别服务）+ `frontend/src/common/components/HtmlSvgViewer.vue`（前端渲染组件）
- **LLM 网关复用**：意图识别 LLM 调用通过 `LlmGateway.chat()`，不直接使用 WebClient/LangChain4j
- **Prompt 模板外置**：意图分类 + HTML+SVG 输出的 prompt 模板均放 `classpath:/prompts/`，复用 ADR-011 的 `PromptTemplateService` + `{{var}}` 范式
- **HTML+SVG 校验重试**：与现有 Markdown 校验重试机制一致（≤2 次重试），校验规则替换为 HTML+SVG 专用规则

## 视觉调性

> 延用 `frontend-ui` CHANGE 锁定的极简（Minimal — Linear/Vercel/Stripe）调性。本 change 仅新增 HTML/SVG 渲染能力，不改变页面布局和设计语言。

## 范围排除（这次不做）

- ❌ **新增意图类型**：Q1 选择 A，不新增 KP_ANALYSIS / CLASS_OVERVIEW / PREREQUISITE_CHAIN / GENERAL 意图
- ❌ **新增剪枝策略**：不实现 `StudentDiagnosisStrategy` 以外的策略。`PruningStrategyRegistry` 仅做路由抽象，不扩展策略清单
- ❌ **流式输出（SSE/WebSocket）**：HTML+SVG 输出可能更长，但 v1 仍走同步 + 异步轮询，不做 streaming
- ❌ **前端 SVG 交互**：LLM 生成的 SVG 为静态图，不做节点点击/悬停 tooltip/缩放拖拽等交互（图谱交互仍由 G6 v5 在独立可视化页面负责）
- ❌ **SVG 后端预渲染**：不做"后端生成 SVG → 返回图片 URL"的方案。SVG markup 由 LLM 直接生成，前端嵌入 DOM
- ❌ **数学公式 LaTeX → SVG 引擎**：不做 MathJax/KaTeX 服务端渲染。公式由 LLM 在 prompt 指导下手写 SVG markup（简单公式）或 MathML（复杂公式）
- ❌ **多轮对话/会话上下文**：同 `intelligent-qa` 范围排除，本次不改
- ❌ **LLM 输出缓存**：不缓存 HTML+SVG 输出，每次请求实时生成
- ❌ **多模型输出格式 A/B**：不做同一问题同时输出 Markdown + HTML+SVG 对比

## 验收线（粗粒度，不是 AC）

1. **LLM 意图识别**：输入"分析学生张三的数学薄弱点"，LLM 分类为 `STUDENT_DIAGNOSIS` → 走诊断链路。输入"帮我看看李四数学怎么样"（不含现有任何关键词），LLM 仍识别为 `STUDENT_DIAGNOSIS` → 成功路由
2. **规则 fallback**：LLM 意图分类失败（超时/JSON 解析异常）→ 降级到关键词匹配 → "薄弱/诊断"等命中 → 仍可正常路由
3. **HTML+SVG 输出**：配置 `output-format=html-svg` 时，LLM 输出含 `<svg>` 标签的 HTML 片段 → 后端校验通过 → 前端经 DOMPurify 净化后渲染，图表/图谱/公式可见
4. **Markdown 回滚**：配置切回 `output-format=markdown` → 行为与当前完全一致（prompt 模板 + 校验 + 前端渲染全链路不变）
5. **前端安全**：LLM 返回含 `<script>alert(1)</script>` 的恶意 HTML → DOMPurify 净化后 `script` 标签被移除 → 页面不执行脚本
6. **策略路由解耦**：`QueryServiceImpl` 通过 `PruningStrategyRegistry.get("STUDENT_DIAGNOSIS")` 获取策略，不直接持有 `StudentDiagnosisStrategy` 引用

## 风险与未知

- **LLM 意图分类准确率**：中文教育领域意图边界模糊（"张三数学怎么样"可能是诊断也可能是概览），单靠 prompt 指导可能误判。需要通过 few-shot 示例 + negative example 约束提升准确率，并在上线后收集 bad case 迭代 prompt
- **LLM 生成 SVG 质量**：LLM 手写 SVG markup 可能产生布局错误（节点重叠、文本溢出、坐标越界）。需要在 prompt 中提供布局约束（如"柱状图柱宽 ≥30px、柱间距 ≥10px、字体 ≥12px"），并在校验层做基础 SVG schema 校验（含 viewBox / 无负宽高）
- **SVG 输出 token 消耗**：SVG markup 文本量大（一个简单柱状图约 500-1000 tokens），可能导致 LLM 输出 token 显著增加（相比纯文本 Markdown）。需要控制 SVG 复杂度——prompt 中约束"每个图表最多 10 个数据点"
- **DOMPurify 漏白风险**：虽然 DOMPurify 成熟度高，但仍需关注 SVG 命名空间下的 XSS 向量（如 `<use>` 标签的 `xlink:href`）。需配置白名单为"仅允许 HTML 结构标签 + SVG 图形标签（circle/rect/line/path/text/g）+ MathML"，排除 `<foreignObject>`、`<use>`、事件属性
- **配置切换的原子性**：`output-format` 切换后前端渲染器不匹配（如后端切 `html-svg` 但前端未更新 → `marked` 尝试渲染 HTML 字符串 prod 出乱码）。通过 API 响应携带 `outputFormat` 字段 + 前端按字段选择渲染器来规避
- **LLMGateway 调用次数增加**：每次问答从 1 次 LLM 调用（仅分析）变为 2 次（意图分类 + 分析），配额消耗翻倍。需要评估成本影响，可在 DESIGN 阶段考虑"意图分类用轻量模型（如 Haiku）降成本"

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。