# CHANGE: 抽取提示词外置 + 节点/边类型可插拔

- **Change ID**: `extraction-prompt-pluggable`
- **创建日期**: 2026-06-21
- **路径建议**: 完整
- **状态**: draft

---

## Why（为什么做）

当前知识图谱抽取的 System Prompt 全量硬编码在 `ExtractionPromptBuilder.buildSystemPrompt()` 的 Java 文本块里（约 110 行：角色 + 5 实体类型 + 3 关系 + 二次函数 few-shot + JSON 约束）。这带来三个具体痛点：

1. **提示词难以管理**：文案与代码耦合，想调一句 prompt 就要改 Java、重新编译、走代码评审。项目在智能问答模块已有 `PromptTemplateService` + `classpath:/prompts/*.md` + `{{var}}` 替换的成熟范式（ADR-011），抽取模块却没用上，两处 prompt 管理方式不一致。

2. **类型定义三处割裂、易漂移**：
   - 实体类型：`EntityType` 枚举已有 value/displayName/description 富元数据，validator 已从枚举派生（单一来源 ✅），但 **prompt 文案里仍硬编码成纯文本** → 新增一个实体分类要改枚举 + 改 prompt 文案两处，漏改就漂移。
   - 关系类型：validator 的 `Set.of("DERIVES","CONTAINS","REFERENCES")` 硬编码、prompt 里硬编码、`ExtractionService.convertToDomain` 的 `switch` 也硬编码 → 三处割裂，新增一个 LLM 关系类型要改三处。

3. **抽取层缺可插拔性，与既有契约矛盾**：ADR-002「GraphNode/GraphEdge 抽象层」+ AC-5 早已确立"新增类型只需注册即可被核心链路识别和持久化"的契约，但那只落地在**持久化层**（`GraphNodeRepository` 面向基类编程，新增子类零改动）。ADR-002 的"扩展流程"第 3 步写着"在 LLM Prompt 的 JSON Schema 中注册新的 entityType 枚举值"——但这步目前是手工改 prompt + validator + switch 三处，**抽取层没有兑现同一份可插拔契约**。新增一个顶层节点类型（如未来的 TheoremNode）要硬改 prompt 段、Raw POJO、校验、转换四处。

触发原因：用户希望把提示词抽成 md 便于管理，同时把节点/边类型做成可插拔，让"新增节点类型"有良好扩展性。

## What（做什么）

把抽取提示词从 Java 文本块外置为 `prompts/*.md`（对齐 ADR-011 范式），并把**抽取层的类型系统**（实体分类、顶层节点类型、LLM 关系类型）改造为可插拔的注册机制，使新增类型时类型段自动注入 prompt、validator 自动派生、转换链路自动路由，消除三处割裂。few-shot 示例按学科可切换。采用**混合**策略：Java 枚举管类型契约（枚举值/校验/转换路由），md 管提示词文案描述；prompt 的类型段由枚举元数据自动生成。

范围覆盖两层（用户已确认「两者都要」）：
- **实体分类层**（entityType ∈ DEFINITION/FORMULA/...）：新增一个分类只需改枚举，prompt 类型段 + validator 自动生效，无需改 switch。
- **顶层节点类型层**（EntityNode / KnowledgePointNode / KnowledgeCategoryNode / 未来 TheoremNode）：提供注册抽象，新增一个顶层节点类型有明确扩展路径（prompt schema 段 + Raw POJO + 校验 + 转换均可注册），并以一个最小新类型验证该路径。

## 视觉调性（前端项目必填，由 0-change 步骤 0.6 预选填入）

> 纯后端任务（0-change 步骤 0.5 判定非前端项目），跳过本节。

## 影响面

- [x] 影响 `REQUIREMENT.md` — 新增抽取层可插拔性 AC + few-shot 按学科切换 AC；保留 AC-1（端到端抽取）/AC-3（JSON Schema 校验）不回归
- [x] 影响 `DESIGN.md` / 引入新 ADR — 抽取层类型注册抽象（prompt 段装配 + Raw POJO + 校验 + 转换的可注册设计）需新 ADR；与 ADR-002（持久化层抽象）、ADR-003（prompt 策略）、ADR-011（prompt 模板）联动
- [x] 影响现有 AC — AC-3 的枚举校验来源从硬编码改为枚举派生（行为等价）；AC-5 的可扩展性从持久化层延伸到抽取层
- [ ] 影响数据模型 / 迁移 — 不动 Neo4j schema，旧数据不迁移
- [ ] 影响外部 API 兼容性 — LLM JSON 契约与 REST API 对上游不变
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- **不实际新增具体业务节点类型**（如 TheoremNode/QuestionNode）—— 本期只交付可插拔骨架 + 以"新增 1 个实体分类"作为可插拔性验证；具体新业务类型留后续 change。
- **不做领域自适应 few-shot 自动选取**（按文档内容语义匹配最贴合的示例）—— 本期只做按 subject 显式映射 + 缺省回退默认示例。ADR-003 提到的"领域自适应 Few-shot 选取"留后续。
- **不切换到 JSON Mode / Function Calling，不重构为多步 Pipeline** —— ADR-003 已否决这两条；仍是单次调用 + 纯文本 prompt + 后处理校验。
- **不做 prompt 文案内容的大幅改写优化** —— 保持现有 prompt 语义不变，只改承载方式（搬到 md）与类型装配（枚举生成），用户未选"允许内容精修"。
- **不改外部 API / LLM JSON 契约** —— 顶层字段仍是 entities/knowledgePoints/categories/alignments/entityRelations/prerequisites/categoryRelations。
- **不覆盖成绩事件图谱的类型可插拔** —— Student/Exam/TESTED/MASTERS 等是结构化导入边，非 LLM 抽取，不在本次范围。
- **不做存量已抽取数据迁移** —— 旧数据不动，新机制只影响新抽取。

## 验收线（粗粒度，不是 AC）

1. 抽取 System Prompt 从 Java 搬到 `prompts/*.md`；类型段由枚举元数据自动生成。新增 1 个实体分类只需改枚举 1 处，即自动出现在 prompt 与 validator，且无需改 `convertToDomain` 的 switch。
2. 新增 1 个 LLM 抽取关系类型只需"枚举 + 注册工厂"，不修改 `ExtractionService.convertToDomain` 的分支代码。
3. 新增 1 个顶层节点类型有明确、文档化的扩展路径（prompt schema 段 + Raw POJO + 校验 + 转换均可注册），并以一个最小新类型跑通该路径。
4. few-shot 可按学科切换，缺省回退默认示例；AC-1（端到端抽取覆盖率）与 AC-3（JSON Schema 校验）不回归。

## 风险与未知

- **顶层节点类型抽取层抽象是本次最重部分**：要让"prompt schema 段 + Raw POJO + 校验 + 转换"四处都可注册，抽象边界需谨慎设计。若 DESIGN 阶段发现过重，按 R1.7 就地在 TASK 拆为 ≥2 子任务，或建议把"顶层节点类型可扩展"拆为独立 change，本期先交付实体分类层 + 关系类型层 + prompt 外置 + few-shot 分域。
- **Raw POJO 的可注册性**：`ExtractionRawResult` 是 Jackson 反序列化目标，顶层节点类型对应不同 JSON 段。新增节点类型如何在不改 `ExtractionRawResult` 硬字段的前提下接入反序列化（自定义反序列化器 / 按段分发 / 预留扩展段），需在 DESIGN 定。
- **prompt 类型段自动生成的语义等价性**：由枚举 description 拼出的类型段必须与现有硬编码文案语义等价，否则可能影响 LLM 抽取质量（AC-1）。需用现有抽取测试做回归比对。
- **few-shot 分域的示例来源**：物理/化学等学科的示例需人工准备；本期是否只交付数学 + 兜底默认，其余学科留占位，待 REQUIREMENT 确认。

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。
