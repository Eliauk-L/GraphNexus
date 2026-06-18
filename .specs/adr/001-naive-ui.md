# ADR-001: Naive UI 作为组件库

- **日期**: 2026-06-18
- **Change**: `frontend-ui`
- **状态**: accepted

---

## Context

GraphNexus 前端（Vue 3 SPA 管理后台）需要选择 UI 组件库，提供 Button/Input/Select/Card/Table/Modal 等基础组件。项目视觉调性为「极简」（0-change 步骤 0.6 选定）。

候选方案：
1. **Naive UI** — Vue 3 原生，TypeScript 优先，默认主题扁平/冷色调/轻阴影
2. **Element Plus** — 国内最流行，中后台首选，中文文档最全
3. **Ant Design Vue** — 企业级，组件最全面

## Decision

**选择 Naive UI 2.x**。

## Consequences

### 正面

- 默认主题天然匹配极简调性（扁平 at rest、冷蓝色调、轻阴影），定制成本最低
- TypeScript 原生支持，类型推断完整，适合 Vue 3 + TS 技术栈
- Tree-shaking 只打包用到的组件，首屏体积可控
- 内置 Data Table 组件覆盖 V1 表格需求

### 负面

- 社区规模 < Element Plus，中文教程/问答较少
- 部分复杂场景可能缺组件（如高性能虚拟表格），但 V1 不涉及
- 如果后续团队决定换组件库，迁移成本中等（需批量替换 import + props 适配）

### 缓解

- 基础组件通过 `src/shared/components/` 二次封装（BaseButton → NButton），降低直接依赖面
- 如遇到 Naive UI 无法满足的组件需求，用 Tailwind + Lucide 自研，不引入第二 UI 库