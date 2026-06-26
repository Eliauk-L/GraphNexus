# UI-DESIGN: LLM 意图识别 + HTML/SVG 输出 — 前端渲染组件

- **Change ID**: `llm-intent-recognition`
- **关联**: `@.specs/llm-intent-recognition/DESIGN.md`、`@.specs/llm-intent-recognition/REQUIREMENT.md`
- **作者**: AI（UI Director 角色）+ 人工 review

---

## 1. 美学北极星

### 1.1 四个问题

- **目的**：让教师/家长查看 LLM 生成的 HTML+SVG 诊断报告（含数据图表、知识图谱子图、数学公式），视觉上延续现有极简管理后台风格
- **调性**：**沿用** `frontend-ui` CHANGE 锁定的「极简（Minimal）— Linear/Vercel/Stripe」。本 change 仅新增一个内容渲染组件，不改变设计语言
- **约束**：桌面端 ≥1280px（V1 目标分辨率）；`tokens.css` 全部变量不可变；DOMPurify 白名单不可绕过；SVG 静态渲染无交互
- **差异化**：无需差异化——本组件是管理后台内部的内容渲染器，用户感知的是"诊断报告看起来清晰专业"，而非"这个渲染器组件真漂亮"

### 1.2 v0 确认记录

- v0 于 2026-06-22 经用户确认「继续」
- 假设全部确认为真：容器样式与 MarkdownViewer 一致 / SVG inline style 不被覆盖 / 无交互 / 无新 token / 无额外 UI 状态

---

## 2. 视觉语汇对齐（brownfield · 步骤 1.5）

### 2.1 观察报告（用户已确认）

| 维度 | 观察值 | 来源 |
|------|--------|------|
| 主色 | `oklch(0.55 0.18 250)` — 冷蓝，页面占比 ~3-5% | `tokens.css:9` |
| 背景 | `oklch(0.985 0.002 95)` 米白 + `oklch(1 0 0)` 纯白 card | `tokens.css:14-15` |
| 文字 | 三级梯度：`0.15` / `0.45` / `0.65`（lightness），无纯黑 | `tokens.css:21-23` |
| 阴影 | 仅 3 级：hover-lift / card-lifted / accent-glow | `tokens.css:53-55` |
| hover | `translateY(-1px)` + 颜色变深，duration 150ms，`cubic-bezier(0.16, 1, 0.3, 1)` | 组件中普遍使用 |
| 字体 | Display: DM Sans + PingFang SC；Body: PingFang SC；Mono: JetBrains Mono | `tokens.css:65-67` |
| 间距 | 8 档非线性：4/8/16/24/32/48/80/120px | `tokens.css:36-43` |
| 圆角 | 4 级：0/4/8/12/9999px，无渐变/噪点/玻璃 | `tokens.css:46-50` |
| 图标 | lucide-react（`lucide-vue-next`） | `package.json` |
| 卡片密度 | 稀，padding 24px，rounded-lg，无渐变 | `BaseCard.vue` |
| 文案 | 工程向，动词为主 | 按钮文案"分析""上传"等 |
| 内容区 | max-width 720px，居中 | `MarkdownViewer.vue:11` |

### 2.2 本次 UI 改动面积极小

```
新增组件（1 个）：
- frontend/src/common/components/HtmlSvgViewer.vue — DOMPurify + v-html 薄封装

修改组件（2 个，均为条件渲染入口）：
- frontend/src/views/query/components/MarkdownReport.vue — 加 outputFormat prop + v-if 选择渲染器
- frontend/src/views/query/IntelligentQAPage.vue — 从 store 读取 outputFormat 传给 MarkdownReport

类型更新（1 个）：
- frontend/src/api/types.ts — QueryAskResponse / QueryResultResponse 加 outputFormat?: string

依赖新增（1 个）：
- dompurify + @types/dompurify

不触碰：
- tokens.css / global.css — 零修改
- MarkdownViewer.vue — 零修改
- AppLayout.vue / 路由 / 导航 — 零修改
- 所有其他页面和组件 — 零修改
```

### 2.3 沿用 vs 引入

```
- 排版层级：**沿用** global.css 9 级 typography（.body / .headline .supporting ...）
- 容器约束：**沿用** MarkdownViewer 的 max-width: 720px + margin: 0 auto
- 间距体系：**沿用** tokens.css 的 --spacing-* 变量
- 颜色体系：**沿用** tokens.css 全部 --color-* 变量
- DOMPurify：**引入新模式** → 理由：首次渲染 LLM 生成的 HTML，XSS 防护是新增安全需求
- contenteditable / 富文本编辑：不涉及
```

---

## 3. 美学维度决策

> 5 维全部沿用既有 tokens。uipro 未检出，使用内置基线。

### 3.1 字体

- **决策**：沿用既有——Display: DM Sans + PingFang SC，Body: PingFang SC，Mono: JetBrains Mono
- **理由**：HtmlSvgViewer 渲染的内容与 MarkdownViewer 并存于同一页面（`IntelligentQAPage`），字体必须一致，否则视觉割裂
- **LLM 生成的 HTML 内字体处理**：LLM 在 prompt 中被约束"不指定 font-family"，由 `HtmlSvgViewer` 的 CSS 继承 `body` 类字体。若 LLM 仍输出了 `font-family` 样式，DOMPurify 不剥离（`style` 属性在白名单中）→ 接受，因为 LLM 通常不额外指定字体

### 3.2 颜色

- **决策**：沿用既有 OKLCH 色板，不新增任何颜色 token
- **理由**：HtmlSvgViewer 的背景/文字/链接色完全继承 `body` 类的 `--color-*` 变量
- **LLM 生成的 SVG 颜色处理**：LLM 在 prompt 中被建议使用品牌兼容色（蓝色系 `#3B82F6` 附近、中性灰 `#6B7280` 附近），但不强制——因为 LLM 对精确色值控制力弱。渲染时不覆盖 SVG 的 `fill`/`stroke` inline style

### 3.3 动效

- **决策**：无动效
- **理由**：静态内容渲染组件。SVG 动画（SMIL/CSS animation）不在 v1 范围。DOMPurify 默认允许 CSS animation 但本组件不主动触发
- **例外**：如果 LLM 在 SVG 中嵌入了 SMIL `<animate>` 标签 → DOMPurify 默认保留 → v1 接受（概率极低且无安全风险）

### 3.4 空间

- **决策**：沿用既有间距 scale。容器 padding 与 MarkdownViewer 一致（`padding: var(--spacing-lg) 0`）
- **SVG 尺寸约束**：`max-width: 100%; height: auto;` — SVG 自适应容器宽度，不超出 720px。宽图（如横向依赖链 >720px）→ `overflow-x: auto` 出现水平滚动条
- **理由**：LLM 生成的 SVG `viewBox` 不可预测，前端不能缩放或裁剪（破坏可读性），滚动是唯一安全策略

### 3.5 质感

- **决策**：纯色背景，无纹理/渐变/噪点/玻璃
- **理由**：延续既有管理后台的极简质感。`HtmlSvgViewer` 无独立背景色，透明继承父级

---

## 4. Design Tokens

> **零新增**。全部复用 `tokens.css` 现有变量。以下仅记录与 HtmlSvgViewer 相关的已存在 token：

```css
/* 全部来自 tokens.css — HtmlSvgViewer 消费的 token 子集 */

/* 排版 */
--font-body: 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif;
--font-display: 'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif;
--font-mono: 'JetBrains Mono', 'SF Mono', 'Cascadia Code', monospace;

/* 颜色 */
--color-text-primary: oklch(0.15 0.005 95);
--color-text-secondary: oklch(0.45 0.005 95);
--color-border: oklch(0.90 0.005 95);
--color-bg: oklch(0.985 0.002 95);

/* 间距 */
--spacing-lg: 24px;
--spacing-md: 16px;
--spacing-sm: 8px;

/* 圆角 */
--rounded-sm: 4px;
```

**HtmlSvgViewer 专用 CSS 变量（组件内定义，不升到 tokens.css）：**

```css
/* HtmlSvgViewer scoped */
.html-svg-viewer {
  --html-svg-max-width: 720px;        /* 与 MarkdownViewer 一致 */
  --html-svg-svg-max-height: 600px;   /* SVG 最大高度，超出滚动 */
}
```

---

## 5. 关键组件规约

### 5.1 HtmlSvgViewer

| 属性 | 规约 |
|------|------|
| **Props** | `content: string`（LLM 原始 HTML+SVG 字符串）；`maxWidth?: number`（默认 720） |
| **核心逻辑** | `DOMPurify.sanitize(content, config)` → `v-html` 渲染 |
| **容器** | `<div class="html-svg-viewer body">`，max-width 继承 prop，margin: 0 auto |
| **加载态** | 无（同步净化 + 渲染，耗时 <5ms，无需 skeleton） |
| **空态** | `content` 为空时渲染空 `<div>`，不报错 |
| **错误态** | DOMPurify 净化失败（不可能——即使输入非法 HTML 也返回净化后字符串）→ 不处理 |
| **SVG 溢出** | `<svg>` 元素 `max-width: 100%; height: auto;`。若 SVG 天然宽度 > 容器 → `overflow-x: auto` |
| **安全边界** | 白名单见 ADR-026 § D5，禁止 script/foreignObject/use[xlink:href]/事件属性 |

#### 排版层次（`:deep()` 样式，对标 MarkdownViewer 的 `:deep()` 规则）

```css
/* HtmlSvgViewer :deep() — 与 MarkdownViewer 保持一致 */
.html-svg-viewer :deep(h2) { font-family: var(--font-display); font-size: 1.5rem; font-weight: 600; ... }
.html-svg-viewer :deep(h3) { font-family: var(--font-display); font-size: 1.125rem; font-weight: 500; ... }
.html-svg-viewer :deep(p)   { margin: var(--spacing-sm) 0; }
.html-svg-viewer :deep(table) { width: 100%; border-collapse: collapse; ... }
.html-svg-viewer :deep(th)  { font-family: var(--font-display); font-size: 0.6875rem; text-transform: uppercase; ... }
.html-svg-viewer :deep(td)  { padding: var(--spacing-sm) var(--spacing-md); border-bottom: 1px solid var(--color-border); }
.html-svg-viewer :deep(svg) { max-width: 100%; height: auto; display: block; margin: var(--spacing-md) 0; }
.html-svg-viewer :deep(strong) { font-weight: 600; }
```

> 详细 CSS 规则在 4-dev 阶段实现，与 `MarkdownViewer.vue` 的 `:deep()` 规则保持对应。

### 5.2 MarkdownReport（修改）

| 属性 | 变更 |
|------|------|
| **Props 新增** | `outputFormat: 'html-svg' \| 'markdown'` |
| **条件渲染** | `v-if="outputFormat === 'html-svg'"` → `HtmlSvgViewer`；`v-else` → `MarkdownViewer` |
| **默认值** | `outputFormat` 缺省时 fallback 到 `'markdown'`（向后兼容旧响应） |
| **其他** | 组件结构不变，仅内部条件分支 |

---

## 6. Do's and Don'ts

基于「极简 Minimal — Linear/Vercel/Stripe」调性 + 内容渲染组件定位：

| ✅ Do | ❌ Don't |
|-------|---------|
| 容器 max-width: 720px，居中，与 MarkdownViewer 一致 | 不要给 HtmlSvgViewer 加背景色/边框/阴影（它只是透明容器） |
| SVG 用 `max-width: 100%` 自适应，超出横向滚动 | 不要用 `transform: scale()` 缩放 SVG（破坏可读性） |
| `:deep()` 排版规则与 MarkdownViewer 保持 1:1 对应 | 不要新创一套排版层次（如不同的 h2 font-size） |
| DOMPurify 净化配置写死在组件内（`ALLOWED_TAGS` 常量） | 不要把净化配置暴露为 prop（会鼓励绕过安全策略） |
| 空内容渲染空 div，静默处理 | 不要对空内容显示"暂无内容"等占位提示 |
| 所有 CSS 用 scoped + `:deep()` | 不要写全局 CSS 污染 MarkdownViewer 的样式 |
| 组件文件名 `HtmlSvgViewer.vue`，PascalCase | 不要命名为 `HTMLViewer` / `SafeHtmlRenderer` / 其他 |

---

## 7. 占位符策略

> 本组件为内容渲染器，不涉及图标/头像/图片/数据/logo/推荐墙的展示。以下仅覆盖可能触及的边缘：

| 缺的东西 | 正确做法 | 禁止做法 |
|----------|---------|---------|
| SVG 无法渲染（浏览器不支持 SVG） | 浏览器支持 SVG 1.1 已 15+ 年，无需降级 | — |
| LLM 输出空 answer | `IntelligentQAPage` 层显示错误状态，HtmlSvgViewer 不渲染 | 不在 HtmlSvgViewer 内显示"无内容" |
| DOMPurify 加载失败（CDN 挂了） | DOMPurify 是 npm 依赖打包进 bundle，不会加载失败 | — |

---

## 8. 反 AI-slop 自检

对照 `ui-anti-patterns.md` 强制禁忌：

| 禁忌 | 命中？ | 说明 |
|------|--------|------|
| Inter / Roboto / Arial 默认字体 | ❌ 未命中 | 沿用 DM Sans + PingFang SC（已于 frontend-ui 避开 Inter） |
| 紫色渐变 + emoji 图标 | ❌ 未命中 | 无渐变，无 emoji，图标为 lucide-react |
| `box-shadow` 泛滥（>4 级 elevation） | ❌ 未命中 | 仅 3 级 shadow，HtmlSvgViewer 不加阴影 |
| 卡片 `border-radius: 16px+` 过度圆角 | ❌ 未命中 | 最大 `--rounded-lg: 12px` |
| `backdrop-filter: blur()` 玻璃效果 | ❌ 未命中 | 无 |
| 编造数据 / 用 emoji 充图标 | ❌ 未命中 | HtmlSvgViewer 不产生内容，仅渲染 |
| `system-ui` / `-apple-system` 字体栈 | ❌ 未命中 | 显式指定 DM Sans + PingFang SC |
| 无意义的 `transition: all 0.3s ease` | ❌ 未命中 | 无动效 |

**结论**：零命中。本组件是极简的内容渲染薄封装，天然避开 AI slop 重灾区。

---

> 本文件不包含完整 Vue 组件实现。CSS 片段为规约级示例，完整组件代码在 4-dev 阶段产出。