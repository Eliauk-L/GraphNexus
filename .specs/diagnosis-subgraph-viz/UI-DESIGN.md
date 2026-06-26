---
name: GraphNexus 诊断子图可视化
description: 极简数据可视化内嵌 — 延续 Linear/Vercel/Stripe 的克制基因，掌握度四档暖色梯度，平面卡片 + hairline 边框，纯 SVG 图表零依赖

colors:
  brand: "oklch(0.55 0.18 250)"
  brand-deep: "oklch(0.48 0.18 250)"
  bg: "oklch(0.985 0.002 95)"
  surface: "oklch(1 0 0)"
  text-primary: "oklch(0.15 0.005 95)"
  text-secondary: "oklch(0.45 0.005 95)"
  text-tertiary: "oklch(0.65 0.005 95)"
  border: "oklch(0.90 0.005 95)"
  # 新增 — 掌握度色阶
  mastery-red: "oklch(0.50 0.20 25)"       # weight < 0.4，复用 --color-error
  mastery-orange: "oklch(0.62 0.16 65)"    # 0.4 ≤ weight < 0.6
  mastery-yellow: "oklch(0.72 0.12 100)"   # 0.6 ≤ weight < 0.8
  mastery-green: "oklch(0.55 0.15 145)"    # weight ≥ 0.8，复用 --color-success
  mastery-gray: "oklch(0.65 0.005 95)"     # 无 MASTERS 节点，复用 --color-text-tertiary 色相

typography:
  display:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "clamp(1.75rem, 4vw, 2.5rem)"
    fontWeight: 700
    lineHeight: 1.15
  headline:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.3
  title:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "1.125rem"
    fontWeight: 500
    lineHeight: 1.4
  body:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  supporting:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    textTransform: uppercase
    letterSpacing: "0.05em"
    lineHeight: 1.3
  micro-label:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 500
    textTransform: uppercase
    letterSpacing: "0.06em"
    lineHeight: 1.2
  mono:
    fontFamily: "'JetBrains Mono', 'SF Mono', 'Cascadia Code', monospace"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5

spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  2xl: "48px"
  3xl: "80px"
  4xl: "120px"

rounded:
  none: "0"
  sm: "4px"
  md: "8px"
  lg: "12px"
  full: "9999px"

motion:
  ease-out: "cubic-bezier(0.16, 1, 0.3, 1)"
  ease-out-quint: "cubic-bezier(0.22, 1, 0.36, 1)"
  duration-fast: "150ms"
  duration-base: "300ms"
  duration-slow: "600ms"

shadow:
  hover-lift: "0 2px 8px rgba(0, 0, 0, 0.08)"
  card-lifted: "0 20px 60px rgba(0, 0, 0, 0.12)"
  accent-glow: "0 20px 60px var(--color-brand-veil)"
---

# UI Design: 诊断子图可视化

## 0. 视觉语汇对齐（brownfield · 步骤 1.5 产出）

### 0.1 观察报告（代码为源）

- **Token 源**：`frontend/src/assets/tokens.css`（所有 CSS custom properties 在 `:root`）+ `global.css`（9 级 typography hierarchy）
- **主色实际比例**：品牌蓝 `oklch(0.55 0.18 250)` 约占 3%，仅在 primary button、链接、focus ring 使用。无第二强调色（除语义色 success/warning/error）
- **中性色**：背景 `oklch(0.985 0.002 95)` 偏米白（chroma=0.002），surface 纯白 `oklch(1 0 0)`，文字三级梯度（L=0.15/0.45/0.65，全部 chroma=0.005）。**不用纯黑纯白**
- **hover / focus 反馈**：统一 `transform: translateY(-1px)` + 颜色变深，duration 150ms，`cubic-bezier(0.16, 1, 0.3, 1)`。focus ring 用 `--color-brand-veil` 3px offset 2px
- **动效语言**：2 条缓动曲线（ease-out + ease-out-quint），3 档 duration（150/300/600ms）。slide Transition 统一用 cubic-bezier(0.16, 1, 0.3, 1)。`@keyframes` 仅 3 处（spin × 2 + status-pulse × 1）。**prefers-reduced-motion 已尊重**
- **elevation 层级**：仅 2 级（`shadow-hover-lift` 8px blur + `shadow-card-lifted` 60px blur）。卡片 at rest 是**平的**（1px hairline 边框，无阴影）。无多层纵深
- **卡片密度 / rounded**：稀 — padding 16px（`--spacing-md`），1px 实线边框，rounded 8px（`--rounded-md`），hover 不 lift。间距用非线性 scale（4/8/16/24/32/48/80/120px）
- **图标库**：lucide-vue v1.20，stroke 风格，常用 size 16px，stroke-width 默认
- **文案调性**：工程向、动词为主。按钮写"发送"（非"立即开始分析"），placeholder 口语化中性

### 0.2 用户校准结论

- 用户确认：7 项观察全部正确，无修正
- 本次全部沿用现有视觉语汇，新增元素（子图组件、详情面板、趋势图）在视觉上与原有 UI "无法区分"

### 0.3 应用策略

- **沿用**：全部 tokens.css 变量（颜色/间距/圆角/阴影/动效/字体）、9 级 typography hierarchy、卡片（1px border + 8px rounded + 16px padding + at rest 平面）、hover/focus 模式、slide Transition 动画
- **延伸**：新增 3 个 CSS 变量用于掌握度色阶（`--mastery-orange`、`--mastery-yellow` + 复用已有的 `--color-error`/`--color-success`/`--color-text-tertiary` 色相）
- **打破**：无

---

## 1. 美学北极星

**克制数据可视化** — 延续 GraphNexus 的极简基因：平面卡片、hairline 边框、单一品牌蓝。子图可视化不加任何"dashboard 味"——不要 glow、不要渐变背景、不要彩色侧条。节点颜色仅传达掌握度（功能需要），图例和标签用 `supporting` 字号安静存在。图表是信息载体，不是装饰。

### v0 确认摸路

- **已确认的假设**（用户说 go）：
  - 子图容器用 `BaseCard` 包裹（1px border + 8px rounded + 16px padding）
  - 详情面板复用 slide Transition 模式（fixed right 0, 320px 宽, shadow-card-lifted）
  - 趋势图为纯 SVG `<path>` 折线 + `<circle>` 数据点，色调用 brand blue，面积不填充
  - 图例在子图右上角横向排列，`supporting` 字号
  - 节点标签字体用 `var(--font-display)`，字号 11px（micro-label 级别）
  - 暂不支持 dark mode（全站未支持）
  - 子图加载中用 skeleton 占位（品牌色调中性灰条）
- **用户指出的偏差**：无

---

## 2. 4 个决策问题

- **目的**：在学情诊断结果页中，让教师一眼看到学生的知识薄弱点图结构 + 掌握度量化信息，无需阅读 LLM 文本即可快速定位最需关注的知识点。核心动作：看子图 → 识别红/橙色节点 → 点击查看趋势
- **调性**：**极简（Minimal）** — 已由 `frontend-ui` CHANGE 锁定。理由：GraphNexus 是教育数据工具，视觉应是中性克制的信息载体而非营销页。极简让数据的 pattern（红 vs 绿、大 vs 小、升 vs 降）自然浮现，不被装饰干扰
- **约束**：Vue 3 + Naive UI + G6 v5（已锁定）、纯 SVG 子图（ADR-034）、桌面 ≥1280px、色觉无障碍（颜色+大小双编码）、中文界面
- **差异化**：不同于传统 dashboard 式学情面板（红绿大卡 + 进度条 + 排名表），GraphNexus 的诊断子图是"图结构 + 时间序列"的双重信息编码——同一张图里看到知识点的结构依赖关系和历次考试演变

---

## 3. 颜色系统

### 主色（沿用）

- **品牌蓝** `oklch(0.55 0.18 250)`：Student 节点填充、链接、选中态边框、趋势图折线。**绝不用于**大面积背景或装饰
- **品牌蓝深** `oklch(0.48 0.18 250)`：hover/active 加深

### 掌握度色阶（新增，tokens.css 中补充 2 个变量）

| 变量名 | OKLCH | 触发条件 | 用途 |
|--------|-------|---------|------|
| `--mastery-red` | `oklch(0.50 0.20 25)` | weight < 0.4 | KP 节点填充。复用 `--color-error` 色值 |
| `--mastery-orange` | `oklch(0.62 0.16 65)` | 0.4 ≤ weight < 0.6 | KP 节点填充。**新增变量** |
| `--mastery-yellow` | `oklch(0.72 0.12 100)` | 0.6 ≤ weight < 0.8 | KP 节点填充。**新增变量** |
| `--mastery-green` | `oklch(0.55 0.15 145)` | weight ≥ 0.8 | KP 节点填充。复用 `--color-success` 色值 |
| `--mastery-gray` | `oklch(0.65 0.005 95)` | weight 不存在（无 MASTERS） | 前置依赖 KP 节点填充。复用 `--color-text-tertiary` 色相 |

### 中性色（全部沿用 tokens.css）

- 背景 `oklch(0.985 0.002 95)`：页面底色（米白，chroma=0.002）
- surface `oklch(1 0 0)`：子图容器卡片、详情面板
- 文字三级：primary `oklch(0.15 0.005 95)` / secondary `oklch(0.45 0.005 95)` / tertiary `oklch(0.65 0.005 95)`
- 边框 `oklch(0.90 0.005 95)`：容器/面板分隔线

### 规则

- **The One Voice Rule**：品牌蓝是唯一强调色。掌握度色阶是**功能色**（编码数据），不是装饰性"第二强调色"
- **The Tinted Neutral Rule**：所有中性色 chroma ≤ 0.005，不用纯灰
- **静态 at rest 不发光**：节点不设 SVG filter drop-shadow，不设 glow

---

## 4. 字体系统

全部沿用 `tokens.css` 的 9 级体系，**不引入新字体**：

- **Display**：DM Sans + 中文字体栈 — **为什么不用 Inter/Roboto**：DM Sans 几何感更强，与 Linear 式极简匹配；Inter 在中英文混排时视觉重量偏轻
- **Body**：PingFang SC + 中文字体栈 — 中文阅读舒适度最优，macOS 原生渲染
- **Mono**：JetBrains Mono — 代码/数据数值

### 诊断子图专用层次

| 角色 | 字体 token | 字号 | 字重 | 用途 |
|------|-----------|------|------|------|
| 子图标题 | `title` | 1.125rem | 500 | "知识结构子图" 卡片标题 |
| 节点标签 | `micro-label` | 11px (0.6875rem) | 500 | SVG `<text>` 节点名称 |
| 图例文字 | `supporting` | 0.875rem | 400 | 颜色→掌握度 映射说明 |
| 面板标题 | `title` | 1.125rem | 500 | 知识点名称 |
| 掌握度数值 | `mono` | 0.875rem | 400 | weight 值展示 |
| 考试记录 | `supporting` | 0.875rem | 400 | 日期 + 得分率列表 |
| 趋势图标注 | `micro-label` | 0.6875rem | 500 | 数据点数值 |

---

## 5. 间距 & 圆角 & 动效

全部沿用 `tokens.css` frontmatter，**不在 token 之外引入新数值**。

### 间距

子图容器 padding：`--spacing-md`（16px）。子图内容 margin-top：`--spacing-lg`（24px，与 LLM 文本报告分隔）。详情面板内 section 间距：`--spacing-md`（16px）。

### 圆角

子图容器：`--rounded-md`（8px）。详情面板：`--rounded-none`（全高侧边栏）。趋势图 SVG：无背景、无圆角。

### 动效

| 元素 | 触发 | 效果 | duration | easing |
|------|------|------|----------|--------|
| 详情面板 | 点击节点 / 关闭 | slide-in-right / slide-out-right | 300ms | `cubic-bezier(0.16, 1, 0.3, 1)` |
| 节点 hover | mouseenter | scale(1.1) + stroke 加粗 | 150ms | ease-out |
| 子图加载 | 数据未就绪 | skeleton shimmer | — | CSS animation |
| 趋势图 | 面板打开 | 折线 draw（stroke-dasharray 动画）| 600ms | ease-out-quint |

---

## 6. 关键组件规约

### DiagnosisSubgraph.vue — 诊断子图

- **容器**: `<BaseCard title="知识结构子图">`（1px border + 8px rounded + 16px padding + 平面 at rest）
- **画布**: `<svg viewBox="0 0 600 400" width="100%" height="400">`，背景透明
- **节点**:
  - `<circle>` 元素，`r` 按 D4 公式 `12 + weight * 28`（无 MASTERS 节点 r=16）
  - `fill` 按 weight 四档色阶映射
  - `stroke`: none（at rest），`stroke: var(--color-brand) stroke-width: 2`（hover/选中）
  - 标签: `<text>` 元素，`font-family: var(--font-display)`，`font-size: 11px`，`fill: var(--color-text-primary)`，位置在 circle 下方
- **边**:
  - `<line>` 元素，MASTERS 边 `stroke: var(--mastery-gray)` + `stroke-width: 1.5 + weight`；PREREQUISITE_OF 边 `stroke: var(--color-border)` + `stroke-dasharray: 4,4` + `stroke-width: 1`
- **图例**: 右上角 `<foreignObject>` 或独立 `<div>`（flex row, gap 8px），5 个色块 + 文字标签，`supporting` 字号
- **布局**: 力导向模拟（≤50 迭代），Student 固定在 (300, 60)
- **占位**: loading → skeleton 灰色矩形（`--color-border` 色，# 比例同画布）；error → "子图数据加载失败" 文本居中（`supporting` + `--color-text-tertiary`）

### DiagnosisNodeDetail.vue — 节点详情滑出面板

- **容器**: `<Transition name="slide">` + `v-if="visible"`，fixed right 0 top 0 bottom 0，width 320px，z-index 40
- **at rest**: `background: var(--color-surface)` + `border-left: 1px solid var(--color-border)` + `box-shadow: var(--shadow-card-lifted)` + `padding: var(--spacing-lg)`（24px）
- **标题行**: flex row，space-between。"知识点名称" `title` 字号 + 关闭按钮（lucide `X` icon 16px，`--color-text-tertiary` → hover `--color-text-primary`）
- **分隔线**: `<div>` height 1px `background: var(--color-border)` margin `--spacing-md` 0
- **Section「掌握度」**: label "当前掌握度"（`label` 样式）+ 数值 weight（`mono` 字号）+ 色块圆点（与节点 fill 同色）
- **Section「考试趋势」**: 内含 `<ExamTrendChart>`，margin-bottom `--spacing-md`
- **Section「历次考试」**: 表格形式（但不用 `<table>`——用 div flex column gap 4px 更极简）。每行 = 日期（`supporting` + `--color-text-secondary`）+ 得分率（`mono` + `--color-text-primary`）

### ExamTrendChart.vue — 微型趋势折线图

- **画布**: `<svg viewBox="0 0 280 140" width="280" height="140">`，背景透明
- **坐标轴**: 仅 y=0 处一条细线 `stroke: var(--color-border) stroke-width: 1`。无 x 轴刻度线，无网格线
- **折线**: `<polyline>` 或 `<path>`，`stroke: var(--color-brand)`，`stroke-width: 2`，`fill: none`（不填充面积），`stroke-linejoin: round`，`stroke-linecap: round`
- **数据点**: `<circle>` r=3，`fill: var(--color-surface)`，`stroke: var(--color-brand)`，`stroke-width: 2`
- **标注**: 每个数据点上方 6px 处 `<text>` 标得分率（如 `0.58`），`font-family: var(--font-mono)`，`font-size: 9px`
- **x 轴标签**: 数据点下方 `<text>` 标日期缩写（如 `9月`），`font-family: var(--font-display)`，`font-size: 9px`，`fill: var(--color-text-tertiary)`
- **空状态**: ≥2 个数据点才渲染图表；<2 个时返回空 `<div>`（由父组件控制是否展示）
- **入场动画**: 折线 stroke-dasharray + stroke-dashoffset 动画（600ms ease-out-quint），仅在首次可见时播放

---

## 7. Do's and Don'ts（本项目特定）

### Do

- 掌握度用颜色+大小**双编码**传递（色觉障碍用户可通过节点大小和详情面板文字获取等同信息）
- 节点标签使用 SVG `<text>` 的 `textContent`（自动转义），不使用 `v-html`
- 图例始终可见，标注颜色→掌握度阈值的明确映射
- 所有新增 token 变量写入 `tokens.css`（不散落在组件 `<style scoped>` 中）
- 趋势图数据点数值标注显式显示，不依赖 hover tooltip

### Don't

- **禁止**在子图中使用 G6 v5（ADR-034 已锁定纯 SVG）
- **禁止**子图容器使用 drop shadow 或渐变背景（at rest 平面 = 极简基因）
- **禁止**在节点上使用 SVG filter（glow / blur）— 保持平面
- **禁止**趋势图填充折线下方面积（极简图表 = 仅线段 + 点）
- **禁止**详情面板内嵌套卡片（卡片嵌套卡片 = anti-pattern）
- **禁止**在子图加载时显示彩色 spinner（用品牌色调 neutral 的 skeleton）
- **禁止**节点标签使用 emoji
- **禁止**趋势图 y 轴不从 0 开始（得分率 0~1 全量程展示）
- **禁止**详情面板宽度 > 320px（保持与 `NodeDetailPanel.vue` 一致）

---

## 8. 占位符策略

| 缺的东西 | 本项目有什么？ | 缺时用什么占位 | 禁什么 |
|---|---|---|---|
| 图标 | lucide-vue（X/chevron 等） | lucide 图标，不缺 | emoji 充图标 |
| 子图数据 | API `getPrunedSubgraph()` | 灰色矩形 skeleton（`--color-border` 色填充，画布比例 600×400） | 彩色 spinner / 假图 |
| 趋势图数据 | MASTERS description JSON → `details[]` | `<2 数据点时整段隐藏，不显示空白图` | 编造数据点 |
| 节点名称 | Neo4j `name` 属性 | 若缺失则显示 `[未命名]` + `--color-text-tertiary` | 编造名称 |
| 掌握度数值 | MASTERS `weight` | 若缺失则详情面板显示"融合数据不可用" | 编造 0.5 默认值 |
| 考试记录 | `examHistory` JSON | `JSON.parse` 失败 → "数据格式异常" | 吞错误 + 静默不展示 |

---

## 9. 反 AI-slop 自检结果

逐条对照 `ui-anti-patterns.md` 强制禁忌：

- [x] 字体类禁忌：**未命中**。沿用 DM Sans + PingFang SC，非 Inter/Roboto/Arial/Space Grotesk
- [x] 颜色类禁忌：**未命中**。不用纯黑纯白、不用紫色渐变、不用霓虹青、只有一个强调色、不用 gradient text、掌握度色阶是功能色非装饰
- [x] 阴影类禁忌：**未命中**。卡片 at rest 平面（hairline border 无阴影）、shadow alpha ≤ 0.12、无装饰性彩色阴影、无 inset+glow 同时用
- [x] 边框类禁忌：**未命中**。无彩色侧条（详情面板 border-left 1px solid `--color-border` 是结构线非彩色侧条）、无渐变边框、无玻璃拟态
- [x] 动效类禁忌：**未命中**。无 bounce/elastic、仅动 transform/opacity、prefers-reduced-motion 已尊重
- [x] 布局类禁忌：**未命中**。无卡片嵌套卡片、非线性 spacing scale、无默认 dark mode
- [x] 文案类禁忌：**未命中**。无空话/hedging、无 Lorem ipsum
- [x] 组件类禁忌：**未命中**。无 modal（侧边面板有 escape 关闭 + × 按钮）、skeleton 用品牌色调 neutral 而非纯灰

**命中条目**：无。

---

## 10. 触发任务

下一步进入 `@flow-kit/prompts/3-task.md` 阶段时，把以下作为**第一批 UI 任务**：

- T-UI-01：`tokens.css` 补充 `--mastery-orange`、`--mastery-yellow` 两个 CSS 变量
- T-UI-02：实现 `DiagnosisSubgraph.vue`（纯 SVG 子图渲染 + 力导向布局 + 颜色/大小映射 + 图例）
- T-UI-03：实现 `DiagnosisNodeDetail.vue`（slide 滑出面板 + 考试历史列表）
- T-UI-04：实现 `ExamTrendChart.vue`（纯 SVG 微型折线图 + 入场动画）
- T-UI-05：`IntelligentQAPage.vue` 集成子图组件（LLM 报告下方插入 DiagnosisSubgraph + 状态连线）
- T-UI-06：`queryStore.ts` 新增 `loadSubgraph()` action + `subgraphData`/`subgraphLoading` 状态