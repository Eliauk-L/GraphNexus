# TASK: 成绩管理重构

- **Change ID**: `grade-management-refactor`
- **关联**: `@.specs/grade-management-refactor/REQUIREMENT.md`、`@.specs/grade-management-refactor/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P]           ← DDL + 依赖 + 错误码
Wave 2 (parallel): T04[P], T05[P], T06[P], T07[P]   ← 枚举 + 模型（依赖 Wave 1 完成）
Wave 3 (parallel): T08[P], T09[P]                    ← 解析器（依赖 T04, T05）
Wave 4 (parallel): T10[P], T11[P]                    ← 事件 + Repository（依赖 T05, T06）
Wave 5 (parallel): T12[P], T13[P], T14[P], T15[P]   ← Service + Listener（依赖 Wave 3+4）
Wave 6:            T16                                ← Controller（依赖 T13）
Wave 7 (parallel): T17[P], T18[P]                    ← 测试（依赖 T08, T09, T14）
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>SQL DDL — exam_record 表删除 csv 列 + 索引</name>
  <read_files>
    src/main/resources/db/init.sql
  </read_files>
  <write_files>
    .specs/grade-management-refactor/migrations/20260619_grade-management-refactor_T01_drop_csv_columns.sql
    src/main/resources/db/init.sql
  </write_files>
  <action>
    1. 创建 migration SQL 文件：ALTER TABLE exam_record DROP COLUMN csv_file_path, DROP COLUMN csv_md5, DROP INDEX idx_csv_md5
    2. 更新 init.sql 中 exam_record 建表语句：删除 csv_file_path 列定义、csv_md5 列定义、idx_csv_md5 索引定义。保留其他列和索引（uk_student_exam、idx_exam_no、idx_student_no）不变
    3. 确认 UNIQUE KEY uk_student_exam (student_no, exam_no) 存在（用于应用层去重保护）
  </action>
  <verify>
    grep -c "csv_file_path\|csv_md5\|idx_csv_md5" src/main/resources/db/init.sql | xargs test 0 -eq
  </verify>
  <done>init.sql 中 exam_record 建表语句零 csv_ 引用</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>pom.xml — 引入 Apache POI 依赖</name>
  <read_files>
    pom.xml
  </read_files>
  <write_files>
    pom.xml
  </write_files>
  <action>
    在 pom.xml 的 dependencies 中新增 Apache POI 依赖：
    - org.apache.poi:poi:5.2.5
    - org.apache.poi:poi-ooxml:5.2.5
    （参考 DESIGN §0 和 ADR-017）
    注意：pom.xml 在禁动清单中，本次显式解禁仅新增这两个依赖，不修改现有任何依赖。
  </action>
  <verify>mvn dependency:tree -Dincludes=org.apache.poi 2>&1 | grep -q "poi:poi:5.2.5\|poi-ooxml:5.2.5"</verify>
  <done>mvn 依赖树中出现 poi 和 poi-ooxml 5.2.5</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>ErrorCode — 新增 A0016 exam_no 重复拒绝</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </write_files>
  <action>
    在 ErrorCode 枚举中新增 A0016：
    - errorCode = "A0016"
    - httpStatus = HttpStatus.CONFLICT (409)
    - defaultUserTip = "考试编号已存在，请先删除该考试再重新上传"
    参考既有 A0014（exam not found）和 A0015（delete exam not found）的格式和位置
  </action>
  <verify>grep -c "A0016" src/main/java/com/graphnexus/common/exception/ErrorCode.java | xargs test 0 -lt</verify>
  <done>ErrorCode 枚举包含 A0016，HTTP 409</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>GradeFileType — CSV_GRADE 拆为 CSV + EXCEL</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java
    src/main/java/com/graphnexus/application/file/parse/FileParseType.java
    src/main/java/com/graphnexus/application/file/parse/FileParser.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java
  </write_files>
  <action>
    修改 GradeFileType 枚举：
    - 删除 CSV_GRADE
    - 新增 CSV（实现 FileParseType，name() 返回 "CSV"）
    - 新增 EXCEL（实现 FileParseType，name() 返回 "EXCEL"）
    参考 DESIGN §1 D7
  </action>
  <verify>
    grep -c "CSV_GRADE" src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java | xargs test 0 -eq
    grep -c '"CSV"' src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java | xargs test 0 -lt
    grep -c '"EXCEL"' src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java | xargs test 0 -lt
  </verify>
  <done>枚举含 CSV 和 EXCEL 两个值，无 CSV_GRADE</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>CsvParsePayload → GradeParsePayload 重命名 + StudentRecord/ScoreDetail 内迁</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
    src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java
  </write_files>
  <action>
    1. 从 CsvGradeParser.java 中提取 CsvParsePayload 内部 record → 新建独立文件 GradeParsePayload.java 到 model 包
       - 重命名为 GradeParsePayload（examNo, examName, examDate, subject, students: List&lt;StudentRecord&gt;, questionCount, knowledgePoints: List&lt;String&gt;）
       - StudentRecord 和 ScoreDetail 也提取为 GradeParsePayload 的内部 record（保持嵌套关系）
    2. 更新 CsvGradeParser.java 中所有 CsvParsePayload 引用为 GradeParsePayload
    参考 DESIGN §1 D1（共享 payload）
  </action>
  <verify>
    grep -c "CsvParsePayload" src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java | xargs test 0 -eq
    grep -c "GradeParsePayload" src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java | xargs test 0 -lt
    test -f src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
  </verify>
  <done>CsvParsePayload 全部替换为 GradeParsePayload，新文件存在于 model 包</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>ExamRecordDO — 删除 csvFilePath 和 csvMd5 字段</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java
  </write_files>
  <action>
    从 ExamRecordDO 中删除：
    - csvFilePath 字段（@Column(name = "csv_file_path")）
    - csvMd5 字段（@Column(name = "csv_md5")）
    - 对应 getter/setter（如有显式定义）
    其他字段和 markDeleted() 方法保留不变
    参考 DESIGN §0.5.1 触碰模块
  </action>
  <verify>
    grep -c "csvFilePath\|csvMd5\|csv_file_path\|csv_md5" src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java | xargs test 0 -eq
  </verify>
  <done>ExamRecordDO 中零 csv_ 引用</done>
  <depends_on></depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>Grade BO/VO — 删除 csvFilePath/csvMd5 字段 + 新增 fileType</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/model/GradeUploadResultBO.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeRecordBO.java
    src/main/java/com/graphnexus/api/file/dto/grade/GradeUploadResultVO.java
    src/main/java/com/graphnexus/api/file/dto/grade/GradeRecordVO.java
    src/main/java/com/graphnexus/api/file/dto/grade/DeleteResultVO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/model/GradeUploadResultBO.java
    src/main/java/com/graphnexus/api/file/dto/grade/GradeUploadResultVO.java
    src/main/java/com/graphnexus/api/file/dto/grade/GradeRecordVO.java
    src/main/java/com/graphnexus/api/file/dto/grade/DeleteResultVO.java
  </write_files>
  <action>
    1. GradeUploadResultBO：删除 csvFilePath、csvMd5 字段；新增 fileType 字段（String，存储 GradeFileType.name() 值如 "CSV"/"EXCEL"）
    2. GradeUploadResultVO：同步删除 csvFilePath、csvMd5 字段；新增 fileType 字段；更新 from(GradeUploadResultBO) 转换方法
    3. GradeRecordVO：删除 id 字段中的 csv 引用（如有）；确认不包含 csvFilePath/csvMd5
    4. DeleteResultVO：删除 filePath、deletedEdgeCount 字段；只保留 examNo 和 deletedRecordCount；不再依赖 DeleteResultBO（textbook 包），改为直接用字段构造
    参考 DESIGN §1 D5、REQUIREMENT AC-1（无 csvFilePath/csvMd5）
  </action>
  <verify>
    grep -rn "csvFilePath\|csvMd5\|filePath\|deletedEdgeCount" src/main/java/com/graphnexus/api/file/dto/grade/ | xargs test 0 -eq
    grep -rn "csvFilePath\|csvMd5" src/main/java/com/graphnexus/application/file/grade/model/ | xargs test 0 -eq
  </verify>
  <done>所有 Grade VO/BO 中零 csv_ 字段，DeleteResultVO 不依赖 DeleteResultBO</done>
  <depends_on></depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>CsvGradeParser — 适配新枚举 + GradeParsePayload</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
    src/main/java/com/graphnexus/application/file/parse/FileParser.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java
  </write_files>
  <action>
    更新 CsvGradeParser：
    1. supportedType() 返回 GradeFileType.CSV（原 CSV_GRADE）
    2. parse() 方法返回 FileParseResult&lt;GradeParsePayload&gt;（原 CsvParsePayload）
    3. 内部 StudentRecord/ScoreDetail 引用改为 GradeParsePayload.StudentRecord/GradeParsePayload.ScoreDetail
    4. businessType() 仍返回 FileParser.BIZ_GRADE（不变）
    解析逻辑（双行表头、UTF-8/GBK、BOM、-/- 缺考）完全不变
    参考 DESIGN §0.5.2（沿用 FileParser 接口）
  </action>
  <verify>mvn test -pl . -Dtest="CsvGradeParserTest" -DfailIfNoTests=false 2>&1 | tail -20</verify>
  <done>CsvGradeParserTest 全部通过，CsvGradeParser 编译无错误</done>
  <depends_on>T04, T05</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>ExcelGradeParser — 新增 Excel 双行表头解析器</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
    src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java
    src/main/java/com/graphnexus/application/file/parse/FileParser.java
    src/main/java/com/graphnexus/application/file/parse/FileParserRegistry.java
    src/main/java/com/graphnexus/application/file/parse/FileParseRequest.java
    src/main/java/com/graphnexus/application/file/parse/FileParseResult.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/parser/ExcelGradeParser.java
  </write_files>
  <action>
    新建 ExcelGradeParser 实现 FileParser 接口：
    - supportedType() 返回 GradeFileType.EXCEL
    - businessType() 返回 FileParser.BIZ_GRADE
    - supportedExtensions() 返回 Set.of(".xlsx", ".xls")
    - parse() 使用 Apache POI WorkbookFactory.create(inputStream) 读取工作簿
    - 第 1 行 = 列名，第 2 行 = 知识点（与 CSV 双行表头结构一致）
    - 列分类：前 8 列元数据（学号,姓名,班级,考试编号,考试名称,日期,总分,班级排名），其余为题列
    - 日期处理：CellType.NUMERIC + DateUtil.isCellDateFormatted() → LocalDate；CellType.STRING → 尝试 3 种格式解析
    - 成绩格式：raw_score/max_score，缺考 -/-、/、空字符串均视为 null
    - 输出：FileParseResult&lt;GradeParsePayload&gt;
    参考 DESIGN §1 D1、ADR-017
  </action>
  <verify>
    mvn compile -pl . 2>&1 | grep -E "BUILD|ERROR"
    grep -c "implements FileParser" src/main/java/com/graphnexus/application/file/grade/parser/ExcelGradeParser.java | xargs test 0 -lt
  </verify>
  <done>ExcelGradeParser 编译通过，Spring 自动注册到 FileParserRegistry</done>
  <depends_on>T02, T04, T05</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>Event — GradeUploadedEvent 扩展 + GradeDeletedEvent 新建</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/event/GradeUploadedEvent.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/event/GradeUploadedEvent.java
    src/main/java/com/graphnexus/application/file/grade/event/GradeDeletedEvent.java
  </write_files>
  <action>
    1. GradeUploadedEvent：扩展字段
       - 现有：examNo (String), subject (String), knowledgePoints (List&lt;String&gt;)
       - 语义变化：现在在 MySQL 写入后发布（非 Neo4j 构建后），Listener 自行从 MySQL 读取学生明细
       - 字段不变，但确保构造器完整（source + examNo + subject + knowledgePoints）
    2. GradeDeletedEvent（新建）：
       - 继承 ApplicationEvent
       - 字段：examNo (String), recordCount (int)
       - 构造器：(Object source, String examNo, int recordCount)
    参考 DESIGN §1 D2（事件轻量）、§1 D3（@Order 排序）、REQUIREMENT § 架构约束
  </action>
  <verify>
    mvn compile -pl . 2>&1 | grep -E "BUILD|ERROR"
    test -f src/main/java/com/graphnexus/application/file/grade/event/GradeDeletedEvent.java
  </verify>
  <done>GradeUploadedEvent 字段不变，GradeDeletedEvent 新建且编译通过</done>
  <depends_on>T05</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>ExamRecordRepository — 删除 csvMd5 查询 + 新增条件查询 + JpaSpecificationExecutor</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
  </write_files>
  <action>
    更新 ExamRecordRepository：
    1. 删除方法：
       - findByCsvMd5AndIsDeleted(String csvMd5, Integer isDeleted) — 不再需要 MD5 判重
    2. 新增扩展接口：extends JpaSpecificationExecutor&lt;ExamRecordDO&gt;
    3. 新增方法（方法名派生）：
       - boolean existsByExamNoAndIsDeletedFalse(String examNo) — exam_no 去重检查
       - List&lt;ExamRecordDO&gt; findByExamNoAndIsDeleted(String examNo, Integer isDeleted) — 保留（Listener 读取）
    4. 保留所有其他方法不变：
       - findByExamNoAndIsDeleted — 保留
       - findByStudentNoAndIsDeleted — 保留
       - findByStudentNoAndSubjectAndIsDeleted — 保留
       - findDistinctSubjectByIsDeletedAndSubjectIsNotNullOrderBySubject — 保留
       - findStudentByName / findStudentByNo / findDistinctExams — 保留
    参考 DESIGN §1 D4（Specification 动态查询）
  </action>
  <verify>
    grep -c "findByCsvMd5" src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java | xargs test 0 -eq
    grep -c "JpaSpecificationExecutor" src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java | xargs test 0 -lt
    grep -c "existsByExamNoAndIsDeletedFalse" src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java | xargs test 0 -lt
  </verify>
  <done>Repository 删除 csvMd5 方法、新增 Specification + existsByExamNo 方法</done>
  <depends_on>T06</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>GradeUploadService — 事件驱动重构（去 MinIO + 去图谱 + exam_no 判重）</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java
    src/main/java/com/graphnexus/application/file/grade/service/GradeService.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeUploadResultBO.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java
    src/main/java/com/graphnexus/application/file/grade/event/GradeUploadedEvent.java
    src/main/java/com/graphnexus/application/file/parse/FileParserRegistry.java
    src/main/java/com/graphnexus/application/file/parse/FileParser.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java
  </write_files>
  <action>
    重构 GradeUploadService.upload() 方法，按 DESIGN §2.1 数据流：
    1. 读取文件字节 → FileParserRegistry.getParser(filename, BIZ_GRADE) → parse()
    2. exam_no 去重：examRecordRepository.existsByExamNoAndIsDeletedFalse(examNo) → true 则 throw BusinessException(ErrorCode.A0016)
    3. GradeParsePayload → List&lt;ExamRecordDO&gt; 转换（注意：DO 不再设 csvFilePath/csvMd5）
    4. examRecordRepository.saveAll(records)
    5. eventPublisher.publishEvent(new GradeUploadedEvent(this, examNo, subject, knowledgePoints))
    6. 返回 GradeUploadResultBO（含 fileType = parser.supportedType().name()）
    
    删除的逻辑：
    - MD5 计算与判重逻辑（删除 Md5Utils 注入）
    - MinIO 文件上传（删除 FileStorageService 注入 + uploadFile 调用）
    - Neo4j 图谱构建（删除 GraphNodeRepository 注入 + MERGE/CREATE 调用）
    - 原有的 csvFilePath/csvMd5 字段赋值
    
    保留：
    - FileParserRegistry 注入
    - ExamRecordRepository 注入
    - ApplicationEventPublisher 注入
    - GradeService 注入（不再需要，原用于 dedup 时调用 deleteByExamNo）
    
    注意：GradeService 注入可以移除——因为 exam_no 判重策略是拒绝而非自动覆盖，不再需要在上传前调用 deleteByExamNo
  </action>
  <verify>
    grep -c "FileStorageService\|MinioFileStorageService\|GraphNodeRepository\|Md5Utils" src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java | xargs test 0 -eq
    grep -c "ErrorCode.A0016\|existsByExamNoAndIsDeletedFalse" src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java | xargs test 0 -lt
    grep -c "eventPublisher.publishEvent" src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java | xargs test 0 -lt
  </verify>
  <done>GradeUploadService 零 MinIO/Neo4j 依赖，exam_no 判重拒绝，事件发布正确</done>
  <depends_on>T08, T09, T10, T11</depends_on>
</task>

<task id="T13" parallel="true" status="pending">
  <name>GradeServiceImpl — 去 MinIO + 去图谱 + 条件查询 + 事件发布删除</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java
    src/main/java/com/graphnexus/application/file/grade/service/GradeService.java
    src/main/java/com/graphnexus/application/file/grade/event/GradeDeletedEvent.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeRecordBO.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java
    src/main/java/com/graphnexus/application/file/grade/service/GradeService.java
  </write_files>
  <action>
    重构 GradeServiceImpl，按 DESIGN §2.2 + §2.3：

    A. 删除 MinIO 和 Neo4j 调用：
       - 删除 FileStorageService 注入
       - 删除 GraphNodeRepository 注入
       - deleteByExamNo() 中删除 MinIO 文件删除步骤（原 C4-1）
       - deleteByExamNo() 中删除 Neo4j 边/节点删除步骤（原 C4-2, C4-3）

    B. 删除链路新逻辑（见 DESIGN §2.2）：
       1. examRecordRepository.findByExamNoAndIsDeleted(examNo, 0)
       2. 空 → 幂等返回（deletedRecordCount=0）
       3. examRecordRepository.deleteAll(records) — 直接物理删除
       4. eventPublisher.publishEvent(new GradeDeletedEvent(this, examNo, records.size()))
       5. 返回 DeleteResultBO(examNo, recordCount) — 仅两个字段

    C. 条件查询新方法 queryByConditions（见 DESIGN §2.3）：
       - 方法签名：PageResult&lt;GradeRecordBO&gt; queryByConditions(String examNo, String examName, String studentNo, String name, String className, String subject, int pageNum, int pageSize)
       - 使用 Specification&lt;ExamRecordDO&gt; 动态构建查询条件：
         * examNo → 精确匹配
         * examName → LIKE %value%
         * studentNo → 精确匹配
         * name → LIKE %value%
         * className → 精确匹配
         * subject → 精确匹配
         * 始终过滤 isDeleted = 0
       - repository.findAll(spec, PageRequest.of(pageNum-1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")))
       - 结果映射 ExamRecordDO → GradeRecordBO

    D. GradeService 接口更新：
       - 删除 queryByExam(String examNo) 方法声明
       - 新增 queryByConditions(...) 方法声明
       - 删除 listExams 方法声明（不再需要独立考试列表，条件查询可满足）
       - deleteByExamNo 返回类型改为 DeleteResultBO（简化为 examNo + recordCount）

    参考 DESIGN §1 D4（Specification）、D5（删除简化）
  </action>
  <verify>
    grep -c "FileStorageService\|MinioFileStorageService\|GraphNodeRepository" src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java | xargs test 0 -eq
    grep -c "GradeDeletedEvent" src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java | xargs test 0 -lt
    grep -c "Specification<ExamRecordDO>" src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java | xargs test 0 -lt
  </verify>
  <done>GradeServiceImpl 零 MinIO/Neo4j 依赖，含 Specification 条件查询，删除发布 GradeDeletedEvent</done>
  <depends_on>T10, T11</depends_on>
</task>

<task id="T14" parallel="true" status="pending">
  <name>GradeGraphEventListener — 新建图谱构建+清理监听器</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/event/GradeUploadedEvent.java
    src/main/java/com/graphnexus/application/file/grade/event/GradeDeletedEvent.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/ExamNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/StudentNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/KnowledgePointNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/AttendedEdge.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/TestedEdge.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/grade/event/GradeGraphEventListener.java
  </write_files>
  <action>
    新建 GradeGraphEventListener（@Component），负责成绩事件驱动的图谱操作：

    A. 监听 GradeUploadedEvent（@Order(1)，优先于融合 Listener）：
       1. 从 examRecordRepository.findByExamNoAndIsDeleted(examNo, 0) 读取全部学生记录
       2. MERGE StudentNode（按 studentNo）→ graphNodeRepository.save(studentNode)
       3. MERGE ExamNode（按 examNo，含 name/examDate/subject）→ graphNodeRepository.save(examNode)
       4. 从 score_details JSON 提取所有去重知识点 → MERGE KnowledgePointNode（按 name+subject）→ graphNodeRepository.save(kpNode)
       5. 为每个学生创建 AttendedEdge（studentNo → examNo）→ graphNodeRepository.saveEdge(edge)
       6. 为每个知识点创建 TestedEdge（examNo → knowledgePointId）→ graphNodeRepository.saveEdge(edge)
       7. 日志记录：traceId + examNo + studentCount + kpCount + edgeCount

    B. 监听 GradeDeletedEvent：
       1. graphNodeRepository.deleteEdgesByExamNo(examNo, "ATTENDED")
       2. graphNodeRepository.deleteEdgesByExamNo(examNo, "TESTED")
       3. graphNodeRepository.deleteExamNode(examNo)
       4. 日志记录：traceId + examNo + 删除结果

    参考 DESIGN §2.1 + §2.2（数据流）、§1 D2（从 MySQL 读取明细）、ADR-018
  </action>
  <verify>
    grep -c "@EventListener" src/main/java/com/graphnexus/application/graph/grade/event/GradeGraphEventListener.java | xargs test 1 -lt
    grep -c "@Order(1)" src/main/java/com/graphnexus/application/graph/grade/event/GradeGraphEventListener.java | xargs test 0 -lt
    mvn compile -pl . 2>&1 | grep -E "BUILD|ERROR"
  </verify>
  <done>GradeGraphEventListener 编译通过，@Order(1) 设置正确，监听两个事件</done>
  <depends_on>T10</depends_on>
</task>

<task id="T15" parallel="true" status="pending">
  <name>GradeUploadedEventListener — 加 @Order(2) 确保图谱构建后融合</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/fusion/event/GradeUploadedEventListener.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/fusion/event/GradeUploadedEventListener.java
  </write_files>
  <action>
    在 GradeUploadedEventListener.onGradeUploaded() 方法上加 @Order(2) 注解：
    - 确保融合（fuseFull）在图谱构建（GradeGraphEventListener @Order(1)）之后执行
    - 原有逻辑不变：fusionService.fuseFull() → eventPublisher.publishEvent(GraphChangedEvent)
    参考 DESIGN §1 D3
  </action>
  <verify>
    grep -c "@Order(2)" src/main/java/com/graphnexus/application/graph/fusion/event/GradeUploadedEventListener.java | xargs test 0 -lt
  </verify>
  <done>GradeUploadedEventListener 标注 @Order(2)</done>
  <depends_on>T10</depends_on>
</task>

<task id="T16" parallel="false" status="pending">
  <name>GradeController — 合并查询端点 + 适配新 API</name>
  <read_files>
    src/main/java/com/graphnexus/api/file/controller/GradeController.java
    src/main/java/com/graphnexus/application/file/grade/service/GradeService.java
    src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java
    src/main/java/com/graphnexus/api/file/dto/grade/GradeUploadResultVO.java
    src/main/java/com/graphnexus/api/file/dto/grade/GradeRecordVO.java
    src/main/java/com/graphnexus/api/file/dto/grade/DeleteResultVO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/file/controller/GradeController.java
  </write_files>
  <action>
    重构 GradeController：

    A. 删除端点：
       - DELETE @GetMapping("/exam/{examNo}") — 原 queryByExam 端点，合并到下方

    B. 修改现有 GET 端点为条件查询：
       - GET /api/v1/file/grades
       - 参数（全部可选）：@RequestParam(required=false) examNo, examName, studentNo, name, className, subject
       - 分页参数：pageNum(default=1), pageSize(default=20)
       - 调用 gradeService.queryByConditions(...)
       - 返回 PageResult&lt;GradeRecordVO&gt;

    C. 保留不变：
       - POST /api/v1/file/grades/upload — 委托给 GradeUploadService（返回 VO 适配新的 GradeUploadResultBO）
       - DELETE /api/v1/file/grades/exam/{examNo} — 委托给 GradeService.deleteByExamNo（返回简化的 DeleteResultVO）

    D. 移除的注入/import：
       - 如有对 queryByExam 的调用 → 删除
       - 如有对 listExams 的调用 → 删除（条件查询替代）
       - 清理不再使用的 import
  </action>
  <verify>
    grep -c 'GetMapping.*exam.*examNo' src/main/java/com/graphnexus/api/file/controller/GradeController.java | xargs test 0 -eq
    grep -c "queryByConditions" src/main/java/com/graphnexus/api/file/controller/GradeController.java | xargs test 0 -lt
  </verify>
  <done>GradeController 仅有 upload + 条件查询 GET + 删除 DELETE 三个端点</done>
  <depends_on>T13</depends_on>
</task>

<task id="T17" parallel="true" status="pending">
  <name>CsvGradeParserTest — 适配 GradeParsePayload + GradeFileType.CSV</name>
  <read_files>
    src/test/java/com/graphnexus/application/file/grade/parser/CsvGradeParserTest.java
    src/main/java/com/graphnexus/application/file/grade/parser/CsvGradeParser.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/file/grade/parser/CsvGradeParserTest.java
  </write_files>
  <action>
    更新 CsvGradeParserTest：
    1. 所有 CsvParsePayload 引用 → GradeParsePayload
    2. CsvGradeParser.StudentRecord → GradeParsePayload.StudentRecord
    3. CsvGradeParser.ScoreDetail → GradeParsePayload.ScoreDetail
    4. GradeFileType.CSV_GRADE → GradeFileType.CSV
    5. 断言中 FileParseResult.getParseType() == GradeFileType.CSV
    6. 测试逻辑完全不变（AC-1/AC-2/AC-6 用例保留）
    确认所有测试仍通过
  </action>
  <verify>mvn test -Dtest="CsvGradeParserTest" -DfailIfNoTests=false 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>CsvGradeParserTest 全部通过，零 CSV_GRADE/CsvParsePayload 引用</done>
  <depends_on>T08</depends_on>
</task>

<task id="T18" parallel="true" status="pending">
  <name>ExcelGradeParserTest — 新增 Excel 解析单元测试</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/parser/ExcelGradeParser.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeParsePayload.java
    src/main/java/com/graphnexus/application/file/grade/model/GradeFileType.java
    src/test/java/com/graphnexus/application/file/grade/parser/CsvGradeParserTest.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/file/grade/parser/ExcelGradeParserTest.java
  </write_files>
  <action>
    新建 ExcelGradeParserTest，覆盖：
    1. AC-2：正常 .xlsx 文件解析（3 学生 × 3 题目 × 多知识点用 ; 分隔），断言解析结果与同内容 CSV 一致
    2. AC-5：格式校验拒绝 — 缺知识点行、成绩格式异常、列数不一致
    3. AC-11：缺考标记 -/- 处理
    4. .xls 格式支持验证（老格式）
    5. 日期列兼容测试：Excel 日期数值（DateUtil.isCellDateFormatted）和字符串日期
    6. 空行跳过、空白单元格处理
    
    测试数据准备：
    - 在测试中通过 Apache POI API 动态创建 Excel 工作簿（XSSFWorkbook / HSSFWorkbook）
    - 写入 ByteArrayOutputStream → ByteArrayInputStream 作为测试输入
    - 无需外部测试文件
    
    参考 CsvGradeParserTest 的结构和断言模式
  </action>
  <verify>mvn test -Dtest="ExcelGradeParserTest" -DfailIfNoTests=false 2>&1 | grep -E "Tests run|BUILD"</verify>
  <done>ExcelGradeParserTest 全部通过，覆盖 AC-2/AC-5/AC-11 + 边界用例</done>
  <depends_on>T09</depends_on>
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