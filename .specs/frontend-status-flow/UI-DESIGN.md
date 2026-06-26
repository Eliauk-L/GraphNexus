---
name: GraphNexus - frontend-status-flow
description: 极简管线进度指示器。继承现有 Minimal 设计系统，零新增 token，在 120px 表格列内展示三阶段处理进度。
colors: {}   # 无新增颜色，全部继承 tokens.css
typography: {}  # 无新增字体，使用现有 micro-label
spacing: {}    # 无新增间距
rounded: {}    # 无新增圆角
motion: {}     # 无新增动效，复用现有 spin keyframe + pulse
shadow: {}     # 无新增阴影
---

# UI Design: 文件处理状态流 — 管线进度指示器

## 0. 视觉语汇对齐（Brownfield）

### 0.1 观察报告（代码为源）

- **Token 源**：`frontend/src/assets/tokens.css` — `:root` 下 68 行 CSS 变量
- **主色实际比例**：冷蓝 `oklch(0.55 0.18 250)`，仅在 primary button、link、focus-ring 使用，约占页面面积 2–3%
- **中性色**：背景 `oklch(0.985 0.002 95)` 偏米白；文字 `oklch(0.15 0.005 95)` 近黑但不纯；中性 chroma 统一 0.002-0.005，绝不纯灰
- **语义色**：success `oklch(0.55 0.15 145)` / warning `oklch(0.65 0.15 85)` / error `oklch(0.50 0.20 25)`，三色均用于状态徽章和反馈
- **hover / focus 反馈**：统一 `color 变深 + 150ms + cubic-bezier(0.16, 1, 0.3, 1)`，无 transform/阴影参与
- **动效语言**：CSS 驱动，`prefers-reduced-motion` 已支持（所有 duration → 0ms），两个缓动曲线（ease-out / ease-out-quint），三个 duration 档（150/300/600ms）。StatusBadge 有 `spin` keyframe（0.8s linear infinite）
- **elevation 层级**：仅 2 级 — `shadow-hover-lift`（轻量，hover 响应）+ `shadow-card-lifted`（卡片浮起）。Card at rest 为平面 + 1px hairline 边框。无纵深堆叠
- **卡片密度 / rounded**：padding 16px，rounded-md（8px），hairline `--color-border`，不可嵌套；表格式布局为主
- **图标库**：Naive UI 内置图标（通过 NButton 等组件间接使用），无独立图标组件
- **文案调性**：工程向，短动词（"解析""删除""上传""搜索"），中文 label 使用 `text-transform: uppercase` + `letter-spacing: 0.05em`
- **状态徽章**：`rounded-full` 胶囊形，`label` 类（0.75rem/500w），颜色按状态映射 8 种，`*ING` 态有 CSS spinner 动画

### 0.2 用户校准结论

- 用户确认观察正确 ✓
- 本次**完全沿用**现有设计系统，零新增 token
- 管线进度指示器是对现有 `StatusBadge` 的**增强替换**，视觉语言保持一致

### 0.3 应用策略

- **沿用**：全部 token（颜色/字体/间距/圆角/动效/阴影），Naive UI 组件库，排版层次
- **延伸**：`StatusBadge` 的 `spin` keyframe 扩展为 `pulse` 呼吸动画（opacity 0.4↔1）用于进行中阶段圆点
- **打破**：无

## 1. 美学北极星

> 极简管理后台 — 冷蓝单色锚点 + 米白背景 + 平面静态卡片。状态可视化用最小的视觉元素传达最多的信息：三个圆点 + 两根连线 = 一个管线的完整进度。

### v0 确认摸路

- **已确认的假设**：
  - 步骤条只展示 3 个阶段（解析/抽取/融合），跳过"上传"（瞬间完成）
  - FAILED 状态不展示步骤条，回退为红色文字徽章
  - tooltip 用 Naive UI NTooltip 实现
  - 不需要暗黑模式适配
  - 圆点 + 连接线在 120px 列宽内可容纳（约 80px 实际宽度）
- **用户指出的偏差**：无

## 2. 4 个决策问题

- **目的**：管理员在文件列表中一眼看清每个文件的处理管线走到了哪一步，无需点击进入详情
- **调性**：**极简（Minimal）** — 继承自 `frontend-ui` CHANGE
  - **理由**：现有管理后台已是极简风格，管线指示器是表内嵌元素，必须安静融入而非抢眼
- **约束**：列宽 120px（硬约束），Naive UI 组件库，桌面端 ≥1280px，中文界面
- **差异化**：三个圆点传达六个状态 — 信息密度最高但视觉噪音最低的状态展示方案

## 3. 颜色系统

**全部继承 `tokens.css`，零新增颜色变量。**

管线指示器用到的现有 token：

| 元素 | Token | 值 |
|------|-------|-----|
| 已完成圆点 | `--color-success` | `oklch(0.55 0.15 145)` |
| 进行中圆点 | `--color-brand` | `oklch(0.55 0.18 250)` |
| 未开始圆点 | `--color-text-tertiary` | `oklch(0.65 0.005 95)` |
| 连接线-已完成 | `--color-success` | 同上 |
| 连接线-未完成 | `--color-border` | `oklch(0.90 0.005 95)` |
| 失败状态文字 | `--color-error` | `oklch(0.50 0.20 25)` |
| 阶段标签文字 | `--color-text-tertiary` | `oklch(0.65 0.005 95)` |

**规则**：
- **The One Voice Rule**：唯一强调色仍是 brand blue，仅用于「进行中」阶段的圆点
- **The Tinted Neutral Rule**：未开始圆点和连接线用 `--color-border`（chroma 0.005），避开纯灰
- **语义色仅用于语义**：success 仅表已完成，warning 不用于管线指示器（无"警告"语义），error 仅表失败

## 4. 字体系统

**全部继承 `global.css` 排版层次，零新增字体。**

| 角色 | 类 | 字体 | 字号 | 字重 | 用途 |
|------|-----|------|------|------|------|
| 阶段标签 | `micro-label` | DM Sans + PingFang SC | 0.6875rem | 500 | 圆点下方的"解析/抽取/融合" |
| tooltip 标题 | `label` | DM Sans + PingFang SC | 0.75rem | 500 | NTooltip 内的状态名称 |
| tooltip 描述 | `supporting` | PingFang SC | 0.875rem | 400 | NTooltip 内的阶段说明 |

## 5. 间距 & 圆角 & 动效

### 间距（用于管线指示器内部布局）

| 元素 | Token | 值 |
|------|-------|-----|
| 圆点直径 | — | 6px |
| 连接线长度 | — | 12px（圆点间距 ≈ 6 + 12 + 6 = 24px/段） |
| 连接线粗细 | — | 1.5px |
| 圆点与标签间距 | `--spacing-xs` | 4px |
| 总宽度 | — | ≈ 80px（3 圆点 × 6 + 2 连线 × 12 + 2 × 12 间距） |

### 圆角

| 元素 | Token | 值 |
|------|-------|-----|
| 圆点 | `--rounded-full` | 9999px（正圆） |
| tooltip 容器 | `--rounded-md` | 8px（继承 NTooltip 默认） |

### 动效

| 场景 | 缓动 | 时长 | keyframe |
|------|------|------|----------|
| 进行中圆点呼吸 | `--ease-out` | `--duration-slow`(600ms) | `pulse`：opacity 0.4 ↔ 1 |
| 阶段完成过渡（圆点变色） | `--ease-out` | `--duration-base`(300ms) | CSS `transition: color, background-color` |
| 连接线状态切换 | `--ease-out` | `--duration-base`(300ms) | CSS `transition: background-color` |
| tooltip 出现/消失 | Naive UI 内置 | Naive UI 默认 | 不自定义 |

**`pulse` keyframe（新增，仅此一个新增定义）**：
```css
@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}
```

> 注：`spin` keyframe 已在 `StatusBadge.vue` 中定义，`pulse` 为本次新增，两者不冲突。`prefers-reduced-motion` 已在 `tokens.css` 中全局处理（所有 duration → 0ms），pulse 自动停止。

## 6. 关键组件规约

### 6.1 StatusPipeline（管线进度指示器）

**替代**：`StatusBadge.vue` 在文件列表状态列的使用（`StatusBadge` 组件本身保留，用于其他场景的纯文字状态展示）

#### 根元素
- `display: inline-flex`，`flex-direction: column`，`align-items: center`
- `gap: var(--spacing-xs)`（4px，圆点行与标签行之间）

#### 圆点行
- `display: inline-flex`，`align-items: center`，`gap: 0`（圆点与连线紧邻）
- 总宽 ≈ 80px，在 120px 列内居中

#### 圆点（.pipeline-dot）
- `width/height: 6px`，`border-radius: var(--rounded-full)`
- 三种视觉态：
  - **completed**：`background: var(--color-success)`，无边框
  - **active**：`background: var(--color-brand)`，`animation: pulse 600ms var(--ease-out) infinite`
  - **pending**：`background: transparent`，`border: 1.5px solid var(--color-text-tertiary)`

#### 连接线（.pipeline-connector）
- `width: 12px`，`height: 1.5px`
- 两种视觉态：
  - **completed**（连接两个已完成阶段）：`background: var(--color-success)`
  - **pending**（连接未完成阶段）：`background: var(--color-border)`

#### 阶段标签行
- `display: inline-flex`，`justify-content: space-between`，`width: 100%`
- 三个 `<span class="micro-label">`：解析 / 抽取 / 融合
- 颜色统一 `var(--color-text-tertiary)`
- 不随圆点状态变色（保持安静）

#### FAILED 回退
- 当 `status === 'FAILED'` 时，不渲染圆点步骤条
- 渲染红色胶囊徽章（复用 `StatusBadge` 的 FAILED 配置）：「失败」
- 徽章旁显示 `failReason` 截断文字（若有），hover tooltip 展示完整原因

#### DELETING 处理
- 当 `status === 'DELETING'` 时，渲染灰色胶囊徽章：「删除中」

#### UPLOADED 处理
- 文件尚未进入管线，三个圆点全为 pending（○○○）
- 阶段标签正常显示
- 但此时轮询不启动（AC-2），所以此态仅在页面初始加载时短暂出现

#### Tooltip（hover）
- 使用 Naive UI `NTooltip` 或 `n-popover`
- 内容：
  - **标题**（`label` 类）：当前精确状态名（如「抽取中」）
  - **描述**（`supporting` 类）：阶段说明（如「LLM 正在从文档中抽取知识点和关系边」）
- 触发：hover 圆点行，delay 300ms
- 位置：`placement="top"`

### 6.2 图谱页文档选择器状态联动

#### 下拉选项增强（GraphVisualizePage.vue）
- 使用 Naive UI `NSelect` 的 `render-label` 或 `render-option` slot
- 每个选项格式：`文档名.pdf (ID: 1)` + 右侧状态标识

#### 状态标识规范
| 文档状态 | 显示 | 可选？ |
|---------|------|--------|
| EXTRACTED / COMPLETED | 绿色圆点 `●` + 「已完成」 | ✅ 正常可选 |
| EXTRACTING | 蓝色圆点 `◉` + 「抽取中」 | ❌ disabled |
| FUSING | 蓝色圆点 `◉` + 「融合中」 | ❌ disabled |

- 圆点与管线指示器的 completed/active 态一致（复用同一颜色 token）
- disabled 选项显示为灰色文字，不可点击

### 6.3 非终态提示条（GraphVisualizePage.vue）

- 位置：`GraphCanvas` 上方，`BaseCard` 内部顶部
- 样式：Naive UI `NAlert` 或自定义 info 条
  - `background: var(--color-brand-veil)`（brand 色 12% 透明度）
  - `border-left: none`（**遵守 ui-anti-patterns：禁止 > 1px 彩色侧条**）
  - 改为顶部 2px brand 色细线替代
  - 文字：`supporting` 类，`color: var(--color-text-secondary)`
- 内容：「该文档图谱数据可能不完整 — 融合尚未完成。建议等待融合完成后刷新。」
- 关闭：提供 × 关闭按钮（不自动消失）
- 显示条件：`document.status === 'EXTRACTED'`（融合未完成或失败回退）

## 7. Do's and Don'ts

### Do

- ✅ 圆点颜色仅通过 CSS `transition` 渐变切换（不突变）
- ✅ 进行中动画使用 `opacity` 属性（GPU 友好，不触发 layout）
- ✅ 连接线仅两种颜色（已完成/未完成），无第三种中间态颜色
- ✅ 阶段标签始终灰色，不抢圆点注意力
- ✅ 所有尺寸使用现有 token 或硬编码小数值（6px/1.5px），不引入新 token
- ✅ FAILED 状态文字旁显示可操作的 failReason 信息

### Don't（除了通用 anti-patterns，本项目额外禁止）

- ❌ **禁止在步骤条中使用彩色侧条**（`border-left > 1px` 样式 — 命中 AI dashboard 反模式）
- ❌ **禁止圆点使用渐变或 glow shadow**（保持扁平极简）
- ❌ **禁止连接线使用动画**（只有圆点能动，线是静态的）
- ❌ **禁止在非终态提示条中使用彩色 `border-left`**（改用顶部细线）
- ❌ **禁止步骤条超过 3 个阶段**（上传瞬间完成跳过了，不需要展示）
- ❌ **禁止在 PARSED/EXTRACTED 稳定态时圆点继续动画**（只有 `*ING` 态动）
- ❌ **禁止圆点使用 emoji**（●◉○ 只是设计稿表示，代码用 CSS 渲染）

## 8. 占位符策略

| 缺的东西 | 本项目有什么？ | 缺时用什么占位 | 禁什么 |
|---------|-------------|-------------|--------|
| 图标 | Naive UI 内置图标 | 不适用 — 本组件无独立图标需求 | emoji 充图标 |
| 失败原因文字 | `TextbookVO.failReason` 字段（后端已有） | 截断至 30 字 + hover tooltip 完整展示 | 编造失败原因 |
| 管线阶段描述文字 | 无后端字段 | 前端硬编码中文描述（UI-DESIGN 已定义） | — |
| 数据 | — | 不适用 | — |

## 9. 反 AI-slop 自检结果

逐条对照 `@flow-kit/reference/ui-anti-patterns.md` 强制禁忌：

- [x] **字体类**：未命中 — 使用现有 DM Sans + PingFang SC，未引入 Inter/Roboto/Space Grotesk
- [x] **颜色类**：未命中 — 无纯黑纯白、无渐变、无紫色/霓虹青、单强调色（brand blue）、语义色仅用于语义
- [x] **阴影类**：未命中 — 圆点无阴影，tooltip 用 Naive UI 默认（轻阴影），card 保持平面 at rest
- [x] **边框类**：命中 ⚠️ — **`border-left > 1px 作彩色侧条`**：非终态提示条最初考虑过此方案，已改为顶部 2px 细线替代（见 §6.3）。管线指示器本身无此问题
- [x] **动效类**：未命中 — 仅使用 `opacity` 动画，无 bounce/elastic，`prefers-reduced-motion` 已全局支持
- [x] **布局类**：未命中 — 无卡片嵌套、无统一大小网格、非线性间距 scale 已存在
- [x] **文案类**：未命中 — 阶段标签用中文短词（"解析/抽取/融合"），按钮保持动词
- [x] **组件类**：未命中 — 圆点不是 button，无 form/placeholder 问题，tooltip 仅桌面端（AC 无移动端要求）

**例外声明**：无命中需豁免的条目。

## 10. 触发任务

下一步进入 `3-task` 阶段时，第一批 UI 任务：

- **T-UI-01**：新建 `StatusPipeline.vue` 组件（管线进度指示器），按 §6.1 规约实现
- **T-UI-02**：`FileManagePage.vue` 状态列替换 `StatusBadge` 为 `StatusPipeline`
- **T-UI-03**：`GraphVisualizePage.vue` 文档选择器增加状态标识 + disabled 逻辑（§6.2）
- **T-UI-04**：`GraphVisualizePage.vue` 新增非终态提示条（§6.3）
- **T-UI-05**：`fileStore.ts` 轮询策略修复（AC-1~AC-4，纯逻辑变更）