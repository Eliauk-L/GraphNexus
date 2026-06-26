# ADR-040: 全端点权限收敛方案与端点-角色映射矩阵

- **状态**: accepted
- **日期**: 2026-06-22
- **决策者**: AI（Architect 角色）+ 人工 review
- **关联**: `@.specs/user-auth-rbac/REQUIREMENT.md` 端点-角色映射矩阵、`@.specs/user-auth-rbac/DESIGN.md` D10/D11

---

## Context

当前所有 `/api/v1/**` 端点无任何鉴权保护。本次 change 需要为所有现有端点加角色权限控制，同时确保：
1. 认证端点（login/refresh/logout）对所有用户开放
2. 不同角色按用例视图的用例分配获得对应的端点访问权
3. 权限注解方式可维护、可扩展（新增端点时容易遵循规则）

备选方案：
- A：SecurityFilterChain 中配置 URL 级别拦截（`.antMatchers("/api/v1/file/**").hasRole("ADMIN")`）
- B：Controller 方法级 `@PreAuthorize` 注解
- C：自定义 `@RequiresRole` 注解 + AOP

## Decision

### 选择 B：类级别 + 方法级别 `@PreAuthorize` 组合

**模式**：
- Controller 类上标注**默认角色**（该 Controller 大多数方法的权限）
- 个别方法需要更严格/更宽松权限时，在方法上覆盖

**示例**：
```java
@RestController
@RequestMapping("/api/v1/file/textbooks")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")  // 类级默认
public class TextbookController {

    @PostMapping("/upload")
    // 继承类级 hasAnyRole('ADMIN','TEACHER')

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")  // 方法级收紧：仅 ADMIN 可删除
    public ApiResult<Void> delete(@PathVariable Long id) { ... }
}
```

### 为什么不用 URL 级别拦截（方案 A）

1. 同一 Controller 内不同方法需要不同权限（如 TextbookController：查询 TEACHER 可看，删除仅 ADMIN）
2. URL 模式匹配粒度不足（`/api/v1/file/textbooks/{id}` 的 DELETE 和 GET 需要不同权限）
3. URL 规则集中写在 SecurityConfig 中，新增端点容易遗漏

### 为什么不用自定义注解（方案 C）

Spring Security 的 `@PreAuthorize` 已经足够表达 AND/OR/ROLE 组合逻辑，自定义注解增加维护成本且新人需要额外学习。

### ADMIN 继承 TEACHER 在注解中的表现

由于 ADR-038 中 ADMIN 自动获得 `ROLE_ADMIN` + `ROLE_TEACHER` 两个 authority：
- `hasRole('TEACHER')` → ADMIN 和 TEACHER 均通过
- `hasRole('ADMIN')` → 仅 ADMIN 通过
- 无需写 `hasAnyRole('ADMIN','TEACHER')` 来表达继承，注解更简洁
- **例外**：显式写 `hasAnyRole('ADMIN','TEACHER')` 作为**文档意图**也是可接受的

### 完整端点-角色映射

> ADMIN 继承 TEACHER，表中 TEACHER 列 = ✅ 时 ADMIN 自动通过。

| Controller / 端点 | 类级默认角色 | 方法级例外 |
|---|---|---|
| **AuthController** `/api/v1/auth` | permitAll | `GET/POST /users` → `hasRole('ADMIN')` · `PUT /users/{id}` → `hasRole('ADMIN')` · `GET /roles` → `authenticated` |
| **TextbookController** `/api/v1/file/textbooks` | `hasRole('TEACHER')` | `POST /upload` → 继承 · `POST /parse/{id}` → `hasRole('ADMIN')` · `DELETE /{id}` → `hasRole('ADMIN')` |
| **GradeController** `/api/v1/file/grades` | `hasRole('TEACHER')` | `POST /upload` → 继承 · `DELETE /exam/{examNo}` → `hasRole('ADMIN')` |
| **ConstructionController** `/api/v1/graph/construction` | `hasRole('TEACHER')` | `POST /extract/{documentId}` → `hasRole('ADMIN')` |
| **MetricsController** `/api/v1/graph/metrics` | `hasAnyRole('ADMIN','OPS_STAFF','OPS_MANAGER')` | 无例外 |
| **FusionController** `/api/v1/analysis/fusion` | `hasAnyRole('ADMIN','OPS_STAFF')` | `POST /execute` → 继承 · `POST /rollback/{id}` → `hasRole('ADMIN')` |
| **AnalysisController** `/api/v1/analysis` | `hasRole('TEACHER')` | 无例外（仅 `GET /subgraph/{taskId}`，STUDENT 方法级加 `hasAnyRole('ADMIN','TEACHER','STUDENT')`） |
| **QueryController** `/api/v1/query` | `hasRole('TEACHER')` | `POST /chat` → `hasAnyRole('ADMIN','TEACHER','STUDENT')` · `GET /result/{taskId}` → `hasAnyRole('ADMIN','TEACHER','STUDENT')` · `DELETE /history/{taskId}` → `hasRole('ADMIN')` |
| **LlmController** `/api/v1/llm` | `hasAnyRole('ADMIN','OPS_STAFF')` | 无例外 |

## Consequences

### 优势
- 权限规则与 Controller 代码共存，新增方法时自然看到类级权限，不易遗漏
- 方法级覆盖灵活，同一 Controller 内的权限差异一目了然
- Spring Security 原生机制，无额外框架依赖

### 代价
- Controller 代码增加注解噪音（类级 + 零星方法级）
- 权限变更需改代码重新部署（v1 可接受，无运行时改权限需求）
- 需额外测试确保注解无遗漏（通过"权限回归测试类"）

### 排除了什么
- **动态权限配置（数据库存 URL-角色映射）**：v1 无运行时改权限需求，数据库中存的方案引入额外复杂度，且 URL 模式匹配在 SpEL 中比 SQL LIKE 更可靠
- **权限注解放在 Service 层**：Service 方法可能被多个 Controller 或内部调用，注解在 Controller 层更清晰