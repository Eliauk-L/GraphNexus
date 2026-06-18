# TASK: 知识图谱抽取 — LLM 驱动的文档实体/知识点/关系识别

- **Change ID**: `knowledge-graph-extraction`
- **关联**: `@.specs/knowledge-graph-extraction/REQUIREMENT.md`、`@.specs/knowledge-graph-extraction/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P]                              ← 基础设施层，互不冲突
Wave 2 (parallel): T04[P], T05[P], T06[P]                              ← 图模型层，全部依赖 T02
Wave 3:            T07 (depends on T03, T04, T05)                      ← 抽取核心逻辑
                   T08 (depends on T06, T07)                            ← 业务编排层
Wave 4:            T09 (depends on T08)                                 ← API 层
Wave 5 (parallel): T10[P], T11[P] (depends on T08/T09)                 ← 测试层
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。Wave 3 内两任务串行（T08 依赖 T07）。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>ErrorCode 新增图谱抽取相关枚举值</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    .specs/CONTEXT.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </write_files>
  <action>
    在既有 ErrorCode 枚举中新增 3 个图谱抽取相关错误码（见 DESIGN § D6）：
    - A0008: 文档文本为空，无法抽取 → 400 BAD_REQUEST, tip="文档文本内容为空，无法进行图谱抽取"
    - A0009: 文档状态不允许抽取 → 400 BAD_REQUEST, tip="文档状态不允许抽取，请先完成文档解析"
    - A0010: LLM 抽取结果校验失败 → 400 BAD_REQUEST, tip="LLM 返回结果格式不符合预期，请稍后重试"

    C0001 已在既存代码中存在（"外部服务调用失败"），LLM 调用失败直接复用 C0001，不新增。
    只新增枚举值，不修改既有枚举常量和其他类。
  </action>
  <verify>mvn compile -q</verify>
  <done>ErrorCode 枚举新增 A0008/A0009/A0010 三个值，编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>图节点/边抽象基类 + 类型注册枚举</name>
  <read_files>
    .specs/adr/002-graph-node-edge-abstraction.md
    .specs/CONTEXT.md
    src/main/java/com/graphnexus/infrastructure/neo4j/package-info.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
  </write_files>
  <action>
    创建图模型的抽象基础设施（见 ADR-002）：

    1. GraphNode.java（抽象基类，不标注 @Node）：
       - @Id String id（UUID 生成）
       - String nodeType（节点类型标识，对应 NodeType 枚举的 label，如 "Entity"/"KnowledgePoint"）
       - String documentId（关联源文档，所有节点通用）
       - LocalDateTime createdAt
       - Map<String, Object> properties（扩展属性容器）

    2. GraphEdge.java（抽象基类，不标注 @RelationshipProperties）：
       - String sourceNodeId
       - String targetNodeId
       - String edgeType（关系类型字符串）
       - LocalDateTime createdAt（关系创建时间）
       - Map<String, Object> properties

    3. NodeType.java（枚举注册中心）：
       - ENTITY("Entity", EntityNode.class)
       - KNOWLEDGE_POINT("KnowledgePoint", KnowledgePointNode.class)
       - KNOWLEDGE_CATEGORY("KnowledgeCategory", KnowledgeCategoryNode.class)
       - DOCUMENT("Document", DocumentNode.class)
       每个枚举值包含 label 字符串和对应的 Java 类引用

    4. EdgeType.java（枚举注册中心）：
       - EXTRACTS / REFERENCES / DERIVES / CONTAINS / ALIGNED_TO / BELONGS_TO / CHILD_OF / PREREQUISITE_OF
       每个枚举值包含 relationshipType 字符串和方向（OUTGOING/INCOMING）

    注意：T02 只定义抽象基类和枚举，不创建具体子类。构造器注入风格（@RequiredArgsConstructor）。
  </action>
  <verify>mvn compile -q</verify>
  <done>GraphNode/GraphEdge 抽象基类 + NodeType/EdgeType 枚举编译通过，子类可继承扩展</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>LlmGateway 接口 + SpringAiLlmGateway 实现 + yml 配置</name>
  <read_files>
    .specs/adr/004-llm-gateway-interface.md
    src/main/resources/application-dev.yml
    src/main/resources/application.yml
    src/main/java/com/graphnexus/application/llmgateway/package-info.java
    src/main/java/com/graphnexus/infrastructure/llm/package-info.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    pom.xml
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/llmgateway/service/LlmGateway.java
    src/main/java/com/graphnexus/infrastructure/llm/client/SpringAiLlmGateway.java
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
  </write_files>
  <action>
    落地 LLM 最小可用调用链路（见 ADR-004）：

    1. LlmGateway.java（L2 接口）：
       - 单方法：String chat(String systemPrompt, String userMessage)
       - 抛出 BusinessException（复用 ErrorCode.C0001）
       - 位置：application/llmgateway/service/

    2. SpringAiLlmGateway.java（L3 实现）：
       - 注入 Spring AI ChatClient（ChatClient.Builder）
       - 读取 spring.ai.openai.* 配置（base-url 指向 DeepSeek API: https://api.deepseek.com，api-key 从环境变量或 yml 读取）
       - chat() 方法：构建 Prompt → 调用 ChatClient.call() → 提取 getResult().getOutput().getContent()
       - 异常处理：网络超时/API 4xx/5xx/空响应 → 包装为 BusinessException(ErrorCode.C0001)
       - 使用构造器注入 + @RequiredArgsConstructor
       - 位置：infrastructure/llm/client/

    3. application.yml 修改：
       - 移除 spring.autoconfigure.exclude 中的 Neo4j 相关项（spring-boot-autoconfigure-neo4j 等），激活 Neo4j 自动配置
       - 注意：仅移除 Neo4j 和 Spring AI 相关的 exclude，保留 Redis/RabbitMQ/Security 的 exclude

    4. application-dev.yml 修改：
       - 新增 spring.ai.openai 配置段：
         * spring.ai.openai.base-url: https://api.deepseek.com
         * spring.ai.openai.api-key: ${DEEPSEEK_API_KEY:}
         * spring.ai.openai.chat.options.model: deepseek-chat
         * spring.ai.openai.chat.options.temperature: 0.3
         * spring.ai.openai.chat.options.max-tokens: 4096
       - 保留既有的 claude.* 配置段不动（预留），但本 change 不使用
  </action>
  <verify>mvn compile -q</verify>
  <done>LlmGateway 接口 + SpringAiLlmGateway 实现编译通过；yml 中 Neo4j exclude 已移除、Spring AI OpenAI 配置段就位</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>4 类 Neo4j 图节点实体</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
    .specs/adr/002-graph-node-edge-abstraction.md
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentDO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/DocumentNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgeCategoryNode.java
  </write_files>
  <action>
    创建 4 个具体图节点类，继承 GraphNode，各自标注 @Node（见 DESIGN § 2.4、ADR-002）：

    1. DocumentNode.java — @Node("Document")：
       - Long mysqlId（对应 MySQL document 表的 id）
       - String name（文档名称）
       - String subject（学科）
       - Integer pageCount（页数）
       - nodeType 固定为 "Document"

    2. EntityNode.java — @Node("Entity")：
       - String entityType（枚举：DEFINITION/FORMULA/CONCEPT/EXAMPLE/SOLUTION）
       - String name（简洁名称）
       - String originalText（原文片段）
       - Integer pageNumber（所在页码）
       - Map<String, Object> metadata（可选扩展元数据）
       - nodeType 固定为 "Entity"

    3. KnowledgePointNode.java — @Node("KnowledgePoint")：
       - String name（标准知识点名称）
       - String description（一句话说明）
       - String subject（学科，继承自文档）
       - String gradeLevel（年级/学段，如"初中"）
       - nodeType 固定为 "KnowledgePoint"

    4. KnowledgeCategoryNode.java — @Node("KnowledgeCategory")：
       - String name（分类名称，如"二次函数"）
       - Integer level（层级深度，1=根 2=分支...）
       - String parentName（父分类名称，用于 LLM 输出中引用；CHILD_OF 边在 T05 中单独表达）
       - nodeType 固定为 "KnowledgeCategory"

    每个子类构造器中自动设置 nodeType（如 DocumentNode 构造器设 nodeType = NodeType.DOCUMENT.getLabel()）。
    所有类使用 Lombok @Getter/@Setter + @RequiredArgsConstructor + 构造器注入风格。
  </action>
  <verify>mvn compile -q</verify>
  <done>4 个 @Node 子类编译通过；字段与 DESIGN § 2.4 一致；EntityNode.entityType 支持 5 种枚举值</done>
  <depends_on>T02</depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>6 类 Neo4j 图关系边实体</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgeCategoryNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/DocumentNode.java
    .specs/adr/002-graph-node-edge-abstraction.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/ExtractsEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/ReferencesEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/AlignedToEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/BelongsToEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/ChildOfEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/PrerequisiteEdge.java
  </write_files>
  <action>
    创建 6 个具体图边类，继承 GraphEdge，各自标注 @RelationshipProperties（见 DESIGN § 2.4）：

    1. ExtractsEdge.java — DocumentNode → EntityNode：
       - edgeType 固定为 "EXTRACTS"

    2. ReferencesEdge.java — EntityNode → EntityNode：
       - edgeType 为 "REFERENCES" / "DERIVES" / "CONTAINS" 之一
       - String referenceType（DERIVES/CONTAINS/REFERENCES）
       - String description（关系说明）

       **注**：DERIVES 和 CONTAINS 不单独建类，统一用 ReferencesEdge + referenceType 字段枚举区分，减少类数量

    3. AlignedToEdge.java — EntityNode → KnowledgePointNode：
       - edgeType 固定为 "ALIGNED_TO"

    4. BelongsToEdge.java — KnowledgePointNode → KnowledgeCategoryNode：
       - edgeType 固定为 "BELONGS_TO"

    5. ChildOfEdge.java — KnowledgeCategoryNode → KnowledgeCategoryNode：
       - edgeType 固定为 "CHILD_OF"

    6. PrerequisiteEdge.java — KnowledgePointNode → KnowledgePointNode：
       - edgeType 固定为 "PREREQUISITE_OF"
       - Double strength（依赖强度 0-1，默认 0.5）
       - String description（为什么 B 依赖 A）

    注意：DERIVES/CONTAINS 合并到 ReferencesEdge（用 referenceType 字段区分），
    因此实际创建 6 个文件（而非 8 个），覆盖所有 8 种逻辑边类型。

    所有类使用 Lombok @Getter/@Setter + 构造器注入风格。作者 Jay。
  </action>
  <verify>mvn compile -q</verify>
  <done>6 个边类编译通过；DERIVES/CONTAINS 通过 ReferencesEdge.referenceType 区分；覆盖全部 8 种逻辑边类型</done>
  <depends_on>T02</depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>GraphNodeRepository 通用图仓库</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java
    .specs/adr/002-graph-node-edge-abstraction.md
    pom.xml
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
  </write_files>
  <action>
    创建通用图仓库 GraphNodeRepository.java（见 ADR-002 伪代码）：

    注入 Neo4jTemplate（Spring Data Neo4j 提供）。

    方法清单：
    - <T extends GraphNode> T save(T node) — 保存任意 GraphNode 子类（委托 Neo4jTemplate.save）
    - <T extends GraphNode> List<T> saveAll(List<T> nodes) — 批量保存
    - void saveEdge(GraphEdge edge) — 通过 Cypher MERGE 保存边
    - void saveAllEdges(List<GraphEdge> edges) — 批量保存边
    - List<GraphNode> findByDocumentId(String documentId) — 按 documentId 查询所有节点（Cypher: MATCH (n) WHERE n.documentId = $docId RETURN n）
    - List<GraphEdge> findEdgesByDocumentId(String documentId) — 按文档 ID 查询所有关联边（通过 sourceNodeId 或 targetNodeId 关联到该文档的节点）
    - void deleteByDocumentId(String documentId) — 删除该文档的所有节点和边（Cypher: MATCH (n {documentId: $docId}) DETACH DELETE n）

    使用 Cypher 模板执行边操作（SDN 7.x 的 Neo4jTemplate 支持 String query + Map params）。
  </action>
  <verify>mvn compile -q</verify>
  <done>GraphNodeRepository 编译通过；提供 save/saveAll/saveEdge/findByDocumentId/deleteByDocumentId 5 类方法</done>
  <depends_on>T02</depends_on>
</task>

<task id="T07" parallel="false" status="done">
  <name>ExtractionService — LLM Prompt 模板 + JSON Schema + 校验</name>
  <read_files>
    .specs/adr/003-llm-extraction-prompt-strategy.md
    .specs/knowledge-graph-extraction/DESIGN.md
    src/main/java/com/graphnexus/application/llmgateway/service/LlmGateway.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EntityNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgeCategoryNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/EdgeType.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/extraction/ExtractionService.java
    src/main/java/com/graphnexus/application/graph/extraction/ExtractionPromptBuilder.java
    src/main/java/com/graphnexus/application/graph/extraction/ExtractionRawResult.java
    src/main/java/com/graphnexus/application/graph/extraction/ExtractionValidator.java
  </write_files>
  <action>
    创建抽取核心逻辑包 application/graph/extraction/，包含 4 个文件（见 ADR-003）：

    1. ExtractionPromptBuilder.java — Prompt 构建器：
       - buildSystemPrompt() → 返回 System Prompt 字符串（约 1500 tokens）：
         §1 角色设定（"你是教育领域的知识图谱构建专家"）
         §2 5 种实体类型定义 + 字段说明
         §3 6 种关系边类型定义 + 方向 + 含义
         §4 Few-shot 示例（二次函数的完整抽取 JSON）
         §5 JSON Schema 格式约束
         §6 特殊规则（数学公式 LaTeX、忽略页眉页脚、entityType 严格枚举值、纯 JSON 输出）
       - buildUserMessage(docName, subject, pageCount, textContent) → 拼接 User Message

    2. ExtractionRawResult.java — LLM 输出的 POJO 映射：
       对应 JSON Schema 结构：entities[], knowledgePoints[], categories[], alignments[], entityRelations[], prerequisites[], categoryRelations[]
       使用 Lombok @Data + 无参构造器（Jackson 反序列化需要）

    3. ExtractionValidator.java — 校验器：
       - validate(ExtractionRawResult raw) → 执行 Bean Validation：
         * entityType 枚举值检查（DEFINITION/FORMULA/CONCEPT/EXAMPLE/SOLUTION）
         * referenceType 枚举值检查（DERIVES/CONTAINS/REFERENCES）
         * entityIndex/knowledgePointIndex 不越界（对齐中引用必须存在）
         * name/originalText 非空
       - 校验失败抛 BusinessException(A0010)，message 含具体字段名和违规值

    4. ExtractionService.java — 抽取编排：
       - 注入 LlmGateway + ExtractionPromptBuilder + ExtractionValidator
       - extract(textContent, docName, subject, pageCount) → ExtractionResult：
         a) 调用 ExtractionPromptBuilder 构建 Prompt
         b) 调用 LlmGateway.chat(systemPrompt, userMessage) 获取 LLM 响应
         c) 预处理：去除 ```json ... ``` 包裹（如存在）
         d) Jackson 反序列化为 ExtractionRawResult
         e) 反序列化失败 → 重试一次（Prompt 追加"请严格输出纯 JSON"）
         f) 再次失败 → throw BusinessException(A0010)
         g) 调用 ExtractionValidator.validate(rawResult)
         h) 将 rawResult 转换为领域对象列表（List<EntityNode>, List<KnowledgePointNode>...）
         i) 返回 ExtractionResult（含所有节点和边对象）

    注意：ExtractionService 不负责写 Neo4j——只产出领域对象。写库由 T08 GraphService 负责。
    使用构造器注入 + @RequiredArgsConstructor。作者 Jay。
  </action>
  <verify>mvn compile -q</verify>
  <done>4 个抽取核心类编译通过；Prompt 模板含 6 段 + Few-shot；校验器覆盖 3 种非法场景</done>
  <depends_on>T03, T04, T05</depends_on>
</task>

<task id="T08" parallel="false" status="done">
  <name>GraphService 接口 + 实现（抽取编排 + 子图查询）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/extraction/ExtractionService.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/DocumentNode.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentStatus.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    .specs/knowledge-graph-extraction/DESIGN.md
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/service/GraphService.java
    src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/application/graph/model/ExtractionResultBO.java
    src/main/java/com/graphnexus/application/graph/model/GraphSubgraphBO.java
  </write_files>
  <action>
    创建 L2 图服务层（见 DESIGN § 2.4）：

    1. ExtractionResultBO.java — 抽取结果摘要 BO：
       - int entityCount, knowledgePointCount, categoryCount, edgeCount
       - String documentId

    2. GraphSubgraphBO.java — 子图查询结果 BO：
       - List<GraphNode> nodes
       - List<GraphEdge> edges

    3. GraphService.java — 接口（application/graph/service/）：
       - ExtractionResultBO extract(Long documentId) — 触发抽取
       - GraphSubgraphBO getSubgraph(Long documentId) — 查询子图

    4. GraphServiceImpl.java — 实现（application/graph/service/impl/）：

       extract(Long documentId) 流程：
       a) 通过 DocumentRepository 查询 DocumentDO
       b) 不存在 → throw BusinessException(A0006)（复用既有错误码）
       c) status != COMPLETED → throw BusinessException(A0009)
       d) text_content 为空或纯空白 → throw BusinessException(A0008)
       e) 构建 DocumentNode（从 DocumentDO 转换）
       f) 调用 ExtractionService.extract(textContent, name, subject, pageCount)
       g) 在 @Transactional 内（Neo4j 事务管理器）：
          - GraphNodeRepository.deleteByDocumentId(docId) — 清除旧子图
          - GraphNodeRepository.save(documentNode) — 保存 DocumentNode
          - GraphNodeRepository.saveAll(entities) — 批量保存 EntityNode
          - GraphNodeRepository.saveAll(knowledgePoints) — 批量保存 KnowledgePointNode
          - GraphNodeRepository.saveAll(categories) — 批量保存 KnowledgeCategoryNode
          - GraphNodeRepository.saveAllEdges(allEdges) — 批量保存所有边
       h) 统计节点/边数量 → 构建 ExtractionResultBO → 返回

       getSubgraph(Long documentId) 流程：
       a) 调用 GraphNodeRepository.findByDocumentId(docId) → List<GraphNode>
       b) 调用 GraphNodeRepository.findEdgesByDocumentId(docId) → List<GraphEdge>
       c) 构建 GraphSubgraphBO → 返回

       事务标注：extract() 标注 @Transactional("neo4jTransactionManager")，确保删+写原子。
       查询方法标注 @Transactional(readOnly = true)。

    使用构造器注入 + @RequiredArgsConstructor。作者 Jay。
  </action>
  <verify>mvn compile -q</verify>
  <done>GraphService 接口+实现编译通过；extract() 含 5 种前置校验 + 事务原子覆盖；getSubgraph() 返回节点+边</done>
  <depends_on>T06, T07</depends_on>
</task>

<task id="T09" parallel="false" status="done">
  <name>GraphController + ExtractionResultVO + GraphSubgraphVO</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/service/GraphService.java
    src/main/java/com/graphnexus/application/graph/model/ExtractionResultBO.java
    src/main/java/com/graphnexus/application/graph/model/GraphSubgraphBO.java
    src/main/java/com/graphnexus/common/ApiResponse.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    .specs/knowledge-graph-extraction/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/api/graph/dto/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/GraphSubgraphVO.java
  </write_files>
  <action>
    创建 L1 API 层（见 DESIGN § 2.4、D6）：

    1. ExtractionResultVO.java — 抽取结果响应：
       - int entityCount, knowledgePointCount, categoryCount, edgeCount
       - String documentId
       - static ExtractionResultVO from(ExtractionResultBO bo) 转换方法

    2. GraphSubgraphVO.java — 子图查询响应：
       - List<GraphNodeVO> nodes（每项含 id, labels, properties）
       - List<GraphEdgeVO> edges（每项含 id, type, sourceNodeId, targetNodeId, properties）
       - 内部静态类 GraphNodeVO / GraphEdgeVO（简单 POJO，不暴露 Neo4j 内部类型）
       - static GraphSubgraphVO from(GraphSubgraphBO bo) 转换方法

    3. GraphController.java — REST 控制器（参考 DocumentController 风格）：
       - @RestController + @RequestMapping("/api/v1/graph") + @RequiredArgsConstructor
       - 注入 GraphService（private final + 构造器注入）
       - POST /api/v1/graph/extract/{documentId} → ApiResponse<ExtractionResultVO>
       - GET /api/v1/graph/document/{documentId} → ApiResponse<GraphSubgraphVO>
       - 端点路径与 DESIGN § D6 完全一致
       - GlobalExceptionHandler 自动处理 BusinessException，Controller 不做 try-catch

    作者 Jay，中文注释。
  </action>
  <verify>mvn compile -q</verify>
  <done>GraphController 编译通过；POST extract + GET document 两端点路径与 DESIGN § D6 一致</done>
  <depends_on>T08</depends_on>
</task>

<task id="T10" parallel="true" status="done">
  <name>单元测试 — AC-3 JSON Schema 校验 + AC-5 抽象可扩展 + AC-6 空文本拒绝</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/extraction/ExtractionValidator.java
    src/main/java/com/graphnexus/application/graph/extraction/ExtractionRawResult.java
    src/main/java/com/graphnexus/application/graph/service/GraphService.java
    src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    .specs/knowledge-graph-extraction/REQUIREMENT.md
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/graph/extraction/ExtractionValidatorTest.java
    src/test/java/com/graphnexus/application/graph/service/GraphServiceTest.java
    src/test/java/com/graphnexus/infrastructure/neo4j/node/GraphNodeAbstractionTest.java
  </write_files>
  <action>
    创建 3 个单元测试文件，覆盖对应 AC：

    1. ExtractionValidatorTest.java（AC-3 — JSON Schema 校验）：
       - testExtractionRawResult_Valid_Success：合法 JSON（字段齐全、枚举值合法）→ 校验通过
       - testExtractionRawResult_MissingRequiredField_Exception：缺少必填字段 name → BusinessException(A0010)
       - testExtractionRawResult_InvalidEntityType_Exception：entityType="UNKNOWN" → BusinessException(A0010)
       - testExtractionRawResult_InvalidJson_Exception：非 JSON 字符串 → BusinessException(A0010)
       - testExtractionRawResult_IndexOutOfBound_Exception：entityIndex 越界 → BusinessException(A0010)
       使用 Mockito，mock LlmGateway 返回预定义 JSON 字符串

    2. GraphServiceTest.java（AC-6 — 空文本/未就绪文档拒绝）：
       - testExtractDocumentNotFound_Exception：DocumentRepository 返回 Optional.empty() → BusinessException(A0006)
       - testExtractDocumentNotCompleted_Exception：status=UPLOADED → BusinessException(A0009)
       - testExtractEmptyText_Exception：textContent="" → BusinessException(A0008)
       - testExtractBlankText_Exception：textContent="   \n  " → BusinessException(A0008)
       使用 Mockito mock DocumentRepository + ExtractionService + GraphNodeRepository

    3. GraphNodeAbstractionTest.java（AC-5 — 抽象层可扩展）：
       - 定义测试内部类 TestNode extends GraphNode（@Node("TestLabel")）
       - testNewNodeType_CanBeSaved：GraphNodeRepository.save(testNode) 不抛异常
       - testNewNodeType_CanBeFound：save 后通过 findByDocumentId 能检索到
       - testNewNodeType_RegisteredInEnum：NodeType 枚举可通过 name() 找到对应条目
       使用 @DataNeo4jTest（轻量 Neo4j 测试切片，无需完整应用上下文）

    使用 JUnit 5 + Mockito + AssertJ。测试类命名遵循既有规范。
    注释中文，作者 Jay。
  </action>
  <verify>mvn test -pl . -Dtest="ExtractionValidatorTest,GraphServiceTest,GraphNodeAbstractionTest" -DfailIfNoTests=false</verify>
  <done>10 个单元测试用例全部通过；AC-3（4 tests）/ AC-5（3 tests）/ AC-6（4 tests）覆盖</done>
  <depends_on>T07, T08</depends_on>
</task>

<task id="T11" parallel="true" status="done">
  <name>集成测试 — AC-1 端到端 + AC-2 子图查询 + AC-4 LLM 调用 + AC-7 失败回滚 + AC-8 重复抽取覆盖</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/application/graph/service/GraphService.java
    src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/application/llmgateway/service/LlmGateway.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentRepository.java
    src/main/java/com/graphnexus/common/ApiResponse.java
    .specs/knowledge-graph-extraction/REQUIREMENT.md
    src/main/resources/application-dev.yml
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/api/graph/controller/GraphControllerIntegrationTest.java
  </write_files>
  <action>
    创建集成测试文件 GraphControllerIntegrationTest.java。

    环境策略：podman 已部署全部组件（Neo4j/MySQL/MinIO/Redis/RabbitMQ），
    集成测试使用 @SpringBootTest(webEnvironment = RANDOM_PORT) + @ActiveProfiles("dev")，
    直连 application-dev.yml 中配置的真实服务，不使用 Testcontainers 或 @MockBean。

    **前置条件**（测试执行前需确认）：
    - podman 容器全部运行中
    - MySQL graphnexus 库中存在至少一条 status=COMPLETED 且 text_content 非空的文档记录
    - DeepSeek API Key 已配置（环境变量 DEEPSEEK_API_KEY）
    - Neo4j bolt://localhost:7687 可连接

    测试用例：

    AC-1（端到端抽取 — 含 AC-4 LLM 真实调用）：
    - testExtractGraph_Success：
      → 从 MySQL 查询一条 COMPLETED 文档
      → POST /api/v1/graph/extract/{docId}
      → 断言 HTTP 200 + ExtractionResultVO.entityCount > 0
      → 断言 Neo4j 中存在 DocumentNode/EntityNode/KnowledgePointNode
        （通过 GraphNodeRepository.findByDocumentId 验证）

    AC-2（子图查询）：
    - testGetSubgraph_Success：完成 AC-1 抽取后
      → GET /api/v1/graph/document/{docId}
      → 断言 HTTP 200 + nodes 非空 + edges 非空
      → 断言 nodes 包含不同 label（"Document"/"Entity"/"KnowledgePoint" 等）

    AC-4（LLM 真实调用）：
      → 已在 AC-1 中一起验证（testExtractGraph_Success 走真实 DeepSeek API）
      → 单独断言 LLM 返回非空且可解析为 JSON

    AC-7（LLM 失败处理）：
    - testExtractGraph_EmptyText_Rejected：
      → 查找一条 text_content 为空的文档（或手动 insert 一条）
      → POST /api/v1/graph/extract/{docId}
      → 断言 HTTP 400 + errorCode = A0008

    AC-8（重复抽取覆盖）：
    - testExtractGraph_ReExtract_Overwrites：
      → 完成第一次抽取后记录 nodes1 = GET 子图
      → 重新 POST /api/v1/graph/extract/{docId}
      → 记录 nodes2 = GET 子图
      → 断言 nodes2 非空且 nodes1.size() 与 nodes2.size() 差异 ≤ 20%

    使用 JUnit 5 + Spring Boot Test。作者 Jay。
    测试类注解：@SpringBootTest(webEnvironment = RANDOM_PORT) + @ActiveProfiles("dev")
    HTTP 调用使用 TestRestTemplate。
  </action>
  <verify>mvn test -pl . -Dtest="GraphControllerIntegrationTest" -DfailIfNoTests=false -Dspring.profiles.active=dev</verify>
  <done>5 个集成测试用例覆盖 AC-1/AC-2/AC-4/AC-7/AC-8；直连 podman 真实组件，LLM 调用走真实 DeepSeek API</done>
  <depends_on>T09</depends_on>
</task>
```

---

## 任务映射 AC 一览

| AC | 覆盖任务 | 验证方式 |
|----|----------|----------|
| AC-1 端到端抽取 | T07+T08+T09 → T11 | 集成测试 |
| AC-2 子图查询 | T08+T09 → T11 | 集成测试 |
| AC-3 JSON Schema 校验 | T07 → T10 | 单元测试 |
| AC-4 LLM 最小调用 | T03 → T11 | 集成测试 |
| AC-5 抽象可扩展 | T02+T06 → T10 | 单元测试 |
| AC-6 空文本拒绝 | T08 → T10 | 单元测试 |
| AC-7 LLM 失败无脏数据 | T08 → T11 | 集成测试 |
| AC-8 重复抽取覆盖 | T08 → T11 | 集成测试 |

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