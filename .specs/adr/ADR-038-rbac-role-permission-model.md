# ADR-038: RBAC 角色权限模型 — 5 类角色 + 继承 + 预置

- **状态**: accepted
- **日期**: 2026-06-22
- **决策者**: AI（Architect 角色）+ 人工 review
- **关联**: `@.specs/user-auth-rbac/DESIGN.md` D5/D6、`@docs/design-view/use-case-view/use-case-view.md` §1.2

---

## Context

系统需要 5 类角色：管理员（ADMIN）、教师（TEACHER）、学生（STUDENT）、运维人员（OPS_STAFF）、运营人员（OPS_MANAGER）。用案视图 §1.3 定义 ADMIN 泛化继承 TEACHER 全部用例，其余角色为独立参与者。

需要决定：
1. 角色是预置（代码/DDL 硬编码）还是完全动态（数据库可任意增删）？
2. 用户-角色关系：一对一还是多对多？
3. 角色继承如何实现？

## Decision

### 数据模型

```
user_account ──< user_role >── role

- user_account: id, username, password(BCrypt), real_name, status(ENABLED/DISABLED), create_time, update_time
- role: id, code(ADMIN/TEACHER/STUDENT/OPS_STAFF/OPS_MANAGER), name(中文), description
- user_role: id, user_id(FK), role_id(FK), UNIQUE(user_id, role_id)
```

### 角色预置策略

- `role` 表 5 行通过 `data.sql` 在启动时自动初始化（使用 `INSERT IGNORE` 或 `ON DUPLICATE KEY` 保证幂等）
- 不支持运行时新增/删除角色（v1）
- 用户可拥有**多个角色**（多对多），如 ADMIN + TEACHER

### 角色继承实现

ADMIN 继承 TEACHER 的实现方式：

**在权限授予层面**：管理员用户被分配 `ADMIN` 角色时，系统自动同时授予 `ROLE_ADMIN` 和 `ROLE_TEACHER` 两个 `GrantedAuthority`。

实现位置：`UserPrincipal` 构建时，检查 roles 集合 → 若含 `ADMIN` → 自动追加 `ROLE_TEACHER` 到 authorities。

**不在注解层面实现继承**：`@PreAuthorize` 中只需写 `hasRole('TEACHER')` 即可同时放行 ADMIN 和 TEACHER，无需到处写 `hasAnyRole('ADMIN','TEACHER')`。

### Spring Security GrantedAuthority 映射

| 角色 code | Spring Security Authority |
|-----------|--------------------------|
| ADMIN | `ROLE_ADMIN` + `ROLE_TEACHER`（继承） |
| TEACHER | `ROLE_TEACHER` |
| STUDENT | `ROLE_STUDENT` |
| OPS_STAFF | `ROLE_OPS_STAFF` |
| OPS_MANAGER | `ROLE_OPS_MANAGER` |

> Spring Security 的 `hasRole('ADMIN')` 会自动查找 `ROLE_ADMIN` 前缀。

## Consequences

### 优势
- 角色继承逻辑集中在一处（`UserPrincipal` 构建），`@PreAuthorize` 注解简洁
- 角色预置 + 代码枚举保证一致性，不会出现数据库中存在的角色但代码不认识的漂移问题
- 多对多支持灵活：运维人员可同时有 OPS_STAFF + OPS_MANAGER 角色

### 代价
- 新增角色需改 4 处：`RoleDO` 枚举 / `data.sql` / `@PreAuthorize` 矩阵 / 前端菜单过滤逻辑
- 角色继承隐式在代码中，排查权限时需知道 ADMIN 自动拥有 TEACHER 权限
- 不支持运行时新增角色（但 v1 无此需求）

### 排除了什么
- **完全动态角色**：运行时增删角色 + 角色管理 UI + 权限配置 UI。v1 不需要，且会引入复杂的角色-权限映射管理
- **角色继承作为数据库关系**（`role.parent_role_id`）：过度设计，v1 仅 ADMIN→TEACHER 一对继承关系