# CHANGE: 知识图谱可视化重写

- **Change ID**: `graph-viz-refactor`
- **创建日期**: 2026-06-21
- **路径建议**: 中等（`REQUIREMENT (增量) → DESIGN (增量) → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

前端知识图谱可视化界面使用 Cytoscape.js 渲染白屏，无法正常使用。且当前实现与原 DESIGN.md 的技术选型（AntV G6 v5）存在偏差 — Cytoscape.js 为 Canvas 渲染，不具备 WebGL 能力，在全量图谱场景下存在性能天花板。同时当前交互仅支持基础的缩放拖拽，缺少节点搜索、类型筛选、路径高亮、节点展开/折叠等知识图谱探索必需的交互模式。

## What（做什么）

1. **替换图可视化库**：Cytoscape.js → AntV G6 v5（WebGL 渲染，与 DESIGN.md 原选型对齐）
2. **重写 GraphCanvas 组件**：正确管理 G6 Graph 实例生命周期，消除白屏问题
3. **升级交互体验**：节点搜索/定位、类型筛选/图例、悬停 tooltip、点击展开邻域、路径高亮
4. **双数据源适配**：统一适配「文档子图」（`GraphSubgraphVO`，轻量节点无 properties）和「剪枝子图」（`SubgraphResponse`，节点含完整 properties Map + 边含 weight）两种后端数据结构
5. **全量图谱视图**：新增融合后全量图谱浏览能力（非仅单文档子图）

## 视觉调性（前端项目必填，由 0-change 步骤 0.6 预选填入）

- **选定**：2️⃣ 极简（Minimal）— 继承自 `frontend-ui` CHANGE，详见 `@.specs/frontend-ui/UI-DESIGN.md`
- **理由**：本次为现有设计系统内的组件重写，不改变全局视觉调性。图谱画布本身遵循 cold-blue 单色锚点 + 节点类型颜色编码（已有 `NODE_COLORS` / `EDGE_COLORS` 映射），与极简工具面板定位一致
- **参考产品**：Linear、Vercel、Stripe（产品端）— 同 frontend-ui
- **明确排除**：不适用（继承现有设计，不重新选型）

> 此选择会被 `2a-ui-design.md` 继承，2a 阶段不再重选调性。

## 影响面

- [x] 影响 `REQUIREMENT.md`（增量：新增图谱交互相关 AC — 搜索、筛选、展开、全量图等）
- [x] 影响 `DESIGN.md` / 引入新 ADR（修正图可视化库选型：Cytoscape.js → G6 v5；新增交互模式设计）
- [x] 影响现有 AC（R-VIZ-001 图谱可视化 AC 需改写为 G6 渲染 + 新交互要求）
- [ ] 影响数据模型 / 迁移
- [ ] 影响外部 API 兼容性（纯前端变更，后端 API 不变）
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- 图谱编辑/创建（节点/边的增删改，V1 只读）
- 图谱数据导出（PNG/SVG/JSON 导出）
- 图谱布局算法自定义选择（V1 使用 G6 默认布局，后续可加布局切换）
- 图谱时间轴/历史版本回放
- 非图谱页面的任何修改（文档管理、智能问答、融合管理、指标看板保持不变）
- 移动端触摸交互（桌面优先）
- 图谱的深色模式（跟随全局 V1 决策，不做 dark mode）

## 验收线（粗粒度，不是 AC）

1. 管理员选择一个已抽取文档后，图谱画布**稳定渲染**（不再白屏），节点和边正确显示，颜色按类型编码
2. 管理员能在图谱中**搜索节点**、**按类型筛选**、**点击节点查看详情**、**高亮关联路径**
3. 全量融合图谱可正常加载和浏览，不因节点数量导致页面卡死

## 风险与未知

- **G6 v5 API 稳定性**：v5 相对较新，API 可能在 minor 版本中有 breaking change。锁版本策略
- **全量图规模未知**：目前不确定融合后的全量图谱节点/边数量上限。若超过 5000 节点需验证 WebGL 渲染性能，若超过 10000 节点可能需考虑节点聚合/虚拟化策略
- **与 Naive UI 的样式冲突**：G6 自带少量默认样式，需确保与全局 Tailwind + design tokens 不冲突
- **graphAdapter.ts 的重写**：当前适配层仅输出 Cytoscape elements 格式，需重写为 G6 data 格式，同时支持两种输入数据结构

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。
