# TASK: 学情诊断页增加剪枝子图可视化 + 度量映射 + 多次考试趋势

- **Change ID**: `diagnosis-subgraph-viz`
- **关联**: `@.specs/diagnosis-subgraph-viz/REQUIREMENT.md`、`DESIGN.md`、`UI-DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T06[P]   ← 无文件冲突，无互相依赖
Wave 2 (parallel): T04[P], T05[P]                     ← T04 import T03, T05 依赖 T02 tokens
Wave 3 (serial):   T07                                 ← 集成所有组件，最终验证
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>后端补齐 MASTERS weight + examHistory 透传</name>
  <read_files>
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
    src/main/java/com/graphnexus/application/analysis/model/PrunedSubgraph.java
    src/main/java/com/graphnexus/application/graph/construction/model/GraphNodeData.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/QueryGraphRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
  </write_files>
  <action>
    在 buildResult() 方法中：
    1. 构建弱掌握 KP 节点的 GraphNodeData 时，从 kpMasteryMap 取 weight 放入 properties.put("weight", weight)
    2. 构建前置依赖 KP 节点的 GraphNodeData 时，同样放入 weight（如果 kpMasteryMap 中存在）
    3. 查找 mastersRows 中对应 KP 的 description 列，放入 properties.put("examHistory", description)
       — 注意 mastersRows 的 key 是 kpId，需要能从中取出 description
       — 如果 description 为 null，不放入 examHistory（前端据此判断降级）
    4. MASTERS 边的 description 保持 null（考试历史走节点属性传递，见 ADR-035）
    参考 DESIGN § D7。
  </action>
  <verify>
    cd src && mvn test -pl . -Dtest="StudentDiagnosisStrategyTest" -DfailIfNoTests=false 2>&1 | tail -20
  </verify>
  <done>
    SubgraphResponse 中 KP 节点的 properties 包含 weight（Double）和 examHistory（String 或 null）；
    单元测试验证 buildResult 产出的节点属性含预期 key
  </done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>tokens.css 补充掌握度色阶变量</name>
  <read_files>
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/assets/tokens.css
  </write_files>
  <action>
    在 tokens.css 的「语义色」段后新增「掌握度色阶」段，添加 3 个 CSS 变量：
    — --mastery-orange: oklch(0.62 0.16 65)  （0.4 ≤ weight &lt; 0.6）
    — --mastery-yellow: oklch(0.72 0.12 100)  （0.6 ≤ weight &lt; 0.8）
    — --mastery-gray: oklch(0.65 0.005 95)    （无 MASTERS 节点，复用 --color-text-tertiary 色相）
    注：红端复用 --color-error，绿端复用 --color-success，不需新增变量。
    参考 UI-DESIGN § 3 颜色系统。
  </action>
  <verify>
    grep -c 'mastery-orange\|mastery-yellow\|mastery-gray' frontend/src/assets/tokens.css | grep -q '3' && echo "PASS: 3 tokens" || echo "FAIL"
  </verify>
  <done>
    tokens.css 新增 3 个掌握度色阶变量，grep 可查
  </done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>实现 ExamTrendChart.vue 微型趋势折线图组件</name>
  <read_files>
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/query/components/ExamTrendChart.vue
  </write_files>
  <action>
    新建纯 SVG 微型折线图组件。Props：
    — details: Array&lt;{examDate: string, scoreRate: number}&gt;
    实现：
    1. &lt;svg viewBox="0 0 280 140"&gt;，背景透明
    2. y=0 处一条细线（--color-border, stroke-width=1），无网格线
    3. &lt;polyline&gt; 折线：stroke=--color-brand, stroke-width=2, fill=none
    4. 数据点：&lt;circle&gt; r=3, fill=--color-surface, stroke=--color-brand
    5. 标注：每个点上方 6px 处文字标 scoreRate（font-size=9px, mono）
    6. x 轴标签：日期缩写（font-size=9px, --color-text-tertiary）
    7. 入场动画：stroke-dasharray + stroke-dashoffset 动画（600ms ease-out-quint）
    8. &lt;2 数据点时返回空（不渲染空白图）
    参考 UI-DESIGN § 6 ExamTrendChart 规约 + DESIGN § D5。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/query/components/ExamTrendChart.vue 2>&1 | head -5
  </verify>
  <done>
    组件 vue-tsc 类型检查通过；≥2 数据点时渲染折线+点+标注；&lt;2 数据点返回空
  </done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>实现 DiagnosisNodeDetail.vue 节点详情滑出面板</name>
  <read_files>
    frontend/src/views/graph/components/NodeDetailPanel.vue
    frontend/src/views/query/components/ExamTrendChart.vue
    frontend/src/assets/tokens.css
  </read_files>
  <write_files>
    frontend/src/views/query/components/DiagnosisNodeDetail.vue
  </write_files>
  <action>
    新建节点详情滑出面板组件。Props：
    — node: { id: string, label: string, weight?: number, examHistory?: string } | null
    — visible: boolean
    Emits: close
    实现：
    1. 复用 NodeDetailPanel.vue 的 slide Transition 模式（fixed right 0, 320px, z-index 40）
    2. 标题行：知识点名称（title）+ 关闭按钮（lucide X icon 16px）
    3. Section「掌握度」：label "当前掌握度" + 数值（mono）+ 同色圆点
       — 若 weight 存在：显示数值 + mastery 色圆点
       — 若 weight 不存在：显示"融合数据不可用"（--color-text-tertiary）
    4. Section「考试趋势」：内含 &lt;ExamTrendChart :details="parsedDetails" /&gt;
       — 解析 examHistory JSON → 提取 details 数组
       — JSON.parse 包裹 try-catch，失败时显示"数据格式异常"
       — 若 examHistory 为 null/空 → 显示"暂无历次考试数据"
    5. Section「历次考试」：div flex column 列表，每行 = 日期（supporting + --color-text-secondary）+ 得分率（mono）
    6. Escape 键关闭
    参考 UI-DESIGN § 6 DiagnosisNodeDetail 规约。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/query/components/DiagnosisNodeDetail.vue 2>&1 | head -5
  </verify>
  <done>
    面板 slide 滑出/关闭正常；掌握度/趋势图/考试列表三段内容按有无数据正确展示/降级
  </done>
  <depends_on>T03</depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>实现 DiagnosisSubgraph.vue 诊断子图可视化组件</name>
  <read_files>
    frontend/src/api/analysis.ts
    frontend/src/api/types.ts
    frontend/src/views/graph/graphAdapter.ts
    frontend/src/views/graph/constants.ts
    frontend/src/assets/tokens.css
    frontend/src/common/components/BaseCard.vue
  </read_files>
  <write_files>
    frontend/src/views/query/components/DiagnosisSubgraph.vue
  </write_files>
  <action>
    新建诊断子图组件。Props：
    — taskId: string | null
    实现：
    1. 数据加载：onMounted 中调用 getPrunedSubgraph(taskId)，管理 loading/loaded/error 三态
    2. 容器：&lt;BaseCard title="知识结构子图"&gt; 包裹
    3. SVG 画布：&lt;svg viewBox="0 0 600 400"&gt;，width 100%，height 400px
    4. 力导向布局（见 DESIGN § D2）：
       — Student 节点固定在 (300, 60)
       — 其余节点随机初始位置 → 50 次迭代力模拟
       — 力：节点间斥力 + 边引力 + 中心重力
    5. 节点渲染：
       — &lt;circle&gt; r = 12 + weight * 28（无 weight 时 r=16）
       — fill 按 weight 四档色阶映射（见 DESIGN § D3 + tokens.css mastery 变量）
       — &lt;text&gt; 标签（font=var(--font-display), size=11px），位置在圆下方
       — @click 事件发射给父组件
    6. 边渲染：
       — MASTERS: &lt;line&gt; stroke=--color-text-tertiary, stroke-width=1.5+weight
       — PREREQUISITE_OF: &lt;line&gt; stroke=--color-border, stroke-dasharray="4,4", stroke-width=1
    7. 图例：右上角 5 个色块 + 文字（supporting 字号），横向排列
    8. 状态处理：
       — loading：skeleton 灰色矩形（--color-border 色，# 同画布比例）
       — error："子图数据加载失败"居中文本（supporting + --color-text-tertiary）
       — empty（nodes=0）："暂无子图数据"
    参考 DESIGN § D1-D4 + UI-DESIGN § 6 DiagnosisSubgraph 规约。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/query/components/DiagnosisSubgraph.vue 2>&1 | head -10
  </verify>
  <done>
    组件 vue-tsc 通过；加载态→skeleton，成功态→SVG 图+图例，失败态→降级文本；节点颜色/大小按 weight 正确映射
  </done>
  <depends_on>T02</depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>queryStore 新增子图数据加载能力</name>
  <read_files>
    frontend/src/views/query/queryStore.ts
    frontend/src/api/analysis.ts
    frontend/src/api/types.ts
  </read_files>
  <write_files>
    frontend/src/views/query/queryStore.ts
  </write_files>
  <action>
    在 queryStore 中新增：
    1. 状态：
       — subgraphData: ref&lt;SubgraphResponse | null&gt;(null)
       — subgraphState: ref&lt;'idle' | 'loading' | 'loaded' | 'error'&gt;('idle')
       — selectedKpNode: ref&lt;{ id, label, weight?, examHistory? } | null&gt;(null)
    2. Action loadSubgraph(taskId: string)：
       — 调用 getPrunedSubgraph(taskId)（api/analysis.ts 已有）
       — 成功 → subgraphData = response, subgraphState = 'loaded'
       — 失败 → subgraphState = 'error', console.error 记录 taskId + 错误
    3. Action clearSubgraph()：重置 subgraphData/subgraphState/selectedKpNode
    4. handleResult() 中调用 clearSubgraph()（新诊断前清空旧子图）
    5. 导出新状态和 action
    参考 DESIGN § D6（异步非阻塞加载 + 状态机）。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit src/views/query/queryStore.ts 2>&1 | head -5
  </verify>
  <done>
    store 新增 subgraphData/subgraphState/selectedKpNode 状态 + loadSubgraph/clearSubgraph action；类型检查通过
  </done>
  <depends_on></depends_on>
</task>

<task id="T07" parallel="false" status="pending">
  <name>IntelligentQAPage.vue 集成子图可视化</name>
  <read_files>
    frontend/src/views/query/IntelligentQAPage.vue
    frontend/src/views/query/queryStore.ts
    frontend/src/views/query/components/DiagnosisSubgraph.vue
    frontend/src/views/query/components/DiagnosisNodeDetail.vue
    frontend/src/views/query/components/MarkdownReport.vue
  </read_files>
  <write_files>
    frontend/src/views/query/IntelligentQAPage.vue
  </write_files>
  <action>
    在 IntelligentQAPage.vue 中集成子图可视化：
    1. 导入 DiagnosisSubgraph、DiagnosisNodeDetail
    2. 在 MarkdownReport 下方插入 &lt;DiagnosisSubgraph :task-id="store.taskId" /&gt;
       — 仅当 store.status === 'completed' && store.taskId 时渲染
    3. 子图节点点击事件 → 设置 store.selectedKpNode → DiagnosisNodeDetail 可见
    4. 插入 &lt;DiagnosisNodeDetail&gt; 组件，绑定 store.selectedKpNode + visible
    5. 关闭面板 → store.selectedKpNode = null
    6. 新诊断时 store.handleResult() 已调用 clearSubgraph()（T06 实现），旧子图自动清空
    参考 DESIGN § 2 数据流图 + UI-DESIGN v0 布局线框。
  </action>
  <verify>
    cd frontend && npx vue-tsc --noEmit 2>&1 | grep -c error | grep -q '^0$' && echo "PASS: 0 type errors" || echo "FAIL: type errors found"
  </verify>
  <done>
    诊断完成后子图在报告下方渲染；点击节点弹出详情面板；关闭面板正常；全站 vue-tsc 零错误
  </done>
  <depends_on>T05, T06</depends_on>
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