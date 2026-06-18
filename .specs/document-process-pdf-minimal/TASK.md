# TASK: 文档处理模块 — PDF 上传/解析/管理最小化实现

- **Change ID**: `document-process-pdf-minimal`
- **关联**: `@.specs/document-process-pdf-minimal/REQUIREMENT.md`、`@.specs/document-process-pdf-minimal/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P]                              ← 基础配置，互不冲突
Wave 2 (parallel): T04[P], T05[P], T06[P]                              ← L3 基础设施 + 解析接口
Wave 3 (parallel): T07[P], T08[P]                                       ← 解析实现 + 业务服务（同包但文件不同，可并行）
Wave 4 (parallel): T09[P], T10[P]                                       ← L1 API 层 + 测试
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>ErrorCode 新增文档相关枚举值</name>
  <read_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    .specs/CONTEXT.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </write_files>
  <action>
    在既有 ErrorCode 枚举中新增 4 个文档相关错误码（见 DESIGN § D9）：
    - A0004: 文件类型不支持 → 400 BAD_REQUEST, tip="仅支持 PDF 格式文件"
    - A0005: 文件大小超限 → 413 PAYLOAD_TOO_LARGE, tip="文件大小不能超过 50MB"
    - A0006: 文档不存在 → 404 NOT_FOUND, tip="文档记录不存在或已被删除"
    - A0007: 文档内容重复 → 409 CONFLICT, tip="该学科下已存在相同内容的文档"

    只新增枚举值，不修改既有枚举常量和其他类。
  </action>
  <verify>mvn compile -q 2>&1 | grep -E 'BUILD|ERROR'</verify>
  <done>ErrorCode 枚举新增 4 个值，编译通过；A0004→400, A0005→413, A0006→404, A0007→409</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>DDL init SQL + yml 启动配置调整</name>
  <read_files>
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
    src/main/java/com/graphnexus/GraphNexusApplication.java
    .specs/document-process-pdf-minimal/DESIGN.md
  </read_files>
  <write_files>
    src/main/resources/db/init-document.sql
    src/main/resources/application.yml
    src/main/resources/application-dev.yml
  </write_files>
  <action>
    1. 创建 DDL init SQL 脚本到 src/main/resources/db/init-document.sql：
       使用用户提供的 DDL 作为基础，并补充以下字段（AC-2 需要持久化解析结果）：
       - text_content MEDIUMTEXT（PDFBox 提取的文本内容，最大 16MB）
       - metadata_json VARCHAR(2000)（PDF 元信息 JSON，标题/作者/创建日期等）

       完整 DDL 字段清单：
       id, document_no, name, subject, file_size, minio_path,
       text_content, page_count, metadata_json, status,
       fail_reason, uploaded_by, is_deleted, create_time, update_time
       含索引：uk_document_subject(document_no, subject), idx_minio_path, idx_status

    2. 修改 application.yml spring.autoconfigure.exclude：
       移除以下 4 项 exclude（使 MySQL/JPA/事务管理激活）：
       - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
       - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
       - org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
       - org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration

       注意：保留 Neo4j/Redis/RabbitMQ/Security 相关的 exclude（本次不变）。

    3. 修改 application-dev.yml：
       - 新增 spring.jpa.hibernate.ddl-auto: validate（配合手动 DDL）
       - 新增 spring.servlet.multipart.max-file-size: 50MB
       - 新增 spring.servlet.multipart.max-request-size: 55MB
       - 确认 minio.bucket 值为 graphnexus-dev

    不修改 GraphNexusApplication.java（当前 exclude 仅在 yml 中声明，代码中无 JPA/MySQL exclude）。
  </action>
  <verify>
    # 验证 1：确认 DDL 文件存在且包含所有字段
    grep -c 'document_no' src/main/resources/db/init-document.sql | grep -q '1'
    grep -c 'uk_document_subject' src/main/resources/db/init-document.sql | grep -q '1'

    # 验证 2：确认 yml 不再排除 MySQL/JPA
    ! grep -q 'DataSourceAutoConfiguration' src/main/resources/application.yml
    ! grep -q 'HibernateJpaAutoConfiguration' src/main/resources/application.yml

    # 验证 3：确认 multipart 配置存在
    grep -q '50MB' src/main/resources/application-dev.yml
  </verify>
  <done>DDL SQL 含全部字段和索引；yml 中 MySQL/JPA AutoConfig 已激活；multipart 50MB 已配置</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>MinioConfig + MinioProperties + FileStorageService</name>
  <read_files>
    src/main/resources/application-dev.yml
    .specs/document-process-pdf-minimal/DESIGN.md
    src/main/java/com/graphnexus/infrastructure/storage/package-info.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/storage/MinioProperties.java
    src/main/java/com/graphnexus/infrastructure/storage/MinioConfig.java
    src/main/java/com/graphnexus/infrastructure/storage/FileStorageService.java
  </write_files>
  <action>
    1. MinioProperties（@ConfigurationProperties(prefix = "minio")）：
       - 字段：endpoint(String), accessKey(String), secretKey(String), bucket(String)
       - 构造器注入风格（@RequiredArgsConstructor + private final，但 @ConfigurationProperties 用 setter 注入是 Spring Boot 约定，本处用 @Data 简化）
       - 见 DESIGN § D2：MinIO 适配层放 L3

    2. MinioConfig（@Configuration + @EnableConfigurationProperties(MinioProperties.class)）：
       - 创建 MinioClient Bean：MinioClient.builder().endpoint(...).credentials(...).build()
       - 启动时检查 bucket 是否存在，不存在则自动创建（makeBucket）
       - 使用 @Slf4j 打日志

    3. FileStorageService（@Service，构造器注入 MinioClient + MinioProperties）：
       - uploadFile(InputStream is, String objectKey, String contentType): void
         → minioClient.putObject(PutObjectArgs.builder()...build())
       - getFile(String objectKey): InputStream
         → minioClient.getObject(GetObjectArgs.builder()...build())
       - deleteFile(String objectKey): void
         → minioClient.removeObject(RemoveObjectArgs.builder()...build())
       - 所有方法异常时抛出 BusinessException（复用已有异常体系）
       - 见 DESIGN § D2

    所有类遵循项目规范：构造器注入 + @RequiredArgsConstructor + Javadoc 中文注释 + 作者 Jay。
  </action>
  <verify>mvn compile -q 2>&1 | grep -E 'BUILD|ERROR'</verify>
  <done>MinioClient Bean 可注入；FileStorageService 三个方法签名可用；编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>DocumentDO + DocumentStatus + DocumentRepository</name>
  <read_files>
    src/main/resources/db/init-document.sql
    .specs/document-process-pdf-minimal/DESIGN.md
    .specs/CONTEXT.md
    src/main/java/com/graphnexus/infrastructure/mysql/package-info.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentStatus.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentRepository.java
  </write_files>
  <action>
    1. DocumentDO（JPA Entity，@Table(name = "document")）：
       字段对齐 init-document.sql：
       - id(Long, @Id @GeneratedValue IDENTITY)
       - documentNo(String, @Column(length=32, nullable=false))
       - name(String)
       - subject(String, @Column(length=20, nullable=false))
       - fileSize(Long)
       - minioPath(String, @Column(length=500, nullable=false))
       - pageCount(Integer)
       - status(@Enumerated(STRING), String 存储，默认 UPLOADED)
       - textContent(@Lob @Column(columnDefinition="MEDIUMTEXT"))
       - metadataJson(String, @Column(length=2000))
       - failReason(String, @Column(length=512))
       - uploadedBy(Long)
       - isDeleted(Boolean/Integer, 默认 0)
       - createTime(LocalDateTime, @CreatedDate, 默认 CURRENT_TIMESTAMP)
       - updateTime(LocalDateTime, @LastModifiedDate)
       加 @EntityListeners(AuditingEntityListener.class) 支持自动时间戳。
       注意：textContent 存储解析后的全文（DESIGN §1 中指出 DDL 未含但 AC 需要，用 @Lob MEDIUMTEXT 补充）。

    2. DocumentStatus 枚举（放在同包下）：
       - UPLOADED, PROCESSING, COMPLETED, FAILED（v1 使用）
       - 内含 allowedTargets Set，如：
         UPLOADED → {PROCESSING}
         PROCESSING → {COMPLETED, FAILED}
         COMPLETED → {PROCESSING}
         FAILED → {PROCESSING}
       - validateTransition(DocumentStatus target) 方法：不在 allowedTargets 中则抛 BusinessException(A0006)
       - 见 DESIGN § 3.1 状态机

    3. DocumentRepository（extends JpaRepository<DocumentDO, Long>）：
       - Page<DocumentDO> findByIsDeletedFalse(Pageable pageable)
       - Optional<DocumentDO> findByIdAndIsDeletedFalse(Long id)
       - boolean existsByDocumentNoAndSubjectAndIsDeletedFalse(String documentNo, String subject)
       - （去重查询，用于上传时检测重复文档）

    遵循项目规范：Javadoc 中文注释 + 作者 Jay + 构造器注入用不到（Entity 用 @Entity + JPA 注解）。
  </action>
  <verify>mvn compile -q 2>&1 | grep -E 'BUILD|ERROR'</verify>
  <done>DocumentDO 字段与 DDL 对齐；DocumentStatus 含 4 个状态+转换规则；Repository 含 3 个查询方法；编译通过</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>DocumentParser 接口 + ParseResult 模型</name>
  <read_files>
    .specs/document-process-pdf-minimal/DESIGN.md
    .specs/adr/001-document-parser-strategy.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/document/service/DocumentParser.java
    src/main/java/com/graphnexus/application/document/service/ParseResult.java
  </write_files>
  <action>
    1. DocumentParser 接口（见 ADR-001 + DESIGN § D1）：
       - 方法签名：ParseResult parse(byte[] pdfBytes)
       - 接口放 L2 application/document/service/（业务决策层）
       - 不依赖任何具体实现库（PDFBox/MinerU）

    2. ParseResult 模型（POJO/record，放同包下）：
       - textContent(String) — 提取的全文文本
       - pageCount(int) — 总页数
       - metadata(Map<String, String>) — PDF 元信息（标题/作者/创建日期等），可为空 Map
       - 使用 Java 17 record 实现（不可变），或普通 class + @Getter（根据团队偏好）
       - 建议用 record：`public record ParseResult(String textContent, int pageCount, Map<String, String> metadata)`

    遵循项目规范：Javadoc 中文注释 + 作者 Jay
  </action>
  <verify>mvn compile -q 2>&1 | grep -E 'BUILD|ERROR'</verify>
  <done>DocumentParser 接口可编译；ParseResult record 三字段齐全；不依赖 PDFBox 或 MinerU import</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>PdfBoxDocumentParser 实现</name>
  <read_files>
    src/main/java/com/graphnexus/application/document/service/DocumentParser.java
    src/main/java/com/graphnexus/application/document/service/ParseResult.java
    .specs/document-process-pdf-minimal/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/document/service/PdfBoxDocumentParser.java
  </write_files>
  <action>
    实现 PdfBoxDocumentParser（@Service，implements DocumentParser）：
    - 使用 org.apache.pdfbox.Loader.loadPDF(byte[]) 加载 PDF
    - 提取 textContent：使用 PDFTextStripper 提取全文文本
    - 提取 pageCount：document.getNumberOfPages()
    - 提取 metadata：document.getDocumentInformation() 获取标题/作者/主题/创建日期
      → 转为 Map<String, String>（null-safe，key 不存在时值为空字符串）
    - 异常处理：PDFBox 解析异常时抛出 BusinessException(A0004, "PDF 文件解析失败", "该 PDF 文件可能已损坏或格式不兼容")
    - 资源释放：finally 中 close() PDDocument
    - 见 DESIGN § D3、ADR-001

    依赖项：pdfbox 3.x 已在 pom.xml 预配置，无需新增依赖。
  </action>
  <verify>mvn compile -q 2>&1 | grep -E 'BUILD|ERROR'</verify>
  <done>PdfBoxDocumentParser 实现 DocumentParser 接口；使用 PDFBox API 提取文本/页数/元信息；编译通过</done>
  <depends_on>T05</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>DocumentService 接口 + DocumentServiceImpl + BO 模型</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentStatus.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentRepository.java
    src/main/java/com/graphnexus/infrastructure/storage/FileStorageService.java
    src/main/java/com/graphnexus/application/document/service/DocumentParser.java
    src/main/java/com/graphnexus/application/document/service/ParseResult.java
    src/main/java/com/graphnexus/common/exception/BusinessException.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    .specs/document-process-pdf-minimal/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/document/service/DocumentBO.java
    src/main/java/com/graphnexus/application/document/service/UpdateDocumentBO.java
    src/main/java/com/graphnexus/application/document/service/DocumentService.java
    src/main/java/com/graphnexus/application/document/service/DocumentServiceImpl.java
  </write_files>
  <action>
    1. DocumentBO（POJO，构造器注入风格用 @Builder 或 @AllArgsConstructor + @Getter）：
       - id(Long), documentNo(String), name(String), subject(String)
       - fileSize(Long), minioPath(String), pageCount(Integer)
       - status(DocumentStatus), textContent(String), metadataJson(String)
       - failReason(String), uploadedBy(Long)
       - createTime(LocalDateTime), updateTime(LocalDateTime)

    2. UpdateDocumentBO：
       - name(String) — 仅允许更名（v1 最小化）

    3. DocumentService 接口（见 DESIGN § 2.1）：
       - DocumentBO upload(MultipartFile file, String subject)  — 上传 PDF
       - ParseResult process(Long documentId)                    — 触发解析
       - Page<DocumentBO> listDocuments(int pageNum, int pageSize) — 分页查询
       - DocumentBO getDocument(Long id)                         — 单条查询
       - DocumentBO updateDocument(Long id, UpdateDocumentBO bo) — 更名
       - void deleteDocument(Long id)                            — 逻辑删除+MinIO清除

    4. DocumentServiceImpl（@Service, @Transactional, 构造器注入所有依赖）：
       upload():
         ① 校验 MIME type = application/pdf（否则抛 A0004）
         ② 校验文件大小 ≤ 50MB（否则抛 A0005）
         ③ 计算 MD5 content hash → documentNo
         ④ 查 Repository.existsByDocumentNoAndSubject(...) 去重（命中则抛 A0007）
         ⑤ 生成 minioPath（UUID + ".pdf"）
         ⑥ FileStorageService.uploadFile(inputStream, minioPath, "application/pdf")
         ⑦ 构造 DocumentDO → save(do) → 返回 DocumentBO

       process():
         ① findByIdAndIsDeletedFalse(id) → 不存在抛 A0006
         ② status.validateTransition(PROCESSING) → 更新 status=PROCESSING
         ③ FileStorageService.getFile(minioPath) → 读入 byte[]
         ④ documentParser.parse(pdfBytes) → ParseResult
         ⑤ 更新 DO: textContent, pageCount, metadataJson, status=COMPLETED
         ⑥ 异常时: status=FAILED, failReason=e.getMessage()，重新抛 BusinessException

       listDocuments():
         Repository.findByIsDeletedFalse(PageRequest.of(pageNum-1, pageSize))
         → PageResult.of(page) 返回

       getDocument():
         findByIdAndIsDeletedFalse(id) → orElseThrow(A0006)

       updateDocument():
         findById → setName(bo.getName()) → save → 返回 BO

       deleteDocument():
         findById → setIsDeleted(true) + FileStorageService.deleteFile(minioPath)

       事务管理：upload/update/delete 用 @Transactional；query 用 @Transactional(readOnly=true)
       异常处理：遵循 CONTEXT「分层异常传递」→ L2 打日志 + 抛 BusinessException
       见 DESIGN § 2.2 完整数据流
  </action>
  <verify>mvn compile -q 2>&1 | grep -E 'BUILD|ERROR'</verify>
  <done>
    DocumentService 接口含 6 个方法；DocumentServiceImpl 实现全部业务逻辑：
    上传→MD5去重→MinIO→DB / 解析→状态流转→PDFBox / CRUD + 逻辑删除；
    DocumentBO + UpdateDocumentBO 编译通过
  </done>
  <depends_on>T04, T05, T06</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>DocumentController L1 API + VO/DTO</name>
  <read_files>
    src/main/java/com/graphnexus/application/document/service/DocumentService.java
    src/main/java/com/graphnexus/application/document/service/DocumentBO.java
    src/main/java/com/graphnexus/application/document/service/UpdateDocumentBO.java
    src/main/java/com/graphnexus/application/document/service/ParseResult.java
    src/main/java/com/graphnexus/common/ApiResponse.java
    src/main/java/com/graphnexus/common/PageResult.java
    .specs/document-process-pdf-minimal/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/document/dto/DocumentVO.java
    src/main/java/com/graphnexus/api/document/dto/ParseResultVO.java
    src/main/java/com/graphnexus/api/document/dto/UpdateDocumentRequest.java
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
  </write_files>
  <action>
    1. DocumentVO（record 或 @Data class）：
       - id(Long → 映射为 documentId 返回前端)
       - documentNo(String), name(String), subject(String)
       - fileSize(Long), minioPath(String), pageCount(Integer)
       - status(String — 枚举名转字符串, 如 "UPLOADED")
       - createTime(LocalDateTime), updateTime(LocalDateTime)
       - 静态工厂 from(DocumentBO) 方法

    2. ParseResultVO（record，对应 ParseResult）：
       - documentId(Long), textContent(String), pageCount(int), metadata(Map<String, String>)
       - 静态工厂 from(Long documentId, ParseResult result)

    3. UpdateDocumentRequest（@Data DTO，接收 JSON body）：
       - name(String, @NotBlank)

    4. DocumentController（@RestController, @RequestMapping("/api/v1/document")）：
       构造器注入 DocumentService。

       端点映射（见 DESIGN § 2.1, § 9.3）：
       - POST /upload
         → @RequestParam MultipartFile file + @RequestParam String subject
         → ApiResponse<DocumentVO>

       - POST /{id}/process
         → @PathVariable Long id
         → ApiResponse<ParseResultVO>

       - GET /
         → @RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="10") int pageSize
         → ApiResponse<PageResult<DocumentVO>>

       - GET /{id}
         → ApiResponse<DocumentVO>

       - PUT /{id}
         → @RequestBody @Valid UpdateDocumentRequest
         → ApiResponse<DocumentVO>

       - DELETE /{id}
         → ApiResponse<Void>

       异常处理：L1 不 try-catch，异常由 GlobalExceptionHandler 统一处理（已有）。
       遵循项目规范：构造器注入 + Javadoc 中文注释 + 作者 Jay。
  </action>
  <verify>mvn compile -q 2>&1 | grep -E 'BUILD|ERROR'</verify>
  <done>6 个 REST 端点编译通过；DocumentVO/ParseResultVO/UpdateDocumentRequest 与前端 JSON 映射正确</done>
  <depends_on>T07</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>单元测试：DocumentStatus + DocumentParser + DocumentService</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentStatus.java
    src/main/java/com/graphnexus/application/document/service/DocumentParser.java
    src/main/java/com/graphnexus/application/document/service/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/application/document/service/ParseResult.java
    src/main/java/com/graphnexus/application/document/service/DocumentService.java
    src/main/java/com/graphnexus/application/document/service/DocumentServiceImpl.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentRepository.java
    src/main/java/com/graphnexus/infrastructure/storage/FileStorageService.java
    .specs/document-process-pdf-minimal/REQUIREMENT.md
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/document/service/DocumentStatusTest.java
    src/test/java/com/graphnexus/application/document/service/PdfBoxDocumentParserTest.java
    src/test/java/com/graphnexus/application/document/service/DocumentServiceTest.java
    src/test/resources/test-pdf/sample.pdf
  </write_files>
  <action>
    使用 JUnit 5 + Mockito。不用 Testcontainers（单元测试 mock 掉外部依赖）。

    1. DocumentStatusTest（对应 AC-3）：
       - 测试每个状态的合法转换（UPLOADED → PROCESSING, PROCESSING → COMPLETED, PROCESSING → FAILED）
       - 测试非法转换抛异常（UPLOADED → COMPLETED, COMPLETED → UPLOADED 等）
       - 测试 FAILED/COMPLETED 可重新 PROCESSING
       - 覆盖率：DocumentStatus.validateTransition() 全部路径

    2. PdfBoxDocumentParserTest（对应 AC-2 解析部分）：
       - 准备 src/test/resources/test-pdf/sample.pdf（最小化文本型 PDF，1 页，含 "Hello GraphNexus" 文本）
       - 测试 parse() 返回 textContent 非空且包含预期文本
       - 测试 parse() 返回 pageCount ≥ 1
       - 测试 parse() 返回 metadata 非 null
       - 示例 PDF 创建方式：用 PDFBox 编程生成（在 @BeforeAll 中动态创建临时 PDF），或手动放入一个极简 PDF

    3. DocumentServiceTest（对应 AC-1, AC-6, AC-7）：
       - Mock DocumentRepository + FileStorageService + DocumentParser
       - 测试 upload(): 校验失败抛 A0004（非 PDF）/ A0005（超限）；成功路径返回 DocumentBO
       - 测试 process(): 状态转换正确；解析失败时 status=FAILED 且 failReason 非空
       - 测试 getDocument(): 存在返回 BO，不存在抛 A0006
       - 测试 deleteDocument(): 逻辑删除 + MinIO deleteFile 被调用
       - 测试 listDocuments(): 分页返回正确
       - 测试 AC-6: 注入 mock DocumentParser → 断言 PdfBoxDocumentParser 未被调用

    遵循项目规范：Javadoc 中文注释 + 作者 Jay + @DisplayName 中文描述
  </action>
  <verify>mvn test -Dtest="DocumentStatusTest,PdfBoxDocumentParserTest,DocumentServiceTest" 2>&1 | tail -20</verify>
  <done>DocumentStatus 状态机全路径覆盖；PdfBoxDocumentParser 解析真实 PDF 通过；DocumentService 6 个方法 mock 测试通过；AC-1/AC-3/AC-6/AC-7 验证通过</done>
  <depends_on>T07, T08</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>集成测试：DocumentService + DocumentController（Testcontainers MySQL + MinIO）</name>
  <read_files>
    src/main/java/com/graphnexus/application/document/service/DocumentServiceImpl.java
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/resources/db/init-document.sql
    src/main/resources/application-dev.yml
    .specs/document-process-pdf-minimal/REQUIREMENT.md
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/document/service/DocumentServiceIntegrationTest.java
    src/test/java/com/graphnexus/api/document/controller/DocumentControllerIntegrationTest.java
    src/test/resources/application-test.yml
  </write_files>
  <action>
    使用 Testcontainers 启动 MySQL 8.0 + MinIO 容器（对应 AC-1, AC-2, AC-4, AC-5, AC-8）。

    1. application-test.yml：
       - MySQL 连接信息指向 Testcontainers 动态端口
       - MinIO 连接信息指向 Testcontainers 动态端口
       - spring.jpa.hibernate.ddl-auto: none（手动执行 init-document.sql）
       - multipart 配置同 dev

    2. DocumentServiceIntegrationTest（@SpringBootTest）：
       - @BeforeAll：启动 MySQL + MinIO 容器，执行 init-document.sql
       - 测试 AC-1：upload PDF → DB 有记录 + MinIO 有文件
       - 测试 AC-2：upload 后 process → textContent 非空 + pageCount 正确 + status=COMPLETED
       - 测试 AC-9（去重）：同一 PDF + 同一 subject 再次上传 → 409 CONFLICT
       - 测试 AC-4：上传 3 个 PDF → listDocuments pageSize=2 → list.size=2, total=3
       - 测试 AC-5：delete → is_deleted=1 + MinIO 文件不存在
       - 测试 AC-8：update name → name 已更新 + update_time 已刷新

    3. DocumentControllerIntegrationTest（@SpringBootTest + @AutoConfigureMockMvc）：
       - 测试全部 6 个端点 HTTP 返回正确（200/400/404/409）
       - 测试 ApiResponse 结构正确（code/message/data/traceId/timestamp）
       - 测试分页返回 PageResult 结构正确
       - 测试非法文件类型返回 400 + errorCode A0004

    遵循项目规范：Javadoc 中文注释 + 作者 Jay
  </action>
  <verify>mvn test -Dtest="DocumentServiceIntegrationTest,DocumentControllerIntegrationTest" 2>&1 | tail -30</verify>
  <done>
    AC-1/AC-2/AC-4/AC-5/AC-8 集成测试全部通过；
    AC-9（去重）集成测试通过；
    Controller 6 个端点 HTTP 返回验证通过
  </done>
  <depends_on>T07, T08</depends_on>
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