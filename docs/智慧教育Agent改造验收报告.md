# 智慧教育 Agent 改造验收报告

> 验收日期：2026-08-14
>
> 分支：`codex/education-agent-refactor`
>
> 基线：`8a2ea62`
> 验收范围：知识图谱质量与剪枝、EMA 掌握度、四类 Tool、受控 ReAct、API/审计、前端工作台

## 1. 验收结论

本次改造范围的编译、打包、定向回归和前端生产构建均通过。新增/修改测试共 20 个测试类、41 个测试用例，失败 0、错误 0。

仓库全量 `mvn test` 仍未达到发布门禁：当前报告为 367 个测试、8 个失败、46 个错误、5 个跳过，分布在 14 个既有测试套件。失败集合与改造前基线登记一致，主要来自本地 MySQL/Neo4j 未启动，以及教材删除、Prompt 一致性、图指标、异常日志和既有分层规则债务。本次新增的 Agent、Mastery、剪枝、抽取质量门禁和夹具测试不在失败清单中。

## 2. 已交付能力

| 领域 | 交付结果 | 关键证据 |
|---|---|---|
| 图谱质量 | 写入前过滤无效端点、自环、重复边、越界权重和有向环 | `GraphQualityValidator` |
| 前置方向 | 统一为 `A-[:PREREQUISITE_OF]->B`，反向追溯 B 的真实前置路径 | `QueryGraphRepositoryQueryTest` |
| 动态剪枝 | 按薄弱度、置信度、依赖强度和跳数稳定排序，返回真实多跳边 | `StudentDiagnosisStrategyTest` |
| 考试证据 | 按学生、考试、知识点聚合题目得分率，保留题号与原始分证据 | `ExamKnowledgeScoreAggregatorTest` |
| EMA 掌握度 | 支持首次初始化、连续更新、幂等、按时间重放、删除和全量重建 | `MasteryUpdateServiceTest` |
| 四类 Tool | 图谱检索、学生画像、薄弱点分析、学习路径推荐均为结构化、可独立测试 Tool | `application.agent.tool` |
| 学习路径 | 对依赖子图执行环检测和稳定拓扑排序，再按每日预算分配时长 | `LearningPathRecommendationToolTest` |
| 受控 ReAct | 严格 JSON 动作协议、白名单 Tool、去重、最大轮次、总超时、Tool 超时、输出预算和规则降级 | `TeachingAgentServiceTest`、`AgentToolExecutorTest` |
| 审计与权限 | 保存任务摘要和公开 Tool 轨迹；学生仅可访问本人，普通用户仅可读取自己的任务 | `AgentTraceService`、`MasteryControllerTest` |
| 可观测性 | 记录任务状态/耗时/轮次、Tool 成败/耗时和降级原因 | `AgentMetricsTest` |
| 前端 | 教学 Agent 工作台展示回答、证据、公开步骤、耗时、掌握度、更新历史和学习路径 | `/teaching-agent` |

## 3. 数据库升级

按顺序执行：

```text
deployment/init-sql/002-agent-mastery.sql
deployment/init-sql/003-agent-trace.sql
```

新增表：

- `mastery_update_event`：掌握度事实账本，可按考试证据重放；
- `agent_task`：Agent 任务摘要与所有者；
- `agent_tool_call`：每轮公开决策、参数、观察、状态和耗时。

迁移为旁路新增，不删除 `exam_record` 或旧问答数据。已有库需手工执行升级脚本；仅首次初始化的容器会自动处理初始化目录。

## 4. 验收命令与结果

### 4.1 本次改造定向回归

```powershell
mvn -q '-Dtest=QueryGraphRepositoryQueryTest,StudentDiagnosisStrategyTest,GraphQualityValidatorTest,ExtractionServiceTest,ExtractionRegressionTest,ExtractionNodeExtensionIntegrationTest,EmaMasteryUpdateStrategyTest,ExamKnowledgeScoreAggregatorTest,MasteryUpdateServiceTest,MasteryQueryServiceTest,MasteryGradeEventListenerOrderTest,MasteryControllerTest,TeachingToolRegistryTest,KnowledgeGraphSearchToolTest,StudentProfileToolTest,WeaknessAnalysisToolTest,LearningPathRecommendationToolTest,LlmAgentPlannerTest,AgentToolExecutorTest,TeachingAgentServiceTest,AgentTraceServiceTest,AgentMetricsTest,AgentFixtureTest' test
```

结果：通过。

### 4.2 后端打包

```powershell
mvn -q -DskipTests package
```

结果：通过。

### 4.3 前端门禁

```powershell
cd frontend
npm run build
```

结果：`vue-tsc` 与 Vite 生产构建通过；仅保留既有大 chunk 警告，不影响产物生成。

### 4.4 全仓测试审计

```powershell
mvn -q test
```

结果：未通过。失败套件如下：

- 外部数据库/完整上下文：`FusionControllerIntegrationTest`、`ConstructionControllerIntegrationTest`、`QueryControllerIntegrationTest`、`FileProcessingIntegrationTest`、`TransactionVisibilityTest`、`CrossStorageCompensationTest`、`QueryServiceImplHistoryTest`、`QueryTaskRepositoryTest`、`MetricsControllerTest`；
- 既有单测断言：`TextbookServiceTest`、`ExtractionPromptBuilderTest`、`MetricsServiceTest`、`GlobalExceptionHandlerLoggingTest`；
- 既有架构债务：`LayeredArchitectureTest`，当前 348 条历史违规。临时发现的 Agent/Mastery 新违规已清零，报告中不再出现本次新增包。

## 5. 固定演示数据

目录：`src/test/resources/fixtures/agent/`

- `knowledge-graph.cypher`：9 个知识点、11 条方向明确且无环的前置依赖；
- `exam-1.csv`、`exam-2.csv`、`exam-3.csv`：3 名学生连续 3 次考试；
- 覆盖满分、部分得分、零分和缺考；
- `AgentFixtureTest` 保证三个 CSV 可由生产解析器重复导入；
- `expected-diagnosis.json` 固定“顶点坐标”诊断期望。

## 6. 5～8 分钟面试演示

1. 执行数据库升级脚本，导入 `knowledge-graph.cypher`。
2. 依次上传三个考试 CSV，展示 `mastery_update_event` 中的 `score_rate`、`old_weight`、`new_weight`、`alpha` 和题目证据。
3. 调用 `GET /api/v1/mastery/S-FIXTURE-001?subject=数学`，展示掌握度最新状态。
4. 打开前端 `/teaching-agent`，输入“请分析我为什么顶点坐标总丢分，并制定一周学习计划”。
5. 展示 Agent 动态调用学生画像、薄弱点分析和学习路径 Tool。
6. 展开执行轨迹，说明只保存公开决策摘要，不保存模型隐藏推理。
7. 展示路径中“函数基础 → 二次函数定义 → 一般式/配方法 → 对称轴 → 顶点坐标”的拓扑顺序。
8. 点击知识点掌握度，展示每场考试后的 EMA 更新历史。

## 7. 面试追问口径

### 知识图谱如何构建

教材文本先经结构化抽取生成知识点、分类、实体和关系。写入 Neo4j 前执行端点、权重、自环、重复边和有向环校验。前置关系只有一种语义：`A PREREQUISITE_OF B` 表示 A 必须先于 B 学习；关系保留强度、描述和来源，便于诊断追溯。

### 图谱剪枝如何实现

先从 `MASTERS` 找出低于阈值的薄弱节点，再沿 `PREREQUISITE_OF` 入向路径回溯真实前置节点。薄弱点按 `(1-mastery)×confidence` 排序，前置节点再结合关系强度和跳数衰减，最后执行稳定 Top-K。多跳路径保留每条真实边，不压缩成不存在的关系。

### 动态权重如何更新

每次考试把同一知识点涉及题目的原始分与满分聚合为 `scoreRate`。首次权重等于得分率，后续按 `new=alpha×scoreRate+(1-alpha)×old` 更新。MySQL 事件表是事实源，Neo4j `MASTERS` 是查询态；重复事件由唯一键保证幂等，删除或乱序导入时按考试时间重放。

### Agent 为什么可控

模型只能输出 `TOOL_CALL` 或 `FINAL_ANSWER` 两类 JSON 动作，只能调用注册表中的四个白名单 Tool。服务端注入用户和学生范围，并限制总时长、单 Tool 时长、最大轮次、重复调用、连续错误和 Observation 大小。LLM 规划失败时使用规则路由，图算法决定事实筛选和学习顺序，LLM 只负责选择工具与解释结果。

## 8. 上线前剩余门禁

1. 在提供 MySQL、Neo4j、MinIO 的 CI/Testcontainers 环境中重新执行全量测试。
2. 修复或确认 5 类既有纯单测断言与 348 条历史架构违规。
3. 在已有数据库副本上演练两份 SQL 的升级与回滚。
4. 用固定问题集测量 Tool 选择准确率、端到端 P95 延迟和剪枝 Token 降幅。
