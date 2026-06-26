# DESIGN: 学情诊断页增加剪枝子图可视化 + 度量映射 + 多次考试趋势

- **Change ID**: `diagnosis-subgraph-viz`
- **关联**: `@.specs/diagnosis-subgraph-viz/CHANGE.md` + `REQUIREMENT.md`
- **ADR 编号范围**: ADR-034 ~ ADR-036

---

## 0. 技术栈选定

全部沿用已有锁定的技术栈（`@.specs/CONTEXT.md` 已锁技术决策），不引入新依赖：

| 层 | 技术 | 来源 |
|---|---|---|
| 前端框架 | Vue 3 + TypeScript + Vite | CONTEXT § 已锁技术决策 |
| 状态管理 | Pinia（`queryStore.ts`） | 既有 |
| HTTP 客户端 | Axios（`api/client.ts`） | 既有 |
| HTML 净化 | DOMPurify（`HtmlSvgViewer.vue`） | 既有 |
| 子图数据转换 | `graphAdapter.ts` → `transformPruningSubgraph()` | 既有 |
| 后端框架 | Spring Boot 3.3.x + Java 17 | CONTEXT § 已锁技术决策 |
| Neo4j 访问 | `QueryGraphRepository`（只读） | 既有 |
| 子图 API | `GET /api/v1/analysis/subgraph/{taskId}` | 既有端点 |

**唯一技术选型决策**：诊断子图渲染方案（见 § 1 D1）。

---

## 0.5 既有架构对齐

### 0.5.1 触碰模块清单

**会修改的前端文件**：

| 文件 | 操作 | 说明 |
|---|---|---|
| `frontend/src/views/query/IntelligentQAPage.vue` | 修改 | 在 MarkdownReport 下方插入 `<DiagnosisSubgraph>` 组件 |
| `frontend/src/views/query/queryStore.ts` | 修改 | 新增 `subgraphData`/`subgraphLoading` 状态 + `loadSubgraph(taskId)` action |
| `frontend/src/api/types.ts` | 不修改 | `SubgraphResponse` 类型已完备，无需变更 |

**会新增的前端文件**：

| 文件 | 说明 |
|---|---|
| `frontend/src/views/query/components/DiagnosisSubgraph.vue` | 诊断子图可视化组件（图渲染 + 图例 + 节点点击） |
| `frontend/src/views/query/components/ExamTrendChart.vue` | 微型考试趋势折线图组件（纯 SVG） |
| `frontend/src/views/query/components/DiagnosisNodeDetail.vue` | 诊断节点详情滑出面板（考试历史列表 + 趋势图） |

**会修改的后端文件**：

| 文件 | 操作 | 说明 |
|---|---|---|
| `src/.../strategy/StudentDiagnosisStrategy.java` | 修改 | `buildResult()` 中 MASTERS 边 description 从 null → 考试历史 JSON；KP 节点 properties 增加 weight |

**不可触碰的文件**（禁动清单本次扩展）：

| 文件 | 原因 |
|---|---|
| `GraphVisualizePage.vue` | 全屏图编辑器，与诊断内嵌子图用途不同 |
| `GraphCanvas.vue` | 全屏 G6 画布，诊断子图用独立轻量组件 |
| `NodeDetailPanel.vue` | 全屏图的详情面板，诊断用独立面板 |
| `graphStore.ts` | 全屏图状态，与 queryStore 分属不同域 |
| `StudentDiagnosisStrategy.prune()` 剪枝逻辑 | 只补数据传递，不改剪枝算法 |

### 0.5.2 既有抽象复用 vs 新引入

| 本次需要 | 既有有没有？ | 决定 |
|---|---|---|
| 子图数据获取 | `api/analysis.ts` → `getPrunedSubgraph(taskId)` | **沿用** |
| 子图数据 → 渲染格式转换 | `graphAdapter.ts` → `transformPruningSubgraph()` | **沿用**（但需补充颜色/大小映射逻辑） |
| SubgraphResponse 类型 | `api/types.ts` → `SubgraphResponse`, `SubgraphNodeVO`, `SubgraphEdgeVO`, `PruningMetaVO` | **沿用** |
| 图渲染引擎 | `@antv/g6` v5.1.1（已安装） | **不沿用** — 诊断子图 ≤30 节点、只读、内嵌展示，G6 过重。新建纯 SVG 轻量渲染（理由见 ADR-034） |
| HTML 安全净化 | `HtmlSvgViewer.vue` → DOMPurify | **沿用** — 节点属性展示走 DOMPurify |
| 节点颜色常量 | `views/graph/constants.ts` → `NODE_COLORS` | **部分沿用** — Student/Mastery 色值可参考，但诊断子图颜色由 MASTERS weight 动态计算，不走静态 type→color 映射 |
| HTTP 客户端 | `api/client.ts` | **沿用** |
| Pinia store 模式 | `queryStore.ts`（Composition API style） | **沿用** |
| 侧边滑出面板 | `NodeDetailPanel.vue`（Transition + fixed 定位） | **不沿用**（诊断面板内容结构不同：考试历史列表 + 趋势图）。新建 `DiagnosisNodeDetail.vue`，复用同样的 slide Transition 动画模式 |
| 折线图渲染 | 无 | **新建** `ExamTrendChart.vue`（纯 SVG，不引入图表库） |

### 0.5.3 沿用模式 vs 引入新模式

- **组件组织**：**沿用** `views/<domain>/components/` 目录结构（`views/query/components/` 下已有 ChatInput/HistoryPanel/MarkdownReport/TokenUsageBar）
- **状态管理**：**沿用** Pinia Composition API store（`queryStore.ts`），新增 `subgraphData`/`subgraphLoading`/`selectedKpNode` 状态
- **API 调用**：**沿用** `api/*.ts` → `client.post/get` → `ApiResult<T>` 解包模式
- **SVG 子图渲染**：**引入新模式** — 项目中首次使用纯 SVG 做图可视化（G6 之外的第二条路径）。理由：G6 为万级节点设计，诊断子图 ≤30 节点用 SVG 更轻更可控，二者场景不重叠。详见 ADR-034
- **趋势图渲染**：**引入新模式** — 纯 SVG 微型折线图。理由：不需要 ECharts/Chart.js 的全套能力，一个 280×140px 的 SVG 元素足够

---

## 1. 技术决策

### D1 · 诊断子图渲染方案：纯 SVG（非 G6 v5）

- **选择**：纯 SVG（`<svg>` + 手动 `<circle>`/`<line>`/`<text>` + 简单力导向布局）
- **备选**：AntV G6 v5（已集成，`GraphCanvas.vue` 使用的方案）
- **选择理由**：
  1. 诊断子图规模 ≤ 30 节点，G6 的 WebGL 渲染管线、插件系统、事件代理对此规模是过度抽象
  2. 内嵌在内容页中（非全屏），需要一个可自然参与文档流的 HTML 元素，G6 的绝对定位 Canvas 需要手动管理容器尺寸
  3. 纯 SVG 的节点样式定制（按 weight 动态 fill、动态 r）比 G6 的 data-driven style 映射更直观、更少配置
  4. 减少组件复杂度：不需要 Graph 实例生命周期（create/render/destroy/resize），不需要与 fullscreen 状态交互
  5. G6 v5 保留给 `GraphVisualizePage` 的全屏交互式图编辑场景（万级节点、缩放拖拽、邻域展开、路径高亮），诊断子图是只读紧凑展示，二者场景不重叠
- **代价**：
  - 需自行实现简单布局算法（力导向或分层布局）— 对于 ≤30 节点的星型 + 树型混合结构，100 行以内的简单力模拟即可
  - 不支持 v2 中可能需要的缩放/拖拽 — 当前 v1 不需要，若 v2 需要可届时迁移到 G6

### D2 · 布局算法：简单力导向 + 层级约束

- **选择**：轻量力导向模拟（~50 次迭代），Student 节点固定在画布上方居中
- **备选**：纯层级布局（Layer 0 = Student，Layer 1 = 弱掌握 KP，Layer 2 = 前置 KP）
- **选择理由**：纯层级在 PREREQUISITE_OF 边较多时容易出现同层节点重叠；力导向自动分散节点、减少重叠，且计算量极小（30 节点 × 50 迭代 = 瞬间完成）
- **代价**：每次渲染结果布局略有不同（力导向随机初始位置），可用固定 seed 或接受微小差异（不影响信息传达）

### D3 · 节点颜色映射：四档离散色阶

- **选择**：按 weight 分四档离散色（红/橙/黄/绿），非连续 gradient
- **备选**：HSL 连续插值（weight=0 → 0° 红, weight=1 → 120° 绿）
- **选择理由**：
  1. 离散色阶提供明确的"严重/需关注/一般/良好"语义锚点，用户无需精确比较色相即可判断等级
  2. 连续插值的中间色（如 weight=0.5 的黄绿色）在色觉障碍用户眼中难以与邻色区分
  3. 与图例标签一一对应，图例更简洁
- **代价**：weight 的微小差异（如 0.39 vs 0.41）跨越阈值时颜色跳变，可能误导。缓解：节点大小是连续映射，弥补离散颜色的粒度不足

### D4 · 节点大小映射：连续线性映射

- **选择**：`radius = 12 + weight * 28` → 范围 [12, 40] px（SVG circle r 属性）
- **备选**：`radius = 20 + weight * 40` → [20, 60] px
- **选择理由**：诊断子图画布紧凑（~600×400px），节点半径过大在 30 节点时重叠严重。12~40px 范围在紧凑画布上区分度足够
- **代价**：小半径时文字标签可能溢出节点边界 → 标签放在节点下方而非内部解决

### D5 · 趋势图实现：纯 SVG 折线图

- **选择**：`ExamTrendChart.vue` 用纯 `<svg>` 元素绘制折线 + 圆点 + 标注，不引入图表库
- **备选**：引入 ECharts 或 Chart.js
- **选择理由**：
  1. 趋势图极其简单：x=日期, y=0~1, 最多 10 个数据点，不需要坐标轴刻度计算、响应式、动画等重型能力
  2. 项目尚未引入任何图表库，为 280×140px 的微型图新增依赖不值得
  3. 纯 SVG ~60 行代码即可实现，打包体积 0 增加
- **代价**：若未来需要更多图表类型（柱状图、雷达图），需重新评估引入图表库

### D6 · 子图数据加载时机：异步非阻塞

- **选择**：LLM 报告先渲染，子图数据通过 `onMounted` 中调用 `store.loadSubgraph(taskId)` 异步加载
- **备选**：在 `handleResult()` 中同步等待子图数据加载完再渲染
- **选择理由**：
  1. 子图 API 是独立端点，不应阻塞 LLM 文本报告的展示
  2. 用户体验：先看到文字结论（核心信息），子图随后出现（补充可视化）
  3. 即使子图加载失败（AC-7），文本报告不受影响
- **代价**：子图区域有短暂的加载闪烁 → 用 skeleton/shimmer 占位缓解

### D7 · MASTERS description 透传方案

- **选择**：在 `StudentDiagnosisStrategy.buildResult()` 中，MASTERS 边的 `GraphEdgeData` 第 4 个参数（description）从 `null` 改为实际的考试历史 JSON 字符串；KP 节点的 properties 中增加 `weight` 字段
- **备选**：新增独立 API 端点查询 MASTERS description
- **选择理由**：
  1. 数据已在内存中（`mastersRows` 返回的 description 列），只是未传入 `GraphEdgeData`。改动 3 行代码
  2. 新增 API 需要 Controller + Service + DTO 全套，过度工程
  3. `SubgraphResponse.EdgeVO` 不含 description 字段——需注意当前 API 响应契约中 EdgeVO 只有 `(sourceNodeId, targetNodeId, edgeType, weight)`，**不含 description**。因此 description 需放到源节点的 properties 中（如 `examHistory` 字段），或扩展 EdgeVO
- **代价与修正**：经检查 `SubgraphResponse.EdgeVO` 不含 description 字段（`@.specs/CONTEXT.md` 中也未提及边的 description 透传），需采取方案：将 MASTERS 考试历史 JSON 作为 KP 节点的 `properties.examHistory` 字段传递（而非透传边 description），避免 API 契约变更。**这修正了 CHANGE.md 中"改 MASTERS 边 description"的初始设想**，实际实现更简单：`nodes` 中 KP 节点的 properties Map 加 `weight` 和 `examHistory` 两个 key

### D8 · 子图画布尺寸

- **选择**：宽度 100%（跟随父容器），高度 400px 固定，内部 SVG viewBox="0 0 600 400"
- **备选**：响应式高度（按节点数动态计算）
- **选择理由**：≤30 节点在 600×400 空间内布局足够；固定高度避免父容器在子图加载前后发生大幅布局抖动
- **代价**：节点极少时（<5）画布显得空旷 → 可调小力导向的 repulsion 让节点聚合

---

## 2. 数据流 / 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    IntelligentQAPage.vue                  │
│                                                           │
│  ┌──────────────────────────────────────────────────┐    │
│  │ ChatInput (用户问题)                               │    │
│  └──────────────┬───────────────────────────────────┘    │
│                 │ sendChat(question)                      │
│                 ▼                                         │
│  ┌──────────────────────────────────────────────────┐    │
│  │ queryStore.sendChat()                             │    │
│  │   → POST /api/v1/query/chat                       │    │
│  │   → handleResult(): answer + taskId                │    │
│  └──────────────┬───────────────────────────────────┘    │
│                 │                                         │
│    ┌────────────┼────────────┐                           │
│    ▼            ▼            ▼                           │
│  ┌──────┐  ┌──────┐   ┌──────────┐                      │
│  │Mark- │  │Token │   │Diagnosis │  ← onMounted          │
│  │down  │  │Usage │   │Subgraph  │    loadSubgraph()     │
│  │Report│  │Bar   │   │  .vue    │                       │
│  └──────┘  └──────┘   └────┬─────┘                      │
│                             │                             │
│               ┌─────────────┼─────────────┐              │
│               ▼             ▼             ▼              │
│         ┌────────┐   ┌──────────┐  ┌──────────┐         │
│         │ SVG 图  │   │  图例    │  │Diagnosis  │         │
│         │(力导向) │   │(颜色→    │  │NodeDetail │         │
│         │节点+边  │   │ 掌握度)  │  │  面板     │         │
│         └───┬────┘   └──────────┘  └────┬─────┘         │
│             │ 点击节点                    │               │
│             └────────────────────────────┘               │
│                                                           │
│  API 调用 (非阻塞):                                       │
│  GET /api/v1/analysis/subgraph/{taskId}                   │
│    → SubgraphResponse { nodes, edges, pruningMeta }       │
│    → transformPruningSubgraph() → { nodes, edges }        │
│    → 覆盖 node.color/size 按 weight 映射                  │
│    → 力导向布局 → SVG 渲染                                │
│                                                           │
│  KP 节点 properties:                                      │
│    { name, weight, examHistory, ... }                     │
│    examHistory = "{ examCount, details: [...] }"          │
│    → ExamTrendChart.vue 解析 → SVG 折线图                 │
└───────────────────────────────────────────────────────────┘

后端数据流:

StudentDiagnosisStrategy.buildResult()
  │
  │ mastersRows → Map<kpId, { weight, description }>
  │
  ├─→ KP 节点 properties: { name, ..., weight, examHistory }
  │     (weight 从 kpMasteryMap 取, examHistory 从 mastersRows description 取)
  │
  └─→ MASTERS 边: (studentId → kpId, "MASTERS", weight, null)
        (description 依旧 null — 考试历史走节点 properties.examHistory)

  ──→ PrunedSubgraph → SubgraphResponse (AnalysisController)
        NodeVO.properties = { name, weight, examHistory, ... }
        EdgeVO = { sourceNodeId, targetNodeId, "MASTERS", weight }
```

**状态机**：

```
queryStore.subgraphState:
  idle ──loadSubgraph(taskId)──▶ loading
  loading ──200 OK─────────────▶ loaded
  loading ──4xx/5xx────────────▶ error
  loaded  ──新诊断──────────────▶ idle (清空上次子图)
```

---

## 3. ADR

### ADR-034 · 诊断子图渲染方案选择：纯 SVG

- **Context**: 诊断页需要在 LLM 文本报告下方嵌入一个只读的剪枝子图。节点数 ≤ 30，需要节点按 MASTERS weight 着色/缩放，点击节点弹出详情面板。项目已有 AntV G6 v5 用于全屏图编辑页。
- **Decision**: 使用纯 SVG（`<svg>` + 手动 `<circle>`/`<line>`/`<text>` + 简单力导向布局），不使用 G6 v5。数据转换复用 `graphAdapter.transformPruningSubgraph()` 的类型和部分逻辑，但渲染层独立于 G6。
- **Consequences**:
  - 正面：组件更轻（无 Graph 实例生命周期），更易嵌入文档流，样式完全可控，无额外打包成本
  - 负面：需自行实现简单布局算法（≤50 行力导向），不支持 G6 的缩放/拖拽/插件生态
  - 若 v2 需要交互式图探索，可届时迁移到 G6——迁移路径清晰（数据格式已兼容）

### ADR-035 · MASTERS 考试历史数据传递路径

- **Context**: MASTERS 边的 description 字段存储考试历史 JSON（`TimeDecayStrategy` 生成），但 `SubgraphResponse.EdgeVO` 当前不含 description 字段。前端需要该数据来渲染详情面板的考试历史列表和趋势图。
- **Decision**: 将考试历史 JSON 作为 KP 节点的 `properties.examHistory` 字段传递（而非扩展 EdgeVO 增加 description）。`StudentDiagnosisStrategy.buildResult()` 中在构建 KP 节点的 `GraphNodeData` 时，从 `mastersRows` 取 description 放入 properties。`weight` 同理放入 `properties.weight`。
- **Consequences**:
  - 正面：零 API 契约变更（`NodeVO.properties` 是 `Map<String, Object>`，天然兼容新增 key），前端无需等待后端 API 升级
  - 负面：`examHistory` 与 KP 节点绑定而非与 MASTERS 边绑定，语义上边是考试历史的载体。但诊断子图中每个 KP 最多一条 MASTERS 边，语义歧义可忽略
  - 替代方案（扩展 EdgeVO 加 description 字段）同样可行但涉及 API 契约变更，需同步更新 types.ts 的 `SubgraphEdgeVO`，风险略高

### ADR-036 · 诊断子图组件隔离策略

- **Context**: 项目已有 `GraphCanvas.vue`（全屏 G6 画布）和 `NodeDetailPanel.vue`（全屏图节点详情）。诊断子图是不同场景（内嵌、只读、紧凑），直接复用会导致两类场景互相拖累。
- **Decision**: 诊断子图使用独立组件树（`DiagnosisSubgraph.vue` + `DiagnosisNodeDetail.vue` + `ExamTrendChart.vue`），与 `views/graph/` 下的全屏图组件完全隔离。共享层仅限 `graphAdapter.ts` 的类型定义（`G6GraphData`/`G6GraphNode`/`G6GraphEdge` 等 interface）和 `constants.ts` 的色值参考，不共享渲染组件。
- **Consequences**:
  - 正面：两个场景独立演化，不会因为一方需求改动而破坏另一方。诊断子图的纯 SVG 方案不会影响全屏图的 G6 升级
  - 负面：`NodeDetailPanel.vue` 和 `DiagnosisNodeDetail.vue` 在 slide Transition 动画上存在模式重复——可接受，因为内容结构完全不同（考试历史 vs 图属性）
  - 未来若出现第三个图可视化场景，需重新评估是否抽取共享图渲染抽象

---

## 4. 风险

| # | 风险 | 类型 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|------|
| R1 | 力导向布局在特定图结构（如链状 PREREQUISITE_OF）下收敛慢或节点重叠 | 实现 | 中 | 中 | 提供 fallback 分层布局（按 MASTERS 距离分层排列）；50 次迭代后强制停止并接受当前布局 |
| R2 | `examHistory` JSON 解析失败（格式与 TimeDecayStrategy 生成不一致、含特殊字符、null）| 实现 | 中 | 低 | 前端 `JSON.parse` 包裹 try-catch，解析失败时降级展示"历次考试数据格式异常"，不阻塞子图渲染 |
| R3 | 连续诊断后子图状态残留（上一次诊断的子图在新诊断完成前短暂可见）| 上线 | 低 | 中 | `handleResult()` 中重置 `subgraphData = null`；`DiagnosisSubgraph.vue` 在 `subgraphData` 为 null 时不渲染 |
| R4 | SVG 内嵌 HTML（节点标签含 XSS 向量）| 安全 | 低 | 高 | 节点标签文本不通过 `v-html` 渲染，使用 SVG `<text>` 元素的 `textContent`（自动转义）；详情面板的属性展示走 DOMPurify |
| R5 | 后端 `examHistory` 字段变更引入后，前端未同步上线致解析失败 | 上线 | 低 | 低 | 前后端同 change 部署，DEV 阶段确认 NodeVO.properties 含 `examHistory` 后再写前端解析逻辑 |

---

## 9. 架构沉淀建议

### 9.1 新增可复用抽象

| 抽象 | 路径 | 复用场景 | 入选理由 |
|------|------|---------|---------|
| `ExamTrendChart.vue` | `frontend/src/views/query/components/` | ① 成绩管理页成绩趋势；② 知识点全局掌握度趋势 | 纯 SVG 折线图组件，Props 仅需 `details: Array<{date, value}>`，与业务解耦即可复用。建议后续迁至 `common/components/` |
| 力导向布局工具函数 | `DiagnosisSubgraph.vue` 内（后续可抽为 `common/utils/forceLayout.ts`） | 任何需要小规模图可视化且不引入 G6 的场景 | ≤30 节点场景的轻量替代 |

### 9.2 项目级技术决策

本 change 无新增项目级技术决策。渲染方案选择（ADR-034）是组件级决策，不约束其他模块的图可视化选型。

### 9.3 跨模块契约

无新增 API 端点或跨模块事件。`GET /api/v1/analysis/subgraph/{taskId}` 响应体中 NodeVO.properties 新增 `weight` 和 `examHistory` 两个可选 key（向后兼容，旧客户端忽略未知 key 不报错）。

### 9.4 依赖变动

无。不新增 npm 包或 Maven 依赖。

### 9.5 禁动清单变动

本次不扩展禁动清单。`GraphVisualizePage.vue` / `GraphCanvas.vue` / `NodeDetailPanel.vue` 的禁动理由已在 § 0.5.1 声明，属于 change 内约定非项目级禁动。

---

> 自检：技术栈已锁定 ✅ | 既有架构对齐已写入 ✅ | 每条决策有备选+理由+代价 ✅ | 数据流图 ✅ | ADR 3 份 ✅ | 风险 5 条每条有缓解 ✅ | 无完整代码实现 ✅ | § 9 已写 ✅