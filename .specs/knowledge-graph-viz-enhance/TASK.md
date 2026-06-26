# TASK: 知识点图谱可视化增强 — 学科全景 + 度量排行

- **Change ID**: `knowledge-graph-viz-enhance`
- **关联**: `@.specs/knowledge-graph-viz-enhance/REQUIREMENT.md`、`@.specs/knowledge-graph-viz-enhance/DESIGN.md`、`@.specs/knowledge-graph-viz-enhance/UI-DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel):  T01[P] T02[P] T03[P]     ← 后端 API 基础
Wave 2 (parallel):  T04[P] T05[P] T06[P]      ← 前端数据层 (depends on Wave 1)
                     T07[P] T08[P]
Wave 3 (parallel):  T09[P] T10[P] T11[P]      ← 前端 UI 组件 (depends on Wave 2)
Wave 4:             T12                        ← 页面集成 + 全量验证 (depends on Wave 3)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

### Wave 1 — 后端 API 基础

```xml
<task id="T01" parallel="true" status="pending">
  <name>ConstructionGraphRepository — 学科图查询 + KP ID 查询</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/QueryGraphRepository.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/SimpleGraphNode.java
    src/main/java/com/graphnexus/application/graph/construction/model/GraphSubgraphBO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
  </write_files>
  <action>
    在 ConstructionGraphRepository 中新增两个方法，沿用现有 Neo4jClient.query().bindAll().fetch().all() 模式：

    1. findBySubject(String subjectName): 按学科查询全景子图。
       Cypher 参照 ADR-032 D3：
       MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name: $name})
       OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp)
       OPTIONAL MATCH (kp)-[:CHILD_OF]->(cat:KnowledgeCategory)
       OPTIONAL MATCH (cat)-[:CHILD_OF]->(parentCat)
       → 收集 DISTINCT 节点，LIMIT 1000，返回 List&lt;GraphNode&gt;
       参照现有 findByDocumentId 的节点提取模式（SimpleGraphNode + labels() + 属性提取）。

    2. findEdgesBySubject(String subjectName): 返回学科图的边列表（PREREQUISITE_OF + CHILD_OF）。
       参照现有 findEdgesByDocumentId 的模式。

    3. findKpIdsBySubject(String subjectName): 轻量查询，仅返回指定学科下所有 KP 的 id 集合（Set&lt;String&gt;）。
       用于 MetricsService 后置过滤（ADR-033）。

    所有方法异常时记 WARN 日志并返回空集合（与现有 findDistinctSubjects 风格一致）。
  </action>
  <verify>
    # 启动应用后 curl 验证学科图端点（T02 完成后联调）：
    curl -s 'http://localhost:8080/api/v1/graph/construction/subject/数学' | jq '.data.nodes | length'
    # 预期返回 &gt;=0 的节点数（即使为 0 也不报错）
  </verify>
  <done>ConstructionGraphRepository 新增 3 个方法，按学科查询子图节点+边+KP ID 集合</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>ConstructionService + ConstructionController — 学科列表 + 学科全景图端点</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java
    src/main/java/com/graphnexus/api/graph/dto/construction/GraphSubgraphVO.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/QueryGraphRepository.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java
  </write_files>
  <action>
    新增 2 个 REST 端点（见 DESIGN D1, D2, ADR-032）：

    1. ConstructionService 接口新增方法签名：
       - List&lt;String&gt; listSubjects(): 委托 QueryGraphRepository.findDistinctSubjects()
       - GraphSubgraphBO getSubjectGraph(String subjectName): 委托 ConstructionGraphRepository.findBySubject() + findEdgesBySubject()

    2. ConstructionServiceImpl 实现上述方法。
       getSubjectGraph 组装 GraphSubgraphBO（nodes + edges），模式参照现有 getSubgraph(documentId)。

    3. ConstructionController 新增端点：
       - GET /api/v1/graph/subjects → ApiResult&lt;List&lt;String&gt;&gt;
       - GET /api/v1/graph/construction/subject/{subjectName} → ApiResult&lt;GraphSubgraphVO&gt;
         复用 GraphSubgraphVO.from(bo)，不改 VO 结构。

    标注 Swagger @Operation，风格与现有端点一致。
  </action>
  <verify>
    # 学科列表
    curl -s 'http://localhost:8080/api/v1/graph/subjects' | jq '.data'
    # 预期返回字符串数组，如 ["数学","物理"]

    # 学科全景图（需 Neo4j 中已有数据）
    curl -s 'http://localhost:8080/api/v1/graph/construction/subject/数学' | jq '.data.nodes | length'
  </verify>
  <done>2 个新端点可用，学科列表返回字符串数组，学科全景图返回 GraphSubgraphVO</done>
  <depends_on>T01</depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>MetricsService + MetricsController — subject 参数过滤</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/metrics/service/MetricsService.java
    src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/metrics/service/MetricsService.java
    src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
  </write_files>
  <action>
    按 ADR-033 实现结果层后置过滤：

    1. MetricsService 接口新增重载方法（保留原有方法不变，向后兼容）：
       - queryPageRank(nodeTypes, edgeTypes, subjectName)
       - queryDegree(nodeTypes, edgeTypes, subjectName)

    2. MetricsServiceImpl 实现：
       - subjectName 为 null/blank → 直接委托原有方法
       - subjectName 非空 → 先调用原有方法获取全图结果（命中 Caffeine 缓存），
         再通过 ConstructionGraphRepository.findKpIdsBySubject 获取 subject KP ID 集合，
         过滤结果：保留 nodeId ∈ subjectKpIds 的条目
       - 缓存 key 不含 subjectName（全图结果共享缓存）

    3. MetricsController 两个端点各新增可选 @RequestParam(required=false) String subject。
       传给 MetricsService 的新重载方法。
       Swagger 文档标注"可选：按学科过滤，仅返回 BELONGS_TO_SUBJECT 指向该学科的节点"
  </action>
  <verify>
    # 无 subject 参数 → 行为不变（向后兼容）
    curl -s 'http://localhost:8080/api/v1/graph/metrics/degree?nodeTypes=KnowledgePoint' | jq '.data | length'

    # 有 subject 参数 → 仅返回该学科 KP
    curl -s 'http://localhost:8080/api/v1/graph/metrics/degree?nodeTypes=KnowledgePoint&subject=数学' | jq '.data[] | select(.nodeType=="KnowledgePoint")'
  </verify>
  <done>指标 API 支持可选 subject 参数，不传行为不变；传入后仅返回该学科节点的指标结果</done>
  <depends_on>T01</depends_on>
</task>
```

### Wave 2 — 前端数据层

```xml
<task id="T04" parallel="true" status="pending">
  <name>api/graph.ts + api/types.ts — 新增 API 函数与类型</name>
  <read_files>
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
    frontend/src/api/client.ts
  </read_files>
  <write_files>
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
  </write_files>
  <action>
    1. api/types.ts 无需新增类型（复用现有 MetricResultVO、GraphSubgraphVO），确认为空操作。

    2. api/graph.ts 新增 3 个 API 函数（沿用手动 params 序列化模式）：
       - listSubjects(): Promise&lt;string[]&gt; → GET /api/v1/graph/subjects
       - getSubjectGraph(subjectName): Promise&lt;GraphSubgraphVO&gt; → GET /api/v1/graph/construction/subject/{name}
       - 修改 queryDegree / queryPageRank 签名：新增可选 subject?: string 参数，
         传了则追加到 query params

    风格与现有 getDocumentSubgraph / queryPageRank 一致。
  </action>
  <verify>
    # vue-tsc 类型检查
    cd frontend && npx vue-tsc --noEmit src/api/graph.ts
  </verify>
  <done>前端 API 层可调用学科列表、学科全景图、带 subject 过滤的指标接口</done>
  <depends_on>T02, T03</depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>constants.ts — 新增度量映射常量</name>
  <read_files>
    frontend/src/views/graph/constants.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/constants.ts
  </write_files>
  <action>
    在 constants.ts 中新增度量映射相关常量（见 DESIGN D4, D5, UI-DESIGN §3）：

    1. 节点大小映射参数：
       - METRIC_SIZE_MIN = 20（最小半径 px）
       - METRIC_SIZE_MAX = 60（最大半径 px）
       - METRIC_SIZE_DEFAULT = 40（无度量数据时的默认半径，与现有 NODE_SIZES.KnowledgePoint 一致）

    2. PageRank 色阶（5 档，OKLCH，见 UI-DESIGN §3）：
       export const PAGERANK_COLORS: string[] = [
         'oklch(0.55 0.18 250)',   // 0~20%  冷蓝（接近默认 KP 色）
         'oklch(0.65 0.10 180)',   // 20~40% 浅暖
         'oklch(0.60 0.15 120)',   // 40~60% 中暖
         'oklch(0.55 0.18 70)',    // 60~80% 暖橙
         'oklch(0.50 0.22 50)',    // 80~100% 深橙
       ]

    3. 百分位计算工具函数：percentileIndex(value, allValues, bucketCount) → 0~4 索引

    所有新增常量标注 JSDoc 注释。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/graph/constants.ts
  </verify>
  <done>度量映射参数和色阶常量就绪，可被 graphAdapter 和 MetricsPanel 引用</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>graphAdapter.ts — 新增 applyMetrics 函数</name>
  <read_files>
    frontend/src/views/graph/graphAdapter.ts
    frontend/src/views/graph/constants.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/graphAdapter.ts
  </write_files>
  <action>
    在 graphAdapter.ts 中新增导出函数 applyMetrics（见 DESIGN §3.2, D4, D5）：

    function applyMetrics(
      graphData: G6GraphData,
      degreeData: MetricResultVO[],
      pagerankData?: MetricResultVO[]
    ): G6GraphData

    逻辑：
    1. 构建 degreeMap: nodeId → { inDegree, outDegree, totalDegree }
       从 degreeData 中聚合（同一 nodeId 可能有 inDegree + outDegree 两条记录）。
    2. 若 pagerankData 非空，构建 pagerankMap: nodeId → score。
    3. 遍历 graphData.nodes：
       - 若节点类型为 KnowledgePoint 且有 degree 数据：
         - size = linearMap(totalDegree, minDegree, maxDegree, METRIC_SIZE_MIN, METRIC_SIZE_MAX)
         - 若度数为 0 或只有一个不同值，全部用 METRIC_SIZE_DEFAULT
       - 颜色：
         - 若 pagerankData 非空且有该节点数据：
           color = PAGERANK_COLORS[percentileIndex(score, allScores, 5)]
         - 否则保持现有 data.color（默认 NODE_COLORS）
    4. 返回新的 G6GraphData（不修改原对象）。

    linearMap 为纯函数：value → [outMin, outMax]，用 95 百分位截断防离群值（D4）。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/graph/graphAdapter.ts
  </verify>
  <done>applyMetrics 函数可用，接受度量数据返回更新了 size/color 的 G6GraphData</done>
  <depends_on>T05</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>useMetrics.ts — 新建度量数据管理 composable</name>
  <read_files>
    frontend/src/views/graph/composables/useGraphInteraction.ts
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/composables/useMetrics.ts
  </write_files>
  <action>
    新建 useMetrics composable（见 DESIGN §3.2），参照 useGraphInteraction 的同目录、同风格：

    export function useMetrics() {
      // 状态
      const degreeData = ref<MetricResultVO[]>([])
      const pagerankData = ref<MetricResultVO[]>([])
      const pagerankEnabled = ref(false)   // 从 localStorage 'graphviz.pagerank' 初始化
      const loading = ref(false)
      const error = ref<string | null>(null)

      // 方法
      async function loadDegreeMetrics(subject: string): Promise<void>
        → 调用 queryDegree(['KnowledgePoint'], undefined, subject)
        → 存入 degreeData
        → 异常时设 error 不抛（AC-10 降级）

      async function loadPageRankMetrics(subject: string): Promise<void>
        → 调用 queryPageRank(['KnowledgePoint'], undefined, subject)
        → 存入 pagerankData

      function togglePageRank(): void
        → 翻转 pagerankEnabled
        → try { localStorage.setItem('graphviz.pagerank', String(enabled)) } catch {}
        → 若变为 ON 且 pagerankData 为空 → 自动触发 loadPageRankMetrics

      function getNodeDegree(nodeId): { inDegree, outDegree, totalDegree }
      function getNodePageRank(nodeId): number | null
    }

    localStorage 读写用 try-catch 包裹，隐私模式降级为内存状态（DESIGN D7, R4）。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/graph/composables/useMetrics.ts
  </verify>
  <done>useMetrics composable 可用，管理度量数据加载/缓存/开关状态</done>
  <depends_on>T04</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>graphStore.ts — 扩展学科图/度量/视图模式状态</name>
  <read_files>
    frontend/src/views/graph/graphStore.ts
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/graphStore.ts
  </write_files>
  <action>
    扩展 graphStore（Pinia defineStore），新增（见 DESIGN D6）：

    // 新增状态
    subjects: string[]                          // 学科列表
    currentSubject: string | null               // 当前选中学科
    viewMode: 'document' | 'subject'            // 视图模式，默认 'document'

    // 新增方法
    async loadSubjects(): Promise<void>
      → 调用 listSubjects()，存入 subjects

    async loadSubjectGraph(subjectName: string): Promise<void>
      → loading=true, error=null
      → 调用 getSubjectGraph(subjectName)
      → currentGraph = 结果
      → 异常时 error = e.userTip
      → finally loading=false

    setViewMode(mode: 'document' | 'subject'): void
      → viewMode = mode
      → 切换时清空对端选择器：
        mode='subject' → currentDocId=null
        mode='document' → currentSubject=null, currentGraph=null

    // 现有方法不变（loadDocuments, loadDocumentSubgraph, clearGraph）
    // loadDocumentSubgraph 内部自动 setViewMode('document')
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/graph/graphStore.ts
  </verify>
  <done>graphStore 支持学科列表加载、学科图加载、视图模式互斥切换</done>
  <depends_on>T04</depends_on>
</task>
```

### Wave 3 — 前端 UI 组件

```xml
<task id="T09" parallel="true" status="pending">
  <name>GraphToolbar.vue — 新增学科选择器 + PageRank Toggle + 排行按钮</name>
  <read_files>
    frontend/src/views/graph/components/GraphToolbar.vue
    frontend/src/common/components/BaseSelect.vue
    frontend/src/views/graph/graphStore.ts
    frontend/src/views/graph/composables/useMetrics.ts
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/graph/components/GraphToolbar.vue
  </write_files>
  <action>
    扩展 GraphToolbar，新增 3 个控件（见 UI-DESIGN §6.1, §6.2, §6.6）：

    1. 学科选择器（BaseSelect）：
       - 位置：搜索框左侧
       - props: options=store.subjects.map(s=>({label:s,value:s})), placeholder="选择学科"
       - @update:model-value → emit('select-subject', value)
       - 宽度 200px

    2. PageRank Toggle（纯 CSS，见 UI-DESIGN §6.2）：
       - 位置：搜索框右侧
       - 30×18px 轨道 + 14×14px 滑块
       - 绑定 useMetrics().pagerankEnabled
       - @click → useMetrics().togglePageRank()
       - 轨道色：OFF=var(--color-border), ON=var(--color-brand)
       - 过渡：滑块 translateX(0→12px)，轨道 background，var(--duration-fast) var(--ease-out)
       - 外置标签 "PageRank"，0.875rem，var(--color-text-secondary)

    3. 度量排行按钮（见 UI-DESIGN §6.1）：
       - 图标 BarChart3（@lucide/vue）
       - at rest: 透明底+1px border
       - hover: border→品牌蓝, translateY(-1px)
       - @click → emit('toggle-metrics-panel')

    新增 emits: 'select-subject', 'toggle-metrics-panel'
    所有控件使用 tokens.css 变量，不硬编码颜色。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/graph/components/GraphToolbar.vue
  </verify>
  <done>GraphToolbar 含学科选择器、PageRank Toggle、度量排行按钮，vue-tsc 通过</done>
  <depends_on>T07, T08</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>MetricsPanel.vue — 新建度量排行面板组件</name>
  <read_files>
    frontend/src/views/graph/components/NodeDetailPanel.vue
    frontend/src/views/graph/composables/useMetrics.ts
    frontend/src/views/graph/constants.ts
    frontend/src/api/types.ts
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/graph/components/MetricsPanel.vue
  </write_files>
  <action>
    新建 MetricsPanel.vue（见 UI-DESIGN §6.3, DESIGN D9）：

    Props:
      - visible: boolean
      - degreeData: MetricResultVO[]
      - pagerankData: MetricResultVO[]
      - pagerankEnabled: boolean
    Emits: 'close', 'select-node' (nodeId: string)

    结构：
    - 右侧固定定位 320px 宽，全高，var(--color-surface) 背景，var(--shadow-card-lifted)
    - Header: h3 "度量排行" + ✕ 关闭按钮（与 NodeDetailPanel 同款 close 按钮）
    - Divider (1px border)
    - 排序下拉：复用 BaseSelect（NSelect），选项：总度数↓/入度↓/出度↓/PageRank↓（后者仅 pagerankEnabled 时出现）
    - 表格：Top 20 行
      - 表头: # │ 知识点 │ 入度 │ 出度 │ 总度 │ PR（v-if="pagerankEnabled"）
      - 数据行: label token 表头，mono 数值右对齐
      - 行 hover: var(--color-brand-veil), cursor pointer
      - 点击行 → emit('select-node', nodeId)
    - 空状态（degreeData 为空）: "该学科暂无度量数据"
    - 错误状态: "度量数据暂不可用"

    Slide 动画：复用 NodeDetailPanel 的 .slide-enter-active/.slide-leave-active
    (transform: translateX(100%), 300ms cubic-bezier(0.16, 1, 0.3, 1))

    本地排序状态：sortField ('totalDegree'|'inDegree'|'outDegree'|'pagerank') + sortOrder ('asc'|'desc')
    用 computed 派生排序后的列表。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/graph/components/MetricsPanel.vue
  </verify>
  <done>MetricsPanel 组件就绪，排行表可排序、可点击联动、有空/错误态、slide 动画</done>
  <depends_on>T05, T07</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>NodeDetailPanel + GraphLegend — 度量指标区 + 度量图例扩展</name>
  <read_files>
    frontend/src/views/graph/components/NodeDetailPanel.vue
    frontend/src/views/graph/components/GraphLegend.vue
    frontend/src/views/graph/composables/useMetrics.ts
    frontend/src/views/graph/constants.ts
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/graph/components/NodeDetailPanel.vue
    frontend/src/views/graph/components/GraphLegend.vue
  </write_files>
  <action>
    1. NodeDetailPanel 扩展（见 UI-DESIGN §6.4）：
       - 新 Prop: metrics?: { inDegree: number, outDegree: number, totalDegree: number, pagerank?: number }
       - 在 detail-actions 之前新增"度量指标"区域（divider 分隔）：
         - 标题 "度量指标"，label token
         - 入度 / 出度 / 总度（始终显示），mono 数值
         - PageRank（v-if="metrics.pagerank != null"），4 位小数
       - 若 metrics prop 为空/undefined，整个度量区域不渲染
       - 必须先用 shouldShowProperty 判断——度量值 0 是合法值，不能因为 falsy 被隐藏

    2. GraphLegend 扩展（见 UI-DESIGN §6.5）：
       - 在现有图例底部新增 divider + 度量映射说明区
       - "节点大小 = 总度数"（始终显示）
       - "节点颜色 = PageRank 百分位"（仅 pagerankEnabled prop 为 true 时显示）
       - 色阶渐变色条：120×12px，CSS linear-gradient 用 PAGERANK_COLORS 数组
       - 新 Props: pagerankEnabled?: boolean
       - 样式：supporting token (0.75rem, var(--color-text-secondary))

    两个组件均使用 tokens.css 变量，不硬编码。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/graph/components/NodeDetailPanel.vue src/views/graph/components/GraphLegend.vue
  </verify>
  <done>NodeDetailPanel 可显示度量值，GraphLegend 可显示度量映射图例</done>
  <depends_on>T05, T07</depends_on>
</task>
```

### Wave 4 — 页面集成 + 全量验证

```xml
<task id="T12" parallel="false" status="pending">
  <name>GraphVisualizePage — 集成学科全景图 + 度量 + 视图模式切换</name>
  <read_files>
    frontend/src/views/graph/GraphVisualizePage.vue
    frontend/src/views/graph/graphStore.ts
    frontend/src/views/graph/graphAdapter.ts
    frontend/src/views/graph/composables/useMetrics.ts
    frontend/src/views/graph/composables/useGraphInteraction.ts
    frontend/src/views/graph/components/GraphToolbar.vue
    frontend/src/views/graph/components/GraphCanvas.vue
    frontend/src/views/graph/components/NodeDetailPanel.vue
    frontend/src/views/graph/components/GraphLegend.vue
    frontend/src/views/graph/components/MetricsPanel.vue
    frontend/src/common/components/BaseSelect.vue
  </read_files>
  <write_files>
    frontend/src/views/graph/GraphVisualizePage.vue
  </write_files>
  <action>
    这是集成任务——在 GraphVisualizePage.vue 中把所有新组件和状态串联起来。

    1. 初始化：
       - onMounted 中增加 store.loadSubjects()
       - 初始化 useMetrics()

    2. 学科选择（handleSubjectSelect）：
       - store.setViewMode('subject')
       - store.loadSubjectGraph(subjectName)
       - useMetrics().loadDegreeMetrics(subjectName)
       - (PageRank 若已开启 → 自动触发 loadPageRankMetrics)
       - watch store.currentGraph → refreshGraphData()

    3. 度量数据 → 图谱更新（watch）：
       - watch([useMetrics().degreeData, useMetrics().pagerankEnabled], () => {
           if (store.viewMode === 'subject' && graphData.value) {
             const updated = applyMetrics(graphData.value,
               useMetrics().degreeData.value,
               useMetrics().pagerankEnabled.value ? useMetrics().pagerankData.value : undefined)
             // 通过 G6 g.updateNodeData 增量更新节点 size/color
             const g = canvasRef.value?.getGraph?.()
             if (g) {
               g.updateNodeData(updated.nodes.map(n => ({
                 id: n.id,
                 data: { size: n.data.size, color: n.data.color }
               })))
               g.draw()
             }
           }
         })

    4. 视图模式互斥：
       - 学科选择器 visible: store.viewMode === 'subject'（也可始终显示）
       - 文档选择器 visible: store.viewMode === 'document'
       - 选学科 → 清文档选择器；选文档 → 清学科选择器

    5. MetricsPanel 集成：
       - visible 状态：新增 local ref showMetricsPanel
       - @toggle-metrics-panel → showMetricsPanel = !showMetricsPanel，
         同时若 NodeDetailPanel 打开则关闭（互斥，UI-DESIGN §6.3）
       - 传递 degreeData / pagerankData / pagerankEnabled
       - @select-node → handleSelectNode（复用搜索选中逻辑）

    6. NodeDetailPanel 集成：
       - 新增 computed：从 useMetrics 获取当前选中节点的度量值
       - 传递 metrics prop

    7. GraphLegend 集成：
       - 传递 pagerankEnabled prop

    PageRank 开关 ON → watch 自动触发 loadPageRankMetrics + applyMetrics 颜色更新。
    PageRank 开关 OFF → watch 触发 applyMetrics(undefined pagerank) → 恢复默认色。
  </action>
  <verify>
    # 类型检查
    cd frontend && npx vue-tsc --noEmit

    # 手动 UAT（AC-1~AC-11）
    # 1. 访问 /knowledge-graph → 学科选择器显示学科列表
    # 2. 选择学科 → 图谱渲染学科全景图
    # 3. 节点大小反映总度数（枢纽节点更大）
    # 4. 开启 PageRank → 节点颜色变化 + 排行面板显示 PR 列
    # 5. 打开排行面板 → Top 20 列表正确排序
    # 6. 点击排行行 → 图谱节点高亮聚焦
    # 7. 点击图谱节点 → 详情面板显示度量值
    # 8. 选择文档 → 切换回文档子图模式
    # 9. 空学科 → 空状态提示
    # 10. 关闭 PageRank → 颜色恢复默认，PR 列隐藏
  </verify>
  <done>全部 AC 可手动验证通过，vue-tsc 0 错误，两种视图模式自由切换，度量映射+排行面板正常工作</done>
  <depends_on>T09, T10, T11</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中
- `status="done"` — 已完成
- `status="blocked"` — 阻塞

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

```xml
<!-- 占位 -->
```

---

## AC 覆盖矩阵

| AC | 覆盖任务 |
|---|---|
| AC-1 · 学科选择器加载全景图 | T01, T02, T08, T09, T12 |
| AC-2 · 文档/学科模式互斥切换 | T08, T12 |
| AC-3 · 度量 API 学科过滤 | T03 |
| AC-4 · 度量视觉映射 | T05, T06, T12 |
| AC-5 · 度量排行面板 | T10, T12 |
| AC-6 · 节点详情度量值 | T11, T12 |
| AC-7 · 学科列表 API | T02 |
| AC-8 · 学科全景图 API | T01, T02 |
| AC-9 · 空学科处理 | T10, T12 |
| AC-10 · 度量降级处理 | T07, T10 |
| AC-11 · PageRank 开关 | T07, T09, T12 |