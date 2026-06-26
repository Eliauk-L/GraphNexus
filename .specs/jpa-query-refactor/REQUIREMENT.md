# REQUIREMENT: Spring Data JPA 方法名派生查询重构

- **Change ID**: `jpa-query-refactor`
- **关联**: `@.specs/jpa-query-refactor/CHANGE.md`、`@.specs/CONTEXT.md`

---

## 用户故事

- **US-1**：作为后端开发者，我想升级 Hibernate 到修复 Boolean/TINYINT 谓词 bug 的版本，以便 `findByIsDeletedFalse()` 方法名派生查询能生成正确的 `is_deleted = 0` SQL。
- **US-2**：作为后端开发者，我想将简单等值匹配 + 逻辑删除过滤的 `@Query` JPQL 替换为 Spring Data JPA 方法名派生，以便减少手写 SQL 的维护成本，方法名即文档。
- **US-3**：作为后端开发者，我想统一 Repository 方法命名规范（去掉 DO 后缀、方法名完整反映查询条件），以便阅读接口名就能理解完整查询意图，不需要跳进 `@Query` 注解查看额外过滤条件。

## 验收准则（AC）

### AC-1 · Hibernate 升级后 Boolean 查询正常

- **Given** Hibernate 已升级到修复 Boolean/TINYINT 谓词 bug 的版本
- **When** 调用 `TextbookRepository.findByIsDeletedFalse(pageable)`（方法名派生，不含 `@Query`）
- **Then** 生成的 SQL 包含 `is_deleted = 0`（或等价的条件），且返回结果与升级前一致
- **验证方式**: 运行 `TextbookRepositoryTest` 中所有涉及 `isDeleted` 过滤的测试用例，全部通过

### AC-2 · 简单 JPQL 被方法名派生替代

- **Given** `TextbookRepository` 和 `ExamRecordRepository` 中存在 10 条简单等值匹配 + `isDeleted` + `status` 过滤的 `@Query` 方法
- **When** 重构完成后
- **Then** 这 10 条方法不再包含 `@Query` 注解，查询语义由方法名完整表达；通过相同的集成测试验证结果不变
- **验证方式**: ① grep `TextbookRepository.java` 和 `ExamRecordRepository.java`，确认目标方法无 `@Query`；② 运行 `mvn test -pl .`，所有已有测试通过

### AC-3 · 方法命名符合 Spring Data 规范

- **Given** 重构后的 Repository 接口
- **When** 审查所有自定义查询方法名
- **Then** ① 方法名中不含 `DO`/`Entity` 后缀；② 涉及 `isDeleted = 0` 过滤的方法使用 `IsDeletedFalse` 前缀；③ 涉及 `status <> 'DELETING'` 过滤的方法使用 `StatusNot` 前缀；④ 含有排序的方法使用 `OrderByXxxAsc/Desc`
- **验证方式**: 人工 code review，对照 Spring Data JPA 官方文档的「Query Creation」章节检查命名合法性

### AC-4 · 复杂 JPQL 保持不变

- **Given** `ExamRecordRepository` 中 `findStudentByName`、`findStudentByNo`、`findDistinctExams` 和 `TextbookRepository` 中 `findByConditions` 共 4 条涉及 `LIKE` + NULL 可选参数 / `Object[]` 投影 / `GROUP BY` 的查询
- **When** 重构完成后
- **Then** 这 4 条方法保留 `@Query` 注解，JPQL 内容不变
- **验证方式**: grep 确认这 4 条方法仍有 `@Query` 注解

### AC-5 · 调用方无需修改查询语义

- **Given** L2 Service 层（`TextbookServiceImpl`、`GradeServiceImpl` 等）调用 Repository 方法的位置
- **When** 方法签名因参数增加（如增加 `FileStatus` 参数）或方法名变化而更新后
- **Then** 每次调用的查询语义（返回结果集）与重构前一致，所有集成测试通过
- **验证方式**: 运行完整测试套件 `mvn test`，0 失败

---

## 范围切分

### v1（本次必做）

1. 升级 Hibernate 版本（`pom.xml` 中覆盖 `<hibernate.version>`，禁动清单豁免）
2. `TextbookRepository`：5 条简单 `@Query` → 方法名派生
3. `ExamRecordRepository`：5 条简单 `@Query` → 方法名派生 + 1 条已有方法名改名
4. 统一方法命名规范（去 DO 后缀、补 `AndStatusNot` 条件）
5. 更新调用方（L2 Service）适配新方法签名
6. 更新 `CONTEXT.md` 中的「DocumentRepository JPQL」决策条目
7. 保持 `init.sql` 不变（数据库表结构无改动）

### v2（下一轮考虑，不本次）

- 将 `findByConditions` 的 NULL 可选参数逻辑重构为 `JpaSpecificationExecutor` 动态查询
- 将 `findStudentByName` / `findStudentByNo` / `findDistinctExams` 的 `Object[]` 投影改为 DTO 投影
- 引入 `@EntityGraph` 优化 N+1 查询

### out（永远不做）

- 重构 Neo4j `GraphNodeRepository`（非 JPA，不适用）
- 重构 `FusionLogRepository` / `QueryTaskRepository`（已全量方法名派生）
- 将 Repository 从接口改为抽象类
- 引入 MyBatis / jOOQ 等其他 ORM

---

## 非功能性需求

- **性能**: 无新增性能要求。方法名派生查询与等价的 `@Query` JPQL 在 Hibernate 6.x 中生成相同 SQL，不应有性能回退
- **兼容性**: ① Hibernate 升级后 Spring Data JPA、Spring Boot 3.3.x 全家族兼容；② MySQL 8.0 驱动兼容；③ 升级后所有现有集成测试通过（`@ActiveProfiles("dev")` 直连 podman）
- **可观测性**: 无新增要求。Hibernate SQL 日志级别保持 `DEBUG`，方法名派生查询的 SQL 输出方式与 `@Query` 一致
- **安全**: 无
- **可访问性**: 无

## 依赖与假设

- **依赖**: ① Spring Boot 3.3.x 对 Hibernate 6.6.x 的兼容性需在 DESIGN 阶段验证；② `ExamRecordDO.isDeleted` 当前为 `Integer` 类型，Spring Data `IsFalse` 对 `Integer` 的处理需验证
- **假设**: ① Hibernate 6.5.4+ 或 6.6.x 已修复 Boolean/TINYINT 谓词 bug；② 方法名变长（如 `findByDocumentNoAndIsDeletedFalseAndStatusNotOrderByCreateTimeAsc`）在可接受范围内，Spring Data JPA 能正确解析；③ `isDeleted` 保持 `Integer` 类型不改 `Boolean`（避免 DO 实体变更扩散范围）

---

> AC 是 TEST 阶段派生用例的唯一来源，禁止在 TEST 阶段引入新 AC。