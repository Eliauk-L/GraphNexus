# ADR-023: 抽取提示词外置与类型可插拔注册

- **状态**: proposed（待 REVIEW 后转 accepted）
- **日期**: 2026-06-21
- **关联**: `extraction-prompt-pluggable` DESIGN、ADR-002（graph-node-edge-abstraction）、ADR-003（llm-extraction-prompt-strategy）、ADR-011（query-prompt-template-engine）

---

## Context

`knowledge-graph-extraction` 的抽取 System Prompt 全量硬编码在 `ExtractionPromptBuilder` 的 Java 文本块（约 110 行），与智能问答模块 ADR-011 的 `classpath:/prompts/*.md` 范式不一致，难管理。

更关键的是**类型定义三处割裂**：

1. **实体分类**：`EntityType` 枚举已有 value/displayName/description 富元数据，validator 已从枚举派生（单一来源 ✅），但 prompt 文案里仍硬编码成纯文本 → 新增分类要改枚举 + 改 prompt 两处，易漂移。
2. **关系类型**：validator 的 `Set.of("DERIVES","CONTAINS","REFERENCES")` 硬编码、prompt 里硬编码、`ExtractionService.convertToDomain` 的 switch 也硬编码 → 三处割裂。
3. **顶层节点类型**：`ExtractionRawResult` 的强类型字段 + `convertToDomain` 的硬编码块，新增一个顶层节点类型要改 prompt 段 / Raw POJO / 校验 / 转换四处，无扩展路径。

ADR-002 兑现了**持久化层**的可扩展契约（`GraphNodeRepository` 面向基类编程，新增子类零改动），但**抽取层**未兑现同一份契约——其"扩展流程"第 3 步"在 LLM Prompt 的 JSON Schema 中注册新的 entityType 枚举值"目前是手工改三处。本 ADR 补齐抽取层。

## Decision

五个子决策（对应 DESIGN D1~D5）：

### D1 · 提示词外置

System/User Prompt 迁到 `classpath:/prompts/extraction-system.md` / `extraction-user.md`，复用 ADR-011 的 `ResourceLoader` + `{{var}}` 约定。**不共用 `PromptTemplateService` 类**——其 `loadTemplate`/`assemble` 包级私有且与 query intent 耦合，跨包复用需重构查询模块，不值；抽取装配高度定制，自建轻量 loader。类型段 / few-shot 段为动态 `{{placeholder}}`，由各自契约源填充。

### D2 · 实体分类枚举驱动

`EntityType` 枚举新增 `example` 字段；prompt 实体段 = `- {value}：{displayName}（如"{example}"）`，validator 合法集 = `EntityType.values()`（已派生）。**新增实体分类 = 加枚举一行**，prompt + validator 自动生效，无需改他处。

### D3 · 关系类型枚举 + 工厂注册表

新增 `EntityRelationType` 枚举（value + description，驱动 prompt 关系段 + validator 合法集）+ `ExtractionEdgeFactory` 注册表（按枚举路由，替代 `convertToDomain` 的 switch）。**新增关系类型 = 加枚举 + 注册工厂 bean**，switch 不动。`EntityRelationType` 与 `EdgeType` 分工：后者是 Neo4j 全量边类型注册，前者是 LLM 可抽取关系子集契约。

### D4 · 顶层节点类型 handler 注册表

`ExtractionNodeHandler` 接口（`sectionKey`/`promptSchema`/`rawType`/`validate`/`convert`）+ 注册表；`ExtractionRawResult` 用 `@JsonAnySetter` 收集未知 JSON 段到 `Map<String,Object> extensionSections`，handler 用 `ObjectMapper.convertValue` 转强类型。**现有 3 类（Entity/KP/Category）保持硬编码不动，注册表仅服务新增类型**（v1 以 TestNode 演示扩展路径）。现有类型稳定无需可插拔；迁移到注册表回归风险大，留后续 change。

### D5 · few-shot 按学科

`extraction-fewshot-{subjectKey}.md` + `extraction-fewshot-default.md` 兜底；subject→key 走可配映射（v1：数学→math），未命中回退 default。

### 关于"混合"策略的落地

CHANGE 阶段选"混合（枚举管契约，md 管文案）"。本 ADR 据此将**契约形态按类型复杂度分级**：实体分类（D2）与关系类型（D3）契约简单（value+description），用枚举；顶层节点类型（D4）契约含 schema/POJO/转换器，枚举无法承载，用 handler 注册表（本质是"富定义对象"的注册集，与枚举同等单一来源角色）。三者 prompt 段均由契约源自动生成，消除漂移。

## Consequences

**正向**：
- prompt 可 md 管理，与智能问答模块约定统一
- 类型单一来源：实体分类（枚举）、关系类型（枚举+工厂）、节点类型（handler 注册表）各自单源，消除三处漂移
- 关系类型 / 顶层节点类型可插拔，新增不改 switch / 核心分支
- 兑现 ADR-002 在抽取层的可扩展契约

**负向**：
- D2 `EntityType`（infra 层枚举）承载 prompt 文案（example 字段），轻微关注点泄漏——可接受，因该枚举 javadoc 本就声明 description"与 LLM Prompt 对应"
- D4 双路径（硬编码现有 3 类 + 注册表新增）是技术债，扩展模型不一致
- D4 扩展段经 `@JsonAnySetter` + `convertValue` 反序列化失去编译期类型安全（仅扩展段，核心段仍强类型）
- D3 新增关系类型需改两处（枚举 + 工厂 bean），是"枚举管契约"的类型安全代价

**被否决的方案**：

| 方案 | 否决理由 |
|:--|:--|
| 复用 `PromptTemplateService` 类（改包级私有为 public / 抽共享 util） | 与 query intent 耦合，跨模块重构查询模块违反 R7.1，收益不抵回归风险；~10 行加载样板不值得 |
| 关系类型纯工厂注册表无枚举 | 失去类型安全，偏离用户"枚举管契约"选择；与 `EntityType` 风格不一致 |
| 在 `EdgeType` 加 `llmExtractable` 标志 | 把"Neo4j 全量边类型注册"与"LLM 可抽取关系子集"混在一处，职责不清 |
| 把现有 3 类节点也迁到 handler 注册表（单一路径） | 需重写 `convertToDomain`/`ExtractionRawResult`/validator 对 3 类处理，回归风险大（违反 AC-6/R7.1）；现有类型稳定无需可插拔 |
| 引入模板引擎（Thymeleaf/Freemarker） | 对纯 `{{var}}` 替换是过度工程，与 ADR-011 不一致 |

---

> 推翻本 ADR 的触发条件：① D4 扩展段反序列化在生产频繁出错 → 为扩展段引入类型安全 POJO 注册；② D4 双路径维护成本过高 → 全量迁移现有 3 类到 handler 注册表；③ 实测发现枚举驱动 prompt 段导致 LLM 抽取质量回归且无法用 golden-file 兜住 → 回退部分类型段为 md 静态文案。
