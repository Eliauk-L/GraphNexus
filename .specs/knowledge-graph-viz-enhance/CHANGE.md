# CHANGE: 知识点图谱可视化增强 — 学科全景 + 度量排行

- **Change ID**: `knowledge-graph-viz-enhance`
- **创建日期**: 2026-06-22
- **路径建议**: 完整
- **状态**: draft

---

## Why（为什么做）

当前图谱可视化页面（`/knowledge-graph`）只能按「单个文档」加载子图，缺乏两个关键能力：

1. **学科全景视图缺失**：用户无法一次性查看某个学科下所有知识点的完整依赖关系图。每次只能选一个文档，看不到跨文档的知识点关联网络。
2. **图谱度量缺失**：后端已有 PageRank + 度中心性 API（`graph-metrics` change 交付），但前端图谱页面未接入。用户无法从图结构中识别哪些知识点是「枢纽节点」（被大量前置依赖引用、处于学科核心位置）。

这两个能力是「知识点可视化」页面的自然延伸——用户进入该页面的核心诉求是理解知识结构，而学科全景 + 重要性排行是理解结构的基础手段。

## What（做什么）

在现有 `/knowledge-graph` 页面增加：

1. **学科级知识图谱视图**：页面顶部新增学科选择器，切换学科后加载该学科下所有知识点的聚合图（跨文档 KP + PREREQUISITE_OF 依赖链 + 关联 KnowledgeCategory），替代当前仅支持单文档子图的限制。
2. **图谱度量可视化**：调用后端已有 PageRank + 度中心性 API，在图谱中按度量值映射节点大小/颜色（视觉化），同时在侧边新增可切换的度量排行面板（Top N 列表），帮助用户快速定位核心知识点。

## 视觉调性（继承既有锁定）

- **选定**：极简（Minimal）— 参考 Linear/Vercel/Stripe
- **理由**：项目已锁定，本次为同一页面的功能增强，继承现有设计语言
- **参考产品**：Linear、Vercel、Stripe
- **明确排除**：Material Design（过于厚重）、Neumorphism（与现有风格冲突）

> 来自 `frontend-ui` CHANGE，本次不重新选型。

## 影响面

- [x] 影响 `REQUIREMENT.md`（新增功能需求 + AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR（后端新增学科图 API + 指标 API 扩展学科过滤参数）
- [ ] 影响现有 AC（不修改现有文档子图 AC，纯新增）
- [ ] 影响数据模型 / 迁移（无 DDL 变更，复用现有 Neo4j 节点/边+SubjectNode）
- [x] 影响外部 API 兼容性（新增端点，不修改现有端点签名）
- [ ] 仅修复 bug，无范围变化

### 具体影响范围

**前端（主要变更）**：
- `GraphVisualizePage.vue` — 新增学科选择器 + 视图模式切换（文档/学科）
- `graphStore.ts` — 新增学科列表加载、学科图加载、度量数据加载
- `GraphCanvas.vue` — 节点大小/颜色按度量值动态映射
- `NodeDetailPanel.vue` — 节点详情中展示度量值（PageRank、入度、出度）
- 新组件 `MetricsPanel.vue` — 度量排行面板（可折叠侧边/底部面板）
- `api/graph.ts` — 新增 API 调用（学科列表、学科图、学科过滤指标）
- `api/types.ts` — 新增类型（如 `SubjectVO`）

**后端（增量变更）**：
- 新增 `GET /api/v1/graph/construction/subject/{subjectName}` — 返回学科级知识点聚合图
- `ConstructionGraphRepository` — 新增学科图查询方法（按 BELONGS_TO_SUBJECT 过滤 KP + PREREQUISITE_OF 边 + CHILD_OF 分类树）
- `MetricsController` / `MetricsService` — 新增可选 `subject` 查询参数，按学科过滤指标结果
- 新增 DTO/VO（`SubjectGraphVO` 等）

**不涉及**：
- 数据库 DDL 变更
- Neo4j 节点/边结构变更
- 融合/抽取/成绩模块
- 智能问答模块

## 范围排除（这次不做）

- **不做学科对比视图**：不支持同时展示两个学科的图谱进行对比，用户一次只看一个学科
- **不做全图指标对比**：度量排行仅限当前选中学科内，不展示跨学科排名（除非用户手动切换学科）
- **不做指标历史趋势**：不记录/展示度量值随时间的变化曲线
- **不做自定义度量算法**：只使用后端已有 PageRank + 度中心性，不引入 Betweenness Centrality / Closeness Centrality 等新算法
- **不改变文档子图模式**：保留现有"选择文档查看子图"的功能，学科全景作为新增视图模式与之并存
- **不做移动端适配**：延续 V1 桌面端 ≥1280px 分辨率约束

## 验收线（粗粒度，不是 AC）

1. 用户在 `/knowledge-graph` 页面可通过学科选择器切换到某学科，看到该学科下所有知识点及其前置依赖关系的聚合图
2. 图谱中节点大小/颜色反映其重要性（PageRank 或度中心性），用户可切换显示度量排行面板查看「重要知识点 Top N」排序表
3. 用户仍可通过文档选择器查看单文档子图，两种视图模式（学科全景 / 文档子图）可自由切换

## 风险与未知

- **性能风险**：学科级全量 KP 图可能包含数百节点+边，G6 v5 WebGL 渲染和 GDS 指标计算的性能需要在 DESIGN 阶段评估。若某学科 KP 超过 500，可能需要分页或虚拟化策略
- **指标 API 扩展未知**：当前 `MetricsService` 接口仅接受 `nodeTypes` + `edgeTypes`，不支持按学科过滤。DESIGN 阶段需确定方案——是在 GDS 投影时按 Subject 节点过滤（推荐），还是全图计算后按 KP 列表筛选（简单但浪费）
- **学科图数据来源**：当前无现成的「按学科查所有 KP 及 PREREQUISITE_OF 边」的 Neo4j 查询，需新增。需确认 BELONGS_TO_SUBJECT 边的覆盖率（是否所有 KP 都有该边）

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。