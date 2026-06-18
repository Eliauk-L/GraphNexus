# TASK: CSV 成绩文件上传与解析入库

- **Change ID**: `csv-grade-import`
- **关联**: `@.specs/csv-grade-import/REQUIREMENT.md`、`@.specs/csv-grade-import/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T00[P], T01[P], T02[P], T03[P], T0A[P], T0B[P], T05[P], T06[P]
Wave 2 (parallel): T07[P], T08[P]   (depends on T02)
Wave 3:            T09               (depends on T07, T08, T0A)
Wave 4:            T10               (depends on T01, T0B)
Wave 5:            T11               (depends on T03, T05, T06, T09, T10)
Wave 6:            T12               (depends on T03, T11)
Wave 7:            T13               (depends on T05, T11, T0B)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T00" parallel="true" status="done">
  <name>pom.xml 新增 Apache Commons CSV 依赖</name>
  <read_files>
    pom.xml
  </read_files>
  <write_files>
    pom.xml
  </write_files>
  <action>
    pom.xml 新增 commons-csv 依赖：groupId=org.apache.commons，artifactId=commons-csv，version=1.11.0。
    仅新增此一个依赖，不修改任何其他配置。理由见 D2。
    注意：pom.xml 属于禁动清单，本次作为独立 T00 单点提交，后续 task 不得触碰。
  </action>
  <verify>mvn dependency:resolve | grep "commons-csv:jar:1.11.0"</verify>
  <done>BUILD SUCCESS；依赖解析后可见 commons-csv 1.11.0</done>
  <depends_on></depends_on>
</task>

<task id="T01" parallel="true" status="done">
  <name>ErrorCode 新增 CSV 解析与删除错误码 A0011~A0015</name>
  <read_files>
    common/exception/ErrorCode.java
  </read_files>
  <write_files>
    common/exception/ErrorCode.java
  </write_files>
  <action>
    在 ErrorCode 枚举中新增 5 个错误码：
    - A0011 (BAD_REQUEST): CSV 格式错误（表头行数/成绩格式/列数不一致）
    - A0012 (BAD_REQUEST): CSV 缺少必要列（学号/考试编号）
    - A0013 (BAD_REQUEST): CSV 编码异常（非 UTF-8 且非 GBK）
    - A0014 (CONFLICT): 考试编号不存在（删除时查询不到）
    - A0015 (NOT_FOUND): 待删除的考试编号不存在（保留占位供未来其他删除场景使用）
    沿用既有 ErrorCode 枚举模式：(errorCode, httpStatus, defaultUserTip) 三参数构造。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -3</verify>
  <done>编译通过；ErrorCode 枚举增加 5 个新常量</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>NodeType + EdgeType 枚举新增 STUDENT/EXAM 和 ATTENDED/TESTED</name>
  <read_files>
    infrastructure/neo4j/node/NodeType.java
    infrastructure/neo4j/edge/EdgeType.java
  </read_files>
  <write_files>
    infrastructure/neo4j/node/NodeType.java
    infrastructure/neo4j/edge/EdgeType.java
  </write_files>
  <action>
    NodeType：新增 STUDENT("Student", StudentNode.class)、EXAM("Exam", ExamNode.class)。
    EdgeType：新增 ATTENDED("ATTENDED")、TESTED("TESTED")。
    延续既有枚举模式：NodeType 每个常量含 (label, nodeClass)，EdgeType 含 (relationshipType)。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -3</verify>
  <done>编译通过；两个枚举各增加 2 个常量</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>提取 Md5Utils 工具类，替换 DocumentServiceImpl.computeMd5</name>
  <read_files>
    application/document/service/impl/DocumentServiceImpl.java
    common/util/
  </read_files>
  <write_files>
    common/util/Md5Utils.java
    application/document/service/impl/DocumentServiceImpl.java
  </write_files>
  <action>
    1. 新增 common/util/Md5Utils.java：public static String computeMd5(byte[] data)，逻辑与 DocumentServiceImpl.computeMd5() 完全一致（MessageDigest MD5 → 32 位小写 hex）
    2. 修改 DocumentServiceImpl.java：删除 private computeMd5 方法，替换调用为 Md5Utils.computeMd5()
    见 DESIGN §0.5.2：MD5 判重从 DocumentServiceImpl 提取为公共方法，PDF 和 CSV 复用
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>Md5Utils 类存在且编译通过；DocumentServiceImpl 不再包含私有 computeMd5 方法</done>
  <depends_on></depends_on>
</task>

<task id="T0A" parallel="true" status="done">
  <name>GraphNode.toProperties() 多态重构 + KnowledgePointNode 无 documentId 构造器 + GraphNodeRepository 简化</name>
  <read_files>
    infrastructure/neo4j/node/GraphNode.java
    infrastructure/neo4j/node/DocumentNode.java
    infrastructure/neo4j/node/EntityNode.java
    infrastructure/neo4j/node/KnowledgePointNode.java
    infrastructure/neo4j/node/KnowledgeCategoryNode.java
    infrastructure/neo4j/repository/GraphNodeRepository.java
  </read_files>
  <write_files>
    infrastructure/neo4j/node/GraphNode.java
    infrastructure/neo4j/node/DocumentNode.java
    infrastructure/neo4j/node/EntityNode.java
    infrastructure/neo4j/node/KnowledgePointNode.java
    infrastructure/neo4j/node/KnowledgeCategoryNode.java
    infrastructure/neo4j/repository/GraphNodeRepository.java
  </write_files>
  <action>
    见 DESIGN D11（多态 toProperties）+ D6（CSV KP 无 documentId）。

    ====== GraphNode.java ======
    新增 public Map&lt;String, Object&gt; toProperties() 方法（非 abstract，基类默认实现）：
    - put id, nodeType, documentId, createdAt（公共字段，逻辑从原 toNodeProps 提取）

    ====== 4 个既有节点类 ======
    各新增 @Override public Map&lt;String, Object&gt; toProperties()：
    - 先调用 super.toProperties() 获取公共字段
    - 再 put 自身独有字段：
      DocumentNode: name, subject, pageCount
      EntityNode: entityType, name, originalText, pageNumber, metadata
      KnowledgePointNode: name, description, subject, gradeLevel
      KnowledgeCategoryNode: name, level, parentName
    - KnowledgePointNode 同时新增构造器：public KnowledgePointNode(String name, String subject)
      调用 super(NodeType.KNOWLEDGE_POINT.getLabel())，设置 name 和 subject，不设 documentId
      既有构造器（含 documentId）保持不变

    ====== GraphNodeRepository.java ======
    toNodeProps() 方法：删除整个 instanceof 链，改为 `return node.toProperties();`
    其他方法（save/saveEdge/deleteByDocumentId 等）不变
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>编译通过；GraphNode.toProperties() 存在，4 个节点类各有 @Override，GraphNodeRepository.toNodeProps() 为单行委托，KnowledgePointNode 有两个构造器</done>
  <depends_on></depends_on>
</task>

<task id="T0B" parallel="true" status="done">
  <name>新增 FileParser 统一接口 + FileParseRequest/FileParseResult + FileParserRegistry</name>
  <read_files>
    application/document/parser/DocumentParser.java
    common/ApiResponse.java
  </read_files>
  <write_files>
    application/document/parser/FileParser.java
    application/document/parser/FileParseRequest.java
    application/document/parser/FileParseResult.java
    application/document/parser/FileParserRegistry.java
  </write_files>
  <action>
    见 DESIGN D12（策略+工厂模式）。

    ====== FileParser.java ======
    public interface FileParser {
        FileParseType supportedType();              // 返回枚举值（CSV_GRADE / PDF_DOCUMENT / ...）
        Set&lt;String&gt; supportedExtensions();        // 返回处理的扩展名集合，如 Set.of(".csv")
        &lt;T&gt; FileParseResult&lt;T&gt; parse(FileParseRequest request);  // 统一解析入口
    }

    ====== FileParseRequest.java ======
    public record FileParseRequest(
        InputStream inputStream,
        String originalFilename,
        String subject,
        byte[] rawBytes          // 预读的字节，用于 MD5 判重
    ) {}

    ====== FileParseResult.java ======
    public record FileParseResult&lt;T&gt;(
        T payload,              // 各解析器的领域对象（CsvParsePayload / PdfParsePayload）
        FileParseType parseType
    ) {}

    ====== FileParseType.java（枚举）=====
    CSV_GRADE, PDF_DOCUMENT

    ====== FileParserRegistry.java ======
    @Component
    public class FileParserRegistry {
        private final Map&lt;String, FileParser&gt; parserByExtension = new HashMap<>();

        public FileParserRegistry(List&lt;FileParser&gt; allParsers) {
            // Spring 自动注入所有 FileParser 实现
            for (FileParser p : allParsers) {
                for (String ext : p.supportedExtensions()) {
                    parserByExtension.put(ext.toLowerCase(), p);
                }
            }
        }

        public Optional&lt;FileParser&gt; getParser(String filename) {
            String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
            return Optional.ofNullable(parserByExtension.get(ext));
        }
    }

    注意：DocumentParser（PDF 专用）本次不改造为实现 FileParser（禁动清单），待独立 refactor change。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>编译通过；FileParser 接口 + 3 个 record/enum + FileParserRegistry 均存在</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>新增 VO/BO 数据类：GradeUploadResultVO/BO + DeleteResultVO/BO</name>
  <read_files>
    api/document/dto/DocumentVO.java
    application/document/model/DocumentBO.java
    common/ApiResponse.java
  </read_files>
  <write_files>
    api/document/dto/GradeUploadResultVO.java
    api/document/dto/DeleteResultVO.java
    application/document/model/GradeUploadResultBO.java
    application/document/model/DeleteResultBO.java
  </write_files>
  <action>
    新增 4 个数据类：
    - GradeUploadResultBO（L2）：examNo, examName, examDate, subject, studentCount, questionCount, knowledgePoints(List&lt;String&gt;), minioPath, csvMd5
    - GradeUploadResultVO（L1）：同上字段 + static from(BO) 工厂方法，对应 AC-1 响应体
    - DeleteResultBO（L2）：examNo, deletedRecordCount, minioPath, deletedEdgeCount
    - DeleteResultVO（L1）：同上字段 + static from(BO) 工厂方法，对应 AC-7 响应体
    延续既有模式：BO 在 application 层，VO 在 api 层，VO 提供 static from(BO) 转换。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -3</verify>
  <done>编译通过；4 个数据类可被其他模块引用</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>新增 ExamRecordDO + ExamRecordRepository（MySQL 成绩记录持久层）</name>
  <read_files>
    infrastructure/mysql/document/DocumentDO.java
    infrastructure/mysql/document/DocumentRepository.java
    infrastructure/mysql/config/JpaAuditConfig.java
  </read_files>
  <write_files>
    infrastructure/mysql/document/ExamRecordDO.java
    infrastructure/mysql/document/ExamRecordRepository.java
  </write_files>
  <action>
    1. ExamRecordDO：
       - @Entity @Table(name = "exam_record")，继承 DocumentDO 的 JPA 审计模式（@EntityListeners + @CreatedDate/@LastModifiedDate）
       - 字段：id(Long AUTO_INCREMENT), studentNo(String), name(String), className(String), examNo(String), examName(String), examDate(LocalDate), subject(String), totalScore(Integer), classRank(Integer), scoreDetails(String @Column(columnDefinition = "JSON")), csvFilePath(String), csvMd5(String), isDeleted(Integer 0/1 default 0), createTime, updateTime
       - 沿用 @Builder + @NoArgsConstructor + @AllArgsConstructor + @Builder.Default
       - 提供 markDeleted() 方法：设置 isDeleted = 1（中间状态，见 D10 全局删除约束 C3）
    2. ExamRecordRepository：
       - extends JpaRepository&lt;ExamRecordDO, Long&gt;
       - 自定义查询（延用显式 JPQL 模式，参考 DocumentRepository 的 Hibernate 6.5 bug 规避）：
         - findByExamNoAndIsDeletedFalse(String examNo)：返回 List&lt;ExamRecordDO&gt;，查询条件 e.isDeleted = 0
         - findByCsvMd5AndIsDeletedFalse(String csvMd5)：返回 Optional&lt;ExamRecordDO&gt;，用于上传判重
         - findByExamNo(String examNo)：返回 List&lt;ExamRecordDO&gt;（含 isDeleted=1，删除流程内部使用）
       - 物理删除：继承自 JpaRepository 的 deleteAll(Iterable) 方法，由 Service 层调用
    见 DESIGN D3（JSON 列）+ D8（MD5 判重）+ D10（中间状态）。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -3</verify>
  <done>编译通过；ExamRecordDO 表映射正确，Repository 自定义查询语法通过编译</done>
  <depends_on></depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>新增 StudentNode + ExamNode（Neo4j 节点类，含 toProperties 覆盖）</name>
  <read_files>
    infrastructure/neo4j/node/NodeType.java
    infrastructure/neo4j/node/GraphNode.java
    infrastructure/neo4j/node/DocumentNode.java
  </read_files>
  <write_files>
    infrastructure/neo4j/node/StudentNode.java
    infrastructure/neo4j/node/ExamNode.java
  </write_files>
  <action>
    1. StudentNode：@Data @Node("Student") @NoArgsConstructor @EqualsAndHashCode(callSuper = true) extends GraphNode
       - 字段：studentNo(String · 学号唯一标识), name(String), className(String), grade(String)
       - 构造器：public StudentNode(String studentNo, String name, String className, String grade)
         调用 super(NodeType.STUDENT.getLabel())，设置以上字段
       - @Override toProperties()：先调 super.toProperties()，再 put studentNo, name, className, grade
    2. ExamNode：@Data @Node("Exam") @NoArgsConstructor @EqualsAndHashCode(callSuper = true) extends GraphNode
       - 字段：examNo(String · 考试编号唯一标识), name(String), examDate(LocalDate), subject(String)
       - 构造器：public ExamNode(String examNo, String name, LocalDate examDate, String subject)
         调用 super(NodeType.EXAM.getLabel())，设置以上字段
       - @Override toProperties()：先调 super.toProperties()，再 put examNo, name, examDate, subject
    延续既有节点类模式（参考 DocumentNode）+ D11 多态 toProperties 模式。
    见 DESIGN §2.3 图模型 + D4/D5 唯一标识策略 + D11。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -3</verify>
  <done>编译通过；StudentNode 和 ExamNode 类可被 GraphNodeRepository 引用，各自含 toProperties() 覆盖</done>
  <depends_on>T02</depends_on>
</task>

<task id="T08" parallel="true" status="done">
  <name>新增 AttendedEdge + TestedEdge（Neo4j 边类）</name>
  <read_files>
    infrastructure/neo4j/edge/EdgeType.java
    infrastructure/neo4j/edge/GraphEdge.java
    infrastructure/neo4j/edge/ExtractsEdge.java
  </read_files>
  <write_files>
    infrastructure/neo4j/edge/AttendedEdge.java
    infrastructure/neo4j/edge/TestedEdge.java
  </write_files>
  <action>
    1. AttendedEdge：@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true) extends GraphEdge
       - 纯结构边（无额外属性），构造器：public AttendedEdge(String studentNodeId, String examNodeId)
         调用 super(EdgeType.ATTENDED.getRelationshipType())，设置 sourceNodeId=studentNodeId, targetNodeId=examNodeId
    2. TestedEdge：@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true) extends GraphEdge
       - 纯结构边（无额外属性），构造器：public TestedEdge(String examNodeId, String kpNodeId)
         调用 super(EdgeType.TESTED.getRelationshipType())，设置 sourceNodeId=examNodeId, targetNodeId=kpNodeId
    延续既有边类模式（参考 ExtractsEdge 的简单双参构造器）。分数不存边，仅存 MySQL。
    见 DESIGN §2.3：ATTENDED 和 TESTED 均为纯结构边，无属性。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -3</verify>
  <done>编译通过；AttendedEdge 和 TestedEdge 类可被 GraphNodeRepository 引用</done>
  <depends_on>T02</depends_on>
</task>

<task id="T09" parallel="false" status="done">
  <name>GraphNodeRepository 扩展：新增级联删除方法（toNodeProps 已在 T0A 重构完成）</name>
  <read_files>
    infrastructure/neo4j/repository/GraphNodeRepository.java
    infrastructure/neo4j/node/StudentNode.java
    infrastructure/neo4j/node/ExamNode.java
    infrastructure/neo4j/edge/AttendedEdge.java
    infrastructure/neo4j/edge/TestedEdge.java
    infrastructure/neo4j/node/NodeType.java
    infrastructure/neo4j/edge/EdgeType.java
  </read_files>
  <write_files>
    infrastructure/neo4j/repository/GraphNodeRepository.java
  </write_files>
  <action>
    新增级联删除方法（见 D10 全局删除约束）。toNodeProps() 已在 T0A 中重构为 `return node.toProperties()`，本任务不修改该逻辑。

    新增方法：
    1. deleteEdgesByExamNo(String examNo, String edgeType)：
       - ATTENDED 方向 (Student→Exam)：MATCH (:Student)-[r:ATTENDED]->(e:Exam {examNo: $examNo}) DELETE r
       - TESTED 方向 (Exam→KP)：MATCH (e:Exam {examNo: $examNo})-[r:TESTED]->(:KnowledgePoint) DELETE r
       - 幂等：若无边可删则不报错，返回删除边数（0）
       - 使用 Neo4jClient.query().bindAll(params).run() 模式
    2. deleteExamNode(String examNo)：
       - Cypher：MATCH (e:Exam {examNo: $examNo}) DETACH DELETE e
       - 幂等：若节点不存在则跳过（run() 不抛异常，affected count = 0）
    注意：Student 和 KnowledgePoint 节点不删除（全局约束 C5 共享节点保留）。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>编译通过；deleteEdgesByExamNo/deleteExamNode 方法存在且 Cypher 语法无错误</done>
  <depends_on>T07, T08, T0A</depends_on>
</task>

<task id="T10" parallel="false" status="done">
  <name>新增 CsvGradeParser（实现 FileParser 接口，CSV 双行表头解析器）</name>
  <read_files>
    application/document/parser/FileParser.java
    application/document/parser/FileParseRequest.java
    application/document/parser/FileParseResult.java
    application/document/parser/DocumentParser.java
    common/exception/ErrorCode.java
    common/exception/BusinessException.java
    docs/学生成绩表示例.csv
  </read_files>
  <write_files>
    application/document/parser/CsvGradeParser.java
  </write_files>
  <action>
    新增 CsvGradeParser，实现 FileParser 接口（见 DESIGN D12），使用 Apache Commons CSV 解析双行表头格式。

    ====== FileParser 实现 ======
    - supportedType() → FileParseType.CSV_GRADE
    - supportedExtensions() → Set.of(".csv")
    - parse(FileParseRequest request) → FileParseResult&lt;CsvParsePayload&gt;

    ====== 内部数据类 CsvParsePayload ======
    - examNo, examName, examDate(LocalDate)
    - students: List&lt;StudentRecord&gt;（每元素 = studentNo + name + className + totalScore + classRank + scoreDetails）
    - scoreDetails: List&lt;ScoreDetail&gt;（每元素 = questionLabel, kpNames(List), rawScore(Integer nullable), maxScore(Integer nullable)）
    - knowledgePoints: Set&lt;String&gt;（去重后的知识点名称集合，用于创建 TESTED 边）

    ====== 解析流程 ======
    1. 从 FileParseRequest 获取 rawBytes 和 subject
    2. 先尝试 UTF-8 BOM 检测，失败回退 GBK（D2 + REQUIREMENT 编码兼容性假设）
    3. 使用 CSVFormat.DEFAULT.withTrim() 构建解析器，读前两行作表头
    4. 校验表头行数 = 2（否则抛 A0011）
    5. 从第 1 行提取元数据列索引：学号、姓名、班级、考试编号、考试名称、日期、总分、班级排名，以及题号列（题1~题N，动态数量）
    6. 从第 2 行对应题号列提取知识点名称（支持 `;` 半角分号分隔多个知识点）
    7. 逐行解析数据行（从第 3 行开始）：
       - 提取学号、姓名、班级、考试编号、考试名称、考试日期、总分、班级排名
       - 每题成绩按 `raw_score/max_score` 格式解析，缺考标记 `-/-` → rawScore=null, maxScore=null（AC-6）
       - 成绩格式异常 → 抛 A0011 附行号+列号
    8. 校验列数一致性（否则抛 A0011 附行号）
    9. 返回 FileParseResult&lt;CsvParsePayload&gt;

    标注 @Component，Spring 自动注册到 FileParserRegistry。
    见 REQUIREMENT AC-1/AC-2/AC-6 + DESIGN D2/D6/D12。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>CsvGradeParser 类存在且编译通过；实现 FileParser 接口，标注 @Component</done>
  <depends_on>T01, T0B</depends_on>
</task>

<task id="T11" parallel="false" status="done">
  <name>新增 GradeService 接口 + GradeServiceImpl（成绩上传/查询/删除全链路编排）</name>
  <read_files>
    application/document/service/DocumentService.java
    application/document/service/impl/DocumentServiceImpl.java
    application/document/parser/CsvGradeParser.java
    application/document/parser/FileParserRegistry.java
    application/document/model/GradeUploadResultBO.java
    application/document/model/DeleteResultBO.java
    infrastructure/storage/FileStorageService.java
    infrastructure/neo4j/repository/GraphNodeRepository.java
    infrastructure/mysql/document/ExamRecordRepository.java
    infrastructure/mysql/document/ExamRecordDO.java
    common/util/Md5Utils.java
    common/exception/ErrorCode.java
    common/exception/BusinessException.java
  </read_files>
  <write_files>
    application/document/service/GradeService.java
    application/document/service/impl/GradeServiceImpl.java
  </write_files>
  <action>
    GradeService 接口方法（见 DESIGN §2.1 + §2.1a）：
    - uploadGradeCsv(MultipartFile file, String subject) → GradeUploadResultBO
    - queryByExam(String examNo) → List&lt;GradeRecordBO&gt;
    - deleteByExamNo(String examNo) → DeleteResultBO

    GradeServiceImpl 实现（@Service @RequiredArgsConstructor，注入 FileStorageService / GraphNodeRepository / ExamRecordRepository / CsvGradeParser / FileParserRegistry）：

    ====== uploadGradeCsv ======
    1. 读 bytes → Md5Utils.computeMd5(bytes)
    2. examRecordRepository.findByCsvMd5AndIsDeletedFalse(md5) 判重 → 若存在：
       a. 获取 examNo → 调用内部 deleteByExamNoInternal(examNo)（复用删除链路，D8 幂等覆盖）
    3. 通过 fileParserRegistry.getParser(filename) 获取 CsvGradeParser
       → parser.parse(new FileParseRequest(inputStream, filename, subject, bytes))
       → 从 FileParseResult 提取 CsvParsePayload（见 D12）
    4. MinIO 上传：fileStorageService.uploadFile(new ByteArrayInputStream(bytes), "grades/" + UUID.randomUUID() + ".csv", "text/csv")
    5. MySQL：构建 List&lt;ExamRecordDO&gt;（每学生一条），examRecordRepository.saveAll()；每条的 scoreDetails 通过 Jackson ObjectMapper 序列化为 JSON 字符串
    6. Neo4j（见 §2.1 时序图）：
       a. 先 MERGE ExamNode(examNo) → 拿到 nodeId
       b. 遍历学生：MERGE StudentNode(studentNo) + CREATE AttendedEdge(Student→Exam)
       c. 遍历去重 KP：MERGE KnowledgePointNode(name+subject 无 documentId) + CREATE TestedEdge(Exam→KP)
    7. 返回 GradeUploadResultBO

    ====== queryByExam ======
    - examRecordRepository.findByExamNoAndIsDeletedFalse(examNo) → 映射为 GradeRecordBO 列表返回

    ====== deleteByExamNo ======
    遵循全局删除约束（@.specs/CONTEXT.md §全局删除约束 + D10）：
    1. examRecordRepository.findByExamNoAndIsDeletedFalse(examNo) → 若空，幂等返回 success（C2）
    2. 设中间状态：遍历 records 调用 markDeleted() + saveAll — MySQL 事务保障（C3）
    3. MinIO 文件删除：fileStorageService.deleteFile(csvFilePath) — try-catch FileNotFoundException，记 WARN 不抛异常（C2）
    4. Neo4j：graphNodeRepository.deleteEdgesByExamNo(examNo, "ATTENDED") + deleteEdgesByExamNo(examNo, "TESTED")（C4）
    5. Neo4j：graphNodeRepository.deleteExamNode(examNo) — DETACH DELETE 兜底
    6. MySQL 物理删除：examRecordRepository.deleteAll(records)
    7. 返回 DeleteResultBO（含删除记录数、文件路径、边数）
    注意：Student 和 KnowledgePoint 节点保留（C5 共享节点）。

    类结构延续 DocumentServiceImpl 模式：分节注释（上传/查询/删除/工具方法），构造器注入。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>编译通过；GradeService 接口和 GradeServiceImpl 类存在，全链路编排逻辑完整，使用 FileParserRegistry 路由</done>
  <depends_on>T03, T05, T06, T09, T10</depends_on>
</task>

<task id="T12" parallel="false" status="done">
  <name>DocumentService 接口扩展 + DocumentServiceImpl 改造（PDF 路径 textbooks/ + CSV 委托）</name>
  <read_files>
    application/document/service/DocumentService.java
    application/document/service/impl/DocumentServiceImpl.java
    application/document/service/GradeService.java
    common/util/Md5Utils.java
  </read_files>
  <write_files>
    application/document/service/DocumentService.java
    application/document/service/impl/DocumentServiceImpl.java
  </write_files>
  <action>
    1. DocumentService 接口新增方法签名：
       - GradeUploadResultBO uploadGradeCsv(MultipartFile file, String subject)
       - DeleteResultBO deleteGradeByExamNo(String examNo)

    2. DocumentServiceImpl 改造：
       a. 注入 GradeService（新增 private final GradeService gradeService）
       b. 新增方法实现：
          - uploadGradeCsv → 委托 gradeService.uploadGradeCsv(file, subject)
          - deleteGradeByExamNo → 委托 gradeService.deleteByExamNo(examNo)
       c. PDF 上传路径修改（见 DESIGN §9.3 MinIO 契约）：
          - 旧：UUID.randomUUID() + ".pdf"
          - 新："textbooks/" + UUID.randomUUID() + ".pdf"
       d. 确认 computeMd5() 调用已替换为 Md5Utils.computeMd5()（T03 已完成）

    注意：DocumentServiceImpl 仅做委托，不在本类中写 CSV 业务逻辑。符合 R7.1 范围控制。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>编译通过；DocumentService 接口有 uploadGradeCsv/deleteGradeByExamNo 方法，PDF 路径前缀为 textbooks/</done>
  <depends_on>T03, T11</depends_on>
</task>

<task id="T13" parallel="false" status="done">
  <name>DocumentController 扩展：FileParserRegistry 路由 + 成绩查询 + 级联删除端点</name>
  <read_files>
    api/document/controller/DocumentController.java
    api/document/dto/GradeUploadResultVO.java
    api/document/dto/DeleteResultVO.java
    application/document/service/DocumentService.java
    application/document/parser/FileParserRegistry.java
    common/ApiResponse.java
    common/PageResult.java
  </read_files>
  <write_files>
    api/document/controller/DocumentController.java
  </write_files>
  <action>
    在 DocumentController 中新增以下能力（见 DESIGN D1/D12 文件路由 + §2.2 + §9.3）：

    1. 注入 FileParserRegistry（新增 private final FileParserRegistry fileParserRegistry）

    2. 修改 POST /api/v1/document/upload（D12 策略+工厂路由）：
       在现有 upload 方法开头增加 FileParserRegistry 路由：
       - String ext = file.getOriginalFilename().substring(...).toLowerCase()
       - fileParserRegistry.getParser(ext) → 若为 CSV_GRADE 类型，委托 documentService.uploadGradeCsv(file, subject) → GradeUploadResultVO.from(BO)
       - 否则走既有 PDF 链路（不变）
       注意：PDF 的 DocumentParser 尚未实现 FileParser，.pdf 扩展名在 registry 中暂无注册，走 else 分支

    3. 新增 GET /api/v1/document/grade/exam/{examNo}（AC-4 成绩查询）：
       @GetMapping("/grade/exam/{examNo}")
       委托 documentService.queryGradeByExam(examNo) → 转为 VO 列表返回

    4. 新增 DELETE /api/v1/document/grade/exam/{examNo}（AC-7 级联删除）：
       @DeleteMapping("/grade/exam/{examNo}")
       委托 documentService.deleteGradeByExamNo(examNo) → DeleteResultVO.from(BO) → ApiResponse.success(vo)

    延续既有 Controller 模式：@RequiredArgsConstructor + ApiResponse.success() 包装。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>编译通过；DocumentController 使用 FileParserRegistry 路由 + 成绩查询 + 级联删除三个端点</done>
  <depends_on>T05, T11, T0B</depends_on>
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
