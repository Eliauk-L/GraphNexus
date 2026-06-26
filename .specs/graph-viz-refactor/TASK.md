# TASK: 知识图谱可视化重写

- **Change ID**: `graph-viz-refactor`
- **关联**: `@.specs/graph-viz-refactor/REQUIREMENT.md`、`@.specs/graph-viz-refactor/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P]
Wave 2 (parallel): T04[P], T05[P]
Wave 3 (parallel): T06, T07[P], T08[P], T09[P]
Wave 4:            T10               (depends on T06, T07, T08, T09)
Wave 5:            T11               (depends on T10)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>依赖更新：移除 cytoscape，新增 @antv/g6</name>
  <read_files>
    frontend/package.json
  </read_files>
  <write_files>
    frontend/package.json
    frontend/package-lock.json
  </write_files>
  <action>
    1. 从 package.json dependencies 中移除 "cytoscape"
    2. 新增 "@antv/g6": "^5.0.0"
    3. 执行 npm install 更新 package-lock.json
  </action>
  <verify>npm ls @antv/g6 2>&1 | grep -q "@antv/g6" && echo "OK: G6 installed" || echo "FAIL"</verify>
  <done>@antv/g6 ^5.0.0 安装成功，cytoscape 已移除，npm ls 无报错</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>抽取颜色/类型常量到 constants.ts</name>
  <read_files>
    frontend/src/views/graph/graphAdapter.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/constants.ts
    frontend/src/views/graph/graphAdapter.ts
  </write_files>
  <action>
    1. 新建 constants.ts，从 graphAdapter.ts 中迁移 NODE_COLORS、NODE_SIZES、EDGE_COLORS 三个常量
    2. 补充 EDGE_LINE_STYLES 常量（按 edgeType 映射 solid/dashed）
    3. 补充交互态样式常量：DIM_OPACITY=0.15、HIGHLIGHT_BORDER_WIDTH=3、PATH_EDGE_WIDTH=3 等
    4. 更新 graphAdapter.ts：从 constants.ts import 这些常量，移除内联定义
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/graph/constants.ts src/views/graph/graphAdapter.ts 2>&1 | grep -v "error" || echo "OK: TS compilation passes"</verify>
  <done>constants.ts 包含完整的颜色/尺寸/线型/交互态常量；graphAdapter.ts 从 constants.ts import</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>后端：新增全量图谱 API 端点</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java
    src/main/java/com/graphnexus/api/graph/dto/construction/GraphSubgraphVO.java
    src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java
    src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
  </write_files>
  <action>
    新增 GET /api/v1/graph/construction/full 端点，返回全量融合图谱（所有节点+边）。
    1. ConstructionService 接口新增 GraphSubgraphBO getFullGraph() 方法
    2. ConstructionServiceImpl 实现：调用 ConstructionGraphRepository 新增的 findAllNodesAndEdges()
    3. ConstructionGraphRepository 新增方法：Cypher MATCH (n) OPTIONAL MATCH (n)-[r]->(m) RETURN n, r, m
      注意排除 DELETING 状态的文档关联节点（WHERE n.documentId IS NULL OR ...）
    4. ConstructionController 新增 @GetMapping("/full")，复用 GraphSubgraphVO 作为响应格式
    5. 添加 Swagger 文档注解
  </action>
  <verify>curl -s http://localhost:8080/api/v1/graph/construction/full | jq '.code' | grep -q 200 && echo "OK: full graph API returns 200" || echo "FAIL"</verify>
  <done>GET /api/v1/graph/construction/full 返回 code=200，响应含 nodes[] 和 edges[]</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>重写 graphAdapter：Cytoscape 格式 → G6 GraphData 格式</name>
  <read_files>
    frontend/src/views/graph/constants.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/graphAdapter.ts
  </write_files>
  <action>
    将 graphAdapter.ts 从输出 Cytoscape ElementDefinition[] 重写为输出 G6 v5 GraphData 格式。
    见 DESIGN D4：统一入口函数 toGraphData(input, options?)。
    1. 导出 G6GraphData 接口：{ nodes: Array<{id, data:{label,nodeType,color,size,...}}>, edges: Array<{id,source,target,data:{type,color,width,lineStyle}}> }
    2. 内部子函数：transformDocumentSubgraph(vo: GraphSubgraphVO) → G6GraphData
    3. 内部子函数：transformPruningSubgraph(res: SubgraphResponse) → G6GraphData
    4. 统一入口 toGraphData() 按节点是否有 'properties' 字段判别数据类型分支
    5. 颜色/尺寸从 constants.ts import，不再硬编码
    6. 保留边过滤逻辑（source/target 必须都在 nodes 中）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/graph/graphAdapter.ts 2>&1 | tail -1</verify>
  <done>graphAdapter.ts 导出 toGraphData、G6GraphData；TypeScript 编译通过</done>
  <depends_on>T02</depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>前端 API 层：新增全量图谱 fetch + 类型定义</name>
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
    1. api/types.ts：补充 FullGraphVO 类型（复用 GraphSubgraphVO 结构 — nodes[] + edges[]）
    2. api/graph.ts：新增 fetchFullGraph() 函数，调用 GET /graph/construction/full
    3. 返回类型使用 ApiResult<GraphSubgraphVO>（与全量图谱响应结构一致）
    4. 沿用 Axios client instance（不直接 import axios）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/api/graph.ts src/api/types.ts 2>&1 | tail -1</verify>
  <done>fetchFullGraph() 可用，TypeScript 编译通过；响应类型与 GraphSubgraphVO 对齐</done>
  <depends_on>T03</depends_on>
</task>

<task id="T06" status="pending">
  <name>重写 GraphCanvas.vue：G6 实例生命周期 + WebGL 渲染</name>
  <read_files>
    frontend/src/views/graph/graphAdapter.ts
    frontend/src/views/graph/constants.ts
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/graph/components/GraphCanvas.vue
  </write_files>
  <action>
    用 G6 v5 重写 GraphCanvas.vue，消除白屏问题。见 DESIGN D1。
    1. onMounted 中创建 G6 Graph 实例：container=ref, renderer='webgl', autoFit='view'
    2. 将 graphAdapter 中的颜色/尺寸常量映射为 G6 node style / edge style 配置
    3. watch(props.data) → graph.setData(data) → graph.render()（增量更新）
    4. 防御：节点数变化 > 5× 时走 graph.destroy() + 重新 create
    5. onBeforeUnmount 中 graph.destroy()
    6. 布局使用 d3-force，配置 nodeRepulsion/idealEdgeLength/gravity
    7. 三态：空态（无数据提示）、加载态（skeleton）、错误态（error + 重试）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/graph/components/GraphCanvas.vue 2>&1 | tail -3</verify>
  <done>GraphCanvas 用 G6 v5 渲染，TypeScript 编译通过；不再引用 cytoscape</done>
  <depends_on>T04</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>新建 useGraphInteraction composable</name>
  <read_files>
    frontend/src/views/graph/graphAdapter.ts
    frontend/src/views/graph/constants.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/composables/useGraphInteraction.ts
  </write_files>
  <action>
    抽离图谱交互逻辑为独立 composable。见 DESIGN D2。
    导出函数 useGraphInteraction(graph: Ref<Graph | null>, data: Ref<G6GraphData | null>)，返回：
    1. search(query: string) → matchedNodes[]（前端内存搜索，防抖由调用方处理）
    2. highlightNode(id: string) → 节点 selected 态 + 其余 dim
    3. clearHighlight() → 所有节点恢复 default
    4. filterByTypes(nodeTypes: string[], edgeTypes: string[]) → 显示/隐藏
    5. expandNeighbors(nodeId: string) → 1-hop highlighted + rest dim
    6. highlightPath(nodeA: string, nodeB: string) → shortest path highlighted
    使用 G6 公开 API：graph.getElementByState()、graph.setElementState()、graph.getNodeData()、graph.getEdgeData()
    最短路径使用 BFS（前端 JS 实现，不依赖 G6 内置算法）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/graph/composables/useGraphInteraction.ts 2>&1 | tail -3</verify>
  <done>useGraphInteraction composable 导出 6 个交互方法；TypeScript 编译通过</done>
  <depends_on>T04</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>新建 GraphToolbar 组件（搜索框 + 视图切换）</name>
  <read_files>
    frontend/src/common/components/BaseInput.vue
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/graph/components/GraphToolbar.vue
  </write_files>
  <action>
    见 UI-DESIGN §4.1。
    1. 左侧：搜索框复用 BaseInput（width=280px, placeholder="搜索节点..."）
       - @input 事件 emit 给父组件，由父组件调用 useGraphInteraction.search()
       - 搜索结果下拉列表：绝对定位，白色底，hairline 边框，max-h=200px 滚动
       - 选中项 emit 'select-node' 事件
    2. 右侧：SegmentedToggle 三段按钮（文档子图 | 剪枝子图 | 全量图谱）
       - 选中段 brand 实底白字；未选中透明底 text-secondary
       - @change emit 'update:viewMode'
    3. props: viewMode, searchResults
    4. emits: 'update:viewMode', 'search', 'select-node'
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/graph/components/GraphToolbar.vue 2>&1 | tail -3</verify>
  <done>GraphToolbar 渲染搜索框 + 三段式视图切换；TypeScript 编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>新建 GraphLegend + NodeDetailPanel + PruningMetaPanel 组件</name>
  <read_files>
    frontend/src/views/graph/constants.ts
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/BaseCard.vue
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/graph/components/GraphLegend.vue
    frontend/src/views/graph/components/NodeDetailPanel.vue
    frontend/src/views/graph/components/PruningMetaPanel.vue
  </write_files>
  <action>
    见 UI-DESIGN §4.2-4.4。

    GraphLegend.vue:
    1. props: nodeTypes (Set<string>), edgeTypes (Set<string>), graphData
    2. 动态渲染 checkbox 组：节点类型区域 + 边类型区域
    3. 每个 checkbox: 色块指示器 + 类型名 label
    4. emits: 'update:nodeFilter', 'update:edgeFilter'

    NodeDetailPanel.vue:
    1. props: node (G6GraphNode | null), visible (boolean)
    2. 右侧 slide-out 面板，320px，fixed 定位
    3. 显示节点 ID、类型、properties 中所有字段（key-value 对）
    4. "展开邻域" Primary Button
    5. emits: 'close', 'expand-neighbors'

    PruningMetaPanel.vue:
    1. props: meta (PruningMetaVO | null)
    2. 仅当 meta 非空时渲染
    3. 水平条：策略名 / 阈值 / 跳数 / 节点数 / 边数 / 截断状态
    4. 风格：brand-veil 底 + supporting 字号
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit src/views/graph/components/GraphLegend.vue src/views/graph/components/NodeDetailPanel.vue src/views/graph/components/PruningMetaPanel.vue 2>&1 | tail -3</verify>
  <done>3 个面板组件 TypeScript 编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T10" status="pending">
  <name>更新 graphStore + GraphVisualizePage：整合所有组件</name>
  <read_files>
    frontend/src/views/graph/graphStore.ts
    frontend/src/views/graph/GraphVisualizePage.vue
    frontend/src/views/graph/graphAdapter.ts
    frontend/src/views/graph/composables/useGraphInteraction.ts
    frontend/src/views/graph/components/GraphCanvas.vue
    frontend/src/views/graph/components/GraphToolbar.vue
    frontend/src/views/graph/components/GraphLegend.vue
    frontend/src/views/graph/components/NodeDetailPanel.vue
    frontend/src/views/graph/components/PruningMetaPanel.vue
    frontend/src/api/graph.ts
  </read_files>
  <write_files>
    frontend/src/views/graph/graphStore.ts
    frontend/src/views/graph/GraphVisualizePage.vue
  </write_files>
  <action>
    graphStore 扩展：
    1. 新增 viewMode: Ref<'document' | 'pruning' | 'full'>
    2. 新增 loadFullGraph() action（调用 fetchFullGraph）
    3. 新增 loadPruningSubgraph(taskId) action（调用 getAnalysisSubgraph）
    4. 现有 loadSubgraph 重命名为 loadDocumentSubgraph，保持兼容

    GraphVisualizePage 重写：
    1. 引入所有新组件（GraphCanvas, GraphToolbar, GraphLegend, NodeDetailPanel, PruningMetaPanel）
    2. 引入 useGraphInteraction composable
    3. 管理视图模式状态 → graphStore 选择正确的 load 方法
    4. 连接 GraphToolbar 搜索事件 → useGraphInteraction.search()
    5. 连接 GraphLegend filter 事件 → useGraphInteraction.filterByTypes()
    6. 连接 NodeDetailPanel expand 事件 → useGraphInteraction.expandNeighbors()
    7. Ctrl+Click 双节点选择 → useGraphInteraction.highlightPath()
    8. 空态/加载态/错误态逻辑
    9. 保留路由参数联动（/graph/document/:id → 自动加载）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit 2>&1 | tail -5</verify>
  <done>全项目 TypeScript 编译通过，graphStore 支持三种视图模式，GraphVisualizePage 整合所有子组件</done>
  <depends_on>T06, T07, T08, T09</depends_on>
</task>

<task id="T11" status="pending">
  <name>构建验证 + Lint + 功能冒烟</name>
  <read_files>
    frontend/src/views/graph/*
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
  </write_files>
  <action>
    1. cd frontend && npm run build（vue-tsc + vite build）
    2. 确认无 TypeScript 错误、无 Vite 构建警告
    3. 确认构建产物中不包含 cytoscape 引用（grep -r cytoscape dist/ 应为空）
    4. 启动 npm run dev，手动验证：
       - 页面加载不报错
       - 选择文档 → 子图渲染不白屏（AC-10）
       - 搜索节点 → 高亮定位（AC-15）
       - 类型筛选 → 节点显隐（AC-16）
       - 点击节点 → 详情面板（AC-17）
       - 全量图谱加载（AC-18）
  </action>
  <verify>cd frontend && npm run build 2>&1 | tail -5; echo "---"; grep -r "cytoscape" dist/ 2>&1 | wc -l | xargs -I{} sh -c '[ {} -eq 0 ] && echo "OK: no cytoscape in build" || echo "FAIL: cytoscape found"'</verify>
  <done>npm run build 成功，dist/ 无 cytoscape 残留，手动 UAT 覆盖 AC-10/15/16/17/18</done>
  <depends_on>T10</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```
