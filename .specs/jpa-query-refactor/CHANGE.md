# CHANGE: Spring Data JPA 方法名派生查询重构

- **Change ID**: `jpa-query-refactor`
- **创建日期**: 2026-06-19
- **路径建议**: 中等
- **状态**: draft

---

## Why（为什么做）

当前 `TextbookRepository`（6 条）和 `ExamRecordRepository`（8 条）大量使用显式 `@Query` JPQL 注解，而 `FusionLogRepository` 和 `QueryTaskRepository` 已全量使用 Spring Data JPA 方法名派生查询。核心矛盾是：

1. **Hibernate 6.5 Boolean/TINYINT 谓词 bug**：之前为避免 `findByIsDeletedFalse()` 生成错误 SQL，统一采用 `@Query("... WHERE d.isDeleted = 0")` 规避（见 `.specs/CONTEXT.md` § 已锁技术决策）
2. **冗余 SQL**：大量简单查询（等值匹配 + `isDeleted` 过滤）手写了本质上是方法名派生的 JPQL，增加维护成本
3. **命名不一致**：`findExamRecordDOByStudentNoAndIsDeleted` 混入 `DO` 后缀，与 Spring Data 规范不符；`findByIsDeletedFalse` 实际还有 `status <> 'DELETING'` 条件，命名未反映全貌

## What（做什么）

1. **升级 Hibernate 版本**：在 `pom.xml` 中覆盖 `<hibernate.version>`，升级到修复 Boolean/TINYINT 谓词 bug 的版本，使 `findByIsDeletedFalse()` 等方法名派生查询正常工作
2. **替换简单 JPQL**：将 10 条简单等值匹配 + `isDeleted` 过滤的 `@Query` 替换为 Spring Data JPA 方法名派生查询，使方法名完整反映查询意图
3. **统一命名规范**：去掉方法名中的 `DO`/`Entity` 后缀，修正 `NotDeleted` → `IsDeletedFalseAndStatusNotDeliting` 等不精确命名
4. **保留复杂 JPQL**：涉及 `LIKE` + `NULL` 可选参数（`findByConditions`）、`Object[]` 投影 + `GROUP BY`（`findDistinctExams`）、`Object[]` 投影 + `DISTINCT`（`findStudentByName`/`findStudentByNo`）的 4 条查询保留 `@Query`，不做强制替换

## 影响面

- [x] 影响 `pom.xml` — Hibernate 版本升级（禁动清单豁免，本次一并处理）
- [ ] 影响 `REQUIREMENT.md` — 无需，仅内部重构
- [x] 影响 `DESIGN.md` — 需记录：① Hibernate 升级版本与兼容性验证 ② 方法名映射表 ③ `isDeleted` 字段类型变更（Integer → Boolean）决策
- [ ] 影响现有 AC — 无，所有 Repository 方法签名在重构后仍保持相同查询语义
- [ ] 影响数据模型 / 迁移 — 无
- [ ] 影响外部 API 兼容性 — 无，L1/L2 调用方无感知

## 范围排除（这次不做）

- **不重构复杂 JPQL**：`findByConditions`（NULL 可选参数）、`findStudentByName`/`findStudentByNo`（Object[] 投影）、`findDistinctExams`（GROUP BY + Object[] 投影）保留 `@Query`，等后续有需要再引入 `Specification` 或 DTO 投影
- **不重构 `FusionLogRepository` / `QueryTaskRepository`**：这两个已全量使用方法名派生，无需改动
- **不涉及 Neo4j `GraphNodeRepository`**：纯 Cypher 查询，不在 Spring Data JPA 范畴
- **不引入 `JpaSpecificationExecutor`**：本次仅做方法名派生替换，不使用动态查询规范

## 验收线（粗粒度，不是 AC）

1. **Hibernate 升级后 Boolean 查询正常**：`findByIsDeletedFalse()` 生成的 SQL 中 `is_deleted = 0`（而非错误谓词），所有现有集成测试通过
2. **方法名完整反映查询语义**：如 `findByIsDeletedFalse` 实际还过滤 `status <> 'DELETING'` → 重构后方法名包含 `AndStatusNot`，读代码即可理解完整查询条件
3. **调用方零改动**：L2 Service 层所有 Repository 方法调用的参数和返回值语义不变，仅 Receiver 端方法名变化

## 风险与未知

1. **pom.xml 禁动清单冲突**：CONTEXT.md 规定 `pom.xml` 变更需走独立 change 评估。本次用户已确认一并处理（Q1:B），视为豁免
2. **Hibernate 版本选择**：需要调研 Spring Boot 3.3.x 兼容的最新 Hibernate 6.5.x/6.6.x 版本（需在 DESIGN 阶段确认），升级后可能影响其他 `@Query` 的 SQL 生成行为
3. **`isDeleted` 字段类型**：当前实体使用 `Integer isDeleted`（0/1），Spring Data JPA `IsFalse` 方法名派生对 Integer 字段的处理需验证。若需改为 `Boolean`，会连带到 DO 实体类变更
4. **`findByIsDeletedFalse` 现有调用方**：方法语义实际包含 `status <> 'DELETING'` 过滤，但方法名只提了 `isDeleted`。重构后方法名变长（如 `findByIsDeletedFalseAndStatusNot`），调用方参数也需增加 `FileStatus` 参数——这实际是**调用方改动**。需在 DESIGN 阶段确认：① 是接受长方法名 ② 还是拆成两条查询 ③ 还是接受调用方需传额外参数

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。