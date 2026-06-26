# ADR-026: HTML+SVG 输出格式与配置驱动切换

## Context

`intelligent-qa` 的 LLM 输出格式为 Markdown（ADR-011 D2），前端用 `marked` 渲染。`llm-intent-recognition` CHANGE 要求输出改为 HTML+SVG（含数据图表、知识图谱子图、数学公式），同时**保留 Markdown 实现路径**以便出问题时一键回滚。

核心问题：
1. 如何在两种格式间切换而不引入过度工程？
2. Prompt 模板如何组织才能避免内容重复和分支爆炸？
3. HTML+SVG 输出的校验规则应该是什么？
4. 如何确保 LLM 生成的 SVG 在浏览器中可渲染？

## Decision

### D1: yml 配置驱动切换，非 Strategy 接口

`application-dev.yml` 新增：

```yaml
query:
  output-format: html-svg  # html-svg | markdown
```

`QueryProperties` 新增：

```java
@Data
public static class OutputFormat {
    /** 输出格式: html-svg (默认) | markdown (回滚) */
    private String format = "html-svg";
}
```

不选 Strategy 接口（`OutputFormatStrategy` + `HtmlSvgStrategy`/`MarkdownStrategy`）的理由：
- 仅 2 种格式，Strategy 模式至少 3 个文件（接口 + 2 实现），过度设计
- 格式切换的核心差异仅 2 处：**模板选择**和**校验规则**，用 if-else 简洁
- 与现有 `query.retry.max-retries`、`query.pruning.weak-threshold` 等配置管理模式一致
- 新增第 3 种格式时再重构为 Strategy，届时成本可控

`QueryServiceImpl` 中的格式感知逻辑：

```java
// 伪代码
private String callLlmWithRetry(QueryIntent intent, String systemPrompt, String userMessage) {
    String format = queryProperties.getOutput().getFormat();
    // 按格式选择校验规则
    Predicate<String> validator = "markdown".equals(format)
            ? this::validateMarkdownResponse
            : this::validateHtmlSvgResponse;
    // 按格式注入重试提示
    String retryHint = "markdown".equals(format)
            ? "请务必以 ## 标题开头..."
            : "请务必以 HTML 标签开头，包含至少一个 <svg> 元素...";
    // ... 重试循环（与现有逻辑一致）
}
```

### D2: Prompt 模板文件命名扩展

`PromptTemplateService.buildPrompt()` 扩展为接受 format 参数：

```java
// 伪代码
public PromptPair buildPrompt(QueryIntent intent, Map<String, String> variables, String format) {
    String prefix = intent.name().toLowerCase().replace('_', '-');
    String systemTemplate = loadTemplateWithFormat(prefix + "-system", format);
    String userTemplate = loadTemplateWithFormat(prefix + "-user", format);
    // ...
}

private String loadTemplateWithFormat(String baseName, String format) {
    if (!"markdown".equals(format)) {
        String formattedPath = baseName + "-" + format;
        // 先尝试带格式后缀的文件，不存在则 fallback 到 baseName
    }
    // markdown 格式直接用 baseName（向后兼容现有模板文件）
}
```

模板文件清单（本次新增 `*` 标记）：

```
prompts/
├── student-diagnosis-system.md       # Markdown system（保留不变）
├── student-diagnosis-user.md         # Markdown user（保留不变）
├── student-diagnosis-system-html.md  # * NEW: HTML+SVG system
├── student-diagnosis-user-html.md    # * NEW: HTML+SVG user
└── intent-classification-system.md   # * NEW: 意图分类 system（无 format 区分）
```

### D3: HTML+SVG System Prompt 设计要点

```
你是一位教育诊断专家。根据提供的学生子图数据，生成 HTML+SVG 格式的诊断报告。

## 输出格式约束
- 必须以 HTML 标签开头（<h2>、<div>、<table> 之一）
- 禁止出现 Markdown 标记（##、- 、1. 等）
- 禁止前导语（"根据数据……"、"以下是分析报告……"）
- 必须包含至少 1 个 <svg> 元素

## SVG 约束
- 每个 <svg> 必须含 xmlns="http://www.w3.org/2000/svg" 和 viewBox
- 柱状图: 柱宽≥30px, 柱间距≥10px, 字体≥12px
- 依赖图: 节点 <circle> r≥20, 边 <line> stroke-width≥2
- 数学公式: 使用 MathML 或纯 SVG text+path
- 最大画布 800×600, 最多 10 个数据点
- 不使用外部 CSS/JS/字体

## 结构要求
<h2>学生姓名 — 学科薄弱点诊断</h2>
<h3>学生基本信息</h3>
<table>...</table>
<h3>薄弱知识点（掌握度分布图）</h3>
<svg>...柱状图...</svg>
<h3>前置依赖链（知识点关系图）</h3>
<svg>...节点-边拓扑图...</svg>
<h3>根因分析与建议</h3>
<p>...文本分析...</p>
```

### D4: HTML+SVG 校验规则

```java
// 伪代码
boolean validateHtmlSvgResponse(String response) {
    if (response == null || response.isBlank()) return false;
    String trimmed = response.trim();

    // 1. 必须以 HTML 标签开头（非 Markdown ## 或纯文本）
    if (!trimmed.startsWith("<")) return false;

    // 2. 必须包含至少 1 个 <svg> 元素
    if (!trimmed.contains("<svg")) return false;

    // 3. SVG 必须含 xmlns（确保标准兼容）
    String lower = trimmed.toLowerCase();
    if (lower.contains("<svg") && !lower.contains("xmlns")) return false;

    // 4. 不含 Markdown 特征（表明 LLM 未理解格式切换）
    if (trimmed.startsWith("##") || trimmed.startsWith("# ")) return false;

    // 5. 不含常见前导语（与 Markdown 校验一致）
    String first100 = lower.substring(0, Math.min(100, lower.length()));
    if (first100.contains("根据提供") || first100.contains("以下是")) return false;

    return true;
}
```

校验失败处理：与现有 Markdown 校验一致——重试 ≤2 次，每次在 system prompt 末尾追加格式修正提示 → 重试耗尽则 WARN 降级返回原始文本（AC-5）。

不选 Jsoup 等 HTML 解析器的理由：
- 校验目的仅是过滤"明显不是 HTML"的响应，非验证 HTML 结构正确性
- 浏览器对不完美 HTML 有容错渲染（标签未闭合等可自行修复），严格校验无必要
- 额外依赖 + 性能开销，而收益仅是提高边界案例的拦截率

### D5: 前端渲染与安全

`HtmlSvgViewer.vue` 组件核心逻辑：

```typescript
// 伪代码
import DOMPurify from 'dompurify'

const ALLOWED_TAGS = [
  // HTML 结构
  'h2', 'h3', 'h4', 'p', 'div', 'span', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'ul', 'ol', 'li', 'strong', 'em', 'br', 'hr',
  // SVG 图形
  'svg', 'g', 'circle', 'ellipse', 'rect', 'line', 'path', 'polygon', 'polyline',
  'text', 'tspan', 'defs', 'linearGradient', 'stop', 'title', 'desc',
  // MathML
  'math', 'mi', 'mo', 'mn', 'mrow', 'msup', 'mfrac', 'msqrt', 'mroot'
]

const purifyConfig = {
  ALLOWED_TAGS,
  ALLOWED_ATTR: ['class', 'id', 'style', 'd', 'cx', 'cy', 'r', 'x', 'y', 'width',
    'height', 'viewBox', 'xmlns', 'fill', 'stroke', 'stroke-width', 'text-anchor',
    'dominant-baseline', 'font-size', 'font-family', 'transform', 'points',
    'x1', 'y1', 'x2', 'y2', 'rx', 'ry', 'offset', 'stop-color', 'marker-end',
    'text-decoration', 'href', 'textLength', 'lengthAdjust'
  ],
  // 禁止
  FORBID_TAGS: ['script', 'foreignObject', 'iframe', 'object', 'embed', 'use'],
  FORBID_ATTR: ['onclick', 'onload', 'onerror', 'xlink:href']
}

const sanitized = DOMPurify.sanitize(props.content, purifyConfig)
```

关键安全决策：
- **禁止** `<foreignObject>`（可嵌入 HTML → 绕过净化）、`<use>`（`xlink:href` 可引用外部资源）、事件属性
- **白名单**覆盖 SVG 1.1 常用图形子集 + MathML 表达子集，新增标签需审慎评估安全影响
- 不采用 IFrame sandbox：隔离性强但无法继承父页面 CSS 变量（`var(--color-brand)` 等），视觉效果割裂

## Consequences

### 正面
- yml 配置切换 + 重启生效，运维简单（AC-6）
- 双模板并存，Markdown 路径零改动（真正的一键回滚）
- DOMPurify 白名单阻断 XSS，安全边界清晰（AC-7）
- HTML+SVG 校验规则简单可测（4 条规则，无外部依赖）

### 负面
- 模板文件数量翻倍（每种意图 2 套 × N 种意图），当前仅 1 种意图均可接受
- DOMPurify 白名单需维护，LLM 使用新的 SVG 标签时需手动加白
- HTML+SVG 模式的 token 消耗可能显著高于 Markdown（SVG markup 冗余），通过 prompt 约束控制
- 校验规则简单可能放过部分"格式异常但浏览器可渲染"的边界情况，但 AC-5 已允许降级返回

### 风险缓解
- 模板文件名约定在 `PromptTemplateService` 集中管理，新增格式有明确的扩展路径
- `query.output-format` 配置值在 `QueryProperties` 中用 `@PostConstruct` 校验，非法值拒绝启动
- 校验失败时记录 LLM 原始响应前 200 字符到 WARN 日志，便于排查格式漂移
- HTML+SVG prompt 的 SVG 约束（尺寸/间距/数据点上限）可独立调优，不依赖代码变更