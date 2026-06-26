# ADR-036 · 诊断子图组件隔离策略

- **日期**: 2026-06-22
- **Change**: `diagnosis-subgraph-viz`
- **状态**: accepted

---

## Context

项目已有以下图可视化组件（`views/graph/`）：

| 组件 | 职责 |
|------|------|
| `GraphCanvas.vue` | 全屏 G6 v5 画布：Graph 实例生命周期、渲染、事件代理 |
| `NodeDetailPanel.vue` | 全屏图节点详情：通用属性列表 + 邻域展开按钮 |
| `GraphToolbar.vue` | 工具栏：搜索 + 筛选 |
| `GraphLegend.vue` | 图例：节点/边类型筛选 |

诊断子图 (`views/query/`) 需要的组件：

| 组件 | 职责 |
|------|------|
| `DiagnosisSubgraph.vue` | 内嵌 SVG 子图：渲染 + 图例 + 节点点击 |
| `DiagnosisNodeDetail.vue` | 诊断节点详情：考试历史列表 + 趋势图 |
| `ExamTrendChart.vue` | 微型折线图：考试日期 → 得分率 |

两组组件在以下维度上存在根本差异：
- **渲染引擎**：G6 WebGL/Canvas vs 纯 SVG
- **交互模式**：缩放/拖拽/邻域展开/路径高亮 vs 点击→详情面板
- **信息密度**：万级节点 vs ≤30 节点
- **页面位置**：全屏独立页 vs 内容页内嵌区域
- **详情内容**：通用图属性（name/description/type）vs 学情诊断特有数据（考试历史/掌握度/趋势）

## Decision

诊断子图使用**独立组件树**，与 `views/graph/` 下的全屏图组件完全隔离。

共享层仅限：
- `graphAdapter.ts` 的类型定义（`G6GraphData`/`G6GraphNode`/`G6GraphEdge` interface）
- `constants.ts` 的色值参考（可能参考 `NODE_COLORS` 中的 brand color token，但诊断子图的颜色由 weight 动态计算）
- `api/types.ts` 的 `SubgraphResponse` 及相关类型

不共享：
- 渲染组件（`GraphCanvas.vue`、`NodeDetailPanel.vue`）
- 状态管理（`graphStore.ts` vs `queryStore.ts`）
- 交互 composables（`useGraphInteraction.ts`）

## Consequences

### 正面

1. **两个场景独立演化**：全屏图编辑页升级 G6 版本/插件/交互不影响诊断子图；诊断子图增加趋势图/考试历史不影响全屏图
2. **SVG 方案不受 G6 约束**：ADR-034 的纯 SVG 决策不需要在全屏图的 G6 框架内"委曲求全"
3. **组件职责单一**：`DiagnosisNodeDetail.vue` 只关心考试历史和掌握度，不需要处理通用图属性的条件展示逻辑（`HIDDEN_KEYS`/`SHOW_KEYS` 白名单）
4. **代码体积**：诊断子图不引入 G6 到 query 域，保持 `IntelligentQAPage.vue` 的依赖面最小

### 负面

1. **动画模式重复**：`DiagnosisNodeDetail.vue` 和 `NodeDetailPanel.vue` 都有 slide Transition + fixed 定位 + 关闭按钮，存在约 15 行 CSS 重复。可接受——内容结构完全不同，强行抽取共享面板组件反而增加条件分支复杂度
2. **两个图渲染路径并存**：未来如果出现第三个图可视化场景，需要决定走 SVG 路径还是 G6 路径，或抽取共享抽象。当前两个场景差异足够大，各自独立是最优解
3. **未来统一成本**：若决定统一所有图可视化为 G6，诊断子图需要重写。但 ADR-034 已评估此迁移路径清晰（数据格式兼容）

### 边界条件

如果未来出现以下场景，考虑打破隔离：
- 第三个内嵌只读小图场景 → 将 SVG 图渲染逻辑抽为 `common/components/MiniGraph.vue`
- 诊断子图需要交互式探索 → 迁到 G6，复用 `GraphCanvas.vue` 模式
- 全屏图也需要节点级指标展示 → `NodeDetailPanel.vue` 和 `DiagnosisNodeDetail.vue` 合并