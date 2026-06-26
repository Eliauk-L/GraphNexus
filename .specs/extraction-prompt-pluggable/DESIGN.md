# DESIGN: 抽取提示词外置 + 节点/边类型可插拔

- **Change ID**: `extraction-prompt-pluggable`
- **关联**: `@.specs/extraction-prompt-pluggable/REQUIREMENT.md`、`@.specs/CONTEXT.md`、`@.specs/adr/002-graph-node-edge-abstraction.md`、`@.specs/adr/003-llm-extraction-prompt-strategy.md`、`@.specs/adr/011-query-prompt-template-engine.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 2-design 步骤 0 锁定。CONTEXT.md「已锁技术决策」已全量锁定本项目栈，属例外路径，不展卡片。变栈视为开新 CHANGE（R7.1）。

- **选定**: 既有 Java / Spring Boot 栈（无新栈）
- **前端**: N/A（纯后端）
- **后端**: Java 17 + Spring Boot 3.3.x + Spring Data Neo4j 7.x
- **数据库**: Neo4j 5.x（本次不动 schema）
- **部署**: 既有（podman 容器化，本次不涉及）
- **关键依赖**: Jackson（JSON 反序列化，既有）/ Spring `ResourceLoader`（classpath 资源加载，既有）/ JUnit 5 + Mockito + ArchUnit（测试，既有）
- **理由**: 本次是抽取模块内部重构 + 可插拔抽象，全部能力既有栈已覆盖，不引入新依赖
- **明确排除**: 不引入模板引擎（Thymeleaf/Freemarker 等）——沿用 ADR-011 的纯字符串 `{{var}}` 替换，与智能问答模块一致

---

## 0.5 既有架构对齐（brownfield 必填）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（grep 出来的实际清单）：
- application/graph/construction/extract/ExtractionPromptBuilder.java（既有 · 重写：从 md 加载 + 段落装配）
- application/graph/construction/extract/ExtractionService.java（既有 · convertToDomain：关系 switch → 工厂注册表；新增扩展节点 handler 循环）
- application/graph/construction/extract/ExtractionValidator.java（既有 · 关系合法集从硬编码 Set 改为 EntityRelationType 枚举派生）
- application/graph/construction/model/ExtractionRawResult.java（既有 · 增 @JsonAnySetter 扩展段 Map）
- infrastructure/neo4j/node/EntityType.java（既有 · 增 example 字段供 prompt 生成）
- application/graph/construction/extract/ExtractionJsonParser.java（既有 · 仅扩展段反序列化微调，核心解析不变）

新增模块：
- src/main/resources/prompts/extraction-system.md
- src/main/resources/prompts/extraction-user.md
- src/main/resources/prompts/extraction-fewshot-{subject}.md（数学 + default）
- application/graph/construction/extract/registry/（ExtractionEdgeFactory + ExtractionNodeHandler 接口 + 注册表 + 3 个关系工厂实现）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- application/query/prompt/service/PromptTemplateService.java（智能问答模块 · 仅沿用其约定，不改其代码）
- infrastructure/neo4j/repository/*（持久化层 · ADR-002 契约不动）
- application/graph/fusion/*、application/graph/metrics/*、application/file/grade/*（无关模块）
- 现有 3 类节点（Entity/KP/Category）的转换逻辑（保持硬编码，仅新增类型走注册表——见 D4）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| classpath md 加载 + `{{var}}` 替换 | `PromptTemplateService`（`application/query/prompt/service/`，ADR-011） | **沿用约定，不沿用类**——`loadTemplate`/`assemble` 包级私有且与 query intent 耦合，跨包复用需重构查询模块，不值；抽取装配高度定制，仅 ~10 行加载样板，自建 loader |
| 可插拔类型注册（策略/注册表模式） | `FileParserRegistry`、`KpMatchingStrategy`、`WeightCalculationStrategy`、`SubgraphPruningStrategy` | **沿用模式**——注册表 + Spring bean 注入是本项目可插拔的标准范式，关系工厂/节点 handler 照此实现 |
| 节点/边类型注册枚举 | `EntityType`、`EdgeType`、`NodeType`（`infrastructure/neo4j/`） | **沿用**——实体分类继续用 `EntityType` 枚举作单一来源；关系类型新增 `EntityRelationType` 枚举对齐此风格 |
| 异常 + 错误码 | `BusinessException` + `ErrorCode`（`C0001`/`A0010`） | **沿用**——prompt 加载失败 `C0001`、校验失败 `A0010`，不新增错误码 |
| JSON 反序列化 | Jackson + `ExtractionRawResult` POJO | **沿用**——扩展段用 `@JsonAnySetter` 收集未知 key，handler 用 `ObjectMapper.convertValue` 转强类型 |

### 0.5.3 沿用模式 vs 引入新模式

```
- prompt 承载：**沿用** ADR-011 的 classpath:/prompts/*.md + {{var}} 约定（与智能问答统一）
- prompt 加载器：**引入新模式（抽取模块内置轻量 loader）** → 理由：PromptTemplateService 与 query intent 耦合且包级私有不可跨包复用；抽取装配逻辑定制，跨模块抽共享 util 的收益不抵重构查询模块的风险（已批准）
- 可插拔类型机制：**沿用** 项目既有注册表/策略模式（FileParserRegistry 等），关系工厂 + 节点 handler 照此实现
- 类型契约来源：**沿用** 既有枚举风格——实体分类用 EntityType，关系类型新增 EntityRelationType 枚举（与 EntityType 一致风格）
- 扩展段反序列化：**引入新模式（@JsonAnySetter + convertValue）** → 理由：顶层节点类型可扩展要求 Raw 层支持未知 JSON 段，既有静态 POJO 无法表达；仅在扩展段使用，核心段保持强类型（已批准）
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | prompt 迁到 `classpath:/prompts/extraction-*.md`，抽取模块自建轻量 loader（ResourceLoader + `{{var}}` 替换），沿用 ADR-011 约定但不共用 `PromptTemplateService` 类 | (a) 复用 `PromptTemplateService`（改包级私有为 public / 抽共享 util）；(b) 引入模板引擎 | 仅 ~10 行加载样板重复；`PromptTemplateService` 与 query intent 耦合，跨模块复用需重构查询模块，违反 R7.1 且引入回归风险；模板引擎对纯 `{{var}}` 替换是过度工程 | 轻微 DRY 损失（加载样板）；未来若第三处需 prompt 加载，再抽共享 `PromptTemplateLoader` 到 common（记入 §9） |
| D2 | 实体分类段由 `EntityType` 枚举驱动：枚举新增 `example` 字段，prompt 实体段 = `- {value}：{displayName}（如"{example}"）`，validator 合法集 = `EntityType.values()`（已派生） | (a) example 留 md 按 type 分片段；(b) 把 example 塞进现有 description 字段 | AC-2 要求"新增分类只改枚举一处"——必须单一来源；(b) 混淆契约与示例 | `EntityType`（infra 层枚举）承载 prompt 文案（example），轻微关注点泄漏；该枚举 javadoc 本就声明 description"与 LLM Prompt 对应"，加 example 一致，可接受 |
| D3 | 关系类型：新增 `EntityRelationType` 枚举（value + description，驱动 prompt 关系段 + validator 合法集）+ `ExtractionEdgeFactory` 注册表（按枚举路由，替代 `convertToDomain` 的 switch） | (a) 纯工厂注册表无枚举（工厂自带 type+desc）；(b) 在 `EdgeType` 加 `llmExtractable` 标志 | 用户选"混合/枚举管契约"——关系类型契约走枚举与 `EntityType` 风格一致、类型安全；(b) 把"Neo4j 边类型注册"与"LLM 可抽取关系子集"混在 `EdgeType`，职责不清；(a) 失去枚举类型安全且偏离用户选择 | 新增关系类型需改两处（枚举 + 注册工厂 bean），比纯工厂多一处——这是"枚举管契约"的类型安全代价；非运行期可注入新类型，AC-3 验证改为数据驱动（遍历枚举 × 工厂断言） |
| D4 | 顶层节点类型：`ExtractionNodeHandler` 接口（sectionKey/promptSchema/rawType/validate/convert）+ 注册表；`ExtractionRawResult` 用 `@JsonAnySetter` 收集扩展段到 `Map<String,Object>`；**现有 3 类（Entity/KP/Category）保持硬编码不动，注册表仅服务新增类型**，v1 以 TestNode 演示 | (a) 把现有 3 类也迁到 handler 注册表（单一路径）；(b) 为每类生成强类型 POJO 注册 | (a) 需重写 `convertToDomain`/`ExtractionRawResult`/validator 对 3 类的处理，回归风险大（违反 AC-6/R7.1）；现有类型稳定无需可插拔，注册表只需证明"新增路径"存在（AC-4） | 双路径（硬编码现有 + 注册表新增）是技术债；扩展段反序列化经 `convertValue` 失去编译期类型安全（仅扩展段，核心段仍强类型）；未来可全量迁移（记入 §6/§9） |
| D5 | few-shot 按学科：`extraction-fewshot-{subjectKey}.md` + `extraction-fewshot-default.md` 兜底；subject→key 走可配映射（v1：数学→math），未命中回退 default | (a) 单一固定 few-shot；(b) 领域自适应语义匹配 | AC-5 要求按学科切换；(b) 是 v2（ADR-003 提及的领域自适应），本期不做 | v1 仅数学 + default 两套，其余学科回退 default（与现状等价，不回归）；subject→key 映射需维护（数学/物理/英语…） |

> **关于"混合"策略的细化**：CHANGE 阶段选"混合（枚举管契约，md 管文案）"。本设计据此落地为——**契约形态随类型复杂度分级**：实体分类（D2）与关系类型（D3）契约简单（value+description），用枚举；顶层节点类型（D4）契约含 schema/POJO/转换器，枚举无法承载，用 handler 注册表（本质是"富定义对象"的注册集，与枚举同等单一来源角色）。三者的 prompt 段均由其契约源自动生成，消除漂移。若你认为关系类型应改纯工厂或节点类型应强枚举，可在确认时提出。

---

## 2. 数据流 / 架构图

```
   EntityType enum ───────┐
   (value/displayName/    │
    example/description)  │
                         ▼
   EntityRelationType ──►┌─────────────────────────────────────┐
   enum (value/desc)      │  ExtractionPromptBuilder           │
                         │   load prompts/extraction-system.md │ ◄── ADR-011 约定
   ExtractionNodeHandler │   (ResourceLoader + {{var}} 替换)    │
   registry ────────────►│   fill {{entityTypesSection}}   ← D2│
                         │   fill {{relationTypesSection}} ← D3│
   few-shot md ─────────►│   fill {{extensionNodeSections}}← D4│
   (per subject+default) │   fill {{fewShotSection}} ←按subject│ D5
                         │   → System Prompt                   │
                         │   load prompts/extraction-user.md   │
                         │   fill {{docName}}{{pageCount}}...   │
                         │   → User Message                    │
                         └────────────────┬────────────────────┘
                                          ▼
                         ┌─────────────────────────────────────┐
                         │ ExtractionService.extract           │
                         │  LlmGateway.chat → JSON             │
                         │  ExtractionJsonParser               │
                         │    → ExtractionRawResult            │
                         │       (typed fields + @JsonAnySetter│
                         │        extensionSections Map)  ◄ D4 │
                         │  ExtractionValidator                │
                         │    entity types ← EntityType enum   │
                         │    relation types← EntityRelationType│
                         │    ext sections  ← handler keys     │
                         │  convertToDomain                    │
                         │    entities/KP/categories (硬编码)  │
                         │    relations ← EdgeFactory 注册表 ◄─┼─ 无 switch (D3)
                         │    extension nodes ← NodeHandler ◄──┼─ 循环 (D4)
                         │  → ExtractionResult                 │
                         └─────────────────────────────────────┘
                                          ▼
                         GraphNodeRepository（ADR-002 持久化层，本次不动）
```

**关键边界**：本次只改"prompt 装配 + 抽取层类型路由"，不动 LLM 调用策略（ADR-003 不变）、不动持久化层（ADR-002 不变）、不动 LLM JSON 契约（顶层字段不变）。

## 3. 关键状态机

无新增状态机。文档状态机（`EXTRACTING→EXTRACTED→...`，见 CONTEXT「三阶段流水线」）本次不动；抽取内部流程仍是 `构建Prompt → LLM调用 → JSON解析 → 校验 → 转换` 单线，无状态分支。

## 4. ADR 索引

- `@.specs/adr/023-extraction-prompt-externalize-and-pluggable-types.md`（本次新增 · 可逆性低：prompt 承载方式 + 类型注册抽象）

> 与既有 ADR 关系：**延续** ADR-003（单次调用+few-shot+JSON Schema 策略不变，仅改 prompt 承载与类型装配）、**延续** ADR-011（沿用 classpath:/prompts + {{var}} 约定）、**补齐** ADR-002（持久化层可扩展契约延伸到抽取层）。不 supersede 任何既有 ADR。

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1（实现） | D4 扩展段 `@JsonAnySetter` + `ObjectMapper.convertValue` 反序列化受 Jackson 类型擦除影响，handler 的 `rawType` 转换可能失败 | 新增节点类型链路不可用 | 中 | handler 显式声明 `Class<R> rawType()`，用 `objectMapper.convertValue(List<Map>, javaType)`；TestNode 集成测试覆盖完整链路（AC-4） |
| R2（实现/回归） | prompt 外置 + 类型段由枚举/注册表重生，措辞与原硬编码有细微漂移，导致 LLM 抽取质量回归（ADR-003 few-shot 过拟合隐患） | AC-1/AC-6 不通过 | 中 | golden-file 测试断言静态段 == 原文案快照；类型段生成结果与原文案逐字比对；AC-6 固定输入回归；保持类型 description/example 与原义一致 |
| R3（上线） | prompt md 是 classpath 资源，Maven 打包遗漏则运行时 `C0001` | 生产抽取不可用 | 低 | md 放 `src/main/resources/prompts/`（标准资源目录，自动打包）；启动期加载并校验存在性 + 单测从 classpath 加载断言非空 |
| R4（长期债务） | D4 双路径（硬编码现有 3 类 + 注册表新增）是技术债，扩展模型不一致 | 未来维护困惑、迁移成本累积 | 高 | DESIGN §6 + ADR-023 显式记录；§9 建议后续 change 全量迁移现有类型到 handler 注册表；迁移前双路径均有测试守护 |

> 含实现风险（R1/R2）/ 上线风险（R3）/ 长期债务（R4）三类。

## 6. 不在范围

- 把现有 3 类顶层节点（Entity/KP/Category）迁移到 `ExtractionNodeHandler` 注册表（保持硬编码，注册表仅服务新增类型；迁移留后续 change）
- 扩展段的强类型 POJO 注册（v1 用 `Map`+`convertValue`；每段独立强类型 POJO 留后续）
- 数学/默认以外的学科 few-shot 示例（物理/化学等留 v2）
- 领域自适应 few-shot 自动选取（ADR-003 提及，留 v2）
- prompt md 运行期热更新（v1 启动期加载 + 缓存）
- prompt 版本化与 A/B 抽取质量对比
- 抽取共享 `PromptTemplateLoader` 提取到 common（待第三处需求出现再做，§9 记录）

---

## 9. 架构沉淀建议（本 change 完成后供 `A-evolve` 同步用 · 软约束）

### 9.1 新增的可复用抽象（建议 append 到 CONTEXT「既有抽象索引」段）

| 路径 | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/graph/construction/extract/registry/ExtractionEdgeFactory` + 注册表 | LLM 抽取关系类型 → 边对象的工厂路由（替代 switch） | 新增 LLM 可抽取关系类型 | 新增关系 = 枚举 + 工厂 bean，不动 convertToDomain |
| `application/graph/construction/extract/registry/ExtractionNodeHandler` + 注册表 | 顶层节点类型的 prompt 段/反序列化/校验/转换一体化扩展点 | 新增 LLM 抽取顶层节点类型 | 新增节点类型 = 实现 handler + 注册 bean |
| prompt 段装配模式（枚举/注册表 → `{{placeholder}}`） | 类型元数据自动注入 prompt 段，消除硬编码漂移 | 任何"类型清单需进 prompt"的场景 | 后续 prompt 若需类型段，照此占位符 + 契约源派生 |

### 9.2 新增 / 改变的项目级技术决策（建议 append 到 CONTEXT「已锁技术决策」段）

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| 抽取层类型契约来源 | 实体分类=EntityType 枚举；关系类型=EntityRelationType 枚举 + EdgeFactory 注册表；顶层节点=NodeHandler 注册表 | 抽取模块 prompt/validator/convert | 高——三处契约源已联动，改一处需同步 |
| 抽取 prompt 承载 | classpath:/prompts/extraction-*.md（ADR-011 约定） | 抽取模块 | 低——搬回 Java 文本块即可，但失去 md 可管理性 |

### 9.3 新增 / 修改的跨模块契约

```
- 无新增 API / 事件 / Schema 契约（LLM JSON 顶层字段不变，REST API 不变）
- 抽取模块 prompt 文件命名约定：extraction-system.md / extraction-user.md / extraction-fewshot-{subjectKey}.md / extraction-fewshot-default.md
```

### 9.4 新增 / 升级的依赖

| 包 | 版本 | 用途 | 是否替换既有 |
|---|---|---|---|
| 无 | — | — | 不新增依赖（复用 Jackson / Spring ResourceLoader / ArchUnit 既有） |

### 9.5 禁动清单变化（建议 patch CONTEXT「禁动清单」段）

```
- 新增禁动：ExtractionPromptBuilder 不允许再硬编码实体/关系类型清单（须由 EntityType/EntityRelationType 派生）
- 新增禁动：ExtractionService.convertToDomain 不允许再加关系类型 switch（须走 EdgeFactory 注册表）
- 新增禁动：新增 LLM 抽取类型不允许硬编码进 ExtractionRawResult 强类型字段（顶层节点类型走 NodeHandler 扩展段）
- 解禁：无
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。
