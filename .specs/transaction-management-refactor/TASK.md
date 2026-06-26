# TASK: 后端事务管理策略梳理与重构

- **Change ID**: `transaction-management-refactor`
- **关联**: `@.specs/transaction-management-refactor/REQUIREMENT.md`、`@.specs/transaction-management-refactor/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[D], T02[D], T03[D], T04[D], T05[D]
Wave 2 (parallel): T06[D] (depends on T05), T07[D], T08[D]
Wave 3 (parallel): T09[D], T10[D]   (depends on Wave 2)
Wave 4:            T11[D]            (depends on Wave 3)
```

> Wave 1: 5 个独立小改动，不同文件，零冲突。
> Wave 2: 3 个核心重构（T06 依赖 T05 的接口变更，T07/T08 独立文件可并行）。
> Wave 3: 测试与验证。
> Wave 4: 全量回归 + UAT。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>FusionServiceImpl.updateLogFailed() 笔误修复</name>
  <read_files>
    src/main/java/com/graphnexus/application/analysis/fusion/service/impl/FusionServiceImpl.java
    src/test/java/com/graphnexus/application/analysis/fusion/service/impl/FusionServiceImplTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/analysis/fusion/service/impl/FusionServiceImpl.java
    src/test/java/com/graphnexus/application/analysis/fusion/service/impl/FusionServiceImplTest.java
  </write_files>
  <action>
    FusionServiceImpl.java 第 228 行：将 `logEntry.setStatus("COMPLETED");` 修复为 `logEntry.setStatus("FAILED");`。
    updateLogFailed() 位于 catch 块，语义明确应为失败状态，这是笔误。
    新增或扩展单元测试：mock 融合执行中抛出异常，捕获后验证 fusionLogRepository.save() 的参数中 status 为 "FAILED"。
    见 D7。
  </action>
  <verify>mvn test -Dtest=FusionServiceImplTest -pl . 2>&1 | tail -5</verify>
  <done>updateLogFailed() 写入 status="FAILED"；单元测试断言通过。对应 AC-7。</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>GradeServiceImpl.deleteByExamNo() 事件移出事务</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java
    src/test/java/com/graphnexus/application/file/grade/service/GradeServiceImplTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java
    src/test/java/com/graphnexus/application/file/grade/service/GradeServiceImplTest.java
  </write_files>
  <action>
    拆分 deleteByExamNo()（当前标注 @Transactional）：
    ① 短事务部分（标注 @Transactional）：查询 findByExamNo + 物理删除 deleteAll。
    ② 事务外部分：发布 GradeDeletedEvent。
    
    调用方（GradeController）改为先调短事务方法，再调 publishEvent。
    或将方法拆为两个：deleteByExamNoInTx() + 在 Controller 发布事件。
    
    保持幂等语义（已删除返回空 = 成功）。
    见 D6。
  </action>
  <verify>mvn test -Dtest=GradeServiceImplTest -pl . 2>&1 | tail -5</verify>
  <done>deleteByExamNo 的 DB 操作在短事务内、事件在事务外发布；既存测试全通过。对应 AC-4。</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>GradeUploadService.upload() 拆分：解析（无事务）+ 短事务（去重+saveAll）+ 事务外事件</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java
    src/main/java/com/graphnexus/application/file/grade/event/GradeUploadedEvent.java
    src/test/java/com/graphnexus/application/file/grade/service/GradeUploadServiceTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeUploadService.java
    src/test/java/com/graphnexus/application/file/grade/service/GradeUploadServiceTest.java
  </write_files>
  <action>
    拆分 upload()（当前整个方法标注 @Transactional）：
    ① 文件解析（无事务）：读取字节 → FileParserRegistry 选解析器 → parse → 产出 GradeParsePayload。
    ② 短事务（标注 @Transactional）：exam_no 去重检查 + saveAll(records) + commit。
    ③ 事务外：发布 GradeUploadedEvent。
    
    实现方式：将步骤②抽取为独立的 @Transactional public 方法（如 `persistGradeRecords`），
   步骤①③在 upload() 中无事务执行。upload() 自身不标注 @Transactional。
    见 D5。
  </action>
  <verify>mvn test -Dtest=GradeUploadServiceTest -pl . 2>&1 | tail -5</verify>
  <done>解析不在事务内、去重+saveAll 在短事务内、事件在事务外；既存测试全通过。对应 AC-4。</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>TextbookUploadService.upload() 调整：MinIO 上传移出事务</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookUploadService.java
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookUploadServiceTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookUploadService.java
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookUploadServiceTest.java
  </write_files>
  <action>
    TextbookUploadService.upload() 当前标注 @Transactional，包含 MinIO 上传（慢 I/O）+ DB insert。
    重构：去除方法上的 @Transactional，将 DB 操作（检查重复 + save）抽取为独立的 @Transactional public 方法。
    MinIO 上传保持在外层方法中（无事务）。
    
    注意：检验重复的 findFirstByDocumentNoAndStatusNotOrderByCreateTimeAsc + save 需要原子性，
    放在同一个 @Transactional 方法中。
    
    调用方 TextbookServiceImpl.upload() 保持不变（通过接口调用，无需感知内部拆分）。
    见 ADR-028 规则 1+2。
  </action>
  <verify>mvn test -Dtest=TextbookUploadServiceTest -pl . 2>&1 | tail -5</verify>
  <done>MinIO 上传不在 @Transactional 内；DB 操作在独立短事务；既存测试通过。对应 AC-3。</done>
  <depends_on></depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>TextbookParsedEventListener.saveFailReason 委托给 TextbookService</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/listener/TextbookParsedEventListener.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookService.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/listener/TextbookParsedEventListener.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookService.java
  </write_files>
  <action>
    当前 TextbookParsedEventListener.saveFailReason() 标注 @Transactional 但被同类 onTextbookParsed()
    直接调用，存在 Spring AOP 自调用穿透问题（@Transactional 可能不生效）。
    
    修复方案（见 D4）：
    ① 在 TextbookService 接口中新增方法签名：
       `void saveFailReason(Long documentId, String errorMessage);`
    ② TextbookParsedEventListener 注入 TextbookService，在 catch 块中调用
       `textbookService.saveFailReason(documentId, e.getMessage())` 替代原有的 `this.saveFailReason(...)`
    ③ 旧的 saveFailReason 方法标记 @Deprecated 并删除 @Transactional
    
    注意：T05 仅修改接口 + 监听器；实现体由 T06 在 TextbookServiceImpl 中完成。
    见 D4、D8。
  </action>
  <verify>mvn compile -pl . 2>&1 | tail -5</verify>
  <done>TextbookService 接口新增 saveFailReason 签名；监听器委托给 Service（消除自调用）；编译通过。对应 AC-6。</done>
  <depends_on></depends_on>
</task>

<task id="T06" parallel="false" status="done">
  <name>TextbookServiceImpl 全量重构：parse 拆分 + deleteTextBook 拆分 + afterCommit 消除 + saveFailReason 实现</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookService.java
    src/main/java/com/graphnexus/application/file/textbook/event/TextbookParsedEvent.java
    src/main/java/com/graphnexus/application/file/textbook/event/TextbookDeletedEvent.java
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookServiceImplTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookServiceImplTest.java
  </write_files>
  <action>
    TextbookServiceImpl 是本次重构最大的改动目标。当前 4 个 @Transactional 方法 + 2 处 afterCommit 回调。
    
    ① parse() 拆分（见 D1）：
       - 状态更新部分抽取为独立 @Transactional public 方法：
         a. `beginParsing(docId)` — findById + validate + status=PARSING → commit
         b. `completeParsing(docId, parseResult)` — status=PARSED + textContent + pageCount → commit
         c. `failParsing(docId, reason)` — status=UPLOADED + failReason → commit
       - parse() 主体（不标注 @Transactional）：
         调 beginParsing → MinIO read → Parser chain → completeParsing/failParsing → 发布 TextbookParsedEvent
       - 移除所有 saveAndFlush() 调用（短事务 commit 替代）
       - 移除 TransactionSynchronizationManager.registerSynchronization() afterCommit 回调
    
    ② deleteTextBook() 拆分（见 D1）：
       - `beginDeletion(docId)` — @Transactional: findById + validate + status=DELETING → commit
       - deleteTextBook() 主体（不标注 @Transactional）：调 beginDeletion → 发布 TextbookDeletedEvent
       - 移除 afterCommit 回调
    
    ③ 实现 saveFailReason()（来自 T05 的接口新增）：
       - @Transactional public 方法：findById → setFailReason + save → commit
    
    ④ finalizeDeletion() 保持不变（已是独立的 @Transactional public 方法，符合规范）。
    
    ⑤ listTextBooks() / getTextBook() 的 @Transactional(readOnly = true) 保持不变（查询方法，符合规范）。
    
    注意：TextbookParsedEvent 在 parse() 外层无事务发布（D3）。TextbookDeletedEvent 同理。
    @Async 监听器 TextbookParsedEventListener 在新线程中自然读到已提交的 PARSED/DELETING 状态。
    见 D1、D3。
  </action>
  <verify>mvn test -Dtest=TextbookServiceImplTest -pl . 2>&1 | tail -5</verify>
  <done>parse() 拆分为 3 段（短tx→无tx→短tx）；deleteTextBook() 拆分为 短tx→事务外事件；afterCommit 回调消除；saveFailReason 实现；既存测试全通过。对应 AC-1、AC-4、AC-5。</done>
  <depends_on>T05</depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>ConstructionServiceImpl.extract() 拆分 + Neo4j TransactionTemplate + 跨存储补偿</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/application/graph/construction/service/ConstructionService.java
    src/main/java/com/graphnexus/infrastructure/neo4j/config/Neo4jTxConfig.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/ConstructionGraphRepository.java
    src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java
    src/test/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImplTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/test/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImplTest.java
  </write_files>
  <action>
    ConstructionServiceImpl.extract() 当前标注 @Transactional，内含 LLM 调用 + Neo4j 裸写 + saveAndFlush + 事件发布。
    
    ① 注入 Neo4jTransactionManager：
       - 新增 `private final Neo4jTransactionManager neo4jTransactionManager;` 构造器参数
    
    ② extract() 拆分（见 D2、D10）：
       a. `beginExtraction(docId)` — @Transactional: findById + validate + status=EXTRACTING → commit
       b. extract() 主体（不标注 @Transactional）：
          - 调 beginExtraction
          - LLM 抽取（ExtractionService.extract()）→ 失败调 failExtraction（补偿回退 PARSED+failReason）
          - Neo4j 阶段一写入（包裹在 TransactionTemplate(neo4jTransactionManager).execute()）→ 失败调 failExtraction
          - `completeExtraction(docId)` — @Transactional: status=EXTRACTED → commit
          - 发布 GraphChangedEvent → 发布 GraphConstructedEvent
       c. `failExtraction(docId, reason)` — @Transactional: status=PARSED + failReason → commit（补偿）
    
    ③ 移除所有 saveAndFlush() 调用（短事务 commit 替代）。
    
    ④ getSubgraph() / getFullGraph() 的 @Transactional(readOnly = true) 保持不变（查询方法）。
    
    见 D2、D9、D10、ADR-029。
  </action>
  <verify>mvn test -Dtest=ConstructionServiceImplTest -pl . 2>&1 | tail -5</verify>
  <done>extract() 拆分为 4 段（短tx→无tx+Neo4jTx→短tx→事务外事件）；Neo4j 有 TransactionTemplate 保护；失败时补偿回退 PARSED+failReason；既存测试通过。对应 AC-2、AC-8。</done>
  <depends_on></depends_on>
</task>

<task id="T08" parallel="true" status="done">
  <name>GraphConstructedEventListener 独立短事务（脱离发布方 JPA tx）</name>
  <read_files>
    src/main/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListener.java
    src/test/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListenerTest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListener.java
    src/test/java/com/graphnexus/application/analysis/fusion/event/GraphConstructedEventListenerTest.java
  </write_files>
  <action>
    当前 GraphConstructedEventListener.onGraphConstructed() 是 plain @EventListener，
    join 发布方 ConstructionServiceImpl.extract() 的 JPA @Transactional（PROPAGATION_REQUIRED）。
    
    T07 重构后 extract() 不再标注 @Transactional，因此本监听器执行时无 JPA tx 上下文。
    需要为文档状态变更添加独立的短事务（见 D4 + ADR-028 规则 1）：
    
    ① handleDocumentPath() 中的状态变更改用独立 @Transactional 方法：
       a. `beginFusion(docId)` — @Transactional: findById + status=FUSING → commit
       b. `completeFusion(docId)` — @Transactional: status=COMPLETED → commit
       c. `failFusion(docId, reason)` — @Transactional: status=EXTRACTED + failReason → commit（补偿）
    
    ② onGraphConstructed() 方法体：
       调 beginFusion → fuseIncremental → completeFusion/failFusion → 发布 GraphChangedEvent
    
    ③ onGraphConstructed() 自身不标注 @Transactional（D4）。
    
    ④ handleCsvPath() 保持不变（无文档状态机，仅调 fuseFull）。
    
    见 D4、D8。
  </action>
  <verify>mvn test -Dtest=GraphConstructedEventListenerTest -pl . 2>&1 | tail -5</verify>
  <done>监听器状态变更使用独立短事务；不依赖发布方事务上下文；FUSING→COMPLETED/EXTRACTED 状态机正确。对应 AC-6、AC-8。</done>
  <depends_on></depends_on>
</task>

<task id="T09" parallel="true" status="done">
  <name>ArchUnit 事务规范测试规则（AC-3 + AC-6）</name>
  <read_files>
    src/main/java/com/graphnexus/application/**/*.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/*.java
    src/main/java/com/graphnexus/infrastructure/neo4j/config/Neo4jTxConfig.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/architecture/TransactionArchTest.java
  </write_files>
  <action>
    新建 ArchUnit 测试类 TransactionArchTest，包含以下规则：
    
    ① AC-3 规则（指导性 · warn 非 fail）：
       - @Transactional 标注的类/方法不应访问 `..neo4j.repository..` 包中的类
       - 豁免清单：Neo4jTxConfig（配置类）、只读查询（需手动审查确认）
    
    ② AC-6 规则：
       a. @Transactional 仅出现在 public 方法上
       b. @EventListener 方法上不出现 @Transactional
       c. 不在 @Transactional 类/方法中调用 TransactionSynchronizationManager.registerSynchronization
    
    ③ 辅助检查（warn）：
       - ApplicationEventPublisher.publishEvent 调用不在 @Transactional 方法内
    
    所有规则使用 ArchUnit `layeredArchitecture()` 或 `methods().that().areAnnotatedWith(...)` API。
    置于 `src/test/java/com/graphnexus/architecture/TransactionArchTest.java`。
    见 DESIGN § 1 (D3, D4) + AC-3 + AC-6。
  </action>
  <verify>mvn test -Dtest=TransactionArchTest -pl . 2>&1 | tail -10</verify>
  <done>ArchUnit 规则可执行；当前代码通过所有规则（或已知豁免项被显式记录）。对应 AC-3、AC-6。</done>
  <depends_on>T06,T07,T08</depends_on>
</task>

<task id="T10" parallel="true" status="done">
  <name>事务重构专项集成测试（AC-1 + AC-2 + AC-5 + AC-7 + AC-8）</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
    src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java
    src/main/java/com/graphnexus/application/analysis/fusion/service/impl/FusionServiceImpl.java
    src/test/java/com/graphnexus/application/**/*Test.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/file/textbook/service/TransactionVisibilityTest.java
    src/test/java/com/graphnexus/application/file/textbook/service/AfterCommitEliminationTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/CrossStorageCompensationTest.java
  </write_files>
  <action>
    新建 3 个测试类，覆盖核心 AC：
    
    ① TransactionVisibilityTest（AC-1）：
       - 集成测试：调 POST /parse/X → 立即在新事务中 GET /list → 断言 status == "PARSING"
       - 使用 @SpringBootTest + @ActiveProfiles("dev") + TestRestTemplate
       - 验证状态在 1s 内对另一数据库连接可见
    
    ② AfterCommitEliminationTest（AC-5）：
       - 扫描 src/main/java 下所有 .java 文件
       - 断言不含 "TransactionSynchronizationManager.registerSynchronization" 和 "new TransactionSynchronization"
       - 简单的文本扫描测试（不依赖 Spring 上下文，快速执行）
    
    ③ CrossStorageCompensationTest（AC-8）：
       - Mock ConstructionGraphRepository.save() 抛出 RuntimeException
       - 调 extract() → 验证文档状态回退到 PARSED + failReason 非空
       - 使用 @SpringBootTest + @ActiveProfiles("dev") + @MockBean
    
    另：FusionServiceImpl.updateLogFailed 的单元测试已在 T01 中覆盖（AC-7）。
    LLM/MinerU 不在事务内（AC-2）由 T09 ArchUnit 规则 + 代码审查覆盖，本任务不重复。
  </action>
  <verify>mvn test -Dtest="TransactionVisibilityTest,AfterCommitEliminationTest,CrossStorageCompensationTest" -pl . 2>&1 | tail -10</verify>
  <done>3 个测试类全部通过；AC-1 状态可见性验证、AC-5 afterCommit 消除验证、AC-8 跨存储补偿验证。对应 AC-1、AC-5、AC-8。</done>
  <depends_on>T06,T07,T08</depends_on>
</task>

<task id="T11" parallel="false" status="done">
  <name>全量回归测试 + 手动 UAT + 工件更新</name>
  <read_files>
    .specs/transaction-management-refactor/CHANGE.md
    .specs/transaction-management-refactor/REQUIREMENT.md
    .specs/transaction-management-refactor/DESIGN.md
    .specs/CONTEXT.md
    STATE.md
  </read_files>
  <write_files>
    .specs/CONTEXT.md
    STATE.md
  </write_files>
  <action>
    ① 全量测试：`mvn test` 所有模块，确认 0 failures, 0 errors。
    ② 手动 UAT 走通完整链路（podman 环境）：
       a. 上传 PDF → 点击解析 → 观察状态 PARSING→PARSED
       b. 点图谱化 → 观察 EXTRACTING→EXTRACTED→FUSING→COMPLETED
       c. 智能问答验证（1 个问题同步模式）
       d. 删除文档 → 验证级联清理
       e. 上传成绩 CSV → 查询成绩 → 删除成绩
    ③ 更新 CONTEXT.md：确认 ADR-028/029 已入术语表与已锁决策（REQUIREMENT 阶段已做，复查无遗漏）。
    ④ 更新 STATE.md：标记 change 完成，更新执行记录。
    ⑤ 各任务 SUMMARY.md 汇总。
    见 AC-9、AC-10。
  </action>
  <verify>mvn test 2>&1 | grep -E "Tests run:|BUILD"</verify>
  <done>全量 mvn test 绿色（0 failures, 0 errors）；手动 UAT 完整链路无异常；CONTEXT.md/STATE.md 更新完毕。对应 AC-9、AC-10。</done>
  <depends_on>T09,T10</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞

---

## AC 覆盖矩阵

| AC | 标题 | 覆盖任务 |
|----|------|---------|
| AC-1 | 中间态前端可见 | T06, T10 |
| AC-2 | 慢操作不在事务内 | T07, T09 |
| AC-3 | JPA 仅包裹 MySQL | T04, T09 |
| AC-4 | 事件不在事务内 | T02, T03, T06 |
| AC-5 | afterCommit 消除 | T06, T10 |
| AC-6 | @Transactional 规范统一 | T05, T08, T09 |
| AC-7 | updateLogFailed 修复 | T01 |
| AC-8 | 跨存储补偿 | T07, T08, T10 |
| AC-9 | 全量回归 | T11 |
| AC-10 | 前后端轮询集成 | T11（手动 UAT） |

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|------|---------|------------|------|
| — | — | — | — |