# ADR-050: 运营仪表盘图表库选型 — Apache ECharts

- **日期**: 2026-06-23
- **状态**: accepted
- **来源**: `ops-analytics` DESIGN

---

## Context

运营仪表盘需要三种图表类型：饼图（操作类型占比）、柱状图（文档状态分布、图谱节点/边分布）、折线图（上传趋势、历史趋势）。项目当前前端无统计图表库：AntV G6 是图可视化引擎（网络节点-边图），不适用于统计图表。

可选方案：
1. **Apache ECharts 5.x + vue-echarts 7.x**
2. **@antv/g2**（与既有 G6 v5 同一厂牌，AntV 家族）
3. **Chart.js 4.x**（轻量级，gzip ~70KB）

## Decision

选择 **Apache ECharts 5.x + vue-echarts 7.x**。

理由：
- 社区最成熟：GitHub 60k+ stars，文档和示例最丰富，遇到问题资料最多
- Vue 3 集成完善：`vue-echarts` 是官方推荐的 Vue 3 封装，支持 Composition API + 响应式 `setOption`
- 图表类型全覆盖：内置 pie/bar/line 三种所需类型，交互（tooltip/legend/zoom/dataZoom）开箱即用，无需额外插件
- 按需引入：ECharts 5 支持 tree-shaking，仅引入 pie/bar/line 三种图表类型，实际包体积可控

备选排除理由：
- **@antv/g2**：社区规模（GitHub 12k stars）远小于 ECharts，文档和生态差距明显；虽然与 G6 同一厂牌，但统计图表和图可视化是不同的渲染模型，同厂牌没有实质收益（无共享代码、无统一主题系统）
- **Chart.js**：轻量但交互能力弱（无内置 zoom/dataZoom、tooltip 定制能力有限），不适合运营仪表盘的交互需求（如折线图缩放查看细节、点击图例切换数据系列）

## Consequences

- **正面**：图表功能强大，交互丰富，满足 REQUIREMENT AC-3/4/5/10 全部图表需求；Vite code-splitting 将 ECharts chunk 独立，不影响其他页面加载
- **负面**：包体积大于 Chart.js（ECharts 按需引入 ~200KB gzip vs Chart.js ~70KB）；与 G6 不同厂牌，需手动统一配色方案（在 ECharts 主题中定义与 G6 一致的色板）
- **约束**：图表组件封装在 `frontend/src/views/ops/components/` 中，不抽象为通用组件库级图表组件（v1 仅运营仪表盘使用）；若未来其他页面需要图表，再提取到 `frontend/src/common/components/charts/`