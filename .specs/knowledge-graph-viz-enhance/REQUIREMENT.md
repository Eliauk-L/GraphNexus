# REQUIREMENT: 知识点图谱可视化增强 — 学科全景 + 度量排行

- **Change ID**: `knowledge-graph-viz-enhance`
- **关联**: `@.specs/knowledge-graph-viz-enhance/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为教师/管理员，我想在知识点可视化页面按学科筛选并查看该学科下所有知识点的聚合图，以便理解整个学科的知识结构和依赖关系。
- **US-2**：作为教师/管理员，我想在学科全景图中看到每个知识点的度量值（度中心性必显、PageRank 可选），以便快速识别哪些知识点是学科中的「枢纽节点」——被大量前置依赖引用、处于核心位置。
- **US-3**：作为教师/管理员，我想在学科全景图和文档子图之间自由切换，以便既能查看单文档的局部知识结构，又能把握整个学科的全貌。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 学科选择器加载学科全景图

- **Given** 用户进入 `/knowledge-graph` 页面，后端 Neo4j 中存在 `Subject` 节点（如"数学""物理"）且有 `KnowledgePoint` 通过 `BELONGS_TO_SUBJECT` 边关联到这些 Subject
- **When** 用户从新增的学科下拉选择器中选中一个学科（如"数学"）
- **Then** 图谱画布展示该学科下所有 `KnowledgePoint` 节点（通过 `BELONGS_TO_SUBJECT` 边筛选），节点间的 `PREREQUISITE_OF` 边完整渲染，关联的 `KnowledgeCategory` 节点及 `CHILD_OF` 边同时展示；文档选择器自动清空（两种视图模式互斥）
- **验证方式**: 手动 UAT — 访问 `/knowledge-graph`，点击学科选择器选择"数学"，确认画布渲染包含该学科所有 KP + 前置依赖边 + 分类节点，不包含其他学科的 KP

### AC-2 · 文档子图模式保留且可互斥切换

- **Given** 用户当前正在查看某学科全景图（或某文档子图）
- **When** 用户点击文档选择器选中文档（或点击学科选择器选中学科）
- **Then** 图谱切换为文档子图模式，学科选择器清空（或学科全景图模式，文档选择器清空），两种模式互斥，不可同时选中
- **验证方式**: 手动 UAT — 在学科全景图模式下选文档 → 确认切换到文档子图且学科选择器清空；反之亦然，来回切换 3 次无异常

### AC-3 · 度量数据查询（后端 API 扩展）

- **Given** 后端运行中，Neo4j 中"数学"学科下有 ≥5 个 KnowledgePoint 节点
- **When** 前端调用 `GET /api/v1/graph/metrics/degree?nodeTypes=KnowledgePoint&subject=数学`
- **Then** 返回仅包含 `BELONGS_TO_SUBJECT` 指向"数学"的 KnowledgePoint 的度中心性数据（inDegree + outDegree），不包含其他学科的节点
- **验证方式**: `curl -s 'http://localhost:8080/api/v1/graph/metrics/degree?nodeTypes=KnowledgePoint&subject=数学' | jq '.data[] | select(.nodeId)'` 确认所有返回节点均属于"数学"学科

### AC-4 · 度量视觉映射（节点大小 + 颜色）

- **Given** 学科全景图已加载（≥10 个 KP 节点），度量数据已拉取（度中心性必取；PageRank 仅在用户开启 PageRank 开关时拉取）
- **When** 图谱渲染完成
- **Then** KnowledgePoint 节点的半径与其总度数（inDegree + outDegree）正相关（最小度数映射到最小半径，最大度数映射到最大半径，线性或对数映射），此为**始终生效**的映射。节点颜色行为取决于 PageRank 开关状态：
  - **开关 ON**：节点颜色按 PageRank 百分位映射（低 PageRank = 冷色 / 高 PageRank = 暖色）
  - **开关 OFF**：所有 KnowledgePoint 节点使用统一的默认主题色，不按 PageRank 区分
- **验证方式**: 手动 UAT — ① 默认状态（开关 OFF）：枢纽节点仅通过大小区分，颜色统一；② 开启开关：枢纽节点同时更大且颜色更暖；③ 关闭开关：颜色恢复统一

### AC-5 · 度量排行面板

- **Given** 学科全景图已加载且度量数据已就绪
- **When** 用户点击工具栏中的"度量排行"按钮
- **Then** 页面侧边（或底部）滑出排行面板，展示按总度数降序排列的知识点列表（Top 20），每行必含：排名、知识点名称、入度、出度、总度数；PageRank 列**仅当用户开启 PageRank 开关时显示**。支持点击表头切换排序字段（入度/出度/PageRank 升/降序）；点击某行 → 图谱中对应节点高亮 + 聚焦
- **验证方式**: 手动 UAT — ① 开关 OFF 时打开排行面板 → PageRank 列不可见；② 开启开关 → PageRank 列出现且可点击排序；③ 点击行确认图谱联动高亮

### AC-6 · 节点详情面板显示度量值

- **Given** 学科全景图已加载且度量数据已就绪
- **When** 用户点击图谱中某个 KnowledgePoint 节点
- **Then** 右侧滑出的 `NodeDetailPanel` 在现有属性列表下方新增"度量指标"区域，始终展示入度（整数）、出度（整数）；PageRank（4 位小数）**仅当用户开启 PageRank 开关时显示**
- **验证方式**: 手动 UAT — ① 开关 OFF 时点击 KP 节点 → 详情面板度量区只含入度+出度，无 PageRank；② 开启开关 → 同一节点的详情面板出现 PageRank 行，数值与排行面板一致

### AC-7 · 学科列表 API

- **Given** 后端运行中，Neo4j 中存在 ≥1 个 Subject 节点
- **When** 前端调用 `GET /api/v1/graph/subjects`
- **Then** 返回 `["数学", "物理", "英语", ...]` 字符串数组，按名称排序，不含空值或重复
- **验证方式**: `curl -s 'http://localhost:8080/api/v1/graph/subjects' | jq '.data'` 返回按字母排序的学科名数组

### AC-8 · 学科全景图 API

- **Given** Neo4j 中"数学"学科下有 ≥3 个 KnowledgePoint 节点，节点间存在 PREREQUISITE_OF 边
- **When** 前端调用 `GET /api/v1/graph/construction/subject/数学`
- **Then** 返回 `GraphSubgraphVO` 格式的响应，`nodes` 包含：① 所有 `BELONGS_TO_SUBJECT → Subject{name:"数学"}` 的 KnowledgePoint 节点；② 这些 KP 之间的 PREREQUISITE_OF 边；③ 关联的 KnowledgeCategory 节点 + CHILD_OF 边。`properties` 中含 `name`、`description`、`subject` 字段
- **验证方式**: `curl -s 'http://localhost:8080/api/v1/graph/construction/subject/数学' | jq '.data.nodes | length'` 返回 ≥3，且所有 KnowledgePoint 节点 subject 为"数学"

### AC-9 · 空学科处理

- **Given** Neo4j 中存在 Subject"化学"但该学科下没有任何 KnowledgePoint 节点
- **When** 用户选择"化学"学科
- **Then** 图谱画布显示空状态提示"该学科暂无知识点图谱数据，请先上传并抽取相关文档"，不展示错误信息或空白画布
- **验证方式**: 手动 UAT — 选择一个无 KP 的学科 → 确认空状态提示文案正确，不显示红色错误

### AC-10 · 度量数据缺失处理

- **Given** 学科全景图已加载但 GDS 计算失败或超时（模拟关闭 Neo4j GDS）
- **When** 度量数据加载失败
- **Then** 图谱仍正常展示（使用默认节点大小/颜色），排行面板显示"度量数据暂不可用"提示，不影响图谱浏览和其他交互
- **验证方式**: 手动 UAT — 模拟后端指标 API 返回 500 → 确认图谱不崩溃，面板显示降级提示

### AC-11 · PageRank 显示开关

- **Given** 学科全景图已加载
- **When** 用户查看图谱工具栏
- **Then** 工具栏中存在 PageRank 开关控件（Toggle/Switch），默认关闭。开启时：① 前端额外请求 PageRank API（`GET /api/v1/graph/metrics/pagerank?nodeTypes=KnowledgePoint&subject=...`）；② 图谱节点颜色按 PageRank 百分位映射（AC-4 颜色规则）；③ 排行面板显示 PageRank 列（AC-5）；④ 节点详情面板显示 PageRank 值（AC-6）。关闭时：① 不请求 PageRank API（节省计算资源）；② 颜色恢复默认；③ PageRank 列/值隐藏。切换开关时，已加载的度中心性数据保持不变不重新请求
- **验证方式**: 手动 UAT — ① 进入学科全景图 → 确认开关默认 OFF，节点颜色统一；② 开启开关 → 确认节点颜色变化（枢纽节点变暖色），排行面板和详情面板出现 PageRank 数据；③ 关闭开关 → 颜色恢复统一，PageRank 数据隐藏；④ 开关切换过程中度中心性大小映射始终保留不变

---

## 范围切分

### v1（本次必做）

- 学科选择器下拉框（从 Neo4j Subject 节点动态加载）
- 学科全景图 API（`GET /api/v1/graph/construction/subject/{subjectName}`）
- 学科列表 API（`GET /api/v1/graph/subjects`）
- 学科全景图前端渲染（KP + PREREQUISITE_OF + CHILD_OF + KnowledgeCategory）
- 指标 API 扩展 `subject` 参数过滤
- 度量视觉映射（节点大小 ← 总度数始终生效；节点颜色 ← PageRank 百分位，由开关控制）
- PageRank 显示开关（Toggle，默认关闭，开启后请求 PageRank API + 颜色映射 + 排行/详情面板展示）
- 度量排行面板（Top 20，可排序，点击联动图谱高亮；PageRank 列按开关显隐）
- 节点详情面板扩展（显示 PageRank + 入度 + 出度）
- 文档/学科两种视图模式互斥切换
- 空状态（无 KP 学科）+ 度量降级处理

### v2（下一轮考虑，不本次）

- 自定义度量阈值高亮（如手动设置"度 > 10 的节点标红"）
- 导出度量排行 CSV/Excel
- 双学科对比视图（并排展示两个学科的图）
- 新增 Betweenness Centrality / Closeness Centrality 等 GDS 算法
- 学科/文档视图切换时的动画过渡
- 节点按度量值过滤显示（如"只显示 PageRank Top 30%"）

### out（永远不做）

- 实时推流式度量更新（WebSocket 推送），已有事件驱动缓存失效 + 手动刷新足够
- 用户自定义度量公式/加权
- 3D 图谱可视化
- 移动端/平板适配（延续现有桌面端约束）
- 度量历史趋势图/时间序列

---

## 非功能性需求

- **性能**:
  - 学科全景图 ≤300 节点 + ≤500 边时，从选择学科到图谱渲染完成 ≤ 3s
  - 度量排行面板排序/切页响应 ≤ 500ms
  - 学科选择器下拉列表加载 ≤ 1s
- **可访问性**:
  - 学科选择器、度量排行按钮、排行面板表头均支持键盘操作（Tab 导航 + Enter 激活）
  - 节点大小/颜色非唯一信息载体（排行面板提供同等的文字数值）
- **安全**:
  - 无新增安全风险，复用现有 Spring Security + JWT 鉴权
  - 前端 DOMPurify 净化如涉及新渲染内容保持一致
- **兼容性**:
  - 桌面端 ≥1280px（延续 V1 分辨率约束）
  - 浏览器：Chrome/Firefox/Edge 120+（WebGL 1.0 必须）
- **可观测性**:
  - DEBUG 级别日志：学科图查询耗时、度量 API 调用耗时、GDS 计算耗时
  - 前端 console.debug：视图模式切换、度量数据加载状态

## 依赖与假设

- **依赖**:
  - Neo4j GDS 5.x 插件已安装并可用（`graph-metrics` change 已确保）
  - `SubjectNode` 和 `BELONGS_TO_SUBJECT` 边已存在于 Neo4j 中（`graph-construction-refactor` change 已交付）
  - `KnowledgeCategoryNode` + `CHILD_OF` 边（`knowledge-graph-extraction` 已交付）
  - 前端 AntV G6 v5 + WebGL 渲染器（`graph-viz-refactor` 已交付）
  - 前端 `BaseSelect` 组件（`frontend-ui` 已交付）
  - 后端 `MetricsService` 接口 + `MetricsController`（`graph-metrics` 已交付）
  - 后端 `ConstructionGraphRepository`（`graph-construction-refactor` 已交付）
- **假设**:
  - 所有 KnowledgePoint 节点均已通过 `BELONGS_TO_SUBJECT` 边关联到 Subject 节点（覆盖率 100%）
  - 单学科 KP 数量在可预期的使用场景下 ≤ 500 个（典型初高中学科）
  - GDS 按学科过滤的性能可接受（学科级投影节点数远小于全图）
  - 用户浏览器支持 WebGL 1.0（Chrome/Firefox/Edge 120+ 均满足）

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。