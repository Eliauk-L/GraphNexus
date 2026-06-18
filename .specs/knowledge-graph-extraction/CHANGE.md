# CHANGE: 知识图谱抽取 — LLM 驱动的文档实体/知识点/关系识别

- **Change ID**: `knowledge-graph-extraction`
- **创建日期**: 2026-06-13
- **路径建议**: 完整（`REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: draft

---

## Why（为什么做）

`document-process-pdf-minimal` 已落地文档上传与 PDF 文本解析链路，产出的 `text_content` 是下游图谱构建的原料。但当前系统停在「文本提取」阶段——解析出的文本仍然是**非结构化字符串**，无法被图分析、智能查询、任务驱动剪枝等后续模块消费。

本 change 是连接「文档处理」与「图分析/智能查询」的**核心桥梁**：将非结构化文本转化为 Neo4j 中的结构化知识点图，打通从 PDF 教辅到知识图谱的完整数据流。

参考文档 `docs/知识图谱构建示例-初中数学教辅.md` 已给出完整的图模型设计和 LLM 抽取方案，本次聚焦其**第一步**落地。

## What（做什么）

基于既有四层架构，实现 **LLM 驱动的文档知识图谱抽取**链路：

1. **LLM 实体/关系抽取**：调用 LLM（通过 `application/llmgateway/` 或 `infrastructure/llm/` 既有抽象），对已解析的文档文本执行 NER/RE：
   - 抽取实体（EntityNode）：定义、公式、概念、例题、解法
   - 抽取实体间关系（ReferencesEdge）：DERIVES / CONTAINS / REFERENCES
   - 对齐到知识点（ALIGNED_TO Edge）：实体 → KnowledgePoint
   - 识别知识点分类层次（KnowledgeCategoryNode + BELONGS_TO + CHILD_OF）
   - 识别知识点前置依赖（PREREQUISITE_OF Edge）
2. **Prompt 工程**：设计 LLM 抽取的 System Prompt + Few-shot 示例，输出 JSON Schema 约束的抽取结果
3. **图节点/边抽象层**（核心架构约束）：节点类型（EntityNode、KnowledgePointNode 等）和边类型（ReferencesEdge、PrerequisiteEdge 等）必须通过抽象基类/接口统一，形成可扩展的类型注册机制。后续新增节点/边类型（如 QuestionNode、MasteryEdge）只需注册新类型，不应修改核心抽取与持久化链路
4. **Neo4j 持久化**：基于抽象层定义本次落地的具体节点类（EntityNode、KnowledgePointNode、KnowledgeCategoryNode、DocumentNode）和边类（ExtractsEdge、ReferencesEdge、DerivesEdge、ContainsEdge、AlignedToEdge、BelongsToEdge、ChildOfEdge、PrerequisiteEdge），通过 Spring Data Neo4j Repository 写入
5. **L1 + L2 全链路**：`api/graph/controller/` + `application/graph/service/` 完整实现，提供触发抽取和查询图谱的 REST API
6. **文档节点关联**：在 Neo4j 中为已解析的 Document 创建对应 `DocumentNode`，建立 `EXTRACTS` 边连接到抽取出的实体

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增功能需求（LLM 抽取/图谱存储/查询的 AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR — 需设计：① LLM Prompt 模板策略与 JSON Schema；② **图节点/边抽象层架构**（类型注册机制、扩展点设计）；③ Neo4j 节点/边模型映射；④ 抽取结果校验机制
- [ ] 影响现有 AC — 无已有 AC，不冲突
- [x] 影响数据模型 / 迁移 — Neo4j 新增 4 类节点（Entity / KnowledgePoint / KnowledgeCategory / Document）、6 类边（EXTRACTS / REFERENCES / DERIVES / CONTAINS / ALIGNED_TO / BELONGS_TO / CHILD_OF / PREREQUISITE_OF）；MySQL 无变更
- [x] 影响外部 API 兼容性 — 新增 REST API 端点（`/api/v1/graph/*`），仅增量不破坏
- [ ] 仅修复 bug，无范围变化
- [x] 依赖 `application/llmgateway/` — 需要 LLM 调用能力（Spring AI + LLMGateway），如该模块尚未实现则本次需同时落地最小可用版本

## 核心设计约束（进入 DESIGN 前必须遵守）

> **图节点与边必须做抽象处理**，为后续新增节点/边类型预留扩展点。
>
> 具体而言：
> - 所有图节点类型共用一个抽象基类/接口，定义 `label`、`properties`、`toMap()` 等通用契约
> - 所有图边类型共用一个抽象基类/接口，定义 `type`、`source`、`target`、`properties` 等通用契约
> - LLM 抽取输出的 JSON Schema 按类型分类设计，新增实体类型/关系类型只需在配置层注册，不应修改核心抽取逻辑
> - Neo4j 持久化层面向抽象编程，不依赖具体节点/边子类
>
> 本次落地 4 类节点（Entity / KnowledgePoint / KnowledgeCategory / Document）和 8 类边，但架构必须能承载后续快速新增 QuestionNode、StudentNode、ExamNode、MasteryEdge 等类型。

## 范围排除（这次不做）

- ❌ **事件图谱**：不处理考试成绩 CSV、EventNode、ExamNode、StudentNode、MasteryEdge（参考文档第四步——那是独立 change）
- ❌ **宽图谱融合**：不做多源图谱的显式融合逻辑（参考文档第五步），但抽取结果入 Neo4j 后自然形成融合基础
- ❌ **知识分类预置种子数据**：KnowledgeCategory 树完全从文档中由 LLM 自动推断，不做手动预置导入
- ❌ **前端可视化**：纯后端 API，不涉及图可视化前端
- ❌ **QuestionNode**：参考文档中未使用的节点类型，本次不涉及
- ❌ **异步抽取**：抽取在请求线程同步完成（后续可改为 MQ 异步），与 `document-process-pdf-minimal` 的同步策略一致
- ❌ **多文档类型适配**：本次仅处理 PDF 文本（继承 `document-process-pdf-minimal` 产出），Word/Markdown 等适配不在范围
- ❌ **增量抽取/重新抽取**：每次触发均为全量抽取覆盖，不做 diff 检测和增量更新
- ❌ **抽取结果人工审核/修正**：LLM 抽取结果直接入库，不经过人工审核工作流

## 验收线（粗粒度，不是 AC）

1. **文档 → 图谱端到端**：给定一篇已解析的文档（`text_content` 非空），调用抽取接口 → Neo4j 中可查询到对应实体节点、知识点节点、分类节点及其关系边
2. **LLM 抽取质量可评估**：抽取结果符合设计的 JSON Schema，实体类型/关系类型在定义枚举范围内，无明显幻觉（如虚构不存在的公式）
3. **图谱可查询**：通过 REST API 可按文档 ID 查询其完整子图（实体+知识点+分类+关系），为下游可视化和分析提供数据

## 风险与未知

- **LLM 抽取质量与成本**：不同 LLM 模型对中文教辅文本的 NER/RE 效果差异大，需用真实教辅 PDF 实测；LLM 调用有 token 成本，大文档需考虑分块策略（chunking）
- **LLMGateway 模块就绪度**：`application/llmgateway/` 和 `infrastructure/llm/` 当前仅有 `package-info.java` 骨架，若未实现则本次需落地最小可用 LLM 调用链路
- **Neo4j Spring Data 映射复杂度**：通过抽象层统一节点/边行为可降低后续扩展成本，但抽象层本身的设计（类型枚举、注册机制、序列化策略）需要在 DESIGN 阶段充分论证，避免过度抽象或抽象泄漏
- **图谱写入性能**：大文档可能抽取数百实体和关系，单次 Cypher 批量写入需考虑事务边界和性能
- **Prompt 迭代风险**：Prompt 设计是核心质量杠杆，可能需要多轮调试，任务拆分时需预留 Prompt 调优 buffer

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。