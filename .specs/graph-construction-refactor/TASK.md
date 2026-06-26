# TASK: 图谱构建模块重命名 + 逻辑优化

- **Change ID**: `graph-construction-refactor`
- **关联**: `@.specs/graph-construction-refactor/REQUIREMENT.md`、`@.specs/graph-construction-refactor/DESIGN.md`

---

> **⚠️ 修订记录（2026-06-21）**：T12（原"三阶段流水线拆分"）已修订——独立"实体对齐"阶段（phase2_align）移除，流水线改为两阶段（phase1_build → phase2_fuse）。T17 中 FileStatus 的 ALIGNING/ALIGNED 扩展已回退到 v2。T12/T17 的任务描述保留历史原文，实际实现以 ADR-022 修订记录和代码为准。

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P], T04[P]    ← 类重命名 + 包移动 + 新节点/边类
Wave 2 (parallel): T05[P], T06[P]                       ← Repository 拆分（依赖 T01 包结构稳定）
Wave 3 (parallel): T07[P], T08[P], T09[P]              ← 调用方切换（依赖 T05 Repository 就位）
Wave 4 (parallel): T10[P], T11[P], T12[P]              ← 核心逻辑（依赖 T03/T07）
Wave 5 (parallel): T13[P], T14[P]                      ← 融合增强（依赖 T05/T12）
Wave 6 (parallel): T15[P], T16[P], T17[P]              ← 周边适配（提示词/索引/EventListener）
Wave 7 (parallel): T18[P], T19[P], T20[P]              ← 测试更新（依赖前 6 波完成）
Wave 8:            T21                                  ← 全量验证
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<!-- ==================== Wave 1: 类重命名 + 包移动 + 新节点/边类 ==================== -->

<task id="T01" parallel="true" status="pending">
  <name>GraphController → ConstructionController 重命名 + URL 变更</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/api/graph/dto/graph/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/graph/GraphSubgraphVO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java (删除)
  </write_files>
  <action>
    1. 新建 ConstructionController.java，与 GraphController 完全相同，仅做以下变更：
       - 类名: GraphController → ConstructionController
       - @RequestMapping: /api/v1/graph → /api/v1/graph/construction
       - @Tag name: "知识图谱" → "图谱构建"，description 更新为"文档知识图谱抽取、文档子图查询与实体对齐"
       - 注入字段: GraphService → ConstructionService（后续 T02 创建接口后编译通过）
       - @Operation summary 更新：明确标注三阶段流水线（构建→对齐→融合）
    2. 删除 GraphController.java
    3. ExtractionResultVO 和 GraphSubgraphVO 暂不移动（T03 统一移动）
    4. 注意：Swagger @ApiResponse 中的错误码引用 A0006/A0008/A0009 保持不变
  </action>
  <verify>
    ls src/main/java/com/graphnexus/api/graph/controller/ConstructionController.java &amp;&amp; ! ls src/main/java/com/graphnexus/api/graph/controller/GraphController.java
  </verify>
  <done>ConstructionController.java 存在，GraphController.java 不存在，URL 为 /api/v1/graph/construction</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>GraphService → ConstructionService 接口重命名 + 包移动</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/core/service/GraphService.java
    src/main/java/com/graphnexus/application/graph/core/model/ExtractionResultBO.java
    src/main/java/com/graphnexus/application/graph/core/model/GraphSubgraphBO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java
    src/main/java/com/graphnexus/application/graph/core/service/GraphService.java (删除)
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionResultBO.java
    src/main/java/com/graphnexus/application/graph/construction/model/GraphSubgraphBO.java
    src/main/java/com/graphnexus/application/graph/core/model/ExtractionResultBO.java (删除)
    src/main/java/com/graphnexus/application/graph/core/model/GraphSubgraphBO.java (删除)
  </write_files>
  <action>
    1. 新建 ConstructionService.java 于 construction/service/ 包：
       - 接口名: GraphService → ConstructionService
       - 方法签名不变: ExtractionResultBO extract(Long docId) + GraphSubgraphBO getSubgraph(Long docId)
       - JavaDoc 更新为"图谱构建服务接口 — 编排三阶段流水线（构建→实体对齐→图谱融合）"
       - import 指向 construction/model/ 包下的 BO 类
    2. 移动 ExtractionResultBO 和 GraphSubgraphBO 到 construction/model/ 包：
       - 仅改 package 声明，类内容不变
    3. 删除 core/service/GraphService.java、core/model/ExtractionResultBO.java、core/model/GraphSubgraphBO.java
    4. 注意：ConstructionService 不再 import ExtractionResultBO 和 GraphSubgraphBO 从 core，而是从 construction
  </action>
  <verify>
    ls src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java &amp;&amp; ls src/main/java/com/graphnexus/application/graph/construction/model/ExtractionResultBO.java &amp;&amp; ! ls src/main/java/com/graphnexus/application/graph/core/service/GraphService.java
  </verify>
  <done>ConstructionService 接口在 construction/service/ 包，BO 在 construction/model/ 包</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>剩余 BO/VO/DTO 包移动 + 全量 import 更新</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/core/model/GraphNodeData.java
    src/main/java/com/graphnexus/application/graph/core/model/GraphEdgeData.java
    src/main/java/com/graphnexus/application/graph/core/model/GraphDataConverter.java
    src/main/java/com/graphnexus/api/graph/dto/graph/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/graph/GraphSubgraphVO.java
    src/main/java/com/graphnexus/application/analysis/model/PrunedSubgraph.java
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/model/GraphNodeData.java
    src/main/java/com/graphnexus/application/graph/construction/model/GraphEdgeData.java
    src/main/java/com/graphnexus/application/graph/construction/model/GraphDataConverter.java
    src/main/java/com/graphnexus/api/graph/dto/construction/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/construction/GraphSubgraphVO.java
    src/main/java/com/graphnexus/application/analysis/model/PrunedSubgraph.java
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    (删除) src/main/java/com/graphnexus/application/graph/core/model/GraphNodeData.java
    (删除) src/main/java/com/graphnexus/application/graph/core/model/GraphEdgeData.java
    (删除) src/main/java/com/graphnexus/application/graph/core/model/GraphDataConverter.java
    (删除) src/main/java/com/graphnexus/api/graph/dto/graph/ExtractionResultVO.java
    (删除) src/main/java/com/graphnexus/api/graph/dto/graph/GraphSubgraphVO.java
  </write_files>
  <action>
    1. 移动剩余 3 个 BO 类到 construction/model/：GraphNodeData, GraphEdgeData, GraphDataConverter（仅改 package，类内容不变）
    2. 移动 2 个 VO 到 dto/construction/：ExtractionResultVO, GraphSubgraphVO（仅改 package，类内容不变；import 的 BO 路径更新为 construction/model）
    3. 更新跨模块 import（analysis + query 模块引用了 core/model 的类）：
       - PrunedSubgraph.java: import core/model/GraphEdgeData → construction/model/GraphEdgeData; import core/model/GraphNodeData → construction/model/GraphNodeData
       - StudentDiagnosisStrategy.java: 同上
       - QueryServiceImpl.java: import core/model/GraphNodeData → construction/model/GraphNodeData
    4. 删除 core/model/ 下 5 个原始文件
    5. 检查 core/ 包是否为空，如果只剩空目录则删除
  </action>
  <verify>
    grep -r "core.model" src/main/java/com/graphnexus/application/ --include="*.java" | wc -l | xargs test 0 -eq
  </verify>
  <done>所有 BO/VO 在 construction/ 包下，跨模块 import 全部更新，core/ 已清理</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>新增 SubjectNode + BelongsToSubjectEdge</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/SubjectNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/BelongsToSubjectEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
  </write_files>
  <action>
    1. 新建 SubjectNode.java：
       - extends GraphNode
       - Label: @Node("Subject")
       - 字段: String name（学科名称，唯一标识，如"数学""物理"）
       - 构造器: SubjectNode(String name)
       - nodeType 使用 NodeType 枚举中的新值（见下）
       - toProperties(): 仅含 name + 基类属性
       - 见 ADR-019 § SubjectNode 设计
    2. 新建 BelongsToSubjectEdge.java：
       - extends GraphEdge
       - @RelationshipProperties + edgeType = EdgeType 枚举新值 BELONGS_TO_SUBJECT
       - 构造器: BelongsToSubjectEdge(String sourceNodeId, String targetNodeId)
       - 无额外属性（纯结构边）
       - 方向: (KP|Exam|FileNode)-[:BELONGS_TO_SUBJECT]->(:Subject)
    3. 更新 NodeType 枚举：新增 SUBJECT("Subject")
    4. 更新 EdgeType 枚举：新增 BELONGS_TO_SUBJECT("BELONGS_TO_SUBJECT")
    5. 沿用既有模式：构造器注入、@Data + @NoArgsConstructor + @EqualsAndHashCode(callSuper=true)
  </action>
  <verify>
    ls src/main/java/com/graphnexus/infrastructure/neo4j/node/SubjectNode.java &amp;&amp; ls src/main/java/com/graphnexus/infrastructure/neo4j/edge/BelongsToSubjectEdge.java &amp;&amp; grep "SUBJECT" src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java &amp;&amp; grep "BELONGS_TO_SUBJECT" src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
  </verify>
  <done>SubjectNode 和 BelongsToSubjectEdge 可编译，NodeType/EdgeType 枚举更新</done>
  <depends_on></depends_on>
</task>

<!-- ==================== Wave 2: Repository 拆分 ==================== -->

<task id="T05" parallel="true" status="pending">
  <name>GraphNodeRepository 拆分为 ConstructionGraphRepository + FusionGraphRepository</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/FusionGraphRepository.java
  </write_files>
  <action>
    按照 ADR-021 的方法分配，从 GraphNodeRepository 中提取方法到两个新 Repository：
    1. ConstructionGraphRepository:
       - save(T), saveAll(List), saveEdge(GraphEdge), saveAllEdges(List)
       - findByDocumentId(String), findEdgesByDocumentId(String)
       - deleteByDocumentId(String)
       - deleteEdgesByExamNo(String, String), deleteExamNode(String)
       - 内部类 SimpleGraphNode, SimpleGraphEdge
       - toNodeProps 私有方法
       - COMMON_LABEL, node() 辅助方法
    2. FusionGraphRepository:
       - findAllKnowledgePoints()
       - findKnowledgePointsByNamesAndSubject(List, String)
       - redirectEdges(String, String), queryRelationshipTypes（private）
       - deleteKnowledgePoints(List)
       - batchUpsertMastersEdges(String, List), MastersEdgeData record
       - findAllStudentsBySubject(String)
       - findStudentsByKpNamesAndSubject(List, String), findStudentsByKpNames(List)
       - findMastersEdgesByStudent(String)
       - deleteIncomingEdges(String, String)
       - updateNodeProperties(String, Map), createNode(String, Map)
       - deleteMastersEdge(String, String), updateMastersWeight(String, String, double, String)
    3. 两个新 Repository 保持与原 GraphNodeRepository 相同的注解（@Slf4j @Repository @RequiredArgsConstructor）
    4. 原 GraphNodeRepository 暂不删除（T07 切换完调用方后再删）
    5. Neo4jClient/Neo4jTemplate 注入方式保持一致
  </action>
  <verify>
    ls src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java &amp;&amp; ls src/main/java/com/graphnexus/infrastructure/neo4j/repository/FusionGraphRepository.java &amp;&amp; mvn compile -pl . 2&gt;&amp;1 | grep -v "ERROR"
  </verify>
  <done>两个新 Repository 编译通过，方法完整</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>新增 QueryGraphRepository + 删除原 GraphNodeRepository</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/QueryGraphRepository.java
  </write_files>
  <action>
    1. 从 GraphNodeRepository 中提取只读查询方法到 QueryGraphRepository：
       - findStudentByName(String)
       - findStudentByNo(String)
       - findMastersByStudentAndSubject(String, String)
       - findTestedKpsByStudentAndSubject(String, String)
       - findPrerequisitesUpstream(List, int)
       - findMastersByStudentAndKpIds(String, List)
       - findDistinctSubjects()
       （注意：findDistinctSubjects 后续 T11 需适配 Subject 节点化，暂保持 kp.subject 查询）
    2. 确认 ConstructionGraphRepository + FusionGraphRepository + QueryGraphRepository 三个 Repository 覆盖了原 GraphNodeRepository 的所有 public 方法
    3. 暂不删除原 GraphNodeRepository（T07 切换完调用方后再删）
  </action>
  <verify>
    ls src/main/java/com/graphnexus/infrastructure/neo4j/repository/QueryGraphRepository.java &amp;&amp; mvn compile -pl . 2&gt;&amp;1 | tail -1 | grep "BUILD SUCCESS"
  </verify>
  <done>三个新 Repository 全部就位，覆盖原 GraphNodeRepository 所有方法</done>
  <depends_on></depends_on>
</task>

<!-- ==================== Wave 3: 调用方切换 ==================== -->

<task id="T07" parallel="true" status="pending">
  <name>GraphServiceImpl 重命名为 ConstructionServiceImpl + 调用方切换到新 Repository</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/core/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/application/graph/core/service/impl/GraphServiceImpl.java (删除)
  </write_files>
  <action>
    1. 新建 ConstructionServiceImpl.java 于 construction/service/impl/：
       - 类名: GraphServiceImpl → ConstructionServiceImpl
       - 实现: implements ConstructionService
       - 注入切换: GraphNodeRepository → ConstructionGraphRepository + QueryGraphRepository（getSubgraph 使用 QueryGraphRepository）
       - 消除完全限定名: new com.graphnexus.infrastructure.neo4j.edge.ExtractsEdge(...) → import + new ExtractsEdge(...)
       - @Transactional 明确化: extract() 保留 @Transactional（含 MySQL 写），getSubgraph() 保留 @Transactional(readOnly=true)
       - 方法注释明确标注 Neo4j 写入不在 Spring @Transactional 范围内
    2. 删除 GraphServiceImpl.java
    3. 此阶段仅做重命名 + 消除 FQN + Repository 切换，不做三阶段流水线拆分（那是 T13 的任务）
    4. ExtractionService 中如有对 GraphNodeRepository 的引用，也一并切换
    5. 见 ADR-021 § 调用方切换表
  </action>
  <verify>
    ls src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java &amp;&amp; ! ls src/main/java/com/graphnexus/application/graph/core/service/impl/GraphServiceImpl.java &amp;&amp; grep -c "com.graphnexus.infrastructure" src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java | xargs test 0 -eq
  </verify>
  <done>ConstructionServiceImpl 在 construction/service/impl/，注入新 Repository，零完全限定名</done>
  <depends_on>T02, T05, T06</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>Fusion 模块调用方切换到新 Repository</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionRollbackService.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/MastersRecalculationService.java
    src/main/java/com/graphnexus/application/graph/fusion/event/GradeUploadedEventListener.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionRollbackService.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/MastersRecalculationService.java
    src/main/java/com/graphnexus/application/graph/fusion/event/GradeUploadedEventListener.java
  </write_files>
  <action>
    1. 将以下 5 个文件中 GraphNodeRepository 的注入全部切换为 FusionGraphRepository：
       - FusionServiceImpl（全量/增量融合 + 回滚 + 状态查询编排）
       - FusionGroupBuilder（融合组构建 + merge 执行）
       - FusionRollbackService（回滚操作）
       - MastersRecalculationService（MASTERS 全量/增量重算）
       - GradeUploadedEventListener（融合触发监听）
    2. 仅更新 import + 字段声明 + 构造器参数，不改变任何业务逻辑
    3. 确保注入的 bean 名称正确（FusionGraphRepository 的 @Repository 默认 bean 名 = "fusionGraphRepository"）
  </action>
  <verify>
    grep -r "GraphNodeRepository" src/main/java/com/graphnexus/application/graph/fusion/ --include="*.java" | wc -l | xargs test 0 -eq
  </verify>
  <done>fusion/ 包下零 GraphNodeRepository 引用，全部切换到 FusionGraphRepository</done>
  <depends_on>T05</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>剩余调用方（Grade/QA/Metrics/Textbook）切换到新 Repository</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java
    src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java
    src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java (删除)
  </write_files>
  <action>
    1. GradeGraphEventListener → 注入 ConstructionGraphRepository
    2. MetricsServiceImpl → 注入 QueryGraphRepository
    3. QueryServiceImpl → 注入 QueryGraphRepository
    4. StudentDiagnosisStrategy → 注入 QueryGraphRepository
    5. TextbookServiceImpl → 注入 ConstructionGraphRepository
    6. 全部调用方切换完毕后，删除 GraphNodeRepository.java
    7. 注意：StudentDiagnosisStrategy 中有 new KnowledgePointNode(name, subject)（CSV 构造器），暂不改（T16 处理）
  </action>
  <verify>
    grep -r "GraphNodeRepository" src/main/java/ --include="*.java" | wc -l | xargs test 0 -eq
  </verify>
  <done>全项目零 GraphNodeRepository 引用，原文件已删除</done>
  <depends_on>T05, T06</depends_on>
</task>

<!-- ==================== Wave 4: 核心逻辑变更 ==================== -->

<task id="T10" parallel="true" status="pending">
  <name>KnowledgePointNode/ExamNode/FileNode 移除 subject 属性</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/ExamNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/FileNode.java
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionService.java
    src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java
    src/main/java/com/graphnexus/application/graph/core/service/impl/GraphServiceImpl.java (已删除, 参考 ConstructionServiceImpl)
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/ExamNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/FileNode.java
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionService.java
    src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java
  </write_files>
  <action>
    1. KnowledgePointNode 变更（见 ADR-019）：
       - 删除字段: private String subject
       - 删除构造器中的 subject 参数和相关赋值:
         - KnowledgePointNode(name, desc, subject, gradeLevel, docId) → KnowledgePointNode(name, desc, gradeLevel, docId)
         - KnowledgePointNode(name, subject) → KnowledgePointNode(name)（CSV 构造器，不再传 subject）
         - KnowledgePointNode(name, desc, subject, gradeLevel, docId, fusionSource) → KnowledgePointNode(name, desc, gradeLevel, docId, fusionSource)
       - toProperties() 中删除: props.put("subject", this.getSubject())
       - 删除 getSubject() 方法（通过 @Data 自动生成，删除字段即删除）
    2. ExamNode 变更：
       - 删除字段: private String subject
       - 构造器: ExamNode(examNo, name, examDate, subject) → ExamNode(examNo, name, examDate)
       - toProperties() 删除 subject
    3. FileNode 变更：
       - 删除字段: private String subject
       - 构造器: FileNode(name, subject, pageCount, docId) → FileNode(name, pageCount, docId)
       - toProperties() 删除 subject
    4. 更新所有调用方中受影响的对象创建（后续 T13/T16 完成 SubjectNode + 边的创建逻辑）
  </action>
  <verify>
    grep "subject" src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java | grep -v "//\|fusionSource" &amp;&amp; echo "STILL HAS SUBJECT" || echo "OK" ; grep "subject" src/main/java/com/graphnexus/infrastructure/neo4j/node/ExamNode.java | grep -v "//" &amp;&amp; echo "STILL HAS SUBJECT" || echo "OK"
  </verify>
  <done>三个节点类中 subject 字段/构造器参数/toProperties 已移除</done>
  <depends_on>T04</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>ExtractionService.convertToDomain — SubjectNode 创建 + BELONGS_TO_SUBJECT 边</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionService.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/SubjectNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/BelongsToSubjectEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionService.java
  </write_files>
  <action>
    1. 更新 convertToDomain() 方法：
       - 从 raw.getKnowledgePoints() 中提取 subject 名（LLM 仍输出 subject 字符串，见 T15 Prompt 更新）
       - 对每个 unique subject 名，创建 SubjectNode 并保存: constructionGraphRepository.save(new SubjectNode(subjectName))
       - 对每个 KnowledgePointNode，创建 BelongsToSubjectEdge(kp.getId(), subjectNode.getId()) 并加入 edges 列表
       - 对 FileNode，同样创建 BelongsToSubjectEdge(fileNode.getId(), subjectNode.getId())
    2. 注意：SubjectNode.save() 使用 MERGE (s:Subject {id: $id}) → 同名字符串会创建不同 UUID 的 SubjectNode。
       需要额外处理：SubjectNode 的 save 应在 create 前先查询是否已有同名 SubjectNode。
       方案：在 ConstructionGraphRepository 中新增方法 findOrCreateSubject(String name)
       → MATCH (s:Subject {name: $name}) RETURN s，若无则 CREATE
    3. ExtractionResult record 不变（entities / knowledgePoints / categories / edges + subjectEdges）
    4. 边数统计：totalEdges 须包含 BELONGS_TO_SUBJECT 边
  </action>
  <verify>
    grep "SubjectNode\|BelongsToSubjectEdge\|findOrCreateSubject" src/main/java/com/graphnexus/application/graph/construction/service/ExtractionService.java
  </verify>
  <done>convertToDomain 中创建 SubjectNode + BELONGS_TO_SUBJECT 边，不再设置 kp.subject 属性</done>
  <depends_on>T04, T05, T10</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>三阶段流水线 — ConstructionServiceImpl 方法拆分</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/FileStatus.java
    src/main/java/com/graphnexus/application/graph/fusion/strategy/KpMatchingStrategy.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
  </write_files>
  <action>
    按 ADR-022 § 三阶段流水线设计，将 extract() 拆分为：
    1. extract(docId) — 编排方法：
       - 阶段一: phase1_build(docId) → 返回 BuildResult(entities, kps, categories)
       - 阶段二: phase2_align(docId, buildResult) → 对齐 Entity 到已有 KP
       - 阶段三: phase3_fuse(docId, buildResult) → 增量融合
       - 编排错误处理：phase1 失败抛异常；phase2 失败记 warn + 继续；phase3 失败记 error + 返回 warning
    2. phase1_build(docId):
       - 文档校验（同原有逻辑）
       - FileStatus → EXTRACTING
       - 调用 ExtractionService.extract()
       - constructionGraphRepository 批量写入（事务内 deleteByDocumentId + saveAll）
       - 统计（含 BELONGS_TO_SUBJECT 边计数）
       - FileStatus → EXTRACTED
    3. phase2_align(docId, buildResult):
       - FileStatus → ALIGNING
       - 通过 queryGraphRepository 查询同 SubjectNode 下已有 KPs
       - 对每个新 Entity，通过 KpMatchingStrategy (FuzzyMatch, threshold=0.85) 匹配已有 KP
       - 命中 → 创建 ALIGNED_TO 边 (Entity → 已有KP)
       - FileStatus → ALIGNED
    4. phase3_fuse(docId, buildResult):
       - FileStatus → FUSING
       - 在 Neo4j 事务内（见 ADR-020 + T14）调用 fusionService.fuseIncremental()
       - 成功 → FileStatus → COMPLETED + 发布 GraphChangedEvent
       - 失败 → FileStatus 回退到 ALIGNED + 返回 fusionWarning
    5. getSubgraph() 保持不变（QueryGraphRepository 取代原 GraphNodeRepository）
  </action>
  <verify>
    grep "phase1_build\|phase2_align\|phase3_fuse" src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java | wc -l | xargs test 3 -eq
  </verify>
  <done>extract() 拆分为 3 个阶段方法，含完整的状态转换和错误处理</done>
  <depends_on>T07, T09, T11</depends_on>
</task>

<!-- ==================== Wave 5: 融合增强 ==================== -->

<task id="T13" parallel="true" status="pending">
  <name>融合原子性 — FusionServiceImpl Neo4j 事务包装</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/MastersRecalculationService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java
  </write_files>
  <action>
    按 ADR-020 § Neo4j 事务包装：
    1. FusionServiceImpl.fuseFull():
       - 预计算阶段（事务外）：build all groups（纯内存计算，FusionGroupBuilder.build）
       - 写入 fusionLog（MySQL 事务）记录 RUNNING 状态
       - Neo4j 事务包装：使用 Neo4jTemplate.transactional() 或 TransactionTemplate 包裹全部 merge + MASTERS 重算
       - 成功 → updateLogCompleted + publish GraphChangedEvent
       - 失败 → Neo4j 自动回滚 + updateLogFailed
    2. FusionServiceImpl.fuseIncremental():
       - 同样包装在 Neo4j 事务中
       - 预计算 groups（事务外）→ 事务内 merge + recalculate
    3. FusionGroupBuilder.merge():
       - 每个 group 的 redirectEdges + deleteKPs + updateProps 现在在同一个 Neo4j 事务中执行
       - 如果有 group 失败，整个事务回滚，前面已执行的 redirectEdges 也回滚
    4. 异常时 fusionLog 更新为 FAILED（不在 Neo4j 事务内，独立 MySQL 事务）
    5. 事务超时配置：fusion.neo4j.transaction-timeout-seconds=30（application.yml）
  </action>
  <verify>
    grep "transactional\|TransactionTemplate\|Neo4jTemplate" src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java
  </verify>
  <done>全量/增量融合在 Neo4j 事务中执行，失败自动回滚</done>
  <depends_on>T05, T08</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>跨源 KP 精确匹配 — FusionGroupBuilder + Subject 节点引用分组</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/FusionGraphRepository.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/SubjectNode.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/FusionGraphRepository.java
  </write_files>
  <action>
    按 DESIGN § D7 + AC-8：
    1. FusionGroupBuilder.build() 更新：
       - 在 FuzzyMatch（threshold=0.85）之前，新增「精确匹配前置 pass」:
         对每对 KP，如果 name 完全相同（normalize 后 equalsIgnoreCase）且
         指向同一个 SubjectNode（通过 BELONGS_TO_SUBJECT 边），直接归入同一 FusionGroup
       - 精确匹配的 KP 跳过 FuzzyMatch，减少 O(n²) 计算
    2. 融合分组改为按 Subject 节点引用：
       - findAllKnowledgePoints() 查询返回时附带 subjectNodeId（通过 BELONGS_TO_SUBJECT 边）
       - build() 中 groupBy subjectNodeId 而非 subject 字符串
       - 同一 SubjectNode 的所有 KP 在同一个 subject 迭代中处理
    3. FusionGraphRepository.findAllKnowledgePoints() 更新查询：
       - 从 MATCH (kp:KnowledgePoint) RETURN kp.id, kp.name, kp.subject, ...
       - 改为 MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject) RETURN kp.id, kp.name, s.id AS subjectNodeId, s.name AS subject, ...
    4. 同样更新 findKnowledgePointsByNamesAndSubject()：按 SubjectNode 引用查询而非 subject 属性
  </action>
  <verify>
    grep "BELONGS_TO_SUBJECT\|subjectNodeId\|精确匹配" src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionGroupBuilder.java
  </verify>
  <done>融合分组基于 Subject 节点引用，跨源同名 KP 精确匹配优先合并</done>
  <depends_on>T05, T08, T10</depends_on>
</task>

<!-- ==================== Wave 6: 周边适配 ==================== -->

<task id="T15" parallel="true" status="pending">
  <name>LLM 提示词更新 — ExtractionPromptBuilder subject 规范化</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionPromptBuilder.java
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionRawResult.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionPromptBuilder.java
  </write_files>
  <action>
    按 AC-13 + DESIGN § D10：
    1. System Prompt 中「## 知识点（knowledgePoints）」段末尾新增：
       ```
       ## subject 命名规范（重要）
       - subject 字段使用**标准学科名称**，如"数学"、"物理"、"英语"
       - 禁止使用含年级/学段的限定名，如"高中数学"、"初中数学"→ 统一为"数学"
       - **必须与文档元数据中的学科名保持一致**（见 User Message 中的"学科"字段）
       - 如果文档内容涵盖多个学科，知识点的 subject 仍与文档元数据学科一致
       ```
    2. Few-shot 示例中 knowledgePoint 的 subject 值保持"数学"不变（已经是规范名）
    3. buildUserMessage() 不需要改——已经传 subject 参数
    4. 注意：LLM 仍输出 subject 字符串（JSON 格式不变），T11 中的 convertToDomain 负责将字符串转为 SubjectNode
  </action>
  <verify>
    grep "subject 命名规范\|标准学科名称\|文档元数据" src/main/java/com/graphnexus/application/graph/construction/service/ExtractionPromptBuilder.java
  </verify>
  <done>Prompt 中包含 subject 命名规范化指令</done>
  <depends_on></depends_on>
</task>

<task id="T16" parallel="true" status="pending">
  <name>Neo4j 索引更新 + GradeGraphEventListener KP 创建策略变更</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/config/Neo4jIndexConfig.java
    src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/config/Neo4jIndexConfig.java
    src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java
  </write_files>
  <action>
    1. Neo4jIndexConfig 更新：
       - 删除: CREATE INDEX kp_subject IF NOT EXISTS FOR (kp:KnowledgePoint) ON (kp.subject)
       - 新增: CREATE INDEX subject_name IF NOT EXISTS FOR (s:Subject) ON (s.name)
       - 更新 QA 索引注释: "Student.studentNo, Subject.name"
    2. GradeGraphEventListener.onGradeUploaded() 中考试 KP 创建策略变更（见 AC-8 + DESIGN § D8）：
       - 修改 KnowledgePointNode 创建方式:
         旧: new KnowledgePointNode(kpName, subject) + save()（UUID MERGE，可能重复）
         新: 先通过 ConstructionGraphRepository 按 name + SubjectNode 查找已有 KP：
           MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name: $subject})
           WHERE kp.name = $kpName RETURN kp
           若无 → 创建新 KP + 创建 SubjectNode + BELONGS_TO_SUBJECT 边 + save
           若有 → 复用已有 KP，不创建新节点
       - ExamNode 创建: new ExamNode(examNo, name, examDate)（不再传 subject）
       - 考试 SubjectNode: 查找或创建 SubjectNode，创建 (exam)-[:BELONGS_TO_SUBJECT]->(subject) 边
    3. 注意：此 task 依赖 T04 (SubjectNode 存在) 和 T10 (构造器变更)
  </action>
  <verify>
    grep "subject_name\|Subject.*name.*INDEX" src/main/java/com/graphnexus/infrastructure/neo4j/config/Neo4jIndexConfig.java &amp;&amp; grep "BELONGS_TO_SUBJECT\|SubjectNode\|MERGE" src/main/java/com/graphnexus/application/graph/construction/listener/GradeGraphEventListener.java
  </verify>
  <done>Neo4j 索引更新为 Subject.name，考试 KP 创建改为 name+SubjectNode 查找再决定</done>
  <depends_on>T04, T10</depends_on>
</task>

<task id="T17" parallel="true" status="pending">
  <name>FileStatus 枚举扩展 + Subject 存量迁移脚本</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/FileStatus.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/FusionGraphRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/FileStatus.java
    .specs/graph-construction-refactor/migrations/subject-node-migration.cypher
    src/main/resources/db/init.sql
  </write_files>
  <action>
    1. FileStatus 枚举扩展（见 DESIGN § 3.1 状态机 v3）：
       - 新增枚举值: ALIGNING, ALIGNED
       - 新增在 EXTRACTED 之后、FUSING 之前
       - 更新 getAllowedTargets()：
         EXTRACTED: {ALIGNING, DELETING}（原 FUSING → ALIGNING）
         ALIGNING: {ALIGNED, FAILED, EXTRACTED} [NEW]
         ALIGNED: {FUSING, DELETING} [NEW]
         FUSING: {COMPLETED, FAILED, ALIGNED}（原 EXTRACTED → ALIGNED）
         COMPLETED: {EXTRACTING, ALIGNING, FUSING, DELETING}（新增 ALIGNING）
       - 更新 JavaDoc 状态流转图
    2. 创建 Subject 存量迁移脚本（见 ADR-019 § 存量迁移）：
       ```cypher
       // Phase 1: 创建 SubjectNode
       MATCH (n) WHERE n.subject IS NOT NULL
       WITH DISTINCT n.subject AS subj
       CREATE (:Subject {id: randomUUID(), nodeType: 'Subject', name: subj, createdAt: datetime()})
       
       // Phase 2: 创建 BELONGS_TO_SUBJECT 边
       MATCH (n) WHERE n.subject IS NOT NULL
       MATCH (s:Subject {name: n.subject})
       CREATE (n)-[:BELONGS_TO_SUBJECT]->(s)
       
       // Phase 3: 移除 subject 属性
       MATCH (n) WHERE n.subject IS NOT NULL
       REMOVE n.subject
       ```
    3. 脚本幂等：每步执行前检查目标是否已存在
    4. 同步更新 init.sql：如有 Neo4j 初始化语句则更新
  </action>
  <verify>
    grep "ALIGNING\|ALIGNED" src/main/java/com/graphnexus/infrastructure/mysql/file/entity/FileStatus.java &amp;&amp; ls .specs/graph-construction-refactor/migrations/subject-node-migration.cypher
  </verify>
  <done>FileStatus 枚举含 ALIGNING/ALIGNED，Subject 迁移脚本就位</done>
  <depends_on>T04, T10</depends_on>
</task>

<!-- ==================== Wave 7: 测试更新 ==================== -->

<task id="T18" parallel="true" status="pending">
  <name>Controller 集成测试更新 — GraphControllerIntegrationTest → ConstructionControllerIntegrationTest</name>
  <read_files>
    src/test/java/com/graphnexus/api/graph/controller/GraphControllerIntegrationTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/api/graph/controller/ConstructionControllerIntegrationTest.java
    src/test/java/com/graphnexus/api/graph/controller/GraphControllerIntegrationTest.java (删除)
  </write_files>
  <action>
    1. 重命名测试文件 + 类名: GraphControllerIntegrationTest → ConstructionControllerIntegrationTest
    2. 更新所有 URL 引用: /api/v1/graph/extract → /api/v1/graph/construction/extract, /api/v1/graph/document → /api/v1/graph/construction/document
    3. 更新 Mock 注入: GraphService → ConstructionService
    4. 新增测试断言:
       - 响应体中 edgeCount = 所有类型边之和（含 BELONGS_TO_SUBJECT）
       - document.status 经历完整状态流转
    5. 如有旧 URL 的负向测试（assert 404），保留
  </action>
  <verify>
    mvn test -pl . -Dtest=ConstructionControllerIntegrationTest -DfailIfNoTests=false 2>&1 | tail -5
  </verify>
  <done>集成测试 URL 已更新，edgeCount 断言已修正，旧文件已删除</done>
  <depends_on>T01, T12</depends_on>
</task>

<task id="T19" parallel="true" status="pending">
  <name>Service 单元测试更新 — GraphServiceTest → ConstructionServiceTest</name>
  <read_files>
    src/test/java/com/graphnexus/application/graph/core/service/GraphServiceTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/graph/construction/service/ConstructionServiceTest.java
    src/test/java/com/graphnexus/application/graph/core/service/GraphServiceTest.java (删除)
  </write_files>
  <action>
    1. 重命名: GraphServiceTest → ConstructionServiceTest
    2. 更新 Mock:
       - GraphService → ConstructionService
       - GraphNodeRepository → ConstructionGraphRepository + QueryGraphRepository
       - GraphServiceImpl → ConstructionServiceImpl
    3. 新增三阶段流水线相关测试:
       - phase1 成功 → EXTRACTED 状态
       - phase2 匹配到已有 KP → 创建额外 ALIGNED_TO 边
       - phase3 融合失败 → 状态回退到 ALIGNED + 响应含 warning
       - 边数统计含 BELONGS_TO_SUBJECT
    4. 更新 import: core/model → construction/model
  </action>
  <verify>
    mvn test -pl . -Dtest=ConstructionServiceTest -DfailIfNoTests=false 2>&1 | tail -5
  </verify>
  <done>Service 单元测试 Mock 已切换到新 Repository，覆盖三阶段和边数统计</done>
  <depends_on>T02, T03, T12</depends_on>
</task>

<task id="T20" parallel="true" status="pending">
  <name>剩余测试文件更新 + 测试编译</name>
  <read_files>
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookServiceTest.java
    src/test/java/com/graphnexus/infrastructure/neo4j/node/GraphNodeAbstractionTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/ExtractionJsonParserTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/ExtractionValidatorTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookServiceTest.java
    src/test/java/com/graphnexus/infrastructure/neo4j/node/GraphNodeAbstractionTest.java
    (其他文件如有 import 从 core/model → construction/model 的也需更新)
  </write_files>
  <action>
    1. TextbookServiceTest: 更新 Mock GraphNodeRepository → ConstructionGraphRepository
    2. GraphNodeAbstractionTest: 
       - 新增 SubjectNode 的 label/属性断言
       - 新增 BelongsToSubjectEdge 的 type 断言
       - KnowledgePointNode 构造断言中不再验证 subject 属性
       - ExamNode/FileNode 构造断言同理
    3. ExtractionJsonParserTest / ExtractionValidatorTest: 如果有 import core/model → construction/model 更新
    4. 全量 grep 测试目录中 import "core.model" 或 "core.service" 的残留引用并修复
  </action>
  <verify>
    grep -r "core.model\|core.service" src/test/ --include="*.java" | wc -l | xargs test 0 -eq
  </verify>
  <done>所有测试 import 已更新，无 core/ 包残留引用</done>
  <depends_on>T03, T10, T04</depends_on>
</task>

<!-- ==================== Wave 8: 全量验证 ==================== -->

<task id="T21" parallel="false" status="pending">
  <name>全量 mvn test + AC 核验</name>
  <read_files>
    (无 · 仅执行)
  </read_files>
  <write_files>
    (无 · 验证任务)
  </write_files>
  <action>
    1. 执行 mvn test 全量测试
    2. 确保失败数 = 0，跳过数 = 0
    3. AC 逐条核验:
       - AC-1: grep "GraphController\|GraphService\|GraphServiceImpl" → 空
       - AC-2: curl POST /api/v1/graph/extract/1 → 404
       - AC-3: curl POST /api/v1/graph/construction/extract/1 → 200, edgeCount 完整
       - AC-4: grep "GraphNodeRepository" src/main → 空
       - AC-5: 日志中三阶段有独立记录
       - AC-6: 模拟融合失败 → document.status != COMPLETED
       - AC-7: edgeCount 验证
       - AC-8: 跨源 KP 融合 → 同名 only 1 KP node
       - AC-9: 融合原子性 → 模拟失败后 KP 数不变
       - AC-10: subject 属性迁移后无残留
       - AC-11: 新建节点无 subject 属性
       - AC-12: 同 Subject 节点 KP 在同一融合组
       - AC-13: grep prompt 含 subject 规范化指令
       - AC-14: grep FQN "com.graphnexus.infrastructure" → 空
       - AC-15: mvn test BUILD SUCCESS
    4. 如有未通过的 AC，开 T-FIX 修复
  </action>
  <verify>
    mvn test 2>&1 | grep -E "Tests run:.*Failures: 0.*Errors: 0"
  </verify>
  <done>mvn test 全绿 + 15 条 AC 全部核验通过</done>
  <depends_on>T01, T02, T03, T04, T05, T06, T07, T08, T09, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20</depends_on>
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