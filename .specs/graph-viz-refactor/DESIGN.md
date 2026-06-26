# DESIGN: 知识图谱可视化重写

- **Change ID**: `graph-viz-refactor`
- **关联**: `@.specs/graph-viz-refactor/REQUIREMENT.md`、`@.specs/CONTEXT.md`、`@.specs/frontend-ui/DESIGN.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 2-design 步骤 0 锁定。变栈视为开新 CHANGE（R7.1）。

图可视化库已由 CONTEXT.md 锁定为 **AntV G6 v5**，跨会话沿用。`package.json` 中 `cytoscape` 依赖将被移除。

| 层 | 选定 | 版本 | 状态 |
|----|------|------|:--:|
| 图可视化 | **AntV G6 v5** | ^5.0.0（锁大版本） | 🔒 已锁 |
| 其余全部 | 沿用 `frontend-ui` DESIGN § 0 | 不变 | 🔒 已锁 |

- **理由**：WebGL 渲染支撑全量图谱、内置交互插件减少自研量、与 frontend-ui DESIGN D4 原选型对齐（纠正实现偏差）
- **排除**：Cytoscape.js（Canvas only，全量图性能天花板；Vue 3 生命周期管理已证不可靠导致白屏）

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块：
- frontend/src/views/graph/GraphVisualizePage.vue     （重写）
- frontend/src/views/graph/components/GraphCanvas.vue  （重写，核心变更）
- frontend/src/views/graph/graphAdapter.ts             （重写，输出格式 Cytoscape → G6）
- frontend/src/views/graph/graphStore.ts               （扩展，新增全量/剪枝模式）
- frontend/src/api/graph.ts                            （扩展，新增全量图谱 fetch）
- frontend/src/api/types.ts                            （微调，补充全量图谱响应类型）
- frontend/package.json                                （移除 cytoscape，新增 @antv/g6）

新增模块：
- frontend/src/views/graph/components/GraphToolbar.vue    （搜索栏 + 视图切换）
- frontend/src/views/graph/components/GraphLegend.vue     （类型筛选面板）
- frontend/src/views/graph/components/NodeDetailPanel.vue （节点详情侧面板）
- frontend/src/views/graph/composables/useGraphInteraction.ts （交互逻辑抽离）

禁动清单（AI 不许"顺手"碰）：
- frontend/src/views/file/*        （文件管理，无关）
- frontend/src/views/query/*       （智能问答，无关）
- frontend/src/views/fusion/*      （融合管理，无关）
- frontend/src/views/metrics/*     （图指标，无关）
- frontend/src/views/grade/*       （成绩管理，无关）
- frontend/src/common/components/* （共享组件库，不动）
- frontend/src/api/client.ts       （HTTP client，不动）
- frontend/src/api/file.ts / query.ts / analysis.ts （其他 API 层，不动）
- 所有后端 Java 代码               （纯前端 change，后端不动）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---------|------------|------|
| HTTP 客户端 | `src/api/client.ts` | **沿用** Axios instance |
| 图谱数据管理 | `src/views/graph/graphStore.ts` (Pinia) | **沿用** Pinia store，扩展 action |
| 节点/边颜色映射 | `src/views/graph/graphAdapter.ts` 中的 `NODE_COLORS` / `EDGE_COLORS` 常量 | **沿用**，抽取为独立常量文件 |
| API 类型定义 | `src/api/types.ts` 中的 `GraphSubgraphVO` / `SubgraphResponse` | **沿用**，补充新类型 |
| CSS 设计 token | `src/assets/tokens.css` | **沿用**，不新增 token |
| 通用组件 | `BaseCard`、`BaseSelect`、`StatusBadge` | **沿用**，图谱页继续使用 |
| 图可视化渲染 | Cytoscape.js（`package.json`） | **替换** → G6 v5（理由：白屏 bug + 无 WebGL） |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据流：**沿用** Pinia store → component props → 渲染（Vue 3 标准单向数据流）
- 项目结构：**沿用** feature-based（views/graph/ 内聚所有图谱相关代码）
- API 组织：**沿用** src/api/graph.ts 按后端 Controller 分组
- 错误处理：**沿用** Axios 拦截器 unwrap ApiResult + GlobalExceptionHandler userTip
- 图谱适配层：**沿用** graphAdapter.ts 模式（输入 API VO → 输出渲染格式），仅改输出目标（Cytoscape Elements → G6 GraphData）
- 交互逻辑：**引入新模式** — composables（useGraphInteraction.ts）抽离 G6 事件绑定，与 GraphCanvas 组件分离
  → 理由：Cytoscape 时代交互直接写在 GraphCanvas.vue 中（~100 行），G6 v5 交互更丰富，单一组件会膨胀到 300+ 行。composable 抽离后便于单独测试和复用
- 全量图谱数据：**引入新模式** — 需新增后端端点。详见 §1 D3
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|------|------|---------|---------|
| D1 | **G6 Graph 实例管理**：在 `onMounted` 创建，`watch(data)` 调用 `graph.setData()` + `graph.render()` 增量更新，`onBeforeUnmount` 调用 `graph.destroy()` | 每次数据变化销毁重建实例（Cytoscape 时代做法） | G6 v5 支持 `setData()` 增量更新，销毁重建会导致布局重新计算 + 闪烁。保留实例只更新数据，切换视图时过渡平滑 | `setData()` 后需手动调 `render()`，忘记调用会导致画布不更新；比销毁重建多一层心智负担 |
| D2 | **交互逻辑抽离为 composable**：`useGraphInteraction(graph, options)` 返回 `{ search, filter, select, expand, highlightPath }` 方法 | 所有交互逻辑写在 GraphCanvas.vue 中 | AC-15~19 的 5 种交互模式若全写在一个组件中，文件将超 300 行难以维护。composable 每个交互独立函数，可单独测试，且 GraphCanvas 只负责渲染 | 多一层抽象，composable 需要访问 G6 graph 实例（通过参数传入，不通过 provide/inject） |
| D3 | **全量图谱 v1 策略**：新增后端端点 `GET /api/v1/graph/construction/full` + 前端聚合兜底 | 仅客户端聚合所有文档子图 / 延迟到 v2 | 客户端聚合无法获取融合后的真实拓扑（跨文档边由 fusion 创建，单文档子图不含 MASTERS/PREREQUISITE_OF 跨文档边）。新增端点是最小后端改动（1 个 Controller 方法 + 1 个 Service 方法 + 1 个 Cypher 查询） | **范围扩大**：本次 change 本是"纯前端"，引入最小后端工作（预计 +30 行 Java + 1 个 Cypher）。若用户拒绝范围扩大，全量图谱降级为"聚合文档子图近似视图"移至 v2 |
| D4 | **数据适配层统一**：`graphAdapter.ts` 导出 `toGraphData(input: GraphSubgraphVO | SubgraphResponse, options?)` 统一函数，内部按 `'properties' in node` 判别数据类型分支 | 两个独立 transform 函数（现有 `transformGraphSubgraphVO` + `transformSubgraphResponse`） | 两者输出格式相同（G6 GraphData），统一入口减少 GraphCanvas 侧的条件判断；新增第三种数据源（全量图谱）时只需加分支 | 统一函数内部有 if-else 分支，不如两个纯函数清晰。通过子函数 `transformDocumentSubgraph` / `transformPruningSubgraph` 保持内部分离 |
| D5 | **G6 布局策略**：文档子图 / 剪枝子图使用 `d3-force`（力导向），全量图谱使用 `d3-force` + `force2` 参数调优（更大 repulsion + 更多迭代） | `dagre`（层次布局）/ `circular`（环形） | 知识图谱无天然层次，力导向最能表达拓扑结构。全量图仅调参数不换算法，降低布局切换时的视觉不一致 | 力导向在大图上收敛慢（>1000 节点需 2000+ 迭代），通过 `animate: false` + 加载 skeleton 缓解感知等待 |
| D6 | **搜索实现**：前端内存搜索（遍历 G6 graph.getData()），防抖 300ms，匹配节点 label/id 字段 | 后端搜索 API | 子图规模 < 1000 节点，内存搜索足够快（< 50ms），无需网络往返。全量图如超 5000 节点可后续迁到后端搜索 | 搜索能力受限于前端已加载数据，跨文档搜索需全量图已加载 |
| D7 | **视图模式切换**：页面顶部 Toggle（文档子图 / 剪枝子图 / 全量图谱），切换时保留 G6 实例只换数据 | 多个独立页面（不同路由） | 三种视图共享同一个 GraphCanvas 和交互逻辑，Toggle 切换只需更新数据源，避免组件销毁重建 | 全量图谱和单文档子图数据量差异大，切换时可能有 1-2s 布局重算（通过 skeleton 过渡缓解） |

---

## 2. 数据流 / 架构图

### 2.1 组件树

```
GraphVisualizePage.vue
├── GraphToolbar.vue
│   ├── NodeSearchInput.vue        ← 搜索框 + 下拉建议
│   └── ViewModeToggle.vue         ← 子图 / 剪枝 / 全量 三选一
├── BaseCard (复用)
│   ├── GraphCanvas.vue            ← G6 实例挂载点
│   │   └── <div ref="container">  ← G6 渲染目标
│   ├── GraphLegend.vue            ← 类型筛选 checkbox 组
│   └── NodeDetailPanel.vue        ← 侧边滑出面板
└── PruningMetaPanel.vue           ← 剪枝元信息（仅剪枝视图）
```

### 2.2 数据流

```
┌────────────────────────────────────────────────────────────┐
│ GraphVisualizePage.vue (orchestrator)                      │
│                                                             │
│  viewMode = ref<'document' | 'pruning' | 'full'>           │
│  selectedDocId / taskId  (路由参数驱动)                     │
│        │                                                    │
│        ▼                                                    │
│  graphStore.loadSubgraph(mode, params)                      │
│        │                                                    │
│        ├── mode='document' → GET /api/v1/graph/             │
│        │   construction/document/{id}                        │
│        │   → GraphSubgraphVO                                │
│        │                                                     │
│        ├── mode='pruning'  → GET /api/v1/analysis/          │
│        │   subgraph/{taskId}                                 │
│        │   → SubgraphResponse                               │
│        │                                                     │
│        └── mode='full'     → GET /api/v1/graph/             │
│            construction/full  (NEW)                          │
│            → FullGraphVO                                    │
│        │                                                    │
│        ▼                                                    │
│  graphAdapter.toGraphData(response, { mode })               │
│        │                                                    │
│        │  GraphSubgraphVO ──→ G6 GraphData                  │
│        │  SubgraphResponse ──→ G6 GraphData                 │
│        │  FullGraphVO      ──→ G6 GraphData                 │
│        │                                                    │
│        ▼                                                    │
│  graphData = ref<G6GraphData | null>                        │
│        │                                                    │
│        ▼                                                    │
│  GraphCanvas.vue                                            │
│    watch(graphData) → graph.setData(graphData)              │
│                    → graph.render()                         │
│                                                             │
│  useGraphInteraction(graph, graphData)                      │
│    ├── search(query) → highlight matching nodes             │
│    ├── filter(types) → show/hide by type                   │
│    ├── selectNode(id) → show detail panel                  │
│    ├── expandNeighbors(id) → highlight 1-hop               │
│    └── highlightPath(a, b) → shortest path highlight        │
│                                                             │
└────────────────────────────────────────────────────────────┘
```

### 2.3 G6 实例生命周期

```
  onMounted
    │
    ▼
  new Graph({ container, ...staticConfig })
    │
    ▼
  watch(graphData)
    │
    ├── graphData 为 null → 显示空态
    │
    └── graphData 有值
          │
          ├── 首次：graph.setData(data) → graph.render()
          │         → layout 计算 → 渲染完成
          │
          └── 后续（切换文档/模式）：
                graph.setData(data) → graph.render()
                  → 新 layout 计算 → 过渡渲染
    │
    ▼
  onBeforeUnmount
    │
    ▼
  graph.destroy()
```

---

## 3. 关键状态机

### 图谱视图状态

```
              ┌──────────┐
              │  empty   │ ← 初始态，未选择文档/task
              └────┬─────┘
                   │ 用户选择文档/输入taskId/切换全量
                   ▼
              ┌──────────┐
              │ loading  │ ← skeleton 占位
              └────┬─────┘
                   │
            ┌──────┴──────┐
            ▼              ▼
       ┌─────────┐   ┌──────────┐
       │  ready  │   │  error   │ ← 含 errorMessage + 重试按钮
       └────┬────┘   └──────────┘
            │
    用户交互（搜索/筛选/点击/展开）
            │
            ▼
       ┌─────────┐
       │  ready  │ ← 画布更新高亮/筛选态，不触发 loading
       └─────────┘
```

### 节点选中状态

```
  [未选中] ──click(node)──→ [选中]
                               │
                               ├── 显示 NodeDetailPanel（侧边）
                               ├── 节点边框加粗 + 发光
                               │
                               ├── click(空白) → [未选中]
                               ├── click(另一node) → 切换选中节点
                               └── Escape → [未选中]

  [选中节点A] ──Ctrl+click(节点B)──→ [路径高亮 A↔B]
                                       │
                                       └── Escape → [仅选中A]
```

---

## 4. ADR 索引

| ADR | 决策 | 文件 | 本次动作 |
|-----|------|------|---------|
| ADR-002 | AntV G6 v5 作图可视化 | `@.specs/adr/002-g6-graph-viz.md` | **update**：补充 WebGL 默认渲染器 + 全量图谱 + 交互架构决策 |
| ADR-xxx | 全量图谱 API 新增（如 D3 获批） | `@.specs/adr/003-full-graph-api.md` | **new**（条件：用户批准 D3 范围扩大） |

### ADR-002 修正摘要

原 ADR-002 说 G6 v5 为 Canvas 渲染，v5 已默认 WebGL。修正：① 渲染器 → WebGL（fallback Canvas）；② 新增全量图谱数据源；③ 交互逻辑抽离为 composable。

---

## 5. 风险

| # | 风险 | 类型 | 影响 | 概率 | 缓解 |
|---|------|------|------|------|------|
| R1 | G6 v5 WebGL 渲染在某些浏览器/GPU 上不兼容，回退到 Canvas 后大图性能骤降 | 实现风险 | 全量图谱 ≥ 1000 节点时帧率 < 20fps | 低 | G6 v5 内置 WebGL→Canvas 自动降级；`graph.getRendererType()` 检测当前渲染器，Canvas 下全量图自动限制显示节点数 + 提示用户 |
| R2 | G6 v5 的 `setData()` + `render()` 增量更新在某些场景不生效（如节点数量从 10→500），需手动 `destroy()` 重建 | 实现风险 | 切换视图时画布不更新或残留旧数据 | 中 | 在 `watch(graphData)` 中加防御：比较新旧数据节点数，差异 > 5× 时走 destroy+create 路径 |
| R3 | 后端无全量图谱端点，D3 若被拒，全量图谱 v1 只能客户端聚合 → 缺失跨文档融合边（MASTERS/PREREQUISITE_OF across documents），视图不完整 | 上线风险 | 全量图谱功能不完整或延迟 | 中 | 若 D3 被拒，全量图谱降级为"跨文档子图拼合视图"并标注数据不完整；或移至 v2 等后端 API 就绪 |
| R4 | `@antv/g6` 包体积较大（~500KB gzip），影响首屏 LCP | 长期债务 | 图谱页首次加载慢 | 低 | 图谱页 lazy load（`defineAsyncComponent`），G6 仅在访问 `/graph` 路由时加载，不影响其他页面首屏 |
| R5 | composable `useGraphInteraction` 与 G6 事件系统耦合过深，G6 大版本升级时可能需要重写 | 长期债务 | G6 升级成本高 | 低 | composable 只使用 G6 公开 API（`getData`/`setData`/`getElementById`/`on` 事件），不访问内部属性；接口稳定 |

---

## 6. 不在范围

- **新增后端 API**：除 D3 提议的 `GET /graph/construction/full` 外，不新增任何后端端点
- **其他前端页面的图谱嵌入**：智能问答页的「查看子图」按钮跳转到 `/graph` 路由（现有行为），不在问答页内嵌小图谱
- **图谱性能监控/APM**：不在 v1 集成性能打点或帧率监控面板
- **节点/边样式自定义（用户侧）**：颜色映射沿用现有常量，不给用户提供自定义 UI
- **图谱数据缓存策略**：每次切换视图重新请求 API，不做客户端 LRU 缓存

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|------|------|---------|---------|
| `src/views/graph/composables/useGraphInteraction.ts` | G6 图谱交互逻辑封装（搜索/筛选/选中/展开/路径高亮） | 任何使用 G6 渲染图谱的组件 | 后续如果有其他页面需要图谱交互（如问答页嵌入小图），直接复用 composable |
| `src/views/graph/graphAdapter.ts`（重写） | 多种后端 VO → G6 GraphData 统一转换 | 文档子图 / 剪枝子图 / 全量图谱三种数据源 | 新增第四种图谱数据源时只需加 `case` 分支 + 子 transform 函数 |
| `src/views/graph/constants.ts`（新增） | `NODE_COLORS` / `EDGE_COLORS` / `NODE_SIZES` / `EDGE_LINE_STYLES` 常量 | 图谱渲染样式映射 | 从 graphAdapter.ts 中抽取，后续如果图例/详情面板需要引用颜色映射，不需要依赖适配层 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|------|------|---------|---------|
| 图可视化库实现 | AntV G6 v5（WebGL 默认） | 图谱可视化模块 | 高 — 需重写适配层 + 交互 composable + GraphCanvas，约 1 周 |
| 图交互架构 | Composable 模式（`useGraphInteraction`） | 图谱交互逻辑 | 低 — 可改为 Pinia store 或直接写在组件中 |
| 全量图谱 API | `GET /api/v1/graph/construction/full`（待批准） | 后端 ConstructionController + 前端 graphStore | 低 — 单一端点，新增约 30 行 Java |

### 9.3 新增的跨模块契约

```
- 前端 graphAdapter.toGraphData() 是图数据到 G6 的唯一入口，GraphCanvas 禁止直接处理 API 响应
- 后端 GET /api/v1/graph/construction/full 响应格式遵循 FullGraphVO（待定义），结构与 GraphSubgraphVO 一致（nodes[] + edges[]）
```

### 9.4 新增 / 升级的依赖

| 包 | 版本 | 用途 | 是否替换既有 |
|----|------|------|:--:|
| `@antv/g6` | ^5.0.0 | 图可视化引擎（WebGL + Canvas 双渲染） | 是 → 替换 `cytoscape` |
| `cytoscape` | — | — | 移除 |

### 9.5 禁动清单变化

```
- 新增禁动：GraphCanvas.vue 禁止直接 import API 类型或 graphAdapter（数据由父组件 props 传入）
- 新增禁动：views/graph/ 以外的模块禁止直接 import G6（图谱交互能力通过 composable 暴露，不暴露 G6 API）
- 新增禁动：graphAdapter.ts 中的颜色常量已迁至 constants.ts，禁止从 graphAdapter  import 颜色
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。
