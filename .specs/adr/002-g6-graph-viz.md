# ADR-002: AntV G6 v5 作图可视化（修订版）

- **日期**: 2026-06-18（原版）/ 2026-06-21（修订）
- **Change**: `frontend-ui` → `graph-viz-refactor`
- **状态**: accepted（修订）

---

## Context

GraphNexus 前端需要渲染知识图谱，包含三种数据来源：
1. 文档子图 `GET /api/v1/graph/construction/document/{id}` → `GraphSubgraphVO`（节点无 properties，轻量）
2. 剪枝子图 `GET /api/v1/analysis/subgraph/{taskId}` → `SubgraphResponse`（节点含 properties Map + 边含 weight + PruningMeta）
3. 全量融合图谱 `GET /api/v1/graph/construction/full` → `FullGraphVO`（new，待后端新增）

需求：力导向布局、节点-边样式（按类型颜色/线型区分）、缩放拖拽、搜索定位、类型筛选、节点详情与邻域展开、路径高亮。子图通常 < 100 节点，但全量图谱可达 1000~5000 节点。

原设计选定 G6 v5 但实际实现偏离为 Cytoscape.js，导致白屏问题。本次修正实现偏差。

候选方案：
1. **AntV G6 v5（WebGL 默认）** — 阿里 AntV 出品，专用图可视化库
2. **Cytoscape.js** — 当前实际使用的库（Canvas only）
3. **Sigma.js v2 + Graphology** — WebGL 渲染，但交互需大量自研

## Decision

**坚持 AntV G6 v5，并纠正实现偏差**。

### 修订内容（2026-06-21）

1. **渲染器**：原 ADR 说 Canvas 渲染 → **修正为 WebGL 默认**（G6 v5 默认 `renderer: 'webgl'`），Canvas 为自动降级后备
2. **数据源扩展**：原仅文档子图 + 剪枝子图 → 新增全量融合图谱
3. **交互架构**：原无交互设计 → 新增 `useGraphInteraction` composable 抽离搜索/筛选/选中/展开/路径高亮
4. **实例生命周期**：原隐含 destroy+create 模式 → 明确 `setData()` + `render()` 增量更新，仅在节点数变化 > 5× 时走 destroy 重建

## Consequences

### 正面

- 专为关系图/网络图设计，节点-边样式定制、力导向布局开箱即用
- WebGL 渲染器支撑 5000 节点规模，满足全量图谱性能要求（AC-18 的 30fps）
- 内置交互插件减少自研量（tooltip、click、zoom、drag 等基础交互无需编码）
- 中文文档优秀，降低团队学习成本
- Vue 3 集成方便（直接操作 DOM container + ref，不依赖 React 生态）

### 负面

- v5 相对新（2024 发布），社区规模 < Cytoscape.js，遇阻塞性 bug 时可供参考的 issues 较少
- G6 绑定了其数据格式（`{nodes: [...], edges: [...]}`），与后端 API 返回结构不完全一致，需要适配层
- 包体积较大（~500KB gzip），需 lazy load 避免影响非图谱页首屏
- `setData()` + `render()` 增量更新模式需要开发团队养成新的心智模型（vs 销毁重建）

### 缓解

- 通过 `graphAdapter.ts` 适配层隔离 G6 依赖
- 若 G6 v5 出现阻塞性 bug 无法解决，适配层可切换渲染库（仅重写 transform 函数 + GraphCanvas 组件）
- 图谱页 lazy load（`defineAsyncComponent`），G6 仅在路由激活时加载
- 防御性编程：节点数变化 > 5× 时自动走 destroy+create 安全路径
