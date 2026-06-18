# ADR-003: Feature-based 项目结构

- **日期**: 2026-06-18
- **Change**: `frontend-ui`
- **状态**: accepted

---

## Context

GraphNexus 前端有 6 个功能模块（文件管理、图谱可视化、成绩管理、智能问答、融合管理、图指标），每个模块有独立的页面、组件、状态、API 调用。需要决定源码目录组织方式。

候选方案：
1. **Feature-based** — `src/features/<module>/` 下聚集该模块的所有文件（page + components + store），跨模块共享放 `src/shared/`
2. **Layer-based** — 按技术层分：`src/pages/`、`src/components/`、`src/stores/`、`src/api/`

## Decision

**选择 Feature-based 结构**。

## Consequences

### 正面

- 6 个模块独立性强，feature-based 让每个模块的开发者只需关注一个目录
- 新增模块只需新建 `src/features/<new-module>/` 目录，不触碰已有代码
- Code review 时变更范围天然限定在对应 feature 目录内
- 模块可被独立删除/重构而不影响其他模块

### 负面

- 跨模块共享的组件/工具需要显式放在 `src/shared/`，开发者需要判断"这是共享的还是模块私有的"
- 初期可能过度拆分：一个组件本应共享却被放在某个 feature 内

### 缓解

- 规则：组件被 ≥2 个 feature 使用时，提升到 `src/shared/components/`
- API 函数（`src/api/`）不按 feature 分，按后端 Controller 分组（file/graph/query/analysis），因为它们跨 feature 可能被多个 store 调用
- `src/shared/` 目录在 code review 中标记为"改这里影响全局"，需额外注意