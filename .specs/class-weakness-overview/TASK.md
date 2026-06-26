# TASK: 班级薄弱概览 — 智能问答新增第二意图

- **Change ID**: `class-weakness-overview`
- **关联**: `@.specs/class-weakness-overview/REQUIREMENT.md`、`@.specs/class-weakness-overview/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T04[P]
Wave 2 (parallel): T05,      T06[P], T07[P]      (depends on Wave 1)
Wave 3:             T08                            (depends on Wave 2)
Wave 4:             T09                            (depends on Wave 3)
Wave 5:             T10                            (depends on Wave 4)
Wave 6 (parallel):  T11[P],   T12[P]              (depends on Wave 5)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>QueryIntent 枚举 + KeywordIntentRecognitionStrategy 关键词新增</name>
  <read_files>
    application/query/chat/model/QueryIntent.java
    application/query/chat/intent/KeywordIntentRecognitionStrategy.java
  </read_files>
  <write_files>
    application/query/chat/model/QueryIntent.java
    application/query/chat/intent/KeywordIntentRecognitionStrategy.java
  </write_files>
  <action>
    见 DESIGN D1、ADR-046。

    1. QueryIntent.java：
       - 新增 CLASS_WEAKNESS_OVERVIEW 枚举值：
         displayName="班级薄弱概览"
         description="聚合全班学生在指定学科上的 MASTERS 数据，统计薄弱知识点排行，分析共性根因"
       - 保留 v2 预留注释（KP_ANALYSIS / PREREQUISITE_CHAIN / GENERAL），
         仅取消 CLASS_OVERVIEW 的注释并重命名为 CLASS_WEAKNESS_OVERVIEW

    2. KeywordIntentRecognitionStrategy.java：
       - buildKeywordMap() 中新增班级相关关键词条目：
         "班级" → CLASS_WEAKNESS_OVERVIEW
         "全班" → CLASS_WEAKNESS_OVERVIEW
         "某班" → CLASS_WEAKNESS_OVERVIEW
       - 关键词放在 STUDENT_DIAGNOSIS 关键词之后（LinkedHashMap 保持插入顺序），
         STUDENT_DIAGNOSIS 的"薄弱""分析学生"优先匹配
  </action>
  <verify>grep -n "CLASS_WEAKNESS_OVERVIEW\|班级薄弱概览" src/main/java/com/graphnexus/application/query/chat/model/QueryIntent.java src/main/java/com/graphnexus/application/query/chat/intent/KeywordIntentRecognitionStrategy.java</verify>
  <done>QueryIntent 枚举含 CLASS_WEAKNESS_OVERVIEW；KeywordIntentRecognitionStrategy 含"班级""全班""某班"三个关键词</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>4 个班级概览 Prompt 模板文件创建</name>
  <read_files>
    src/main/resources/prompts/student-diagnosis-system.md
    src/main/resources/prompts/student-diagnosis-user.md
    src/main/resources/prompts/student-diagnosis-system-html.md
    src/main/resources/prompts/student-diagnosis-user-html.md
  </read_files>
  <write_files>
    src/main/resources/prompts/class-weakness-overview-system.md
    src/main/resources/prompts/class-weakness-overview-user.md
    src/main/resources/prompts/class-weakness-overview-system-html.md
    src/main/resources/prompts/class-weakness-overview-user-html.md
  </write_files>
  <action>
    见 DESIGN D6、D7、ADR-044 D2。参考 student-diagnosis-*.md 的结构，创建班级概览专用模板。

    1. class-weakness-overview-system.md（Markdown 版 system prompt）：
       - 角色：班级教学诊断专家（非学生个体专家）
       - 分析框架：① 班级整体掌握度概览 → ② 薄弱知识点排行（按薄弱人数降序）→ ③ 共性根因分析（依赖链视角）→ ④ 教学建议（班级层面干预策略）
       - 输出格式约束：以 ## 标题开头，禁止前导语，至少含 1 个列表和 1 个表格
       - 变量占位符：{{className}}/{{classSize}}/{{subject}}/{{weakThreshold}}/{{maxHops}}

    2. class-weakness-overview-user.md（Markdown 版 user prompt）：
       - 注入班级子图数据 + 用户问题
       - 变量占位符：{{className}}/{{classSize}}/{{subject}}/{{subgraphText}}/{{userQuestion}}/{{weakThreshold}}/{{maxHops}}/{{mastersAvailable}}

    3. class-weakness-overview-system-html.md（HTML+SVG 版 system prompt）：
       - 角色同上（班级诊断专家）
       - 输出格式约束：以 HTML 标签开头（&lt;h2&gt;/&lt;div&gt;），禁止 Markdown 标记
       - 必须含至少 1 个 &lt;svg&gt; 元素
       - SVG 约束：xmlns + viewBox，柱宽≥30px/间距≥10px/字体≥12px，最大画布 800×600，每图表最多 10 个数据点
       - 结构要求：h2 标题 → h3 班级信息 → table 整体掌握度 → h3 薄弱知识点排行 → svg 柱状图/条形图 → h3 依赖链 → svg 拓扑图 → h3 教学建议 → p 文本
       - 含正确示例（完整 HTML+SVG 片段）

    4. class-weakness-overview-user-html.md（HTML+SVG 版 user prompt）：
       - 与 Markdown 版 user prompt 结构相同，仅去掉 Markdown 格式约束
       - 变量占位符同 Markdown 版
  </action>
  <verify>ls -la src/main/resources/prompts/class-weakness-overview-system.md src/main/resources/prompts/class-weakness-overview-user.md src/main/resources/prompts/class-weakness-overview-system-html.md src/main/resources/prompts/class-weakness-overview-user-html.md</verify>
  <done>4 个模板文件创建完毕；含 {{var}} 占位符；含 SVG 约束；含 few-shot 示例</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>Repository 层新增班级查询方法</name>
  <read_files>
    infrastructure/mysql/file/repository/ExamRecordRepository.java
    infrastructure/mysql/file/entity/ExamRecordDO.java
    infrastructure/neo4j/repository/QueryGraphRepository.java
  </read_files>
  <write_files>
    infrastructure/mysql/file/repository/ExamRecordRepository.java
    infrastructure/neo4j/repository/QueryGraphRepository.java
  </write_files>
  <action>
    见 DESIGN D3、ADR-044 D1。

    1. ExamRecordRepository 新增：
       - findDistinctStudentsByClassName(String className)：按班级名查询不重复学生
         @Query("SELECT DISTINCT e.studentNo AS studentNo, e.name AS name, e.className AS className " +
                "FROM ExamRecordDO e WHERE e.className = :className")
         List&lt;Object[]&gt; findDistinctStudentsByClassName(String className);
       - findByStudentNoIn(List&lt;String&gt; studentNos)：批量查询学生记录（降级路径用）
         List&lt;ExamRecordDO&gt; findByStudentNoIn(List&lt;String&gt; studentNos);
         沿用 Spring Data JPA 方法名派生，无需 @Query

    2. QueryGraphRepository 新增：
       - findMastersByStudentNos(List&lt;String&gt; studentNos, String subjectName)：
         批量查询多个学生的 MASTERS 边（一次 Cypher 替代 N 次逐生查询）
         Cypher: MATCH (s:Student)-[m:MASTERS]->(kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(sub:Subject {name: $name})
         WHERE s.studentNo IN $studentNos
         RETURN s.studentNo AS studentNo, s.name AS studentName, kp.id AS kpId,
                COALESCE(kp.name, '未命名知识点') AS kpName, m.weight AS weight, m.description AS description
         沿用既有 Neo4jClient.query().bindAll().fetch().all() 模式
  </action>
  <verify>grep -n "findDistinctStudentsByClassName\|findByStudentNoIn" src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java && grep -n "findMastersByStudentNos" src/main/java/com/graphnexus/infrastructure/neo4j/repository/QueryGraphRepository.java</verify>
  <done>ExamRecordRepository 含 2 个新方法；QueryGraphRepository 含 1 个新方法；编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>DTO + PruningRequest 适配班级参数</name>
  <read_files>
    api/query/dto/chat/QueryAskRequest.java
    application/analysis/model/PruningRequest.java
  </read_files>
  <write_files>
    api/query/dto/chat/QueryAskRequest.java
    application/analysis/model/PruningRequest.java
  </write_files>
  <action>
    见 DESIGN D4、D9。

    1. QueryAskRequest.java：
       - 新增可选字段：
         @Schema(description = "班级名称（班级概览时使用，与 studentName/studentNo 互斥）", example = "初三(1)班")
         String className
       - 字段放在 subject 之后，保持向后兼容（旧调用方不传 → null，走学生诊断路径）

    2. PruningRequest.java：
       - Javadoc 更新 entityId 字段说明：
         "@param entityId 目标实体标识（STUDENT_DIAGNOSIS→studentNo，CLASS_WEAKNESS_OVERVIEW→className）"
       - record 签名不变（不新增字段，语义通过 Javadoc + params Map 扩展）
  </action>
  <verify>grep -n "className\|entityId.*studentNo.*className" src/main/java/com/graphnexus/api/query/dto/chat/QueryAskRequest.java src/main/java/com/graphnexus/application/analysis/model/PruningRequest.java</verify>
  <done>QueryAskRequest 含 className 可选字段；PruningRequest Javadoc 反映多语义 entityId</done>
  <depends_on></depends_on>
</task>

<task id="T05" status="pending">
  <name>ClassWeaknessOverviewStrategy 剪枝策略实现</name>
  <read_files>
    application/analysis/strategy/SubgraphPruningStrategy.java
    application/analysis/strategy/StudentDiagnosisStrategy.java
    application/analysis/model/PruningRequest.java
    application/analysis/model/PrunedSubgraph.java
    infrastructure/neo4j/repository/QueryGraphRepository.java
    infrastructure/mysql/file/repository/ExamRecordRepository.java
    application/graph/construction/model/GraphNodeData.java
    application/graph/construction/model/GraphEdgeData.java
    application/graph/construction/model/GraphDataConverter.java
  </read_files>
  <write_files>
    application/analysis/strategy/ClassWeaknessOverviewStrategy.java
  </write_files>
  <action>
    见 DESIGN D2、ADR-044。实现 SubgraphPruningStrategy 接口。

    @Component("CLASS_WEAKNESS_OVERVIEW")
    @RequiredArgsConstructor
    注入：QueryGraphRepository + ExamRecordRepository + ObjectMapper

    prune(PruningRequest) 分 5 步：

    Step 1 — 班级学生查询：
      - 从 entityId 获取 className
      - 调用 examRecordRepository.findDistinctStudentsByClassName(className)
      - 若结果为空 → emptyResult("CLASS_NOT_FOUND")
      - 提取 studentNo 列表 + classSize

    Step 2 — 批量 MASTERS 查询：
      - 调用 queryGraphRepository.findMastersByStudentNos(studentNos, subject)
      - 若结果非空 → mastersAvailable=true，按 KP 聚合 weight
      - 若结果为空 → mastersAvailable=false，降级：逐生查 TESTED 路径 +
        调用 examRecordRepository.findByStudentNoIn() 批量获取成绩，
        Java 层按 kpName 分组计算原始得分率（参考 StudentDiagnosisStrategy.calculateRawScoreRate 逻辑但批量化）

    Step 3 — Java 聚合（核心逻辑）：
      - 按 kpId groupBy，统计：
        * weakCount：weight < weakThreshold 的学生数
        * avgWeight：全班平均掌握度
        * minWeight：最低掌握度
        * maxWeight：最高掌握度
        * totalCount：有该 KP 数据的学生总数
      - 筛选薄弱 KP：avgWeight < weakThreshold 或 weakCount > 0
      - 按 weakCount 降序排列，取 Top 20

    Step 4 — 前置依赖链展开：
      - 提取薄弱 KP 的 kpId 列表
      - 调用 queryGraphRepository.findPrerequisitesUpstream(weakKpIds, maxHops)
      - 补全前置 KP 的名称（从 kpNameMap）
      - 前置 KP 的聚合数据若不存在于 MASTERS 结果中，标注为"仅依赖"

    Step 5 — 组装 PrunedSubgraph：
      - 节点：1 个虚拟班级节点（type="ClassInfo"，properties 含 className/classSize/subject）+ 薄弱 KP 节点 + 前置 KP 节点
      - 边：聚合 MASTERS 边（ClassInfo→KP，weight=avgWeight，description=JSON 聚合统计）+ PREREQUISITE_OF 边
      - 元信息：PruningMeta(strategy="CLASS_WEAKNESS_OVERVIEW", mastersAvailable, weakThreshold, maxHops, totalNodes, totalEdges)

    延续 StudentDiagnosisStrategy 的代码风格：
      - private static final 常量（DEFAULT_WEAK_THRESHOLD / DEFAULT_MAX_HOPS）
      - getParam() 辅助方法复用
      - buildKpNode() 逻辑一致（复用 KnowledgePointNode + GraphDataConverter）
      - 聚合 description JSON 使用 ObjectMapper 序列化
  </action>
  <verify>grep -n "@Component.*CLASS_WEAKNESS_OVERVIEW\|class ClassWeaknessOverviewStrategy\|implements SubgraphPruningStrategy\|public PrunedSubgraph prune" src/main/java/com/graphnexus/application/analysis/strategy/ClassWeaknessOverviewStrategy.java</verify>
  <done>ClassWeaknessOverviewStrategy 编译通过；@Component("CLASS_WEAKNESS_OVERVIEW") 自动注册到 PruningStrategyRegistry；5 步剪枝逻辑完整</done>
  <depends_on>T01, T03</depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>init.sql 种子数据 — 新增 4 条班级概览 Prompt 配置项</name>
  <read_files>
    src/main/resources/db/init.sql
  </read_files>
  <write_files>
    src/main/resources/db/init.sql
  </write_files>
  <action>
    见 DESIGN §9.3、CONTEXT.md「配置管理覆盖范围」。
    在 system_config 种子数据的 LLM_PROMPT 段（student-diagnosis 条目之后，intent-classification 条目之前），
    新增 4 条 IGNORE INSERT：

    ('prompt.class-weakness-overview-system', 'TEXT', 'LLM_PROMPT',
     '班级概览System Prompt', '班级薄弱概览的角色设定(Markdown)',
     'classpath:/prompts/class-weakness-overview-system.md', 0, 24),
    ('prompt.class-weakness-overview-user', 'TEXT', 'LLM_PROMPT',
     '班级概览User Prompt', '班级薄弱概览的用户消息模板(Markdown)',
     'classpath:/prompts/class-weakness-overview-user.md', 0, 25),
    ('prompt.class-weakness-overview-system-html', 'TEXT', 'LLM_PROMPT',
     '班级概览System Prompt(HTML)', '班级薄弱概览的角色设定(HTML+SVG)',
     'classpath:/prompts/class-weakness-overview-system-html.md', 0, 26),
    ('prompt.class-weakness-overview-user-html', 'TEXT', 'LLM_PROMPT',
     '班级概览User Prompt(HTML)', '班级薄弱概览的用户消息模板(HTML+SVG)',
     'classpath:/prompts/class-weakness-overview-user-html.md', 0, 27),

    同时更新此行上方的注释：`LLM_PROMPT 类（9 个提示词模板）` → `LLM_PROMPT 类（13 个提示词模板）`
  </action>
  <verify>grep -c "prompt.class-weakness-overview" src/main/resources/db/init.sql | grep 4</verify>
  <done>init.sql 含 4 条班级概览 prompt 种子数据；sort_order=24~27；config_value=NULL 表示默认使用 classpath</done>
  <depends_on>T02</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>intent-classification-system.md 新增 CLASS_WEAKNESS_OVERVIEW few-shot</name>
  <read_files>
    src/main/resources/prompts/intent-classification-system.md
    application/query/chat/model/QueryIntent.java
  </read_files>
  <write_files>
    src/main/resources/prompts/intent-classification-system.md
  </write_files>
  <action>
    见 AC-1、AC-2、AC-3、ADR-045。

    在意图分类 system prompt 中新增 CLASS_WEAKNESS_OVERVIEW 的 few-shot 示例：

    新增 few-shot：
    Q: "分析初三(1)班数学薄弱知识点" → {"intent":"CLASS_WEAKNESS_OVERVIEW"}
    Q: "初三(1)班数学哪些知识点需要加强" → {"intent":"CLASS_WEAKNESS_OVERVIEW"}
    Q: "帮我看看初三(1)班全班数学掌握情况" → {"intent":"CLASS_WEAKNESS_OVERVIEW"}

    新增边界 few-shot（班级 vs 学生区分）：
    Q: "分析初三(1)班张三的数学薄弱点" → {"intent":"STUDENT_DIAGNOSIS"}
    （含明确学生姓名 → 学生诊断优先）

    更新注意事项：
    - 当用户同时提到班级和学生姓名时，意图优先为学生诊断
    - {{intentList}} 占位符由 buildIntentList() 自动生成，新增枚举后自动包含 CLASS_WEAKNESS_OVERVIEW

    不修改 prompt 结构（角色设定/输出约束/negative example 保持不变）
  </action>
  <verify>grep -n "CLASS_WEAKNESS_OVERVIEW\|初三(1)班\|初三(1)班.*张三" src/main/resources/prompts/intent-classification-system.md</verify>
  <done>intent-classification-system.md 含 CLASS_WEAKNESS_OVERVIEW 的 3 个正向 few-shot + 1 个边界 few-shot；班级 vs 学生优先级规则已写明</done>
  <depends_on>T01</depends_on>
</task>

<task id="T08" status="pending">
  <name>QueryServiceImpl 新增班级私有方法（resolveClass + serializeClassSubgraph + buildClassTemplateVars）</name>
  <read_files>
    application/query/chat/service/impl/QueryServiceImpl.java
    application/query/chat/model/QueryIntent.java
    application/analysis/strategy/ClassWeaknessOverviewStrategy.java
    application/analysis/model/PrunedSubgraph.java
    application/analysis/model/PruningRequest.java
    infrastructure/mysql/file/repository/ExamRecordRepository.java
    application/query/chat/config/QueryProperties.java
  </read_files>
  <write_files>
    application/query/chat/service/impl/QueryServiceImpl.java
  </write_files>
  <action>
    见 DESIGN D1、D6、D7、ADR-046 D2。

    在 QueryServiceImpl 中新增 3 个 private 方法（不修改任何现有 public 方法，确保零回归风险）：

    1. resolveClass(String className)：
       - 调用 examRecordRepository.findDistinctStudentsByClassName(className)
       - 若结果为空 → throw BusinessException(A0006, "未找到班级: " + className)
       - 返回 record：含 studentNos（List&lt;String&gt;）+ classSize（int）+ className
       - 定义内部 record：ClassInfo(String className, int classSize, List&lt;String&gt; studentNos)

    2. serializeClassSubgraph(PrunedSubgraph subgraph, String className, int classSize)：
       - 输出格式（见 DESIGN D6）：
         ```
         ## 班级信息
         - 班级: {className}
         - 学生数: {classSize}
         - 学科: {subject}

         ## 薄弱知识点排行
         | 排名 | 知识点 | 薄弱人数 | 平均掌握度 | 最低掌握度 |
         | ... |

         ## 前置依赖关系
         - **KP_A** → **KP_B**（依赖强度: 0.85）
         ```
       - 聚合 MASTERS 边的 description JSON 中包含完整统计，需在序列化时提取展示
       - 沿用 serializeSubgraph() 的节点索引 + 度中心性计算逻辑
       - 排序：按薄弱人数降序

    3. buildClassTemplateVars(String question, String className, int classSize,
                             String subject, String subgraphText, PrunedSubgraph subgraph)：
       - 返回 Map&lt;String, String&gt;，包含：
         * className / classSize / subject / subgraphText / userQuestion
         * weakThreshold / maxHops / mastersAvailable（与学生版一致）
       - 不包含 studentName / studentNo（班级场景不需要）

    代码风格延续：
      - 与既有 resolveStudent / serializeSubgraph / buildTemplateVars 方法对称
      - 放在对应学生版方法附近（便于对比维护）
  </action>
  <verify>grep -n "resolveClass\|serializeClassSubgraph\|buildClassTemplateVars" src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java | head -10</verify>
  <done>3 个新 private 方法编译通过；resolveClass 验证班级存在；serializeClassSubgraph 输出聚合视图；buildClassTemplateVars 含班级变量集</done>
  <depends_on>T01, T03, T05</depends_on>
</task>

<task id="T09" status="pending">
  <name>QueryServiceImpl 实体提取扩展 — className 字段</name>
  <read_files>
    application/query/chat/service/impl/QueryServiceImpl.java
    infrastructure/mysql/file/repository/ExamRecordRepository.java
  </read_files>
  <write_files>
    application/query/chat/service/impl/QueryServiceImpl.java
  </write_files>
  <action>
    见 DESIGN D5、ADR-045。

    在 T08 修改后的 QueryServiceImpl 基础上，扩展实体提取逻辑：

    1. ExtractedEntities record 新增字段：
       - String className（可为 null，与学生信息互斥）
       - 保留原有 studentName/studentNo/subject

    2. extractViaLlm() 扩展：
       - system prompt 中 JSON 输出格式改为：
         ```
         {"studentName":"...","studentNo":"...","className":"...","subject":"..."}
         studentName/studentNo 和 className 互斥（至少提取一组），无法确定的字段设为 null
         班级名称通常包含"班"字，如"初三(1)班"、"高一3班"
         ```
       - 解析逻辑新增 className 字段提取
       - 校验逻辑调整：studentName/studentNo 和 className 至少有一组非 null

    3. extractViaRegex() 扩展：
       - 新增 extractClassName(String question) 私有方法：
         Pattern.compile("([\\u4e00-\\u9fa5]{0,6}年级?[\\u4e00-\\u9fa5]?\\d+班)")
         匹配：初三(1)班 / 九年级1班 / 高一3班 / 初一(12)班
       - 在 extractViaRegex() 末尾调用 extractClassName，填充到 ExtractedEntities

    4. getKnownSubjects() 不变（学科列表仍从 MySQL exam_record 查）
  </action>
  <verify>grep -n "className\|extractClassName\|初三.*班.*正则\|年级.*班" src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java | head -15</verify>
  <done>ExtractedEntities 含 className 字段；LLM prompt + 正则均支持班级名提取；编译通过</done>
  <depends_on>T08</depends_on>
</task>

<task id="T10" status="pending">
  <name>QueryServiceImpl + QueryController 意图分支集成</name>
  <read_files>
    application/query/chat/service/impl/QueryServiceImpl.java
    application/query/chat/service/QueryService.java
    api/query/controller/QueryController.java
    api/query/dto/chat/QueryAskRequest.java
    application/query/chat/model/QueryIntent.java
  </read_files>
  <write_files>
    application/query/chat/service/impl/QueryServiceImpl.java
    api/query/controller/QueryController.java
  </write_files>
  <action>
    见 DESIGN D1、ADR-046 D0/D1。

    1. QueryServiceImpl.chat() 意图分支（主入口，Q2=C）：
       在意图识别后、实体提取前插入分支：

       ```
       chat(question):
         1. intent = recognizeIntent(question)
         2. entities = extractEntities(question, subjects)  // 统一提取，含 className
         3. if intent == CLASS_WEAKNESS_OVERVIEW:
              if entities.className == null:
                → 正则降级 extractClassName(question)
                仍为 null → throw A0019("无法从问题中识别班级名称...")
              return askClassWithIntent(question, entities.className, entities.subject, intent)
         4. else (STUDENT_DIAGNOSIS):
              现有逻辑不变
       ```

    2. 新增 askClassWithIntent() 方法：
       - 参数：(question, className, subject, intent)
       - 流程：resolveClass → pruningStrategyRegistry.get("CLASS_WEAKNESS_OVERVIEW").prune →
         serializeClassSubgraph → buildClassTemplateVars →
         promptTemplateService.buildPrompt(intent, vars, format) → callLlmWithRetry → persistTask
       - PruningRequest 构造：new PruningRequest("CLASS_WEAKNESS_OVERVIEW", className, subject, params)
       - 与 askWithIntent() 对称，但 entityId 传 className 而非 studentNo

    3. QueryController.ask() 适配：
       - 从 request.className() 读取可选班级名
       - 若 className 非空 → 调用 queryService.ask() 时传入 className
       - 若 className 为空 → 现有逻辑不变（传 null）
       - 注：QueryService.ask() 签名不变，QueryServiceImpl.ask() 内部按 className 是否为空分支：
         * className 非空 → CLASS_WEAKNESS_OVERVIEW 意图 → 班级流程
         * className 为空 → 现有 STUDENT_DIAGNOSIS 流程

    4. ask() 内部意图分支：
       ```
       ask(question, studentName, studentNo, className, subject):
         if className != null:
           intent = CLASS_WEAKNESS_OVERVIEW  // 显式参数优先
           classInfo = resolveClass(className)
           return askClassInternal(taskId, question, className, subject, intent)
         else:
           现有学生流程不变
       ```
       ask() 方法签名的 className 参数通过方法重载添加（新增 5 参数版本，原 4 参数版本保留并委托到新版本传 null）
  </action>
  <verify>grep -n "askClassWithIntent\|CLASS_WEAKNESS_OVERVIEW\|resolveClass\|classInfo\|serializeClassSubgraph\|buildClassTemplateVars" src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java | head -20</verify>
  <done>chat() 含 CLASS_WEAKNESS_OVERVIEW 分支；askClassWithIntent() 完整链路可执行；QueryController 传递 className；编译通过；STUDENT_DIAGNOSIS 路径零改动</done>
  <depends_on>T09</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>单元测试 — ClassWeaknessOverviewStrategy + 意图识别扩展</name>
  <read_files>
    application/analysis/strategy/ClassWeaknessOverviewStrategy.java
    application/query/chat/intent/KeywordIntentRecognitionStrategy.java
    application/query/chat/intent/IntentRecognitionService.java
    application/query/chat/registry/PruningStrategyRegistry.java
    src/test/java/com/graphnexus/application/query/chat/intent/KeywordIntentRecognitionStrategyTest.java
    src/test/java/com/graphnexus/application/query/chat/intent/IntentRecognitionServiceTest.java
    src/test/java/com/graphnexus/application/query/chat/registry/PruningStrategyRegistryTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/analysis/strategy/ClassWeaknessOverviewStrategyTest.java
    src/test/java/com/graphnexus/application/query/chat/intent/KeywordIntentRecognitionStrategyTest.java
    src/test/java/com/graphnexus/application/query/chat/intent/IntentRecognitionServiceTest.java
    src/test/java/com/graphnexus/application/query/chat/registry/PruningStrategyRegistryTest.java
  </write_files>
  <action>
    覆盖以下测试场景（JUnit 5 + Mockito，纯单元测试不启动 Spring 上下文）：

    1. ClassWeaknessOverviewStrategyTest（新建）：
       - mock QueryGraphRepository + ExamRecordRepository
       - 正常路径：班级 5 人，3 个 KP，2 个薄弱 → 断言 PrunedSubgraph 含 ClassInfo 节点 + 聚合 MASTERS 边
       - 班级不存在：findDistinctStudentsByClassName 返回空 → 断言 meta.strategy="CLASS_NOT_FOUND"
       - MASTERS 降级：findMastersByStudentNos 返回空 → 断言 meta.mastersAvailable=false
       - 无薄弱 KP：所有 avgWeight ≥ threshold → 断言 nodes 仅含 ClassInfo 节点
       - 聚合正确性：3 个学生对同一 KP 的 weight 分别为 0.3/0.5/0.7 → 断言 avg=0.5, weakCount=2

    2. KeywordIntentRecognitionStrategyTest（扩展）：
       - "分析初三(1)班全班数学薄弱点" → CLASS_WEAKNESS_OVERVIEW（含"全班"）
       - "初三(1)班数学班级薄弱知识点分析" → CLASS_WEAKNESS_OVERVIEW（含"班级"）
       - 验证"薄弱"关键词仍优先匹配 STUDENT_DIAGNOSIS 而非 CLASS_WEAKNESS_OVERVIEW
         （因 STUDENT_DIAGNOSIS 关键词在 LinkedHashMap 中先插入）

    3. IntentRecognitionServiceTest（扩展）：
       - 注入 mock LlmIntentStrategy 返回 CLASS_WEAKNESS_OVERVIEW → 验证链在首个策略处停止
       - 注入 2 个策略（均返回 null）→ 验证抛 BusinessException(A0019)

    4. PruningStrategyRegistryTest（扩展）：
       - 注入 Map("CLASS_WEAKNESS_OVERVIEW" → mockClassStrategy) → get("CLASS_WEAKNESS_OVERVIEW") 返回 mock
  </action>
  <verify>mvn test -pl . -Dtest="ClassWeaknessOverviewStrategyTest,KeywordIntentRecognitionStrategyTest,IntentRecognitionServiceTest,PruningStrategyRegistryTest" -DfailIfNoTests=false 2>&1 | tail -20</verify>
  <done>4 个测试类全部绿色；覆盖 AC-1/AC-4/AC-6/AC-7/AC-8/AC-14 的关键路径</done>
  <depends_on>T10</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>集成测试 — 班级概览端到端验证 + 学生诊断回归</name>
  <read_files>
    src/test/java/com/graphnexus/api/query/controller/QueryControllerIntegrationTest.java
    application/query/chat/service/impl/QueryServiceImpl.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/api/query/controller/QueryControllerIntegrationTest.java
  </write_files>
  <action>
    扩展既有 QueryControllerIntegrationTest（@SpringBootTest + @ActiveProfiles("dev")，
    LlmGateway 使用 @MockBean）：

    新增测试用例：

    1. AC-1 验证：LLM 意图识别班级概览 —
       mock LlmGateway 意图分类返回 {"intent":"CLASS_WEAKNESS_OVERVIEW"}
       + 实体提取返回 {"className":"初三(1)班","subject":"数学"}
       + 分析返回合法 HTML+SVG →
       POST /chat → 断言 HTTP 200 + intent=CLASS_WEAKNESS_OVERVIEW

    2. AC-4 验证：关键词 fallback —
       mock 意图分类抛异常 + POST /chat（含"全班"关键词）→ 断言 HTTP 200 + intent=CLASS_WEAKNESS_OVERVIEW

    3. AC-6 验证：班级剪枝正常路径 —
       确保 Neo4j 中存在初三(1)班学生 + MASTERS 边 →
       POST /chat → 断言 prunedNodes ≥ 6 + prunedEdges ≥ 3

    4. AC-9 验证：HTML+SVG 输出 —
       配置 output-format=html-svg，mock LLM 分析返回合法 HTML+SVG →
       断言 outputFormat=html-svg + answer 以 &lt; 开头

    5. AC-10 验证：Markdown 输出 —
       配置 output-format=markdown →
       断言 outputFormat=markdown + answer 以 # 开头

    6. AC-13 验证：学生诊断不退化 —
       发送学生诊断请求（"分析张三数学薄弱点"）→ 断言 intent=STUDENT_DIAGNOSIS + 行为与原有完全一致

    7. AC-8 验证：班级不存在 —
       mock 班级查询返回空 → POST /chat（不存在的班级名）→ 断言 HTTP 400 + errorCode=A0006

    测试依赖：LlmGateway 使用 @MockBean（mock 意图分类 + 实体提取 + 分析三次调用的返回值），
    Neo4j/MySQL 使用真实 podman 容器
  </action>
  <verify>mvn test -pl . -Dtest="QueryControllerIntegrationTest" -DfailIfNoTests=false 2>&1 | tail -30</verify>
  <done>集成测试全部绿色；覆盖 AC-1/AC-4/AC-6/AC-8/AC-9/AC-10/AC-13</done>
  <depends_on>T10</depends_on>
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