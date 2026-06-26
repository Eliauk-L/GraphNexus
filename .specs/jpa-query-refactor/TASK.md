# TASK: Spring Data JPA 方法名派生查询重构

- **Change ID**: `jpa-query-refactor`
- **关联**: `@.specs/jpa-query-refactor/REQUIREMENT.md`、`@.specs/jpa-query-refactor/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P]
Wave 2 (parallel): T03[P], T04[P]   (depends on T01, T02)
Wave 3:            T05               (depends on T03, T04)
```

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>TextbookRepository 5 条 @Query 替换为方法名派生</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/TextbookRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/TextbookDO.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/FileStatus.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/TextbookRepository.java
  </write_files>
  <action>
    将 TextbookRepository 中 5 条简单 @Query 方法替换为方法名派生，移除 @Query 注解和显式 JPQL。
    方法名映射（见 DESIGN § 2.1）：

    1. findByIsDeletedFalse(Pageable)
       → findByIsDeletedAndStatusNot(Integer isDeleted, FileStatus status, Pageable pageable)
    2. findByIdAndIsDeletedFalse(Long id)
       → findByIdAndIsDeletedAndStatusNot(Long id, Integer isDeleted, FileStatus status)
    3. findIdByDocumentNoAndSubjectAndIsDeletedFalse(String documentNo, String subject)
       → findIdByDocumentNoAndSubjectAndIsDeletedAndStatusNot(String documentNo, String subject, Integer isDeleted, FileStatus status)
    4. findFirstByDocumentNoAndNotDeleted(String documentNo)
       → findFirstByDocumentNoAndIsDeletedAndStatusNotOrderByCreateTimeAsc(String documentNo, Integer isDeleted, FileStatus status)
    5. countByFilePathAndNotDeleted(String filePath)
       → countByFilePathAndIsDeletedAndStatusNot(String filePath, Integer isDeleted, FileStatus status)

    第 6 条 findByConditions 保留 @Query 不动（复杂查询，NULL 可选参数 + LIKE %%，DESIGN D5）。

    规范要点：
    - 方法名不含 DO/Entity 后缀
    - isDeleted 使用属性名（isDeleted）非 IsDeletedFalse（因为字段类型是 Integer）
    - 补 AndStatusNot 以完整反映 status &lt;&gt; 'DELETING' 过滤
    - 保留 Javadoc 注释，补充方法名含义说明
    - 移除不再需要的 import（@Query、@Param）
  </action>
  <verify>mvn compile -pl . -q</verify>
  <done>T01 编译通过；5 条方法无 @Query 注解；findByConditions 仍保留 @Query</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>ExamRecordRepository 6 条方法重构（5 替换 + 1 改名）</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/ExamRecordDO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
  </write_files>
  <action>
    将 ExamRecordRepository 中 5 条简单 @Query 方法替换为方法名派生 + 1 条已有方法名派生改名，移除对应的 @Query 注解。
    方法名映射（见 DESIGN § 2.2）：

    1. findByExamNoAndIsDeletedFalse(String examNo)
       → findByExamNoAndIsDeleted(String examNo, Integer isDeleted)  [去 @Query, 加 isDeleted 参数]
    2. findByCsvMd5AndIsDeletedFalse(String csvMd5)
       → findByCsvMd5AndIsDeleted(String csvMd5, Integer isDeleted)  [去 @Query, 加 isDeleted 参数]
    3. findByExamNo(String examNo)
       → findByExamNo(String examNo)  [去 @Query, 方法名不变, 最简单]
    4. findExamRecordDOByStudentNoAndIsDeleted(String studentNo, Integer isDeleted)
       → findByStudentNoAndIsDeleted(String studentNo, Integer isDeleted)  [仅改名去 DO 后缀, 已是方法名派生]
    5. findByStudentNoAndSubject(String studentNo, String subject)
       → findByStudentNoAndSubjectAndIsDeleted(String studentNo, String subject, Integer isDeleted)  [去 @Query, 加 isDeleted 参数]
    6. findDistinctSubjects()
       → findDistinctSubjectByIsDeletedAndSubjectIsNotNullOrderBySubject(Integer isDeleted)  [去 @Query, 加 isDeleted 参数]

    保留 @Query 不动（4 条复杂查询，DESIGN D5）：
    - findStudentByName(String) — LIKE + DISTINCT + Object[]
    - findStudentByNo(String) — DISTINCT + Object[]
    - findDistinctExams(Pageable) — GROUP BY + COUNT + Object[]

    规范要点：
    - 方法名不含 DO/Entity 后缀（第 4 条改名）
    - isDeleted 使用属性名（isDeleted）非 IsDeletedFalse
    - 更新类 Javadoc，移除"延用显式 JPQL 模式"的说明
    - 移除不再需要的 import（@Query、@Param）
  </action>
  <verify>mvn compile -pl . -q</verify>
  <done>T02 编译通过；5+1 条方法无 @Query 注解；4 条复杂方法仍保留 @Query</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="pending">
  <name>更新 TextbookRepository 调用方（3 Service + 3 Test）</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookUploadService.java
    src/main/java/com/graphnexus/application/graph/core/service/impl/GraphServiceImpl.java
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookServiceTest.java
    src/test/java/com/graphnexus/application/file/textbook/service/FileProcessingIntegrationTest.java
    src/test/java/com/graphnexus/application/graph/core/service/GraphServiceTest.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/TextbookRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/entity/FileStatus.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookServiceImpl.java
    src/main/java/com/graphnexus/application/file/textbook/service/TextbookUploadService.java
    src/main/java/com/graphnexus/application/graph/core/service/impl/GraphServiceImpl.java
    src/test/java/com/graphnexus/application/file/textbook/service/TextbookServiceTest.java
    src/test/java/com/graphnexus/application/file/textbook/service/FileProcessingIntegrationTest.java
    src/test/java/com/graphnexus/application/graph/core/service/GraphServiceTest.java
  </write_files>
  <action>
    更新所有调用 TextbookRepository 重构方法的位置，适配新方法签名（新增 isDeleted 和 status 参数）。
    完整调用点清单：

    【TextbookServiceImpl.java】
    - L154: findByIdAndIsDeletedFalse(id)
      → findByIdAndIsDeletedAndStatusNot(id, 0, FileStatus.DELETING)
    - L176: countByFilePathAndNotDeleted(doc.getFilePath())
      → countByFilePathAndIsDeletedAndStatusNot(doc.getFilePath(), 0, FileStatus.DELETING)
    - L147: findByConditions(...) 不变（保留 @Query）

    【TextbookUploadService.java】
    - L72: findFirstByDocumentNoAndNotDeleted(documentNo)
      → findFirstByDocumentNoAndIsDeletedAndStatusNotOrderByCreateTimeAsc(documentNo, 0, FileStatus.DELETING)

    【GraphServiceImpl.java】
    - L52: findByIdAndIsDeletedFalse(documentId)
      → findByIdAndIsDeletedAndStatusNot(documentId, 0, FileStatus.DELETING)

    【TextbookServiceTest.java】
    - L207: when(textbookRepository.findByIdAndIsDeletedFalse(999L))
      → when(textbookRepository.findByIdAndIsDeletedAndStatusNot(eq(999L), eq(0), eq(FileStatus.DELETING)))
      注：需要 import static org.mockito.ArgumentMatchers.eq

    【FileProcessingIntegrationTest.java】
    - L93: findByIdAndIsDeletedFalse(result.getId())
      → findByIdAndIsDeletedAndStatusNot(result.getId(), 0, FileStatus.DELETING)
    - L150: findByIdAndIsDeletedFalse(uploadedDocId)
      → findByIdAndIsDeletedAndStatusNot(uploadedDocId, 0, FileStatus.DELETING)

    【GraphServiceTest.java】
    - L75/86/98/113: when(textbookRepository.findByIdAndIsDeletedFalse(NN))
      → 每处改为 when(textbookRepository.findByIdAndIsDeletedAndStatusNot(eq(NN), eq(0), eq(FileStatus.DELETING)))

    规范：
    - 每次调用传 isDeleted=0, status=FileStatus.DELETING
    - 不引入常量类（v1 最小改动），字面量可接受（DESIGN R2 缓解方案：后续可抽常量）
  </action>
  <verify>mvn compile -pl . -q</verify>
  <done>T03 编译通过；6 个文件的方法调用签名全部更新；findByConditions 调用不变</done>
  <depends_on>T01</depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>更新 ExamRecordRepository 调用方（3 Service + 1 Strategy）</name>
  <read_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/MastersRecalculationService.java
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/file/grade/service/GradeServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/MastersRecalculationService.java
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    src/main/java/com/graphnexus/application/analysis/strategy/StudentDiagnosisStrategy.java
  </write_files>
  <action>
    更新所有调用 ExamRecordRepository 重构方法的位置，适配新方法签名。

    【GradeServiceImpl.java】
    - L76: findByCsvMd5AndIsDeletedFalse(csvMd5)
      → findByCsvMd5AndIsDeleted(csvMd5, 0)
    - L186: findByExamNoAndIsDeletedFalse(examNo)
      → findByExamNoAndIsDeleted(examNo, 0)
    - L226: findByExamNoAndIsDeletedFalse(examNo)
      → findByExamNoAndIsDeleted(examNo, 0)
    - L205: findDistinctExams(...) 不变（保留 @Query）

    【MastersRecalculationService.java】
    - L96: findExamRecordDOByStudentNoAndIsDeleted(studentNo, 0)
      → findByStudentNoAndIsDeleted(studentNo, 0)  （仅改名，参数不变）

    【QueryServiceImpl.java】
    - L356: findDistinctSubjects()
      → findDistinctSubjectByIsDeletedAndSubjectIsNotNullOrderBySubject(0)
    - L413: findStudentByNo(...) 不变（保留 @Query）
    - L432: findStudentByName(...) 不变（保留 @Query）

    【StudentDiagnosisStrategy.java】
    - L146: findByStudentNoAndSubject(studentNo, null)
      → findByStudentNoAndSubjectAndIsDeleted(studentNo, null, 0)
      注：此处 subject 传 null 是原代码行为（查询某学生所有学科），保持不变

    规范：
    - 每次调用传 isDeleted=0
    - 字面量 0 可接受（DESIGN R2 缓解方案）
  </action>
  <verify>mvn compile -pl . -q</verify>
  <done>T04 编译通过；4 个文件的 ExamRecordRepository 方法调用全部更新；复杂查询调用不变</done>
  <depends_on>T02</depends_on>
</task>

<task id="T05" parallel="false" status="pending">
  <name>全量测试验证 + CONTEXT.md 核验</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/TextbookRepository.java
    src/main/java/com/graphnexus/infrastructure/mysql/file/repository/ExamRecordRepository.java
    src/test/java/com/graphnexus/
    .specs/CONTEXT.md
    .specs/jpa-query-refactor/REQUIREMENT.md
  </read_files>
  <write_files>
    .specs/CONTEXT.md
  </write_files>
  <action>
    运行完整测试套件，逐条验证 REQUIREMENT.md 的 5 条 AC：

    AC-1 验证（Boolean 查询正常）：
    - 确认 findByIsDeletedAndStatusNot(0, DELETING, pageable) 生成的 SQL 包含 is_deleted = 0
    - 与重构前查询结果一致

    AC-2 验证（@Query 已替换）：
    - grep TextbookRepository.java 确认 T1-T5 无 @Query 注解
    - grep ExamRecordRepository.java 确认 E1-E6 无 @Query 注解

    AC-3 验证（命名规范）：
    - 人工 review 方法名，确认无 DO 后缀
    - 确认 isDeleted=0 过滤在方法名中体现（含 IsDeleted 参数）
    - 确认 status<>'DELETING' 过滤在方法名中体现（含 StatusNot）

    AC-4 验证（复杂查询保留）：
    - grep 确认 findByConditions、findStudentByName、findStudentByNo、findDistinctExams 仍含 @Query

    AC-5 验证（调用方语义不变）：
    - 运行 mvn test，确认 0 失败

    CONTEXT.md 更新：
    - 将「DocumentRepository JPQL」条目标记为已废弃/已由 jpa-query-refactor 替换
    - 注：此更新已在 REQUIREMENT 阶段部分完成，本步做最终核验
  </action>
  <verify>mvn test -pl . 2>&1 | tail -30</verify>
  <done>mvn test 全部通过（0 failures, 0 errors）；5 条 AC 全部满足；CONTEXT.md 已更新</done>
  <depends_on>T03, T04</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

## Fix 任务（来自 REVIEW / INTEGRATION）

```xml
<!-- 占位 -->
```