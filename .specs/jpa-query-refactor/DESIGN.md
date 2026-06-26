# DESIGN: Spring Data JPA 方法名派生查询重构

- **Change ID**: `jpa-query-refactor`
- **关联**: `@.specs/jpa-query-refactor/REQUIREMENT.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> CONTEXT.md 已锁定技术栈，本 change 沿用，不引入新技术栈。

- **后端框架**: Spring Boot 3.3.5（不变）
- **ORM**: Hibernate 6.5.3.Final（Spring Boot 3.3.5 默认，**不升级**，理由见 D1）
- **数据访问**: Spring Data JPA 方法名派生查询（替代显式 `@Query` JPQL）
- **数据库**: MySQL 8.0（不变）
- **理由**: `isDeleted` 字段为 `Integer` 类型，通过 `findByIsDeleted(Integer)` 而非 `findByIsDeletedFalse()` 即可完全规避 Hibernate 6.5 Boolean/TINYINT 谓词 bug，无需升级 Hibernate

---

## 0.5 既有架构对齐

### 0.5.1 本次 change 触碰的既有模块

**修改模块**：
- `infrastructure/mysql/file/repository/TextbookRepository.java` — 6 条方法：去掉 `@Query`，改为方法名派生
- `infrastructure/mysql/file/repository/ExamRecordRepository.java` — 6 条方法：去掉 `@Query` + 改名
- `application/file/textbook/service/TextbookServiceImpl.java` — 适配新方法签名（3 处调用）
- `application/file/textbook/service/TextbookUploadService.java` — 适配新方法签名（1 处调用）
- `application/file/grade/service/GradeServiceImpl.java` — 适配新方法签名（3 处调用）
- `application/graph/core/service/impl/GraphServiceImpl.java` — 适配新方法签名（1 处调用）
- `application/graph/fusion/service/impl/MastersRecalculationService.java` — 适配新方法签名（1 处调用）
- `application/query/chat/service/impl/QueryServiceImpl.java` — 适配新方法签名（1 处调用）
- `application/analysis/strategy/StudentDiagnosisStrategy.java` — 适配新方法签名（1 处调用）
- `.specs/CONTEXT.md` — 更新「已锁技术决策」DocumentRepository JPQL 条目

**新增模块**：无

**禁动清单**（与本次无关，不允许"顺手"碰）：
- `pom.xml` — 确认不升级 Hibernate 后，不再改动（D1 决策）
- `infrastructure/mysql/file/entity/TextbookDO.java` — DO 实体字段不动（`isDeleted` 保持 Integer，D2 决策）
- `infrastructure/mysql/file/entity/ExamRecordDO.java` — DO 实体字段不动
- `infrastructure/mysql/fusion/repository/FusionLogRepository.java` — 已全量方法名派生，不动
- `infrastructure/mysql/query/repository/QueryTaskRepository.java` — 已全量方法名派生，不动
- `infrastructure/neo4j/repository/GraphNodeRepository.java` — Neo4j，不动

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 数据访问 | `JpaRepository<T, Long>`（4 个 Repository 已统一） | 沿用 |
| 方法名派生查询 | Spring Data JPA 自动提供 | 沿用，不引入 Specification |
| 事务管理 | `@Transactional` 在 L2 Service 层 | 沿用 |
| 逻辑删除过滤 | `isDeleted` Integer 字段 + `FileStatus` 枚举 | 沿用既有字段，不改类型 |
| JPA 审计 | `@EnableJpaAuditing` + `@CreatedDate`/`@LastModifiedDate` | 沿用 |

### 0.5.3 沿用模式 vs 引入新模式

```
- 数据访问：**沿用** JpaRepository + 方法名派生（既有的 FusionLogRepository/QueryTaskRepository 已是此模式）
- 方法命名：**沿用** Spring Data JPA 规范命名（去掉 DO 后缀，与既有 FusionLogRepository 一致）
- 错误处理：**沿用** BusinessException + GlobalExceptionHandler
- 不引入：JpaSpecificationExecutor、EntityGraph、DTO 投影（均为 v2 范围）
```

---

## 1. 决策清单

### D1 · Hibernate 不升级

- **决策**: 不升级 Hibernate 版本，保持 Spring Boot 3.3.5 默认的 Hibernate 6.5.3.Final
- **备选**: 升级到 Hibernate 6.5.x 最新版（覆盖 `<hibernate.version>`）或升级 Spring Boot 3.3.x → 3.3.13
- **选择理由**: 
  1. `TextbookDO.isDeleted` 和 `ExamRecordDO.isDeleted` 的类型是 `Integer`（非 `Boolean`），重构后使用 `findByIsDeleted(Integer)` 而非 `findByIsDeletedFalse()`，不会触发 Hibernate 6.5 对 `Boolean` + MySQL TINYINT 的 `isFalse()` 谓词构建 bug
  2. CONTEXT.md 记录的 bug 仅在尝试使用 Spring Data JPA 的 `IsFalse` 方法名后缀时触发，本次重构不使用该后缀
  3. 不升级 = 零依赖风险，改动面最小
- **取舍代价**: 方法名中使用 `IsDeleted`（属性名）而非 `IsDeletedFalse`（Spring Data 惯用后缀），调用方需显式传递 `Integer isDeleted` 参数（值为 0）。这种写法的清晰度实际更高——调用代码看得见 `isDeleted = 0` 的值

### D2 · isDeleted 字段类型保持 Integer

- **决策**: `TextbookDO.isDeleted` 和 `ExamRecordDO.isDeleted` 保持 `Integer` 类型（值为 0/1），不改为 `Boolean`
- **备选**: 改为 `Boolean` + `@Type` 注解，使 `findByIsDeletedFalse()` 可用
- **选择理由**: 
  1. 改 DO 字段类型会联动影响数据库 schema、所有序列化/反序列化路径、前端展示逻辑
  2. `Integer` 类型的 0/1 与 MySQL TINYINT 天然匹配，不会产生类型转换歧义
  3. 改动面最小原则——本次目标是简化 Repository 查询，不涉及实体模型重构
- **取舍代价**: 方法名中 `findByIsDeleted(Integer)` 需传 0，不如 `findByIsDeletedFalse()` 语义化；但调用代码中 `0` 的含义清晰（`isDeleted = 0` 即未删除），可读性不降

### D3 · 方法名使用属性名而非 Spring Data 惯用后缀

- **决策**: 方法名使用实体属性名（如 `IsDeleted`）而非 Spring Data 惯用的布尔后缀（如 `IsDeletedFalse`），因为 `isDeleted` 是 `Integer` 类型
- **备选**: 改实体为 `Boolean` 后使用 `IsDeletedFalse`
- **选择理由**: D2 已决定保持 Integer，Spring Data 对 Integer 属性的等值比较使用属性名本身（`findByXxx(Integer)`），语义正确
- **取舍代价**: 方法名无法自动表达"isDeleted = 0 即未删除"，需通过参数值表达。可在方法 Javadoc 中补充说明

### D4 · 方法名完整反映查询条件

- **决策**: 重构后的方法名必须包含**所有**查询条件，包括原 `@Query` 中隐式的 `status <> 'DELETING'` 过滤
- **备选**: 保持旧命名（如 `findByIsDeletedFalse`）但实际还过滤 status——名字不反映全貌
- **选择理由**: AC-3 要求"方法名完整反映查询语义"。当前 `findByIsDeletedFalse` 实际还有 `status <> 'DELETING'` 条件，名字误导读者。重构是一次性修正的时机
- **取舍代价**: 方法名变长。例：`findByIsDeletedFalse` → `findByIsDeletedAndStatusNot`（增加 `AndStatusNot`）。可通过静态导入或 Service 层封装缓解

### D5 · 复杂查询保留 @Query

- **决策**: 4 条复杂查询保留 `@Query` 注解不动
- **备选**: 引入 `JpaSpecificationExecutor` 或 DTO 投影重构
- **选择理由**: AC-4 要求保留，且 Specification/DTO 投影属于 v2 范围。这些查询涉及 `LIKE %?%`、`NULL` 可选参数、`GROUP BY`、`Object[]` 投影，方法名派生无法表达
- **取舍代价**: 4 条 `@Query` 仍是"手写 SQL"风格，但功能复杂度决定了这是合理的边界

---

## 2. 方法名映射表

### 2.1 TextbookRepository（6 → 1 条替换 + 5 条改为方法名派生）

| # | 旧方法名 | 旧 JPQL 条件 | 新方法签名 | 调用方参数变化 |
|---|---|---|---|---|
| T1 | `findByIsDeletedFalse(Pageable)` | `isDeleted=0 AND status<>'DELETING'` | `findByIsDeletedAndStatusNot(Integer isDeleted, FileStatus status, Pageable)` | 需传 `(0, FileStatus.DELETING, pageable)` |
| T2 | `findByIdAndIsDeletedFalse(Long)` | `id=? AND isDeleted=0 AND status<>'DELETING'` | `findByIdAndIsDeletedAndStatusNot(Long id, Integer isDeleted, FileStatus status)` | 需传 `(id, 0, FileStatus.DELETING)` |
| T3 | `findIdByDocumentNoAndSubjectAndIsDeletedFalse(String, String)` | `documentNo=? AND subject=? AND isDeleted=0 AND status<>'DELETING'`（返回 id） | `findIdByDocumentNoAndSubjectAndIsDeletedAndStatusNot(String documentNo, String subject, Integer isDeleted, FileStatus status)` | 需传 `(documentNo, subject, 0, FileStatus.DELETING)` |
| T4 | `findFirstByDocumentNoAndNotDeleted(String)` | `documentNo=? AND isDeleted=0 AND status<>'DELETING' ORDER BY createTime ASC LIMIT 1` | `findFirstByDocumentNoAndIsDeletedAndStatusNotOrderByCreateTimeAsc(String documentNo, Integer isDeleted, FileStatus status)` | 需传 `(documentNo, 0, FileStatus.DELETING)` |
| T5 | `countByFilePathAndNotDeleted(String)` | `filePath=? AND isDeleted=0 AND status<>'DELETING'` | `countByFilePathAndIsDeletedAndStatusNot(String filePath, Integer isDeleted, FileStatus status)` | 需传 `(filePath, 0, FileStatus.DELETING)` |
| T6 | `findByConditions(String, String, Pageable)` | 复杂：NULL 可选 + LIKE | **保留 @Query** | 不变 |

### 2.2 ExamRecordRepository（9 → 4 条替换 + 1 条改名 + 4 条保留）

| # | 旧方法名 | 旧 JPQL 条件 | 新方法签名 | 调用方参数变化 |
|---|---|---|---|---|
| E1 | `findByExamNoAndIsDeletedFalse(String)` | `examNo=? AND isDeleted=0` | `findByExamNoAndIsDeleted(String examNo, Integer isDeleted)` | 需传 `(examNo, 0)` |
| E2 | `findByCsvMd5AndIsDeletedFalse(String)` | `csvMd5=? AND isDeleted=0` | `findByCsvMd5AndIsDeleted(String csvMd5, Integer isDeleted)` | 需传 `(csvMd5, 0)` |
| E3 | `findByExamNo(String)` | `examNo=?` | `findByExamNo(String examNo)` | 不变（最简单） |
| E4 | `findExamRecordDOByStudentNoAndIsDeleted(String, Integer)` | 已是方法名派生 | `findByStudentNoAndIsDeleted(String studentNo, Integer isDeleted)` | 改名 + 参数不变 |
| E5 | `findByStudentNoAndSubject(String, String)` | `studentNo=? AND subject=? AND isDeleted=0` | `findByStudentNoAndSubjectAndIsDeleted(String studentNo, String subject, Integer isDeleted)` | 需传 `(studentNo, subject, 0)` |
| E6 | `findDistinctSubjects()` | `DISTINCT subject WHERE isDeleted=0 AND subject IS NOT NULL ORDER BY subject` | `findDistinctSubjectByIsDeletedAndSubjectIsNotNullOrderBySubject(Integer isDeleted)` | 需传 `(0)` |
| E7 | `findStudentByName(String)` | 复杂：LIKE + DISTINCT + Object[] | **保留 @Query** | 不变 |
| E8 | `findStudentByNo(String)` | 复杂：DISTINCT + Object[] | **保留 @Query** | 不变 |
| E9 | `findDistinctExams(Pageable)` | 复杂：GROUP BY + COUNT + Object[] | **保留 @Query** | 不变 |

### 2.3 方法名最长案例评估

最长方法名：`findFirstByDocumentNoAndIsDeletedAndStatusNotOrderByCreateTimeAsc`

| 指标 | 值 |
|---|---|
| 字符数 | 67 |
| 可读性 | `findFirstBy` + `DocumentNo` + `AndIsDeleted` + `AndStatusNot` + `OrderByCreateTimeAsc` — 每个段独立可读 |
| Spring Data 限制 | 无硬性字符限制，仅要求方法名可解析为合法属性路径 |
| 既有 FusionLogRepository 参考 | `findTopByStatusOrderByExecutedAtDesc`（37 字符），增加条件后变长是自然结果 |
| 判断 | ✅ 可接受。IDEA 自动补全 + Javadoc 注释辅助理解 |

---

## 3. 数据流

```
L2 Service（调用方）
    │
    │  调用 textbookRepository.findByIsDeletedAndStatusNot(0, FileStatus.DELETING, pageable)
    │          examRecordRepository.findByStudentNoAndIsDeleted("S001", 0)
    ▼
L3 Repository（Spring Data JPA 接口）
    │
    │  方法名解析 → JPQL 自动生成
    │  例：findByIsDeletedAndStatusNot(Integer, FileStatus, Pageable)
    │       → SELECT d FROM TextbookDO d WHERE d.isDeleted = ?1 AND d.status <> ?2
    ▼
Hibernate 6.5.3.Final
    │
    │  JPQL → SQL 转换（MySQL 8.0 方言）
    │  Integer isDeleted = 0  →  is_deleted = 0  （纯 Integer 比较，不触发 Boolean/TINYINT bug）
    │  FileStatus.DELETING   →  status <> 'DELETING'
    ▼
MySQL 8.0
```

**关键点**：`Integer isDeleted` 的直接等值比较不经过 `AttributeConverter<Boolean, Integer>`，Hibernate 生成标准 `col = ?` SQL，不会出现 `false`/`true` 字面量错误。

---

## 4. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | `findDistinctSubjectByIsDeletedAndSubjectIsNotNullOrderBySubject` 方法名过长导致 Spring Data 解析失败 | E6 方法无法生成 | 低 | Spring Data 对 Distinct + OrderBy 组合有良好支持；若解析失败则保留 `@Query`，归入"复杂查询"范畴 |
| R2 | 调用方需新增 `Integer isDeleted` 参数（传 0）和 `FileStatus status` 参数（传 DELETING），Service 层出现硬编码的 0 / FileStatus.DELETING | 代码中散布魔法值 | 高 | 在 Repository 接口中定义常量：`int NOT_DELETED = 0` + `FileStatus EXCLUDE_STATUS = FileStatus.DELETING`。Service 层引用常量而非字面量 |
| R3 | 重构后方法名与旧名不一致，未更新全部调用方导致编译失败 | 编译错误 | 中 | IDEA "Find Usages" + 全局搜索逐条确认；编译即验证 |
| R4 | 方法名派生生成的 SQL 与原始 `@Query` JPQL 语义不完全等价（如隐式 JOIN、缓存策略差异） | 查询结果不一致 | 低 | 运行完整集成测试套件（`mvn test`），对比重构前后关键查询的 SQL 日志；Hibernate 6.x 方法名派生与等价 JPQL 应生成相同 SQL |
| R5 | 长期债务：方法名随条件增加而持续变长，最终超过可读性阈值 | 可维护性下降 | 中 | 当方法名超过 ~80 字符或条件超过 4 个时，触发 v2 重构（引入 `@Query` 或 Specification）。当前最长 67 字符，留有余量 |

---

## 5. 不在范围

- 将 `isDeleted` 从 Integer 改为 Boolean（见 D2 决策）
- 引入 `JpaSpecificationExecutor` 处理动态查询（v2）
- 将 `Object[]` 投影替换为 DTO 投影（v2）
- 引入 `@EntityGraph` 优化 N+1（v2）
- 升级 Spring Boot 3.3.5 → 3.3.x 最新版（独立 change 评估）
- 修改数据库表结构或 `init.sql`

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

本 change 无新增可复用抽象。仅修改既有 Repository 接口的方法签名。

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| Repository 方法命名规范 | 方法名不含 DO/Entity 后缀；使用属性名（非 IsFalse）表达 Integer 字段过滤；方法名包含所有查询条件 | 所有新增或重构的 Spring Data JPA Repository | 低——未来若改为 Boolean 类型，可批量替换为 IsFalse 后缀 |

### 9.3 新增 / 修改的跨模块契约

N/A — 纯 L3 Repository 内部重构，L1/L2 API 契约不变。

### 9.4 新增 / 升级的依赖

N/A — 不新增依赖，不升级 Hibernate（D1）。

### 9.5 禁动清单变化

```
- 新增建议：infrastructure/mysql/file/repository/ 下的 Repository 方法名必须符合 Spring Data 规范（无 DO 后缀，属性名完整）
```

---

> 本文件不包含完整代码实现。函数签名、伪代码、接口定义可以；函数体不行。