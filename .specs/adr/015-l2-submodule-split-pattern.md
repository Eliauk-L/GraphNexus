# ADR-015: L2 模块子域拆分模式

- **日期**: 2026-06-17
- **Change**: `package-restructure`

## Context

随项目代码量增长（当前 216 个 Java 文件），`application/graph/`（49 文件）、`application/document/`（20 文件）、`application/query/`（11 文件）等 L2 模块内部类堆叠，职责边界模糊。

以 `graph/` 为例，`extraction/` 是扁平结构，而 `fusion/` 和 `metrics/` 已有 `config/model/service/strategy` 标准子包，`model/` 和 `service/` 又挂在 graph 层级做共享，子模块间结构不一致、边界不清。

## Decision

大模块按功能子域拆分为 3-4 个子模块，每个子模块含标准骨架（`service/` + `service/impl/` + `model/`）+ 按需扩展包。具体：

```
file/       → upload/ + parse/ + core/
graph/      → core/ + construction/ + fusion/ + metrics/
query/      → chat/ + prompt/ + conversation/
```

扩展包规则（沿用 `docs/package-structure-spec.md` §3.2）：
- 命名用单数名词（`parser`、`strategy`、`event`）
- 接口与实现同包
- 新增扩展子包需在对应 change 的 DESIGN.md 中声明

## Consequences

- **正**：每个子包 ≤ 15 个类，职责边界清晰，新人可快速定位
- **正**：新增功能子域按模板创建骨架即可，保持一致性
- **正**：`upload/` 和 `conversation/` 等预留子模块降低未来启动成本
- **负**：包路径更深（如 `application.file.parse.parser.mineru.client`，7 层）
- **负**：跨子模块 import 增多（如 core 引用 parse 的 BO）
- **负**：未来新增模块需遵循同样拆分逻辑，有一定设计成本