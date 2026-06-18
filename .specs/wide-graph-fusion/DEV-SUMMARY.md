# DEV-SUMMARY: wide-graph-fusion 实现完成

- **Change ID**: `wide-graph-fusion`
- **提交**: `c63e927`
- **日期**: 2026-06-15

---

## 完成的任务

| Task | 状态 | 说明 |
|------|:--:|------|
| T01 | ✅ | ErrorCode A0016~A0018 |
| T02 | ✅ | EdgeType MASTERS + MastersEdge |
| T03 | ✅ | KnowledgePointNode fusionSource |
| T04 | ✅ | FusionProperties + dev.yml |
| T05 | ✅ | FusionLogDO + FusionLogRepository |
| T06 | ✅ | KpMatchingStrategy + ExactMatchStrategy |
| T07 | ✅ | WeightCalculationStrategy + SimpleAverageStrategy |
| T08 | ✅ | FuzzyMatchStrategy (多度量组合) |
| T09 | ✅ | TimeDecayStrategy (时间衰减) |
| T10 | ✅ | GraphNodeRepository 融合 Cypher 方法 |
| T11 | ✅ | FusionService + FusionServiceImpl |
| T12 | ✅ | GradeServiceImpl 钩子 |
| T13 | ✅ | GraphServiceImpl 钩子 |
| T14 | ✅ | FusionController + VOs |
| T15 | ✅ | 策略单元测试 (13/13 pass) |
| T16 | ⏸️ | 集成测试 (需 podman，deferred) |

## 文件统计

- 新增: 30 files
- 修改: 8 files
- 插入: 1970 lines
- 删除: 6 lines

## 关键设计决策落实

- **KpMatchingStrategy**: 接口 + FuzzyMatchStrategy (charJaccard+bigramJaccard+Levenshtein) + ExactMatchStrategy 测试桩
- **WeightCalculationStrategy**: 接口 + TimeDecayStrategy (月衰减 0.9) + SimpleAverageStrategy 测试桩
- **策略选择**: yml `fusion.kp-matching.strategy` / `fusion.weight.strategy` 配置切换，Spring Map 注入
- **KP 分组**: 并查集聚类，documentId 非空优先选主 KP
- **边重定向**: 按边类型分别处理入边/出边，Cypher CREATE + DELETE
- **MASTERS**: Student 分组 UNWIND 批量 MERGE，从 MySQL exam_record 读成绩
- **回滚**: fusion_log JSON 快照驱动逆向恢复 (DETACH DELETE 规范 KP → CREATE 源 KP → 边恢复 → MASTERS 回退)
- **并发控制**: fusion_log.status=RUNNING 检查

## 测试结果

- FuzzyMatchStrategyTest: 6/6 ✅
- TimeDecayStrategyTest: 7/7 ✅
- 既有测试无回归 (预存在 3 个失败与本次变更无关)

## 已知未完成项

- T16 集成测试: 需要 podman Neo4j+MySQL 环境，后续补
- fusion_log 表 DDL: 需生成迁移 SQL (见 .specs/wide-graph-fusion/migrations/)

## 6 维自查

- R1 认知过载: FusionServiceImpl 约 380 行，方法职责单一
- R2 变更传播: 仅触碰 TASK write_files 范围内文件
- R3 知识重复: 匹配分组逻辑抽取为 buildFusionGroups() 复用
- R4 偶然复杂: 策略模式为需求明确要求，非过度设计
- R5 依赖混乱: L2 → L3 方向正确，无反向依赖
- R6 领域扭曲: 命名使用融合领域术语 (FusionGroup/MastersEdge/TimeDecay)

## 越界检查

✅ 所有变更严格在 TASK write_files 范围内，0 越界。