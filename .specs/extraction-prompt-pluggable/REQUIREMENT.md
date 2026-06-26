# REQUIREMENT: 抽取提示词外置 + 节点/边类型可插拔

- **Change ID**: `extraction-prompt-pluggable`
- **关联**: `@.specs/extraction-prompt-pluggable/CHANGE.md`、`@.specs/CONTEXT.md`、`@.specs/adr/002-graph-node-edge-abstraction.md`、`@.specs/adr/003-llm-extraction-prompt-strategy.md`、`@.specs/adr/011-query-prompt-template-engine.md`

---

## 用户故事

- **US-1**：作为图谱模块维护者，我想把抽取 System Prompt 从 Java 文本块外置成 `classpath:/prompts/*.md` 文件，以便不改 Java 代码即可调整 prompt 文案，且与智能问答模块的 `PromptTemplateService` 范式（ADR-011）统一。
- **US-2**：作为图谱模块维护者，我想新增一个实体分类（entityType）时只改 `EntityType` 枚举一处，prompt 的实体类型段和 validator 校验自动生效，以便消除"改枚举 + 改 prompt 文案 + 改 validator 硬编码"的漂移风险。
- **US-3**：作为图谱模块维护者，我想新增一个 LLM 抽取关系类型时通过注册工厂接入，不修改 `ExtractionService.convertToDomain` 的 switch 分支，以便关系类型可插拔。
- **US-4**：作为图谱模块维护者，我想新增一个顶层节点类型时有文档化的扩展路径（prompt schema 段 + 反序列化 + 校验 + 转换均可注册），以便兑现 ADR-002 / AC-5 在抽取层尚未落地的可扩展契约。
- **US-5**：作为教育领域使用者，我想 few-shot 示例能按学科切换，以便数学/物理等不同学科文档的抽取精度不被单一数学示例锚定（对应 ADR-003 的领域过拟合隐患）。

## 验收准则（AC）

每条用 Given / When / Then，必须可验证。

### AC-1 · 提示词外置且语义等价

- **Given** 抽取模块的 System Prompt 已从 `ExtractionPromptBuilder` 的 Java 文本块迁出，改为从 `classpath:/prompts/*.md` 加载（复用 ADR-011 的 `ResourceLoader` 范式）
- **When** 调用 `buildSystemPrompt()` / `buildUserMessage(...)`
- **Then** 返回的 System Prompt 与原硬编码文案**语义等价**：静态段落（角色设定、输出规则、subject 命名规范等）逐段一致；类型段落由枚举派生（见 AC-2）；few-shot 段落由学科选择（见 AC-5）。User Message 拼接行为不变
- **验证方式**: `ExtractionPromptBuilderTest` — golden-file 断言：外置 md 的静态段落 == 原硬编码文案快照；`ExtractionServiceTest` 回归：对固定 mock LLM 响应，抽取产出 JSON 结构（顶层字段名 + entities/knowledgePoints 字段）与重构前一致

### AC-2 · 实体分类单一来源（消除漂移）

- **Given** `EntityType` 枚举为实体分类的唯一来源，prompt 的实体类型段与 validator 的合法实体类型集合均由 `EntityType.values()` 派生，代码中不存在第二份硬编码实体分类清单
- **When** 在 `EntityType` 枚举新增一个分类（作为可插拔性验证，具体值由 TASK 定），不修改 prompt 文案、不修改 validator 的硬编码集合
- **Then** 该新分类自动出现在 System Prompt 的实体类型段；validator 接受该 entityType 值（不再因"不在合法枚举"拒绝）
- **验证方式**: `ExtractionPromptBuilderTest` — 断言 `buildSystemPrompt()` 输出含 `EntityType.values()` 每个枚举的 value，且不含枚举外的实体类型词；`ExtractionValidatorTest` — 断言 validator 合法集合 == `EntityType.values()` 的 value 集合；新增分类后两个测试无需改动即通过

### AC-3 · LLM 关系类型可插拔（消除 switch）

- **Given** LLM 抽取关系类型通过注册机制路由到对应边工厂，`ExtractionService.convertToDomain` 中不存在按关系类型字符串分支的 switch/if-else
- **When** 新增一个 LLM 抽取关系类型（在关系类型注册处声明其值 + 注册边工厂），不修改 `convertToDomain` 的分支代码
- **Then** LLM 返回该新关系类型的 `entityRelation` 时，系统自动路由到注册的边工厂生成对应 `GraphEdge`，校验通过，边被加入抽取结果
- **验证方式**: `ExtractionServiceTest` — 注册一个测试关系类型 + 测试边工厂，传入含该 type 的 `RawEntityRelation`，断言生成的 `GraphEdge` 类型正确；ArchUnit 或代码审查断言 `convertToDomain` 无关系类型 switch

### AC-4 · 顶层节点类型可扩展路径

- **Given** 存在文档化的顶层节点类型扩展路径：新增一个顶层节点类型时，其 prompt schema 段、Raw POJO 反序列化、校验、转换四环节均可通过注册接入，不硬编码于 `ExtractionService` 核心分支
- **When** 按该路径新增一个最小测试用顶层节点类型（仅验证可扩展性，不投入生产业务），走完 prompt 段 → 反序列化 → 校验 → 转换 链路
- **Then** 该新类型的 JSON schema 段出现在 System Prompt；LLM 输出含该段时能被反序列化为对应 Raw POJO、通过校验、转换为对应领域节点，且全程未修改 `ExtractionService` 核心分支代码
- **验证方式**: `ExtractionServiceTest` — 注册一个测试用顶层节点类型描述符（TestNode），构造含其段的 mock LLM 响应，断言反序列化 + 校验 + 转换产出 TestNode 领域实例；扩展路径写入 DESIGN 并在 REVIEW 核对

### AC-5 · few-shot 按学科切换 + 默认回退

- **Given** 配置了至少两套 few-shot 示例：数学学科示例 + 默认示例（兜底）
- **When** 对 `subject=数学` 的文档构建 System Prompt / 对未配置学科（如"物理"，本期未配）的文档构建 System Prompt
- **Then** 数学文档的 System Prompt 含数学 few-shot 段；未配置学科回退到默认 few-shot 段；System Prompt 其余段落（角色/类型/输出规则）两者一致
- **验证方式**: `ExtractionPromptBuilderTest` — `buildSystemPrompt(subject=数学)` 输出含数学示例标识且不含默认示例标识；`buildSystemPrompt(subject=物理)` 输出含默认示例标识；两次输出的非 few-shot 段落 diff 为空

### AC-6 · 不回归（既有 AC-1 / AC-3 行为等价）

- **Given** 现有抽取测试套件（`ExtractionValidatorTest` / `ExtractionJsonParserTest` / `ConstructionServiceTest`）覆盖了 `knowledge-graph-extraction` 的 AC-1（端到端抽取）与 AC-3（JSON Schema 校验）
- **When** 重构后运行 `mvn test`
- **Then** 全量测试通过；对固定输入的校验行为与重构前等价（合法 JSON 通过、缺字段/枚举越界/非 JSON 三类非法输入仍抛 `BusinessException(A0010)` 且 message 含字段名）
- **验证方式**: `mvn test -pl . -Dtest='ExtractionValidatorTest,ExtractionJsonParserTest,ConstructionServiceTest'` 全绿；新增一个回归测试对固定 JSON 输入断言校验结果与重构前快照一致

---

## 范围切分

### v1（本次必做）

- System Prompt 从 Java 文本块外置到 `classpath:/prompts/*.md`（对齐 ADR-011），User Message 行为不变
- 实体分类层可插拔：`EntityType` 枚举为唯一来源，prompt 实体类型段 + validator 合法集合均由枚举派生，消除硬编码清单
- LLM 关系类型层可插拔：注册工厂替代 `convertToDomain` 的 switch，新增关系类型不改分支代码
- 顶层节点类型可扩展路径：注册抽象覆盖 prompt schema 段 + Raw POJO 反序列化 + 校验 + 转换四环节，以 1 个最小测试用新类型验证（AC-4）
- few-shot 按学科切换：本期交付**数学 + 默认**两套，未配置学科回退默认
- 不回归：AC-1 / AC-3 行为等价（AC-6）
- 错误处理：prompt 模板加载失败、类型注册冲突复用既有错误码体系（`C0001` / `A0010`）并记日志

### v2（下一轮考虑，不本次）

- 领域自适应 few-shot 自动选取：按文档内容语义匹配最贴合的示例（ADR-003 提及），替代本期按 subject 显式映射
- 实际新增业务节点类型：TheoremNode / QuestionNode 等具体生产类型（本期只交付骨架 + 测试用类型）
- 物理化学等更多学科 few-shot 示例库（本期仅数学 + 默认）
- few-shot / prompt 版本化与 A/B 抽取质量对比
- 抽取 prompt 的在线热更新（免重启）—— 若 v1 运行期配置方向在 DESIGN 落地则可提前

### out（永远不做）

- 切换到 LLM JSON Mode / Function Calling —— ADR-003 已否决，待 Spring AI GA 后另开 change 再议
- 重构为多步 Pipeline 抽取（先抽 Entity → 再对齐 KP → ...）—— ADR-003 已否决，仍是单次调用
- 成绩事件图谱（Student / Exam / TESTED / MASTERS / ATTENDED）的类型可插拔 —— 非 LLM 抽取链路，属结构化导入，不在本 change 范围

---

## 非功能性需求

- **性能**: prompt 外置本身不增加 LLM 调用次数（仍单次）也不增加 token 消耗（类型段由枚举生成须保证不比原硬编码文案膨胀 >10%）；md 文件 IO 在服务启动或首次调用时加载并缓存，禁止每次抽取读盘
- **可访问性**: 无
- **安全**: prompt 模板仅从 classpath 加载，不接受外部用户输入的文件路径（防路径遍历）；拼入 prompt 的 `subject` / `docName` 来自受信文档元数据，prompt 注入风险与现状一致，不新增攻击面
- **兼容性**: Java 17 + Spring Boot 3.3.x + Spring Data Neo4j 7.x（既有栈）；对外 REST API 与 LLM JSON 契约向后兼容
- **可观测性**: prompt 模板缺失 / 加载失败、类型注册键冲突需记 ERROR 日志并抛可读异常（复用 `C0001` / `A0010`）；prompt 加载耗时与缓存命中记 DEBUG 日志

## 依赖与假设

- 依赖既有 `PromptTemplateService` 范式与 Spring `ResourceLoader`（已存在，无需新增依赖）
- 假设 `EntityType` 现有元数据（value / displayName / description）足以生成与原硬编码文案语义等价的 prompt 实体类型段；若不足，DESIGN 决定是否补充枚举字段
- 假设现有抽取测试套件足以覆盖回归（AC-6）；若覆盖率不足，TEST 阶段补齐
- 假设本期交付**数学 + 默认**两套 few-shot 即满足 AC-5，其余学科占位留 v2（待用户确认）
- 假设 AC-4 的顶层节点类型注册抽象采用运行期可注册（Spring bean）形式，以便单测注册 TestNode 走完链路；若 DESIGN 选枚举静态注册则 AC-4 验证方式调整为"加枚举 + 注册工厂"演示

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。
