# TASK: 抽取提示词外置 + 节点/边类型可插拔

- **Change ID**: `extraction-prompt-pluggable`
- **关联**: `@.specs/extraction-prompt-pluggable/REQUIREMENT.md`、`@.specs/extraction-prompt-pluggable/DESIGN.md`、`@.specs/CONTEXT.md`

> Artifact Preflight：REQUIREMENT.md ✅、DESIGN.md ✅、UI-DESIGN.md N/A（后端）✅、CONTEXT.md ✅。
> 设计决策引用：D1 prompt 外置 / D2 实体枚举驱动 / D3 关系枚举+工厂 / D4 节点 handler 注册表 / D5 few-shot 分域。

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T04[P], T05[P]
Wave 2 (parallel): T06[P] (dep T02), T07[P] (dep T02), T08[P] (dep T03)
Wave 3 (parallel): T09[P] (dep T03, T04, T06), T10[P] (dep T01, T02, T03, T05)
Wave 4:            T11   (dep T04, T08, T09, T10)
Wave 5:            T12   (dep T09, T10, T11)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>EntityType 枚举增 example 字段（D2）</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityType.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityType.java
    src/test/java/com/graphnexus/infrastructure/neo4j/node/EntityTypeTest.java
  </write_files>
  <action>
    为 EntityType 枚举新增 example 字段（String），5 个现有值（DEFINITION/FORMULA/CONCEPT/EXAMPLE/SOLUTION）各填入与现有 ExtractionPromptBuilder 硬编码文案中"（如 ...）"语义一致的示例。
    保留现有 value/displayName/description 字段不动。见 D2。
    不改 ExtractionPromptBuilder（后续 T10 才消费 example）。
  </action>
  <verify>mvn test -Dtest=EntityTypeTest</verify>
  <done>5 个枚举值均含 example 字段且非空；AC-2 的实体分类单一来源前置就绪</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>新增 EntityRelationType 枚举（D3）</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionValidator.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/EntityRelationType.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/EntityRelationTypeTest.java
  </write_files>
  <action>
    新建 EntityRelationType 枚举（extract/registry 包），含 DERIVES/CONTAINS/REFERENCES 三个值，每个带 value + description（description 与现有 ExtractionPromptBuilder 硬编码关系定义语义一致，见 D3）。
    风格对齐既有 EntityType（value/displayName/description 三字段 + fromValue 查找）。
    不改 EdgeType（EdgeType 是 Neo4j 全量边注册，本枚举是 LLM 可抽取关系子集契约，分工见 ADR-023）。
  </action>
  <verify>mvn test -Dtest=EntityRelationTypeTest</verify>
  <done>3 个关系类型枚举值含 description；AC-3 关系契约源就绪</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>新增 ExtractionNodeHandler 接口 + 注册表（D4）</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionRawResult.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandler.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandlerRegistry.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandlerRegistryTest.java
  </write_files>
  <action>
    定义 ExtractionNodeHandler 接口（泛型 <R, N extends GraphNode>）：sectionKey()（JSON 段 key）、promptSchema()（prompt schema 段文本）、rawType()（Class<R>）、validate(List<R> raw, ExtractionRawResult context)、convert(List<R> raw, String documentId) 返回 List<N>。
    ExtractionNodeHandlerRegistry：Spring 组件，构造期注入 List<ExtractionNodeHandler>（生产为空）按 sectionKey 索引入可变内部 Map；提供 all()、findByKey()、register(handler)（供测试手动注册，避免 @Component 测试 bean 污染其他 @SpringBootTest）。空注册表行为正常（返回空列表）。见 D4。
    不实现具体 handler（T08 的 TestNodeHandler 是首个实现）。
  </action>
  <verify>mvn test -Dtest=ExtractionNodeHandlerRegistryTest</verify>
  <done>接口 + 注册表就绪；空注册表查询返回空；AC-4 扩展点骨架就绪</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>ExtractionRawResult 增 @JsonAnySetter 扩展段（D4）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionRawResult.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionJsonParser.java
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionJsonParserTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionRawResult.java
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionJsonParserTest.java
  </write_files>
  <action>
    为 ExtractionRawResult 新增 extensionSections 字段（Map<String, Object>），用 @JsonAnySetter 收集所有未映射到现有强类型字段的 JSON key。
    验证 ExtractionJsonParser 无需改动——既有 lenientMapper 已配 FAIL_ON_UNKNOWN_PROPERTIES=false，@JsonAnySetter 与之兼容（未知 key 从"忽略"变为"捕获"）。
    在 ExtractionJsonParserTest 增用例：含未知 key（如 "testNodes"）的 JSON 解析后 extensionSections 含该 key；已知 key 仍进强类型字段、extensionSections 不含。见 D4。
  </action>
  <verify>mvn test -Dtest=ExtractionJsonParserTest</verify>
  <done>未知 JSON key 被捕获到 extensionSections；已知 key 行为不变；AC-4 反序列化前置就绪</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>创建 4 个 prompt md 文件（D1/D5）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
    src/main/resources/prompts/student-diagnosis-system.md
  </read_files>
  <write_files>
    src/main/resources/prompts/extraction-system.md
    src/main/resources/prompts/extraction-user.md
    src/main/resources/prompts/extraction-fewshot-math.md
    src/main/resources/prompts/extraction-fewshot-default.md
  </write_files>
  <action>
    从现有 ExtractionPromptBuilder 硬编码文本块语义等价抽取为 md：
    - extraction-system.md：静态段落（角色设定、知识点/分类/前置依赖说明、输出规则、subject 命名规范）保留原文；实体类型段、关系类型段、扩展节点段、few-shot 段替换为占位符 {{entityTypesSection}}、{{relationTypesSection}}、{{extensionNodeSections}}、{{fewShotSection}}。
    - extraction-user.md：{{docName}}/{{pageCount}}/{{subject}}/{{textContent}} 占位符（对齐 ADR-011 {{var}} 约定）。
    - extraction-fewshot-math.md：现有二次函数 few-shot 示例原文。
    - extraction-fewshot-default.md：v1 与 math 相同内容（兜底；后续可分域替换）。
    参考 student-diagnosis-system.md 的 md 风格。占位符名须与 T10 装配逻辑一致。见 D1/D5。
  </action>
  <verify>test -f src/main/resources/prompts/extraction-system.md && grep -q '{{entityTypesSection}}' src/main/resources/prompts/extraction-system.md && grep -q '{{fewShotSection}}' src/main/resources/prompts/extraction-system.md</verify>
  <done>4 个 md 文件存在；system.md 含 4 个占位符；user.md 含 4 个占位符；AC-1 外置载体就绪</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>ExtractionEdgeFactory 接口 + 注册表 + 3 工厂实现（D3）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/EntityRelationType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/DerivesEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/ContainsEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/ReferencesEdge.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionEdgeFactory.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionEdgeFactoryRegistry.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/DerivesEdgeFactory.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ContainsEdgeFactory.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ReferencesEdgeFactory.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionEdgeFactoryRegistryTest.java
  </write_files>
  <action>
    定义 ExtractionEdgeFactory 接口：relationType()（返回 EntityRelationType）、create(String srcId, String tgtId, String desc) 返回 GraphEdge。
    ExtractionEdgeFactoryRegistry：Spring 组件，注入 List<ExtractionEdgeFactory>，按 relationType 索引；提供 create(type, src, dst, desc)。
    3 个 @Component 工厂实现：DerivesEdgeFactory→new DerivesEdge、ContainsEdgeFactory→new ContainsEdge、ReferencesEdgeFactory→new ReferencesEdge（沿用既有边类，只读引用，不改边类）。
    见 D3。本任务不修改 ExtractionService（T09 才接入）。
  </action>
  <verify>mvn test -Dtest=ExtractionEdgeFactoryRegistryTest</verify>
  <done>3 个关系类型均注册工厂；registry.create 按 type 路由到对应边类；AC-3 工厂机制就绪</done>
  <depends_on>T02</depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>ExtractionValidator 关系合法集从枚举派生（D3）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionValidator.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/EntityRelationType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityType.java
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionValidatorTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionValidator.java
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionValidatorTest.java
  </write_files>
  <action>
    将 ExtractionValidator 的 VALID_RELATION_TYPES 从硬编码 Set.of("DERIVES","CONTAINS","REFERENCES") 改为从 EntityRelationType.values() 派生（消除第二处硬编码）。
    实体合法集 VALID_ENTITY_TYPES 已从 EntityType 派生，确认保持不变。
    扩展段校验不在本任务（由 T09 的 handler.validate 负责）。
    更新 ExtractionValidatorTest：断言关系合法集 == EntityRelationType.values()；既有合法/非法用例保持通过。见 D3。
  </action>
  <verify>mvn test -Dtest=ExtractionValidatorTest</verify>
  <done>关系合法集由枚举派生；既有校验行为不变；AC-2/AC-3 校验侧单一来源达成</done>
  <depends_on>T02</depends_on>
</task>

<task id="T08" parallel="true" status="done">
  <name>TestNode + TestNodeHandler 测试用扩展节点（D4）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandler.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandlerRegistry.java
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionRawResult.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/TestNode.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/TestNodeRaw.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/TestNodeHandler.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/TestNodeHandlerTest.java
  </write_files>
  <action>
    测试专用（src/test 下）：TestNodeRaw（含 name/originalText 的简单 POJO）、TestNode（extends GraphNode 的最小子类，无需 @Node 持久化）、TestNodeHandler implements ExtractionNodeHandler<TestNodeRaw, TestNode>（sectionKey="testNodes"、promptSchema 返回测试 schema 文本、rawType=TestNodeRaw、validate 校验 name 非空、convert 产出 TestNode 列表）。
    TestNodeHandler 为普通类（不加 @Component，避免污染其他 @SpringBootTest）；由 T11 手动 registry.register() 激活。
    单测断言 handler 各方法行为 + 手动注册到 registry 后可按 key 查到。见 D4。
  </action>
  <verify>mvn test -Dtest=TestNodeHandlerTest</verify>
  <done>TestNodeHandler 实现完整可手动注册；AC-4 的扩展类型演示载体就绪</done>
  <depends_on>T03</depends_on>
</task>

<task id="T09" parallel="true" status="done">
  <name>ExtractionService.convertToDomain 关系工厂化 + 扩展节点循环（D3/D4）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionEdgeFactoryRegistry.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandlerRegistry.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandler.java
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionRawResult.java
    src/test/java/com/graphnexus/application/graph/construction/service/ConstructionServiceTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionServiceTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/ConstructionServiceTest.java
  </write_files>
  <action>
    重构 convertToDomain：
    ① 关系边：删除 switch(rer.getType()) 分支，改为 edgeFactoryRegistry.create(type, srcId, tgtId, desc) 查询路由（见 D3）。
    ② 扩展节点：注入 ExtractionNodeHandlerRegistry，遍历 raw.extensionSections，对每个注册 handler：用 ObjectMapper.convertValue 把 Map 段转 rawType → handler.validate → handler.convert，收集到 ExtractionResult 新增的 extensionNodes 字段（List<GraphNode> 或 Map<String,List<GraphNode>>）。见 D4。
    ③ ExtractionResult record 增 extensionNodes 字段。
    红线：现有 Entity/KP/Category 三类的转换逻辑块保持硬编码不动（DESIGN 禁动清单）；只替换关系 switch + 新增扩展循环。
    新增 ExtractionServiceTest：数据驱动遍历 EntityRelationType × 注册工厂，断言每个关系类型路由到正确边类（无 switch）；扩展循环用测试内 inline stub handler（非 TestNode，避免依赖 T08）断言机制产出节点。
    确保 ConstructionServiceTest 既有用例仍通过（行为等价）。
  </action>
  <verify>mvn test -Dtest=ExtractionServiceTest,ConstructionServiceTest</verify>
  <done>convertToDomain 无关系 switch；扩展节点经 handler 循环产出；现有 3 类转换不变；AC-3/AC-4 转换侧达成</done>
  <depends_on>T03, T04, T06</depends_on>
</task>

<task id="T10" parallel="true" status="done">
  <name>重写 ExtractionPromptBuilder：md 加载 + 段落装配 + few-shot 分域（D1/D2/D3/D4/D5）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/EntityRelationType.java
    src/main/java/com/graphnexus/application/graph/construction/extract/registry/ExtractionNodeHandlerRegistry.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityType.java
    src/main/resources/prompts/extraction-system.md
    src/main/resources/prompts/extraction-user.md
    src/main/resources/prompts/extraction-fewshot-math.md
    src/main/resources/prompts/extraction-fewshot-default.md
    src/main/java/com/graphnexus/application/query/prompt/service/PromptTemplateService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilderTest.java
  </write_files>
  <action>
    重写 ExtractionPromptBuilder（沿用 ADR-011 约定，自建轻量 loader，不改 PromptTemplateService——见 D1）：
    ① buildSystemPrompt(subject)：ResourceLoader 加载 extraction-system.md；装配占位符：
       - {{entityTypesSection}} ← 遍历 EntityType.values() 生成 "- {value}：{displayName}（如"{example}"）"（见 D2）
       - {{relationTypesSection}} ← 遍历 EntityRelationType.values() 生成 "- {value}：{description}"（见 D3）
       - {{extensionNodeSections}} ← 遍历 NodeHandlerRegistry.all() 拼接各 handler.promptSchema()（生产空注册表→空串，见 D4）
       - {{fewShotSection}} ← 按 subject 选 extraction-fewshot-{subjectKey}.md，未命中回退 default（见 D5）
    ② buildUserMessage：加载 extraction-user.md，{{var}} 替换 docName/pageCount/subject/textContent。
    ③ md 启动期/首次加载缓存，禁止每次读盘（非功能-性能）。
    ExtractionPromptBuilderTest：golden-file 断言静态段（角色/输出规则/subject 规范）== 原硬编码快照；实体段含全部 EntityType value；关系段含全部 EntityRelationType value；subject=数学→含 math few-shot 标识、subject=物理→含 default 标识且非 few-shot 段 diff 为空。
  </action>
  <verify>mvn test -Dtest=ExtractionPromptBuilderTest</verify>
  <done>prompt 从 md 加载并按枚举/注册表装配段；few-shot 按学科切换；静态段语义等价；AC-1/AC-2/AC-5 达成</done>
  <depends_on>T01, T02, T03, T05</depends_on>
</task>

<task id="T11" parallel="false" status="done">
  <name>AC-4 集成测试：TestNode 端到端扩展链路</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/TestNodeHandler.java
    src/test/java/com/graphnexus/application/graph/construction/extract/registry/TestNode.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionNodeExtensionIntegrationTest.java
  </write_files>
  <action>
    端到端集成测试（测试上下文注册 TestNodeHandler）：
    ① buildSystemPrompt 输出含 TestNode 的 promptSchema 段（扩展段非空）。
    ② 构造含 "testNodes" 段的 mock LLM JSON 响应 → ExtractionJsonParser 解析 → extensionSections 含 testNodes。
    ③ ExtractionValidator 通过（已知段合法）+ convertToDomain 的 handler 循环：validate 通过 + convert 产出 TestNode 进入 ExtractionResult.extensionNodes。
    ④ 断言全程未修改 convertToDomain 的核心分支（TestNode 由 handler 循环处理，非新分支）。
    对应 AC-4。
  </action>
  <verify>mvn test -Dtest=ExtractionNodeExtensionIntegrationTest</verify>
  <done>TestNode 走完 prompt段→反序列化→校验→转换链路产出领域节点；AC-4 达成</done>
  <depends_on>T04, T08, T09, T10</depends_on>
</task>

<task id="T12" parallel="false" status="done">
  <name>AC-1/AC-6 回归：固定输入快照 + 全量测试</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionPromptBuilder.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionValidator.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionJsonParser.java
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionValidatorTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/graph/construction/extract/ExtractionRegressionTest.java
  </write_files>
  <action>
    最终回归门：
    ① ExtractionRegressionTest：对固定合法 JSON 输入断言校验通过 + 解析字段齐全；对 3 类非法输入（缺 name / entityType 枚举越界 / 非 JSON）断言抛 BusinessException(A0010) 且 message 含字段名——行为与重构前等价（AC-3 不回归）。
    ② 对固定 mock LLM 响应断言 convertToDomain 产出的 entities/knowledgePoints/categories/edges 字段结构与重构前等价（AC-1 不回归）。
    ③ 运行全量测试套件确认全绿。
  </action>
  <verify>mvn test</verify>
  <done>固定输入校验/解析/转换快照等价；mvn test 全绿；AC-1/AC-6 达成，全 change 验收线闭合</done>
  <depends_on>T09, T10, T11</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中（同时只允许一个非 [P] 任务为此状态）
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞（必须在下方「阻塞日志」记录）

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
