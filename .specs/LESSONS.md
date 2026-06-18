# LESSONS — 跨任务失败知识库

> 项目级常驻文件，位置：`.specs/LESSONS.md`
> **每个新任务开工前必扫**（R1.8）；任务完成后按本文件末尾的「提名条件」决定是否新增。

---

## 标签索引

- `arch` 架构决策类
- `lib` 第三方库选型 / 陷阱
- `tool` 构建 / 测试 / 工具链
- `data` 数据建模 / 迁移
- `perf` 性能
- `a11y` 可访问性
- `sec` 安全
- `ux` 交互 / 视觉
- `ops` 部署 / 运维
- `proc` 流程 / 协作

---

## 条目区

### L-001 · Neo4j GDS 2.x 关系投影通配符语法差异

- **标签**: `lib` `data`
- **关键词**: GDS 2.x, Neo4j 5, graph projection, wildcard, Cypher
- **适用栈**: Java + Spring Data Neo4j + GDS 2.x
- **状态**: active
- **日期**: 2026-06-17
- **来源**: `graph-metrics` DEV/UAT

**问题**：GDS 2.x（Neo4j 5.x 配套）的 `gds.graph.project()` 语法与 GDS 1.x 有差异。在关系投影中，映射格式 `{'*': {orientation: 'NATURAL'}}` 不被接受（报错 `Invalid input '*'`）。

**根因**：GDS 2.x 的关系投影 Map 中不支持 `'*'` 作为 key。通配所有关系类型时必须使用字符串 `'*'`（非 Map 格式）。

**正确写法**：
```cypher
-- GDS 2.x 正确：字符串 '*' 表示所有关系类型
CALL gds.graph.project('graph', ['*'], '*')

-- GDS 2.x 指定具体类型 + 方向：
CALL gds.graph.project('graph', ['Label1'], {TYPE1: {orientation: 'NATURAL'}})

-- ❌ GDS 2.x 错误：Map 中不能使用 '*' key
CALL gds.graph.project('graph', ['*'], {'*': {orientation: 'NATURAL'}})
```

**影响范围**：所有使用 Neo4j GDS 2.x 进行图投影的代码。`nodeProjection` 中 `['*']` 仍正常。

**规避方法**：在 GdsAdapter 的 `buildRelationshipProjection()` 中，当 `edgeTypes` 为空时返回字符串 `'*'` 而非 Map。

**检测方式**：在 Cypher shell 中直接测试 `CALL gds.graph.project('test', ['*'], {'*': {orientation: 'NATURAL'}})` — GDS 2.x 会报语法错误。

**相关文件**: `infrastructure/neo4j/gds/GdsAdapter.java:buildRelationshipProjection()`