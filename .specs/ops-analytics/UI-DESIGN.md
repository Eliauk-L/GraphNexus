---
name: GraphNexus 运营仪表盘
description: 极简数据仪表盘，延续 GraphNexus 既有的冷蓝单主色 + 暖底中性 + 平面卡片 + 工程向文案。图表色板从 brand 派生 6 色环。

colors:
  brand: "oklch(0.55 0.18 250)"
  brand-deep: "oklch(0.48 0.18 250)"
  brand-veil: "oklch(0.55 0.18 250 / 0.12)"
  bg: "oklch(0.985 0.002 95)"
  surface: "oklch(1 0 0)"
  text-primary: "oklch(0.15 0.005 95)"
  text-secondary: "oklch(0.45 0.005 95)"
  text-tertiary: "oklch(0.65 0.005 95)"
  border: "oklch(0.90 0.005 95)"
  success: "oklch(0.55 0.15 145)"
  warning: "oklch(0.65 0.15 85)"
  error: "oklch(0.50 0.20 25)"

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
  micro-label:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 500
    textTransform: uppercase
    letterSpacing: "0.06em"
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

chart-palette:
  c0: "oklch(0.55 0.18 250)"   # brand
  c1: "oklch(0.60 0.15 180)"   # teal
  c2: "oklch(0.60 0.15 145)"   # green
  c3: "oklch(0.65 0.15 85)"    # amber
  c4: "oklch(0.55 0.18 30)"    # coral
  c5: "oklch(0.50 0.12 300)"   # violet
  neutral: "oklch(0.75 0.005 95)"  # light gray for "other" slice
---

# UI Design: GraphNexus 运营仪表盘

## 0. 视觉语汇对齐（brownfield）

> 来自 2a 步骤 1.5。新增元素目标：与原有 UI 在视觉上"无法区分"。

### 0.1 观察报告（代码为源）

- **Token 源**：[tokens.css](frontend/src/assets/tokens.css) + [global.css](frontend/src/assets/global.css)，`:root` 中定义全部 CSS 变量，无 Tailwind
- **主色实际比例**：品牌蓝 `oklch(0.55 0.18 250)` 占可视面积 ≈ 2%，仅在品牌标识、链接、激活指示条、`:focus-visible` 环使用。**不在卡片背景和边框上出现**
- **中性色**：背景 `oklch(0.985 0.002 95)` 偏米白（chroma 0.002，hue 95 暖底），文字 `oklch(0.15 0.005 95)` 近黑但不纯黑，tertiary `oklch(0.65 0.005 95)` 用于辅助信息。侧边栏深色 `oklch(0.12 0.01 250)`
- **hover / focus 反馈**：颜色/边框变化 + `transition: <prop> var(--duration-fast) var(--ease-out)`（150ms cubic-bezier(0.16, 1, 0.3, 1)）。**无 transform 微抬，无阴影出现/消失**。聚焦环 `outline: 3px solid var(--color-brand-veil) + offset 2px`
- **动效语言**：纯 CSS transitions，仅 StatusBadge/StatusPipeline 有 @keyframes（spin/pulse）。三档时长 + 两条缓动曲线。`prefers-reduced-motion` 归零处理已做
- **elevation 层级**：3 级阴影（hover-lift / card-lifted / accent-glow），日常卡片 **at rest 无阴影**
- **卡片密度/rounded**：稀 — padding 24px、border 1px solid、圆角 8px（`--rounded-lg`）、背景白 surface。无渐变、无装饰线
- **图标库**：lucide-vue，stroke-width 默认 2，14~20px 混用。空状态用大号图标 + 灰色文字
- **文案调性**：中文工程向，菜单名直接描述功能（"教材管理""学情诊断"），按钮动词为主（"上传""创建""保存"）

### 0.2 用户校准结论

- ✅ 用户确认观察报告无误
- ✅ 本次确定**全部沿用**既有视觉语汇，不引入新模式

### 0.3 应用策略

- **沿用**：全部 design tokens（颜色 / 字体 / 间距 / 圆角 / 动效 / 阴影）— 运营仪表盘不做任何 token 层变更
- **延伸**：新增 ECharts 图表色板 6 色（从 brand hue 250 出发，等距分布色环），确保图表配色与 brand 体系协调；新增 `--color-trend-up: oklch(0.55 0.15 145)` 和 `--color-trend-down: oklch(0.50 0.20 25)` 两个微型语义色用于趋势箭头
- **打破**：无

---

## 1. 美学北极星

> **极简数据仪表盘**：冷蓝单主色 + 暖底中性 + 平面卡片 + 克制图表。运营人员面对的是一个"干净的数据报告"，不是一个"炫技的 Dashboard"。三天后用户会形容它"清晰""不吵""一眼看到重点"。

### v0 确认摸路

- **已确认的假设**（用户明说 go）：
  - 时间粒度切换用 NButtonGroup（天/周/月），默认选中"月"
  - 数字卡片带环比变化箭头，数据来源"比上一周期"
  - 图表区域卡片沿用既有 BaseCard 模式（白色底、1px 边框、8px 圆角、24px 内边距）
  - 图谱分布学科筛选器默认"全部学科"，切换联动柱状图
  - 实时卡片标注"当前"，历史图表标注"历史趋势"
  - 页面纵向滚动，三区顺序排列
  - ECharts 配色从 brand 派生，不使用默认 ECharts 主题
  - 无 dark mode
- **用户指出的偏差**：无

---

## 2. 4 个决策问题

- **目的**：ADMIN 和 OPS_MANAGER 查看系统运营数据（使用量/文档处理量/图谱分布），核心动作为**扫读数字 → 看趋势图表 → 切换粒度/学科**。非频繁操作页面，更接近"周期性查阅的报告"
- **调性**：极简（Minimal）— 沿用 CHANGE.md 锁定项，参考 Linear/Vercel/Stripe 的数据展示风格
- **约束**：Vue 3 + Naive UI 组件库 + ECharts 图表；桌面端 ≥ 1280px；数字来自后端 API 真数据；不支持移动端；`prefers-reduced-motion` 控制动画
- **差异化**：**"只有数据，没有装饰"**——与典型 SaaS dashboard（渐变背景、彩色侧条、hero metric、装饰图标）形成反差。卡片平面无阴影 at rest，图表去网格线保留轴线，数字大字 + mono 字体突出

---

## 3. 颜色系统

### 图表色板（本次新增）

运营仪表盘引入 6 色图表色板，色相从 brand hue（250）出发等距分布（~52° 步长），饱和度和亮度保持统一档位：

| Token | OKLCH | 用途 |
|-------|-------|------|
| `--chart-c0` | `oklch(0.55 0.18 250)` | 主色 = brand，用于最核心数据系列 |
| `--chart-c1` | `oklch(0.58 0.15 198)` | 青色，第二数据系列 |
| `--chart-c2` | `oklch(0.58 0.15 146)` | 绿色，第三数据系列 |
| `--chart-c3` | `oklch(0.62 0.15 94)` | 琥珀，第四数据系列 |
| `--chart-c4` | `oklch(0.58 0.18 42)` | 珊瑚，第五数据系列 |
| `--chart-c5` | `oklch(0.52 0.14 290)` | 紫罗兰，第六数据系列 |
| `--chart-neutral` | `oklch(0.78 0.005 95)` | 浅灰，"其他"切片用 |

### 趋势指示色（本次新增）

| Token | OKLCH | 用途 |
|-------|-------|------|
| `--color-trend-up` | `oklch(0.55 0.15 145)` | ↑ 上升箭头（= success hue） |
| `--color-trend-down` | `oklch(0.50 0.20 25)` | ↓ 下降箭头（= error hue） |
| `--color-trend-flat` | `oklch(0.65 0.005 95)` | → 持平 = tertiary text |

### 现有色沿用不变

所有 `--color-brand` / `--color-bg` / `--color-surface` / `--color-text-*` / `--color-border` / `--color-sidebar-*` 保持 tokens.css 原值不变。

---

## 4. 字体系统

**完全沿用** [global.css](frontend/src/assets/global.css) 的 9 级层次，不新增字体或修改字号。

运营仪表盘特有的字体使用约定：

| 上下文 | 使用层级 | 示例 |
|--------|---------|------|
| 区域标题（"系统使用量"） | `.headline` | 1.5rem / 600 / DM Sans |
| 图表标题 | `.title` | 1.125rem / 500 / DM Sans |
| 数字卡片的值 | `.display`（缩小） | clamp(1.5rem, 3vw, 2rem) / 700 / DM Sans |
| 数字卡片的标签 | `.micro-label` | 0.6875rem / 500 / DM Sans / uppercase |
| 变化箭头文字（+12%） | `.supporting` | 0.875rem / 400 / PingFang SC |
| 图表轴标签 | `.supporting` | 0.875rem / 400 / PingFang SC |
| "当前"/"历史趋势"标注 | `.micro-label` | 0.6875rem / 500 / DM Sans |

---

## 5. 间距 & 圆角 & 动效

**完全沿用** [tokens.css](frontend/src/assets/tokens.css) 的既有权值，不新增。

运营仪表盘特有的布局约定：

- 三大区域之间间距：`var(--spacing-2xl)` = 48px
- 区域内卡片行间距：`var(--spacing-lg)` = 24px
- 数字卡片内部 padding：`var(--spacing-lg)` = 24px（与 BaseCard 一致）
- 图表容器内部 padding：`var(--spacing-lg)` = 24px（与 BaseCard 一致）
- 页面内容区（`.content`）已有 padding: `var(--spacing-xl)` = 32px，不修改

---

## 6. 关键组件规约

### MetricCard（数字统计卡片 · 新组件）

- **at rest**：白色 surface 背景 + 1px border 细线 + rounded-lg（8px）+ padding 24px。**无阴影**
- **布局**：上标签（micro-label + text-tertiary）→ 中大数字（display 缩小 + DM Sans 700 + text-primary）→ 下变化箭头行（supporting + trend color + 百分比文本）
- **hover**：**无**。数字卡片是只读展示，非可交互元素，不与用户产生 hover 反馈
- **变化箭头规则**：
  - 环比 > 0 → ↑ 绿色 + 数字（如 `↑12%`）
  - 环比 < 0 → ↓ 红色 + 数字（如 `↓3%`）
  - 环比 = 0 → → 灰色 + 文字"持平"
  - 无环比数据（首日）→ 不显示箭头行

```
 MetricCard at rest:
 ┌──────────────────┐
 │ 活跃用户数  MICRO │  ← micro-label, text-tertiary
 │                  │
 │      42          │  ← display 缩小, DM Sans 700
 │                  │
 │  ↑ 12% 较上月    │  ← supporting, --color-trend-up
 └──────────────────┘
```

### ECharts 图表容器（嵌入卡片内）

- **容器**：与 MetricCard 相同的卡片外观（白色 surface + 1px border + rounded-lg + padding 24px）
- **标题**：`.title` 层级，位于图表上方，左对齐
- **图例**：ECharts 内置 legend，位置在图表下方居中（不在右侧占用数据空间），文字用 `.supporting` 同色
- **网格线**：仅 Y 轴保留虚线（`oklch(0.90 0.005 95)` 色），X 轴无线；去除默认的 splitArea 填充
- **Tooltip**：白底 + 1px border + rounded-sm + 不透明（不用半透明玻璃），文字用 body/supporting，数据值用 mono
- **配色**：使用 §3 图表色板，按数据系列顺序 c0 → c1 → c2 ...，饼图最后一个切片用 `--chart-neutral`
- **动画**：首次渲染时 300ms ease-out-quint 入场（柱状图从 0 到值，折线图从左到右绘制，饼图扇形展开）。数据更新时不播放动画（`notMerge: false` + `duration: 0`）
- **hover**：柱/线 hover 时轻微高亮（opacity 变化）+ tooltip 显示，**不改变元素位置、不加阴影**

### 时间粒度切换器（NButtonGroup）

- **容器**：位于仪表盘右上角（与页面标题同行）
- **三个按钮**：天 / 周 / 月，大小 `small`（Naive UI 默认）
- **选中态**：Naive UI NButtonGroup 默认样式（filled primary 或 outlined），沿用 Naive UI 主题色不做自定义
- **标签文字**："天" / "周" / "月"，单字，中文，`.supporting` 层级
- **交互**：点击即时切换，无加载动画；数据加载期间图表区域显示 Naive UI skeleton（neutral 色调，非灰色条）

### 学科选择器（NSelect）

- **位置**：「图谱分布」区域标题右侧
- **默认值**："全部学科"
- **下拉选项**：从 `GET /api/v1/ops/stats/subjects` API 获取，格式与既有 `GET /api/v1/graph/subjects` 一致
- **样式**：Naive UI NSelect 默认，size `small`，宽度 160px

### "当前"/"历史趋势"标注

- **形式**：Naive UI NTag，size `tiny`，`type="info"` 或 `type="default"`
- **位置**：数字卡片区域标题旁标注 `当前`（bordered透明），历史趋势区域标题旁标注 `历史趋势`（bordered透明）
- **颜色**：tag 文字 = text-tertiary，背景透明，边框 = border color

---

## 7. Do's and Don'ts（本项目特定）

### Do

- 数字卡片的大数字用 **mono** 或 **display** 字体，突出数值本身
- 图表使用从 brand 派生的 6 色色板，保持色相一致
- 所有卡片保持平面 at rest，让数据自己说话
- 时间粒度切换后图表用 0ms 即时更新（不播动画避免闪烁）
- 图表标题左对齐，图例下方居中，保持与页面文字流向一致

### Don't（除了通用 anti-patterns，本项目额外禁止）

- ❌ 图表动画使用 bounce/elastic 缓动（违反动效类禁忌）
- ❌ 数字卡片加 drop shadow at rest（违反阴影类禁忌，且项目卡片均为平面）
- ❌ 图表容器使用渐变背景、彩色侧条（`border-left > 1px`）（违反边框类禁忌）
- ❌ 用 emoji 作为变化趋势指示（如 📈📉），用 §3 定义的趋势色 + lucide 箭头图标
- ❌ 卡片内嵌套卡片（违反布局类禁忌 — MetricCard 和图表容器均为一层卡片，不互相嵌套）
- ❌ 统一大小的卡片网格（三大区域各自有不同的布局：使用量=4 列数字卡片 + 1 饼图；文档量=1 数字卡片 + 3 图表不等宽；图谱=2 图表等宽）
- ❌ 在数据为 0 或 loading 时显示假数据或 "-"，应显示 "0"（数字）或空图表 + "暂无数据"文案

---

## 8. 占位符策略（反伪造 · 对应 R8.9）

| 缺的东西 | 本项目有什么？ | 缺时用什么占位 | 禁什么 |
|---|---|---|---|
| 图标 | lucide-vue（菜单导航、按钮、空状态均用它） | 新菜单项用 `BarChart3`（lucide 内建） | emoji / AI 自绘 SVG |
| 头像 | AppLayout 的首字母圆 + brand 色填充 | 沿用既有模式，不新增头像场景 | AI 生人脸 / 网抓图 |
| 图片 | 无（仪表盘无图片需求） | N/A | — |
| 数据 | **后端 API 真数据** | 前端开发阶段用 `opsStore` mock 数据（标 `// MOCK: remove before merge`），API 联调后删除 | 编造活跃用户数 / 好评率 / 处理量 |
| 图表 | ECharts 实例 | 数据为空时渲染空图表 + 居中"暂无数据"文字（`.supporting` + text-tertiary），**不隐藏图表容器** | 编数据填充图表 |
| 趋势箭头 | lucide `TrendingUp` / `TrendingDown` / `Minus` | 无环比数据时不渲染该行（而非显示虚假箭头） | 编环比数据 |
| logo | 无（侧边栏文字标 "GraphNexus"） | 沿用文字标 | AI 自绘图形标 |

**红线**：emoji 一个不用（项目不是 Notion/早期 Linear 风格）。

---

## 9. 反 AI-slop 自检结果

逐条对照 `@flow-kit/reference/ui-anti-patterns.md` 强制禁忌：

- [x] 字体类：DM Sans / PingFang SC，非 Inter/Roboto/Arial，display ≠ body 同字体不同字重。**通过**
- [x] 颜色类：无纯黑纯白（`oklch(0.15/0.985)` 倾斜），无紫色渐变，无霓虹青黑底，单主色原则，无 gradient text。**通过**
- [x] 阴影类：卡片 at rest 无阴影，shadow alpha max 0.12（低于 0.15），无装饰性彩色阴影，无 inset+glow。**通过**
- [x] 边框类：无 `border-left > 1px` 彩色侧条，无渐变边框，无玻璃拟态。**通过**
- [x] 动效类：无 bounce/elastic 缓动，仅 transition transform/opacity，`prefers-reduced-motion` 已支持，无滚动劫持。**通过**
- [x] 布局类：卡片不嵌套，非统一大小网格，无 hero metric cliché，间距非线性 scale（沿用 tokens.css），无 dark mode。**通过**
- [x] 文案类：中文工程向，按钮动词具体（"上传""创建""保存"），无 lorem ipsum，无空话。**通过**
- [x] 组件类：按钮用 Naive UI 默认（非通用圆角+阴影），无 placeholder-as-label，无 skeleton 纯灰条（用品牌色 neutral）。**通过**

**命中项**：无。

---

## 10. 触发任务

下一步进入 `3-task` 阶段时，运营仪表盘专属 UI 任务：

- **T-UI-01**：在 `tokens.css` 中新增图表色板 6 色 + 趋势指示 3 色 CSS 变量
- **T-UI-02**：实现 `MetricCard.vue` 通用数字卡片组件（Props: label / value / trend / trendValue / loading）
- **T-UI-03**：实现 `OpsDashboardPage.vue` 页面骨架（三区纵向布局 + 时间粒度切换 + 学科选择器 + 数据加载/空/错误状态）
- **T-UI-04**：实现 `UsageStatsPanel.vue`（系统使用量区域：4 卡片 + 操作类型饼图，复用 MetricCard）
- **T-UI-05**：实现 `DocumentStatsPanel.vue`（文档处理量区域：总量卡片 + 状态柱状图 + 学科饼图 + 趋势折线图）
- **T-UI-06**：实现 `GraphStatsPanel.vue`（图谱分布区域：节点类型柱状图 + 边类型柱状图 + 学科选择器联动）
- **T-UI-07**：ECharts 主题初始化（注册 6 色色板 + 去网格线 + tooltip 样式 + 入场动画 300ms ease-out-quint）
- **T-UI-08**：在 `AppLayout.vue` 的 `navItems` 中新增「运营管理」（`BarChart3` 图标，ADMIN/OPS_MANAGER 角色）
- **T-UI-09**：在 `router/index.ts` 中新增 `/ops` 路由（meta roles: ADMIN/OPS_MANAGER）
- **T-UI-10**：实现 `opsStore.ts`（Pinia store：summary + trend 数据 + granularity + subject 选择 + 加载状态）