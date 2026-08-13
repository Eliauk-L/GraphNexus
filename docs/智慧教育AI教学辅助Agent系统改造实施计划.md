# 智慧教育 AI 教学辅助 Agent 系统改造实施计划

> 项目：GraphNexus（学情智图）  
> 版本：v1.0  
> 日期：2026-08-13  
> 目标周期：15～20 个工作日  
> 核心目标：将现有“知识图谱增强问答”升级为“基于知识图谱和受控 ReAct 的教学辅助 Agent”，使简历中的知识图谱剪枝、四类 Tool、EMA 掌握度更新和个性化学习路径均可通过代码、数据和演示验证。

---

## 1. 政造目标

### 1.1 必须实现的能力

1. 修正并验证知识点前置依赖的遍历方向，保证薄弱点根因追溯正确。
2. 将考试成绩转换为可审计的掌握度更新事件，使用 EMA 增量更新 `MASTERS.weight`。
3. 建立统一 Tool 协议，实现四类核心 Tool：
   - 知识图谱检索 Tool；
   - 学生画像 Tool；
   - 薄弱点分析 Tool；
   - 学习路径推荐 Tool。
4. 实现受控 ReAct Agent，由大模型按需选择 Tool，而不是执行固定问答流水线。
5. 保存 Agent 工具调用轨迹、证据和耗时，支持问题定位与面试演示。
6. 前端展示诊断依据、掌握度变化、学习路径和 Agent 执行步骤。

### 1.2 非本期目标

- 不允许大模型生成并直接执行任意 SQL 或 Cypher。
- 不在第一期引入独立向量数据库；知识点定位先采用名称、别名和关键词召回。
- 不自动生成具体练习题资源；学习计划先推荐知识点、顺序、时长和学习目标。
- 不替换现有 Neo4j、MySQL、Vue 3 和 LangChain4j 技术栈。

---

## 2. 当前能力与改造范围

| 能力 | 当前状态 | 改造动作 |
|---|---|---|
| 教材知识抽取 | 已有实体、知识点、分类、依赖关系抽取 | 增加方向校验、去环、质量指标和来源证据 |
| 成绩导入 | 已支持 CSV/Excel，成绩明细包含题目与知识点 | 增加本次考试知识点得分率聚合 |
| 掌握度 | 融合阶段按历史成绩进行时间衰减重算 | 改为事件驱动 EMA 增量更新，保留全量重放能力 |
| 图谱剪枝 | 已按薄弱阈值筛选并展开依赖 | 修复遍历方向，返回真实路径并增加排序和预算控制 |
| 智能问答 | 固定“意图识别→剪枝→Prompt→LLM”流程 | 改为受控 ReAct 循环和 Tool Registry |
| 学习建议 | 由 LLM 根据 Prompt 自由生成 | 新增确定性学习路径算法，LLM 只负责解释 |
| 可观测性 | 已记录问答任务和 Token 估算 | 新增工具调用轨迹、错误、证据摘要和执行轮次 |

---

## 3. 目标架构

```text
教材 PDF/TXT
    └─ 文本解析 → 知识抽取 → 质量校验 → Neo4j 学科知识图谱

考试 CSV/XLSX
    └─ 成绩解析 → 按学生/知识点聚合得分率
                    └─ MasteryUpdateEvent → EMA 更新 → MASTERS 最新状态
                                             └─ MySQL 掌握度事件账本

用户问题
    └─ TeachingAgentService
         ├─ Thought/Action 结构化决策
         ├─ KnowledgeGraphSearchTool
         ├─ StudentProfileTool
         ├─ WeaknessAnalysisTool
         ├─ LearningPathRecommendationTool
         └─ Final Answer（结论 + 证据 + 学习计划）
```

建议新增后端包结构：

```text
com.graphnexus.application.agent
├── config
├── model
├── service
├── tool
│   ├── graph
│   ├── profile
│   ├── weakness
│   └── recommendation
└── trace

com.graphnexus.application.mastery
├── config
├── event
├── model
├── service
└── strategy
```

---

## 4. 总体实施顺序

| 阶段 | 工作内容 | 预计时间 | 前置依赖 | 交付结果 |
|---|---|---:|---|---|
| P0 | 基线冻结与测试数据准备 | 1 天 | 无 | 可重复验证的基准数据集 |
| P1 | 前置依赖方向修复与剪枝增强 | 2～3 天 | P0 | 可信的薄弱点上下文 |
| P2 | EMA 掌握度事件化 | 3～5 天 | P0 | 可解释、可重放的动态权重 |
| P3 | 四类核心 Tool | 4～6 天 | P1、P2 | 独立、可测试的工具能力 |
| P4 | 受控 ReAct Agent | 4～5 天 | P3 | 动态工具编排链路 |
| P5 | API、前端和可观测性 | 3～4 天 | P4 | 可演示的完整产品闭环 |
| P6 | 全链路验收与面试材料 | 2～3 天 | P5 | 测试报告、演示脚本和指标 |

---

## 5. P0：基线冻结与测试数据准备

### T0.1 建立验收数据集

使用现有数学或线性回归示例，固定以下数据：

- 1 个学科；
- 8～12 个知识点；
- 10～15 条方向明确的前置依赖；
- 3 名学生；
- 每名学生至少 3 次考试；
- 同时包含满分、部分得分、零分和缺考记录。

新增建议文件：

```text
src/test/resources/fixtures/agent/knowledge-graph.cypher
src/test/resources/fixtures/agent/exam-1.csv
src/test/resources/fixtures/agent/exam-2.csv
src/test/resources/fixtures/agent/exam-3.csv
src/test/resources/fixtures/agent/expected-diagnosis.json
```

### T0.2 记录改造前基线

记录以下指标：

- 单次诊断的剪枝节点数、边数和估算 Token 数；
- 单次问答耗时；
- 当前薄弱点列表；
- 当前前置依赖查询结果；
- 当前掌握度计算结果。

### 验收标准

- 测试数据能够被重复导入。
- 相同数据重复运行得到相同基线结果。
- 所有后续阶段共用同一套期望结果，避免边开发边修改验收口径。

---

## 6. P1：前置依赖方向修复与剪枝增强

### T1.1 统一关系语义

系统唯一采用如下定义：

```text
A -[:PREREQUISITE_OF]-> B
含义：学习 B 之前需要先掌握 A。
```

同步检查并修正文档、Prompt、示例和测试中的相反定义。

涉及文件：

- `infrastructure/neo4j/edge/PrerequisiteEdge.java`
- `resources/prompts/extraction-system.md`
- `resources/prompts/extraction-fewshot-*.md`
- `docs/examples/知识图谱构建示例-初中数学教辅.md`

### T1.2 修复前置知识查询

修改 `QueryGraphRepository.findPrerequisitesUpstream`，从薄弱知识点沿入边反向查询真正的前置节点：

```cypher
MATCH path =
  (prerequisite:KnowledgePoint)
  -[:PREREQUISITE_OF*1..N]->
  (weak:KnowledgePoint)
WHERE weak.id IN $weakKpIds
RETURN path
```

查询结果需要包含：

- 路径中的全部知识点；
- 路径中的每条真实关系；
- 跳数；
- 依赖强度；
- 关系描述。

不要把多跳路径压缩成一条不存在的边。

### T1.3 改造剪枝结果模型

扩展 `PrunedSubgraph.PruningMeta`：

```text
strategy
mastersAvailable
weakThreshold
maxHops
topK
originalNodeCount
retainedNodeCount
retainedEdgeCount
truncated
truncatedReasons
```

### T1.4 增加剪枝排序规则

薄弱节点优先级：

```text
weakScore = (1 - mastery) × confidence
```

前置节点优先级：

```text
prerequisiteScore = weakScore × relationshipStrength / (hop + 1)
```

按分数排序后执行 `topK` 和 Token 预算限制，禁止依赖无序的 `LIMIT 200`。

### T1.5 增加图谱质量校验

教材图谱写入前检查：

- 自环；
- 重复边；
- 无效端点；
- 环路；
- 依赖强度是否处于 `[0,1]`；
- 学科是否一致。

环路第一期采用“记录告警并拒绝该关系”，避免推荐阶段无法拓扑排序。

### 测试用例

1. `一般式 → 对称轴 → 顶点坐标`，诊断“顶点坐标”必须得到前两个节点。
2. 从“对称轴”追溯时不能返回其后继“顶点坐标”。
3. 两跳结果必须包含两条真实边。
4. 出现自环或闭环时构图失败或关系被隔离。
5. 相同优先级时按知识点 ID 稳定排序。

### 验收标准

- 前置关系方向测试全部通过。
- 学习路径中的每一步都满足前置节点排在目标节点之前。
- 剪枝结果在相同输入下完全稳定。

---

## 7. P2：EMA 掌握度动态更新

### T2.1 新增掌握度更新事件表

在 `src/main/resources/db/init.sql` 增加：

```sql
CREATE TABLE mastery_update_event (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    event_id           VARCHAR(36) NOT NULL,
    student_no         VARCHAR(64) NOT NULL,
    exam_no            VARCHAR(64) NOT NULL,
    subject            VARCHAR(32) NOT NULL,
    knowledge_point_id VARCHAR(64) NOT NULL,
    knowledge_point_name VARCHAR(255) NOT NULL,
    score_rate         DECIMAL(6,5) NOT NULL,
    old_weight         DECIMAL(6,5),
    new_weight         DECIMAL(6,5) NOT NULL,
    alpha              DECIMAL(6,5) NOT NULL,
    sample_count       INT NOT NULL,
    evidence_json      JSON,
    occurred_at        DATETIME NOT NULL,
    create_time        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mastery_exam_kp (student_no, exam_no, knowledge_point_id),
    INDEX idx_mastery_student_kp (student_no, knowledge_point_id),
    INDEX idx_mastery_exam (exam_no)
);
```

### T2.2 新增配置

```yaml
mastery:
  strategy: ema
  ema:
    alpha: 0.30
    initial-weight-mode: SCORE_RATE
  weak-threshold: 0.60
  mastered-threshold: 0.80
```

在 `system_config` 增加：

- `mastery.strategy`；
- `mastery.ema.alpha`；
- `mastery.weak-threshold`；
- `mastery.mastered-threshold`。

### T2.3 实现本次考试得分聚合

聚合粒度为：

```text
studentNo + examNo + knowledgePoint
```

一题对应多个知识点时，第一期采用“每个知识点获得该题完整得分率”的可解释规则；在事件的 `evidence_json` 中记录题号、原始得分和满分。

计算公式：

```text
scoreRate = Σ rawScore / Σ maxScore
```

缺考或所有题目满分为 0 时跳过更新。

### T2.4 实现 EMA 策略

```java
public interface MasteryUpdateStrategy {
    MasteryUpdateResult update(MasteryState oldState, ExamEvidence evidence);
}
```

EMA 公式：

```text
首次：newWeight = scoreRate
后续：newWeight = alpha × scoreRate + (1 - alpha) × oldWeight
```

示例：旧掌握度 `0.60`，本次得分率 `0.90`，`alpha=0.30`：

```text
newWeight = 0.30 × 0.90 + 0.70 × 0.60 = 0.69
```

### T2.5 更新 Neo4j MASTERS

`MASTERS` 关系新增或维护：

```text
weight
sampleCount
confidence
lastExamNo
lastExamDate
updatedAt
version
description
```

置信度第一期采用：

```text
confidence = 1 - exp(-sampleCount / 3)
```

### T2.6 事件驱动处理

新增链路：

```text
GradeUploadedEvent
→ MasteryUpdateEventListener
→ ExamKnowledgeScoreAggregator
→ EmaMasteryUpdateStrategy
→ MasteryEventRepository
→ MasteryGraphRepository
```

要求：

- MySQL 唯一键保证重复成绩事件不重复更新；
- 写入事件账本成功后再更新 Neo4j；
- Neo4j 更新失败时记录待补偿状态或通过重放任务修复；
- 记录 `traceId`、`examNo` 和 `studentNo`。

### T2.7 实现删除与重放

删除考试时：

1. 查找受影响的学生和知识点；
2. 删除该考试对应的 `mastery_update_event`；
3. 按 `occurred_at, id` 顺序重放剩余事件；
4. 重建对应 `MASTERS` 状态；
5. 没有剩余事件时删除该 `MASTERS` 关系。

提供管理员接口：

```http
POST /api/v1/mastery/rebuild?studentNo={studentNo}&subject={subject}
GET  /api/v1/mastery/{studentNo}?subject={subject}
GET  /api/v1/mastery/{studentNo}/{kpId}/history
```

### 测试用例

- 首次考试初始化权重。
- 连续三次考试严格符合 EMA 公式。
- 部分得分和一题多知识点聚合正确。
- 重复消费同一考试事件，权重不发生二次变化。
- 删除中间一次考试后，重放结果正确。
- 乱序导入考试后按考试日期重放结果正确。
- 缺考不改变掌握度。
- Neo4j 失败后能够通过重建接口恢复。

### 验收标准

- 权重计算误差不超过 `1e-5`。
- 重复导入幂等。
- 任一 MASTERS 权重都可追溯到具体考试和题目。
- 删除考试后不存在失效的掌握度证据。

---

## 8. P3：四类核心 Tool

### T3.1 定义统一 Tool 协议

```java
public interface TeachingTool<I, O> {
    String name();
    String description();
    Class<I> inputType();
    ToolResult<O> execute(I input, ToolExecutionContext context);
}
```

统一返回：

```java
public record ToolResult<T>(
        boolean success,
        T data,
        List<EvidenceRef> evidence,
        List<String> warnings,
        ToolMetrics metrics
) {}
```

`ToolExecutionContext` 至少包含：

```text
taskId、userId、role、studentScope、subject、deadline、traceId
```

### T3.2 实现 Tool Registry

```java
@Component
public class TeachingToolRegistry {
    private final Map<String, TeachingTool<?, ?>> tools;
}
```

要求：

- Tool 名称唯一；
- 启动时校验名称和输入类型；
- 只向模型暴露白名单 Tool；
- 每个 Tool 独立设置超时、最大结果数和最大输出字符数。

### T3.3 知识图谱检索 Tool

Tool 名称：`knowledge_graph_search`

输入：

```json
{
  "query": "为什么顶点坐标总是算错？",
  "subject": "数学",
  "topK": 5,
  "maxHops": 2
}
```

处理步骤：

1. 知识点标准名精确匹配；
2. 知识点别名和关键词匹配；
3. 必要时调用 LLM 做查询词标准化，但不允许 LLM 决定最终数据库条件；
4. 查询教材描述、来源和前后置依赖；
5. 按匹配分数返回 Top-K。

输出：

- 命中知识点；
- 匹配原因和分数；
- 教材定义与来源文档；
- 前置/后继节点；
- 结构化子图。

需要新增或扩展：

- `KnowledgeGraphSearchTool`；
- `KnowledgePointSearchService`；
- `AgentGraphRepository`；
- Neo4j `KnowledgePoint(name, normalizedName, aliases)` 查询索引。

### T3.4 学生画像 Tool

Tool 名称：`student_profile`

输入：

```json
{
  "studentNo": "S20240011",
  "subject": "数学",
  "recentExamLimit": 5
}
```

输出：

- 学生基本信息；
- 最近考试；
- 成绩趋势；
- 各知识点掌握度、置信度和更新时间；
- 薄弱/临界/已掌握知识点分组；
- 数据完整性警告。

权限要求：

- 教师只能查看授权班级；
- 学生只能查看本人；
- 管理员按现有 RBAC 规则访问。

### T3.5 薄弱点分析 Tool

Tool 名称：`weakness_analysis`

输入：

```json
{
  "studentNo": "S20240011",
  "subject": "数学",
  "weakThreshold": 0.60,
  "maxHops": 2,
  "topK": 10
}
```

算法：

```text
weaknessScore = (1 - mastery) × confidence
rootCauseScore = weaknessScore × prerequisiteStrength × downstreamImpact / (hop + 1)
```

输出：

- 直接薄弱点；
- 根因知识点；
- 依赖路径；
- 掌握度变化趋势；
- 分数解释；
- 证据考试和题目。

当前 `StudentDiagnosisStrategy` 应逐步变成该 Tool 的领域服务，避免 Agent 层直接依赖剪枝策略实现。

### T3.6 学习路径推荐 Tool

Tool 名称：`learning_path_recommendation`

输入：

```json
{
  "studentNo": "S20240011",
  "subject": "数学",
  "targetKnowledgePointIds": ["kp-1004"],
  "dailyMinutes": 45,
  "days": 7
}
```

确定性算法：

1. 获取目标薄弱点和根因子图；
2. 检查有向环；
3. 过滤已掌握且置信度较高的节点；
4. 对剩余子图执行拓扑排序；
5. 同一层按 `rootCauseScore` 降序；
6. 根据知识点难度和用户时间预算分配时长；
7. 生成每日学习步骤和复习检查点。

输出：

```json
{
  "goal": "掌握顶点坐标求解",
  "steps": [
    {
      "order": 1,
      "knowledgePointId": "kp-1001",
      "name": "二次函数一般式",
      "reason": "目标知识的前置基础",
      "currentMastery": 0.52,
      "plannedMinutes": 30,
      "successCriterion": "相关练习正确率达到80%"
    }
  ]
}
```

大模型仅把结构化路径改写为自然语言，不允许改变拓扑顺序或伪造知识点。

### Tool 验收标准

- 四个 Tool 均可脱离 Agent 单独调用和测试。
- 输入不合法时返回结构化错误。
- 每个结论至少携带一个证据引用或明确标记“证据不足”。
- Tool 输出稳定，不依赖 LLM 自由文本才能完成核心计算。

---

## 9. P4：受控 ReAct Agent

### T4.1 定义 Agent 状态

```java
public record AgentState(
        String taskId,
        String userQuestion,
        AgentEntityContext entities,
        List<ToolCallRecord> toolCalls,
        int currentRound,
        int maxRounds,
        AgentStatus status
) {}
```

状态枚举：

```text
PLANNING、TOOL_RUNNING、ANSWERING、COMPLETED、FAILED、MAX_ROUNDS_REACHED
```

### T4.2 定义模型动作协议

模型每轮只能返回以下 JSON 之一：

```json
{
  "type": "TOOL_CALL",
  "tool": "student_profile",
  "arguments": {},
  "decisionSummary": "需要先获取学生当前掌握情况"
}
```

```json
{
  "type": "FINAL_ANSWER",
  "answerPlan": "根据已取得的画像、薄弱点和学习路径生成回答"
}
```

禁止解析自由格式的 `Thought:` 和 `Action:` 文本，避免脆弱的正则协议。

### T4.3 实现执行循环

伪代码：

```java
while (state.currentRound() < state.maxRounds()) {
    AgentAction action = planner.nextAction(state, availableTools);
    if (action.isFinalAnswer()) {
        return answerGenerator.generate(state);
    }
    validateAction(action);
    authorize(action, context);
    ToolResult<?> observation = toolExecutor.execute(action);
    state = state.append(action, observation);
}
return fallbackAnswer(state);
```

推荐配置：

```yaml
agent:
  max-rounds: 6
  total-timeout-ms: 30000
  tool-timeout-ms: 5000
  max-observation-chars: 12000
  max-consecutive-errors: 2
```

### T4.4 设定推荐调用策略

模型可动态选择，但系统 Prompt 应引导形成以下常见路径：

| 问题类型 | 推荐 Tool 链 |
|---|---|
| “解释某个知识点” | 图谱检索 |
| “我的数学学得怎么样” | 学生画像 |
| “为什么这次考差” | 学生画像 → 薄弱点分析 |
| “帮我制定复习计划” | 学生画像 → 薄弱点分析 → 学习路径推荐 |
| “某问题和我的薄弱点有关吗” | 图谱检索 → 学生画像 → 薄弱点分析 |

### T4.5 异常与降级

- 单 Tool 失败：将错误作为 Observation，允许模型选择替代 Tool。
- 连续两次失败：停止循环，返回已取得的信息和失败说明。
- LLM 规划失败：降级到规则路由。
- 图谱无匹配：返回教材数据不足，不让模型编造。
- MASTERS 不存在：学生画像明确标记数据不足，必要时使用考试原始得分率临时计算，但不写回状态。
- 超过最大轮次：根据已有证据生成不完整回答，并标记原因。

### T4.6 安全边界

- Tool 参数采用 JSON Schema 和 Bean Validation 双重校验。
- Agent 不暴露通用数据库执行 Tool。
- 服务端从登录态注入学生或班级权限范围，不能相信模型传入权限信息。
- Tool Observation 对学生隐私字段做最小化返回。
- 日志中不保存完整成绩明细和模型隐藏推理。

### 验收标准

- 同一 Agent 能根据不同问题选择不同 Tool 链。
- 复习计划类问题能够调用至少三个相关 Tool。
- 最大轮次、超时和错误降级均可通过测试触发。
- 最终回答中的知识点和数值都能映射到 Tool Observation。

---

## 10. P5：数据表、API、前端和可观测性

### T5.1 新增 Agent 调用轨迹表

```sql
CREATE TABLE agent_tool_call (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    task_id           VARCHAR(36) NOT NULL,
    round_no          INT NOT NULL,
    tool_name         VARCHAR(64) NOT NULL,
    arguments_json    JSON NOT NULL,
    observation_json  MEDIUMTEXT,
    decision_summary  VARCHAR(512),
    status            VARCHAR(20) NOT NULL,
    elapsed_ms        BIGINT,
    error_message     VARCHAR(1000),
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_agent_tool_task (task_id, round_no)
);
```

`arguments_json` 写入前必须移除隐私字段和密钥。

### T5.2 扩展 query_task

新增字段：

```text
agent_mode
agent_rounds
evidence_json
fallback_reason
```

保留原 `/ask` 接口兼容固定流水线，新建或升级 `/chat` 使用 Agent 模式。

### T5.3 API 设计

```http
POST /api/v1/agent/chat
GET  /api/v1/agent/result/{taskId}
GET  /api/v1/agent/result/{taskId}/trace

POST /api/v1/agent/tools/knowledge-graph-search
POST /api/v1/agent/tools/student-profile
POST /api/v1/agent/tools/weakness-analysis
POST /api/v1/agent/tools/learning-path-recommendation

GET  /api/v1/mastery/{studentNo}
GET  /api/v1/mastery/{studentNo}/{kpId}/history
POST /api/v1/mastery/rebuild
```

Tool 独立接口仅建议开放给管理员或测试环境，生产端主要通过 Agent 调用。

### T5.4 Agent 响应模型

```json
{
  "taskId": "...",
  "status": "COMPLETED",
  "answer": "...",
  "toolsUsed": ["student_profile", "weakness_analysis"],
  "evidence": [],
  "learningPath": {},
  "tokenUsage": {},
  "warnings": []
}
```

### T5.5 前端改造

在智能问答页增加：

1. **Agent 步骤面板**：展示“查询图谱、读取画像、分析薄弱点、生成路径”等可公开步骤及耗时。
2. **诊断证据卡片**：展示知识点掌握度、涉及考试、题目得分和前置路径。
3. **掌握度趋势图**：展示每次考试后的 EMA 变化。
4. **学习路径时间轴**：按拓扑顺序展示知识点、预计时长和达标条件。
5. **降级提示**：明确展示数据不足、Tool 超时或使用规则兜底。

建议新增组件：

```text
frontend/src/views/query/components/AgentTracePanel.vue
frontend/src/views/query/components/EvidencePanel.vue
frontend/src/views/query/components/MasteryTrendChart.vue
frontend/src/views/query/components/LearningPathTimeline.vue
```

### T5.6 监控指标

增加 Micrometer 指标：

```text
agent_request_total
agent_request_duration_seconds
agent_rounds
agent_tool_call_total{tool,status}
agent_tool_duration_seconds{tool}
agent_fallback_total{reason}
mastery_update_total{status}
mastery_rebuild_total{status}
graph_pruning_ratio
```

### 验收标准

- 用户能够查看最终答案、证据、学习路径和工具步骤。
- 任何 Tool 失败均能在任务轨迹中定位。
- 监控可区分 Agent 总耗时和各 Tool 耗时。
- 原有历史记录和 `/ask` 接口不受破坏。

---

## 11. P6：测试与最终验收

### 11.1 单元测试

| 模块 | 核心测试 |
|---|---|
| EMA | 首次、连续更新、边界值、缺考、部分得分 |
| 得分聚合 | 一题多 KP、多题同 KP、零满分异常 |
| 图剪枝 | 方向、多跳、去重、环、排序、topK |
| 学习路径 | 拓扑顺序、已掌握过滤、预算分配、环检测 |
| Tool Registry | 重名、未知 Tool、参数校验、超时 |
| Agent | Tool 选择、最大轮次、失败降级、最终答案 |

### 11.2 集成测试

- MySQL + Neo4j Testcontainers 验证成绩导入到 MASTERS 更新。
- 删除考试后验证事件重放和图状态。
- 验证四个 Tool 查询真实测试图谱。
- Mock LLM 返回固定动作，验证完整 ReAct 循环。
- 验证权限范围不能被模型参数绕过。

### 11.3 端到端场景

**场景 A：考试后自动更新**

```text
上传考试 → 解析成功 → 写入成绩 → 更新 EMA → 页面展示新权重和变化证据
```

**场景 B：薄弱点诊断**

```text
提问“张三为什么顶点坐标总丢分”
→ 学生画像
→ 图谱定位顶点坐标
→ 薄弱点和前置根因分析
→ 返回证据链
```

**场景 C：个性化学习计划**

```text
提问“给张三制定一周数学复习计划”
→ 学生画像
→ 薄弱点分析
→ 学习路径推荐
→ 返回每日计划
```

### 11.4 质量指标

| 指标 | 目标 |
|---|---:|
| 前置关系方向正确率 | 100%（验收集） |
| 学习路径拓扑违反率 | 0% |
| EMA 计算误差 | ≤ 1e-5 |
| 重复事件幂等率 | 100% |
| Tool 参数校验覆盖率 | 100% |
| Tool 选择准确率 | ≥ 90%（固定问题集） |
| 最终答案关键数值可追溯率 | 100% |
| Agent 最大执行时长 | ≤ 30 秒（正常环境） |
| 剪枝后 Token 降幅 | 建议 ≥ 60% |

### 11.5 发布门禁

- 后端全量测试通过。
- 前端 `vue-tsc` 和构建通过。
- 数据库升级脚本可在空库和已有库执行。
- 旧问答接口回归通过。
- 无明文学生隐私或 LLM 密钥进入日志。
- 演示数据可以一键初始化或有完整初始化说明。

---

## 12. 数据迁移与回滚方案

### 12.1 上线迁移

1. 新建 `mastery_update_event` 和 `agent_tool_call`，不立即修改旧逻辑。
2. 增加 `mastery.strategy=time-decay|ema` 开关，默认保持旧策略。
3. 从历史 `exam_record` 按考试日期生成掌握度事件。
4. 在影子模式计算 EMA，但暂不覆盖现有 `MASTERS`。
5. 比对一周或完整测试集后切换为 `ema`。
6. 保留全量重建命令，完成数据校验后再移除旧重算入口。

### 12.2 回滚

- Agent 异常时通过配置切回固定问答流水线。
- EMA 异常时切回 `time-decay` 策略并全量重算 MASTERS。
- 新表为旁路新增，不删除原始 `exam_record`，因此可重新生成全部状态。
- 前端对新字段采用可选读取，后端回滚时仍可正常显示旧回答。

---

## 13. 风险与应对

| 风险 | 影响 | 应对措施 |
|---|---|---|
| 前置边历史数据方向不一致 | 根因和路径错误 | 迁移前运行方向审计，人工抽查高频关系 |
| EMA 对导入顺序敏感 | 乱序导入产生不同结果 | 按考试日期重放，事件保持不可变 |
| MySQL 与 Neo4j 双写不一致 | 事件有记录但状态未更新 | 事件账本为事实源，提供补偿和重建任务 |
| LLM 循环或重复调用 Tool | 成本和延迟升高 | 最大轮次、调用去重、总超时和缓存 |
| Tool 输出过大 | Token 预算超限 | Top-K、字段白名单、Observation 压缩 |
| 图谱抽取关系存在幻觉 | 推荐路径不可信 | 关系强度阈值、来源证据、人工验收集 |
| 学生隐私泄露 | 合规风险 | 权限范围服务端注入、日志脱敏、最小化返回 |

---

## 14. 建议的提交拆分

为降低审查和回滚成本，按以下粒度提交：

1. `fix(graph): correct prerequisite traversal direction`
2. `test(graph): add prerequisite direction fixtures`
3. `feat(mastery): add mastery update event schema`
4. `feat(mastery): implement exam evidence aggregation`
5. `feat(mastery): implement EMA update and replay`
6. `feat(agent): add teaching tool contract and registry`
7. `feat(agent): add knowledge graph search tool`
8. `feat(agent): add student profile tool`
9. `feat(agent): add weakness analysis tool`
10. `feat(agent): add learning path recommendation tool`
11. `feat(agent): implement controlled ReAct loop`
12. `feat(api): expose agent and mastery endpoints`
13. `feat(frontend): visualize agent trace and learning path`
14. `test(agent): add integration and end-to-end scenarios`
15. `docs(agent): add demo and interview evidence`

---

## 15. 面试演示脚本

最终准备一个 5～8 分钟的完整演示：

1. 展示教材中“对称轴是顶点坐标的前置知识”。
2. 在 Neo4j 中展示相应知识点和 `PREREQUISITE_OF` 边。
3. 导入一场包含部分得分的新考试。
4. 展示某学生该知识点掌握度按 EMA 从旧值更新到新值。
5. 展示 `mastery_update_event` 中的旧值、新值、α 和题目证据。
6. 提问：“为什么张三顶点坐标总丢分？”
7. 展示 Agent 调用图谱检索、学生画像和薄弱点分析 Tool。
8. 展示根因路径和具体考试证据。
9. 继续提问：“给他制定一周复习计划。”
10. 展示学习路径 Tool 输出的拓扑顺序、每日时长和达标条件。

面试时可明确说明：

- 图算法负责筛选、排序和学习顺序，LLM 负责意图理解、工具选择和语言生成；
- 掌握度事件保存在 MySQL，Neo4j 的 `MASTERS` 是面向查询的最新状态；
- Agent 不能执行任意数据库语句，只能使用经过校验和授权的教学 Tool；
- 每个诊断结论均能追溯到知识图谱路径和考试题目证据。

---

## 16. 完成定义（Definition of Done）

只有同时满足以下条件，才视为改造完成：

- [ ] 前置依赖方向已统一，方向和多跳测试通过。
- [ ] 考试上传后自动执行 EMA 掌握度更新。
- [ ] EMA 更新支持幂等、删除重放和全量重建。
- [ ] 四类 Tool 均具有独立接口、结构化输入输出和测试。
- [ ] Agent 能够按问题动态选择 Tool，并受最大轮次和超时约束。
- [ ] 学习路径由图算法生成，满足拓扑顺序。
- [ ] 最终回答包含证据引用和数据不足提示。
- [ ] 前端能够展示 Agent 步骤、掌握度趋势和学习路径。
- [ ] Agent、Tool、EMA 和剪枝指标可监控。
- [ ] 旧问答链路和历史数据保持兼容。
- [ ] 全链路演示可以使用固定测试数据稳定复现。

