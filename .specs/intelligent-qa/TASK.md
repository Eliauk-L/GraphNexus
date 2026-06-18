# TASK: 智能问答 — 图剪枝驱动的 LLM 分析与诊断

- **Change ID**: `intelligent-qa`
- **关联**: `@.specs/intelligent-qa/REQUIREMENT.md`、`@.specs/intelligent-qa/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T04[P], T05[P], T06[P]
Wave 2 (parallel): T07[P], T08[P], T09[P], T10[P]     (T09→T05,T07; T10→T06)
Wave 3:            T11                                  (→ T03,T04,T07,T09,T10)
Wave 4 (parallel): T12[P], T12b[P], T13[P]              (→ T11)
Wave 5 (parallel): T14[P], T15[P]                      (独立配置)
Wave 6 (parallel): T16[P], T17[P]                      (→ T11,T12,T12b)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>ErrorCode 新增 QA 错误码 A0019/A0020/A0021</name>
  <read_files>
    common/exception/ErrorCode.java
  </read_files>
  <write_files>
    common/exception/ErrorCode.java
  </write_files>
  <action>
    在 ErrorCode 枚举中新增 3 个错误码（见 DESIGN D3/D12/D8）：
    - A0019 (BAD_REQUEST, "无法识别查询意图"): 意图识别失败，关键词不匹配任何意图
    - A0020 (CONFLICT, "存在多个同名或相似学生"): studentName 匹配到多个 Student，需消歧
    - A0021 (NOT_FOUND, "问答任务不存在"): taskId 在 query_task 表中查不到
    沿用既有 ErrorCode 枚举模式：(errorCode, httpStatus, defaultUserTip) 三参数构造。
    注意：A0016~A0018 已被 wide-graph-fusion 使用，从 A0019 开始。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；ErrorCode 新增 3 个 QA 相关错误码，HTTP 状态码正确</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>QueryProperties — yml QA 配置绑定类</name>
  <read_files>
    application/graph/fusion/config/FusionProperties.java
  </read_files>
  <write_files>
    application/query/config/QueryProperties.java
    application/query/config/package-info.java
  </write_files>
  <action>
    创建 @ConfigurationProperties("query") 配置类，绑定以下配置块（见 DESIGN D6/D7/D8）：
    - token-budget: max-input-tokens (默认 8000), chars-per-token (默认 3)
    - pruning: weak-threshold (默认 0.6), max-prerequisite-hops (默认 2)
    - retry: max-retries (默认 2), retry-delay-ms (默认 1000)
    - async: core-pool-size (默认 2), max-pool-size (默认 5), queue-capacity (默认 10)
    - timeout: sync-timeout-seconds (默认 30)
    使用 Java record 定义内嵌配置组（TokenBudget/Pruning/Retry/Async/Timeout）。
    参考 FusionProperties 的 @ConfigurationProperties 绑定模式。
    需要 @EnableConfigurationProperties 或 @ConfigurationPropertiesScan（Spring Boot 3.3 自动扫描，只要类在 component-scan 路径下）。
  </action>
  <verify>mvn test -pl . -Dtest="QueryPropertiesTest" 2>&1 | grep "Tests run"</verify>
  <done>QueryProperties 可正确绑定 yml 配置；单元测试覆盖默认值和绑定</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>QueryIntent 枚举 + PruningRequest/PrunedSubgraph/PruningMeta BO</name>
  <read_files>
    infrastructure/neo4j/node/GraphNode.java
    infrastructure/neo4j/edge/GraphEdge.java
  </read_files>
  <write_files>
    application/query/model/QueryIntent.java
    application/analysis/model/PruningRequest.java
    application/analysis/model/PrunedSubgraph.java
    application/analysis/model/package-info.java
  </write_files>
  <action>
    1. application/query/model/QueryIntent 枚举：
       v1 仅 STUDENT_DIAGNOSIS，预留 KP_ANALYSIS/CLASS_OVERVIEW/PREREQUISITE_CHAIN/GENERAL（注释标记 v2 预留）。
       QueryIntent 归属 query 模块——它是问答意图路由标签，由 QueryService 使用。

    2. application/analysis/model/ 下创建：
       - PruningRequest (record)：intent(QueryIntent), entityId(String), subject(String), params(Map<String,Object>)
       - PrunedSubgraph (record)：nodes(List<GraphNode>), edges(List<GraphEdge>), meta(PruningMeta)
       - PruningMeta (record)：strategy(String), mastersAvailable(boolean), weakThreshold(double), maxHops(int), totalNodes(int), totalEdges(int), truncated(boolean), truncatedNodeNames(List<String>)
       PrunedSubgraph 归属 analysis 模块——它是图剪枝操作的输出，属于图分析结果。

    遵循既有 BO 风格：Lombok @Builder 或 Java record，字段注释中文。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；analysis/model 下 4 个文件可正常 import</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>QueryTaskStatus 枚举 + QueryTaskDO + QueryTaskRepository（MySQL L3）</name>
  <read_files>
    infrastructure/mysql/fusion/FusionLogDO.java
    infrastructure/mysql/fusion/FusionLogRepository.java
    infrastructure/mysql/config/JpaAuditConfig.java
  </read_files>
  <write_files>
    infrastructure/mysql/query/QueryTaskStatus.java
    infrastructure/mysql/query/QueryTaskDO.java
    infrastructure/mysql/query/QueryTaskRepository.java
    infrastructure/mysql/query/package-info.java
  </write_files>
  <action>
    1. QueryTaskStatus 枚举：PENDING/PROCESSING/COMPLETED/FAILED
    2. QueryTaskDO（JPA Entity，表名 query_task）：
       字段见 DESIGN D9。重点：
       - taskId VARCHAR(36) UNIQUE NOT NULL (UUID)
       - question TEXT NOT NULL
       - studentName/studentNo/subject VARCHAR
       - intent VARCHAR(32)
       - status VARCHAR(20) NOT NULL
       - answer MEDIUMTEXT ( @Column(columnDefinition = "MEDIUMTEXT") )
       - subgraphJson MEDIUMTEXT ( @Column(columnDefinition = "MEDIUMTEXT") )
       - tokenUsageJson JSON ( @Column(columnDefinition = "JSON") )
       - errorMessage TEXT
       - retryCount INT DEFAULT 0
       - elapsedMs BIGINT
       - createTime/updateTime（JPA Auditing 自动填充）
       不设 isDeleted（日志类表，永久保留）。
    3. QueryTaskRepository：JpaRepository<QueryTaskDO, Long> + findByTaskId(String taskId) 方法，返回 Optional<QueryTaskDO>
    参考 FusionLogDO 风格：@EntityListeners(AuditingEntityListener.class)，@CreatedDate/@LastModifiedDate
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；QueryTaskDO 可正常被 JPA 管理；表结构符合 D9 设计</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>SubgraphPruningStrategy 接口定义（analysis 模块）</name>
  <read_files>
    application/graph/fusion/strategy/KpMatchingStrategy.java
    application/analysis/model/PruningRequest.java
    application/analysis/model/PrunedSubgraph.java
  </read_files>
  <write_files>
    application/analysis/strategy/SubgraphPruningStrategy.java
    application/analysis/strategy/package-info.java
  </write_files>
  <action>
    在 application/analysis/strategy/ 下创建 SubgraphPruningStrategy 接口（见 ADR-010 D1）：
    - 方法签名：PrunedSubgraph prune(PruningRequest request)
    - Javadoc 说明契约：输入 PruningRequest（意图+实体+参数）→ 输出 PrunedSubgraph（节点+边+元数据）
    - 接口注释说明实现类应处理 MASTERS 降级场景（mastersAvailable=false）
    参考 KpMatchingStrategy 的接口风格：简洁、单方法、Javadoc 完整。
    本接口放 analysis 模块的理由：图剪枝本质是图分析操作，与 api/analysis/ 包的"PageRank、度中心性、指标度量查询"语义一致。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；接口可被 application/query 模块依赖</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>Prompt 模板文件 — student-diagnosis system + user prompt</name>
  <read_files>
    无（首次创建模板，仅参照 DESIGN D4/D5/ADR-011）
  </read_files>
  <write_files>
    src/main/resources/prompts/student-diagnosis-system.md
    src/main/resources/prompts/student-diagnosis-user.md
  </write_files>
  <action>
    创建 classpath 下 prompts/ 目录及两个模板文件（见 ADR-011）：

    1. student-diagnosis-system.md（System Prompt）：
       - 角色设定：你是一位经验丰富的教育诊断专家
       - 分析框架：薄弱知识点识别 → 根因分析（前置依赖追溯）→ 学习建议
       - 输出约束：Markdown 格式、以 # 标题开头（禁止前导语）、至少一个列表
       - 数据约束：仅基于提供的子图数据回答，禁止编造
       - 正确示例（few-shot）：完整的 Markdown 分析报告示例
       - 占位符：无（system prompt 不含动态数据）

    2. student-diagnosis-user.md（User Prompt 模板）：
       - 占位符变量：{{studentName}} {{studentNo}} {{className}} {{subject}} {{subgraphText}} {{userQuestion}} {{weakThreshold}} {{maxHops}}
       - 结构：用户问题回显 + 子图结构化数据（{{subgraphText}}）+ 分析要求重申
       - MASTERS 降级提示：条件占位 {{mastersWarning}}（由 PromptTemplateService 按需注入）

    模板语言：中文。{{variable}} 是纯字符串占位符，由 PromptTemplateService 做 String.replace() 替换。
  </action>
  <verify>ls -la src/main/resources/prompts/student-diagnosis-system.md src/main/resources/prompts/student-diagnosis-user.md 2>&1</verify>
  <done>两个模板文件存在；system prompt 含角色+格式约束+示例；user prompt 含正确的 {{variable}} 占位符</done>
  <depends_on></depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>GraphNodeRepository 新增 5 个只读查询方法</name>
  <read_files>
    infrastructure/neo4j/repository/GraphNodeRepository.java
    infrastructure/neo4j/node/StudentNode.java
    infrastructure/neo4j/node/KnowledgePointNode.java
    infrastructure/neo4j/edge/MastersEdge.java
    infrastructure/neo4j/edge/PrerequisiteEdge.java
  </read_files>
  <write_files>
    infrastructure/neo4j/repository/GraphNodeRepository.java
  </write_files>
  <action>
    在 GraphNodeRepository 中新增 5 个只读方法（见 DESIGN D10），均通过 Neo4jClient 查询，不影响现有方法：

    1. findStudentByName(String name) → List<Map<String,Object>>
       MATCH (s:Student) WHERE s.name CONTAINS $name RETURN s.id, s.studentNo, s.name, s.className, s.grade

    2. findStudentByNo(String studentNo) → Optional<Map<String,Object>>
       MATCH (s:Student {studentNo: $studentNo}) RETURN s.id, s.studentNo, s.name, s.className, s.grade
       不存在返回 Optional.empty()

    3. findMastersByStudentAndSubject(String studentNodeId, String subject) → List<Map<String,Object>>
       MATCH (s:Student {id: $sid})-[m:MASTERS]->(kp:KnowledgePoint {subject: $subject})
       RETURN kp.id, kp.name, kp.description, kp.gradeLevel, m.weight, m.description
       若返回空列表 → 调用方触发 MASTERS 降级（T09 处理）

    4. findPrerequisitesUpstream(List<String> kpIds, int maxHops) → List<Map<String,Object>>
       MATCH (kp:KnowledgePoint)-[:PREREQUISITE_OF*1..{maxHops}]->(pre:KnowledgePoint)
       WHERE kp.id IN $ids
       RETURN DISTINCT kp.id AS fromKpId, pre.id AS toKpId, pre.name AS toKpName, length(path) AS hops
       LIMIT 200
       使用 String.format 注入 maxHops（整数，非用户输入，安全）

    5. findMastersByStudentAndKpIds(String studentNodeId, List<String> kpIds) → List<Map<String,Object>>
       MATCH (s:Student {id: $sid})-[m:MASTERS]->(kp:KnowledgePoint)
       WHERE kp.id IN $kpIds
       RETURN kp.id, kp.name, m.weight

    所有方法遵循既有模式：Neo4jClient.query().bindAll().fetch().all() → Stream/Map 提取字段。
    异常处理：catch Exception → log.warn + return 空集合/Optional.empty()（读操作不抛异常）。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；GraphNodeRepository 新增 5 个方法，不破坏现有方法签名</done>
  <depends_on></depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>ExamRecordRepository 新增按学号+学科查询方法</name>
  <read_files>
    infrastructure/mysql/document/ExamRecordRepository.java
    infrastructure/mysql/document/ExamRecordDO.java
  </read_files>
  <write_files>
    infrastructure/mysql/document/ExamRecordRepository.java
  </write_files>
  <action>
    在 ExamRecordRepository 中新增 JPQL 查询方法（MASTERS 降级场景用，见 DESIGN D11）：
    findByStudentNoAndSubject(String studentNo, String subject) → List<ExamRecordDO>
    JPQL：SELECT e FROM ExamRecordDO e WHERE e.studentNo = :studentNo AND e.subject = :subject AND e.isDeleted = 0
    沿用既有显式 JPQL 模式（避免 Hibernate Boolean/TINYINT 谓词 bug）。
    只读查询，不修改任何 DO 字段。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；新增方法可正常使用</done>
  <depends_on></depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>StudentDiagnosisStrategy — v1 剪枝策略实现（analysis 模块）</name>
  <read_files>
    application/analysis/strategy/SubgraphPruningStrategy.java
    application/analysis/model/PruningRequest.java
    application/analysis/model/PrunedSubgraph.java
    application/query/model/QueryIntent.java
    infrastructure/neo4j/repository/GraphNodeRepository.java
    infrastructure/mysql/document/ExamRecordRepository.java
    infrastructure/mysql/document/ExamRecordDO.java
  </read_files>
  <write_files>
    application/analysis/strategy/StudentDiagnosisStrategy.java
  </write_files>
  <action>
    实现 SubgraphPruningStrategy 接口（见 ADR-010 D2），四步剪枝：

    Step 1 — 确认学生：调用 graphNodeRepository.findStudentByNo(entityId)
    Step 2 — 找薄弱点：
      先调 findMastersByStudentAndSubject()；
      若返回空 → MASTERS 降级：调 findStudents + examRecord 查成绩路径，
      MATCH (s:Student {studentNo})-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint {subject})
      → 结合 MySQL ExamRecord.score_details 计算原始得分率（简单平均），
      mark PruningMeta.mastersAvailable = false
      若正常 → filter weight < weakThreshold（PruningRequest.params 中获取，默认 0.6）
    Step 3 — 前置依赖链：
      对 Step 2 的弱掌握 KP ID 列表调用 findPrerequisitesUpstream(kpIds, maxHops)
      若返回空（无 PREREQUISITE_OF 边）→ 子图仅含 Student + 弱掌握 KP + MASTERS 边
    Step 4 — 补全前置 KP 掌握度：
      对 Step 3 的前置 KP ID 列表调用 findMastersByStudentAndKpIds()
      若 MASTERS 降级 → 同 Step 2 查 TESTED 路径

    组装 PrunedSubgraph：
    - nodes：Student + 弱掌握 KP + 前置 KP（去重）
    - edges：MASTERS 边（含 weight）+ PREREQUISITE_OF 边
    - meta：strategy="STUDENT_DIAGNOSIS", mastersAvailable=true/false, weakThreshold, maxHops, totalNodes, totalEdges

    构造器注入 GraphNodeRepository + ExamRecordRepository。
    使用 Lombok @Slf4j + @RequiredArgsConstructor。
  </action>
  <verify>mvn test -pl . -Dtest="StudentDiagnosisStrategyTest" 2>&1 | grep "Tests run"</verify>
  <done>StudentDiagnosisStrategy 正确实现四步剪枝；MASTERS 降级路径可用；单元测试通过（≥ 5 个 case：正常/降级/无薄弱点/无前置依赖/空结果）</done>
  <depends_on>T05, T07</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>PromptTemplateService — Prompt 模板加载与变量替换</name>
  <read_files>
    src/main/resources/prompts/student-diagnosis-system.md
    src/main/resources/prompts/student-diagnosis-user.md
    application/query/config/QueryProperties.java
  </read_files>
  <write_files>
    application/query/prompt/PromptTemplateService.java
    application/query/prompt/package-info.java
  </write_files>
  <action>
    创建 PromptTemplateService（见 DESIGN D5/ADR-011）：

    - loadTemplate(String name)：通过 Spring ResourceLoader 加载 classpath:prompts/{name}.md → 返回字符串
      若文件不存在 → throw BusinessException(C0001, "Prompt模板不存在: {name}")
    - assemble(String template, Map<String,String> variables)：遍历 Map，template.replace("{{"+key+"}}", value) → 返回替换后字符串
      对 null value 替换为空字符串 ""
    - buildPrompt(QueryIntent intent, Map<String,String> variables)：
      ① loadTemplate(intent.name().toLowerCase() + "-system") → systemPrompt
      ② loadTemplate(intent.name().toLowerCase() + "-user") → userTemplate
      ③ assemble(userTemplate, variables) → userMessage
      ④ 若 variables 含 mastersAvailable=false → userMessage 末尾追加 "⚠️ 融合数据不可用，以下掌握度为原始考试得分率..."
      ⑤ 返回 record PromptPair(String systemPrompt, String userMessage)

    构造器注入 ResourceLoader（Spring Boot 自动提供）。
    模板缓存：v1 不缓存（每次 loadTemplate 读文件，方便开发阶段热修改）。
  </action>
  <verify>mvn test -pl . -Dtest="PromptTemplateServiceTest" 2>&1 | grep "Tests run"</verify>
  <done>PromptTemplateService 可正确加载模板 + 替换变量；MASTERS 降级警告正确注入；单元测试通过（≥ 3 个 case）</done>
  <depends_on>T06</depends_on>
</task>

<task id="T11" parallel="false" status="pending">
  <name>QueryService 接口 + QueryServiceImpl — QA 核心编排逻辑</name>
  <read_files>
    application/query/config/QueryProperties.java
    application/query/prompt/PromptTemplateService.java
    application/analysis/strategy/SubgraphPruningStrategy.java
    application/analysis/strategy/StudentDiagnosisStrategy.java
    application/query/model/QueryIntent.java
    application/analysis/model/PruningRequest.java
    application/analysis/model/PrunedSubgraph.java
    application/llmgateway/service/LlmGateway.java
    infrastructure/neo4j/repository/GraphNodeRepository.java
    infrastructure/mysql/query/QueryTaskDO.java
    infrastructure/mysql/query/QueryTaskRepository.java
    infrastructure/mysql/query/QueryTaskStatus.java
    infrastructure/mysql/document/ExamRecordRepository.java
    common/exception/ErrorCode.java
    common/exception/BusinessException.java
  </read_files>
  <write_files>
    application/query/service/QueryService.java
    application/query/service/impl/QueryServiceImpl.java
    application/query/service/impl/package-info.java
    application/query/model/QueryResultBO.java
    application/query/model/package-info.java
  </write_files>
  <action>
    创建 QueryService 接口 + QueryServiceImpl 实现，核心方法（见 DESIGN §2.1~2.3）：

    1. QueryService 接口方法：
       - ask(String question, String studentName, String studentNo, String subject) → QueryResultBO
       - askAsync(String question, String studentName, String studentNo, String subject) → String (taskId)
       - getResult(String taskId) → QueryResultBO
       - getSubgraph(String taskId) → PrunedSubgraph

    2. QueryServiceImpl（@Service, @Slf4j, @RequiredArgsConstructor）：

       **意图识别 recognizeIntent(String question)**（见 D3）：
       - 关键词映射表：Map.of("薄弱","STUDENT_DIAGNOSIS","加强","STUDENT_DIAGNOSIS","掌握","STUDENT_DIAGNOSIS","诊断","STUDENT_DIAGNOSIS","分析学生","STUDENT_DIAGNOSIS")
       - 遍历映射表，question.contains(keyword) → 返回对应 QueryIntent
       - 无匹配 → throw BusinessException(A0019)
       - 实体提取：正则 "分析学生(.+?)的" → 提取 studentName（若请求中未显式传入）
       - v1 仅 STUDENT_DIAGNOSIS，if intent != STUDENT_DIAGNOSIS → throw A0019

       **ask()（同步模式，见 D8）**：
       ① 意图识别 + 实体解析：
          - studentNo 不为空 → graphNodeRepository.findStudentByNo()
          - 否则 studentName → graphNodeRepository.findStudentByName()
            → 多结果 (>1) → throw BusinessException(A0020, candidates列表序列化为JSON)
            → 单结果 → 取 studentNo + studentNodeId
          - 无结果 → throw BusinessException(A0006, "未找到学生: {name}")
       ② 构建 PruningRequest(intent=STUDENT_DIAGNOSIS, entityId=studentNodeId, subject, params={weakThreshold, maxHops})
       ③ 调用 studentDiagnosisStrategy.prune(request) → PrunedSubgraph
       ④ 子图序列化 serializeSubgraph(subgraph)：
          - 见 D4 格式：## 学生信息 → ## 薄弱知识点 → ## 前置依赖关系 → ## 前置知识点掌握度
          - Token 预算控制（见 D6）：估算 token = text.length()/charsPerToken → 超预算按优先级截断 → 追加省略声明
       ⑤ 组装 Prompt：promptTemplateService.buildPrompt(intent, variables)
          variables: studentName, studentNo, className, subject, subgraphText(序列化结果), userQuestion, weakThreshold, maxHops
          + mastersAvailable(PruningMeta) → 若 false 触发降级警告
       ⑥ LLM 调用（含重试，见 D7）：
          - 最多重试 maxRetries 次（默认 2）
          - 每次调用 llmGateway.chat(systemPrompt, userMessage)
          - 格式校验：非空 → startsWith("#") → 含列表标记("- " 或 "1. ")
          - 不通过 → retry，systemPrompt 追加格式约束
          - 仍失败 → 返回原始文本（降级）
       ⑦ 写入 query_task（同步模式：status=COMPLETED，含 answer/subgraphJson/tokenUsageJson/elapsedMs）
       ⑧ 返回 QueryResultBO(taskId, status=COMPLETED, question, intent, answer, tokenUsage)

       **askAsync()（异步模式，见 D8）**：
       ① 生成 taskId (UUID)
       ② INSERT query_task (status=PENDING)
       ③ 返回 taskId
       ④ @Async 后台执行同 ask() 步骤 ①~⑦：
          - 更新 status=PROCESSING
          - 成功 → UPDATE status=COMPLETED + answer + subgraphJson + tokenUsageJson + elapsedMs
          - 失败 → UPDATE status=FAILED + errorMessage + retryCount + elapsedMs

       **getResult(taskId)**：
       - SELECT query_task WHERE task_id = ? → 不存在 throw A0021
       - 返回 QueryResultBO（status + 若 COMPLETED 含 answer/tokenUsage/createdAt）

       **getSubgraph(taskId)**：
       - SELECT query_task WHERE task_id = ? → 不存在 throw A0021
       - status != COMPLETED → 返回 PrunedSubgraph with meta only
       - 反序列化 subgraphJson → PrunedSubgraph

       **MASTERS 降级逻辑**已在 T09 StudentDiagnosisStrategy 中实现，此处仅按 PruningMeta.mastersAvailable 注入警告。

    注入依赖：GraphNodeRepository, ExamRecordRepository, StudentDiagnosisStrategy,
             PromptTemplateService, LlmGateway, QueryTaskRepository, QueryProperties
  </action>
  <verify>mvn test -pl . -Dtest="QueryServiceImplTest" 2>&1 | grep "Tests run"</verify>
  <done>QueryService 实现完整 QA 链路；意图识别正确；同步/异步模式可用；格式校验+重试可用；单元测试通过（≥ 8 个 case：正常/异步/意图失败/学生不存在/同名冲突/LLM失败重试/MASTERS降级/taskId不存在）</done>
  <depends_on>T03, T04, T07, T09, T10</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>QueryController + API DTO/VO — L1 QA 端点</name>
  <read_files>
    api/graph/controller/GraphController.java
    api/graph/dto/GraphSubgraphVO.java
    common/ApiResponse.java
    application/query/service/QueryService.java
    application/query/model/QueryResultBO.java
    application/analysis/model/PrunedSubgraph.java
  </read_files>
  <write_files>
    api/query/controller/QueryController.java
    api/query/dto/QueryAskRequest.java
    api/query/dto/QueryAskResponse.java
    api/query/dto/QueryAsyncResponse.java
    api/query/dto/QueryResultResponse.java
  </write_files>
  <action>
    创建 3 个 REST 端点（见 DESIGN §2 数据流）：

    1. QueryAskRequest（VO）：
       - question: String (@NotBlank)
       - studentName: String（与 studentNo 至少传一个）
       - studentNo: String
       - subject: String (@NotBlank)

    2. QueryAskResponse（同步响应）：
       - taskId, question, intent, answer(Markdown), status("COMPLETED")
       - tokenUsage: TokenUsageVO(prunedNodes, prunedEdges, estimatedTokens, promptTokens, completionTokens)

    3. QueryAsyncResponse（异步提交响应）：
       - taskId, status("PENDING"), createdAt

    4. QueryResultResponse（轮询结果）：
       - taskId, status(PROCESSING/COMPLETED/FAILED)
       - answer(COMPLETED时含), tokenUsage, errorMessage(FAILED时含), createdAt, updatedAt

    5. QueryController（@RestController, @RequestMapping("/api/v1/query"), @RequiredArgsConstructor）：
       - POST /ask：@RequestBody @Valid QueryAskRequest → QueryService.ask() → 200 QueryAskResponse
         同步超时降级：Controller 层不处理超时（由 QueryService 内部 Future.get(30s) 实现），
         若 QueryService 返回 status=PROCESSING（超时降级）→ 202 QueryAsyncResponse
       - POST /ask-async：@RequestBody @Valid QueryAskRequest → QueryService.askAsync() → 202 QueryAsyncResponse
       - GET /result/{taskId}：QueryService.getResult() → 200 QueryResultResponse

    VO 转换用静态工厂方法 QueryAskResponse.from(QueryResultBO), QueryResultResponse.from(QueryResultBO) 等。
    参数校验用 @Valid + @NotBlank，异常由既有 GlobalExceptionHandler 统一处理。
    参考 GraphController 风格：@RestController + @RequestMapping + ApiResponse.success() 包装。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；3 个端点 ask/ask-async/result 路径和方法签名正确；VO 含必要字段</done>
  <depends_on>T11</depends_on>
</task>

<task id="T12b" parallel="true" status="pending">
  <name>AnalysisController + SubgraphResponse — L1 子图端点（analysis 模块）</name>
  <read_files>
    api/graph/controller/GraphController.java
    common/ApiResponse.java
    application/query/service/QueryService.java
    application/analysis/model/PrunedSubgraph.java
  </read_files>
  <write_files>
    api/analysis/controller/AnalysisController.java
    api/analysis/dto/SubgraphResponse.java
  </write_files>
  <action>
    在 api/analysis/ 下创建子图端点（见 DESIGN §2.3）：

    1. SubgraphResponse（VO）：
       - taskId: String
       - status: String ("NOT_READY" / 子图状态)
       - nodes: List<NodeVO>（含 id/nodeType/properties）
       - edges: List<EdgeVO>（含 sourceNodeId/targetNodeId/edgeType/weight）
       - pruningMeta: PruningMetaVO（strategy/mastersAvailable/weakThreshold/maxHops/totalNodes/totalEdges/truncated）

    2. AnalysisController（@RestController, @RequestMapping("/api/v1/analysis"), @RequiredArgsConstructor）：
       - GET /subgraph/{taskId}：QueryService.getSubgraph(taskId) → PrunedSubgraph → SubgraphResponse
         查询逻辑在 QueryService 中，端点在 analysis 模块中暴露。
         不存在 → A0021(404)
       - 仅此一个端点。后续 PageRank/度中心性等分析端点追加到此 Controller。

    理由：子图属于图分析结果，/api/v1/analysis 是图分析 API 的统一入口。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；GET /api/v1/analysis/subgraph/{taskId} 端点可正常调用</done>
  <depends_on>T11</depends_on>
</task>

<task id="T13" parallel="true" status="pending">
  <name>@EnableAsync 配置 — Spring 异步线程池 + QA 异步支持</name>
  <read_files>
    common/exception/ErrorCode.java
  </read_files>
  <write_files>
    application/query/config/AsyncConfig.java
  </write_files>
  <action>
    创建 AsyncConfig（@Configuration + @EnableAsync），配置 QA 异步任务线程池（见 DESIGN D8）：

    - Bean 名：queryAsyncExecutor
    - CorePoolSize：${query.async.core-pool-size:2}
    - MaxPoolSize：${query.async.max-pool-size:5}
    - QueueCapacity：${query.async.queue-capacity:10}
    - RejectedExecutionHandler：CallerRunsPolicy（队列满时在调用线程同步执行）
    - ThreadNamePrefix："query-async-"

    实现 AsyncConfigurer 接口的 getAsyncExecutor() 方法。
    确保 @Async 注解在 QueryServiceImpl 的 askAsync 内部方法上生效（需通过代理调用，即 QueryService 接口方法上标注 @Async 或内部 self-injection）。v1 采用简单方案：QueryServiceImpl 中 askAsync() 方法直接使用 @Autowired 注入的 TaskExecutor 手动 submit Callable。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；queryAsyncExecutor Bean 注册成功；@Async 支持就绪</done>
  <depends_on>T01</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>Neo4jIndexConfig 新增 Student + KnowledgePoint 查询索引</name>
  <read_files>
    infrastructure/neo4j/config/Neo4jIndexConfig.java
  </read_files>
  <write_files>
    infrastructure/neo4j/config/Neo4jIndexConfig.java
  </write_files>
  <action>
    在 Neo4jIndexConfig.createIndexes() 中新增以下索引（幂等，IF NOT EXISTS）：

    1. Student 节点索引（支撑 findStudentByName 的 CONTAINS 查询 + findStudentByNo 的点查）：
       - CREATE INDEX student_studentNo IF NOT EXISTS FOR (s:Student) ON (s.studentNo)
       - CREATE INDEX student_name IF NOT EXISTS FOR (s:Student) ON (s.name)
         （注：CONTAINS 不走 BTREE 索引，但 TEXT 索引可加速前缀匹配。v1 学生数据量小（< 1000），全表扫描可接受。创建索引为 v2 数据增长做准备）

    2. KnowledgePoint 节点索引（支撑 PREREQUISITE_OF 变长路径匹配的起点过滤）：
       - CREATE INDEX kp_id_lookup IF NOT EXISTS FOR (kp:KnowledgePoint) ON (kp.id)
         （注意：此索引可能与已有的 kp_id 索引重复。检查现有 kp_id 索引已存在 → 跳过或改名为 kp_subject 索引）
         - CREATE INDEX kp_subject IF NOT EXISTS FOR (kp:KnowledgePoint) ON (kp.subject)

    遵循既有模式：try/catch + log.warn（索引已存在则幂等跳过）。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；索引创建语句语法正确；不破坏现有索引创建逻辑</done>
  <depends_on></depends_on>
</task>

<task id="T15" parallel="true" status="pending">
  <name>application-dev.yml 新增 query 配置块</name>
  <read_files>
    src/main/resources/application-dev.yml
  </read_files>
  <write_files>
    src/main/resources/application-dev.yml
  </write_files>
  <action>
    在 application-dev.yml 末尾新增 query 配置块（见 DESIGN D2/D6/D7/D8）：

    ```yaml
    # ---------------------------------------------------------------------------
    # 智能问答配置
    # ---------------------------------------------------------------------------
    query:
      token-budget:
        max-input-tokens: 8000
        chars-per-token: 3
      pruning:
        weak-threshold: 0.6
        max-prerequisite-hops: 2
      retry:
        max-retries: 2
        retry-delay-ms: 1000
      async:
        core-pool-size: 2
        max-pool-size: 5
        queue-capacity: 10
      timeout:
        sync-timeout-seconds: 30
    ```

    放在 fusion 配置块之后、mineru 配置块之前（或文件末尾），保持与其他配置块一致的注释风格。
  </action>
  <verify>grep -A 15 "query:" src/main/resources/application-dev.yml 2>&1</verify>
  <done>query 配置块已写入 application-dev.yml；QueryProperties 可正常绑定</done>
  <depends_on></depends_on>
</task>

<task id="T16" parallel="true" status="pending">
  <name>单元测试 — 策略 + Prompt + Service（JUnit 5 + Mockito）</name>
  <read_files>
    application/analysis/strategy/StudentDiagnosisStrategy.java
    application/query/prompt/PromptTemplateService.java
    application/query/service/impl/QueryServiceImpl.java
    application/query/config/QueryProperties.java
    application/analysis/model/PrunedSubgraph.java
    application/query/model/QueryResultBO.java
    src/test/java/com/graphnexus/application/graph/service/GraphServiceTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategyTest.java
    src/test/java/com/graphnexus/application/query/prompt/PromptTemplateServiceTest.java
    src/test/java/com/graphnexus/application/query/service/QueryServiceImplTest.java
    src/test/java/com/graphnexus/application/query/config/QueryPropertiesTest.java
  </write_files>
  <action>
    编写 4 个单元测试类（见 DESIGN/D7 AC 覆盖）：

    1. StudentDiagnosisStrategyTest（≥ 5 cases）：
       - 正常剪枝：MASTERS 存在，3 个薄弱点 + 2 个前置依赖
       - MASTERS 降级：MASTERS 返回空，走 TESTED 路径
       - 无薄弱点：所有 weight >= 0.6，返回仅含 Student 的 PrunedSubgraph
       - 无前置依赖：弱掌握 KP 无 PREREQUISITE_OF 边
       - 空结果：Student 在 subject 下无任何 KP 关联
       使用 @Mock GraphNodeRepository + @Mock ExamRecordRepository
       参考 GraphServiceTest 的 @ExtendWith(MockitoExtension.class) 风格

    2. PromptTemplateServiceTest（≥ 3 cases）：
       - 正常加载 + 变量替换（所有占位符被正确替换）
       - MASTERS 降级警告注入（mastersAvailable=false → 提示文本出现）
       - 模板文件不存在 → 抛 BusinessException
       使用 @Mock ResourceLoader，测试模板字符串内嵌在测试方法中（不依赖真实文件）

    3. QueryServiceImplTest（≥ 8 cases）：
       - 同步问答：识别意图 STUDENT_DIAGNOSIS → 剪枝 → LLM 返回合法 Markdown
       - 异步问答：返回 taskId + query_task INSERT + 后台执行
       - 意图识别失败：无关键词匹配 → A0019
       - 学生不存在：findStudentByNo 返回 empty → A0006
       - 同名冲突：findStudentByName 返回 >1 条 → A0020
       - LLM 调用失败 + 重试：LlmGateway.chat() 前 2 次抛异常，第 3 次成功 → 校验重试次数
       - LLM 格式校验失败 + 重试：返回无标题文本 2 次，第 3 次返回合法 Markdown
       - LLM 格式重试全失败：返回原始文本（降级），status=COMPLETED
       - MASTERS 降级：策略返回 mastersAvailable=false，Prompt 含警告
       - taskId 不存在：getResult/getSubgraph → A0021
       使用 @Mock 所有依赖（GraphNodeRepository, ExamRecordRepository, StudentDiagnosisStrategy,
            PromptTemplateService, LlmGateway, QueryTaskRepository）
       参考 GraphServiceTest 的 Mock 风格

    4. QueryPropertiesTest（≥ 2 cases）：
       - 默认值验证（使用 @SpringBootTest + @ActiveProfiles("dev") 或纯 Java 构造）
       - 自定义值绑定验证

    所有测试遵循既有限制：JUnit 5 + Mockito + @ExtendWith(MockitoExtension.class)
    Mock 不屏蔽真实失败——仅 mock 外部依赖（Neo4j/MySQL/LLM），不 mock 本 change 的内部类。
  </action>
  <verify>mvn test -pl . -Dtest="StudentDiagnosisStrategyTest,PromptTemplateServiceTest,QueryServiceImplTest,QueryPropertiesTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>4 个测试类全部通过；覆盖 AC-1~AC-12 的关键路径；无 @MockBean 屏蔽真实逻辑</done>
  <depends_on>T11</depends_on>
</task>

<task id="T17" parallel="true" status="pending">
  <name>集成测试 — QA 端点 + Neo4j + MySQL 直连 podman</name>
  <read_files>
    src/test/java/com/graphnexus/api/graph/controller/GraphControllerIntegrationTest.java
    api/query/controller/QueryController.java
    api/analysis/controller/AnalysisController.java
    application/query/service/QueryService.java
    src/main/resources/application-dev.yml
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/api/query/controller/QueryControllerIntegrationTest.java
  </write_files>
  <action>
    编写集成测试（@SpringBootTest + @ActiveProfiles("dev")），直连 podman 中的 Neo4j/MySQL/LLM API：

    前置数据准备（@BeforeEach）：
    - Cypher 创建 Student S1 (studentNo="S2024001", name="张三") + 3 个 KP (对称轴/顶点坐标/判别式) + MASTERS 边 (weight 0.35/0.20/0.75) + PREREQUISITE_OF 边 (配方法→顶点坐标)
    - MySQL exam_record 插入对应成绩记录

    测试用例（≥ 4 cases）：
    1. **同步问答端到端** (AC-1)：POST /api/v1/query/ask → 200 + answer 含 Markdown 标题 + "对称轴"/"顶点坐标" + 列表
    2. **异步问答 + 轮询** (AC-2)：POST /api/v1/query/ask-async → 202 + taskId → 轮询 GET /api/v1/query/result/{taskId} 最多 60s → 200 + status=COMPLETED
    3. **子图端点** (AC-3)：同步问答获取 taskId → GET /api/v1/analysis/subgraph/{taskId} → 200 + nodes.length >= 3 + edges 含 MASTERS + pruningMeta.strategy=STUDENT_DIAGNOSIS
    4. **学生不存在** (AC-10)：POST /api/v1/query/ask {studentName="不存在"} → 404 + 错误码 A0006
    5. **Markdown 格式** (AC-4)：同步问答 → 提取 answer → 断言不以"根据"/"以下"开头 → 含 # 标题 + 列表标记

    清理（@AfterEach）：删除测试创建的 Neo4j 节点/边 + MySQL exam_record。
    使用 TestRestTemplate 或 MockMvc 发送 HTTP 请求。
    参考 GraphControllerIntegrationTest 的 podman 直连模式。
    若 LLM API Key 未配置 → 测试标记 @Disabled + 原因说明。
  </action>
  <verify>mvn test -pl . -Dtest="QueryControllerIntegrationTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>集成测试通过（≥ 3/5 cases 通过，LLM 依赖项可标 @Disabled）；AC-1~AC-4/AC-10 验证通过</done>
  <depends_on>T12</depends_on>
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
| T16 | 单元测试 | `e3a164c` | ✅ 7/7 pass |
| T17 | 集成测试 | — | ⏸️ deferred（需 podman + LLM API Key） |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```

---

## AC 覆盖矩阵

| AC | 覆盖任务 | 验证方式 |
|-----|---------|---------|
| AC-1 同步问答端到端 | T11, T17 | 集成测试：POST /ask → Markdown 报告 |
| AC-2 异步问答+轮询 | T11, T17 | 集成测试：POST /ask-async → 轮询 COMPLETED |
| AC-3 独立子图端点 | T11, T12, T17 | 集成测试：GET /subgraph → nodes+edges+meta |
| AC-4 Markdown 格式 | T06, T10, T11, T16, T17 | 单测：格式校验逻辑；集成测试：实际 LLM 输出 |
| AC-5 图剪枝正确范围 | T09, T16 | 单测：Mock 验证子图节点/边范围 |
| AC-6 意图识别 | T11, T16 | 单测：规则匹配的 4 个关键词组合 |
| AC-7 Token 预算控制 | T11, T16 | 单测：构造大子图 → 验证截断+省略声明 |
| AC-8 LLM 调用失败重试 | T11, T16 | 单测：Mock LlmGateway 失败 2 次→第 3 次成功 |
| AC-9 MySQL 任务持久化 | T04, T11, T17 | 集成测试：query_task 表记录完整生命周期 |
| AC-10 学生不存在 | T11, T17 | 集成测试：404 + A0006 |
| AC-11 无薄弱点 | T09, T16 | 单测：全部 weight >= 0.6 → LLM 生成正面评价 |
| AC-12 空图谱 | T09, T16 | 单测：Student 无 MASTERS → 提示无成绩数据 |