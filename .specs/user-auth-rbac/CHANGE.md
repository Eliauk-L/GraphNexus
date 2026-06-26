# CHANGE: 用户认证与角色权限管理

- **Change ID**: `user-auth-rbac`
- **创建日期**: 2026-06-22
- **路径建议**: 完整（REQUIREMENT → DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION）
- **状态**: draft

---

## Why（为什么做）

当前系统所有 API 端点均无鉴权保护（Spring Security 被 `autoconfigure.exclude` 显式排除），任何能访问服务器的人都能操作全部功能。用例视图已定义 5 类角色（管理员/教师/学生/运维/运营）及 UC-20「管理用户角色与权限」，User Story 1.9 也已规划管理员管理用户角色的能力。技术栈（Spring Security 6.x + JWT + Redis 7.x）早已锁定，`textbook.uploaded_by` 字段预留了 `FK→user_account.id` 外键——基础设施层面的准备工作就绪，仅待实现。

一句话：**V1 跳过鉴权的阶段已经过去，系统需要知道"谁在操作"并据此控制"能做什么"。**

## What（做什么）

1. **认证基础设施**：启用 Spring Security，实现 JWT 双 Token（Access 30min + Refresh 7d）认证链路，用户登录/登出/Token 刷新
2. **用户-角色数据模型**：MySQL 新建 `user_account` + `role` + `user_role` 表，5 类角色（超级管理员/教师/学生/运维人员/运营人员），管理员泛化继承教师权限
3. **Redis 缓存层**：添加 Redis 依赖与配置，缓存用户信息 + 角色权限（TTL 可配置），用户信息变更时主动失效
4. **全端点权限收敛**：为所有现有 REST API 端点标注角色权限注解，各角色按用例视图定义的用例范围获得对应访问权
5. **前端认证闭环**：登录页（极简风格）、路由守卫（未登录跳转登录页、无权限跳转 403）、Token 自动刷新、用户管理页（管理员 CRUD 用户 + 分配角色）

## 视觉调性

- **选定**：极简（Minimal）— 参考 Linear/Vercel/Stripe
- **理由**：项目既有调性，登录页和用户管理页保持一致，不引入新风格
- **参考产品**：Linear、Vercel、Stripe
- **明确排除**：Material Design（过于厚重）、Neumorphism（与现有风格冲突）

> 此选择会被 `2a-ui-design.md` 继承（若本次涉及登录页/用户管理页的 UI 设计）。

## 影响面

- [x] 影响 `REQUIREMENT.md`（新增认证 + 权限需求 + AC）
- [x] 影响 `DESIGN.md` / 引入新 ADR（JWT 方案、角色模型、Redis 缓存策略、Spring Security 配置）
- [x] 影响现有 AC（全端点加权限注解 = 所有现有 API 测试需携带 Token）
- [x] 影响数据模型 / 迁移（新增 `user_account`、`role`、`user_role` 三张表 + DDL）
- [x] 影响外部 API 兼容性（破坏性：所有端点从此需要 Authorization header）
- [x] 影响 `pom.xml`（新增 `spring-boot-starter-data-redis` + `jjwt` 等依赖）
- [x] 影响前端路由（新增 `/login` 路由 + 全局路由守卫 + 菜单按角色渲染）

## 范围排除（这次不做）

- **不实现公开注册流程**：用户由超级管理员在后台手动创建
- **不实现忘记密码/重置密码**：v1 仅支持登录后修改密码
- **不实现 OAuth/SSO 第三方登录**：仅用户名+密码
- **不实现细粒度操作权限**：按钮级/数据行级权限控制不在本 change，仅做 API/页面级角色控制
- **不实现数据级权限隔离**：如"教师只能看自己班级学生"——这是后续 change 的事，本次仅区分角色能访问哪些端点/页面
- **不实现用户自助注册 + 审批流程**

## 验收线（粗粒度，不是 AC）

1. **认证闭环**：未登录用户访问任何页面 → 自动跳转 `/login`；登录成功后回到目标页；Token 过期自动刷新或跳转登录页
2. **角色区分**：不同角色登录后看到不同侧边菜单（如学生看不到「文件管理」「融合管理」），直接访问无权限 URL 返回 403
3. **用户管理**：超级管理员可在「用户管理」页面创建/编辑/禁用用户，为用户分配一个或多个角色
4. **Redis 缓存**：登录后用户信息 + 权限缓存到 Redis，后续请求不查 MySQL；用户被禁用/角色变更后缓存即时失效

## 风险与未知

- **全端点权限收敛**波及面大：需要为 6 个 Controller 模块的所有端点逐一标注角色，DESIGN 阶段需产出完整的「端点-角色映射矩阵」
- **测试回归**：所有现有集成测试需要携带有效 Token，测试基础设施需新增 `@WithMockUser` 或 Test Security 配置
- **pom.xml 变更**：新增 Redis starter 可能引入自动配置冲突，需在 DESIGN 阶段评估
- **Token 刷新 UX**：前端需实现无感刷新逻辑（axios interceptor），避免用户使用中途被踢出
- **Neo4j 不受 Spring Security 保护**：Neo4j bolt 协议不走 HTTP 过滤器，需确认 Neo4j 自身认证已足够

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。