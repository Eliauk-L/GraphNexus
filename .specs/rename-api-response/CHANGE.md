# CHANGE: 重命名系统响应体 ApiResponse，消除与 Swagger 注解的命名冲突

- **Change ID**: `rename-api-response`
- **创建日期**: 2026-06-17
- **路径建议**: 最短
- **状态**: draft

---

## Why（为什么做）

系统自定义响应体 `com.graphnexus.common.ApiResponse<T>` 与 Swagger/OpenAPI 注解 `io.swagger.v3.oas.annotations.responses.ApiResponse` 同名。当前在 6 个 Controller 中被迫使用全限定名 `@io.swagger.v3.oas.annotations.responses.ApiResponse(...)` 来避免编译歧义，代码啰嗦且 import 容易写错。

## What（做什么）

将系统响应体 `com.graphnexus.common.ApiResponse<T>` 重命名为 `ApiResult<T>`（或 DESIGN 阶段最终确定的名称），更新所有引用处（6 个 Controller + 1 个集成测试），以及 `OpenApiConfig` 和 `.specs/CONTEXT.md` 中的文档性引用。

重命名后，Swagger 的 `@ApiResponse` 可通过简洁的 `import io.swagger.v3.oas.annotations.responses.ApiResponse` 导入，不再需要全限定名。

## 视觉调性（前端项目必填）

（非前端项目，跳过）

## 影响面

- [ ] 影响 `REQUIREMENT.md`（不涉及需求变更）
- [ ] 影响 `DESIGN.md` / 引入新 ADR（不涉及架构决策）
- [ ] 影响现有 AC
- [ ] 影响数据模型 / 迁移
- [x] 影响外部 API 兼容性 — **仅 Java 类名变更，REST API JSON 结构不变（字段 code/message/data/traceId/timestamp 保持不变）**
- [ ] 仅修复 bug，无范围变化

## 范围排除（这次不做）

- 不修改 `ApiResponse` 的 JSON 字段结构（code/message/data/traceId/timestamp 保持不变）
- 不清理 Controller 中与本次重命名无关的代码
- 不动 Swagger 注解 `@ApiResponses` / `@ApiResponse` 的逻辑或描述内容
- 不新增或修改任何业务逻辑

## 验收线（粗粒度，不是 AC）

- `com.graphnexus.common.ApiResponse` 类不存在，已重命名为目标名称（如 `ApiResult`）
- 所有 6 个 Controller 的返回类型和 import 使用新类名，编译通过
- 所有 Controller 文件中的 Swagger `@ApiResponse` 注解使用简洁 import（不再用全限定名）
- 现有测试全部通过，无回归

## 风险与未知

- **类名最终选择**：用户倾向 `Result` / `ApiResult` 方向，具体名称由 DESIGN 阶段确认。需注意 `Result` 可能与 `java.sql.ResultSet` 等在 import 时产生新的歧义，需在 DESIGN 阶段评估
- **Spring 容器扫描**：若存在通过类名字符串反射引用的地方（如 SpEL 表达式），需一并更新（grep 排查）

---

> 后续 AC 与设计细节进入 `REQUIREMENT.md` / `DESIGN.md`，本文件不再扩展。