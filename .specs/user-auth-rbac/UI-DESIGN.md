---
name: GraphNexus
description: 极简工具型管理后台，单一蓝色强调 + 深色侧边栏 + 平面静态卡片，线性风格工具应用

colors:
  brand: "oklch(0.55 0.18 250)"
  brand-deep: "oklch(0.48 0.18 250)"
  bg: "oklch(0.985 0.002 95)"
  surface: "oklch(1 0 0)"
  text-primary: "oklch(0.15 0.005 95)"
  text-secondary: "oklch(0.45 0.005 95)"
  text-tertiary: "oklch(0.65 0.005 95)"
  border: "oklch(0.90 0.005 95)"

typography:
  display:
    fontFamily: "'DM Sans', 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "clamp(1.75rem, 4vw, 2.5rem)"
    fontWeight: 700
    lineHeight: 1.15
  headline:
    fontFamily: "'DM Sans', 'PingFang SC', sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  supporting:
    fontFamily: "'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "'DM Sans', 'PingFang SC', sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    textTransform: "uppercase"
    letterSpacing: "0.05em"
    lineHeight: 1.3

spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  2xl: "48px"
  3xl: "80px"

rounded:
  none: "0"
  sm: "4px"
  md: "8px"
  lg: "12px"
  full: "9999px"

motion:
  ease-out: "cubic-bezier(0.16, 1, 0.3, 1)"
  duration-fast: "150ms"
  duration-base: "300ms"
  duration-slow: "600ms"

shadow:
  hover-lift: "0 2px 8px rgba(0, 0, 0, 0.08)"
  card-lifted: "0 20px 60px rgba(0, 0, 0, 0.12)"
---

# UI-DESIGN: 用户认证与角色权限管理（前端部分）

- **Change ID**: `user-auth-rbac`
- **关联**: `@.specs/user-auth-rbac/DESIGN.md`、`@frontend/src/assets/tokens.css`
- **范围**: 登录页 + 用户管理页 + 路由守卫 UI 行为 + 菜单权限过滤 + 顶栏用户区

---

## 0. 视觉语汇对齐（brownfield）

### 0.1 观察报告（代码为源）

- **Token 源**：[tokens.css](frontend/src/assets/tokens.css) — 68 行完整 Design Token，所有颜色 OKLCH
- **主色实际比例**：蓝 `oklch(0.55 0.18 250)` 约占 3%（BaseButton primary / 链接 / 侧边栏 active 指示条）
- **中性色**：bg `oklch(0.985 0.002 95)` 偏米白 / text 3 级灰（chroma=0.005）/ 侧边栏深蓝黑 `oklch(0.12 0.01 250)` / 不用纯黑纯白
- **hover/focus 反馈**：颜色变化 + 偶有 `translateY(-1px)`，transition 统一 `150ms cubic-bezier(0.16, 1, 0.3, 1)`，focus ring `3px solid var(--color-brand-veil)`
- **动效语言**：主缓动 `cubic-bezier(0.16, 1, 0.3, 1)` / 备 `cubic-bezier(0.22, 1, 0.36, 1)` / 3 档 duration（150/300/600ms）/ `prefers-reduced-motion` 已尊重 / 仅 loading spin + status-pulse animation
- **elevation 层级**：仅 2 级（`shadow-hover-lift` + `shadow-card-lifted`）+ 品牌发光 `shadow-accent-glow`
- **卡片密度/rounded**：BaseCard 纯白表面 + `1px solid border` + `rounded-md`(8px) / 内边距 24px / rounded 5 级：0/4/8/12/9999px / 卡片平坦 at rest，无 drop shadow
- **图标库**：`@lucide/vue`（Lucide Icons，stroke-width 默认 1.5）/ 无插画
- **文案调性**：工程向/工具向，中文界面，直白动词（"上传文件"/"删除"/"查询"），状态标签简洁中性

### 0.2 用户校准结论

- ✅ 用户确认观察无误
- 本次**沿用**全部既有 token（colors / typography / spacing / rounded / motion / shadow），不做任何修改
- 新增页面在此基础上**延伸**

### 0.3 应用策略

- **沿用**：所有 tokens.css 变量 + global.css 排版层次 + BaseCard/BaseButton/BaseInput 组件包装
- **延伸**：登录页全屏居中布局（无侧边栏/顶栏）、用户管理页 NDataTable + NModal 模式（对齐 FileManagePage/GradeManagePage）
- **打破**：无

---

## 1. 美学北极星

> **Tool-Minimal Blueprint** — 深色侧边栏 + 米白内容区 + 单一蓝色功能强调。像 Linear 的后台兄弟：工具感、精确、无装饰。中文界面但不牺牲排版质量。

### v0 确认摸路

- **已确认的假设**（用户说 go）：
  - 登录页无验证码/记住我/忘记密码 — v1 scope
  - 用户管理页无搜索过滤栏 — v1，用户量小
  - 角色标签颜色：ADMIN 蓝 / TEACHER 绿 / STUDENT 灰 / OPS_STAFF 橙 / OPS_MANAGER 紫
  - 登录背景纯色 `var(--color-bg)`，不加渐变/纹理/插图
  - 403 页复用 AppLayout 壳（侧边栏保持可见），不做独立全屏页
  - 不做 dark mode
- **用户指出的偏差**：无

---

## 2. 4 个决策问题

- **目的**：两个新页面 + 全局认证行为。登录页 — 用户输入凭证获得访问权；用户管理页 — 管理员管理账号。核心动作：登录表单提交、用户 CRUD、角色分配、Token 自动刷新
- **调性**：极简（Minimal）— 工具型管理后台，无装饰元素，功能即美学
  - **理由**：已有项目调性锁定（CHANGE.md + CONTEXT.md），登录页/用户管理页需与现有页面视觉一致
- **约束**：Naive UI 组件库（NButton/NInput/NModal/NDataTable/NTag 等）、Vue 3 + TypeScript、Tailwind CSS 4（Vite 插件）、lucide-vue 图标库、桌面端 ≥1280px、不支持移动端
- **差异化**：整个应用像"一个精致内部工具"——用户打开后感觉这不是通用 SaaS 模板，而是为 GraphNexus 专门定制的操作界面

---

## 3. 颜色系统

**全部沿用 tokens.css，本次不新增颜色变量。** 仅新增角色标签语义色（在组件内部使用 tokens.css 已有变量组合）：

| 角色 | 标签颜色 | 来源 |
|------|---------|------|
| ADMIN | 蓝 — `var(--color-brand)` | 复用主色 |
| TEACHER | 绿 — `var(--color-success)` | 复用语义色 |
| STUDENT | 灰 — `var(--color-text-tertiary)` | 复用中性色 |
| OPS_STAFF | 橙 — `var(--color-warning)` | 复用语义色 |
| OPS_MANAGER | 紫 — `oklch(0.55 0.12 300)` | 新增（仅此一处，因为 tokens.css 无紫色语义色） |

### 命名规则

- **The One Voice Rule**：蓝色是唯一强调色，登录按钮、active 指示、链接均用蓝
- **The Tinted Neutral Rule**：所有中性色 chroma=0.002~0.005（沿用既有）
- **语义色用于状态**：success/warning/error 仅用于 StatusBadge 和角色标签，不用于装饰

---

## 4. 字体系统

**全部沿用 global.css 的 9 级排版层次。** 本次新增页面的文字使用：

| 用途 | 排版类 | 字体 | 字号 |
|------|--------|------|------|
| 登录页 "GraphNexus" | `.display` | DM Sans | clamp(1.75rem, 4vw, 2.5rem) |
| 页面标题（"用户管理"） | `.headline` | DM Sans | 1.5rem |
| 表单标签 | `.label` | DM Sans | 0.75rem uppercase |
| 登录错误提示 | `.supporting` | PingFang SC | 0.875rem（color: var(--color-error)） |
| 表格内容 | `.body` | PingFang SC | 1rem |
| 403 标题 | `.display` | DM Sans | clamp(1.75rem, 4vw, 2.5rem) |
| 403 描述 | `.body-lead` | PingFang SC | 1.125rem |

---

## 5. 间距 & 圆角 & 动效

**全部沿用 tokens.css。** 新增页面的关键布局数值：

| 场景 | 间距 | 值 |
|------|------|-----|
| 登录卡片内边距 | `var(--spacing-xl)` | 32px |
| 登录卡片宽度 | 固定 360px | — |
| 登录表单字段间距 | `var(--spacing-md)` | 16px |
| 用户管理页内容区内边距 | `var(--spacing-lg)` | 24px |
| 新建用户弹窗宽度 | 480px（与既有 FileUpload NModal 一致） | — |
| 弹窗表单项间距 | `var(--spacing-md)` | 16px |
| 403 页面内容间距 | `var(--spacing-lg)` | 24px |

圆角：登录卡片 `rounded-lg`(12px) — 比 BaseCard 的 md(8px) 大一级，凸显登录页的"独立页面"感。其他沿用 md(8px)。

动效：登录失败错误提示 → `opacity 0→1` `150ms ease-out`；NModal 弹窗 → Naive UI 默认（`transform scale`，约 200ms）不自定义。

---

## 6. 关键组件规约

### LoginPage.vue（登录页）

**布局**：
- 全屏居中：`display: flex; justify-content: center; align-items: center; min-height: 100vh`
- 背景：`var(--color-bg)`，无渐变/纹理/插图
- 无 AppLayout 壳（无侧边栏、无顶栏）

**登录卡片**：
```
  width: 360px
  background: var(--color-surface)
  border: 1px solid var(--color-border)
  border-radius: var(--rounded-lg)
  padding: var(--spacing-xl)
  box-shadow: var(--shadow-card-lifted)
```

**卡片内容（自上而下）**：
1. **品牌文字** "GraphNexus" — `.display` 类，居中，`color: var(--color-brand)`
2. **间距** `var(--spacing-lg)`（24px）
3. **用户名字段** — Naive UI NInput，placeholder "请输入用户名"，size: large
4. **间距** `var(--spacing-md)`（16px）
5. **密码字段** — Naive UI NInput type="password"，placeholder "请输入密码"，size: large，show-password-on="click"
6. **间距** `var(--spacing-lg)`（24px）
7. **登录按钮** — BaseButton primary，size: large，**全宽**（`width: 100%`）
8. **错误提示** — 条件渲染：`.supporting` + `color: var(--color-error)`，文本居中，`margin-top: var(--spacing-sm)`，带 `transition: opacity var(--duration-fast) var(--ease-out)`

**交互**：
- Enter 键提交表单（任意字段聚焦时）
- 登录中按钮显示 loading + disabled（Naive UI NButton 内置）
- 失败 → 错误提示淡入，修改输入后自动消失
- 成功 → `router.push(redirect || '/materials')`

**禁止**：
- ❌ 不显示"记住我"复选框 / "忘记密码"链接（v1 scope）
- ❌ 不加背景插图/几何装饰/渐变
- ❌ 不加"GraphNexus 基于图谱技术的..."副标题

### UserManagePage.vue（用户管理页）

**布局**：
- 标准 AppLayout 壳内：侧边栏 + 顶栏（标题 "用户管理"）+ 内容区
- 内容区：BaseCard 包裹，无额外外层装饰

**功能区域**：
1. **顶部操作栏**：BaseButton primary "新建用户"（左对齐）
2. **用户表格**：Naive UI NDataTable
   - 列：用户名 / 真实姓名 / 角色（NTag 标签列表）/ 状态（StatusBadge 复用）/ 创建时间 / 操作
   - **角色列**：每个角色一个 `<NTag>` ，颜色按角色映射：
     ```typescript
     const roleColorMap: Record<string, string> = {
       ADMIN: 'var(--color-brand)',        // 蓝
       TEACHER: 'var(--color-success)',     // 绿
       STUDENT: 'var(--color-text-tertiary)', // 灰
       OPS_STAFF: 'var(--color-warning)',   // 橙
       OPS_MANAGER: 'oklch(0.55 0.12 300)', // 紫
     }
     ```
     NTag 样式：`border: none; border-radius: var(--rounded-sm);`，文字用 12px label
   - **状态列**：复用 `StatusBadge` 组件（ENABLED→绿 / DISABLED→红）
   - **操作列**："编辑" 文字按钮 → 触发编辑弹窗
   - 行 hover：`background: var(--color-bg)`（与 GradeManagePage 一致）
3. **分页器**：Naive UI NPagination（表格底部，右对齐）

**新建/编辑用户弹窗**（Naive UI NModal）：
- `title="新建用户"` 或 `"编辑用户 — {username}"`
- `style="width: 480px"`（与既有 FileUpload NModal 尺寸一致）
- 表单字段：
  - 用户名：NInput（新建时必填 · 编辑时 disabled）
  - 真实姓名：NInput（必填）
  - 密码：NInput type="password"（新建时必填 · 编辑时可为空=不修改密码）
  - 角色：NSelect multiple，options 来自 `GET /api/v1/auth/roles`，标签显示中文名
  - 状态：NSwitch（启用/禁用），编辑已有用户时显示
- 底部操作：NSpace justify="end" — "取消"（BaseButton 文字样式）+ "确认"（BaseButton primary）
- Escape 键关闭弹窗

### 顶栏用户区改动

```
当前: <div class="avatar"> (空白圆)
改为:
<div class="topbar__user">
  <n-popover trigger="click" placement="bottom-end">
    <template #trigger>
      <div class="avatar" :style="{ background: 'var(--color-brand-veil)' }">
        <span class="label" style="color: var(--color-brand)">{{ userInitial }}</span>
      </div>
      <span class="supporting">{{ username }}</span>
    </template>
    <div style="padding: var(--spacing-sm); min-width: 160px">
      <div class="supporting" style="padding: 4px 8px">{{ username }}</div>
      <div class="supporting" style="padding: 4px 8px; color: var(--color-text-tertiary)">{{ roles }}</div>
      <n-button text @click="handleLogout" style="margin-top: 8px; color: var(--color-text-secondary)">
        登出
      </n-button>
    </div>
  </n-popover>
</div>
```

- 头像：首字母圆形，24×24px（非 AI 生成人脸）
- 用户名文字显示在头像右侧
- **仅已登录状态显示**（有 Token 时渲染，无 Token 时不渲染整个 topbar__user）

### 403 禁止访问页

**布局**：
- AppLayout 壳内（侧边栏保持可见，用户知道自己在系统中）
- 内容区居中显示

```
<div style="display:flex; flex-direction:column; align-items:center; justify-content:center;
            min-height: 400px; gap: var(--spacing-lg)">
  <ShieldOff :size="48" color="var(--color-text-tertiary)" />    <!-- lucide icon -->
  <div class="display" style="color: var(--color-text-primary)">403</div>
  <div class="body-lead" style="color: var(--color-text-secondary)">
    您没有权限访问此页面
  </div>
  <BaseButton @click="router.push('/materials')">返回首页</BaseButton>
</div>
```

**交互**：
- 无权限菜单项：侧边栏不渲染（而非渲染后点击才 403）
- 用户手动输 URL 访问无权限路径 → 显示此页，侧边栏该分组无高亮项

### 路由守卫行为（非视觉但影响 UX）

- `router.beforeEach`：
  - 无 Token → `/login?redirect=<原路径>`
  - 有 Token 但无目标页权限 → `/403`
  - `/login` 已登录 → `/materials`
- 页面过渡：无动画（spa 即显），不引入 page transition

---

## 7. Do's and Don'ts（本项目特定）

### Do

- ✅ 登录页卡片用 `shadow-card-lifted`（唯一用卡片阴影的场景——登录页是独立入口，非内容操作页）
- ✅ 角色标签用语义色 thin 背景（`oklch(... / 0.12)`）+ 文字色，无边框
- ✅ 用户名首字母圆形头像（品牌色填充 + 白色文字），非图片
- ✅ 表单 label 每个字段都有（用 `var(--font-display)` `.label` 类，与上传弹窗一致）
- ✅ 错误提示具体（"用户名或密码错误" 而非 "登录失败"）

### Don't（本项目额外禁止，补充全局 anti-patterns）

- ❌ 登录页不要背景图/渐变/几何装饰/动画背景（即使用户没要求也**不能**加）
- ❌ 登录卡片不要 `border-left` 彩色侧条装饰
- ❌ 不要 emoji 图标（🚀⚡✨）——用 lucide 图标
- ❌ 不要 gradient text（`background-clip: text`）
- ❌ 不要 glassmorphism（毛玻璃卡片）
- ❌ 角色标签不要使用 NTag 的 `type` 属性默认样式（颜色不可控），必须用自定义 `:style`
- ❌ 不要在登录页显示任何导航/侧边栏/页脚
- ❌ 不要 dark mode（tokens.css 无 dark token，不引入）

---

## 8. 占位符策略（反伪造）

| 缺的东西 | 本项目有什么？ | 缺时用什么占位 | 禁什么 |
|---|---|---|---|
| 图标 | `@lucide/vue` (Lucide Icons) | 用 lucide 内对应图标（ShieldOff/Users/LogOut/User） | emoji / AI 自绘 SVG |
| 头像 | 无图片头像组件 | **首字母圆形**（品牌色 fill + 白色文字）24×24px | AI 生人脸 / 网抓图 / Gravatar |
| 图片 | 无 | 本项目不需要图片 | stock photo / AI 图 |
| 数据 | 来自后端 API | `NEmpty` 组件（Naive UI 内置空状态） | 编造用户列表 / 编角色名 |
| logo | "GraphNexus" 文字 | 品牌文字 .display（现有用法） | AI 自绘 logo 图形 |
| 用户列表为空 | — | Naive UI NEmpty "暂无用户" | 编 3 个 mock 用户 |

---

## 9. 反 AI-slop 自检结果

逐条对照 `ui-anti-patterns.md` 强制禁忌：

| 类别 | 检查项 | 结果 |
|------|--------|:--:|
| 字体 | Inter/Roboto/Arial 作主字体 | ✅ 未命中（DM Sans + PingFang SC） |
| 字体 | Space Grotesk 作 display | ✅ 未命中 |
| 字体 | display=body 同样字体不同字重 | ✅ 未命中（DM Sans display vs PingFang SC body） |
| 颜色 | 纯黑 `#000` / 纯白 `#fff` | ✅ 未命中（所有色均为 OKLCH） |
| 颜色 | 紫色渐变 on 白底 | ✅ 未命中 |
| 颜色 | 两个以上强调色 | ⚠️ 角色标签用了 5 种颜色 — **已解释**：语义需要，非装饰，每种颜色代表一类角色 |
| 颜色 | Gradient text | ✅ 未命中 |
| 阴影 | 静态卡片带 drop shadow | ⚠️ 登录卡片用了 `shadow-card-lifted` — **已解释**：登录页是独立入口页，非内容操作卡片，阴影区分"登录入口"与"操作界面"的视觉层级 |
| 阴影 | Shadow alpha > 0.15 | ✅ 未命中（hover-lift 0.08 / card-lifted 0.12） |
| 阴影 | 装饰性彩色阴影 | ✅ 未命中 |
| 边框 | `border-left` > 1px 彩色侧条 | ✅ 未命中 |
| 边框 | 渐变边框 / 玻璃拟态 | ✅ 未命中 |
| 动效 | Bounce/elastic 缓动 | ✅ 未命中（仅用 ease-out） |
| 动效 | 不支持 prefers-reduced-motion | ✅ 已支持（tokens.css） |
| 布局 | 卡片嵌套卡片 | ✅ 未命中（BaseCard 单层） |
| 布局 | 统一大小卡片网格图标标题文本 | ✅ 未命中（用户管理页是表格，非卡片网格） |
| 布局 | 默认 dark mode | ✅ 未命中（不做 dark mode） |
| 文案 | "Boost your productivity" 空话 | ✅ 未命中（中文工具向文案） |
| 文案 | Lorem ipsum | ✅ 未命中 |
| 组件 | 圆角矩形 + drop shadow button | ✅ 未命中（按钮无 drop shadow at rest） |
| 组件 | placeholder 替代 label | ✅ 未命中（表单均有 label） |
| 组件 | 模态框无 Escape 关闭 | ✅ 支持（Naive UI NModal 默认） |

**唯一例外声明**：
1. 角色标签 5 色 — 非装饰，语义需求（区分角色类型），每个颜色代表数据属性
2. 登录卡片阴影 — 非内容卡片装饰，独立入口页的视觉层级标识

## 10. 触发任务

下一步进入 `3-task` 阶段时，前端 UI 任务（第一批）：

- **T-UI-01**：`LoginPage.vue` — 全屏居中卡片 + 表单 + 登录逻辑
- **T-UI-02**：`UserManagePage.vue` — 表格 + 新建/编辑弹窗 + CRUD 交互
- **T-UI-03**：`AppLayout.vue` 改造 — 菜单按角色过滤 + 顶栏用户头像/下拉
- **T-UI-04**：`authGuard.ts` — 路由守卫（beforeEach）+ 403 页面
- **T-UI-05**：`authStore.ts` — Pinia 认证状态 + localStorage Token 管理
- **T-UI-06**：`auth.ts` (API) — 登录/刷新/登出/用户CRUD API 封装
- **T-UI-07**：`router/index.ts` — 新增 `/login` `/settings/users` `/403` 路由
- **T-UI-08**：axios interceptor — 401 自动刷新 + 刷新锁

后端任务在 `3-task` 中统一规划。以上任务顺序：T-UI-07 → T-UI-04/05/06 并行 → T-UI-03 → T-UI-01 → T-UI-02 → T-UI-08。