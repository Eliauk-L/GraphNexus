# ADR-014: 根包全小写命名

- **日期**: 2026-06-17
- **Change**: `package-restructure`

## Context

项目当前根包名为 `com.GraphNexus`，含大写字母 G 和 N。`docs/项目规范.md` §1.4.1 明确规定「包名全小写，点分隔，单数形式」，示例为 `com.graphnexus.infrastructure.neo4j`。当前命名违反项目自身规范 + Java 社区惯例（JLS §6.1）。

## Decision

强制根包名改为 `com.graphnexus`（全小写）。所有子包继承此命名。

## Consequences

- **正**：对齐项目规范 + Java 惯例，IDE 和工具链无告警
- **正**：消除 macOS 大小写不敏感文件系统上 `com/GraphNexus` 与 `com/graphnexus` 并存的潜在冲突
- **负**：全部 216 个 Java 文件的 package + import 声明需修改，约 1500+ 行变更
- **负**：`git log --follow` 可能无法追踪个别文件的完整历史
- **负**：需同步检查并修正 yml 配置、Spring 注解中的硬编码包名引用