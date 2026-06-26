# ADR-034 · 诊断子图渲染方案选择：纯 SVG

- **日期**: 2026-06-22
- **Change**: `diagnosis-subgraph-viz`
- **状态**: accepted

---

## Context

学情诊断结果页需要在 LLM 文本报告下方内嵌展示剪枝子图（≤ 30 节点）。子图需支持：
- 节点按 MASTERS weight 着色（四档色阶）和缩放（线性映射）
- 点击节点弹出详情面板（考试历史 + 趋势图）
- 图例说明颜色→掌握度映射规则
- 只读，无需缩放/拖拽/邻域展开等交互

项目已有 AntV G6 v5.1.1 用于全屏图编辑页（`GraphVisualizePage.vue`），通过 `GraphCanvas.vue` 管理 Graph 实例生命周期。

## Decision

诊断子图使用**纯 SVG**（`<svg>` + 手动 `<circle>`/`<line>`/`<text>` + 简单力导向布局）。不使用 G6 v5。

数据转换层复用 `graphAdapter.ts` 的 `SubgraphResponse` 类型，但渲染层完全独立于 G6。

## Consequences

### 正面

1. **组件轻量**：无 Graph 实例生命周期（create → render → setData → draw → destroy），组件更简单，bug 面更小
2. **自然嵌入文档流**：`<svg>` 是普通 HTML 元素，可参与 Flex/Grid 布局，不需要 `position: absolute` + 手动 resize 管理
3. **样式完全可控**：SVG 的 `fill`/`r`/`stroke` 直接绑定 Vue 响应式数据，无 G6 data-driven style 的配置层抽象
4. **零额外打包成本**：G6 v5 仍保留给全屏图编辑页，不会因为诊断子图的需求而锁定 G6 版本或配置

### 负面

1. **需自行实现布局算法**：力导向模拟约需 50 行代码。对于 ≤30 节点的星型+树型混合结构，收敛极快（≤50 次迭代）。缓解：提供 fallback 分层布局
2. **不支持 G6 内置交互**（缩放/拖拽/鱼眼放大/小地图）：当前 v1 不需要这些交互。若 v2 需要，迁移路径清晰：数据格式已兼容 `G6GraphData`，届时替换渲染层即可
3. **两个图渲染路径并存**：项目中将有 SVG 图（诊断子图）和 G6 图（全屏图编辑）两套渲染方式。未来若出现第三种图场景，需评估是否统一

### 迁移路径（如需切换到 G6）

若 v2 开启诊断子图的缩放/拖拽/搜索等交互需求：
1. 将 `DiagnosisSubgraph.vue` 的 `<svg>` 模板替换为 `<div ref="container">`（G6 挂载点）
2. 引入 G6 Graph 实例创建逻辑（可复用 `GraphCanvas.vue` 的模式）
3. `transformPruningSubgraph()` 返回的 `G6GraphData` 已是 G6 兼容格式，直接传入 `graph.setData()` / `graph.draw()`
4. 颜色/大小映射从 SVG 属性绑定改为 G6 node style 函数