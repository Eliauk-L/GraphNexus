---
name: GraphNexus
description: 极简管理后台 — 蓝白灰为主色，平面静态 + 微抬 hover，Lucide 图标，工程向文案

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

typography:
  display:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
  body:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  mono:
    fontFamily: "'JetBrains Mono', 'SF Mono', 'Cascadia Code', monospace"
  label:
    fontWeight: 500
    textTransform: uppercase
    letterSpacing: "0.05em"

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

# UI Design: 知识点图谱可视化增强

## 0. 视觉语汇对齐（brownfield）

### 0.1 观察报告（代码为源）

- **Token 源**：`frontend/src/assets/tokens.css` — 完整 design token 体系
- **主色实际比例**：品牌蓝 oklch(0.55 0.18 250) 约占 3%，仅在 primary button / 链接 / focus 边框使用
- **中性色**：背景偏米白 oklch(0.985 0.002 95)，不纯白；文字不纯黑 oklch(0.15 0.005 95)
- **hover/focus 反馈**：统一 `translateY(-1px)` + 颜色过渡到品牌蓝，`var(--duration-fast)` 150ms
- **动效语言**：CSS transition 为主，`cubic-bezier(0.16, 1, 0.3, 1)`；loading 用 `0.8s linear spin` 关键帧
- **elevation 层级**：2 级（hover-lift + card-lifted/accent-glow），无多层纵深
- **卡片密度/rounded**：稀密度、内边距 24px、rounded-lg 12px
- **图标库**：`@lucide/vue` v1.20+，stroke-width 默认 1.5
- **文案调性**：工程向，中文标签，动词为主（"展开邻域"/"触发抽取" 而非 "探索更多"）

### 0.2 用户校准结论

- 用户确认：以上全部正确 ✓
- 本次完全沿用既有视觉语汇，新增组件严格匹配既有 token

### 0.3 应用策略

- **沿用**：颜色系统、字体栈、间距 scale、圆角词汇、缓动曲线、阴影、图标库——全量照搬
- **延伸**：新增 Toggle 开关组件（纯 CSS，匹配现有 hover 动效）、MetricsPanel（复用 NodeDetailPanel 的 slide 动画 + 固定定位模式）
- **打破**：无

---

## 1. 美学北极星

**Minimal Admin** —— 蓝白灰主色调，平面 at rest + 1px 微抬 hover，Lucide 极简图标，工程向中文文案。视觉重心全在数据（图谱节点/边/度量数值），UI 架子完全退后。

### v0 确认摸路

- **已确认的假设**：
  - Toggle 用纯 CSS 实现（不引入 Naive UI Switch 组件）
  - 排行面板与 NodeDetailPanel 互斥——打开排行自动关闭详情
  - 排行面板 PR 列在 PageRank 开关 OFF 时整列隐藏
  - 面板宽度固定 320px（与 NodeDetailPanel 一致）
  - 图例底部新增度量映射说明行
- **用户指出的偏差**：无

---

## 2. 4 个决策问题

- **目的**：图谱可视化页面的功能增强。给教师/管理员用的管理后台，核心动作是查看学科知识点依赖图 + 快速定位枢纽知识点
- **调性**：极简 Minimal — 已锁定（来自 `frontend-ui` CHANGE）
- **约束**：Vue 3 + Naive UI（BaseSelect 底层）+ AntV G6 v5；性能要求 ≤300 节点 3s 内渲染；桌面端 ≥1280px；WCAG 键盘可访问
- **差异化**：度量数据"嵌在图上而非单独报表"——节点大小直接告诉你什么是枢纽，无需先看表格再回想图

---

## 3. 颜色系统（本次新增/延伸）

### 度量映射扩展色阶（仅 PageRank ON 时生效）

PageRank 百分位 → 5 档暖色梯度，替代默认的 KnowledgePoint 蓝色 `#3B82F6`：

```
百分位         颜色              OKLCH                      用途
0~20%（最低）  cold-blue        沿用 NODE_COLORS.KnowledgePoint (#3B82F6)   — 低重要性，接近默认蓝
20~40%         light-warm       oklch(0.65 0.10 180)        — 中低
40~60%         neutral-warm     oklch(0.60 0.15 120)        — 中等
60~80%         warm-orange      oklch(0.55 0.18 70)         — 中高
80~100%（最高） deep-orange      oklch(0.50 0.22 50)         — 最高重要性，突出
```

> 色阶定义为 JavaScript 常量（`frontend/src/views/graph/constants.ts`），不写入 `tokens.css`（度量相关仅图谱页面使用，不污染全局 token）。

### Toggle 颜色

- **OFF 轨道**：`var(--color-border)` — 灰边框
- **ON 轨道**：`var(--color-brand)` — 品牌蓝
- **滑块**：`var(--color-surface)` — 纯白，`box-shadow: var(--shadow-hover-lift)`

---

## 4. 字体系统（沿用现有，不新增）

本次不新增字体层次。新组件使用的字体角色：

| 组件位置 | 使用 token | 说明 |
|---|---|---|
| MetricsPanel 标题 | body (1rem, 500) | "度量排行" |
| MetricsPanel 表头 | label (0.75rem, 500, uppercase) | "入度"/"出度"/"PR" |
| MetricsPanel 数值 | mono (JetBrains Mono) | 度量数值对齐 |
| Toggle 标签 | body (0.875rem) | "PageRank" |
| 度量图例 | supporting (0.75rem, --color-text-secondary) | 映射规则说明 |

---

## 5. 间距 & 圆角 & 动效（沿用现有）

新组件使用的具体值：

| 场景 | 值 | Token |
|---|---|---|
| MetricsPanel 内边距 | 24px | `--spacing-lg` |
| 排行表行高 | 36px | 固定高度 |
| 排行表单元格内边距 | 8px 12px | `--spacing-sm` `--spacing-md` |
| Toggle 轨道尺寸 | 30×18px | 固定 |
| Toggle 滑块尺寸 | 14×14px | 固定 |
| MetricsPanel slide 动效 | 300ms | `var(--duration-base)` |
| Toggle 切换动效 | 150ms | `var(--duration-fast)` |
| 排行按钮 hover | 150ms, translateY(-1px) | 与其他按钮一致 |

---

## 6. 关键组件规约

### 6.1 Button "度量排行"（工具栏新增）

- **类型**：Toolbar Button（非 Primary/Secondary）
- **形状**：`rounded-sm` (4px)，高 36px
- **at rest**：透明背景 + `1px solid var(--color-border)` + 文字 `var(--color-text-secondary)`
- **hover**：边框 → `var(--color-brand)`，文字 → `var(--color-brand)`
- **图标**：`@lucide/vue` 的 `BarChart3`（已在 AppLayout 中使用）
- **内边距**：`8px 12px`
- **参考**：与现有全屏按钮 `.btn-fullscreen` 同风格

### 6.2 Toggle · PageRank 开关（新增）

- **类型**：纯 CSS Toggle Switch（不依赖 Naive UI）
- **尺寸**：轨道 30×18px，滑块 14×14px
- **OFF 状态**：轨道 `var(--color-border)` 背景，滑块 `var(--color-surface)` 靠左，`shadow-hover-lift`
- **ON 状态**：轨道 `var(--color-brand)` 背景，滑块靠右
- **过渡**：滑块 `translateX` + 轨道 `background-color`，`var(--duration-fast)` `var(--ease-out)`
- **标签**："PageRank" 文字 0.875rem，`var(--color-text-secondary)`，位于 Toggle 右侧 8px
- **focus**：轨道外 2px `var(--color-brand-veil)` 光环
- **参考**：Linear 风格的极简开关，无 ON/OFF 文字嵌入轨道内

### 6.3 MetricsPanel · 度量排行面板（新增）

- **布局**：右侧固定定位 320px 宽，全高，`var(--color-surface)` 背景
- **阴影**：`var(--shadow-card-lifted)`
- **结构**：
  ```
  ┌─ Header ─────────────────────────────┐
  │  h3 "度量排行"               ✕ close │
  ├─ Divider (1px border) ──────────────┤
  │  排序: [Dropdown ▼]                  │
  ├─ Table ──────────────────────────────┤
  │  # │ 知识点 │入度│出度│总度│ PR* │
  │  ──┼────────┼───┼───┼───┼─────│
  │  1 │ 二次函数│ 7 │ 5 │12 │.234│
  │  ...                                 │
  └──────────────────────────────────────┘
  ```
- **表头样式**：`label` token（0.75rem, 500, uppercase），文字 `var(--color-text-secondary)`
- **数据行样式**：`body` token（0.875rem），知识点名 `var(--color-text-primary)`，数值 `var(--font-mono)` 右对齐
- **行 hover**：背景 `var(--color-brand-veil)`，cursor pointer
- **点击行**：emit `select-node` 事件 → 图谱节点高亮聚焦
- **排序下拉**：复用 Naive UI NSelect（与 BaseSelect 一致），选项：总度数↓ / 入度↓ / 出度↓ / PageRank↓
- **PR 列显隐**：`v-if="pagerankEnabled"`，整列移除/出现（非 `v-show` 灰掉）
- **空状态**：面板中心显示 "该学科暂无度量数据"
- **错误状态**：面板中心显示 "度量数据暂不可用"
- **slide 过渡**：复用 `NodeDetailPanel` 的 `.slide-enter-active/.slide-leave-active`（300ms `cubic-bezier(0.16, 1, 0.3, 1)`）
- **与 NodeDetailPanel 互斥**：打开 MetricsPanel 时若 NodeDetailPanel 可见，先关闭 NodeDetailPanel

### 6.4 NodeDetailPanel · 度量指标区（已有组件扩展）

- **位置**：现有属性列表下方，`detail-divider` 分隔线之后，`detail-actions` 展开邻域按钮之前
- **标题**："度量指标"，`label` token（0.75rem, 500, uppercase, `var(--color-text-secondary)`）
- **字段布局**：复用 `.detail-field` 模式（label + value 上下排列）
- **必显字段**：
  - 入度（InDegree）：整数
  - 出度（OutDegree）：整数
  - 总度（TotalDegree）：整数
- **条件字段**：
  - PageRank：4 位小数，`v-if="pagerankEnabled"`，整个 `.detail-field` 移除/出现
- **数值格式**：`var(--font-mono)`，`var(--color-text-primary)`

### 6.5 GraphLegend · 度量映射说明（已有组件扩展）

- **位置**：现有图例底部，1px divider 分隔线之后
- **内容**：
  - "节点大小 = 总度数"（始终显示）
  - "节点颜色 = PageRank 百分位" （仅 `pagerankEnabled` 时显示）
  - 显示 5 档色阶渐变色条（120×12px 水平条）+ "低" / "高" 标签
- **样式**：`supporting` token（0.75rem, `var(--color-text-secondary)`）

### 6.6 学科选择器（复用 BaseSelect）

- **组件**：直接复用 `BaseSelect`（底层 Naive UI NSelect）
- **位置**：工具栏左侧，与其他选择器同行
- **宽度**：200px
- **placeholder**："选择学科"
- **数据源**：`GET /api/v1/graph/subjects` 返回的字符串数组，映射为 `{ label: name, value: name }`
- **行为**：选中学科 → `viewMode='subject'` + 清空文档选择器 + 加载学科全景图

---

## 7. Do's and Don'ts（本项目特定）

### Do

- ✅ Toggle、MetricsPanel 的动效/间距/字体必须使用 `tokens.css` 中的 CSS 变量
- ✅ 度量色阶常量定义在 `constants.ts` 中（与 `NODE_COLORS` 同文件），不散落在组件内
- ✅ 排行面板数值用 `var(--font-mono)` 确保对齐
- ✅ 所有新 UI 元素支持键盘操作（Tab + Enter）
- ✅ 新的图标从 `@lucide/vue` 选取（已在 package.json 中）

### Don't（除了通用 anti-patterns，本项目额外禁止）

- ❌ **禁止在组件内硬编码颜色/间距/字体**——必须引用 `tokens.css` 变量或 `constants.ts`
- ❌ **禁止引入新的 UI 组件库**（如引入 Element Plus 的 Switch 替代纯 CSS Toggle）
- ❌ **禁止 Toggle 内嵌 ON/OFF 文字**——滑块 + 外置标签足够
- ❌ **禁止度量数值使用非等宽字体**——右对齐必须视觉对齐
- ❌ **禁止排行面板和详情面板同时可见**——同一侧 320px，必须互斥
- ❌ **禁止空状态显示红色错误**——空数据不是错误，应展示中性提示
- ❌ **禁止 emoji 用于任何 UI 元素**（占位、提示、按钮）

---

## 8. 占位符策略（反伪造）

| 缺的东西 | 本项目有什么？ | 缺时用什么占位 | 禁什么 |
|---|---|---|---|
| 图标 | `@lucide/vue`（已在 package.json） | 从 Lucide 选最接近的图标；若无合适则用 `<svg>` 几何形 | emoji / AI 自绘 SVG |
| 数据 | 后端 API 返回真数据 | loading 态用 `spin` 动画（已有 `.spin` 类）；失败用文字说明 | 编造度数值 / 知识点名 |
| 图片 | 无 | N/A — 本 change 不涉及图片 | — |

---

## 9. 反 AI-slop 自检结果

逐条对照 `@flow-kit/reference/ui-anti-patterns.md` 的"强制禁忌"段：

- [x] **字体类**：未命中。沿用既有 DM Sans + PingFang SC，非 Inter/Roboto/Arial
- [x] **颜色类**：未命中。主色仅 1 个 hue（蓝），中性色含 chroma 不纯灰，OKLCH 全量
- [x] **阴影类**：未命中。仅 2 级阴影，MetricsPanel 用既有 `card-lifted`，Toggle 滑块用 `hover-lift`
- [x] **边框类**：未命中。面板 hairline 1px `var(--color-border)`，无彩虹边框
- [x] **动效类**：未命中。150ms/300ms 两档，单一 `ease-out` 曲线，`prefers-reduced-motion` 已处理
- [x] **布局类**：未命中。固定侧边栏 + 内容区布局不变，新面板 320px 固定宽
- [x] **文案类**：未命中。中文工程向标签，无 lorem ipsum / "令人惊叹的"等营销词
- [x] **组件类**：未命中。无卡片套卡片、无多余 secondary button

**例外申请**：无。

---

## 10. 触发任务

下一步进入 `3-task` 阶段时，UI 相关任务作为第一批：

- **T-UI-01**：`constants.ts` 新增度量色阶常量 + 大小映射参数
- **T-UI-02**：`GraphToolbar.vue` 新增 PageRank Toggle + 度量排行按钮
- **T-UI-03**：新建 `MetricsPanel.vue` 组件（排行表 + 排序 + 空/错误态 + slide 动画）
- **T-UI-04**：`NodeDetailPanel.vue` 新增度量指标区
- **T-UI-05**：`GraphLegend.vue` 新增度量映射说明行