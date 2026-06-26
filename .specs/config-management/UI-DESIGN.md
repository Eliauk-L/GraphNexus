# UI-DESIGN: 系统配置页面（SettingsConfigPage）

- **Change ID**: `config-management`
- **关联**: `@.specs/config-management/DESIGN.md`、`@.specs/CONTEXT.md`
- **类型**: brownfield — 在既有管理后台中新增一个页面
- **视觉调性**: 极简 Minimal — 沿用既有（Linear/Vercel/Stripe 参照），已在 `CHANGE.md` 锁定

---

## 0. 视觉语汇对齐（brownfield · 步骤 1.5）

### 观察报告（用户已确认 ✅）

```
✅ 主色：oklch(0.55 0.18 250) 蓝紫色，仅用于 primary button / 链接 / 选中态
✅ 中性：bg=oklch(0.985 0.002 95)，surface=oklch(1 0 0)，不用纯黑纯白
✅ 字体：DM Sans(display) + PingFang SC(body) + JetBrains Mono(mono)，9 级排版
✅ 动效：cubic-bezier(0.16, 1, 0.3, 1) ease-out，150/300/600ms 三档
✅ elevation：3 级阴影，卡片平面 at rest
✅ 卡片：1px border + rounded-md(8px) + spacing-md(16px) padding
✅ 图标：@lucide/vue
✅ 组件库：Naive UI（NDataTable / NModal / NInput / NSelect / NSwitch / NTag）
✅ 页面模板：BaseCard(title) + NDataTable + NPagination + NModal preset="card" 480px
✅ 文案：工程向，动词为主
```

### 校准结论

本次新增 `SettingsConfigPage` **完全沿用既有视觉体系**，不引入新颜色、新字体、新动效曲线或新交互模式。页面布局对齐 `UserManagePage` 的 BaseCard + 列表 + Modal 模式，新增的 Tab 分类切换和固定底部 Apply 栏是此页面特有的功能需求（非新视觉模式）。

---

## 1. 美学北极星

### 4 个问题

- **目的**：ADMIN 在后台查看/修改/应用系统配置。核心动作：浏览配置 → 编辑 → 保存 → 应用
- **调性**：极简 Minimal — 从 CHANGE.md 读取，已锁定
- **约束**：桌面端 ≥1280px；Naive UI 组件库；Vue 3 + TypeScript；无 dark mode（v1）
- **差异化**：配置管理页面的"那一件事" — 修改值后不是即时生效，底部固定 Badge 提示"待应用"，给 ADMIN 一个明确的"确认窗口"，降低误操作风险

### v0 确认摸路

- **用户确认的假设**：配置列表不分页（≤ 40 条）、编辑用 NModal 弹窗、TEXT 用 textarea autosize、底部 Apply 栏固定、默认 Tab 选"业务参数"、侧边栏新增 Settings 图标入口
- **无偏差，直接 go**

---

## 2. Design Tokens（全量沿用 `tokens.css`）

本页面**不新增任何 token**。以下为引用清单：

| Token 类别 | 变量 | 值 | 用途 |
|-----------|------|-----|------|
| 主色 | `--color-brand` | `oklch(0.55 0.18 250)` | Apply 按钮、编辑图标、链接 |
| 背景 | `--color-bg` | `oklch(0.985 0.002 95)` | 页面底色 |
| 表面 | `--color-surface` | `oklch(1 0 0)` | BaseCard 背景 |
| 主文字 | `--color-text-primary` | `oklch(0.15 0.005 95)` | 配置项名称 |
| 次文字 | `--color-text-secondary` | `oklch(0.45 0.005 95)` | 当前值显示 |
| 弱文字 | `--color-text-tertiary` | `oklch(0.65 0.005 95)` | 默认值标注 |
| 边框 | `--color-border` | `oklch(0.90 0.005 95)` | 卡片/行分隔 |
| 语义·成功 | `--color-success` | `oklch(0.55 0.15 145)` | applied=true 状态 |
| 语义·警告 | `--color-warning` | `oklch(0.65 0.15 85)` | applied=false Badge |
| 间距-md | `--spacing-md` | `16px` | 卡片 padding、表单 gap |
| 间距-lg | `--spacing-lg` | `24px` | Tab 与列表间距 |
| 圆角-md | `--rounded-md` | `8px` | 卡片圆角 |
| 圆角-sm | `--rounded-sm` | `4px` | Badge 圆角 |
| 动效-base | `--duration-base` | `300ms` | Tab 切换过渡 |
| 缓动 | `--ease-out` | `cubic-bezier(0.16, 1, 0.3, 1)` | hover/焦点动画 |
| 字体-display | `--font-display` | `DM Sans, PingFang SC, ...` | 标题、Tab、Label |
| 字体-body | `--font-body` | `PingFang SC, Microsoft YaHei, ...` | 配置项正文 |
| 字体-mono | `--font-mono` | `JetBrains Mono, SF Mono, ...` | 配置值（NUMBER 类型） |

---

## 3. 关键组件规约

### 3.1 SettingsConfigPage — 页面布局

```
┌─ 侧边栏 ──┬─ 内容区（flex: 1, overflow-y: auto）────────────┐
│            │  <h1 class="headline">系统配置</h1>               │
│            │                                                   │
│            │  ┌─ NTabs ───────────────────────────────────┐   │
│            │  │ 业务参数  │ 提示词模板  │ 模型配置             │   │
│            │  └───────────────────────────────────────────┘   │
│            │                                                   │
│            │  ┌─ BaseCard（无 title，由 Tab 承担标题）─────┐   │
│            │  │                                             │   │
│            │  │  配置项列表（紧凑行布局）                      │   │
│            │  │  ┌ config_name ─── config_value ── [✎] ─┐  │   │
│            │  │  │ 融合匹配阈值      0.85              ✎  │  │   │
│            │  │  │ ...                                  │  │   │
│            │  │  └──────────────────────────────────────┘  │   │
│            │  │                                             │   │
│            │  └─────────────────────────────────────────────┘   │
│            │                                                   │
│            │  ← 底部留白 80px（给固定栏让位）→                   │
│            │                                                   │
│            │  ┌─ 固定底部栏（position: sticky, bottom: 0）─┐   │
│            │  │  bg: var(--color-surface)                  │   │
│            │  │  border-top: 1px solid var(--color-border) │   │
│            │  │  [⏺ 3 项待应用]          [应用配置] button  │   │
│            │  └──────────────────────────────────────────┘   │
└────────────┴──────────────────────────────────────────────────┘
```

**规则**：
- 头部 `headline` 类 `<h1>`，上下间距 `var(--spacing-lg)`
- NTabs 使用 Naive UI 默认样式，`type="line"`，active color = `var(--color-brand)`
- BaseCard 包裹列表，`padding: var(--spacing-md)`
- 底部固定栏：`position: sticky; bottom: 0; z-index: 10; padding: var(--spacing-md) var(--spacing-lg); display: flex; justify-content: space-between; align-items: center; background: var(--color-surface); border-top: 1px solid var(--color-border)`
- 页面整体 padding: `0 var(--spacing-lg)`

### 3.2 配置项行

```
┌──────────────────────────────────────────────────────────┐
│  config_name                config_value         [✎ 编辑] │
│  ─ 14px body, primary       ─ 14px mono/body    ─ label │
│  description（如有）                                      │
│  ─ 12px supporting, tertiary                             │
│                                                          │
│  ⚠ 未应用标记（如 DB 值 ≠ 运行值）                          │
│  ─ NTAG size=tiny type=warning "未应用"                   │
└──────────────────────────────────────────────────────────┘
```

**规则**：
- 行高 ≥ 48px（`min-height`），`padding: var(--spacing-sm) 0`
- 行底部分隔线 `border-bottom: 1px solid var(--color-border)`（最后一行无边线）
- 配置名用 `body` 类或 `font-size: 0.9375rem`（介于 body 和 supporting 之间），`color: var(--color-text-primary)`
- 配置值用 `mono` 类（NUMBER/BOOLEAN/STRING）或 `body` 类缩减（TEXT 显示前 N 字 + "..."）
- 编辑按钮：图标 `Pencil`（lucide），`color: var(--color-text-tertiary)`，hover 变 `--color-brand`，`cursor: pointer`
- `applied = false` 时行左侧 2px 宽的 `--color-warning` 竖条指示
- 未应用标记用 NTag `type="warning" size="tiny"`，文字"未应用"

### 3.3 编辑弹窗（NModal）

```
┌─────────────────────────────────────┐
│ 编辑：融合匹配阈值                    │  ← NModal title
│                                     │
│ 当前值    0.85                      │  ← supporting + mono
│ 默认值    0.85（yml 默认）            │  ← supporting + tertiary
│ 说明      KP 匹配相似度阈值，取值范围  │  ← supporting + tertiary
│           0~1，越大匹配越严格         │
│                                     │
│ ┌─ 新值 ─────────────────────────┐  │
│ │ [____0.80____]                 │  │  ← NInput / NInputNumber
│ └────────────────────────────────┘  │
│                                     │
│              [取消]  [保存]          │  ← BaseButton danger + primary
└─────────────────────────────────────┘
```

**按 configType 渲染不同编辑控件**：

| ConfigType | 控件 | Naive UI 组件 | 额外设定 |
|-----------|------|--------------|---------|
| NUMBER | 数字输入 | `NInputNumber` | `:min` / `:max` / `:step` 从 `validation_rule` JSON 读取 |
| STRING | 单行文本 | `NInput` | `:maxlength="256"` |
| BOOLEAN | 开关 | `NSwitch` | checked-value="true", unchecked-value="false" |
| TEXT | 多行文本 | `NInput type="textarea"` | `:autosize="{ minRows: 15, maxRows: 30 }"`，`font-family: var(--font-mono)` |

**规则**：
- NModal `preset="card"`，`style="width: 520px"`（比 UserManagePage 的 480px 略宽，适配 TEXT 类型）
- 弹窗内间距 `gap: var(--spacing-md)`，纵向 flex 布局
- 当前值/默认值/说明 用 `supporting` 类，颜色 `--color-text-tertiary`
- 底部按钮对齐 UserManagePage：`<NSpace justify="end">`，取消用 `BaseButton variant="danger"`，保存用 `BaseButton variant="primary"`

### 3.4 Apply 按钮与 Badge

```
┌──────────────────────────────────────────────┐
│  [⏺ N 项待应用]              [应用配置]       │
│   ─ NTag warning tiny        ─ BaseButton    │
│                               variant=primary │
│                               size=medium     │
└──────────────────────────────────────────────┘
```

**规则**：
- NTag：`type="warning" size="small" round`，仅在 `pendingCount > 0` 时显示
- Apply 按钮始终可见，`pendingCount === 0` 时 `disabled`（视觉上降低透明度但不可点击）
- Apply 点击后：按钮进入 `loading` 状态，请求完成后恢复，同时 Badge 消失
- Apply 成功 → `message.success('配置已应用，共更新 N 项')`
- Apply 失败 → `message.error('应用配置失败：' + error.userTip)`

### 3.5 字体层级（本页面映射）

| 层级 | 类 | 应用位置 |
|------|-----|---------|
| Headline | `.headline` | 页面标题"系统配置" |
| Title | `.title` | Tab 标签（Naive UI 默认） |
| Body | `.body` | 配置项名称、编辑弹窗标签 |
| Supporting | `.supporting` | 当前值/默认值/说明文字 |
| Label | `.label` | "应用配置"按钮文字、"编辑"按钮 |
| Mono | `.mono` | NUMBER/STRING 类型的配置值、TEXT 编辑区 |

---

## 4. Do's and Don'ts

### 本页面额外禁忌（基于极简调性 + 既有体系）

| ✅ Do | ❌ Don't |
|------|---------|
| 编辑弹窗 520px 宽，配置信息紧凑展示 | 弹窗内增加不必要的信息层级（如折叠面板、步骤条） |
| TAB 切换用 NTabs type="line"，颜色跟 `--color-brand` | 用卡片式 Tab 或 pill Tab（与既有无 Tab 页面的 BaseCard 风格冲突） |
| 未应用标记用 2px 竖条 + NTag warning tiny | 用背景色整行高亮（破坏极简卡片的平整感） |
| Apply 按钮 disabled 时降低 opacity 至 0.5 | 按钮消失或替换为其他元素（会让用户困惑"应用功能哪去了"） |
| 配置值 NUMBER/BOOLEAN 用等宽字体 `.mono` | 配置值用 display 字体或有衬线字体 |
| 弹窗标题用 NModal 的 `title` prop | 在弹窗 body 里自己写标题 |
| 底部 Apply 栏 `position: sticky; bottom: 0` | 用 `position: fixed`（会在侧边栏外溢出） |

### 交互行为禁忌

| ✅ Do | ❌ Don't |
|------|---------|
| PUT 修改后仅保存 DB，前端 `applied` 变 false | 修改后自动 Apply（违背用户 Q2=C 的选择） |
| TEXT 类型编辑框支持多行，至少可见 15 行 | TEXT 用单行 input（提示词可能数千字） |
| NUMBER 输入校验前后端各做一次 | 仅前端校验（API 可绕过） |
| Apply 操作有 loading 状态 + 成功/失败 Toast | Apply 静默执行无反馈 |

---

## 5. 占位符策略

| 缺的东西 | 正确做法 | 禁止做法 |
|---|---|---|
| 配置项列表为空 | 显示"暂无配置数据"空状态（NEmpty），含「加载失败？」链接→重试 | 显示假数据行 |
| 提示词文本为空 | 显示"未自定义，使用系统默认模板"文字 + 默认模板前 100 字预览 | 编辑框留白让用户猜 |
| 图标 | 侧边栏入口用 `Settings`（lucide），编辑按钮用 `Pencil`（lucide）| 用 emoji ⚙️✏️ |
| 数据 | 所有配置值来自 API 返回的真实数据 | 编造默认值 |

---

## 6. 反 AI-slop 自检

对照 `@flow-kit/reference/ui-anti-patterns.md`（未安装 uipro，用内置基线自检）：

- [x] 无 emoji 图标（全部用 Lucide）
- [x] 无 AI 生成的插画/头像/图片
- [x] 无"现代化""直观"等空洞形容
- [x] 颜色全部 OKLCH（延续 tokens.css）
- [x] 字体非 Inter/Roboto/Arial/system-ui（DM Sans + PingFang SC 是既有选定）
- [x] 无过度阴影/渐变/毛玻璃（延续 flat at rest + hover lift 模式）
- [x] 无 mock 数据（API 驱动，空状态用 NEmpty）
- [x] 间距和圆角使用既有 token scale，未新造数值
- [x] 动效用既有 `--ease-out` + `--duration-fast/base`，未自定义新曲线

**命中 0 条强制禁忌。** ✅