---
name: GraphNexus 图谱可视化组件
description: 继承 frontend-ui 极简冷蓝工具面板设计系统，为图谱画布、交互反馈和图例/面板定义视觉规则。图谱是唯一"有颜色"的区域，节点/边颜色按类型编码，chrome 退到背景。

colors:
  brand: "oklch(0.55 0.18 250)"         # 选中态高亮、聚焦环
  brand-veil: "oklch(0.55 0.18 250 / 0.12)"  # 搜索匹配底、邻域展开底
  canvas-bg: "oklch(1 0 0)"             # 画布背景 = surface
  canvas-border: "oklch(0.90 0.005 95)" # 画布边框 = border
  dim-opacity: 0.15                       # 非高亮节点/边透明度
  overlay-bg: "oklch(1 0 0 / 0.92)"     # tooltip 背景

typography:
  node-label: "'PingFang SC', 'Microsoft YaHei', sans-serif"
  edge-label: "'DM Sans', 'PingFang SC', sans-serif"
  tooltip-title: "'DM Sans', 'PingFang SC', sans-serif"
  tooltip-body: "'PingFang SC', 'Microsoft YaHei', sans-serif"
---

# UI Design: 图谱可视化组件

## 0. 视觉语汇对齐

**Brownfield**。本项目已有完整的 `frontend-ui` 设计系统（tokens.css + global.css + 8 个通用组件）。本次仅新增图谱画布及配套交互 UI，**全部视觉决策继承现有 token**，不引入新颜色/字体/间距词汇。

### 观察报告（已校准）

- **主色**：冷蓝 `oklch(0.55 0.18 250)` — 仅用于选中态、聚焦环、搜索匹配底
- **中性色**：画布 `oklch(1 0 0)` + 边框 `oklch(0.90 0.005 95)` + 侧边栏深底 — 全部复用 tokens.css
- **hover**：`translateY(-1px)` + 颜色加深，expo-out 缓动（`cubic-bezier(0.16, 1, 0.3, 1)`），150-300ms
- **elevation**：工具提示/悬浮卡片使用 `0 2px 8px rgba(0,0,0,0.08)`（token `--shadow-hover-lift`）
- **图标**：Lucide Vue，stroke 风格，16-20px
- **文案**：中文，动词为主（"搜索节点" "展开邻域"）

> ✅ 用户确认：观察准确，无修正。

---

## 1. 美学北极星

> **Data-First Minimalism（图谱特化）** — 图谱画布本身是"数据说话"的区域。节点/边的颜色编码是信息层（information layer），不是装饰层（decoration layer）。所有交互反馈（选中、高亮、dim）服务于数据探索效率，不服务于视觉愉悦。

### 4 个决策问题

- **目的**：教师/管理员探索知识图谱，核心动作 = 搜索→定位→展开→理解关联。不是"浏览艺术品"。
- **调性**：**极简（Minimal）** — 从 CHANGE.md 继承，与 frontend-ui 一致。
- **约束**：G6 v5 WebGL 渲染器；画布内元素（节点/边/标签）由 G6 样式规则控制，画布外元素（工具栏/图例/面板）使用 Vue + Tailwind + 现有 tokens.css
- **差异化**：图谱是唯一"有颜色"的页面区域。6 种节点色 + 5 种边色 = 11 种色相，但每个色都有数据语义（KnowledgePoint = 蓝，Student = 绿，MASTERS = 黄...），不是装饰色。

### v0 确认摸路

- **已确认的假设**（用户说 go）：
  - 画布背景纯白、hairline 边框、rounded 8px
  - 节点颜色/大小沿用现有 `NODE_COLORS` / `NODE_SIZES` 常量
  - 选中态冷蓝 3px 边框 + 外发光，与全局 focus ring 对齐
  - dim opacity 0.15
  - tooltip 白色卡片 + hairline 边框 + supporting 字号
  - 图例面板在画布下方，水平排列，不浮动在画布内
  - 详情面板右侧滑出 320px，同 BaseCard
  - 搜索框复用 BaseInput
  - 视图切换用分段按钮（segmented button）
  - 不做 dark mode
- **用户指出的偏差**：无。

---

## 2. 图谱专用颜色系统

> 全局 token 见 `tokens.css`。本节仅定义图谱画布内的数据颜色映射。

### 节点颜色（按 nodeType · 信息层）

| 节点类型 | 颜色 | 色值 | 语义 |
|---------|------|------|------|
| KnowledgePoint | 蓝 | `#4A90D9` | 核心枢纽 — 知识点 |
| Student | 绿 | `#52C41A` | 主体 — 学生 |
| Exam | 琥珀 | `#FAAD14` | 事件 — 考试 |
| KnowledgeCategory | 紫 | `#722ED1` | 分类 — 知识类别 |
| Document | 灰 | `#BFBFBF` | 源 — 文档 |
| Entity | 中灰 | `#8C8C8C` | 片段 — 原文实体 |

> 这些值来自现有 `graphAdapter.ts` 中的 `NODE_COLORS` 常量，**本次不改动**。未来如需对齐 OKLCH，作为独立 change 处理。

### 边颜色（按 edgeType · 信息层）

| 边类型 | 颜色 | 色值 | 线型 |
|--------|------|------|------|
| PREREQUISITE_OF | 蓝 | `#4A90D9` | solid |
| ALIGNED_TO | 绿 | `#52C41A` | solid |
| MASTERS | 琥珀 | `#FAAD14` | solid |
| TESTED | 橙 | `#FF7A45` | solid |
| CHILD_OF | 紫 | `#722ED1` | dashed |
| BELONGS_TO | 灰 | `#8C8C8C` | dashed |
| REFERENCES | 灰 | `#8C8C8C` | dashed |

### 交互态颜色

| 状态 | 节点 | 边 |
|------|------|-----|
| **default** | 按类型色，opacity 1.0 | 按类型色，opacity 0.6 |
| **hover** | 外发光 `brand-veil` + scale 1.1 | opacity 1.0 + 宽度 2× |
| **selected** | 3px `brand` 边框 + `brand-veil` 外发光 | — |
| **dimmed** | opacity 0.15 | opacity 0.08 |
| **highlighted** | opacity 1.0 + 同类型色 2px 外发光 | opacity 1.0 + 宽度 2.5× |
| **path-highlighted** | opacity 1.0 + `brand` 2px 外发光 | opacity 1.0 + `brand` 色 + 宽度 3× |

---

## 3. 图谱画布容器

```
┌─ .graph-canvas ─────────────────────────────────────────┐
│ background: var(--color-surface)  /* oklch(1 0 0) */    │
│ border: 1px solid var(--color-border)                    │
│ border-radius: var(--rounded-md)  /* 8px */              │
│ min-height: 500px                                        │
│ overflow: hidden                                         │
│                                                          │
│  ┌─ G6 WebGL Canvas ────────────────────────────────┐   │
│  │                                                    │   │
│  │  节点/边由 G6 样式规则渲染，CSS 不穿透              │   │
│  │                                                    │   │
│  └────────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─ 空态 ───────────────────────────────────────────┐   │
│  │ [icon: 搜索 48px text-tertiary]                    │   │
│  │ 选择文档以查看知识图谱子图                           │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌─ 加载态 ──────────────────────────────────────────┐  │
│  │ skeleton: brand-veil 底色 + shimmer 动画            │   │
│  └───────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## 4. 关键组件规约

### 4.1 GraphToolbar

```
┌─ GraphToolbar ────────────────────────────────────────────┐
│ h=48px, padding x=0, y=sm(8px)                           │
│ display: flex; gap: sm(8px); align-items: center          │
│                                                           │
│ [ 🔍 搜索节点...           ]  [ 文档子图 | 剪枝子图 | 全量 ] │
│  └─ BaseInput 复用         └─ Segmented Toggle, 3 段       │
│     w=280px                     选中段: brand 实底白字      │
│     placeholder: text-tertiary  未选中: 透明底 text-secondary│
│     rounded: sm                  hover: brand-veil 底      │
│     height: 36px                font: label 0.75rem        │
│                                                           │
│  搜索框右侧: 匹配数量 badge（仅搜索激活时显示）              │
│  badge: rounded-full, text-tertiary, supporting            │
└───────────────────────────────────────────────────────────┘
```

### 4.2 GraphLegend

```
┌─ GraphLegend ─────────────────────────────────────────────┐
│ padding: sm(8px) 0; border-top: 1px solid border          │
│                                                           │
│  ┌─ 节点类型 ───────────────────────────────────────┐    │
│  │ label 0.75rem text-secondary uppercase           │    │
│  │ ☑ ● KnowledgePoint  ☑ ○ Student  ☑ ● Exam       │    │
│  │ ☑ ● Entity          ☑ ● Category ☑ ● Document    │    │
│  └──────────────────────────────────────────────────┘    │
│  ┌─ 边类型 ────────────────────────────────────────┐     │
│  │ ☑ ── MASTERS  ☑ ── PREREQUISITE  ☑ ── ALIGNED   │     │
│  │ ☑ --- CHILD_OF  ☑ ── TESTED  ☑ --- REFERENCES    │     │
│  └──────────────────────────────────────────────────┘    │
│                                                           │
│  每个 checkbox 项:                                         │
│    - w=4px 色块 + 类型名（label 0.75rem）                  │
│    - 色块 rounded-sm，边类型用线段 + 线型示意               │
│    - 取消勾选 → 色块变灰 opacity 0.3 → 画布对应类型隐藏     │
└───────────────────────────────────────────────────────────┘
```

### 4.3 NodeDetailPanel

```
┌─ NodeDetailPanel ─────────────────────────────────────────┐
│ position: fixed; right: 0; top: 0; bottom: 0              │
│ width: 320px; background: surface                         │
│ border-left: 1px solid border                             │
│ box-shadow: var(--shadow-card-lifted)                     │
│ padding: lg(24px)                                         │
│ transition: transform 300ms ease-out                      │
│   └─ open: translateX(0); closed: translateX(100%)        │
│ z-index: 40 (above graph, below modal)                    │
│                                                           │
│ ┌──────────────────────────────────────────────────┐      │
│ │ KnowledgePoint                          [✕]     │      │
│ │ ─────────────────────────────────────           │      │
│ │ label: supporting 0.875rem text-secondary        │      │
│ │ value: body 1rem text-primary                    │      │
│ │                                                  │      │
│ │ ID               quadratic-function-vertex       │      │
│ │ 类型              KnowledgePoint                  │      │
│ │ 名称             二次函数顶点坐标                  │      │
│ │ 描述             二次函数 y=ax²+bx+c ...          │      │
│ │ (properties 动态渲染)                             │      │
│ │                                                  │      │
│ │ [ 展开邻域 ]  ← Primary Button, w=100%           │      │
│ └──────────────────────────────────────────────────┘      │
│                                                           │
│ 属性为空时显示 "无额外属性" supporting text-tertiary        │
└───────────────────────────────────────────────────────────┘
```

### 4.4 PruningMetaPanel

```
┌─ PruningMetaPanel（仅剪枝子图视图显示）──────────────────┐
│ 位于画布上方或侧边，与 GraphToolbar 并列                   │
│ background: brand-veil; rounded-md; padding: sm(8px) md(16px)│
│                                                           │
│ 策略: StudentDiagnosis  |  阈值: 0.6  |  跳数: 2           │
│ 节点: 47  |  边: 83  |  截断: 否                            │
│                                                           │
│ font: supporting 0.875rem; label inline label+value 对     │
│ truncated=true 时截断节点名以 tag 形式列出                  │
└───────────────────────────────────────────────────────────┘
```

### 4.5 Graph Nodes（G6 样式 · 非 CSS）

```
节点:
  - 形状: circle (所有类型统一圆形)
  - 大小: 28-40px，按 NODE_SIZES[type] 映射
  - 填充: NODE_COLORS[type]
  - 边框: 2px white（default）/ 3px brand（selected）
  - 标签: node-label 字体 11px，color #333
  - 标签位置: 节点下方，偏移 6px
  - hover: scale 1.1 + brand-veil 外发光，duration 150ms

边:
  - 宽度: 1.5px（default）/ 2.5px（highlighted）/ 3px（path）
  - 颜色: EDGE_COLORS[type]
  - 线型: solid / dashed 按类型
  - 箭头: target 端三角形，scale 0.8
  - 曲线: bezier（smooth）
  - 标签: 9px edge-label，color #666
  - 标签背景: white + opacity 0.8 + padding 2px
  - hover: 宽度 2× + opacity 1.0
```

### 4.6 Tooltip（G6 内置 · 非 Vue 组件）

```
Tooltip:
  - 触发器: node hover（300ms delay 防误触）
  - 容器: 白色背景，1px border 边框，rounded-sm(4px)
  - 内边距: xs(4px) sm(8px)
  - 阴影: hover-lift
  - 内容: 节点类型名 + ID（前 20 字符）
  - 字体: supporting 0.75rem text-primary
  - 位置: 节点上方 12px，智能翻转防溢出
```

---

## 5. 交互反馈规格

### 搜索定位

1. 用户输入 ≥ 2 字符 → 300ms 防抖 → 内存搜索匹配 label/id
2. 下拉建议列表：白色底，hairline 边框，max-height 200px 滚动，每项 32px
3. 选中结果 → 画布平移至节点居中 → 节点 selected 态 → 其余 dim
4. Escape → 清除搜索 → 清除高亮 → 所有节点恢复 default

### 类型筛选

1. 取消勾选 → 对应类型节点/边立即 fade out（G6 `hide()` 或 opacity 动画，150ms）
2. 重新勾选 → fade in
3. 全部取消 → 画布显示空态 "所有类型已隐藏"

### 邻域展开

1. 单击节点 → 选中态 + NodeDetailPanel 滑出
2. 点击「展开邻域」→ 1 跳邻居 + 连接边 highlighted → 其余 dim
3. 再次点击「收起」→ 清除高亮 → 仅保留节点选中
4. 点击另一节点 → 切换选中，清除前一个的展开态

### 路径高亮

1. 选中节点 A → Ctrl/Cmd + 点击节点 B
2. A↔B 最短路径（节点 + 边）→ path-highlighted 态 → 其余 dim
3. Escape → 清除路径高亮 → 仅保留 A 选中

---

## 6. Do's and Don'ts（图谱特定）

### Do

- 节点颜色 + 形状双重编码（圆形大小差异 + 颜色差异，色觉障碍友好）
- 交互反馈使用 150-300ms expo-out 过渡（与全局动效一致）
- 空态/加载态/错误态三态俱全，不给用户"卡死"的错觉
- 图例仅列出图中实际存在的类型（动态生成，不写死 11 项）

### Don't

- 禁止在图谱画布内使用非数据颜色的装饰元素（渐变背景、粒子效果、动画闪点）
- 禁止节点使用 emoji 或图片作为图标（纯色圆点 + 文字标签）
- 禁止画布内浮动图例（遮挡数据） — 图例在画布外
- 禁止节点标签使用超过 12px 的字号（画布密集时重叠严重）
- 禁止动效使用 bounce/elastic 缓动（极简调性不走 playful 路线）
- 禁止在 dimmed 态完全隐藏节点（opacity 0.15 保留空间感知）

---

## 7. 占位符策略

| 缺的东西 | 正确做法 | 禁止做法 |
|---------|---------|---------|
| 图谱数据 | 空态提示 "选择文档以查看知识图谱子图" + 上传引导 | 编造假节点/假边 |
| 节点属性 | 详情面板显示 "无额外属性"（剪枝子图 properties 可能为空 Map） | 编造属性值 |
| 剪枝元信息 | 面板显示从 `PruningMeta` 真实读取的值 | 硬编码示例值 |
| 全量图谱 API | v1 若 API 未就绪 → 切换按钮 disabled + tooltip "后端 API 开发中" | 用文档子图冒充全量图谱 |
| 图标 | Lucide Vue（Search、Filter、X、ChevronRight、Network） | emoji |

---

## 8. 反 AI-slop 自检

逐条对照 `ui-anti-patterns.md` 强制禁忌段：

- [x] **字体类**：图谱内标签使用 PingFang SC / DM Sans（继承全局），无 Inter/Roboto/Arial
- [x] **颜色类**：节点/边颜色为数据编码（11 色有语义），非装饰色；画布容器无渐变；无纯黑纯白
- [x] **阴影类**：画布容器 at rest 无阴影（仅 hairline 边框）；tooltip 阴影 alpha=0.08 ≤ 0.15
- [x] **边框类**：无彩色侧条；无渐变边框；无玻璃拟态
- [x] **动效类**：expo-out 缓动；仅动 transform + opacity；无 scroll-jacking
- [x] **布局类**：图例在画布外；面板 slide-out；不卡套卡
- [x] **文案类**：中文工程向（"搜索节点" "展开邻域"）；无 Lorem ipsum
- [x] **组件类**：按钮平面 at rest → hover lift；form label 不在 placeholder 里
- [x] **数据类**：空态/加载态/错误态三态俱全；不编造数据

**未命中任何强制禁忌。**

---

## 9. 触发任务

下一步进入 `3-task` 阶段时，第一批 UI 任务建议：

- T-UI-G1：物化图谱专用 CSS variables（画布容器、面板、dim opacity）→ 追加到 tokens.css 或新建 `tokens-graph.css`
- T-UI-G2：实现 GraphToolbar 组件（搜索框 + SegmentedToggle）
- T-UI-G3：实现 GraphLegend 组件（动态类型 checkbox 组）
- T-UI-G4：实现 NodeDetailPanel 组件（slide-out 面板 + properties 渲染）
- T-UI-G5：实现 PruningMetaPanel 组件（剪枝元信息条）
- T-UI-G6：定义 G6 节点/边样式常量（从现有 NODE_COLORS/EDGE_COLORS 迁移 + 交互态样式）
