# ADR-030: 历史查询 JPA Specification — 动态条件查询方案

- **状态**: accepted
- **日期**: 2026-06-22
- **Change**: `diagnosis-history-export`
- **关联**: DESIGN §1 D2

---

## Context

历史诊断记录查询需支持 6 个可选筛选参数：

| 参数 | 匹配方式 | 说明 |
|------|---------|------|
| `studentName` | LIKE 模糊 | 学生姓名 |
| `studentNo` | = 精确 | 学号 |
| `subject` | = 精确 | 学科 |
| `status` | = 精确 | COMPLETED / FAILED |
| `startDate` | >= | 查询起始时间 |
| `endDate` | <= | 查询结束时间 |

全部可选 = 2^6 = 64 种参数组合。Spring Data JPA 的方法名派生不支持"参数为 null 时忽略条件"，`@Query` JPQL 拼接 `WHERE (:param IS NULL OR field = :param)` 可行但 6 个参数会导致 JPQL 冗长难读。

## Decision

`QueryTaskRepository` 扩展 `JpaSpecificationExecutor<QueryTaskDO>`，在 Service 层构建 `Specification<QueryTaskDO>` 动态 where 链。

```java
// 接口声明
public interface QueryTaskRepository extends JpaRepository<QueryTaskDO, Long>,
        JpaSpecificationExecutor<QueryTaskDO> { ... }

// Service 层构建
private Specification<QueryTaskDO> buildSpec(HistoryQueryRequest req) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        if (req.studentName() != null) predicates.add(cb.like(root.get("studentName"), "%" + req.studentName() + "%"));
        if (req.studentNo() != null)   predicates.add(cb.equal(root.get("studentNo"), req.studentNo()));
        // ... subject, status, startDate, endDate
        query.orderBy(cb.desc(root.get("createTime")));
        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

## Consequences

- **正面**：① 条件组合灵活，新增筛选参数只需加一行 predicate；② 遵循 Spring Data JPA 标准模式，团队学习成本低；③ Repository 声明式接口不改已有查询方法。
- **负面**：① 项目首次引入 `JpaSpecificationExecutor`，无既有先例可抄；② Specification 的 where 链是程序化构建，不像 `@Query` 注解那样一眼看清完整 SQL；③ 需额外编写 `@DataJpaTest` 验证每个筛选参数独立生效。
- **长期**：后续需要动态筛选的其他 Repository（如 `document` 表的复合条件查询）可参照此模式，形成项目级惯例。