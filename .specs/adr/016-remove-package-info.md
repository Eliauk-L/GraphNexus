# ADR-016: 删除 package-info.java

- **日期**: 2026-06-17
- **Change**: `package-restructure`

## Context

项目当前有 ~67 个 `package-info.java` 文件，绝大多数仅含一行 `@NonNullApi` 注解（`org.springframework.lang.NonNullApi`），该注解实际未在项目中启用 null-safety 检查（无 `@Nullable` 注解配合使用，无 SpotBugs/NullAway 等静态分析工具）。

每次新增子包都需手工创建 `package-info.java`，且 `refine-package-structure` change 中将此作为硬性要求（`docs/package-structure-spec.md` §7 反模式：「只建空目录不写 package-info.java」）。

维护成本（67 个文件 × 每次重构移动/新增）远高于实际收益。

## Decision

全项目删除全部 `package-info.java`，不再要求每个包必须有此文件。如需包级注解，改为在关键类上声明或通过 `common/config/` 集中管理。

## Consequences

- **正**：减少 67 个样板文件，新增子包时无需额外步骤
- **正**：消除 `refine-package-structure` 产生的人为约束（「必须建 package-info.java」的反模式条目）
- **正**：删除与启用 null-safety 检查不冲突——若未来引入，可在 `common/` 下统一配置或使用 Maven 编译器参数
- **负**：失去包级 Javadoc 的规范位置。当前 project 的包文档已在 `docs/package-structure-spec.md` 中维护，不依赖 package-info.java 的 Javadoc
- **负**：若未来确实需要 `@NonNullApi`，需另选方案（如 `common/config/NonNullApiConfig.java` 或 Maven compiler args）