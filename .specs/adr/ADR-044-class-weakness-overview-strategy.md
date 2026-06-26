# ADR-044: 班级薄弱概览剪枝策略 — 批量 MASTERS + Java 聚合

## Context

`class-weakness-overview` 需要实现 `CLASS_WEAKNESS_OVERVIEW` 意图的剪枝策略。与 `STUDENT_DIAGNOSIS`（单学生 → 薄弱 KP → 依赖链）不同，班级概览需要**聚合全班学生的 MASTERS 数据**，按 KP 统计薄弱人数、平均掌握度等聚合指标。

核心问题：
1. 聚合在哪里完成？Cypher 聚合 vs Java 聚合？
2. 学生数据从哪里获取？Neo4j vs MySQL？
3. 聚合后的子图结构如何设计（节点/边的语义）？

## Decision

### D0 · 聚合在 Java 层完成（非 Cypher）

**选择**：分步 Cypher 查询 → Java Stream groupBy 聚合。

**理由**：
- 与 ADR-010"分步 Cypher + Java 合并"范式一致
- Cypher `avg()`/`count()` 对 MASTERS weight 的统计表达能力有限——需要同时计算薄弱人数（`count WHERE weight < threshold`）、平均 weight、最小/最大 weight，单一 Cypher 查询会极其复杂
- MASTERS 降级路径需要从 MySQL `exam_record` 计算原始得分率（跨存储查询），Cypher 无法完成
- Java Stream 聚合灵活且可读，班级 ≤ 60 人时 O(n) 耗时 < 50ms

**代价**：需要在应用层传输全部 MASTERS 边数据（班级 60 人 × 平均 15 KP = ~900 条边），网络传输量略大于 Cypher 聚合。但 900 条边数据量很小（< 100KB），可忽略。

### D1 · 两步查询：MySQL 查学生 → Neo4j 查 MASTERS

**选择**：
1. MySQL `exam_record` 查询班级学生列表（`DISTINCT studentNo WHERE className = ?`）
2. Neo4j 批量查询这些学生的 MASTERS 边（`WHERE s.studentNo IN $studentNos`）

**理由**：
- MySQL `exam_record` 是学生数据的权威来源（成绩上传时写入 className），有索引
- Neo4j Student 节点的 className 属性可能不存在（融合未执行时）
- MySQL 优先保证班级查询不依赖融合状态

**代价**：需 2 次跨存储查询，但单次 < 20ms，总延迟可忽略。

### D2 · 聚合子图结构：虚拟班级节点 + KP 节点 + 聚合 MASTERS 边

**选择**：子图节点包含：
- 1 个虚拟班级节点（type=ClassInfo, properties 含 className/classSize/subject）
- N 个 KP 节点（弱掌握 + 前置依赖）
- 聚合 MASTERS 边（ClassInfo → KP, weight=avgWeight, description=JSON{weakCount,totalCount,avgWeight,minWeight,maxWeight}）
- PREREQUISITE_OF 边（KP → KP，与学生诊断一致）

**理由**：
- 虚拟班级节点替代学生节点，使子图结构与 STUDENT_DIAGNOSIS 区分，前端可据此判断子图类型
- 聚合 MASTERS 边的 weight 存储平均掌握度，description JSON 存储完整聚合统计，LLM 可读取全部维度
- PREREQUISITE_OF 边处理与学生诊断一致，复用 `findPrerequisitesUpstream()`

**代价**：虚拟节点在前端子图可视化中无法渲染为有意义的节点（`DiagnosisSubgraph.vue` 预期 Student 节点）。v1 中班级子图可视化不在范围（LLM 报告中 SVG 呈现依赖链）。

## Consequences

- **正向**：班级聚合子图文本量大幅小于逐生列举（15-30 行 vs 500+ 行），token 消耗与学生诊断相当
- **正向**：聚合逻辑封装在 `ClassWeaknessOverviewStrategy` 内，未来新增"年级概览"等更高层聚合意图可直接复用
- **负向**：降级路径（MASTERS 不可用）需要批量查 MySQL exam_record 并按 kpName 分组计算得分率，新增 `ExamRecordRepository.findByStudentNoIn()` 批量查询方法
- **负向**：虚拟班级节点使前端子图组件不可用，但 v1 明确排除，v2 时适配
- **中性**：与 `StudentDiagnosisStrategy` 共享 `QueryGraphRepository.findPrerequisitesUpstream()` 方法，前置依赖链查询逻辑完全相同