# T01-SUMMARY: 后端补齐 MASTERS weight + examHistory 透传

- **Task ID**: T01
- **Change**: `diagnosis-subgraph-viz`
- **日期**: 2026-06-22

---

## 做了什么

1. `QueryGraphRepository.findMastersByStudentAndKpIds()` — Cypher RETURN 子句新增 `m.description AS description`，使前置 KP 的 MASTERS 查询也返回考试历史
2. `StudentDiagnosisStrategy.prune()` — 新增 `kpExamHistoryMap`（`Map<String, String>`），在 mastersAvailable 分支和 Step 4 preMasters 查询中捕获 MASTERS description
3. `StudentDiagnosisStrategy.buildResult()` — 签名增加 `kpExamHistoryMap` 参数；新增 `enrichKpNodeProperties()` 辅助方法，将 `weight` 和 `examHistory` 注入 KP 节点的 `GraphNodeData.properties`
4. 降级路径（mastersAvailable=false）中 `kpExamHistoryMap` 初始化为空 HashMap，前端据此展示"暂无历次考试数据"

### 改动文件

| 文件 | 改动 |
|------|------|
| `QueryGraphRepository.java` | RETURN 加 `m.description AS description` |
| `StudentDiagnosisStrategy.java` | +kpExamHistoryMap 捕获/传递 + enrichKpNodeProperties 方法 |

## verify 输出

```
$ mvn test -Dtest="PruningStrategyRegistryTest,QueryControllerIntegrationTest"

Tests run: 7, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

（2 skipped = LLM API key 未配置，非本次改动影响）

## 6 维自查

- **R1 认知过载**：新增 `enrichKpNodeProperties` 方法 12 行，职责单一 ✅
- **R2 变更传播**：仅修改 TASK write_files 范围内的 2 个文件 ✅
- **R3 知识重复**：weight + examHistory 注入逻辑抽取为独立方法，不重复 ✅
- **R4 偶然复杂**：无——仅补充数据透传，不引入新抽象 ✅
- **R5 依赖混乱**：无——所有依赖方向与原有代码一致 ✅
- **R6 领域扭曲**：`kpExamHistoryMap` 命名反映业务语义（考试历史）✅

## 越界检查 (R6.5)

- TASK write_files：2 项（`StudentDiagnosisStrategy.java` + `QueryGraphRepository.java`）
- 实际 diff：2 项
- 越界：0 ✅

## LESSONS

- L-001（Neo4j GDS 2.x 语法）：与本次无关，已确认跳过

## 破坏性变更

未命中（无删除 ≥ 5 行、无公共 API 签名变更、新增字段向后兼容）