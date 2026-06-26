# CHANGE: 学情诊断页增加剪枝子图可视化 + 度量映射 + 多次考试趋势

- **Change ID**: `diagnosis-subgraph-viz`
- **创建日期**: 2026-06-22
- **路径建议**: 完整（REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION）
- **状态**: draft

---

## Why（为什么做）

当前学情诊断页面（`IntelligentQAPage.vue`）仅展示 LLM 文本分析报告（Markdown 或 HTML+SVG），但剪枝子图的结构数据已通过 `GET /api/v1/analysis/subgraph/{taskId}` 返回却未被前端渲染。用户只能看到文字结论，看不到「学生 → 薄弱知识点 → 前置依赖」的图结构和掌握度量化信息。

此外，MASTERS 边的 `description` 字段中已存储了历次考试的得分率序列（JSON），但前端完全未利用——导致多次考试的趋势变化被埋没在文本中，无法形成直观的时间维度认知。

## What（做什么）

在学情诊断结果页下方增加一个图可视化区域，渲染剪枝子图（Student → 薄弱 KP → 前置依赖 KP），节点颜色/大小按 MASTERS 掌握度映射。点击节点弹出详情面板展示历次考试得分率 + 微型趋势折线图，呈现掌握度随时间的变化。

核心改动：
1. **前端**：`IntelligentQAPage.vue` 增加子图可视化区域（LLM 回答下方）
2. **前端**：新增 `DiagnosisSubgraph.vue` 组件，用 AntV G6 v5 渲染剪枝子图
3. **前端**：节点颜色按掌握度（weight 0→1）映射暖色梯度（红→黄→绿）；节点大小按 weight 线性映射
4. **前端**：新增 `ExamTrendChart.vue` 组件，微型 SVG 折线图展示历次考试掌握度变化
5. **后端**：`StudentDiagnosisStrategy.buildResult()` 补充 MASTERS 边的 `description` 字段传递（考试历史 JSON），补充 KP 节点的 `weight` 属性到 properties

## 视觉调性（前端项目）

- **选定**：极简（Minimal）— 已由 `frontend-ui` change 锁定
- **理由**：与项目整体 UI 调性一致，图可视化区保持低视觉噪音，用颜色梯度传达掌握度
- **参考产品**：Linear（信息密度控制）、Vercel（数据可视化克制）、Stripe（清晰层级）
- **明确排除**：无（已锁定）

> 此选择被 `2a-ui-design.md` 继承，2a 阶段不再重选。

## 影响面

- [x] 影响 `REQUIREMENT.md`（新增功能需求 + AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR（子图可视化布局方案、掌握度→颜色映射算法、趋势图数据流）
- [ ] 影响现有 AC（不修改已有功能，仅新增）
- [x] 影响数据模型（MASTERS description 字段需透传到前端，当前 buildResult 中 description 传 null）
- [ ] 影响外部 API 兼容性（不变更 API 契约，SubgraphResponse 已有 properties Map 可承载 weight + description）
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- 不在 `GraphVisualizePage.vue`（全屏图可视化页）中增加诊断子图入口 — 诊断子图专属于学情诊断页
- 不增加新的后端指标计算 API（PageRank/度中心性不适用于 ≤50 节点的小子图）
- 不支持子图导出/下载
- 不在子图中展示 Exam 节点（保持与现有剪枝策略一致：仅 Student + KnowledgePoint）
- 不修改 `StudentDiagnosisStrategy` 的剪枝逻辑本身（只补充数据传递）

## 验收线（粗粒度，不是 AC）

1. 诊断完成后，LLM 文本报告下方出现以学生为中心的剪枝子图（Student 节点 + 薄弱/前置 KP 节点 + MASTERS/PREREQUISITE_OF 边），节点颜色依掌握度从红（弱）到绿（强），节点越大掌握度越高
2. 点击子图中任意 KP 节点，侧边详情面板展示：知识点名称、当前掌握度、历次考试日期 + 得分率列表，以及一个微型折线图展示掌握度随时间变化趋势
3. 子图与 LLM 文本报告在同一页面上下排列，无需切换 Tab 即可对照阅读

## 风险与未知

- **G6 v5 在子图规模下的表现**：剪枝子图通常 ≤ 30 节点，G6 v5 的 WebGL 渲染对此规模过于笨重，可能考虑更轻量的 SVG 渲染方案（DESIGN 阶段评估）
- **MASTERS description JSON 解析**：`TimeDecayStrategy` 生成的 JSON 格式可能含 `examDate`/`scoreRate`/`decayWeight`，需确认所有 MASTERS 边 description 格式一致（含降级路径中无 description 的兜底）
- **诊断页已有 G6 依赖？**：当前 `IntelligentQAPage.vue` 不依赖 G6。需评估是复用 G6 还是用纯 SVG/CSS 实现小型子图渲染

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。