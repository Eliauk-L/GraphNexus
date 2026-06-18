# ADR-002: AntV G6 v5 作图可视化

- **日期**: 2026-06-18
- **Change**: `frontend-ui`
- **状态**: accepted

---

## Context

GraphNexus 前端需要渲染知识图谱子图（节点-边图），包含两种数据来源：
1. 文档子图 `GET /api/v1/graph/document/{id}` → `GraphSubgraphVO`
2. 剪枝子图 `GET /api/v1/analysis/subgraph/{taskId}` → `SubgraphResponse`

需求：力导向布局、节点-边样式（按边类型颜色/线型区分）、缩放拖拽、节点悬浮属性浮窗。v1 子图规模通常 < 100 节点。

候选方案：
1. **AntV G6 v5** — 阿里 AntV 出品，专用图可视化库，Canvas 渲染
2. **Apache ECharts** — 通用图表库，图/网络渲染通过 `echarts-graph` 扩展实现
3. **Cytoscape.js** — 国际最流行的图可视化库，Canvas + WebGL 双渲染器

## Decision

**选择 AntV G6 v5**。

## Consequences

### 正面

- 专为关系图/网络图设计，节点-边样式定制、力导向布局、交互（缩放/拖拽/悬浮）开箱即用
- 中文文档与示例优秀，降低团队学习成本
- Vue 3 集成方便（直接操作 DOM container + ref）
- Canvas 渲染器在 < 200 节点规模下性能充足

### 负面

- v5 相对新（2024 发布），社区规模 < Cytoscape.js，遇阻塞性 bug 时可供参考的 issues 较少
- G6 绑定了其数据格式（`{nodes: [...], edges: [...]}`），与后端 API 返回结构不完全一致，需要适配层

### 缓解

- 通过 `src/features/graph/graphAdapter.ts` 适配层隔离 G6 依赖：输入 API 响应，输出 G6 GraphData
- 若 G6 v5 出现阻塞性 bug 无法解决，适配层可切换为 Cytoscape.js（仅重写 transform 函数 + 渲染组件，不影响其他模块）
- 大图场景（>500 节点）可升级到 G6 v5 的 WebGL 渲染器