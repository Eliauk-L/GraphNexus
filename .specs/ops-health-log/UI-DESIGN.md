---
name: GraphNexus ops-health-log
description: 极简运维面板 — 继承项目 Minimal（Linear/Vercel/Stripe）调性，深色侧边栏 + 浅色内容区，蓝调品牌色占比 <3%，平面卡片 + hairline 边框，纯色过渡交互

colors:
  brand: "oklch(0.55 0.18 250)"
  brand-deep: "oklch(0.48 0.18 250)"
  brand-veil: "oklch(0.55 0.18 250 / 0.12)"
  bg: "oklch(0.985 0.002 95)"
  surface: "oklch(1 0 0)"
  sidebar-bg: "oklch(0.12 0.01 250)"
  sidebar-text: "oklch(0.75 0.01 250)"
  sidebar-text-active: "oklch(1 0 0)"
  text-primary: "oklch(0.15 0.005 95)"
  text-secondary: "oklch(0.45 0.005 95)"
  text-tertiary: "oklch(0.65 0.005 95)"
  text-on-brand: "oklch(1 0 0)"
  border: "oklch(0.90 0.005 95)"
  border-focus: "oklch(0.55 0.18 250)"
  success: "oklch(0.55 0.15 145)"
  warning: "oklch(0.65 0.15 85)"
  error: "oklch(0.50 0.20 25)"

typography:
  display:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontWeight: 700
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

# UI Design: GraphNexus 系统运维面板

> 本文档复用并扩展 `frontend/src/assets/tokens.css` 和 `global.css` 的既有 design tokens。
> 本 change 不引入新的颜色 / 字体 / 动效 token，所有新增页面严格使用现有变量。

---

## 0. 视觉语汇对齐（brownfield）

### 0.1 观察报告（代码为源）

- **Token 源**：`frontend/src/assets/tokens.css`（CSS variables）+ `global.css`（排版层次），无 Tailwind，无 theme.ts。NConfigProvider 无 themeOverrides → Naive UI 走默认主题。
- **主色实际比例**：Brand oklch(0.55 0.18 250) 仅在侧边栏品牌名、链接、聚焦环使用，页面内容区占比 < 3%。
- **中性色**：背景 `oklch(0.985 0.002 95)` 偏米白；卡片表面 `oklch(1 0 0)` 纯白 + `oklch(0.90 0.005 95)` hairline 边框分隔；文字 chroma ≤ 0.005，不用纯黑。
- **hover / focus 反馈**：颜色变浅/深（background/color oklch 变化），duration 150ms ease-out，**无 transform 位移，无 scale**。active 态用半透明底色 overlay。
- **动效语言**：2 条缓动曲线（ease-out / ease-out-quint），3 档时长（150/300/600ms），纯 CSS transition，无 @keyframes 动画。
- **elevation 层级**：3 级阴影（hover-lift / card-lifted / accent-glow），但实际页面中卡片**at rest 无阴影**（仅 hairline 边框），阴影只出现在显式触发的状态（hover / dialog）。
- **卡片密度 / rounded**：padding 16px（`spacing-md`），rounded 8px（`rounded-md`），稀疏，不用渐变，不嵌套卡片。
- **图标库**：lucide-vue，stroke-width 1.5。本 change 需新增 `Activity`（健康）、`FileText`（日志）两个图标。
- **文案调性**：工程向中文名词，按钮文案简短直接（"上传""删除""应用配置"），无营销语调。

### 0.2 用户校准结论

- 用户确认所有观察无误 ✓
- 本次新增的运维页面**全部沿用**现有视觉语汇，不引入任何新模式

### 0.3 应用策略

- **沿用**：所有 token（颜色 / 字体 / 间距 / 圆角 / 动效 / 阴影），BaseCard 组件，Naive UI 组件库，侧边栏 + 内容区布局模式，lucide 图标库
- **延伸**：新增 `Activity` / `FileText` 图标（lucide 已有），健康状态色映射（UP=success 色 / DOWN=error 色），日志等宽字体内容区
- **打破**：无

---

## 1. 美学北极星

> "运维驾驶舱" — 继承 GraphNexus 极简 Minimal 调性。运维面板是内部工具，不是营销页——不需要任何装饰性视觉元素。信息密度适中（4 卡片 + 4 指标），一眼判断系统状态。三天后用户会形容它："干净、一眼能看出哪个组件挂了"。

### v0 确认摸路

- **已确认的假设**（用户明说 go）：
  - 四组件状态卡片 4 列等宽 grid（≥1280px 时每卡片 ~280px）
  - JVM 指标用文字 + 进度条，不含图表/环形图
  - 日志页左栏 280px 固定宽，不拖拽调整
  - 未选文件时右栏显示空状态提示，不预加载
  - 健康面板「最后刷新时间」由前端 setInterval 回调计算
  - 下载用 `<a>` 标签直接触发，不解压 .gz 但可下载
  - Naive UI 现有组件够用，不引入新组件库
- **用户指出的偏差**：无

---

## 2. 4 个决策问题

- **目的**：运维人员（ADMIN / OPS_MANAGER / OPS_STAFF）登录管理后台，快速判断基础设施健康状态 + 查看/下载运行日志。核心动作：扫一眼健康面板 → 发现异常 → 打开日志排查 → 下载日志离线分析。
- **调性**：极简（Minimal）— Linear/Vercel/Stripe。已在 `frontend-ui` CHANGE 步骤 0.6 锁定。
  - **理由**：运维面板是内部工具，需要信息清晰、无视觉噪音。极简调性天生适合"状态面板"类界面——卡片 + 颜色标签 + 数字，不需要任何装饰。
- **约束**：技术栈 Vue 3 + Naive UI，桌面端 ≥1280px，无暗色模式（运维场景默认亮色），中文界面。
- **差异化**：「运维驾驶舱」——不是花哨的 Grafana 仪表盘，是极简状态面板。运维人员 3 秒内能回答"系统正常吗？哪个组件有问题？"

---

## 3. 颜色系统（全部沿用 tokens.css）

### 本 change 新增的颜色使用场景

| Token | 用途 | 场景 |
|-------|------|------|
| `--color-success` | 组件 UP 状态 | `n-tag` type="success" 或直接使用色值 |
| `--color-error` | 组件 DOWN 状态 | `n-tag` type="error" 或直接使用色值 |
| `--color-bg` | 日志内容区背景 | `background: var(--color-bg)`，与页面背景同色以区分于白色卡片 |
| `--color-text-tertiary` | 空状态提示文字 | "选择一个日志文件查看内容" |
| `--font-mono` | 日志行字体 | 等宽字体，行高 1.5 |

### 命名规则（沿用）

- **The One Voice Rule**：品牌主色只有一个 hue（250 蓝），不引入第二个强调色
- **The Tinted Neutral Rule**：所有中性色 chroma ≤ 0.005
- **语义色仅用于状态表达**：success/error/warning 仅用于 UP/DOWN 标签和错误提示，不作装饰

---

## 4. 字体系统（全部沿用 global.css）

Display 用 DM Sans（不常用，仅页面主标题），Body 用 PingFang SC，日志内容用 JetBrains Mono。项目已避开 Inter/Roboto/Arial/system-ui，无需调整。

### 层次表（沿用 global.css 9 级）

| 角色 | 字体 | 字号 | 字重 | 行高 | 本 change 用途 |
|------|------|------|------|------|---------------|
| Headline | DM Sans | 1.5rem | 600 | 1.3 | 页面主标题"系统健康""系统日志" |
| Supporting | PingFang SC | 0.875rem | 400 | 1.5 | 最后刷新时间、文件大小、修改时间 |
| Mono | JetBrains Mono | 0.875rem | 400 | 1.5 | 日志文件内容行 |
| Micro-label | DM Sans | 0.6875rem | 500 | 1.2 | 组件卡片内标签（"延迟""状态"） |

---

## 5. 间距 & 圆角 & 动效（全部沿用 tokens.css）

不新增任何 token 值。新页面使用现有间距 scale（md=16px 为主要内边距，lg=24px 为卡片间距，sm=8px 为元素内间隙）。动效仅用 fast(150ms) 档的 ease-out。

---

## 6. 关键组件规约

### 6.1 PageHeader（页面标题栏 · 本 change 新增组合模式）

```
┌─ PageHeader ──────────────────────────────────────────────┐
│  headline "系统健康"                                        │
│  supporting "最后刷新: 11:09:23"           [n-button 刷新] │
└───────────────────────────────────────────────────────────┘
```

- 布局：flex row，space-between，align-items baseline
- 左侧：headline 标题 + supporting 副标题（竖排或横排，间距 sm）
- 右侧：操作区（按钮等）
- 底部不画分割线（与既有页面保持一致——AppLayout 内容区无 section divider）

### 6.2 HealthCard（组件状态卡片）

```
┌── n-card (BaseCard 风格) ──┐
│                             │
│  micro-label "MySQL"        │
│  n-tag "UP" (success 色)   │
│  mono "12ms"               │
│                             │
└─────────────────────────────┘
```

- **容器**：`n-card` 或复用 `BaseCard` 组件（`background: var(--color-surface)`, `border: 1px solid var(--color-border)`, `border-radius: var(--rounded-md)`, `padding: var(--spacing-lg)`）
- **at rest**：平面，仅 hairline 边框，**无阴影**
- **hover**：不响应（静态信息卡片，不需要交互反馈）
- **状态标签**：`n-tag`，UP → type="success"（绿），DOWN → type="error"（红）
- **延迟数值**：`.mono` 字号，UP 时显示毫秒，DOWN 时显示简化错误原因（`.supporting` 字号 `--color-text-tertiary`）

### 6.3 JvmMetricsPanel（JVM 指标面板）

```
┌── n-card (BaseCard 风格) ─────────────────────┐
│  title "JVM 运行时指标"                         │
│                                                │
│  堆内存    ████████████░░░░░░  512/1024 MB     │
│           n-progress (percentage, brand 色)     │
│                                                │
│  CPU       32%  │  线程活跃    47              │
│  GC 次数   12   │  堆最大      1024 MB         │
│                                                │
└────────────────────────────────────────────────┘
```

- **容器**：同 HealthCard（BaseCard 风格）
- **进度条**：`n-progress` type="line"，颜色 `--color-brand`，百分比 = used/max * 100
- **指标数字**：`.mono` 字体，`--color-text-primary`
- **指标标签**：`.micro-label`，`--color-text-tertiary`
- **栅格布局**：2×2 或 2×3 网格，间距 `spacing-md`
- **无环形图/仪表盘**（v1 保持极简）

### 6.4 LogFileList（日志文件列表）

```
┌── 文件列表 ────────────────────┐
│  graphnexus-dev.log           │ ← 14px body, --color-text-primary
│  1.6 MB  2026-06-23 11:09     │ ← 12px supporting, --color-text-tertiary
│                               │
│  graphnexus-error.log  ← 选中 │ ← active 态：半透明底色 overlay
│  253 KB   2026-06-23 10:12    │
│                               │
│  graphnexus-dev.2026-06-22    │
│  .0.log.gz                    │
│  487 KB   2026-06-22 08:49    │
└───────────────────────────────┘
```

- **容器**：宽度固定 280px，`border-right: 1px solid var(--color-border)`
- **列表项**：
  - 文件名：`.body` 等效字号（14px），`--color-text-primary`，单行省略号溢出
  - 元数据行：`.supporting` 字号（12px），`--color-text-tertiary`，格式 "大小 · 日期"
  - hover：背景变 `oklch(0 0 0 / 0.04)`（极浅灰），duration fast
  - active（选中）：背景 `oklch(0.55 0.18 250 / 0.06)`（brand veil 半透明），同 sidebar__item.active 逻辑
- **列表滚动**：`overflow-y: auto`，整体高度撑满可用空间
- **文件类型区分**：`.gz` 文件用 `--color-text-tertiary` 淡化文件名（但仍可点击下载/查看）

### 6.5 LogContentViewer（日志内容区）

```
┌── 日志内容 ────────────────────────────────────────────┐
│  << 第 1/156 页 >>                    [n-button 下载] │
│                                                       │
│  ┌─ pre 块 (bg: --color-bg) ──────────────────────┐  │
│  │ 2026-06-23 11:09:01.123 [main] INFO [...] ...  │  │
│  │ 2026-06-23 11:09:02.456 [main] DEBUG [...] ... │  │
│  │ 2026-06-23 11:09:03.789 [main] WARN [...] ...  │  │
│  │ ...                                             │  │
│  └─────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────┘
```

- **分页控件**：手写上一页/下一页按钮（`n-button` secondary，size small）+ 页码文字（`.supporting`），不使用 `n-pagination` 完整组件（极简风格下全功能分页器过重）
  - 「上一页」按钮在 page=1 时 disabled
  - 「下一页」按钮在 page=totalPages 时 disabled
  - 中间显示 "第 X/Y 页"（`.supporting`）
- **内容区**：`<pre>` 标签，`font-family: var(--font-mono)`, `font-size: 0.8125rem`(13px)，`line-height: 1.5`, `background: var(--color-bg)`, `padding: var(--spacing-md)`, `border-radius: var(--rounded-sm)`, `overflow-x: auto`
  - 日志行不做语法高亮（v1），纯文本单色 `--color-text-primary`
  - 不折行（`white-space: pre`），横向溢出可滚动
- **下载按钮**：`n-button` secondary，size small，lucide `Download` 图标 + "下载" 文字，放在分页控件右侧
- **空状态**：未选中文件时，内容区居中显示 "选择一个日志文件查看内容"（`.supporting`，`--color-text-tertiary`）

### 6.6 侧边栏新增菜单项

```
系统运维 ▾
  系统健康  Activity 图标  14px
  系统日志  FileText 图标  14px
```

- 结构：在既有 `navItems` 数组中新增一个「系统运维」分组（或追加两个独立项）
- 样式：完全复用既有 `sidebar__item` 的 hover/active 状态（`.sidebar__item:hover` / `.sidebar__item.active`）
- 图标：lucide `Activity`（健康）、`FileText`（日志），stroke-width 1.5
- 可见性：仅 `hasRole('ADMIN') \|\| hasRole('OPS_MANAGER') \|\| hasRole('OPS_STAFF')` 时渲染这两项
- 实现策略：追加到 navItems 数组，roles 字段设为 `['ADMIN', 'OPS_MANAGER', 'OPS_STAFF']`，`visibleNavItems` computed 自动过滤

---

## 7. Do's and Don'ts（本项目特定）

### Do

- 所有新页面使用既有 CSS variables，禁止硬编码颜色值
- 新页面内容区 padding 使用 `var(--spacing-lg)` = 24px（与既有页面一致）
- 列表/卡片 hover 反馈统一使用半透明 overlay（oklch + alpha），duration 150ms ease-out
- 按钮使用 Naive UI `n-button`，secondary 类型用于次要操作
- 页面标题使用 `.headline` 类

### Don't（除了通用 anti-patterns，本项目额外禁止）

- **禁止**在日志内容区使用语法高亮（v1 纯文本即可，高亮留给 v2）
- **禁止**在健康面板使用环形图/仪表盘/渐变背景装饰（违反极简调性）
- **禁止**为组件卡片添加 at-rest shadow（保持平面 hairline 边框风格）
- **禁止**在未选中文件时预加载第一个日志文件内容（浪费带宽，空状态即可）
- **禁止**使用彩色侧条（`border-left` > 1px）标记组件状态（用 n-tag 色标代替）

---

## 8. 占位符策略

| 缺的东西 | 本项目有什么？ | 缺时用什么占位 | 禁什么 |
|----------|-------------|-------------|--------|
| 图标 | lucide-vue（Activity / FileText / Download） | `[icon]` 方块标 | emoji / AI 自绘 SVG |
| 健康数据 | 后端 API 返回真实数据 | n-progress 显示 0% + `.supporting` "加载中..." | 编造 UP/DOWN 状态 |
| 日志内容 | 后端读取真实日志文件 | 空状态 "选择一个日志文件查看内容" | Lorem ipsum / 编造日志行 |
| 文件大小 | 后端 `Files.size()` 返回真实字节数 | "—" | 编文件大小 |
| 头像 | 无头像组件（运维面板不需要） | 不适用 | — |

---

## 9. 反 AI-slop 自检结果

逐条对照 `@flow-kit/reference/ui-anti-patterns.md` "强制禁忌"段：

- [x] **字体类**：未命中。项目已用 DM Sans / PingFang SC / JetBrains Mono，避开 Inter/Roboto/Arial。
- [x] **颜色类**：未命中。不用纯黑纯白，不用紫色渐变，不用 gradient text，单一品牌色。
- [x] **阴影类**：未命中。卡片 at rest 平面无阴影，hover 仅颜色变化，不用彩色阴影。
- [x] **边框类**：未命中。无彩色侧条，无渐变边框，无玻璃拟态。
- [x] **动效类**：未命中。仅用 CSS transition color/background，`prefers-reduced-motion` 已支持。
- [x] **布局类**：未命中。无卡片嵌套，无非线性间距外的均匀步长，无 metric hero 数字。
- [x] **文案类**：未命中。工程向中文，按钮动词具体。
- [x] **组件类**：未命中。Naive UI button 不自定义圆角阴影。

**无命中条目。** 本设计通过反 AI-slop 自检。

---

## 10. 触发任务

下一步进入 `3-task` 阶段时，**不需要**单独的 UI token 物化任务——tokens.css 和 global.css 已就绪。Task 拆分直接进入：

- 后端：HealthIndicator + SystemHealthController + LogController + SecurityConfig 修改
- 前端：systemStore + system API + SystemHealthPage + SystemLogPage + AppLayout 菜单 + router 路由