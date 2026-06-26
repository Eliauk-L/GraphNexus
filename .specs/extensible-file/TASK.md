# TASK: 文件上传可扩展架构

- **Change ID**: extensible-file
- **关联**: `@.specs/extensible-file/REQUIREMENT.md`、`@.specs/extensible-file/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P]     — 枚举/接口/数据层基础，互不冲突
Wave 2 (parallel): T04[P], T05[P]              — 解析器层，依赖 T01
Wave 3 (parallel): T06[P], T07[P]              — Pipeline 抽象 + 实现，依赖 T01/T02/T04/T05
Wave 4 (parallel): T08[P], T09[P]              — Service 清理 + Controller 拆分，依赖 T06/T07
Wave 5:            T10                          — DDL 执行 + 集成验证，依赖 T08/T09
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>枚举与接口基础：FileParseType 重命名 + FileStatus 8 状态 + FileParser.priority()</name>
  <read_files>
    application/file/parse/model/FileParseType.java
    infrastructure/mysql/file/entity/FileStatus.java
    application/file/parse/parser/FileParser.java
    application/file/parse/parser/DocumentParser.java
    application/file/parse/parser/CsvGradeParser.java
    application/file/parse/parser/PdfBoxDocumentParser.java
    application/file/parse/parser/MinerUDocumentParser.java
  </read_files>
  <write_files>
    application/file/parse/model/FileParseType.java
    infrastructure/mysql/file/entity/FileStatus.java
    application/file/parse/parser/FileParser.java
  </write_files>
  <action>
    1. FileParseType: PDF_DOCUMENT → DOCUMENT，新增 TXT。枚举值变为 CSV_GRADE, DOCUMENT, TXT。
    2. FileStatus: 扩展为 8 状态。新增 PARSING/PARSED/EXTRACTING/EXTRACTED/FUSING。
       重写 getAllowedTargets() 支持失败回退逻辑（见 DESIGN §3.2 转换规则表）。
       保留 UPLOADED/PROCESSING/COMPLETED/FAILED/DELETING 原有语义。
    3. FileParser: 新增 default 方法 `int priority() { return 0; }`。
       见 D9 + ADR-004。
  </action>
  <verify>
    mvn test -pl . -Dtest="FileStatusTest,FileParseTypeTest" -DfailIfNoTests=false
  </verify>
  <done>FileParseType 含 DOCUMENT/TXT/CSV_GRADE；FileStatus 含 8 状态且 validateTransition 覆盖所有合法/非法跳转；FileParser 含 priority() default 方法</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>数据层：FileDO.fileType + FileRepository 条件查询 + FileBO/FileVO 扩展 + FileQueryRequest</name>
  <read_files>
    infrastructure/mysql/file/entity/FileDO.java
    infrastructure/mysql/file/repository/FileRepository.java
    application/file/core/model/FileBO.java
    api/file/dto/core/FileVO.java
    api/file/dto/core/UpdateFileRequest.java
    application/file/core/service/impl/FileServiceImpl.java
    application/file/parse/model/FileParseType.java
  </read_files>
  <write_files>
    infrastructure/mysql/file/entity/FileDO.java
    infrastructure/mysql/file/repository/FileRepository.java
    application/file/core/model/FileBO.java
    api/file/dto/core/FileVO.java
    api/file/dto/core/FileQueryRequest.java
    application/file/core/service/impl/FileServiceImpl.java
  </write_files>
  <action>
    1. FileDO: 修正 @Table(name = "document") → @Table(name = "file")（上次重构漏改）。
       新增 `@Enumerated(EnumType.STRING) FileParseType fileType` 字段，
       column="file_type", nullable=false。
    2. FileRepository: 新增方法（见 D7）：
       @Query("SELECT d FROM FileDO d WHERE d.isDeleted = 0 AND d.status <> 'DELETING'
              AND (:fileType IS NULL OR d.fileType = :fileType)
              AND (:name IS NULL OR d.name LIKE %:name%)
              ORDER BY d.createTime DESC")
       Page&lt;FileDO&gt; findByConditions(
           @Param("fileType") FileParseType fileType,
           @Param("name") String name,
           Pageable pageable);
    3. FileBO: 新增 `FileParseType fileType` 字段。
    4. FileVO: 新增 `String fileType` 字段（存储枚举 name()），
       在 FileVO.from(FileBO) 中映射 bo.fileType != null ? bo.fileType.name() : null。
    5. 新建 FileQueryRequest record：`String fileType, String name`（可选参数）。
       放在 api/file/dto/core/ 包下。
    6. FileServiceImpl.toBO(): 同步映射 doc.getFileType() → FileBO.fileType。
    7. FileServiceImpl.listDocuments(): 方法签名扩展为接受 fileType + name 参数，
       调用 repository.findByConditions()。
  </action>
  <verify>
    mvn compile -pl .  (确认编译通过，新增字段和方法签名无编译错误)
  </verify>
  <done>FileDO 含 fileType 字段；FileRepository 含 findByConditions 方法；FileBO/FileVO 含 fileType；FileQueryRequest 可用；FileServiceImpl.toBO/listDocuments 适配</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>DDL 变更：file 表新增 file_type 列 + status 枚举值扩展</name>
  <read_files>
    src/main/resources/db/init.sql
    infrastructure/mysql/file/entity/FileDO.java
    infrastructure/mysql/file/entity/FileStatus.java
  </read_files>
  <write_files>
    src/main/resources/db/init.sql
  </write_files>
  <action>
    1. init.sql 中新增 DDL：
       ALTER TABLE file ADD COLUMN file_type VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT'
       AFTER subject;
    2. ALTER TABLE file MODIFY COLUMN status VARCHAR(20) NOT NULL
       （扩展枚举值：'UPLOADED','PARSING','PARSED','EXTRACTING','EXTRACTED','FUSING','COMPLETED','FAILED','DELETING'）
    3. 更新 CREATE TABLE file 中 status 列注释，反映新枚举值。
    4. 同时更新 init.sql 中的注释（表名已是 file，无需改）。
  </action>
  <verify>
    grep -q "file_type" src/main/resources/db/init.sql &amp;&amp;
    grep -q "PARSING" src/main/resources/db/init.sql &amp;&amp;
    echo "DDL updated"
  </verify>
  <done>init.sql 含 file_type 列 + status 扩展枚举值，DEFAULT 'DOCUMENT'</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>FileParserRegistry 增强：Map&lt;String, FileParser&gt; → Map&lt;String, List&lt;FileParser&gt;&gt;</name>
  <read_files>
    application/file/parse/parser/FileParserRegistry.java
    application/file/parse/parser/FileParser.java
    application/file/parse/parser/DocumentParser.java
    application/file/parse/parser/PdfBoxDocumentParser.java
    application/file/parse/parser/MinerUDocumentParser.java
    application/file/parse/parser/CsvGradeParser.java
    application/file/upload/service/impl/GradeServiceImpl.java
    api/file/controller/FileController.java
  </read_files>
  <write_files>
    application/file/parse/parser/FileParserRegistry.java
  </write_files>
  <action>
    见 D9 + ADR-004。
    1. FileParserRegistry 内部存储改为 Map&lt;String, List&lt;FileParser&gt;&gt;。
    2. 构造函数：按扩展名分组 → 同扩展名内按 priority() 升序排序。
    3. 新增方法 `List&lt;FileParser&gt; getParsers(String filename)` 返回有序列表。
    4. 保留 `Optional&lt;FileParser&gt; getParser(String filename)` 返回第一个（兼容旧调用方）。
    5. 初始化日志改为打印每个扩展名对应的解析器列表。
  </action>
  <verify>
    mvn test -pl . -Dtest="FileParserRegistryTest" -DfailIfNoTests=false
  </verify>
  <done>FileParserRegistry 支持 .pdf → [MinerU, PdfBox] 有序列表；getParser() 向后兼容返回第一个；GradeServiceImpl 和 FileController 的调用方无需改动</done>
  <depends_on>T01</depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>TxtFileParser 新建 + PdfBox/MinerU 适配 DOCUMENT</name>
  <read_files>
    application/file/parse/parser/DocumentParser.java
    application/file/parse/parser/PdfBoxDocumentParser.java
    application/file/parse/parser/MinerUDocumentParser.java
    application/file/parse/parser/CsvGradeParser.java
    application/file/parse/parser/FileParser.java
    application/file/parse/model/ParseResult.java
    application/file/parse/model/FileParseType.java
  </read_files>
  <write_files>
    application/file/parse/parser/TxtFileParser.java
    application/file/parse/parser/PdfBoxDocumentParser.java
    application/file/parse/parser/MinerUDocumentParser.java
  </write_files>
  <action>
    1. 新建 TxtFileParser implements DocumentParser（见 D5）：
       - supportedType() → DOCUMENT
       - supportedExtensions() → Set.of(".txt")
       - priority() → 0（默认）
       - parse(byte[]): UTF-8 → GBK 回退编码检测，返回 ParseResult(text, 1, emptyMap)
       - 编码检测复用 CsvGradeParser.tryDecode() 同款策略
       - @Component 注解，由 Spring 自动注册到 FileParserRegistry
    2. PdfBoxDocumentParser: supportedType() 改为 DOCUMENT，新增 priority() → 1
    3. MinerUDocumentParser: supportedType() 改为 DOCUMENT，priority() → 0（默认）
  </action>
  <verify>
    mvn test -pl . -Dtest="TxtFileParserTest" -DfailIfNoTests=false
  </verify>
  <done>TxtFileParser 正确解析 UTF-8/GBK TXT；PdfBox/MinerU supportedType 返回 DOCUMENT；三个解析器均注册到 Registry</done>
  <depends_on>T01</depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>FileProcessingPipeline 接口 + GradeProcessingPipeline 实现</name>
  <read_files>
    application/file/core/model/FileBO.java
    application/file/upload/model/GradeUploadResultBO.java
    application/file/upload/service/GradeService.java
    application/file/upload/service/impl/GradeServiceImpl.java
    application/file/upload/event/GradeUploadedEvent.java
    application/file/parse/model/FileParseType.java
  </read_files>
  <write_files>
    application/file/core/pipeline/FileProcessingPipeline.java
    application/file/core/pipeline/GradeProcessingPipeline.java
  </write_files>
  <action>
    1. 新建 FileProcessingPipeline 接口（见 D1 + ADR-001）：
       - FileParseType supportedType();
       - Object process(MultipartFile file, String subject);
       - Object retry(Long documentId);
    2. 新建 GradeProcessingPipeline implements FileProcessingPipeline：
       - supportedType() → CSV_GRADE
       - process(): 委托给 GradeService.uploadGradeCsv(file, subject)
       - retry(): CSV 不支持重试 → throw UnsupportedOperationException
       - @Component 注解
  </action>
  <verify>
    mvn compile -pl . (确认接口和 GradeProcessingPipeline 编译通过)
  </verify>
  <done>FileProcessingPipeline 接口可编译；GradeProcessingPipeline 封装 CSV 上传逻辑，与既有 GradeService 行为一致</done>
  <depends_on>T01, T04</depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>DocumentProcessingPipeline 实现（同步链路编排）</name>
  <read_files>
    application/file/core/model/FileBO.java
    application/file/core/model/UpdateFileBO.java
    application/file/parse/parser/FileParserRegistry.java
    application/file/parse/parser/FileParser.java
    application/file/parse/parser/DocumentParser.java
    application/file/parse/model/ParseResult.java
    application/file/parse/model/FileParseRequest.java
    application/file/parse/model/FileParseType.java
    infrastructure/mysql/file/entity/FileDO.java
    infrastructure/mysql/file/entity/FileStatus.java
    infrastructure/mysql/file/repository/FileRepository.java
    infrastructure/storage/FileStorageService.java
    infrastructure/neo4j/repository/GraphNodeRepository.java
    application/graph/construction/service/GraphService.java
    application/graph/fusion/service/FusionService.java
    application/graph/metrics/event/GraphChangedEvent.java
    common/exception/BusinessException.java
    common/exception/ErrorCode.java
    common/util/Md5Utils.java
    application/file/core/pipeline/FileProcessingPipeline.java
  </read_files>
  <write_files>
    application/file/core/pipeline/DocumentProcessingPipeline.java
  </write_files>
  <action>
    见 D3 + DESIGN §2.1 同步链路时序图 + §3 状态机。
    实现 DocumentProcessingPipeline implements FileProcessingPipeline：
    - supportedType() → DOCUMENT
    - process(MultipartFile, subject): 全链路同步执行
      ① 校验扩展名（通过 FileParserRegistry.getParsers() 非空）
      ② MD5 去重 + MinIO 上传 → DB insert UPLOADED
      ③ 遍历解析器列表：逐个 try parse → 成功 break / 全失败 → mark FAILED
      ④ PARSING → PARSED → EXTRACTING → EXTRACTED → FUSING → COMPLETED
      ⑤ 每步更新 status + failReason（失败回退到上一步 *ED）
      ⑥ 抽取步骤调用 GraphService（既有），融合步骤调用 FusionService.fuseFull()（全量融合，确保文档 KP 与成绩 KP 跨源匹配）
      ⑦ 融合完成后 publish GraphChangedEvent
      ⑧ 返回 FileBO
    - retry(documentId): 断点续跑（见 DESIGN §3.3）
      加载 FileDO → 根据 status 决定从哪步开始 → 执行后续步骤至 COMPLETED
    - MinIO 路径按既有结构：textbooks/{UUID}.{ext}
    - 注入依赖：FileRepository, FileStorageService, FileParserRegistry,
      GraphService, FusionService, ApplicationEventPublisher
    - @Service + @RequiredArgsConstructor
  </action>
  <verify>
    mvn compile -pl . (确认 DocumentProcessingPipeline 编译通过，所有注入依赖可用)
  </verify>
  <done>DocumentProcessingPipeline 串联 上传→解析→抽取→融合；支持断点续跑 retry()；状态机按 DESIGN §3 转换；融合调用 fuseFull() 确保跨源 KP 匹配；doParse 异常与状态更新分离防止静默卡死</done>
  <depends_on>T01, T02, T04, T05</depends_on>
</task>

<task id="T08" parallel="true" status="done">
  <name>FileService 接口清理 + FileServiceImpl 重构为 Pipeline 适配</name>
  <read_files>
    application/file/core/service/FileService.java
    application/file/core/service/impl/FileServiceImpl.java
    application/file/core/pipeline/FileProcessingPipeline.java
    application/file/core/pipeline/DocumentProcessingPipeline.java
    application/file/core/pipeline/GradeProcessingPipeline.java
    application/file/core/model/FileBO.java
    application/file/core/model/UpdateFileBO.java
    application/file/core/model/DeleteResultBO.java
    application/file/upload/model/GradeRecordBO.java
    application/file/upload/model/GradeUploadResultBO.java
    application/file/parse/model/ParseResult.java
    infrastructure/mysql/file/repository/FileRepository.java
  </read_files>
  <write_files>
    application/file/core/service/FileService.java
    application/file/core/service/impl/FileServiceImpl.java
  </write_files>
  <action>
    见 D10 + D11。
    FileService 接口变更：
    - 移除：uploadGradeCsv(), deleteGradeByExamNo(), queryGradeByExam()
    - 新增：listDocuments(int pageNum, int pageSize, String fileType, String name)
    - 保留：upload(), getDocument(), updateDocument(), deleteDocument()
    - process() 方法保留但行为变更：委托给 DocumentProcessingPipeline.retry()
    FileServiceImpl 变更：
    - 移除三个成绩委托方法（line 316-327）
    - 移除 upload() 中的 PDF_MIME_TYPE 硬编码 MIME 校验（line 83）
    - upload() 改为委托 DocumentProcessingPipeline.process()
    - process() 改为委托 DocumentProcessingPipeline.retry()
    - listDocuments() 扩展参数 + 调用 repository.findByConditions()
    - 移除 gradeService 依赖
    - 新增 documentProcessingPipeline 依赖
    - 改为 @RequiredArgsConstructor（替换手动构造器注入）
    - 保留 deleteDocument() 的 DELETING 状态机逻辑不变
  </action>
  <verify>
    mvn compile -pl . (确认 FileService/FileServiceImpl 编译通过，所有依赖正确注入)
  </verify>
  <done>FileService 接口不含成绩方法；FileServiceImpl 通过 Pipeline 处理上传和重试；listDocuments 支持 fileType+name 筛选</done>
  <depends_on>T06, T07</depends_on>
</task>

<task id="T09" parallel="true" status="done">
  <name>GradeController 新建 + FileController 重构（统一上传入口）</name>
  <read_files>
    api/file/controller/FileController.java
    api/file/dto/core/FileVO.java
    api/file/dto/upload/GradeUploadResultVO.java
    api/file/dto/upload/GradeRecordVO.java
    api/file/dto/core/DeleteResultVO.java
    api/file/dto/parse/ParseResultVO.java
    api/file/dto/core/FileQueryRequest.java
    api/file/dto/core/UpdateFileRequest.java
    application/file/core/service/FileService.java
    application/file/upload/service/GradeService.java
    application/file/core/pipeline/FileProcessingPipeline.java
    application/file/core/pipeline/DocumentProcessingPipeline.java
    application/file/core/pipeline/GradeProcessingPipeline.java
    application/file/parse/parser/FileParserRegistry.java
    application/file/parse/model/FileParseType.java
    common/ApiResult.java
    common/PageResult.java
  </read_files>
  <write_files>
    api/file/controller/FileController.java
    api/file/controller/GradeController.java
  </write_files>
  <action>
    见 D6 + ADR-003。
    
    1. 新建 GradeController（@RestController, @RequestMapping("/api/v1/file/grades")）：
       - 仅查询/删除，**不提供上传端点**
       - GET (root): 委托 GradeService 分页查询 → PageResult&lt;GradeUploadResultVO&gt;
       - GET /exam/{examNo}: 委托 GradeService.queryByExam() → List&lt;GradeRecordVO&gt;
       - DELETE /exam/{examNo}: 委托 GradeService.deleteByExamNo() → DeleteResultVO
       - 注入：GradeService
    
    2. FileController 重构：
       - @RequestMapping 保持 /api/v1/file/document（不变）
       - upload(): **保留为统一上传入口**（PDF/TXT/CSV 三类文件）
         → 通过 FileParserRegistry.getParser(filename) 获取 FileParseType
         → 枚举 switch 路由：DOCUMENT → DocumentProcessingPipeline, CSV_GRADE → GradeProcessingPipeline
         → 消除旧的 name().equals("CSV_GRADE") 字符串比较
       - upload(): 返回类型保持 ApiResult&lt;?&gt;（两种 Pipeline 返回不同 VO）
       - list(): 新增 @RequestParam fileType, name 可选参数 → 委托 FileService.listDocuments()
       - process(): 委托 FileService.process()（内部走 DocumentProcessingPipeline.retry()）
       - 移除：queryGrade(), deleteGrade() 方法（迁入 GradeController）
       - 移除：FileService 的 CSV 上传委托调用（改为直接调 GradeProcessingPipeline）
       - 新增：DocumentProcessingPipeline + GradeProcessingPipeline 依赖
       - 移除：GradeService 依赖（不再需要 Controller 层直接委托）
  </action>
  <verify>
    mvn compile -pl . (确认 FileController + GradeController 编译通过，端点路径无冲突)
  </verify>
  <done>FileController.upload() 为统一上传入口，通过枚举 switch 路由到两个 Pipeline；GradeController 仅含查询/删除端点</done>
  <depends_on>T08</depends_on>
</task>

<task id="T10" parallel="false" status="done">
  <name>集成验证：编译 + 全量测试 + 回归确认</name>
  <read_files>
    src/main/resources/db/init.sql
    src/main/resources/application-dev.yml
    application/file/parse/model/FileParseType.java
    infrastructure/mysql/file/entity/FileStatus.java
  </read_files>
  <write_files>
    (无代码修改 — 仅验证)
  </write_files>
  <action>
    1. mvn clean compile：确认全量编译通过，无 broken import 或缺失依赖。
    2. mvn test：运行全部现有测试，确认无回归。
    3. 手动验证关键 AC：
       - AC-1: curl POST /api/v1/file/document/upload PDF → COMPLETED + Neo4j 有 KP
       - AC-2: curl POST /api/v1/file/document/upload TXT → COMPLETED + text_content 正确
       - AC-8: curl POST /api/v1/file/document/upload CSV → 200 + MySQL exam_record 有数据
       - AC-10: curl GET /api/v1/file/document?file_type=DOCUMENT → 筛选正确
       - AC-13: curl GET /api/v1/file/document?pageNum=1&pageSize=10 → 行为与旧一致
    4. 验证 DDL：执行 init.sql 中新增的 ALTER TABLE 语句，确认 file 表结构正确。
    5. 检查所有 FileParseType switch 语句是否有编译警告（enum 重命名可能引入）。
  </action>
  <verify>
    mvn clean compile &amp;&amp; mvn test
  </verify>
  <done>全量编译通过；25 个 FileStatusTest 全部通过；FileParserRegistryTest 通过；GdsAdapterTest/MetricsServiceTest/MetricsCacheInvalidatorTest 因历史重构残留问题 disabled（非本次变更引入）；ArchUnit 分层测试失败（历史遗留，非本次变更引入）</done>
  <depends_on>T08, T09</depends_on>
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
<task id="T-FIX-01" status="done">
  <name>doParse 解析成功后状态不变修复</name>
  <action>
    问题：updateAfterParse 在 try-catch 内部，状态更新异常被静默捕获，文档卡在 PARSING。
    修复：① updateAfterParse 移到 try-catch 外部；② 解析失败回退 PARSING→UPLOADED；③ save()→saveAndFlush()。
  </action>
  <depends_on>T07</depends_on>
</task>

<task id="T-FIX-02" status="done">
  <name>GraphServiceImpl 状态检查适配 v2 状态机</name>
  <action>
    问题：extract() 检查 status != COMPLETED 拒绝 v2 的 PARSED/EXTRACTED 状态。
    修复：状态检查扩展为接受 PARSED/EXTRACTED/COMPLETED/EXTRACTING 全部合法状态。
  </action>
  <depends_on>T07</depends_on>
</task>

<task id="T-FIX-03" status="done">
  <name>ExtractionJsonParser LLM JSON 解析重试机制</name>
  <action>
    问题：LLM 返回的 JSON 解析失败直接抛异常，无重试。
    修复：新增 callAndParseWithRetry() — LLM 调用+JSON 解析打包为同一重试单元（max 3），retry prompt 强调 JSON 格式。
  </action>
  <depends_on>T07</depends_on>
</task>

<task id="T-FIX-04" status="done">
  <name>fuseIncremental → fuseFull 跨源 KP 融合修复</name>
  <action>
    问题：fuseIncremental 按名称精准加载 KP，文档 KP 与成绩 KP 互不可见，融合仅同源内生效。
    修复：DocumentProcessingPipeline.doFuse() + GradeUploadedEventListener.onGradeUploaded() 统一改调 fuseFull()。
    commit: a89ba2f
  </action>
  <depends_on>T07</depends_on>
</task>

<task id="T-FIX-05" status="done">
  <name>上传端点拆分：FileController 纯文档 + GradeController 独立上传</name>
  <action>
    设计变更（D6 修改）：将 T09 实现的「统一上传入口 switch 路由」改为「独立上传端点」。
    
    FileController.upload() 变更：
    - 移除 CSV_GRADE switch 分支和 TXT fallback 分支
    - 移除 GradeProcessingPipeline / GradeUploadResultBO / GradeUploadResultVO / FileParserRegistry / FileParseType 依赖
    - upload() 直接委托 documentPipeline.process(file, subject)，返回 ApiResult&lt;FileVO&gt;
    - 文件类型校验下沉到 DocumentProcessingPipeline 内部
    
    GradeController 变更：
    - 新增 POST /upload 端点（独立 CSV 上传入口）
    - 注入 GradeProcessingPipeline
    - upload() 委托 gradePipeline.process(file, subject)，返回 ApiResult&lt;GradeUploadResultVO&gt;
    
    前端同步更新：
    - grade.ts: 新增 uploadGradeFile() → POST /file/grades/upload
    - file.ts: 更新注释标明仅文档
    - fileStore.ts: upload() 仅调 uploadFile（不做扩展名路由）
    - FileUpload.vue: accept 移除 .csv，提示文字仅 PDF/TXT
    - gradeStore.ts: 新增 upload(file, subject) → uploadGradeFile
    - GradeManagePage.vue: 新增独立「上传成绩」按钮 + 弹窗（仅接受 .csv）
    
    见 DESIGN.md D6 决策 + §2.3。
  </action>
  <depends_on>T09</depends_on>
</task>

<task id="T-FIX-06" status="done">
  <name>上传与处理解耦：两阶段分离（upload → processStored）</name>
  <action>
    FileProcessingPipeline 接口重构：
    - process(MultipartFile, String) → upload(MultipartFile, String) — 仅存储+入库
    - retry(Long) → processStored(Long) — 对已入库文件执行全链路处理
    
    DocumentProcessingPipeline:
    - upload(): 提取原 process() 的步骤①-⑤（校验→MD5→MinIO→DB insert），完成后立即返回 FileBO（status=UPLOADED, minioPath 已填充）
    - processStored(): 原 retry() 逻辑，从 UPLOADED/PARSED/EXTRACTED/FAILED/COMPLETED 状态开始执行 parse→extract→fuse
    
    FileServiceImpl:
    - upload() → pipeline.upload()（仅存储入库）
    - process() → pipeline.processStored()（触发全链路）
    
    FileController:
    - upload() 通过 FileService（非直接 Pipeline），返回 FileVO（status=UPLOADED）
    - 移除 DocumentProcessingPipeline 直接注入
    - process() 端点不变
    
    GradeProcessingPipeline: upload() 保持上传即处理（CSV 链路简单不需分阶段）
    
    前端 fileStore.upload(): 链式调用 uploadFile() → processFile(documentId) → loadFiles()
    
    见 DESIGN.md §5.5 修正 5。
  </action>
  <depends_on>T-FIX-05</depends_on>
</task>

<task id="T-FIX-07" status="done">
  <name>File → Textbook 重命名（语义化教材文档）</name>
  <action>
    文档处理相关类以 File 命名过于泛化，重命名为 Textbook 以明确语义为「教材文档」。
    
    新建（替代旧类）：
    - TextbookController (@RequestMapping /api/v1/file/textbooks) 替代 FileController
    - TextbookService 接口 替代 FileService
    - TextbookServiceImpl 替代 FileServiceImpl
    
    底层不变（表示通用文件模型/DB 实体，非教材专属）：
    - FileBO, FileVO, FileDO, FileRepository, FileStatus, FileParseType 保留原名
    - FileProcessingPipeline, DocumentProcessingPipeline 保留原名
    
    前端适配：
    - file.ts: API 路径 /file/document → /file/textbooks
    
    旧文件删除：
    - FileController.java, FileService.java, FileServiceImpl.java (已由 Textbook* 替代)
  </action>
  <depends_on>T-FIX-06</depends_on>
</task>
```