# CHANGE: 智能问答 — 图剪枝驱动的 LLM 分析与诊断

- **Change ID**: `intelligent-qa`
- **创建日期**: 2026-06-17
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: confirmed
- **用户决策**:
  - Q1 查询意图范围: A — 最小 MVP，仅支持 `STUDENT_DIAGNOSIS`（学生薄弱点诊断）
  - Q2 异步任务持久化: A — MySQL 持久化 `qa_task` 表
  - Q3 子图数据返回: B — LLM 结论 + 独立子图端点
  - Q4 输出语言与格式: A — 中文，LLM 答案以 Markdown 格式生成

---

## Why（为什么做）

`wide-graph-fusion` 已完成宽图谱融合——文档图谱（Entity → KnowledgePoint → KnowledgeCategory + PREREQUISITE_OF）和成绩事件图谱（Student → Exam → KnowledgePoint + ATTENDED + TESTED + MASTERS）已在 KnowledgePoint 层面合并为统一视图，MASTERS 聚合边反映了学生对知识点的掌握度。

但现在的问题是：**图建好了，却问不了问题**。

当前 `GraphService.getSubgraph()` 只能按 `documentId` 查询文档子图，无法回答业务问题：
- "分析学生张三数学薄弱点及根因" → 需要从 Student 出发，沿 MASTERS → KnowledgePoint → PREREQUISITE_OF 链裁剪子图
- "二次函数章节班级整体掌握情况如何" → 需要从 KnowledgeCategory 出发，沿 CHILD_OF → KnowledgePoint → MASTERS → Student 聚合
- "哪些知识点是教学高风险点（全班得分率低于 60%）" → 需要跨 MASTERS 边全局聚合

核心矛盾：**宽图谱有几千个节点和上万条边，直接喂给 LLM 会超出上下文窗口且包含大量噪音**。需要任务驱动的图剪枝策略，从宽图谱中裁剪出"最小够用子图"，再交给 LLM 生成自然语言分析结论。

参考 `CONTEXT.md` 术语"任务驱动剪枝"和"图剪枝"——本 change 是这些概念的落地实现。

## What（做什么）

基于既有四层架构和图节点/边抽象体系，实现**智能问答引擎**，核心链路：

```
用户自然语言问题 → 意图识别 → 图剪枝策略选择 → Cypher 子图查询 → 子图 → LLM Prompt 组装 → LLM 生成分析结论
```

1. **意图识别与策略路由**：
   - 定义查询意图枚举：`STUDENT_DIAGNOSIS`（学生诊断）、`KP_ANALYSIS`（知识点分析）、`CLASS_OVERVIEW`（班级概览）、`PREREQUISITE_CHAIN`（前置依赖链追溯）、`GENERAL`（通用查询）
   - 根据用户问题文本 + LLM 或规则快速判定意图 → 选择对应剪枝策略
   - v1 使用规则（关键词 + 实体提取）+ LLM fallback，后续可扩展为 embedding 分类

2. **图剪枝策略（可扩展）**：
   - 定义 `SubgraphPruningStrategy` 接口（契约：输入 `PruningRequest`（意图 + 实体 + 上下文参数）→ 输出 `PrunedSubgraph`（节点 + 边））
   - v1 首发策略：
     - **StudentDiagnosisStrategy**：Student → MASTERS(weight < 0.6 的薄弱点) → KnowledgePoint → PREREQUISITE_OF(≤2 跳) → KnowledgePoint → MASTERS → Student 的 K 跳子图
     - **KpAnalysisStrategy**：KnowledgePoint → MASTERS（所有学生）→ Student + KnowledgePoint → PREREQUISITE_OF（≤2 跳）的子图
     - **ClassOverviewStrategy**：KnowledgeCategory → CHILD_OF → KnowledgePoint → MASTERS → Student 的聚合子图（含统计聚合而非全量学生节点）
     - **PrerequisiteChainStrategy**：从目标 KnowledgePoint 沿 PREREQUISITE_OF 双向遍历（≤3 跳），附带 MASTERS 边的子图
     - **GraphPruningStrategy**：输入目标 KP + 起始 Student，沿 PREREQUISITE_OF 链向下游遍历，剪去掌握度 > 0.8 的分支，保留薄弱链路
   - 接口预留扩展点，后续可接入 PageRank 中心性剪枝、社区发现剪枝、LLM 自主选择遍历路径等

3. **子图序列化与 LLM Prompt 组装**：
   - 子图 → 结构化文本描述（节点列表 + 关系列表 + 统计摘要），控制 token 消耗
   - Prompt 模板引擎：按意图类型组装 system prompt（角色设定 + 分析框架 + 输出格式约束）+ user prompt（子图结构化描述 + 用户原始问题）
   - 模板可插拔，支持不同分析风格的 prompt（如"教学建议优先"vs"数据罗列优先"）

4. **LLM 调用与结论生成**：
   - 通过既有 `LlmGateway.chat()` 调用 LLM
   - 支持同步调用（REST 同步响应）和异步调用（返回 taskId，轮询结果）两种模式
   - LLM 输出结构化 JSON（含分析结论 + 建议 + 引用证据节点），便于前端渲染

5. **REST API**：
   - `POST /api/v1/qa/ask` — 同步问答（简单问题，30s 内完成）
   - `POST /api/v1/qa/ask-async` — 异步问答（复杂问题，返回 taskId 轮询）
   - `GET /api/v1/qa/result/{taskId}` — 查询异步结果
   - `GET /api/v1/qa/subgraph/{taskId}` — 仅返回剪枝子图（不含 LLM 结论，供调试/可视化）

## 影响面

- [x] 影响 `REQUIREMENT.md` — 全新功能需求（意图识别/剪枝策略/LLM 分析/API 的 AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① `SubgraphPruningStrategy` 接口体系 + 各具体策略的 Cypher 模板；② 子图→LLM Prompt 序列化格式与 token 预算控制；③ 意图识别机制（规则 + LLM fallback）；④ 同步/异步问答 API 协议；⑤ 子图缓存策略
- [ ] 影响现有 AC — 无已有 AC 冲突，本 change 是全新功能
- [x] 影响数据模型 / 迁移 — 新增 `qa_task` 表（MySQL，异步问答任务状态）；无 Neo4j schema 变更（只读查询，不写新节点/边类型）
- [x] 影响外部 API 兼容性 — 新增 4 个 REST 端点，仅增量不破坏；`LlmGateway.chat()` 调用频率增加（需关注配额消耗）
- [ ] 仅修复 bug，无范围变化
- [ ] 依赖新模块 — 复用既有基础设施（Neo4j `GraphNodeRepository`、`LlmGateway`、`FusionService` 查询方法、`GraphEdge`/`GraphNode` 抽象），不引入新外部依赖

## 核心设计约束（进入 DESIGN 前必须遵守）

- **只读操作**：智能问答对 Neo4j 只做查询，不写不删不修改任何节点/边。图谱写入仍由 `knowledge-graph-extraction` / `csv-grade-import` / `wide-graph-fusion` 负责
- **剪枝策略模式**：定义 `SubgraphPruningStrategy` 接口，每种意图对应一个策略实现。新增意图只需实现接口 + 注册，不改路由核心逻辑
- **子图大小上限**：单次 LLM 调用输入 token ≤ 配置值（默认 8000 tokens），子图过大时自动按权重/边类型优先级截断，确保不超 LLM 上下文窗口
- **LLM 输出结构化**：LLM 响应必须包含结构化 JSON（分析结论 + 建议列表 + 证据引用节点 ID 列表），非结构化文本仅作降级兜底。解析失败时自动重试 ≤ 2 次
- **Prompt 模板可替换**：每种意图对应一个 prompt 模板文件（classpath 下的 Markdown），通过配置切换，支持不同学科/学段的定制模板
- **LLM 网关复用**：所有 LLM 调用必须通过 `LlmGateway.chat()`，禁止在 QA 模块中直接使用 LangChain4j client
- **遵循既有四层架构 + 构造器注入 + GraphNode/GraphEdge 抽象体系**。QA 模块代码放 `application/qa/`（L2 服务）+ `api/query/`（L1 控制器，复用既有 `query` 包），策略接口放 `application/qa/strategy/`
- **token 预算控制**：子图序列化时按"相关度优先级"排序节点/边，超出预算截断并在 prompt 中标注"以下内容因长度限制已省略"

## 视觉调性（前端项目必填）

> 本 change 为纯后端，不涉及前端 UI，跳过视觉调性字段。

## 范围排除（这次不做）

- ❌ **前端对话 UI**：不做聊天界面、图可视化、分析报告渲染。纯后端 API，前端由后续 change 负责
- ❌ **多轮对话/会话上下文**：v1 每次问答独立，不维护对话历史和多轮上下文。用户需每次携带完整问题
- ❌ **流式输出（SSE/WebSocket）**：v1 只做同步 + 异步轮询两种模式。LLM streaming 响应留给后续 change
- ❌ **PageRank/中心性等图算法剪枝**：v1 剪枝基于任务类型 + 权重阈值 + K 跳距离，不引入图算法（PageRank、Betweenness、Community Detection）做剪枝决策。`SubgraphPruningStrategy` 接口预留此扩展点
- ❌ **LLM 自主选择遍历路径（Agent 模式）**：v1 剪枝路径由预定义策略决定，不让 LLM 自己决定"接下来查哪些节点"。避免 Agent 循环和不确定性
- ❌ **跨学科分析**：v1 分析单学科内进行（subject 作为必传或从实体推断），不做"学生A数学薄弱→物理也受影响"的跨学科归因
- ❌ **实时成绩更新推送**：新成绩上传后不主动推送更新的分析结论，用户需重新提问
- ❌ **分析结果持久化/缓存**：v1 不缓存历史问答结果。每次请求实时查询 Neo4j + 实时调用 LLM。后续 change 可引入 Redis 缓存
- ❌ **知识点预测/推荐**：不做"建议学生B优先复习哪些知识点"的主动推荐，只做基于提问的被动分析
- ❌ **多模型 A/B 对比**：v1 单一 LLM 调用，不做多模型输出对比评分
- ❌ **QuestionNode 细粒度试题节点**：不引入题目级别的图谱节点，不解析试卷中的具体题目
- ❌ **GraphRAG 全局社区摘要**：不做 Microsoft GraphRAG 式的全局社区发现 + 摘要预生成，本 change 聚焦 query-time 动态剪枝

## 验收线（粗粒度，不是 AC）

1. **学生薄弱点诊断**：输入"分析学生张三的数学薄弱点"，系统识别意图为 `STUDENT_DIAGNOSIS` → 从宽图谱剪枝出张三的低掌握度 KP + 前置依赖链子图 → LLM 生成包含"薄弱知识点 + 根因（前置依赖缺失）+ 学习建议"的结构化分析结论
2. **知识点班级掌握度分析**：输入"二次函数章节的班级整体掌握情况"，系统识别意图为 `KP_ANALYSIS` → 剪枝出该章节所有 KP + 全体学生 MASTERS 边的子图 → LLM 生成包含"各知识点平均掌握度 + 高风险知识点 + 教学建议"的分析
3. **子图大小可控**：即使宽图谱有 5000+ 节点，单次查询剪枝出的子图不超过 LLM token 预算上限（默认 8000 tokens），超出时按权重优先级截断并在 prompt 中标注省略
4. **策略可扩展**：新增一种查询意图只需 ① 实现 `SubgraphPruningStrategy` 接口（一个类 + 一个 Cypher 模板）+ ② 添加一个 Prompt 模板文件（一个 `.md`）+ ③ 注册到策略工厂，不修改路由/调用链路代码
5. **LLM 输出可解析**：LLM 响应能被解析为结构化 JSON（分析结论 + 建议 + 证据引用），解析失败时自动重试（≤2 次），仍失败返回友好错误提示而非原始 LLM 文本
6. **同步/异步双模式**：简单问题通过 `POST /api/v1/qa/ask` 同步返回（<30s）；复杂问题通过 `POST /api/v1/qa/ask-async` 提交 → 返回 taskId → 轮询 `GET /api/v1/qa/result/{taskId}` 获取结果

## 风险与未知

- **剪枝策略"剪多了"**：当前策略基于固定 K 跳 + 权重阈值，可能遗漏关键节点（如一个看似无关的 KP 恰好是根因）。需要在实际数据上验证策略参数，并预留"松弛剪枝"开关（K 跳 +1、阈值 -0.1）
- **LLM 幻觉风险**：LLM 可能引用子图中不存在的节点或编造因果关系。通过 prompt 约束（"仅基于提供的子图数据回答"）+ 输出校验（证据引用必须是子图中的节点 ID）可部分缓解
- **LLM 调用成本**：每次问答 = 1 次意图识别（可选 LLM）+ 1 次分析 LLM 调用。若用户量增大，LLM 费用线性增长。v1 不做缓存，后续可引入"相似问题缓存命中"或"预生成摘要"
- **Token 预算紧张**：对于大班级（50+ 学生）的全局分析，即使剪枝后子图仍可能很大。需要设计"聚合节点"策略——例如班级分析不列出每个学生 MASTERS 边，而是预聚合为 `avg/min/max/distribution` 统计值
- **LLMGateway 负载**：`LlmGateway.chat()` 目前只用于文档抽取（异步低频），问答是同步高频场景，需要评估网关的并发能力和配额上限。v1 不引入队列削峰，通过同步超时 30s + 异步模式兜底
- **意图识别准确率**：v1 规则匹配可能误判（如"张三的数学怎么样"可能同时匹配 STUDENT_DIAGNOSIS 和 CLASS_OVERVIEW）。需要定义优先级和兜底策略（规则未命中时默认走 GENERAL 策略或用 LLM 判定）
- **Neo4j 只读查询性能**：复杂子图查询涉及多跳遍历 + 条件过滤，深链查询（如 PREREQUISITE_OF 链 3 跳以上）可能慢。需要为高频查询模式添加 Neo4j 索引，并在剪枝策略中限制最大跳数
- **科目识别歧义**：用户问题可能不显式包含科目（如"分析张三薄弱点"而非"分析张三数学薄弱点"）。v1 需从 Student 节点推断（张三的年级 → 科目列表）或要求客户端传入 subject 参数

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。