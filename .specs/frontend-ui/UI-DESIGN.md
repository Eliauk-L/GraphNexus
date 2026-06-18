---
name: GraphNexus 管理后台
description: 极简冷蓝工具面板 — 单一品牌色 oklch(0.55 0.18 250) + 几何无衬线 + 大留白。数据与图谱是主角，chrome 是背景。

colors:
  brand: "oklch(0.55 0.18 250)"
  brand-deep: "oklch(0.48 0.18 250)"
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
  brand-veil: "oklch(0.55 0.18 250 / 0.12)"

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
  body-lead:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif"
    fontSize: "1.125rem"
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
    textTransform: "uppercase"
    letterSpacing: "0.05em"
    lineHeight: 1.3
  micro-label:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 500
    textTransform: "uppercase"
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
  hover-lift: "0 2px 8px rgba(0,0,0,0.08)"
  card-lifted: "0 20px 60px rgba(0,0,0,0.12)"
  accent-glow: "0 20px 60px var(--color-brand-veil)"
---

# UI Design: GraphNexus 管理后台

## 0. 视觉语汇对齐

greenfield 项目，无既有 UI。跳过。

---

## 1. 美学北极星

> **Data-First Minimalism** — 冷蓝单色锚点 + 暖米白底 + 平面静态。读起来像专业工具（Linear/Vercel），不像 SaaS 落地页。知识图谱和诊断报告是视觉主角，chrome 退到背景。

### v0 确认摸路

- **已确认的假设**（用户说 go）：
  - 侧边栏用深色底（oklch(0.12 0.01 250)），极简工具类产品的常见选择
  - 卡片 at rest 无阴影，仅 hairline 边框 — 不是 Material Design 风
  - 默认亮色模式，不做 dark mode（V1 out）
  - 不需要 secondary button，次级动作用 inline link
  - 文件上传用按钮 + 隐藏 input，不拖拽
  - Markdown 渲染用 `marked` + `highlight.js`
  - 图标库用 Lucide Vue（轻量 stroke 风格）
- **用户指出的偏差**：无。

---

## 2. 4 个决策问题

- **目的**：教育知识图谱管理工具。管理员/教师上传教辅 PDF 和成绩 CSV → 管理文件 → 查看图谱 → 智能问答诊断学生薄弱点。核心动作：上传、查询、看图、提问。
- **调性**：**极简（Minimal）** — 从 CHANGE.md 继承，用户在 0-change 步骤 0.6 选定。
  - **理由**：专业教育分析工具，教师需要专注、无干扰的数据交互。极简的几何字体 + 大留白让图谱可视化和诊断报告成为视觉焦点，不被 chrome 抢戏。
- **约束**：Vue 3 + TypeScript + Vite；桌面端 ≥1280px；V1 无鉴权、无 dark mode、无 i18n；颜色必须 OKLCH；字体避开 Inter/Roboto/Arial。
- **差异化**：冷蓝 + 暖米白的温度对比。深色侧边栏 + 亮色内容区的双域布局。图谱可视化是唯一"有颜色"的区域（边类型颜色编码），其余页面严格单色。

---

## 3. 颜色系统

### Primary

- **冷蓝** `oklch(0.55 0.18 250)`：用于 primary button 背景、链接色、聚焦环、侧边栏选中态指示。**绝不用于**大面积背景或装饰。

### Neutral

- **暖米白** `oklch(0.985 0.002 95)`：主背景。chroma 0.002 在 95° hue 上，比纯白略暖，避免"医院白"。
- **纯白** `oklch(1 0 0)`：卡片/表格表面。仅在 elevation 上使用，与背景形成微弱温差。
- **主文字** `oklch(0.15 0.005 95)`：正文。比纯黑亮半档，阅读更舒适。
- **次要文字** `oklch(0.45 0.005 95)`：辅助信息、label。
- **三级文字** `oklch(0.65 0.005 95)`：placeholder、disabled 态。
- **边框** `oklch(0.90 0.005 95)`：hairline 1px 分隔。刚好可见，不抢注意力。

### 侧边栏

- **深底** `oklch(0.12 0.01 250)`：左侧导航背景。带极微量蓝色调，与品牌色呼应，不是死黑。
- **暗淡文字** `oklch(0.75 0.01 250)`：未选中导航项。
- **选中态** `oklch(1 0 0)`：纯白文字 + 左侧 3px 冷蓝指示条。

### 语义色

- **成功** `oklch(0.55 0.15 145)`：COMPLETED 状态、成功 toast。
- **警告** `oklch(0.65 0.15 85)`：PROCESSING 状态、警告提示。
- **错误** `oklch(0.50 0.20 25)`：FAILED 状态、错误提示、删除按钮 hover。

### 命名规则

- **The One Voice Rule**：本项目主色只有冷蓝。语义色（success/warning/error）是必要例外，不视为第二个 hue。
- **The Tinted Neutral Rule**：所有中性色 chroma ≥ 0.002。不做纯灰。
- **The No Pure B/W Rule**：背景不是 `#fff`，文字不是 `#000`。

---

## 4. 字体系统

- **Display**：`DM Sans` — 几何无衬线，x-height 高，在中文环境下与 CJK 搭配不违和。**为什么不用 Inter/Roboto**：Inter 是 AI slop 默认，DM Sans 有更鲜明的几何骨架。
- **Body**：`PingFang SC / Microsoft YaHei / Noto Sans SC` — 系统 CJK 栈。中文 Web 字体体积过大（≥5MB），v1 使用系统字体是务实选择。**为什么不是自定义 CJK font**：首屏性能预算不允许加载中文字体文件。
- **Mono**：`JetBrains Mono` — 用于数据表格数字、代码块、指标值。等宽 + 高可读性。

### 例外声明

CJK body 使用了系统字体栈（含 PingFang SC / Microsoft YaHei），这**形式上**与 ui-anti-patterns 「禁止 system-ui 作主字体」冲突。但中文 Web 字体生态与拉丁不同：高质量 CJK Web 字体文件 ≥5MB，v1 阶段加载它们会违反 LCP ≤ 2.5s 的性能约束。**这是权衡后的显式例外**，v2 可评估子集化 CJK 字体方案。

### 层次表

| 角色 | 字体 | 字号 | 字重 | 行高 | 用途 |
|------|------|------|------|------|------|
| Display | DM Sans + CJK | clamp(1.75rem, 4vw, 2.5rem) | 700 | 1.15 | 页级标题（极少使用） |
| Headline | DM Sans + CJK | 1.5rem | 600 | 1.3 | Section 标题 |
| Title | DM Sans + CJK | 1.125rem | 500 | 1.4 | 卡片标题、表格标题 |
| Body | CJK stack | 1rem | 400 | **1.6** | 正文段落、Markdown 内容 |
| Body-lead | CJK stack | 1.125rem | 400 | 1.6 | 引导语、摘要 |
| Supporting | CJK stack | 0.875rem | 400 | 1.5 | 辅助说明、时间戳 |
| Label | DM Sans + CJK | 0.75rem | 500 | 1.3 | 按钮文字、表单标签、状态标签 |
| Micro-label | DM Sans + CJK | 0.6875rem | 500 | 1.2 | 表格列头、badge |
| Mono | JetBrains Mono | 0.875rem | 400 | 1.5 | 数据数值、代码、指标 |

---

## 5. 间距 & 圆角 & 动效

按 frontmatter 中的 token 执行，**禁止在 token 之外引入新数值**。

### 间距使用约定

| Token | 值 | 典型用途 |
|-------|-----|---------|
| `xs` | 4px | 图标与文字间距、tag 内边距 |
| `sm` | 8px | 表格单元格内边距、inline 元素间距 |
| `md` | 16px | 卡片内边距、表单项间距 |
| `lg` | 24px | Section 内边距、模态框内边距 |
| `xl` | 32px | 页面内容区 padding |
| `2xl` | 48px | 大 section 间距 |
| `3xl` | 80px | 页面级间距 |
| `4xl` | 120px | Hero 区上下留白（极少用） |

### 圆角约定

| Token | 值 | 用途 |
|-------|-----|------|
| `none` | 0 | 表格、分隔线 |
| `sm` | 4px | Input、select、tag、badge |
| `md` | 8px | Button、card、dropdown |
| `lg` | 12px | Modal、dialog |
| `full` | 9999px | 状态圆点、pill badge |

### 动效约定

- **默认缓动**：`cubic-bezier(0.16, 1, 0.3, 1)`（expo-out）
- **颜色/opacity 变化**：150ms
- **transform 变化**（hover lift、展开）：300ms
- **编排式入场**（页面切换、列表 stagger）：600ms，每项 stagger 50ms
- **仅动 transform 和 opacity**，不动 width/height/padding
- **必须支持 `prefers-reduced-motion`**：所有动效降级为 0ms 即时切换

---

## 6. 关键组件规约

### Shell — 侧边栏 + 内容区

```
┌──────────────────┬────────────────────────────────────────┐
│ 侧边栏 240px      │ 顶部栏 h=48px                          │
│ 深底 oklch(0.12)  │ 浅底，面包屑 + 用户头像占位             │
│                  │                                        │
│ [logo]           │ ┌─ 内容区 ───────────────────────────┐ │
│                  │ │ padding: xl (32px)                  │ │
│ 导航项 h=40px     │ │                                    │ │
│ 圆角 md          │ │                                    │ │
│ hover 浅底       │ │                                    │ │
│ 选中: 白字+     │ │                                    │ │
│ 左侧3px冷蓝条    │ │                                    │ │
│                  │ └────────────────────────────────────┘ │
└──────────────────┴────────────────────────────────────────┘
```

- 侧边栏固定 240px，不折叠（V1 不做汉堡菜单）
- 导航项图标 + 文字，图标 20px Lucide，间距 xs(4px)
- 顶部栏仅显示当前页面标题 + 右侧用户占位

### Button (Primary)

- **形状**：rounded `md`（8px）
- **背景**：冷蓝 `oklch(0.55 0.18 250)`
- **文字**：`text-on-brand` 白色，label 字重 500
- **内边距**：`sm(8px)` 垂直 × `md(16px)` 水平
- **最小宽度**：80px，高度 36px
- **at rest**：无阴影，无边框
- **hover**：背景 → `brand-deep`，transform: translateY(-1px)，duration `base`(300ms)，ease-out
- **focus**：`0 0 0 3px var(--color-brand-veil)` 聚焦环
- **disabled**：opacity 0.4，cursor not-allowed
- **本项目无 secondary button**。次级动作用 `品牌色文字链接 + hover 下划线` 表达。

### Button (Danger)

- 仅用于删除操作。
- at rest：背景透明，文字 `error` 色，边框 `error` 色 1px
- hover：背景 `error`（opacity 0.1），文字 `error`

### Input / Field

- **at rest**：边框 1px `border`，bg `surface`，rounded `sm`(4px)，内边距 `sm`(8px) × `md`(16px)，高度 36px
- **focus**：边框色 → `border-focus`，外发光 `0 0 0 3px var(--color-brand-veil)`
- **error**：边框色 → `error`，下方 4px 处显示 supporting 大小 `error` 色错误文字
- **label**：在 input 上方，不在 placeholder 里（无障碍红线）
- **placeholder**：`text-tertiary`，仅作示例提示，label 独立存在

### Select / Dropdown

- 外观同 Input
- 下拉面板：rounded `md`，阴影 `hover-lift`，选项 hover 冷蓝 10% 底
- 选项高度 36px，内边距同 button

### Card / Container

- **at rest**：平面，无阴影，hairline 边框 `border`，rounded `md`(8px)，内边距 `md`(16px)
- **hover**：无变化（卡片不是交互元素；如整卡可点击 → hover 时边框色 → `border-focus`）
- **嵌套规则**：禁止 card 套 card。如需分区 → 用 `<section>` + 标题分隔，不嵌套。

### Table

- **表头**：`micro-label` 字体，`text-secondary`，底部 1px `border` 分隔
- **行**：高度 44px，底部 hairline `border` 分隔
- **hover**：行背景 → `brand-veil`（冷蓝 12% opacity）
- **分页**：底部居中，页码按钮 32px 方形，rounded `sm`，当前页冷蓝实底白字
- **空态**：居中显示 `[icon] 暂无数据`，图标 48px `text-tertiary`，文字 `supporting`

### Status Badge

- rounded `full`，内边距 `xs(4px)` × `sm(8px)`
- `COMPLETED` / 已完成：`success` 色浅底 + `success` 色文字
- `PROCESSING` / 处理中：`warning` 色浅底 + `warning` 色文字（可加旋转动画指示器）
- `FAILED` / 失败：`error` 色浅底 + `error` 色文字
- `UPLOADED` / 已上传：`text-secondary` 色浅底 + `text-secondary` 色文字

### Markdown 渲染区（智能问答页）

- 正文区最大宽度 720px，居中
- 标题（h2/h3/h4）使用 Headline/Title 对应字号，`text-primary`
- 列表项间距 `sm`(8px)
- 代码块：`mono` 字体，背景 `bg` 色块，rounded `sm`，内边距 `md`(16px)
- 表格：同 Table 规约
- Token 用量栏：底部固定，`supporting` 文字，`text-tertiary`

### 文件上传

- 上传区：虚线边框 2px `border`，rounded `md`，内边距 `xl`(32px)，居中
- 上传图标：Lucide `Upload` 48px，`text-tertiary`
- 文字：`body-lead` "选择文件或拖拽到此区域"
- 支持按钮：Primary Button "选择文件" + 隐藏 `<input type="file">`
- 文件选中后：显示文件名 + 大小 + "移除"链接
- 学科选择：Select 组件，必填
- 提交按钮：Primary Button "上传"

### Typography Hierarchy

参考第 4 节层次表。页面结构优先级：
1. Headline — 每页最多 1 个
2. Title — 每个 card/section 最多 1 个
3. Body — Markdown 正文
4. Supporting / Label — 辅助信息

---

## 7. Do's and Don'ts（本项目特定）

### Do

- 用留白建立视觉层次，不要依赖线框
- 数据密集区域（表格/指标）用 `JetBrains Mono` 数字
- 状态变化用颜色 + 图标双重编码（色觉障碍友好）
- 空态页展示明确引导（"上传第一个文件" + 操作按钮）
- 加载态用品牌色 skeleton（冷蓝 12% opacity 底色 + 动画）

### Don't（除了通用 anti-patterns，本项目额外禁止）

- 禁止卡片带 drop shadow at rest（极简调性要求平面）
- 禁止两个以上 hue 同时出现在同一页面（语义色除外）
- 禁止渐变背景或渐变边框（纯粹的极简不做这个）
- 禁止 emoji 作图标（占位符用 Lucide 或 `[icon]` 方块）
- 禁止彩色侧边栏（深色中性底 + 品牌色小指示条，不是整个 sidebar 上色）
- 禁止在非图谱页面使用彩色图形装饰

---

## 8. 占位符策略（反伪造）

| 缺的东西 | 本项目有什么？ | 缺时用什么占位 | 禁什么 |
|---|---|---|---|
| 图标 | Lucide Vue（已选） | `[icon]` 方块 24×24px 灰底 | emoji / AI 粗糙 SVG |
| 头像 | 无头像组件 | 首字母圆 32px + `brand-veil` 填充 | AI 生人脸 / 网抓图 |
| 图片 | 无 CDN | aspect-ratio 16:9 卡片，标 `16:9 image` | stock photo / AI 生成图 |
| 数据 | — | **反问用户要真数据** | 编活跃用户数 / 好评率 |
| logo | 无品牌 logo | 文字 "GraphNexus" DM Sans 700 + 冷蓝色 | AI 自绘图形标 |
| 客户推荐 | — | 不适用（管理工具） | — |
| KPI / 指标 | 后端 GDS 所出真数据 | `[awaits real metric]` | 编数字 |

红线：本项目禁止 emoji（🚀⚡✨），极简调性不用这个。

---

## 9. 反 AI-slop 自检结果

逐条对照 `@flow-kit/reference/ui-anti-patterns.md` 强制禁忌段：

- [x] **字体类**：Display 用 DM Sans（非 Inter/Roboto）；body 用系统 CJK 栈 — 已写例外声明（§4）
- [x] **颜色类**：无纯黑纯白；无紫色渐变/霓虹青；只有一个主色 hue；无 gradient text
- [x] **阴影类**：卡片 at rest 无阴影（仅 hairline 边框）；hover 阴影 alpha=0.08 ≤ 0.15；无装饰性彩色阴影
- [x] **边框类**：无 >1px 彩色侧条；侧边栏用深底而非彩色边框；无渐变边框；无玻璃拟态
- [x] **动效类**：用 expo-out 缓动（非 bounce/elastic）；仅动 transform + opacity；支持 `prefers-reduced-motion`；不劫持滚动
- [x] **布局类**：不禁卡套卡；间距用非线性 scale（非 4/8/12/16 均匀步长）；不默认 dark mode
- [x] **文案类**：中文产品，无 "Boost your productivity" 类 slogan；按钮用具体动词；无 Lorem ipsum
- [x] **组件类**：button 平面 at rest（非圆角+阴影模板）；form 用 label 非 placeholder 替代；桌面端无 tooltip 问题；模态框支持 Escape

**命中条目**：CJK body 字体使用系统栈（§4 例外声明已解释）。其余未命中。

---

## 10. 触发任务

下一步进入 `3-task` 阶段时，把以下作为**第一批 UI 任务**：

- T-UI-01：物化 design tokens → CSS variables（`:root` 块，`src/assets/tokens.css`）
- T-UI-02：实现 typography 层次（global stylesheet，`src/assets/global.css`）
- T-UI-03：实现 Shell 布局组件（侧边栏 + 顶部栏 + 内容区插槽，`AppLayout.vue`）
- T-UI-04：实现 Button 组件（Primary + Danger，`BaseButton.vue`）
- T-UI-05：实现 Input / Select / Field 组件（`BaseInput.vue`, `BaseSelect.vue`）
- T-UI-06：实现 Card 组件（`BaseCard.vue`）
- T-UI-07：实现 Table 组件（含分页、空态、加载态，`DataTable.vue`）
- T-UI-08：实现 StatusBadge 组件（`StatusBadge.vue`）
- T-UI-09：实现 Markdown 渲染组件（`MarkdownViewer.vue`，封装 marked + highlight.js）