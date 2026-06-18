# DESIGN: GraphNexus 前端管理界面

- **Change ID**: `frontend-ui`
- **关联**: `@.specs/frontend-ui/REQUIREMENT.md`、`@.specs/frontend-ui/UI-DESIGN.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 2-design 步骤 0 锁定。变栈视为开新 CHANGE（R7.1）。

| 层 | 选定 | 版本 | 状态 |
|----|------|------|:--:|
| 框架 | Vue 3 + TypeScript + Vite | Vue 3.5+, TS 5.x, Vite 6 | 🔒 已锁 |
| 组件库 | **Naive UI** | 2.x | 🆕 |
| CSS | **Tailwind CSS** | v4 | 🆕 |
| 状态管理 | Pinia | 2.x | 🔒 已锁 |
| 路由 | Vue Router | 4.x | 🔒 已锁 |
| HTTP | Axios | 1.x | 🔒 已锁 |
| 图标 | Lucide Vue | latest | 🔒 已锁 |
| 图可视化 | **AntV G6** | v5 | 🆕 |
| Markdown | **marked** + **highlight.js** | latest | 🆕 |

- **理由**：Vue 3 SPA 中后台方案（对应 tech-stacks 卡片 7 Spring Boot + Vue），全部组件库/可视化库选择以「极简调性 + 中文文档 + Vue 3 原生支持」为优先条件。
- **明确排除**：
  - Nuxt 3 — 管理后台无需 SSR/SEO，纯 SPA 即可
  - Element Plus — 默认主题与极简调性差距大，定制成本高
  - ECharts 作图可视化 — 图非其主业，关系图渲染不如专用库

---

## 0.5 既有架构对齐

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（后端 API，仅 HTTP 消费，不修改）：
- /api/v1/document/*   — 文件上传/解析/CRUD（Axios 调用）
- /api/v1/graph/*      — 图谱抽取/子图/融合/指标（Axios 调用）
- /api/v1/query/*      — 智能问答（Axios 调用）
- /api/v1/analysis/*   — 剪枝子图（Axios 调用）

新增模块（全部新建，无既有前端代码）：
- src/                    — 整个前端项目从零搭建
- deployment/nginx/       — Nginx 配置新增 SPA 静态文件服务 + API 反向代理

禁动清单（前端不许碰的）：
- 所有后端 Java 代码（src/main/java/**）
- pom.xml
- docs/项目规范.md
- deployment/nginx/ 中已有的后端 proxy 配置（仅追加 SPA 段）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？ | 决定 |
|---------|------------|------|
| 后端 API | 20 个端点已实现 | 消费，不修改 |
| 统一响应格式 | `ApiResult<T>` (code/message/data/traceId/timestamp) | Axios 拦截器统一 unwrap |
| 错误格式 | `ErrorResponse` (errorCode/errorMessage/userTip) | 拦截器捕获 → toast 提示 |
| 分页格式 | `PageResult<T>` (list/total/pageNum/pageSize) | 前端分页组件对接 |
| 鉴权 | V1 跳过 | 不设登录页 |
| API 文档 | Swagger UI `/swagger-ui.html` | 开发期间手动查阅 |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据流：**沿用** 后端 REST → JSON → Pinia store → Vue 组件（标准 Vue 3 模式）
- 项目结构：**引入新模式** — feature-based 目录（按模块分，非 layer-based）
  → 理由：6 个模块独立性强，feature-based 便于独立迭代和 code review
- 错误处理：**沿用** 后端 ErrorCode 体系 — 前端按 errorCode 展示 userTip，非自定义错误文案
- API 组织：**引入新模式** — src/api/ 下按后端 Controller 分组（file/graph/query/analysis）
  → 理由：与后端 Swagger Tag 一一对应，降低前后端认知差距
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | SPA 架构（Vue 3 + Vite） | Nuxt 3 SSR | 管理后台无 SEO 需求；纯 SPA 部署更简单（静态文件 + Nginx） | 首屏加载比 SSR 慢，通过代码分割 + 预加载缓解 |
| D2 | 组件库 Naive UI | Element Plus / Ant Design Vue | 默认主题天然极简（扁平、冷色调、轻阴影），与 UI-DESIGN 调性一致；TypeScript 原生；tree-shaking | 社区 < Element Plus；复杂场景可能缺组件需自研 |
| D3 | CSS Tailwind CSS v4 | UnoCSS / 纯 SCSS | 设计 token 直接映射 theme config；文档丰富中文资料多；@apply 可封装组件样式 | 原子类 HTML 较长，通过组件封装缓解 |
| D4 | 图可视化 AntV G6 v5 | ECharts / Cytoscape.js | 专为关系图设计（力导向/节点边样式/缩放拖拽开箱即用）；中文文档优秀；Vue 3 集成方便 | v5 较新，遇 bug 需查 GitHub issues；社区 < Cytoscape.js |
| D5 | 状态管理 Pinia | Vuex 4 / provide/inject | Vue 3 官方推荐；TypeScript 推断完整；按模块分 store（fileStore/graphStore/queryStore/...） | 跨 store 通信需显式 import，不如全局 store 方便 |
| D6 | HTTP Axios + 拦截器 | fetch / ofetch | 拦截器统一 unwrap `ApiResult.data`；统一注入 traceId；统一错误处理 → toast | 包体积约 14KB gzip，fetch 为 0 |
| D7 | Markdown marked + highlight.js | markdown-it | 需求明确（标题/列表/表格/代码块），marked 更轻量；highlight.js 代码高亮开箱即用 | 不支持 markdown-it 的插件扩展（V1 不需要） |
| D8 | 项目结构 feature-based | layer-based（components/pages/stores 分家） | 6 个模块独立性强（文件/图谱/成绩/问答/融合/指标），feature-based 便于独立迭代 | 跨模块共享组件需显式放在 `src/common/` |
| D9 | 错误处理：后端 errorCode → 前端 userTip | 前端自定错误文案 | 后端 ErrorResponse 已含 userTip 字段，前端直接展示避免前后端文案不一致 | 部分 userTip 可能过于技术化，需后端配合优化 |
| D10 | 图数据适配层 | 直接传 API 响应给 G6 | 两种子图端点结构不同（GraphSubgraphVO 无 properties，SubgraphResponse 有 properties Map），适配层统一转为 G6 GraphData 格式 | 增加一层抽象，约 100 行代码 |

---

## 2. 数据流 / 架构图

```
┌─────────────────────────────────────────────────────────┐
│ Browser                                                  │
│                                                          │
│  Vue Router (history mode)                               │
│  ┌─────┬─────┬─────┬─────┬─────┬─────┐                  │
│  │/files│/graph│/grades│/qa │/fusion│/metrics│           │
│  └──┬───┴──┬───┴──┬───┴──┬──┴───┬───┴───┬────┘          │
│     │      │      │      │      │       │               │
│  ┌──┴──────┴──────┴──────┴──────┴───────┴──┐            │
│  │         Pinia Stores (per-module)        │            │
│  │  fileStore │ graphStore │ queryStore     │            │
│  │  fusionStore │ metricsStore              │            │
│  └──────────────────┬───────────────────────┘            │
│                     │                                    │
│  ┌──────────────────┴───────────────────────┐            │
│  │           Axios Instance                  │            │
│  │  baseURL: /api/v1                        │            │
│  │  ┌──────────────┐  ┌──────────────────┐  │            │
│  │  │ req interceptor│  │ res interceptor  │  │            │
│  │  │ attach traceId │  │ unwrap ApiResult │  │            │
│  │  │                │  │ handle ErrorResp │  │            │
│  │  └──────────────┘  └──────────────────┘  │            │
│  └──────────────────┬───────────────────────┘            │
│                     │ HTTP                                │
└─────────────────────┼────────────────────────────────────┘
                      │
         ┌────────────┴────────────┐
         │   Vite Proxy (dev)      │
         │   Nginx (prod)          │
         └────────────┬────────────┘
                      │
         ┌────────────┴────────────┐
         │   Spring Boot :8080     │
         │   /api/v1/*             │
         │   ┌──────────────┐      │
         │   │ ApiResult<T> │      │
         │   │ ErrorResponse│      │
         │   └──────────────┘      │
         └─────────────────────────┘
```

### 关键数据转换链路

```
智能问答完整链路：
  用户输入问题 (QueryChatPage)
    → POST /api/v1/query/chat {question}
    → 轮询 GET /api/v1/query/result/{taskId}
    → QueryResultResponse {answer(Markdown), tokenUsage}
    → marked 渲染 Markdown → 展示
    → 用户点击「查看子图」
    → GET /api/v1/analysis/subgraph/{taskId}
    → SubgraphResponse {nodes, edges, pruningMeta}
    → G6 适配层 transform → G6 GraphData
    → AntV G6 渲染力导向图

文档子图链路：
  用户选择文档 (GraphVisualizePage)
    → GET /api/v1/graph/document/{documentId}
    → GraphSubgraphVO {nodes, edges}
    → G6 适配层 transform → G6 GraphData
    → AntV G6 渲染
```

---

## 3. 路由设计

| 路径 | 页面组件 | 模块 | 说明 |
|------|---------|------|------|
| `/` | — | — | redirect → `/files` |
| `/files` | `FileManagePage` | 文件管理 | 上传 + 列表分页 + 解析/删除 |
| `/grades` | `GradeManagePage` | 成绩管理 | 按考试编号查询 + 删除 |
| `/qa` | `IntelligentQAPage` | 智能问答 | `/chat` 对话模式，含 Markdown 渲染 |
| `/qa/:taskId` | `IntelligentQAPage` | 智能问答 | 带 taskId 查询历史结果 |
| `/graph` | `GraphVisualizePage` | 图谱可视化 | 选择文档 → 渲染子图 |
| `/graph/document/:id` | `GraphVisualizePage` | 图谱可视化 | 直接渲染指定文档子图 |
| `/fusion` | `FusionManagePage` | 融合管理 | 触发 + 状态 + 回滚 |
| `/metrics` | `MetricsDashboardPage` | 图指标 | PageRank + 度中心性表格 |

**路由守卫**：V1 无鉴权，不设守卫。

---

## 4. 项目目录结构

```
GraphNexus/
├── src/                              # Java 后端（不动）
│   └── main/java/com/graphnexus/...
├── frontend/                         # Vue 3 前端（新建）
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src/
│       ├── main.ts                   # 入口：createApp + router + pinia
│       ├── App.vue                   # 根组件：AppLayout shell
├── assets/
│   ├── tokens.css                   # T-UI-01: CSS variables (OKLCH colors/spacing/rounded/motion)
│   └── global.css                   # T-UI-02: typography hierarchy + base reset
├── shared/                          # 跨模块共享
│   ├── components/                  # BaseButton, BaseInput, BaseCard, DataTable, StatusBadge, MarkdownViewer
│   ├── composables/                 # useApi, usePagination, usePolling
│   └── utils/                       # formatFileSize, formatDate, graphAdapter
├── api/                             # Axios 请求函数（按后端 Tag 分组）
│   ├── client.ts                    # Axios instance + interceptors
│   ├── file.ts                      # /api/v1/document/*
│   ├── graph.ts                     # /api/v1/graph/*
│   ├── query.ts                     # /api/v1/query/*
│   ├── analysis.ts                  # /api/v1/analysis/*
│   └── types.ts                     # ApiResult<T>, PageResult<T>, ErrorResponse 类型定义
├── features/
│   ├── file/                        # 文件管理模块
│   │   ├── FileManagePage.vue
│   │   ├── components/              # FileUpload, FileTable, FileDetail
│   │   └── fileStore.ts
│   ├── grade/                       # 成绩管理模块
│   │   ├── GradeManagePage.vue
│   │   ├── components/              # GradeTable
│   │   └── gradeStore.ts
│   ├── query/                       # 智能问答模块
│   │   ├── IntelligentQAPage.vue
│   │   ├── components/              # ChatInput, MarkdownReport, TokenUsageBar
│   │   └── queryStore.ts
│   ├── graph/                       # 图谱可视化模块
│   │   ├── GraphVisualizePage.vue
│   │   ├── components/              # GraphCanvas, NodeDetailPopover, PruningMetaPanel
│   │   ├── graphAdapter.ts          # GraphSubgraphVO/SubgraphResponse → G6 GraphData
│   │   └── graphStore.ts
│   ├── fusion/                      # 融合管理模块
│   │   ├── FusionManagePage.vue
│   │   ├── components/              # FusionStatus, FusionRollback
│   │   └── fusionStore.ts
│   └── metrics/                     # 图指标模块
│       ├── MetricsDashboardPage.vue
│       ├── components/              # MetricsTable
│       └── metricsStore.ts
└── router/
    └── index.ts                     # Vue Router 配置
```

---

## 5. 关键状态机

### 文件状态展示

```
UPLOADED ──[点击解析]──→ PROCESSING ──[轮询完成]──→ COMPLETED
                           │                          │
                           └──[解析失败]──→ FAILED    │
                                                      │
                           所有状态 ──[点击删除]──→ (从列表消失)
```

前端对 `PROCESSING` 状态每 2 秒轮询 `GET /api/v1/document/{id}` 直到 `COMPLETED` 或 `FAILED`。

### 问答任务状态

```
[提交问题] → PENDING → PROCESSING → COMPLETED (展示 Markdown)
                    │              │
                    └──→ FAILED ──→ (展示 errorMessage)
```

异步模式：`POST /ask-async` → 获 taskId → 每 1-2 秒轮询 `GET /result/{taskId}`。

---

## 6. ADR 索引

| ADR | 决策 | 文件 |
|-----|------|------|
| ADR-001 | Naive UI 作为组件库 | `@.specs/adr/001-naive-ui.md` |
| ADR-002 | AntV G6 v5 作图可视化 | `@.specs/adr/002-g6-graph-viz.md` |
| ADR-003 | Feature-based 项目结构 | `@.specs/adr/003-feature-based-structure.md` |

---

## 7. 风险

| # | 风险 | 类型 | 影响 | 概率 | 缓解 |
|---|------|------|------|------|------|
| R1 | AntV G6 v5 如遇阻塞性 bug，替换成本高 | 实现风险 | 图谱可视化页面不可用 | 低 | 在 `<GraphCanvas>` 组件中隔离 G6 依赖；预研 Cytoscape.js 作 fallback 方案（仅切换适配层） |
| R2 | 后端包重构（`package-restructure` change）提交后 DTO 路径变化，前端 TypeScript 类型需同步更新 | 上线风险 | 类型不匹配，编译失败 | 中 | 前端 `api/types.ts` 集中管理响应类型，后端变更时仅改一处；依赖 Swagger doc 做类型校验 |
| R3 | 大子图（>200 节点）Canvas 渲染性能不足，帧率 < 30fps | 实现风险 | 图谱页面卡顿 | 中 | G6 v5 默认 Canvas 渲染器，大图场景可切换 WebGL 渲染器；v1 子图为剪枝产物通常 < 100 节点 |
| R4 | Naive UI 缺少某个业务需要的组件（如复杂表格） | 长期债务 | 需自研或引入第二组件库 | 低 | 评估后如确需 → 用 Tailwind + Lucide 自研简单组件，不引入第二 UI 库 |
| R5 | 生产环境无 Vite proxy，Nginx 需额外配置 SPA + API 反向代理 | 上线风险 | 部署时额外工作 | 高 | DESIGN §9 沉淀 Nginx 参考配置；`deployment/nginx/` 中已有后端 proxy，追加 SPA 段即可 |

---

## 8. 不在范围

- **SSR / SEO**：管理后台无需搜索引擎索引
- **PWA / Service Worker / 离线模式**：V1 仅在线使用
- **前端单元测试 / E2E 测试**：V1 用手动 UAT（见 REQUIREMENT.md AC 验证方式）；测试框架引入留到后续 change
- **国际化（vue-i18n）**：V1 仅中文
- **Theme 切换（亮色/暗色）**：V1 仅亮色极简
- **登录/鉴权前端**：后端 Spring Security V1 临时放开
- **移动端响应式**：仅保证 ≥1280px 桌面端

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|------|------|---------|---------|
| `src/api/client.ts` | Axios 实例 + `ApiResult` unwrap + 错误拦截 | 所有 API 调用 | 后续任何前端 change 必须通过此 client 发请求，禁止直接 import axios |
| `src/views/graph/graphAdapter.ts` | `GraphSubgraphVO` / `SubgraphResponse` → G6 GraphData | 文档子图 + 剪枝子图渲染 | 后续新增子图类型只需加 transform 函数 |
| `src/common/composables/usePolling.ts` | 通用轮询 hook（interval + maxRetries + stopCondition） | 文档解析状态 / 问答异步结果 | 后续任何轮询场景复用 |

### 9.2 新增的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|------|------|---------|---------|
| 前端组件库 | Naive UI 2.x | 全部页面组件 | 中 — 需批量替换 import，工作量约 2-3 天 |
| 图可视化库 | AntV G6 v5 | 图谱可视化模块 | 高 — 需重写适配层 + 组件，工作量约 1 周 |
| CSS 方案 | Tailwind CSS v4 | 全部样式 | 中 — 需批量替换 class 或回退到 CSS variables |
| 项目结构 | Feature-based | 全部源码组织 | 中 — 目录重组 + import 路径更新 |

### 9.3 新增的跨模块契约

```
- 前端 ↔ 后端：HTTP REST，baseURL /api/v1，统一 ApiResult<T> 响应
- Axios client 是前端唯一 HTTP 出口，禁止组件直接 import axios
- Pinia store 是组件唯一数据源，禁止组件内直接调 api/* 函数
```

### 9.4 新增的依赖

| 包 | 用途 |
|----|------|
| naive-ui | UI 组件库 |
| @antv/g6 | 图可视化 |
| pinia | 状态管理 |
| vue-router | 路由 |
| axios | HTTP 客户端 |
| marked | Markdown 解析 |
| highlight.js | 代码语法高亮 |
| lucide-vue-next | 图标 |
| tailwindcss | CSS 框架 |

### 9.5 禁动清单变化

```
- 新增禁动：src/api/client.ts 不允许绕过直接 import axios
- 新增禁动：src/common/components/ 中组件不允许包含业务逻辑（纯展示）
- 新增禁动：features/*/components/ 中组件不允许直接调 api/*（必须通过 store）
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。