# REQUIREMENT: 学情诊断页增加剪枝子图可视化 + 度量映射 + 多次考试趋势

- **Change ID**: `diagnosis-subgraph-viz`
- **关联**: `@.specs/diagnosis-subgraph-viz/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师，我想在 LLM 诊断报告下方看到以学生为中心的剪枝子图，以便直观理解学生的知识薄弱点及其前置依赖关系结构。
- **US-2**：作为教师，我想通过节点颜色和大小一眼定位最薄弱的知识点，以便优先关注最需要补救的领域。
- **US-3**：作为教师，我想点击知识点节点查看该生历次考试的得分率记录，以便判断薄弱点是持续存在还是偶发性下滑。
- **US-4**：作为教师，我想看到掌握度随考试时间的变化趋势折线图，以便判断学生是在进步还是退步。

## 验收准则（AC）

### AC-1 · 诊断完成后展示子图

- **Given** 用户已发起学情诊断且状态为 `completed`（有 answer + taskId）
- **When** 页面渲染诊断结果
- **Then** LLM 文本报告下方出现子图可视化区域，包含：
  - Student 节点（1 个，居中或左上角定位）
  - 薄弱 KnowledgePoint 节点（MASTERS weight < 0.6）+ 前置依赖 KnowledgePoint 节点
  - MASTERS 边（Student → KP，线上标注 weight 值或通过颜色隐式传达）
  - PREREQUISITE_OF 边（KP → KP）
  - 图例说明颜色→掌握度映射规则
- **验证方式**: 发起 `POST /api/v1/query/chat` 诊断请求，等待完成后检查页面是否在报告下方渲染子图

### AC-2 · 节点按掌握度着色和缩放

- **Given** 子图中有 ≥ 1 个带有 MASTERS weight 的 KnowledgePoint 节点
- **When** 节点在子图中渲染
- **Then** 节点填充色按 weight 值映射为暖色梯度：
  - weight < 0.4 → 红色（严重薄弱）
  - 0.4 ≤ weight < 0.6 → 橙色（需关注）
  - 0.6 ≤ weight < 0.8 → 黄色（一般）
  - weight ≥ 0.8 → 绿色（良好）
- **And** 节点半径与 weight 值正相关（weight 越大节点越大）
- **And** 无 MASTERS 边的节点（纯前置依赖 KP，未直接考查）使用中性灰色，默认大小
- **验证方式**: 检查不同掌握度的 KP 节点是否呈现不同颜色和大小，对比 weight 值验证映射正确

### AC-3 · 点击 KP 节点展示详情面板

- **Given** 子图已渲染
- **When** 用户点击（或触摸）子图中任意 KnowledgePoint 节点
- **Then** 侧边滑出详情面板，展示：
  - 知识点名称
  - 当前掌握度（weight 值，格式化为百分比或 0.00 小数）
  - 历次考试记录列表：每行 = 考试日期 + 得分率（如 `2025-01-15: 58%`）
  - 考试总次数
- **And** 面板右上角有关闭按钮
- **And** 点击其他节点时面板内容切换，不关闭再打开
- **验证方式**: 依次点击不同 KP 节点，检查面板内容是否切换正确；点击关闭按钮检查面板消失

### AC-4 · 考试趋势折线图

- **Given** KP 节点有 ≥ 2 次考试记录（MASTERS description JSON 中 `details` 数组长度 ≥ 2）
- **When** 详情面板渲染该 KP 节点
- **Then** 面板内考试记录列表上方显示一个微型折线图：
  - x 轴 = 考试日期（按时间顺序）
  - y 轴 = 得分率（0.0 ~ 1.0）
  - 数据点标注实际得分率数值
  - 图表为纯 SVG 实现，不依赖外部图表库
  - 尺寸紧凑（建议 280×140px），适配 320px 宽面板
- **验证方式**: 点击有多次考试记录的 KP 节点，检查折线图数据点与 MASTERS description 中的 details 一致

### AC-5 · 无考试历史时的降级展示

- **Given** KP 节点无 MASTERS description（降级路径，`mastersAvailable=false`）或 description 为 null/空
- **When** 详情面板渲染该 KP 节点
- **Then** 显示提示文本"暂无历次考试数据"
- **And** 不展示折线图
- **And** 掌握度显示为降级标注："原始得分率（融合数据不可用）"
- **验证方式**: 在未执行融合的环境下诊断，检查详情面板降级展示

### AC-6 · 诊断未完成时不展示子图

- **Given** 页面状态为 `idle`、`pending` 或 `processing`
- **When** 页面渲染
- **Then** 子图可视化区域不可见
- **验证方式**: 页面初始加载时检查无子图区域；诊断进行中检查无子图区域

### AC-7 · 子图数据加载失败时的降级

- **Given** 诊断已完成（有 taskId），但 `GET /api/v1/analysis/subgraph/{taskId}` 返回错误（4xx/5xx）
- **When** 前端尝试加载子图数据
- **Then** 子图区域显示降级提示"子图数据加载失败"，不影响 LLM 文本报告的正常展示
- **验证方式**: Mock 子图 API 返回 500，检查页面仍展示文本报告 + 子图区域降级提示

---

## 范围切分

### v1（本次必做）

- 上下分区布局：LLM 报告在上，子图可视化在下
- 节点按 MASTERS weight 着色（红→绿四档梯度）+ 大小映射
- 点击节点弹出详情面板（考试历史列表）
- 详情面板内嵌微型 SVG 折线图（≥2 次考试时）
- 无考试历史/融合不可用时的降级展示
- 子图加载失败降级展示
- MASTERS 边的 weight 标注
- 图例说明颜色→掌握度映射
- 后端 `StudentDiagnosisStrategy` 补齐 MASTERS description 和 KP weight 属性透传

### v2（下一轮考虑，不本次）

- 子图支持缩放/拖拽交互
- 节点搜索/筛选功能
- 子图导出为 PNG/SVG 图片
- PREREQUISITE_OF 边的依赖强度（strength）可视化（如线宽/虚线样式）
- 多学生对比子图（同时展示两个学生的薄弱点差异）

### out（永远不做）

- 在诊断子图中展示 Exam 节点（保持与现有剪枝策略一致，避免子图膨胀）
- 全图 PageRank/度中心性指标叠加（小规模子图中无统计意义）
- 移动端/平板端适配（V1 桌面端 ≥ 1280px）
- 子图实时编辑/手动调整布局

---

## 非功能性需求

- **性能**: 子图渲染（含 G6/SVG 初始化 + 数据转换）≤ 500ms；详情面板打开 ≤ 200ms；趋势图绘制 ≤ 100ms。首屏不阻塞 LLM 报告渲染（子图异步加载）
- **可访问性**: 颜色不是传达掌握度的唯一手段——节点大小 + 详情面板文字数值 + 图例标签共同确保色觉障碍用户可获取等同信息
- **安全**: 子图节点属性（name/description）来自 Neo4j，需经 XSS 过滤后渲染；DOMPurify 已在前端集成，复用既有净化管线
- **兼容性**: Chrome/Firefox/Edge 120+，桌面端 ≥ 1280px
- **可观测性**: 子图加载失败时 console.error 记录 taskId + 错误原因，便于通过 traceId 定位

## 依赖与假设

- **依赖 `GET /api/v1/analysis/subgraph/{taskId}`**：已有端点，返回 SubgraphResponse（nodes + edges + pruningMeta）。假设响应格式不变
- **依赖 MASTERS description JSON 格式**：假设 `TimeDecayStrategy` 生成的 JSON 结构为 `{"examCount":N, "lastExamDate":"...", "details":[{"examDate":"...", "scoreRate":0.X, "decayWeight":0.X}]}`，前端按此结构解析
- **依赖 AntV G6 v5**：已在 `graph-viz-refactor` 中集成，`graphAdapter.ts` 已有 `transformPruningSubgraph()` 转换函数。假设可复用或适配用于诊断子图
- **假设**：后端补齐 MASTERS description 和 KP weight 属性透传后，API 响应中的 `SubgraphNodeVO.properties` 将包含 `weight` 字段，`SubgraphEdgeVO` 的 description 将包含考试历史 JSON 字符串
- **假设**：每次诊断完成后子图数据已持久化在 `query_task.subgraph_json` 中，API 查询不触发重新剪枝

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。