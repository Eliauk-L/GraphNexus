# TASK: GraphNexus 前端管理界面

- **Change ID**: `frontend-ui`
- **关联**: `@.specs/frontend-ui/REQUIREMENT.md`、`@.specs/frontend-ui/DESIGN.md`、`@.specs/frontend-ui/UI-DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P]
Wave 2 (parallel): T03[P], T04[P], T05[P], T06[P], T07[P]    (depends on T01)
Wave 3 (parallel): T08[P], T09[P], T10[P], T11[P]              (depends on T02)
Wave 4 (parallel): T12[P], T13[P], T14[P], T15[P], T16[P], T17[P]  (depends on Wave 2 + 3)
Wave 5:            T18                                          (depends on Wave 4)
```

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>Vite + Vue 3 项目脚手架</name>
  <read_files>
    .specs/frontend-ui/DESIGN.md
    flow-kit/reference/tech-stacks.md
  </read_files>
  <write_files>
    frontend/package.json
    frontend/vite.config.ts
    frontend/tsconfig.json
    frontend/tsconfig.app.json
    frontend/tsconfig.node.json
    frontend/index.html
    frontend/frontend/src/main.ts
    frontend/frontend/src/App.vue
    frontend/frontend/src/env.d.ts
    frontend/.gitignore
  </write_files>
  <action>
    在 frontend/ 目录下用 `npm create vite@latest` 创建 Vue 3 + TypeScript 项目骨架。
    安装依赖：vue, vue-router, pinia, axios, naive-ui, lucide-vue-next,
    @antv/g6, marked, highlight.js, tailwindcss, @tailwindcss/vite。
    vite.config.ts 中配置 @tailwindcss/vite 插件。
    前端项目根目录为 frontend/，与后端 frontend/src/ 同级。
  </action>
  <verify>cd frontend && npm install && npx vue-tsc --noEmit && npm run dev</verify>
  <done>项目启动成功，访问 http://localhost:5173 显示默认 Vite+Vue 页面</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>Design tokens + Tailwind 主题 + 全局排版</name>
  <read_files>
    .specs/frontend-ui/UI-DESIGN.md
  </read_files>
  <write_files>
    frontend/src/assets/tokens.css
    frontend/src/assets/global.css
    frontend/src/main.ts
  </write_files>
  <action>
    按 UI-DESIGN.md frontmatter 创建 CSS variables（颜色/间距/圆角/阴影/动效），
    全部颜色用 OKLCH。Tailwind config 中 extend theme 映射这些 token。
    global.css：typography 9 级层次（display/headline/title/body/body-lead/supporting/label/micro-label/mono），
    应用 base reset，body 字体设为 CJK 系统栈，line-height: 1.6。
    main.ts 中 import 这两个 CSS 文件。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit && cat frontend/src/assets/tokens.css | grep "oklch"</verify>
  <done>tokens.css 含 15 个 OKLCH 颜色变量 + spacing/rounded/motion；global.css 含 9 级 typography</done>
  <depends_on>T01</depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>API TypeScript 类型定义</name>
  <read_files>
    .specs/frontend-ui/CHANGE.md
    .specs/CONTEXT.md
  </read_files>
  <write_files>
    frontend/src/api/types.ts
  </write_files>
  <action>
    基于 CHANGE.md 后端 API 清单和 CONTEXT.md 中的 DTO 定义，
    编写所有 TypeScript 接口：
    - ApiResult&lt;T&gt;（code, message, data, traceId, timestamp）
    - PageResult&lt;T&gt;（list, total, pageNum, pageSize）
    - 请求/响应类型：DocumentVO, GradeRecordVO, GradeUploadResultVO,
      ExtractionResultVO, GraphSubgraphVO, FusionExecuteVO, FusionStatusVO,
      FusionRollbackVO, MetricResultVO, QueryAskRequest, QueryChatRequest,
      QueryAskResponse, QueryAsyncResponse, QueryResultResponse, SubgraphResponse 等
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>types.ts 包含所有 DTO 对应的 TS 接口，编译无类型错误</done>
  <depends_on>T01</depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>Axios 客户端实例 + 拦截器</name>
  <read_files>
    frontend/src/api/types.ts
    .specs/frontend-ui/DESIGN.md
  </read_files>
  <write_files>
    frontend/src/api/client.ts
  </write_files>
  <action>
    创建 Axios 实例：baseURL='/api/v1'，timeout 30s。
    request 拦截器：从 sessionStorage 读 traceId 注入 X-Trace-Id header（V1 暂无 traceId 来源，预留）。
    response 拦截器：成功时 return response.data.data（unwrap ApiResult），
    失败时从 ErrorResponse 提取 userTip → 用 Naive UI useMessage 弹 error toast，
    同时 console.error 打印 traceId + errorCode。
    见 DESIGN D6。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>Axios 实例导出，拦截器逻辑完整，类型推断正确</done>
  <depends_on>T01, T03</depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>API 请求函数（按后端模块分组）</name>
  <read_files>
    frontend/src/api/client.ts
    frontend/src/api/types.ts
    .specs/frontend-ui/CHANGE.md
  </read_files>
  <write_files>
    frontend/src/api/file.ts
    frontend/src/api/graph.ts
    frontend/src/api/query.ts
    frontend/src/api/analysis.ts
  </write_files>
  <action>
    基于 CHANGE.md 端点明细表，创建 4 个 API 模块：
    - file.ts：uploadFile, listFiles, getFile, processFile, updateFile, deleteFile,
      queryGradeByExam, deleteGradeByExam（共 8 函数）
    - graph.ts：extractGraph, getDocumentSubgraph, executeFusion, getFusionStatus,
      rollbackFusion, queryPageRank, queryDegree（共 7 函数）
    - query.ts：askSync, askAsync, chat, getResult（共 4 函数）
    - analysis.ts：getPrunedSubgraph（共 1 函数）
    所有函数调用 client.ts 导出的 Axios 实例，返回值使用 types.ts 中的类型标注。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>20 个 API 函数全部可用，编译通过</done>
  <depends_on>T04</depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>Vue Router 路由配置</name>
  <read_files>
    .specs/frontend-ui/DESIGN.md
  </read_files>
  <write_files>
    frontend/src/router/index.ts
  </write_files>
  <action>
    按 DESIGN §3 路由设计创建 Vue Router 配置（history mode）：
    - / → redirect /files
    - /files → lazy(() => import('@/features/file/FileManagePage.vue'))
    - /grades → lazy(...GradeManagePage)
    - /qa → lazy(...IntelligentQAPage)
    - /graph → lazy(...GraphVisualizePage)
    - /graph/document/:id → lazy(...GraphVisualizePage)
    - /fusion → lazy(...FusionManagePage)
    - /metrics → lazy(...MetricsDashboardPage)
    所有路由组件延迟加载。V1 无路由守卫。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>8 条路由配置完成，lazy loading 生效</done>
  <depends_on>T01</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>Pinia stores（5 个模块 store）</name>
  <read_files>
    frontend/src/api/file.ts
    frontend/src/api/graph.ts
    frontend/src/api/query.ts
    frontend/src/api/analysis.ts
    frontend/src/api/types.ts
    .specs/frontend-ui/DESIGN.md
  </read_files>
  <write_files>
    frontend/src/views/file/fileStore.ts
    frontend/src/views/grade/gradeStore.ts
    frontend/src/views/query/queryStore.ts
    frontend/src/views/graph/graphStore.ts
    frontend/src/views/fusion/fusionStore.ts
    frontend/src/views/metrics/metricsStore.ts
  </write_files>
  <action>
    按 DESIGN D5 创建 6 个 Pinia store（option store 风格，TypeScript）：
    - fileStore：files[], loading, error, uploadFile/loadFiles/processFile/deleteFile actions
    - gradeStore：grades[], searchExamNo/deleteGrade actions
    - queryStore：currentQuestion, answer, taskId, status, tokenUsage,
      sendChat/startPolling/stopPolling actions（见 DESIGN §5 问答状态机）
    - graphStore：currentGraphData, selectDocument/loadSubgraph action
    - fusionStore：lastFusionStatus, executeFusion/loadStatus/rollback actions
    - metricsStore：pagerankResults[], degreeResults[], queryMetrics action
    每个 action 调用对应的 api/* 函数，状态更新用 this.xxx = result。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>6 个 store 定义完成，action 调用 api 函数，类型编译通过</done>
  <depends_on>T05</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>BaseButton + BaseInput + BaseSelect 组件</name>
  <read_files>
    frontend/src/assets/tokens.css
    .specs/frontend-ui/UI-DESIGN.md
  </read_files>
  <write_files>
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/BaseInput.vue
    frontend/src/common/components/BaseSelect.vue
  </write_files>
  <action>
    基于 Naive UI 的 NButton / NInput / NSelect 二次封装，应用 UI-DESIGN §6 的
    Button/Input/Select 规约：
    - BaseButton：封装 primary（冷蓝底白字）+ danger（红字红边框）两种 variant，
      size=medium(36px)，rounded=md(8px)，hover 时 translateY(-1px) 300ms expo-out，
      focus ring 3px brand-veil。无 secondary variant。
    - BaseInput：Naive NInput 封装，应用 tokens.css 的 border/border-focus/error 色，
      高度 36px，focus 外发光 brand-veil。
    - BaseSelect：Naive NSelect 封装，样式同 BaseInput。
    props 透传 Naive 原有 props + 额外 variant/size 等。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>3 个基础表单组件封装完成，样式匹配 UI-DESIGN 规约</done>
  <depends_on>T02</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>BaseCard + StatusBadge 组件</name>
  <read_files>
    frontend/src/assets/tokens.css
    .specs/frontend-ui/UI-DESIGN.md
  </read_files>
  <write_files>
    frontend/src/common/components/BaseCard.vue
    frontend/src/common/components/StatusBadge.vue
  </write_files>
  <action>
    按 UI-DESIGN §6 创建：
    - BaseCard：平面（无阴影）+ hairline border，rounded=md(8px)，padding=md(16px)。
      插槽：header（title）+ default + footer。
    - StatusBadge：rounded=full，padding xs×sm。props: status（UPLOADED/PROCESSING/COMPLETED/FAILED）。
      映射到 UI-DESIGN 的颜色方案：COMPLETED=success 绿，PROCESSING=warning 黄（含旋转动画指示器），
      FAILED=error 红，UPLOADED=text-secondary 灰。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>卡片和状态标签组件样式匹配规约</done>
  <depends_on>T02</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>DataTable 通用表格组件</name>
  <read_files>
    frontend/src/assets/tokens.css
    .specs/frontend-ui/UI-DESIGN.md
  </read_files>
  <write_files>
    frontend/src/common/components/DataTable.vue
  </write_files>
  <action>
    基于 Naive UI NDataTable 封装通用表格组件。
    Props：columns（列定义），data（数据源），loading，emptyText（默认"暂无数据"），
    pagination（{page, pageSize, total}）。
    样式应用 UI-DESIGN §6 Table 规约：表头 micro-label+text-secondary，行高 44px，
    hairline 分隔线，hover 冷蓝 12% 底，分页器页码范围。
    空态：居中 Lucide icon + 文字。
    加载态：Naive UI NDataTable 内置 skeleton。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>DataTable 组件支持分页/空态/加载态/ hover，列定义类型安全</done>
  <depends_on>T02</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>MarkdownViewer 组件</name>
  <read_files>
    frontend/src/assets/tokens.css
    .specs/frontend-ui/UI-DESIGN.md
  </read_files>
  <write_files>
    frontend/src/common/components/MarkdownViewer.vue
  </write_files>
  <action>
    用 marked 解析 Markdown 文本，highlight.js 高亮代码块。
    Props：content（Markdown 字符串），maxWidth（默认 720px）。
    渲染结果应用 UI-DESIGN §6 Markdown 渲染区规约：
    h2/h3 使用 Headline/Title 字号，正文 body 1rem/1.6，代码块 mono 字体+浅灰底。
    用 Naive UI NScrollbar 包裹长文本滚动。
    XSS 防护：marked 默认不解析 HTML。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>MarkdownViewer 正确渲染标题/列表/表格/代码块，样式匹配 typography 层次</done>
  <depends_on>T02</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>文件管理页（FileManagePage + FileUpload）</name>
  <read_files>
    frontend/src/api/file.ts
    frontend/src/api/types.ts
    frontend/src/views/file/fileStore.ts
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/BaseCard.vue
    frontend/src/common/components/BaseInput.vue
    frontend/src/common/components/BaseSelect.vue
    frontend/src/common/components/DataTable.vue
    frontend/src/common/components/StatusBadge.vue
    frontend/src/assets/tokens.css
    .specs/frontend-ui/UI-DESIGN.md
    .specs/frontend-ui/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/views/file/FileManagePage.vue
    frontend/src/views/file/components/FileUpload.vue
  </write_files>
  <action>
    实现 AC-1（导航入口）、AC-2（上传 PDF）、AC-3（上传 CSV）、AC-4（列表分页）、
    AC-5（触发解析）、AC-6（删除）。
    FileManagePage：使用 fileStore 获取文件列表，DataTable 展示（文件名/学科/大小/StatusBadge/时间），
    分页滚动。顶部 FileUpload 按钮 + 学科 Select。
    FileUpload：虚线边框上传区（UI-DESIGN 规约），隐藏 input，选择后显示文件名+大小，
    提交调用 fileStore.uploadFiles。支持 PDF 和 CSV，CSV 上传成功展示返回结果（examNo/studentCount/知识点列表）。
    每行操作：解析按钮（仅 UPLOADED 状态显示）、删除按钮（Danger variant，二次确认）。
    解析中状态每 2 秒轮询 GET /api/v1/document/{id} 直到 COMPLETED/FAILED。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>AC-1~AC-6 可手动验证：上传→列表→解析→删除 完整链路</done>
  <depends_on>T07, T08, T09, T10</depends_on>
</task>

<task id="T13" parallel="true" status="pending">
  <name>成绩管理页（GradeManagePage）</name>
  <read_files>
    frontend/src/api/file.ts
    frontend/src/api/types.ts
    frontend/src/views/grade/gradeStore.ts
    frontend/src/common/components/BaseInput.vue
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/DataTable.vue
    .specs/frontend-ui/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/views/grade/GradeManagePage.vue
  </write_files>
  <action>
    实现 AC-7（按考试编号查询成绩）。
    GradeManagePage：考试编号输入框 + 查询按钮，调用 gradeStore.searchExamNo，
    DataTable 展示（学号/姓名/班级/总分/排名/各题得分明细）。
    查询结果表格上方显示考试信息（名称/日期/学科/考生数）。
    删除按钮（Danger variant，二次确认），调用 gradeStore.deleteGrade。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>AC-7 可手动验证：输入 examNo 查询→展示成绩列表→删除</done>
  <depends_on>T07, T08, T10</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>智能问答页（IntelligentQAPage + 组件）</name>
  <read_files>
    frontend/src/api/query.ts
    frontend/src/api/types.ts
    frontend/src/views/query/queryStore.ts
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/BaseInput.vue
    frontend/src/common/components/MarkdownViewer.vue
    .specs/frontend-ui/UI-DESIGN.md
    .specs/frontend-ui/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/views/query/IntelligentQAPage.vue
    frontend/src/views/query/components/ChatInput.vue
    frontend/src/views/query/components/MarkdownReport.vue
    frontend/src/views/query/components/TokenUsageBar.vue
  </write_files>
  <action>
    实现 AC-8（对话模式）、AC-9（异步轮询）。
    IntelligentQAPage：顶部 ChatInput（textarea + 发送按钮 Lucide Send），
    下方展示对话历史（用户问题 + AI 回答）。
    ChatInput：自动增高 textarea（max 4 行），Enter 发送，Shift+Enter 换行。
    发送后调用 queryStore.sendChat → POST /api/v1/query/chat。
    异步模式：如返回 PENDING → 显示"分析中…"+ 旋转动画，每 1.5 秒轮询
    GET /result/{taskId}，完成后渲染 MarkdownReport。
    MarkdownReport：MarkdownViewer + TokenUsageBar（底部显示 prunedNodes/prunedEdges/estimatedTokens）。
    失败时展示 errorMessage。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>AC-8+AC-9 可手动验证：输入问题→提交→Markdown 报告展示 + Token 统计</done>
  <depends_on>T07, T08, T11</depends_on>
</task>

<task id="T15" parallel="true" status="pending">
  <name>图谱可视化页（GraphVisualizePage + G6 适配 + 画布）</name>
  <read_files>
    frontend/src/api/graph.ts
    frontend/src/api/analysis.ts
    frontend/src/api/types.ts
    frontend/src/views/graph/graphStore.ts
    frontend/src/common/components/BaseSelect.vue
    frontend/src/common/components/BaseCard.vue
    .specs/frontend-ui/UI-DESIGN.md
    .specs/frontend-ui/REQUIREMENT.md
    .specs/adr/002-g6-graph-viz.md
  </read_files>
  <write_files>
    frontend/src/views/graph/GraphVisualizePage.vue
    frontend/src/views/graph/graphAdapter.ts
    frontend/src/views/graph/components/GraphCanvas.vue
    frontend/src/views/graph/components/NodeDetailPopover.vue
  </write_files>
  <action>
    实现 AC-10（文档子图可视化）。
    graphAdapter.ts：核心适配层（见 ADR-002）。
    transformGraphSubgraphVO(GraphSubgraphVO) → G6 GraphData
    transformSubgraphResponse(SubgraphResponse) → G6 GraphData
    统一输出格式：{nodes: [{id, label, nodeType, properties?, style}], edges: [{source, target, type, weight, style}]}
    边颜色映射：PREREQUISITE_OF→蓝，ALIGNED_TO→绿，MASTERS→橙，BELONGS_TO→灰，其余→默认灰。
    不同边类型用不同 lineDash 区分。
    GraphCanvas：接收 G6 GraphData，初始化 G6 Graph 实例（Canvas 渲染器），
    力导向布局（g6 force layout），节点大小按 nodeType 差异（KP=36px, Student=32px, Entity=28px），
    缩放 0.5~3x，拖拽，节点悬浮显示 NodeDetailPopover（properties 键值表）。
    节点颜色按 nodeType 映射（KP=冷蓝，Student=绿，Entity=灰）。
    GraphVisualizePage：文档选择器（Select 列出已抽取文档）→ 加载文档子图 → GraphCanvas 渲染。
    支持路由参数 /graph/document/:id 直接加载指定文档子图。
    V1 不做剪枝子图联动跳转（v2）。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>AC-10 可手动验证：选择文档→画布渲染节点-边图→拖拽缩放→悬浮看属性</done>
  <depends_on>T07, T08</depends_on>
</task>

<task id="T16" parallel="true" status="pending">
  <name>融合管理页（FusionManagePage）</name>
  <read_files>
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
    frontend/src/views/fusion/fusionStore.ts
    frontend/src/common/components/BaseButton.vue
    frontend/src/common/components/BaseCard.vue
    frontend/src/common/components/StatusBadge.vue
    .specs/frontend-ui/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/views/fusion/FusionManagePage.vue
  </write_files>
  <action>
    实现 AC-12（触发融合）、AC-13（查看状态与回滚）。
    FusionManagePage：顶部"执行全量融合"Primary Button，点击调用 fusionStore.executeFusion，
    进行中时按钮 disabled+loading 动画。完成后展示 FusionExecuteVO（fusionLogId/KP 组数/MASTERS 边数）。
    下方展示最近融合状态（FusionStatusVO 全部字段）：触发方式/执行时间/合并 KP 数/MASTERS 边数/是否已回滚。
    "回滚"按钮（Danger variant，二次确认），调用 fusionStore.rollback。
    回滚后刷新状态。
    并发控制：执行中再次点击 → Naive UI useMessage 提示"融合操作正在进行中"。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>AC-12+AC-13 可手动验证：执行融合→查看结果→查看状态→回滚</done>
  <depends_on>T07, T08, T09</depends_on>
</task>

<task id="T17" parallel="true" status="pending">
  <name>图指标看板页（MetricsDashboardPage）</name>
  <read_files>
    frontend/src/api/graph.ts
    frontend/src/api/types.ts
    frontend/src/views/metrics/metricsStore.ts
    frontend/src/common/components/BaseSelect.vue
    frontend/src/common/components/BaseCard.vue
    frontend/src/common/components/DataTable.vue
    .specs/frontend-ui/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/views/metrics/MetricsDashboardPage.vue
  </write_files>
  <action>
    实现 AC-14（图指标查询）。
    MetricsDashboardPage：两个 Tab（PageRank / 度中心性）。
    每个 Tab 内含：指标查询按钮 + DataTable 展示结果（nodeId/nodeType/metricName/metricValue，
    按 metricValue 降序）。
    可选过滤参数：nodeTypes（Select 多选）、edgeTypes（Select 多选），不填=全图默认。
    V1 过滤参数为简单输入框逗号分隔（v2 再改交互式选择器）。
    数字用 JetBrains Mono 字体（DataTable 中 metricValue 列）。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>AC-14 可手动验证：切换 PageRank/Degree tab→查询→排序列表展示</done>
  <depends_on>T07, T08, T09, T10</depends_on>
</task>

<task id="T18" status="pending">
  <name>AppLayout 壳 + Vite proxy 配置 + 全链路冒烟验证</name>
  <read_files>
    frontend/src/router/index.ts
    frontend/src/views/file/FileManagePage.vue
    frontend/src/views/grade/GradeManagePage.vue
    frontend/src/views/query/IntelligentQAPage.vue
    frontend/src/views/graph/GraphVisualizePage.vue
    frontend/src/views/fusion/FusionManagePage.vue
    frontend/src/views/metrics/MetricsDashboardPage.vue
    frontend/src/assets/tokens.css
    .specs/frontend-ui/UI-DESIGN.md
    .specs/frontend-ui/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/App.vue
    frontend/src/common/components/AppLayout.vue
    vite.config.ts
  </write_files>
  <action>
    实现 AC-1（页面框架与导航）。
    AppLayout.vue：左侧深色侧边栏（240px，oklch(0.12 0.01 250) 底）+ 右侧内容区。
    侧边栏顶部文字 logo "GraphNexus" DM Sans 700 + 冷蓝色，
    下方 6 个导航项（Lucide 图标 20px + 文字），router-link 高亮选中态（白字+左侧 3px 冷蓝条）。
    顶部栏 h=48px：显示当前页面标题，右侧预留用户头像占位（首字母圆 32px）。
    内容区：router-view，padding xl(32px)。
    见 UI-DESIGN §6 Shell 规约。

    vite.config.ts：配置 server.proxy {'/api': 'http://localhost:8080'}，
    开发环境 API 请求转发到后端 Spring Boot。

    App.vue：使用 AppLayout 包裹 router-view。

    全链路冒烟：启动 dev server，手动验证 6 个页面导航切换正常，无 console 报错。
    确认所有页面至少渲染出框架结构（数据依赖后端，允许空态展示）。
  </action>
  <verify>cd frontend && npm run dev</verify>
  <done>AC-1 可手动验证：6 模块导航正常切换，侧边栏高亮正确，Vite proxy 转发 /api 到后端</done>
  <depends_on>T06, T12, T13, T14, T15, T16, T17</depends_on>
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

```xml
<!-- 占位 -->
```