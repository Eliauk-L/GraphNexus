# TASK: 宽图谱融合 — 多源知识图谱合并与掌握度聚合

- **Change ID**: `wide-graph-fusion`
- **关联**: `@.specs/wide-graph-fusion/REQUIREMENT.md`、`@.specs/wide-graph-fusion/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T04[P], T05[P], T06[P], T07[P]
Wave 2 (parallel): T08[P], T09[P], T10[P]        (depends on T02,T03,T06,T07)
Wave 3:            T11                             (depends on T04,T05,T08,T09,T10)
Wave 4 (parallel): T12[P], T13[P], T14[P]         (depends on T11)
Wave 5 (parallel): T15[P], T16[P]                 (depends on T11,T14)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>ErrorCode 新增融合与回滚错误码 A0012~A0014</name>
  <read_files>
    common/exception/ErrorCode.java
  </read_files>
  <write_files>
    common/exception/ErrorCode.java
  </write_files>
  <action>
    在 ErrorCode 枚举中新增 3 个错误码（见 DESIGN D7/D9）：
    - A0012 (NOT_FOUND, "融合日志不存在"): 回滚时 fusionLogId 查不到
    - A0013 (CONFLICT, "融合进行中，请稍后重试"): 并发控制，已有 RUNNING 状态融合
    - A0014 (CONFLICT, "图状态已变更，无法回滚"): 回滚前一致性校验失败
    沿用既有 ErrorCode 枚举模式：(errorCode, httpStatus, defaultUserTip) 三参数构造。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；ErrorCode 枚举增加 3 个新常量，含正确 HTTP 状态码映射</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>EdgeType 新增 MASTERS + MastersEdge 边类</name>
  <read_files>
    infrastructure/neo4j/edge/EdgeType.java
    infrastructure/neo4j/edge/GraphEdge.java
    infrastructure/neo4j/edge/TestedEdge.java
  </read_files>
  <write_files>
    infrastructure/neo4j/edge/EdgeType.java
    infrastructure/neo4j/edge/MastersEdge.java
  </write_files>
  <action>
    EdgeType 枚举新增 MASTERS("MASTERS")。
    MastersEdge 继承 GraphEdge，构造器接受 (studentNodeId, kpNodeId)，
    设置 edgeType=MASTERS。weight 和 description 通过 GraphEdge 基类继承。
    参考 TestedEdge 的实现模式（见 ADR-002 的边抽象体系）。
    MASTERS 边方向：(Student)-[:MASTERS]->(KnowledgePoint)。
  </action>
  <verify>mvn test -pl . -Dtest="GraphNodeAbstractionTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>编译通过；EdgeType.MASTERS 可正常使用；现有图节点/边抽象测试不退化</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>KnowledgePointNode 新增 fusionSource 字段</name>
  <read_files>
    infrastructure/neo4j/node/KnowledgePointNode.java
    infrastructure/neo4j/node/GraphNode.java
  </read_files>
  <write_files>
    infrastructure/neo4j/node/KnowledgePointNode.java
  </write_files>
  <action>
    KnowledgePointNode 新增字段：
    - fusionSource (String): 标记融合来源，默认 null。
      文档抽取 KP 构造时设为 "DOCUMENT"，
      CSV 导入 KP 构造时设为 "CSV_IMPORT"，
      融合后规范 KP 拼接为 "DOCUMENT,CSV_IMPORT"。
    toProperties() 覆盖追加 fusionSource 字段映射。
    新增含 fusionSource 参数的构造器重载，保持旧构造器兼容（默认 null）。
    见 DESIGN D10。
  </action>
  <verify>mvn test -pl . -Dtest="GraphNodeAbstractionTest" 2>&1 | grep "Tests run"</verify>
  <done>编译通过；KnowledgePointNode 可携带 fusionSource；toProperties() 输出含该字段</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>FusionProperties — yml 融合配置绑定类</name>
  <read_files>
    infrastructure/storage/config/MinioProperties.java
  </read_files>
  <write_files>
    application/graph/fusion/config/FusionProperties.java
    application/graph/fusion/config/package-info.java
  </write_files>
  <action>
    新建 @ConfigurationProperties("fusion") 绑定类 FusionProperties：
    - kpMatching.strategy (String, 默认 "fuzzy")
    - kpMatching.threshold (double, 默认 0.85)
    - kpMatching.fuzzy.alpha (double, 默认 0.3)
    - kpMatching.fuzzy.beta (double, 默认 0.5)
    - kpMatching.fuzzy.gamma (double, 默认 0.2)
    - weight.strategy (String, 默认 "time-decay")
    - weight.timeDecay.factor (double, 默认 0.9)
    - rollback.weightTolerance (double, 默认 0.01)
    使用 Java record 或 @Data，参考 MinioProperties 的 @ConfigurationProperties 模式。
    在 application-dev.yml 中追加上述配置段（含注释）。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；application-dev.yml 含 fusion 配置段；FusionProperties 可被 Spring 注入</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>FusionLogDO + FusionLogRepository — MySQL 融合日志持久化</name>
  <read_files>
    infrastructure/mysql/document/ExamRecordDO.java
    infrastructure/mysql/document/ExamRecordRepository.java
    infrastructure/mysql/config/JpaAuditConfig.java
  </read_files>
  <write_files>
    infrastructure/mysql/fusion/FusionLogDO.java
    infrastructure/mysql/fusion/FusionLogRepository.java
    infrastructure/mysql/fusion/package-info.java
  </write_files>
  <action>
    新建 FusionLogDO（JPA Entity → MySQL fusion_log 表）：
    - id (IDENTITY 自增)
    - triggerType (VARCHAR: MANUAL_FULL / AUTO_INCREMENTAL)
    - status (VARCHAR: RUNNING / COMPLETED / ROLLED_BACK)
    - mergedKpGroupCount (INT)
    - mastersEdgeCount (INT)
    - fusionDetailJson (MEDIUMTEXT, JSON 列 — 融合明细快照，见 ADR-007)
    - mastersSnapshotJson (MEDIUMTEXT, JSON 列 — MASTERS 变更快照)
    - rolledBack (Boolean, 默认 false)
    - executedAt (LocalDateTime)
    - createTime / updateTime (审计字段，@CreatedDate/@LastModifiedDate)
    FusionLogRepository 继承 JpaRepository<FusionLogDO, Long>：
    - findTopByStatusOrderByExecutedAtDesc(String status) — 并发检查用
    - findTopByOrderByExecutedAtDesc() — 状态查询用
    参考 ExamRecordDO 的 JPA 注解模式。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；FusionLogDO JPA 映射正确；FusionLogRepository 接口可用</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>KpMatchingStrategy 接口 + ExactMatchStrategy 测试桩</name>
  <read_files>
    application/document/parser/FileParser.java
    infrastructure/neo4j/node/KnowledgePointNode.java
  </read_files>
  <write_files>
    application/graph/fusion/strategy/KpMatchingStrategy.java
    application/graph/fusion/strategy/ExactMatchStrategy.java
    application/graph/fusion/model/KpCandidate.java
    application/graph/fusion/model/package-info.java
    application/graph/fusion/strategy/package-info.java
  </write_files>
  <action>
    KpMatchingStrategy 接口（见 ADR-006 § Decision）：
    - double match(KpCandidate a, KpCandidate b): 返回 0~1 相似度
    - String getName(): 策略标识，对应 yml 中 fusion.kp-matching.strategy 值
    KpCandidate record：包含 name、subject、documentId、fusionSource 四个字段，
      从 KnowledgePointNode 属性提取，用于匹配判断。
    ExactMatchStrategy（测试桩，验证 AC-11）：
    - match() 逻辑：a.name.equalsIgnoreCase(b.name) && a.subject.equals(b.subject) → 1.0，否则 0.0
    - getName() 返回 "exact"
    参考 FileParser 接口的扩展设计模式（见 D12 策略+工厂）。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；KpMatchingStrategy 接口契约清晰；ExactMatchStrategy 可作为 yml 配置切换目标</done>
  <depends_on></depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>WeightCalculationStrategy 接口 + SimpleAverageStrategy 测试桩</name>
  <read_files>
    application/graph/fusion/strategy/KpMatchingStrategy.java
  </read_files>
  <write_files>
    application/graph/fusion/strategy/WeightCalculationStrategy.java
    application/graph/fusion/strategy/SimpleAverageStrategy.java
    application/graph/fusion/model/TestedRecord.java
    application/graph/fusion/model/WeightResult.java
  </write_files>
  <action>
    WeightCalculationStrategy 接口（见 ADR-007 § Decision）：
    - WeightResult calculate(List<TestedRecord> records): 输入成绩记录列表，输出 weight + 摘要 JSON
    - String getName(): 策略标识
    TestedRecord record：examDate(LocalDate)、rawScore(Double)、maxScore(Double)、kpName(String)。
      rawScore 为 null 表示缺考。
    WeightResult record：weight(Double 0~1)、summaryJson(String)。
    SimpleAverageStrategy（测试桩，验证 AC-12）：
    - 简单算术平均：Σ(rawScore/maxScore) / n，缺考跳过
    - summaryJson 仅含 {"examCount": n}
    - getName() 返回 "simple-average"
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；WeightCalculationStrategy 接口契约清晰；SimpleAverageStrategy 可用</done>
  <depends_on></depends_on>
</task>

<task id="T08" parallel="true" status="done">
  <name>FuzzyMatchStrategy v1 实现 — 多度量组合模糊匹配</name>
  <read_files>
    application/graph/fusion/strategy/KpMatchingStrategy.java
    application/graph/fusion/model/KpCandidate.java
    application/graph/fusion/config/FusionProperties.java
  </read_files>
  <write_files>
    application/graph/fusion/strategy/FuzzyMatchStrategy.java
  </write_files>
  <action>
    实现 FuzzyMatchStrategy（见 ADR-006 § Decision + DESIGN D1）：
    - 名称归一化：全角转半角、trim、去标点符号、小写
    - 前置过滤：subject 不同直接返回 0.0
    - 三度量组合：combinedScore = α×charJaccard + β×bigramJaccard + γ×(1-normLevenshtein)
      - charJaccard: 字符集交集/并集
      - bigramJaccard: 二字滑动窗口交集/并集
      - normLevenshtein: 编辑距离 / max(lenA, lenB)
    - 权重 α/β/γ 从 FusionProperties 读取
    - 结果与 threshold 比较（也来自 FusionProperties）
    - getName() 返回 "fuzzy"
    - 注入 FusionProperties，使用构造器注入（@RequiredArgsConstructor）
    注意：不引入外部 NLP 库，手写编辑距离和 Jaccard 计算。
  </action>
  <verify>mvn test -pl . -Dtest="FuzzyMatchStrategyTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>编译通过；FuzzyMatchStrategy 单元测试覆盖："二次函数顶点坐标" vs "顶点坐标公式"（相似度合理）、"一次函数图像" vs "二次函数图像"（低于阈值，不合并）</done>
  <depends_on>T06</depends_on>
</task>

<task id="T09" parallel="true" status="done">
  <name>TimeDecayStrategy v1 实现 — 时间衰减加权平均</name>
  <read_files>
    application/graph/fusion/strategy/WeightCalculationStrategy.java
    application/graph/fusion/model/TestedRecord.java
    application/graph/fusion/model/WeightResult.java
    application/graph/fusion/config/FusionProperties.java
  </read_files>
  <write_files>
    application/graph/fusion/strategy/TimeDecayStrategy.java
  </write_files>
  <action>
    实现 TimeDecayStrategy（见 ADR-007 § Decision + DESIGN D2）：
    - 过滤缺考：rawScore == null → 跳过
    - 同考试多题考同一 KP 先取平均（按 examDate 分组 → 组内平均得分率）
    - 时间衰减：decayWeight = factor^(monthsAgo)，monthsAgo = (now - examDate) / 30 取整
    - 最终 weight = Σ(avgScoreRate_i × decayWeight_i) / Σ(decayWeight_i)
    - 衰减因子 factor 从 FusionProperties 读取
    - summaryJson 含 examCount、lastExamDate、details[]（每条含 examDate/scoreRate/decayWeight）
    - 空记录列表 → weight = 0.0，summaryJson = {"examCount": 0}
    - getName() 返回 "time-decay"
    - 注入 FusionProperties，使用构造器注入
    验证逻辑：单次考试时 weight = scoreRate（衰减因子在分子分母抵消 — 见 ADR-007 Consequences）。
  </action>
  <verify>mvn test -pl . -Dtest="TimeDecayStrategyTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>编译通过；TimeDecayStrategy 单元测试覆盖：AC-4 示例（weight≈0.554）、单次考试（weight=scoreRate）、缺考跳过</done>
  <depends_on>T07</depends_on>
</task>

<task id="T10" parallel="true" status="done">
  <name>GraphNodeRepository 新增融合相关 Cypher 方法</name>
  <read_files>
    infrastructure/neo4j/repository/GraphNodeRepository.java
    infrastructure/neo4j/node/KnowledgePointNode.java
    infrastructure/neo4j/node/StudentNode.java
    infrastructure/neo4j/edge/MastersEdge.java
    infrastructure/neo4j/edge/EdgeType.java
  </read_files>
  <write_files>
    infrastructure/neo4j/repository/GraphNodeRepository.java
  </write_files>
  <action>
    GraphNodeRepository 新增以下方法（见 DESIGN D4/D5 + 0.5.2）：
    
    1. findAllKnowledgePointsBySubject(String subject):
       MATCH (kp:KnowledgePoint {subject: $subject}) RETURN kp
       返回 List<Map<String,Object>>（含 id/name/subject/documentId/fusionSource）
    
    2. findKnowledgePointsByNames(List<String> names, String subject):
       MATCH (kp:KnowledgePoint) WHERE kp.name IN $names AND kp.subject = $subject RETURN kp
       增量融合用，仅查询受影响 KP
    
    3. redirectEdges(String fromKpId, String toKpId, List<EdgeRedirect> redirects):
       对每条 redirect：MATCH (a)-[r:type]->(b) WHERE id(a)=from OR id(b)=from
       → CREATE 新边指向 toKpId → DELETE 旧边 r
       见 DESIGN D4 就地升级算法
    
    4. deleteKnowledgePoints(List<String> kpIds):
       MATCH (kp:KnowledgePoint) WHERE kp.id IN $ids DETACH DELETE kp
       融合后清理冗余 KP
    
    5. batchUpsertMastersEdges(String studentNodeId, List<MastersEdgeData> edges):
       UNWIND $edges AS edge
       MATCH (s:Student {id: edge.studentId}), (kp:KnowledgePoint {id: edge.kpId})
       MERGE (s)-[r:MASTERS]->(kp)
       SET r.weight = edge.weight, r.description = edge.description
       按 Student 分组调用（见 DESIGN D5）
    
    6. findStudentsByKnowledgePointNames(List<String> kpNames, String subject):
       MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint)
       WHERE kp.name IN $names AND kp.subject = $subject
       RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentId
       增量融合时确定受影响学生范围
    
    7. findMastersEdgesByStudent(String studentNodeId):
       MATCH (s:Student {id: $id})-[r:MASTERS]->(kp:KnowledgePoint)
       RETURN kp.name AS kpName, r.weight AS weight
       回滚一致性校验用
    
    所有方法复用 Neo4jClient.query().bindAll().run() 模式，不引入 SDN Repository。
    新增内部辅助 record：EdgeRedirect(String type, String fromNodeId, String toNodeId, String direction)、
    MastersEdgeData(String studentId, String kpId, double weight, String description)。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；GraphNodeRepository 新增 7 个融合相关方法，沿用 Neo4jClient 手动 Cypher 模式</done>
  <depends_on>T02,T03</depends_on>
</task>

<task id="T11" status="done">
  <name>FusionService 接口 + FusionServiceImpl — 融合引擎核心实现</name>
  <read_files>
    application/graph/fusion/strategy/KpMatchingStrategy.java
    application/graph/fusion/strategy/WeightCalculationStrategy.java
    application/graph/fusion/strategy/FuzzyMatchStrategy.java
    application/graph/fusion/strategy/TimeDecayStrategy.java
    application/graph/fusion/strategy/ExactMatchStrategy.java
    application/graph/fusion/strategy/SimpleAverageStrategy.java
    application/graph/fusion/config/FusionProperties.java
    application/graph/fusion/model/KpCandidate.java
    application/graph/fusion/model/TestedRecord.java
    application/graph/fusion/model/WeightResult.java
    infrastructure/neo4j/repository/GraphNodeRepository.java
    infrastructure/mysql/fusion/FusionLogDO.java
    infrastructure/mysql/fusion/FusionLogRepository.java
    infrastructure/mysql/document/ExamRecordRepository.java
    infrastructure/mysql/document/ExamRecordDO.java
    common/exception/BusinessException.java
    common/exception/ErrorCode.java
    application/document/model/GradeUploadResultBO.java
    application/graph/model/ExtractionResultBO.java
  </read_files>
  <write_files>
    application/graph/fusion/service/FusionService.java
    application/graph/fusion/service/impl/FusionServiceImpl.java
    application/graph/fusion/service/impl/package-info.java
    application/graph/fusion/service/package-info.java
    application/graph/fusion/model/FusionGroup.java
    application/graph/fusion/model/FusionExecuteResult.java
    application/graph/fusion/model/FusionStatusResult.java
    application/graph/fusion/model/FusionRollbackResult.java
  </write_files>
  <action>
    新建 FusionService 接口，FusionServiceImpl 实现（见 DESIGN §2.1-2.3 + D3/D4/D5/D6/D7/D8/D9）：
    
    【接口方法】
    - FusionExecuteResult fuseFull(): 全量融合
    - FusionExecuteResult fuseIncremental(List<String> kpNames, String subject): 增量融合
    - FusionRollbackResult rollback(Long fusionLogId): 回滚
    - FusionStatusResult getStatus(): 查询最近融合状态
    
    【全量融合 fuseFull() — 见 DESIGN §2.1】
    1. 并发检查：fusionLogRepository.findTopByStatusOrderByExecutedAtDesc("RUNNING")
       → 存在则抛 BusinessException(A0013)
    2. 写 fusion_log status=RUNNING（MySQL 事务）
    3. 查询所有 KP（graphNodeRepository.findAllKnowledgePointsBySubject — 按 subject 分组）
    4. KP 匹配分组（见 D1/D3）：
       - 按 subject 分桶 → 桶内两两匹配（调用 kpMatchingStrategy.match()）
       - 相似度 ≥ threshold → 归入同一 FusionGroup
       - 每组选主 KP：documentId 非空优先 → fusionSource 含 "DOCUMENT" 优先 → 第一个
    5. 融合执行（对每组，Neo4j 事务）：
       - redirectEdges（其余 KP 的边 → 主 KP）
       - deleteKnowledgePoints（删除冗余 KP）
    6. MASTERS 全量重算（见 D2/D5）：
       - 查 MySQL exam_record 获取所有 (studentNo, kpName) 的 TestedRecord 列表
       - 调用 weightCalculationStrategy.calculate() 得到 weight + summaryJson
       - 按 Student 分组 batchUpsertMastersEdges
    7. 构建 fusionDetailJson + mastersSnapshotJson（见 ADR-008 JSON schema）
    8. 更新 fusion_log status=COMPLETED + 填充 JSON 列（MySQL 事务）
    9. 返回 FusionExecuteResult（fusionLogId, mergedKpGroupCount, mastersEdgeCount）
    
    【增量融合 fuseIncremental() — 见 DESIGN §2.2 + ADR-009】
    范围限定：仅查询 affectedKpNames 的 KP → 匹配分组 → 合并 → MASTERS 仅重算受影响 Student
    triggerType = AUTO_INCREMENTAL。其余流程同全量融合。
    
    【回滚 rollback() — 见 DESIGN §2.3 + ADR-008】
    1. 查 fusion_log → 不存在抛 A0012
    2. 已回滚 → 幂等返回成功
    3. 一致性校验：规范 KP 存在 + MASTERS weight ≈ newWeight（容差来自 FusionProperties）
       → 失败抛 A0014
    4. 逆向恢复：DETACH DELETE 规范 KP → CREATE 源 KP → CREATE 原始边 → UPDATE/DELETE MASTERS
    5. 标记 rolledBack=true
    
    【状态查询 getStatus()】
    查最近一条 fusion_log → 返回 FusionStatusResult
    
    【策略选择】通过 FusionProperties 的 strategy 字段，在 postConstruct 或 setter 中
    从 Spring 容器获取对应 strategy bean（KpMatchingStrategy/WeightCalculationStrategy 各有多个实现）。
    可用 Map<String, KpMatchingStrategy> 注入（Spring 自动收集所有实现），按 name 查找。
    
    注入依赖：GraphNodeRepository, FusionLogRepository, ExamRecordRepository,
    Map<String, KpMatchingStrategy>, Map<String, WeightCalculationStrategy>, FusionProperties。
    构造器注入（@RequiredArgsConstructor）。
  </action>
  <verify>mvn test -pl . -Dtest="FusionServiceTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>编译通过；FusionService 单元测试覆盖：全量融合（mock Neo4j/MySQL）、增量融合（仅 affected 范围）、回滚（快照驱动逆向恢复）、并发拒绝（RUNNING 状态检查）</done>
  <depends_on>T04,T05,T08,T09,T10</depends_on>
</task>

<task id="T12" parallel="true" status="done">
  <name>GradeServiceImpl 增量融合钩子</name>
  <read_files>
    application/document/service/impl/GradeServiceImpl.java
    application/graph/fusion/service/FusionService.java
  </read_files>
  <write_files>
    application/document/service/impl/GradeServiceImpl.java
  </write_files>
  <action>
    在 GradeServiceImpl.uploadGradeCsv() 中 Neo4j 图构建完成后（见现有第 154 行 return 前），
    新增增量融合调用（见 ADR-009 § Decision）：
    
    ```java
    // 增量融合（见 ADR-009）
    try {
        fusionService.fuseIncremental(payload.knowledgePoints(), payload.subject());
    } catch (Exception e) {
        log.error("增量融合失败（CSV 上传后），examNo={}, kps={}，可手动全量融合修复",
                payload.examNo(), payload.knowledgePoints(), e);
    }
    ```
    
    注入 FusionService（构造器新增 1 个参数）。
    增量融合失败不阻塞主流程（仅 ERROR 日志，不抛异常）。
  </action>
  <verify>mvn test -pl . -Dtest="GradeServiceImplTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>编译通过；GradeServiceImpl 在 CSV 上传成功后调用 fusionService.fuseIncremental()；mock 验证调用次数=1</done>
  <depends_on>T11</depends_on>
</task>

<task id="T13" parallel="true" status="done">
  <name>GraphServiceImpl 增量融合钩子</name>
  <read_files>
    application/graph/service/impl/GraphServiceImpl.java
    application/graph/service/GraphService.java
    application/graph/fusion/service/FusionService.java
  </read_files>
  <write_files>
    application/graph/service/impl/GraphServiceImpl.java
  </write_files>
  <action>
    在 GraphServiceImpl.extract() 中 Neo4j 写入完成后（见现有第 83 行 return 前），
    新增增量融合调用（见 ADR-009 § Decision）：
    
    ```java
    // 增量融合（见 ADR-009）
    try {
        List<String> affectedKpNames = extracted.knowledgePoints().stream()
                .map(KnowledgePointNode::getName).toList();
        fusionService.fuseIncremental(affectedKpNames, doc.getSubject());
    } catch (Exception e) {
        log.error("增量融合失败（文档抽取后），documentId={}, kps={}，可手动全量融合修复",
                documentId, affectedKpNames, e);
    }
    ```
    
    注入 FusionService（构造器新增 1 个参数）。
    增量融合失败不阻塞主流程（仅 ERROR 日志，不抛异常）。
    注意：affectedKpNames 从 extracted.knowledgePoints() 提取，需确认 ExtractionService.ExtractionResult
    中的 knowledgePoints() 返回 List<KnowledgePointNode>（当前已有）。
  </action>
  <verify>mvn test -pl . -Dtest="GraphServiceTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>编译通过；GraphServiceImpl 在抽取完成后调用 fusionService.fuseIncremental()；mock 验证调用</done>
  <depends_on>T11</depends_on>
</task>

<task id="T14" parallel="true" status="done">
  <name>FusionController + VOs — L1 融合 API 端点</name>
  <read_files>
    api/graph/controller/GraphController.java
    api/graph/dto/ExtractionResultVO.java
    common/ApiResponse.java
    application/graph/fusion/service/FusionService.java
    application/graph/fusion/model/FusionExecuteResult.java
    application/graph/fusion/model/FusionStatusResult.java
    application/graph/fusion/model/FusionRollbackResult.java
  </read_files>
  <write_files>
    api/graph/controller/FusionController.java
    api/graph/dto/FusionExecuteVO.java
    api/graph/dto/FusionStatusVO.java
    api/graph/dto/FusionRollbackVO.java
  </write_files>
  <action>
    新建 FusionController（@RestController, @RequestMapping("/api/v1/graph/fusion")）：
    
    - POST /execute → fusionService.fuseFull() → ApiResponse<FusionExecuteVO>
    - GET /status → fusionService.getStatus() → ApiResponse<FusionStatusVO>
    - POST /rollback/{fusionLogId} → fusionService.rollback(id) → ApiResponse<FusionRollbackVO>
    
    VOs：
    - FusionExecuteVO: fusionLogId(Long), mergedKpGroupCount(int), mastersEdgeCount(int)
    - FusionStatusVO: 最近融合的完整信息（含 triggerType/status/executedAt/mergedKpGroupCount/
      mastersEdgeCount/rolledBack）+ fusionDetailJson/mastersSnapshotJson 字符串
    - FusionRollbackVO: fusionLogId(Long), restoredKpCount(int), restoredEdgeCount(int)
    
    BO → VO 转换在 Controller 层完成（延续既有分层模式）。
    异常由 GlobalExceptionHandler 统一处理（A0012/A0013/A0014 已注册）。
    注入 FusionService，构造器注入。
    参考 GraphController 的 @RestController + @RequestMapping 模式。
  </action>
  <verify>mvn compile -pl . 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>编译通过；三个融合端点可用、VOs 字段覆盖 FusionExecuteResult/FusionStatusResult/FusionRollbackResult</done>
  <depends_on>T11</depends_on>
</task>

<task id="T15" parallel="true" status="done">
  <name>单元测试 — 策略 + FusionService + Controller</name>
  <read_files>
    application/graph/fusion/strategy/FuzzyMatchStrategy.java
    application/graph/fusion/strategy/TimeDecayStrategy.java
    application/graph/fusion/strategy/ExactMatchStrategy.java
    application/graph/fusion/strategy/SimpleAverageStrategy.java
    application/graph/fusion/service/impl/FusionServiceImpl.java
    api/graph/controller/FusionController.java
    test/java/com/graphnexus/application/graph/service/GraphServiceTest.java
  </read_files>
  <write_files>
    test/java/com/graphnexus/application/graph/fusion/strategy/FuzzyMatchStrategyTest.java
    test/java/com/graphnexus/application/graph/fusion/strategy/TimeDecayStrategyTest.java
    test/java/com/graphnexus/application/graph/fusion/service/FusionServiceTest.java
    test/java/com/graphnexus/api/graph/controller/FusionControllerTest.java
  </write_files>
  <action>
    编写单元测试（JUnit 5 + Mockito，参考 GraphServiceTest 模式）：
    
    FuzzyMatchStrategyTest（≥ 4 用例）：
    - AC-2: "二次函数顶点坐标" vs "顶点坐标公式" → combinedScore 合理（含 charJaccard/bigramJaccard 计算验证）
    - AC-3: "二次函数图像" vs "一次函数图像" → combinedScore < 0.85 不合并
    - 完全相同 KP → combinedScore = 1.0
    - 完全不同 KP → combinedScore < 0.3
    - 跨 subject 不匹配 → 前置过滤直接 0.0
    
    TimeDecayStrategyTest（≥ 4 用例）：
    - AC-4: 两次考试时间衰减 → weight ≈ 0.554
    - 单次考试 → weight = scoreRate（衰减抵消）
    - AC-5: 缺考跳过 → 不计入
    - 空记录 → weight = 0.0
    
    FusionServiceTest（≥ 5 用例，mock GraphNodeRepository + FusionLogRepository + ExamRecordRepository）：
    - 全量融合：mock KP 列表 → 验证 match/merge/masters 调用链
    - 增量融合：仅 affected KPs 被处理
    - 并发拒绝：fusion_log RUNNING 状态 → A0013
    - 回滚成功：mock 快照 → 验证逆向恢复调用
    - 回滚幂等：已 rolledBack → 直接返回
    
    FusionControllerTest（≥ 3 用例，@WebMvcTest + mock FusionService）：
    - POST /execute → 200 + FusionExecuteVO
    - GET /status → 200 + FusionStatusVO
    - POST /rollback/{id} → 200 + FusionRollbackVO
  </action>
  <verify>mvn test -pl . -Dtest="FuzzyMatchStrategyTest,TimeDecayStrategyTest,FusionServiceTest,FusionControllerTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>所有单元测试通过（≥ 16 tests, 0 failures）；覆盖 AC-2/3/4/5/8/9/10/11/12</done>
  <depends_on>T11,T14</depends_on>
</task>

<task id="T16" parallel="true" status="done">
  <name>集成测试 — 端到端融合 + 回滚（直连 podman Neo4j + MySQL）</name>
  <read_files>
    api/graph/controller/FusionController.java
    application/graph/fusion/service/impl/FusionServiceImpl.java
    test/java/com/graphnexus/api/graph/controller/GraphControllerIntegrationTest.java
    application-dev.yml
  </read_files>
  <write_files>
    test/java/com/graphnexus/api/graph/controller/FusionControllerIntegrationTest.java
  </write_files>
  <action>
    编写集成测试（@SpringBootTest + @ActiveProfiles("dev")，直连 podman 真实组件，
    参考 GraphControllerIntegrationTest 模式）：
    
    ≥ 4 个测试场景：
    1. AC-1 全量融合端到端：
       - 预先创建文档侧 KP + CSV 侧同名 KP（通过直接 Cypher 或复用已有数据）
       - POST /execute → 200
       - Cypher 验证：仅 1 个规范 KP、MASTERS 边存在
    2. AC-6 增量融合：
       - 上传 CSV → 自动触发增量融合 → fusion_log 有 AUTO_INCREMENTAL 记录
    3. AC-8 回滚：
       - 融合 → 记录 fusionLogId → POST /rollback/{id} → 200
       - Cypher 验证：源 KP 恢复、规范 KP 删除
    4. AC-14 跨学科不合并：
       - 数学"函数定义" + 物理"函数定义" → 保持 2 个节点
    
    每个测试方法加 @Transactional 自动回滚（MySQL），Neo4j 侧在 @AfterEach 清理。
  </action>
  <verify>mvn test -pl . -Dtest="FusionControllerIntegrationTest" 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>集成测试全通过（≥ 4 tests, 0 failures）；覆盖 AC-1/6/8/14 端到端验证</done>
  <depends_on>T11,T14</depends_on>
</task>
```

---

## 状态字段说明

- `status="done"` — 未开始
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