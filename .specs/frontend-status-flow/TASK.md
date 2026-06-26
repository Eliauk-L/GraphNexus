# TASK: 文件处理状态流前端实时展示

- **Change ID**: `frontend-status-flow`
- **关联**: `@.specs/frontend-status-flow/REQUIREMENT.md`、`@.specs/frontend-status-flow/UI-DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P]
Wave 2 (parallel): T03[P], T04       (T04 depends on T01, T02)
Wave 3:            T05               (depends on T03, T04)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>修复 fileStore 轮询策略 + 移除页面挂载无条件轮询</name>
  <read_files>
    frontend/src/views/file/fileStore.ts
    frontend/src/views/file/FileManagePage.vue
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/file/fileStore.ts
    frontend/src/views/file/FileManagePage.vue
  </write_files>
  <action>
    fileStore.ts 三处改动：

    1. INTERMEDIATE_STATES 改为 ['PARSING', 'EXTRACTING', 'FUSING']
       （仅活跃处理态，移除 UPLOADED 稳定态避免空转）

    2. startPolling() 增加最大轮询时长兜底：
       - 记录 pollingStartTime = Date.now()
       - 每轮检查：若 Date.now() - pollingStartTime > 300_000（5分钟），调用 stopPolling() 并 return
       - stopPolling() 中清空 pollingStartTime

    3. startPolling() 改为仅在上传/手动解析后调用，不再无条件启动：
       - upload() 中已有 startPolling() 调用（保留）
       - parse() 中已有 startPolling() 调用（保留）
       - FileManagePage.vue onMounted 中移除 setTimeout(() => { if (store.hasIntermediateFiles) store.startPolling() }, 1000)
       - 移除对 store.isPolling / store.hasIntermediateFiles 的引用（如模板中的轮询提示）

    遵循现有代码风格：setInterval + clearInterval，静默失败。
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>vue-tsc 零错误；轮询仅在主动触发后启动；UPLOADED 文件不触发轮询；5分钟后自动停止；AC-1~AC-4 的轮询逻辑部分就绪</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>新建 StatusPipeline 管线进度指示器组件</name>
  <read_files>
    frontend/src/assets/tokens.css
    frontend/src/common/components/StatusBadge.vue
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/file/components/StatusPipeline.vue
  </write_files>
  <action>
    在 frontend/src/views/file/components/StatusPipeline.vue 新建组件，按 UI-DESIGN §6.1 规约：

    1. Props: status: FileStatus（必填）, failReason?: string（可选）

    2. 模板结构（非 FAILED/DELETING 时）：
       - 根 div.inline-flex(flex-col, items-center, gap: 4px)
       - 圆点行：3 个 span.pipeline-dot + 2 个 span.pipeline-connector
       - 标签行：3 个 span.micro-label（解析/抽取/融合）

    3. 圆点三态 computed（基于 status）：
       - completed: bg=--color-success, 无边框, 6px实心
       - active: bg=--color-brand, animation: pulse 600ms ease-out infinite
       - pending: bg=transparent, border=1.5px --color-text-tertiary, 6px空心

    4. 连接线两态 computed：
       - completed: bg=--color-success（连接两个已完成阶段）
       - pending: bg=--color-border（其他情况）

    5. 状态→圆点映射：
       - UPLOADED/PARSING → [active, pending, pending]
       - PARSED → [completed, pending, pending]
       - EXTRACTING → [completed, active, pending]
       - EXTRACTED → [completed, completed, pending]
       - FUSING → [completed, completed, active]
       - COMPLETED → [completed, completed, completed]

    6. FAILED/DELETING 回退：
       - FAILED: 渲染红色胶囊徽章「失败」+ failReason 截断文字，hover NTooltip 完整原因
       - DELETING: 渲染灰色胶囊徽章「删除中」
       - 复用 StatusBadge 的样式映射（color + bg）

    7. Hover tooltip：使用 Naive UI NTooltip，placement="top"，delay 300ms
       - 内容：当前状态名（label 类）+ 阶段说明（supporting 类）

    8. 新增 @keyframes pulse { 0%,100%{opacity:0.4} 50%{opacity:1} }
       注：prefers-reduced-motion 已在 tokens.css 全局处理，无需重复

    9. 样式 scoped，引用 tokens.css 的 CSS 变量，不新定义任何 token
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>vue-tsc 零错误；组件覆盖全部 8 个文档状态 + DELETING；FAILED 态渲染红色徽章 + failReason；hover 显示 tooltip；AC-5 组件就绪</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>图谱页文档选择器状态联动 + 非终态提示条</name>
  <read_files>
    frontend/src/views/graph/GraphVisualizePage.vue
    frontend/src/views/graph/graphStore.ts
    frontend/src/api/types.ts
    frontend/src/api/file.ts
    frontend/src/assets/tokens.css
    frontend/src/common/components/BaseSelect.vue
    frontend/src/common/components/BaseCard.vue
  </read_files>
  <write_files>
    frontend/src/views/graph/GraphVisualizePage.vue
    frontend/src/views/graph/graphStore.ts
  </write_files>
  <action>
    按 UI-DESIGN §6.2 + §6.3 实施两处改动：

    【graphStore.ts】
    1. loadDocuments() 不再过滤文档列表，全部文档都返回（保留 status 字段）
       - 移除 graphStatuses 过滤逻辑
       - 但若文档处于 EXTRACTING/FUSING，前端在图谱页标记为不可选
    2. 新增 currentDocStatus computed：从当前选中文档获取 status
    3. 将全部文档列表暴露为 allDocuments（含状态），供下拉框展示

    【GraphVisualizePage.vue】
    1. 文档选择器（BaseSelect）选项增强：
       - docOptions computed 中为每个选项增加 disabled 标记：
         EXTRACTING/FUSING/其他非终态 → disabled: true
         COMPLETED/EXTRACTED → disabled: false
       - 选项 label 格式：`文档名.pdf — 抽取中`（状态文字用中文）
       - 利用 BaseSelect 或 NSelect 的选项 disabled 属性

    2. 非终态提示条（UI-DESIGN §6.3）：
       - 位置：GraphCanvas 上方、BaseCard 内部（header slot 或 body 顶部）
       - 显示条件：selectedDocId 对应的文档 status === 'EXTRACTED'（融合未完成/失败回退）
       - 样式：背景 --color-brand-veil，顶部 2px --color-brand 细线（不用 border-left 侧条）
       - 文字（supporting 类）：「该文档图谱数据可能不完整 — 融合尚未完成」
       - 提供 × 关闭按钮（v-if 控制的本地状态，不自动消失）
       - 切换文档时重置提示条显示状态
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>vue-tsc 零错误；文档选择器中 EXTRACTING/FUSING 文档 disabled；选 EXTRACTED 文档时显示提示条；选 COMPLETED 文档时无提示条；AC-6 + AC-7 就绪</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="false" status="pending">
  <name>FileManagePage 状态列替换为 StatusPipeline</name>
  <read_files>
    frontend/src/views/file/FileManagePage.vue
    frontend/src/views/file/fileStore.ts
    frontend/src/views/file/components/StatusPipeline.vue
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/file/FileManagePage.vue
  </write_files>
  <action>
    在 FileManagePage.vue 中：

    1. import 替换：StatusBadge → StatusPipeline（从 ./components/StatusPipeline.vue）

    2. columns 定义中「状态」列的 render 函数：
       - 原：h(StatusBadge, { status: row.status })
       - 新：h(StatusPipeline, { status: row.status, failReason: row.failReason })
       - 注：TextbookVO 当前未包含 failReason 字段，需同步在 types.ts 中新增
         failReason?: string（TextbookVO 接口），后端已有此字段（FileStatus + failReason）

    3. 模板中移除轮询提示（已在 T01 中移除相关逻辑，此处清理残留）：
       - 若 <span v-if="store.isPolling"> 仍存在则移除

    4. 状态列宽度调整（管线指示器约 80px 宽，120px 列宽足够，保持不变）
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>vue-tsc 零错误；文件列表状态列显示管线进度指示器而非文字徽章；各状态对应正确的圆点组合；FAILED 文件显示红色徽章+failReason；AC-5 完成</done>
  <depends_on>T01, T02</depends_on>
</task>

<task id="T05" parallel="false" status="pending">
  <name>全量验证 + AC 逐条核对</name>
  <read_files>
    frontend/src/views/file/fileStore.ts
    frontend/src/views/file/FileManagePage.vue
    frontend/src/views/file/components/StatusPipeline.vue
    frontend/src/views/graph/GraphVisualizePage.vue
    frontend/src/views/graph/graphStore.ts
    frontend/src/api/types.ts
    .specs/frontend-status-flow/REQUIREMENT.md
  </read_files>
  <write_files>
    frontend/src/api/types.ts
  </write_files>
  <action>
    1. types.ts：TextbookVO 新增 failReason?: string 字段（后端已有，前端补齐）

    2. 编译验证：cd frontend && npx vue-tsc --noEmit，确保零错误

    3. AC 逐条手动核对（无自动化测试框架，走手动验证）：

       AC-1（轮询追踪完整管线）：
       - 启动后端 + 前端，上传 PDF，观察文件列表状态列
       - 确认状态依次经历：解析中→已解析→抽取中→已抽取→融合中→已完成

       AC-2（仅追踪活跃态）：
       - 上传文件不点解析，打开 Network 面板，确认 10s 内无持续 /textbooks 请求
       - 等待文件走到 COMPLETED 后，确认轮询停止

       AC-3（最大轮询时长兜底）：
       - 代码审查确认：startPolling() 含 maxDuration 检查（5min = 300_000ms）
       - 可通过临时改为 10s 验证自动停止

       AC-4（页面挂载不自动轮询）：
       - 从其他页面切到文件管理页，Network 面板确认只有 1 次 /textbooks 请求

       AC-5（管线进度可视化）：
       - 确认不同状态文件显示正确的圆点组合（参考 T02 action 的映射表）

       AC-6（图谱页文档选择器状态）：
       - 同时有 EXTRACTING 和 COMPLETED 文档时，下拉框展示差异、EXTRACTING disabled

       AC-7（非终态提示条）：
       - 选 EXTRACTED 文档加载子图，确认提示条出现；选 COMPLETED 文档，确认无提示条
  </action>
  <verify>cd frontend && npx vue-tsc --noEmit</verify>
  <done>vue-tsc 零错误；7 条 AC 全部通过手动核对（前 4 条涉及后端联动需实际运行确认）</done>
  <depends_on>T03, T04</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中（同时只允许一个非 [P] 任务为此状态）
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞（必须在文件末尾「阻塞日志」记录）

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