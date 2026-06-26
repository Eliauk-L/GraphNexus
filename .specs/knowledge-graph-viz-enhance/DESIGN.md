# DESIGN: 知识点图谱可视化增强 — 学科全景 + 度量排行

- **Change ID**: `knowledge-graph-viz-enhance`
- **关联**: `@.specs/knowledge-graph-viz-enhance/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 CONTEXT.md「已锁技术决策」直接锁定，不重新选型。

- **选定**：既有栈全继承
- **前端**：Vue 3 + TypeScript + Vite（已锁定）
- **后端**：Spring Boot 3.3.x + Java 17（已锁定）
- **图数据库**：Neo4j 5.26 + GDS 5.x（已锁定）
- **关系数据库**：MySQL 8.0（本次不涉及）
- **图可视化库**：AntV G6 v5 + WebGL 渲染器（已锁定）
- **关键依赖**：Pinia（状态管理）、Caffeine（指标缓存）、Spring Data Neo4j `Neo4jClient`（Cypher 查询）
- **理由**：本次为既有页面功能增强，无引入新依赖的需求。所有能力（G6 v5 渲染、GDS 算法、Neo4j Cypher 查询、Vue 3 组件体系）均已就绪
- **明确排除**：不引入新 npm 包、不升级现有依赖版本、不引入新 Java 依赖

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
后端触碰：
- src/main/java/.../api/graph/controller/ConstructionController.java（新增端点）
- src/main/java/.../api/graph/controller/MetricsController.java（扩展 subject 参数）
- src/main/java/.../application/graph/construction/service/ConstructionService.java（新增接口方法）
- src/main/java/.../application/graph/construction/service/impl/ConstructionServiceImpl.java（新增实现）
- src/main/java/.../application/graph/metrics/service/MetricsService.java（接口新增方法）
- src/main/java/.../application/graph/metrics/service/impl/MetricsServiceImpl.java（实现 subject 过滤）
- src/main/java/.../infrastructure/neo4j/repository/ConstructionGraphRepository.java（新增学科图查询）
- src/main/java/.../infrastructure/neo4j/repository/QueryGraphRepository.java（复用 findDistinctSubjects）

前端触碰：
- frontend/src/views/graph/GraphVisualizePage.vue（学科选择器 + 度量开关 + 视图模式）
- frontend/src/views/graph/graphStore.ts（新增 actions）
- frontend/src/views/graph/graphAdapter.ts（新增度量映射转换函数）
- frontend/src/views/graph/constants.ts（新增度量映射常量）
- frontend/src/views/graph/components/GraphToolbar.vue（新增 PageRank 开关 + 排行按钮）
- frontend/src/views/graph/components/NodeDetailPanel.vue（新增度量指标区）
- frontend/src/views/graph/composables/useGraphInteraction.ts（不变，但新增 useMetrics composable）
- frontend/src/api/graph.ts（新增 API 函数）
- frontend/src/api/types.ts（新增类型）

新增模块：
- frontend/src/views/graph/components/MetricsPanel.vue（度量排行面板）
- frontend/src/views/graph/composables/useMetrics.ts（度量数据管理 composable）

禁动清单（与本次无关，禁止触碰）：
- src/main/java/.../application/graph/fusion/*（融合模块）
- src/main/java/.../application/query/*（智能问答模块）
- src/main/java/.../application/document/*（文档处理模块）
- src/main/java/.../infrastructure/neo4j/gds/GdsAdapter.java（GDS 适配器，本次不改其接口）
- frontend/src/views/qa/*（问答页面）
- frontend/src/views/file/*（文件管理页面）
- pom.xml（依赖变更需独立 change）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 前端 API 客户端 | `frontend/src/api/client.ts` | 沿用，新增 API 函数追加到 `graph.ts` |
| 前端状态管理 | Pinia `defineStore`（`graphStore.ts`） | 沿用，扩展现有 store |
| 前端下拉选择器 | `BaseSelect.vue`（`@/common/components/`） | 沿用，用于学科选择器 |
| 前端开关控件 | 无既有 Toggle/Switch 组件 | **新建简单 Toggle**（内联在 GraphToolbar 中，CSS only，不引入组件库） |
| 前端面板/侧边栏 | `NodeDetailPanel.vue`（slide 动画） | 参照其 Transition + 固定定位模式实现 MetricsPanel |
| G6 节点大小/颜色更新 | `g.updateNodeData()`（`useGraphInteraction.filterByTypes` 已用） | 沿用此 API 做度量映射更新 |
| 后端 Cypher 查询 | `Neo4jClient.query().bindAll().fetch().all()` | 沿用，新增学科图查询方法 |
| 后端 REST 响应 | `ApiResult<T>` + VO 模式 | 沿用，新增 VO 如需 |
| 后端指标计算 | `MetricsService` 接口 + `MetricsServiceImpl` | 扩展接口（新增 subject 参数） |
| 后端学科列表 | `QueryGraphRepository.findDistinctSubjects()` | 直接复用 |
| GDS 投影/算法 | `GdsAdapter` | **不改**，subject 过滤在 Service 层后置完成 |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：**沿用** Neo4jClient + Repository 模式（既有 ConstructionGraphRepository 是这风格）
- 错误处理：**沿用** BusinessException + GlobalExceptionHandler（后端）/ store.error + 条件渲染（前端）
- API 路由组织：**沿用** 既有 Controller URL 前缀（/api/v1/graph/construction、/api/v1/graph/metrics）
- 前端组件通信：**沿用** Pinia store + props/emits（不引入 provide/inject 或 event bus）
- 前端 composable：**沿用** useGraphInteraction 模式，新增 useMetrics（同目录、同风格）
- 度量可视化映射：**引入新模式** → 理由：项目首次在图谱中按数据驱动节点视觉属性，但复用 G6 updateNodeData API
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | 学科全景图 API 端点：`GET /api/v1/graph/construction/subject/{subjectName}` | A) 放在 ConstructionController B) 新建 SubjectController | 选 A。学科图与文档子图同属图谱构建模块的查询能力，放同一 Controller 减少类膨胀。URL 前缀 `/graph/construction/` 语义为「图谱构建产物的查询」 | 若后续学科相关端点超过 3 个，需拆分为独立 Controller（低风险） |
| D2 | 学科列表 API：`GET /api/v1/graph/subjects` | A) 放在 ConstructionController B) 新建独立端点 | 选 A 但 URL 不放在 `/construction/` 下。学科列表是图谱的元数据查询，`/api/v1/graph/subjects` 简短清晰 | 复用了 `QueryGraphRepository.findDistinctSubjects()`，零新增后端逻辑 |
| D3 | 指标学科过滤：**结果层后置过滤**（MetricsService 全图计算 → Controller 按 subject KP 列表过滤） | A) GDS 投影层过滤 B) 结果层过滤 | 选 B（见 ADR-033）。理由：① GdsAdapter 无需改动；② 不同学科共享同一个 GDS 缓存（全图计算一次，各学科过滤复用）；③ 单学科 ≤500 KP，过滤开销 O(n) 可忽略 | 全图 GDS 计算比学科级投影稍重，但被缓存分摊；学科数增长时优势更大（N 个学科 = 1 次 GDS 而非 N 次） |
| D4 | 节点大小映射：**线性映射** totalDegree → [20, 60]px | 对数映射 / 分位数映射 | 选线性。学科内 KP 度数差异通常不超过 1 个数量级（2~20），线性映射直观且易在图例中解释。若出现极端离群值（1 个节点度=50 其余≤5），用 95 百分位截断 | 度数分布极不均匀时小节点区分度不足，v2 可切换对数映射 |
| D5 | 节点颜色映射（PageRank ON）：**百分位分段暖色梯度**（0~20% 浅蓝 → 80~100% 深橙） | 连续色阶（d3-scale-chromatic）/ 固定分档 | 选分段（5 档）。理由：不引入 d3 依赖；5 档足以让用户区分"重要/中等/次要"；图例可标注每档范围 | 颜色过渡不够平滑，但对分析目的够用 |
| D6 | 前端视图模式切换：store 中 `viewMode: 'document' \| 'subject'`，两种模式**互斥** | URL 路由参数 / 组件内 local state | 选 Pinia store。理由：① 同页面内切换无需改 URL；② store 让 GraphToolbar/GraphCanvas/MetricsPanel 都能响应模式变化；③ 与现有 `currentDocId` 模式一致 | 刷新页面后模式丢失（回到默认文档模式），可接受 |
| D7 | PageRank 开关状态：**localStorage 持久化** | Pinia 不持久 / URL query param | 选 localStorage。理由：用户偏好应跨会话保持；实现简单（Toggle onChange 写 localStorage，store 初始化时读）。开关默认 OFF（首次使用） | 需处理 localStorage 不可用场景（隐私模式），降级为内存状态 |
| D8 | 学科图 Neo4j 查询策略：**单 Cypher 查询**返回 KP + PREREQUISITE_OF + CHILD_OF 子图 | 多次查询分别取 KP 列表再取边 | 选单查询。与现有 `findByDocumentId` 模式一致（一次 OPTIONAL MATCH 拿全子图）。通过 `DISTINCT` 去重 | 查询复杂度 O(KP数 × 平均度数)，需加 `LIMIT` 防护（≤1000 节点） |
| D9 | MetricsPanel 布局位置：**右侧滑出面板**（与 NodeDetailPanel 同侧，互斥显示） | 底部面板 / 可拖拽浮窗 | 选右侧滑出。理由：① 与 NodeDetailPanel 模式一致；② 排行面板占据 320px 宽，与详情面板相同宽度；③ 两个面板互斥——打开排行面板时若详情面板已打开则关闭详情面板 | 无法同时看排行和节点详情（可接受，点击排行行会打开详情面板） |

---

## 2. 数据流 / 架构图

### 2.1 学科全景图加载流程

```
用户选择学科
      │
      v
GraphVisualizePage
  handleSubjectSelect("数学")
      │
      ├──> graphStore.setViewMode('subject')
      ├──> graphStore.loadSubjectGraph("数学")
      │        │
      │        v
      │    GET /api/v1/graph/construction/subject/数学
      │        │
      │        v
      │    ConstructionController.getSubjectGraph("数学")
      │        │
      │        v
      │    ConstructionService.getSubjectGraph("数学")
      │        │
      │        v
      │    ConstructionGraphRepository.findBySubject("数学")
      │        │  Cypher:
      │        │  MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name:"数学"})
      │        │  OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp)
      │        │  OPTIONAL MATCH (kp)-[:CHILD_OF]->(cat:KnowledgeCategory)
      │        │  ...
      │        │  RETURN DISTINCT nodes + edges
      │        │
      │        v
      │    GraphSubgraphVO → store.currentGraph
      │
      ├──> graphAdapter.toGraphData(store.currentGraph) → G6 GraphData
      │        │
      │        v
      │    GraphCanvas 渲染
      │
      └──> useMetrics.loadDegreeMetrics("数学")
               │
               v
           GET /api/v1/graph/metrics/degree?nodeTypes=KnowledgePoint&subject=数学
               │
               v
           MetricsController.getDegree(..., subject="数学")
               │
               v
           MetricsService.queryDegree({KnowledgePoint}, {PREREQUISITE_OF})
               │  (全图 GDS 计算，命中/未命中 Caffeine 缓存)
               v
           Post-filter: 只保留 KP ∈ subjectKpIds 的结果
               │  (subjectKpIds 来自 Neo4j 快速查询)
               v
           List<MetricResultVO> → useMetrics.degreeData
               │
               v
           graphAdapter.applyMetrics(graphData, degreeData, pagerankData?)
               │  (更新 G6GraphNode.data.size, data.color)
               v
           g.updateNodeData(...) → g.draw()  （增量更新，不重建图）
```

### 2.2 PageRank 开关切换流程

```
用户点击 PageRank Toggle (OFF → ON)
      │
      v
GraphToolbar @toggle-pagerank
      │
      v
useMetrics.togglePageRank()
      │
      ├── localStorage.setItem('graphviz.pagerank', 'true')
      │
      ├── loadPageRank("数学")
      │     │
      │     v
      │   GET /api/v1/graph/metrics/pagerank?nodeTypes=KnowledgePoint&subject=数学
      │     │  (同 D3 后置过滤策略)
      │     v
      │   pagerankData
      │
      └── graphAdapter.applyPageRankColors(graphData, pagerankData)
            │
            v
          g.updateNodeData(...) → g.draw()

用户点击 Toggle (ON → OFF)
      │
      v
useMetrics.togglePageRank()
      │
      ├── localStorage.setItem('graphviz.pagerank', 'false')
      │
      └── graphAdapter.resetNodeColors(graphData)  → 恢复 NODE_COLORS 默认色
            │
            v
          g.updateNodeData(...) → g.draw()
          （不重新请求任何 API，度中心性数据不变）
```

### 2.3 视图模式互斥状态机

```
                    ┌──────────────┐
       页面加载      │              │  选文档
    ──────────────> │  document    │ <─────────────┐
                    │  (默认模式)   │               │
                    │              │ ──选学科────>  │
                    └──────────────┘               │
                           ^                       │
                           │                       v
                    ┌──────────────────────────────────┐
                    │              │                   │
                    │  subject     │ <──选另一学科────  │
                    │              │ ──选文档────────> │
                    └──────────────────────────────────┘

状态变量：store.viewMode ∈ { 'document', 'subject' }
触发规则：
  - 选学科 → viewMode='subject', selectedDocId=null, 清空文档选择器
  - 选文档 → viewMode='document', selectedSubject=null, 清空学科选择器
  - 两种模式不可同时激活
```

### 2.4 前端组件树

```
GraphVisualizePage
├── GraphToolbar
│   ├── BaseSelect (学科选择器 · 新增)
│   ├── BaseInput (节点搜索 · 已有)
│   ├── Toggle (PageRank 开关 · 新增)
│   └── Button "度量排行" (新增)
├── BaseSelect (文档选择器 · 已有 · subject 模式下隐藏)
├── .graph-body
│   ├── .graph-canvas-area
│   │   └── GraphCanvas (已有 · 度量映射后节点 size/color 动态)
│   └── GraphLegend (已有 · 新增度量映射图例说明)
├── NodeDetailPanel (已有 · 新增度量指标区)
└── MetricsPanel (新增 · 右侧滑出 · 与 NodeDetailPanel 互斥)
```

---

## 3. 关键接口签名

> 伪代码级别，不含完整实现。

### 3.1 后端新增/变更接口

```java
// ConstructionController — 新增 2 个端点

// 学科列表
@GetMapping("/api/v1/graph/subjects")
ApiResult<List<String>> getSubjects();

// 学科全景图
@GetMapping("/api/v1/graph/construction/subject/{subjectName}")
ApiResult<GraphSubgraphVO> getSubjectGraph(@PathVariable String subjectName);

// MetricsController — 扩展已有端点，新增可选参数

@GetMapping("/api/v1/graph/metrics/pagerank")
ApiResult<List<MetricResultVO>> getPageRank(
    @RequestParam List<String> nodeTypes,
    @RequestParam(required = false) List<String> edgeTypes,
    @RequestParam(required = false) String subject   // 新增
);

@GetMapping("/api/v1/graph/metrics/degree")
ApiResult<List<MetricResultVO>> getDegree(
    @RequestParam List<String> nodeTypes,
    @RequestParam(required = false) List<String> edgeTypes,
    @RequestParam(required = false) String subject   // 新增
);
```

```java
// MetricsService — 接口新增方法（不破坏现有签名）

// 现有方法保留（向后兼容）
List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes);
List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes);

// 新增：带 subject 过滤的方法
List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName);
List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName);
```

```java
// ConstructionGraphRepository — 新增方法

/**
 * 按学科查询知识图谱子图（KP + PREREQUISITE_OF + CHILD_OF + KnowledgeCategory）。
 * 返回 GraphSubgraphBO。
 */
GraphSubgraphBO findBySubject(String subjectName);
```

### 3.2 前端新增/变更接口

```typescript
// api/graph.ts — 新增

/** 学科列表 */
export function listSubjects(): Promise<string[]>

/** 学科全景图 */
export function getSubjectGraph(subjectName: string): Promise<GraphSubgraphVO>

/** 查询度中心性（扩展 subject 参数） */
export function queryDegree(
  nodeTypes?: string[],
  edgeTypes?: string[],
  subject?: string    // 新增
): Promise<MetricResultVO[]>

/** 查询 PageRank（扩展 subject 参数） */
export function queryPageRank(
  nodeTypes?: string[],
  edgeTypes?: string[],
  subject?: string    // 新增
): Promise<MetricResultVO[]>
```

```typescript
// composables/useMetrics.ts — 新增

export function useMetrics() {
  const degreeData = ref<MetricResultVO[]>([])
  const pagerankData = ref<MetricResultVO[]>([])
  const pagerankEnabled = ref(false)     // 从 localStorage 初始化
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function loadDegreeMetrics(subject: string): Promise<void>
  async function loadPageRankMetrics(subject: string): Promise<void>
  function togglePageRank(): void         // 切换开关 + localStorage
  function getNodeDegree(nodeId: string): { inDegree: number, outDegree: number, totalDegree: number }
  function getNodePageRank(nodeId: string): number | null

  return { degreeData, pagerankData, pagerankEnabled, loading, error,
           loadDegreeMetrics, loadPageRankMetrics, togglePageRank,
           getNodeDegree, getNodePageRank }
}
```

```typescript
// graphAdapter.ts — 新增

/**
 * 将度量数据应用到 G6 GraphData，更新节点 size 和 color。
 * - size: 基于 totalDegree 线性映射到 [MIN_METRIC_SIZE, MAX_METRIC_SIZE]
 * - color: 若 pagerankData 非空，按 PageRank 百分位映射暖色梯度；否则使用默认 NODE_COLORS
 */
export function applyMetrics(
  graphData: G6GraphData,
  degreeData: MetricResultVO[],
  pagerankData?: MetricResultVO[]
): G6GraphData
```

---

## 4. ADR 索引

| ADR | 标题 | 关键决策 |
|---|---|---|
| ADR-032 | [学科全景图 API 设计](.specs/adr/032-subject-graph-api.md) | 端点路径、查询策略、返回格式复用 |
| ADR-033 | [指标学科过滤策略](.specs/adr/033-metrics-subject-filter.md) | 结果层后置过滤 vs GDS 投影过滤，缓存策略 |

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **学科全景图节点数过大**（某学科 KP > 500），G6 WebGL 渲染性能下降或浏览器卡顿 | 用户体验差，页面无响应 | 低（初高中单学科 KP 预估 50~300） | 后端 Cypher 加 `LIMIT 1000`；前端超过 500 节点时 console.warn 提示；v2 可做虚拟化/聚合节点 |
| R2 | **GDS 全图计算耗时过长**（全图 KP 数 > 2000 时 PageRank 迭代可能 > 5s），首次请求超时 | 度量数据加载失败触发 AC-10 降级 | 中（取决于已导入数据量） | ① Caffeine 缓存分摊重复请求；② 前端 10s 超时 + 降级提示；③ yml 可配 GDS maxIterations；④ 未来可改为学科级投影（变 D3 决策） |
| R3 | **BELONGS_TO_SUBJECT 边覆盖率不足**，部分 KP 未关联 Subject，学科全景图缺失节点 | 用户认为数据不完整 | 中（依赖 graph-construction-refactor 的 Subject 节点化质量） | AC-1 验证时需检查覆盖率；若覆盖率 < 90%，需先修数据迁移脚本，本 change 不解决数据质量问题 |
| R4 | **localStorage 在浏览器隐私模式不可用**，PageRank 开关偏好无法持久化 | 用户每次打开页面需重新开启开关（轻微不便） | 低 | try-catch 读写 localStorage，失败时降级为内存状态（本次会话有效），不阻塞功能 |
| R5 | **前端状态复杂度增长**：store 同时管理文档图/学科图/度量数据/视图模式，可能引入状态不一致 bug | 视图错乱（如文档选择器在学科模式下显示） | 中 | 视图模式互斥由 store action 保证原子性（setViewMode 同时清空对端选择器）；vue-tsc + AC 手动验证覆盖切换场景 |
| R6 | **长期债务**：`MetricsService` 接口重载（新增 subject 参数的方法）导致接口膨胀 | 每次加过滤维度都需新方法 | 低 | 当前仅 1 个新维度；若未来过滤维度 ≥ 3，重构为 `MetricsQuery` 对象参数（已有 `MetricsQuery` record） |

---

## 6. 不在范围

- 不解决 BELONGS_TO_SUBJECT 边缺失的数据质量问题（依赖上游 change 保证）
- 不做 GDS 学科级投影优化（当前全图计算 + 后置过滤足够，性能问题由 R2 监控）
- 不引入新 npm 包（如 d3-scale 用于色阶）——v1 用硬编码色阶常量
- 不做节点聚合/折叠（度数相近的节点合并显示）
- 不做学科选择器的搜索/过滤（下拉列表直接展示所有学科，预期 ≤ 20 个）
- 不改变 `GdsAdapter` 公开接口（保持其职责单一：接收 nodeTypes/edgeTypes → 返回计算结果）

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `frontend/src/views/graph/composables/useMetrics.ts` | 度量数据加载 + 缓存 + 视觉映射状态管理 | 学科全景图度量展示 | 若后续在其他页面（如全图浏览）使用度量，可直接复用此 composable |
| `frontend/src/views/graph/components/MetricsPanel.vue` | 通用度量排行面板（列表 + 排序 + 点击联动） | 知识点度量排行 | 组件设计为数据驱动（接收 `MetricResultVO[]`），可在其他场景复用 |

### 9.2 新增/改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| GDS 指标 subject 过滤策略 | 全图计算 + Service 层后置过滤 | 所有调用 MetricsService 的模块 | 低——改为投影层过滤只需修改 MetricsServiceImpl 内部实现，不改变接口签名 |
| 前端度量映射算法 | 节点大小 = 线性映射(总度数)；节点颜色 = 分档暖色梯度(PageRank) | 图谱可视化页面 | 低——`applyMetrics` 是纯函数，替换映射算法不影响其他模块 |

### 9.3 新增/修改的跨模块契约

```
- 新增 GET /api/v1/graph/subjects                 → 返回 List<String> 学科名数组
- 新增 GET /api/v1/graph/construction/subject/{name} → 返回 GraphSubgraphVO（复用现有结构）
- 扩展 GET /api/v1/graph/metrics/{pagerank,degree}  → 新增可选参数 subject: String，不传行为不变
```

### 9.4 新增/升级的依赖

无。本次不引入新依赖。

### 9.5 禁动清单变化

```
- 新增禁动：frontend/src/views/graph/composables/useMetrics.ts 的视觉映射常量（色阶、大小范围）集中管理，
  不允许在 GraphCanvas.vue 或 graphAdapter.ts 中硬编码重复的映射值
- 新增禁动：后端 GdsAdapter.java 接口本次及后续不应膨胀——subject 过滤在上层完成
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。