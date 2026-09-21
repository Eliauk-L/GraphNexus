# GraphNexus Jev 模型接入实施计划

> 状态：J1/J2 安全接入基础已实现；J0 与 J3-J6 依赖来源模型、候选账本和审核闭环，尚未开始。现有生产构图与融合链路保持不变。  
> 日期：2026-09-21  
> 适用范围：知识点来源记录、候选召回、语义消歧、教师审核与规范知识点映射  
> 上游方案：[知识点去重与消歧改造实施计划](./知识点去重与消歧改造实施计划.md)

## 1. 目标与接入决策

本次接入使用 TypeSafe Jev 对“一个来源知识点是否应映射到一个既有规范知识点”进行快速、结构化的语义判断。Jev 返回三段关系判断、概率分布、置信度和若干独立语义信号；GraphNexus 代码继续负责候选召回、硬冲突检查、决策策略、教师审核、图写入和回滚。

**接入位置固定为：完成 `KpMention` 来源模型之后，在候选召回与硬规则过滤之后、三段决策与 Neo4j 合并写入之前。** 对应上游方案 P2“召回与打分”中的语义裁决环节，并为 P3“审核与执行”提供结构化证据。

```text
教材/考试来源
  → KpMention 独立落库
  → CandidateRetriever 召回规范知识点候选
  → HardConflictChecker 执行确定性冲突规则
  → JevKpPairJudge 对候选对做结构化语义判断
  → KpDecisionPolicy 生成 AUTO / REVIEW / INDEPENDENT
  → 持久化候选、模型证据与决策
  → MergeProjectionService 在 Neo4j 事务中应用映射
  → 重算受影响的 MASTERS 与派生关系
```

Jev 不接入以下位置：

- 不替换 `ExtractionService` 的教材实体、知识点和关系生成；Jev 不生成任意节点列表或图结构。
- 不替换教学 Agent 的最终回答生成；Jev 不生成自然语言报告。
- 不在 Neo4j/MySQL 事务中发起网络调用。
- 不复用现有 `LlmGateway`；该接口返回自由文本，TypeSafe API 返回结构化 typed answers，契约不同。
- 不将 Jev 单次结果直接视为自动合并授权；必须经过硬规则和版本化 `KpDecisionPolicy`。

## 2. 官方能力与工程约束

依据 TypeSafe 当前官方文档：

- 调用入口为 `POST https://api.typesafe.ai/v1/systemone`，请求包含 `state`、`model` 和 `questions`，响应按问题 ID 返回 `answers`。
- Jev 支持 `Choice`、`Score`、`Noul` 三类问题；`Choice` 和 `Score` 返回概率分布及 `confidence`，`Noul` 直接返回“是”的概率。
- 同一请求中的多个问题共享同一份 state，但彼此独立、并行求值；代码负责组合结果。
- 当前稳定模型为 `jev-1.13.0`，`jev-latest` 是会随发布移动的别名。生产阈值一旦校准，应固定版本号并通过显式升级重新评测。
- Jev 主要训练语言为英语，CJK 可处理但准确率不等同于英语；中文教育语料必须先经过本项目标注集评测。
- HTTP 直连遇到 `429` 或 `529` 时需要指数退避；`401`、`422` 属于配置或请求契约问题，不应盲目重试。
- 官方知识图谱实体对齐 cookbook 采用“廉价候选召回 → 每个候选对一次 Jev 请求 → 三档 Score + 多个 Noul → 自动处理或人工审核”的结构，本方案沿用该模式并增加 GraphNexus 的学科、学段、章节和图结构硬约束。

官方资料：

- [How to build with TypeSafe](https://docs.typesafe.ai/concepts/how-to-build-with-system-one)
- [HTTP API reference](https://docs.typesafe.ai/api)
- [Models](https://docs.typesafe.ai/models)
- [Confidence](https://docs.typesafe.ai/confidence)
- [Knowledge graph entity alignment](https://docs.typesafe.ai/cookbooks/entity_alignment)

## 3. 当前系统差距与实施前置条件

### 3.1 当前阻塞点

现有流程在 Jev 有机会判断之前已经发生了不可逆语义折叠：

1. `ConstructionServiceImpl.phase1_build()` 会按同学科同名直接复用既有 KnowledgePoint ID。
2. `FusionGroupBuilder` 对同名 KP 无条件 union，其余候选按字符 Jaccard 阈值 union。
3. `FusionServiceImpl` 在得到分组后立即进入 Neo4j 事务执行合并，没有候选账本、审核队列或语义判定阶段。
4. `GraphConstructedEvent` 仅携带 `kpNames`，无法稳定标识一次教材/考试来源，也无法对同名异义记录分别判断。

因此，不能把 Jev 简单加到 `FuzzyMatchStrategy.match()` 或 `FusionGroupBuilder.build()` 中。这样既会在事务前后混入远程调用，也无法修复更早发生的同名复用。

### 3.2 必须先完成的上游工作

Jev 工程接线前必须满足：

- 新增 `KpMention`，教材和成绩导入均先保存来源记录，不按名称提前合并。
- `KpMention` 至少包含 `mentionId`、名称、描述、学科、学段、年级、章节路径、来源类型和来源 ID；缺失字段显式为空，不由模型补造。
- `KnowledgePoint` 作为规范概念保留稳定 `canonicalKpId`，来源与规范点通过映射关系关联。
- 修复抽取临时 KP ID 到最终 ID 的边端点重写，写图前继续执行 `GraphQualityValidator`。
- `GraphConstructedEvent` 或替代的融合任务载荷由 `kpNames` 升级为 `mentionIds + sourceId + graphRevision`。
- 新增 `kp_merge_run`、`kp_match_candidate`、`kp_match_decision` 账本，能够在不写正式图的情况下保存候选和模型证据。

在这些条件未完成前，只允许做独立 HTTP 契约验证和离线固定样本实验，不允许接入生产构图链路。

## 4. 目标组件与职责边界

### 4.1 应用层端口

建议在 `com.graphnexus.application.analysis.fusion` 下增加：

```text
candidate/
  CandidateRetriever.java
  CandidatePair.java
judge/
  KpPairJudge.java
  KpPairState.java
  KpPairJudgment.java
  JevLinkOutcome.java
policy/
  HardConflictChecker.java
  KpDecisionPolicy.java
  KpDecision.java
service/
  KpAlignmentService.java
  MergeProjectionService.java
```

职责如下：

| 组件 | 职责 | 是否调用 Jev |
|---|---|---:|
| `CandidateRetriever` | 按学科、学段、章节、名称、别名和可选语义索引生成小规模候选集 | 否 |
| `HardConflictChecker` | 判断跨学科、明确学段冲突、已拒绝配对、自环风险和图修订冲突 | 否 |
| `KpPairJudge` | 对一个 mention 与一个 canonical KP 返回结构化语义判断 | 是 |
| `KpDecisionPolicy` | 组合硬规则、Jev 结果、上下文完整性和业务风险，输出三段决策 | 否 |
| `MergeProjectionService` | 在 Neo4j 事务中应用已批准映射并重建派生边 | 否 |
| `KpAlignmentService` | 编排运行、持久化候选/证据/决策和失败状态 | 间接 |

`KpPairJudge` 作为应用端口，业务测试可用固定假实现，不依赖真实 TypeSafe 服务：

```java
public interface KpPairJudge {
    KpPairJudgment judge(KpPairState state);
}
```

### 4.2 基础设施适配器

建议新增：

```text
com.graphnexus.infrastructure.typesafe/
  client/TypeSafeJevClient.java
  client/TypeSafeHttpException.java
  config/TypeSafeProperties.java
  dto/SystemOneRequest.java
  dto/SystemOneResponse.java
  dto/QuestionDto.java
  dto/AnswerDto.java
  judge/JevKpPairJudge.java
```

`TypeSafeJevClient` 使用项目已有的 Spring `RestClient`，不引入 Python 服务，也不经由 LangChain4j/OpenAI 兼容接口。客户端只负责认证、序列化、响应校验、有限重试和调用指标；问题含义、决策门槛与业务降级位于应用层。

## 5. Jev 请求契约 v1

### 5.1 State

每次请求只包含一个来源记录和一个规范候选，避免把全图或无关教材内容送入模型：

```json
{
  "state": {
    "mention": {
      "name": "一次函数图像",
      "description": "研究一次函数图像的形状与变化规律",
      "subject": "数学",
      "stage": "初中",
      "grade": "八年级",
      "chapterPath": ["函数", "一次函数"],
      "prerequisites": ["平面直角坐标系"]
    },
    "candidate": {
      "canonicalKpId": "kp-123",
      "name": "一次函数的图象",
      "aliases": ["一次函数图像"],
      "description": "一次函数图象及其性质",
      "subject": "数学",
      "stage": "初中",
      "grade": "八年级",
      "chapterPath": ["函数", "一次函数"],
      "prerequisites": ["平面直角坐标系"]
    }
  },
  "model": "jev-1.13.0",
  "questions": {}
}
```

State 只使用字符串、字符串数组和嵌套对象。数值距离、名称编辑距离、章节最近公共祖先等精确计算保留在代码中，不要求 Jev 重新计算。

### 5.2 Question Set `kp-alignment-v1`

首版一次提交一个 `Score` 和三个 `Noul`，四个问题独立判断同一候选对：

```json
{
  "link_state": {
    "type": "score",
    "instructions": {
      "question": "`mention` 与 `candidate` 在当前教学语境中是否表示同一个可共享的规范知识点？",
      "focus": "判断概念身份，不要因为名称相似、同属一个章节或存在前置关系就视为同一知识点。"
    },
    "criteria": [
      {
        "outcome": "different",
        "meaning": "表示两个不同的知识点，应保持独立。",
        "examples": ["上下位概念", "前置与后续概念", "同名但定义范围不同"]
      },
      {
        "outcome": "review",
        "meaning": "两者相关且可能相同，但描述、范围或上下文不足以安全确认，需要教师审核。",
        "examples": ["教材表述过短", "别名可能有歧义", "章节或学段信息缺失"]
      },
      {
        "outcome": "same",
        "meaning": "两者表达同一个教学概念，仅名称、书写方式或描述详略不同，可以映射到同一规范知识点。"
      }
    ]
  },
  "same_core_concept": {
    "type": "noul",
    "instructions": "`mention` 与 `candidate` 是否教授相同的核心概念，而不只是主题相关？",
    "criteria": {
      "true": "定义对象、教学范围和学习目标一致。",
      "false": "只是相关、上下位、前后置，或核心对象不同。"
    }
  },
  "descriptions_consistent": {
    "type": "noul",
    "instructions": "`mention.description` 与 `candidate.description` 是否不存在实质语义冲突？"
  },
  "neighborhood_consistent": {
    "type": "noul",
    "instructions": "`mention.prerequisites` 与 `candidate.prerequisites` 是否支持两者为同一知识点？"
  }
}
```

问题集使用独立版本号管理。修改 instructions、criteria、字段选择或规范化方式时必须升级版本，例如 `kp-alignment-v2`，不得在同一版本下静默改变语义。

### 5.3 响应映射

应用层统一转换为：

```java
public record KpPairJudgment(
        String provider,
        String model,
        String questionVersion,
        double linkScore,
        Map<Integer, Double> linkProbabilities,
        double confidence,
        double sameCoreConcept,
        double descriptionsConsistent,
        double neighborhoodConsistent,
        int inputTokens,
        int outputTokens,
        String stateHash,
        long durationMs) {}
```

响应必须通过以下校验后才能进入决策层：

- 返回模型名、所有预期问题 ID 和正确的 answer type。
- 所有概率、Noul 和 confidence 位于 `[0,1]`。
- `link_state.probabilities` 包含三个 level，概率和在容差范围内等于 1。
- `score` 位于 `[0,2]`，legend 与问题版本一致。
- usage 字段可记录；缺失时不得伪造为真实用量，应标记为未知。
- 未知字段可忽略，但已知字段类型错误、缺问题或模型返回空响应时整次判断失败。

## 6. 决策策略

### 6.1 硬规则优先

以下条件由代码直接处理，不调用 Jev 或不允许 Jev 覆盖：

- 学科不同：不召回。
- 明确学段冲突：默认 `INDEPENDENT`；跨学段复用只能通过教师维护的显式映射。
- 已存在有效拒绝决策且输入、特征和规范点版本未变化：跳过重复提议。
- 合并后会产生 `PREREQUISITE_OF` 自环、方向冲突或规范组内部冲突：`REVIEW` 或拒绝。
- 候选图修订号已变化：本次结果过期，重新召回，不执行旧判断。

### 6.2 Jev 三段语义

`link_state` 的最近等级只表达语义倾向：

- 近 0：`different`，倾向保持独立。
- 近 1：`review`，进入教师审核。
- 近 2：`same`，具备自动映射候选资格。

`confidence` 表达概率分布集中程度，不等于结果正确率，也不是自动执行授权。`Noul` 没有独立 confidence，接近 0.5 表示真假概率接近，不表示“中等相似”。

### 6.3 `KpDecisionPolicy v1`

首版策略：

| 条件 | 输出 |
|---|---|
| 命中硬冲突 | `INDEPENDENT` 或 `REVIEW`，按冲突类型固定处理 |
| 上下文缺失且 Jev 不是明确 different | `REVIEW` |
| Jev 服务失败、超时、429/529 重试耗尽 | `REVIEW`，若无审核能力则暂时保持独立 |
| `link_state=review` | `REVIEW` |
| `link_state=same` 但 confidence 未达到标注集校准门槛 | `REVIEW` |
| `link_state=same`、置信度达标、无硬冲突、上下文完整、规范组一致 | `AUTO` 候选 |
| `link_state=different` 且结果稳定 | `INDEPENDENT` |

在 `SHADOW` 阶段，无论结果如何都不执行 AUTO；策略仅输出建议和原因。正式门槛不得直接采用官方 cookbook 示例值或凭经验指定，必须通过本项目中文标注集按学科、学段和模型版本校准。

现有名称相似度、章节距离、年级兼容度和邻居特征继续保存并用于：

1. 候选召回和排序；
2. 硬冲突检测；
3. 教师审核解释；
4. 与 Jev 的离线对照评测。

它们不再通过并查集直接触发合并，也不与 Jev 重复语义信号简单相加后自动执行。

## 7. 持久化与审计

复用上游方案的三张表，补充以下约定：

### 7.1 `kp_merge_run`

- `model_version`：实际固定模型 ID，例如 `jev-1.13.0`。
- `feature_version`：确定性特征版本。
- `threshold_version`：`KpDecisionPolicy` 和校准门槛版本。
- 运行级统计写入扩展 JSON：请求数、缓存命中数、输入 token、失败数、降级数和总耗时。

### 7.2 `kp_match_candidate`

- `feature_scores_json`：名称、章节、年级、邻居等确定性特征。
- `evidence_json`：Jev 的 model、questionVersion、stateHash、score、完整 probabilities、confidence、Noul 值、usage 和 duration。
- `decision_band`：`AUTO`、`REVIEW` 或 `INDEPENDENT`。
- `status`：`PENDING → APPROVED/REJECTED → APPLIED`，失败为 `APPLY_FAILED`。

不保存 API Key，不默认保存整篇教材原文。审计 state 只保存本次判断使用的知识点字段或其哈希；若保存完整 state，需沿用项目数据保留和访问控制策略。

### 7.3 缓存键

建议缓存键：

```text
sha256(
  normalized mention state
  + normalized candidate state
  + modelVersion
  + questionVersion
)
```

缓存只复用完全相同的判断输入。规范点描述、别名、章节、邻居关系、模型版本或问题版本变化时自动失效。

## 8. 配置、密钥与运行模式

建议配置：

```yaml
typesafe:
  enabled: false
  base-url: https://api.typesafe.ai
  api-key: ${TYPESAFE_API_KEY:}
  model: jev-1.13.0
  question-version: kp-alignment-v1
  connect-timeout-ms: 2000
  read-timeout-ms: 5000
  max-retries: 2
  initial-backoff-ms: 300
  max-concurrency: 4
  max-candidates-per-mention: 8
  mode: OFF # OFF / SHADOW / REVIEW / AUTO
```

约束：

- API Key 仅从服务端环境变量读取，不下发前端、不写日志、不进入运行证据 JSON。
- 开发和测试配置默认 `OFF`；没有 Key 时应用仍可启动，但不能进入真实 Jev 模式。
- `SHADOW` 只记录结果；`REVIEW` 只生成审核任务；`AUTO` 允许通过校准门槛的候选进入自动映射。
- `max-concurrency` 与候选上限先保守配置，再依据延迟、429 比例和配额测量调整。
- 生产固定 `jev-1.13.0`；模型升级通过新运行模式、独立评测和显式配置变更完成。

## 9. 错误处理与降级

| 场景 | 处理 | 是否重试 |
|---|---|---:|
| 未配置 API Key | 健康状态 `DISABLED`；OFF 可运行，其他模式启动校验失败 | 否 |
| 401 | 标记运行配置失败，停止新请求并告警 | 否 |
| 422 | 保存脱敏后的契约错误和 questionVersion，运行失败 | 否 |
| 429 | 尊重 `Retry-After`，否则指数退避并加入抖动 | 是 |
| 529 / 5xx | 指数退避；达到上限后候选转 REVIEW | 是 |
| 连接/读取超时 | 有限重试；耗尽后候选转 REVIEW | 是 |
| 响应缺问题或类型错误 | 契约失败，不使用部分答案 | 否 |
| 图修订已变化 | 判断标记过期，重新召回和评分 | 否 |

禁止降级回“同名自动合并”或“Jaccard 超阈值自动合并”。模型不可用时，系统宁可产生待审记录或保留重复节点，也不能制造误合并。

Jev 调用放在数据库事务之外。只有候选和决策已持久化、图修订仍匹配时，才由 `MergeProjectionService` 开启短 Neo4j 事务应用映射。

## 10. 可观测性

新增 Micrometer 指标：

```text
graphnexus.typesafe.requests{model,questionVersion,outcome}
graphnexus.typesafe.duration{model,questionVersion}
graphnexus.typesafe.tokens{direction=input|output}
graphnexus.typesafe.cache.hits
graphnexus.typesafe.retries{status}
graphnexus.typesafe.failures{reason}
graphnexus.kp.alignment.decisions{band,subject,stage}
graphnexus.kp.alignment.review.queue
```

日志至少关联 `mergeRunId`、`candidateId`、`mentionId`、`canonicalKpId`、`model`、`questionVersion`、`stateHash`、耗时和结果类型。不得记录 Authorization header，不默认打印完整教材内容或学生信息。

运营报告按运行输出：

- 候选对数量、每个 mention 的候选分布和截断数量；
- Jev 请求数、缓存命中率、token、估算费用和 P50/P95 延迟；
- AUTO/REVIEW/INDEPENDENT 比例；
- 模型错误、超时、429/529 和降级比例；
- 教师批准率、拒绝率、撤回率；
- 按置信度区间统计的准确率和自动合并精确率。

## 11. 实施阶段与任务拆分

### J0：基线与决策冻结

依赖：上游 P0。

| 任务 | 内容 | 完成标准 |
|---|---|---|
| J0-1 | 冻结中文知识点候选对标注集，覆盖同名异义、异名同义、上下位、前后置和缺上下文 | 每对有 `same/review/different` 标签、审核人和理由 |
| J0-2 | 固定 `kp-alignment-v1` 问题集与字段规范 | JSON fixture、问题版本和变更规则入库 |
| J0-3 | 记录当前同名/Jaccard 基线 | 输出自动合并精确率、召回率、误合并和审核量基线 |

### J1：HTTP 契约闭环

可与上游 P1 并行，但不接生产链路。

| 任务 | 建议文件 | 完成标准 |
|---|---|---|
| J1-1 | `TypeSafeProperties`、dev/prod/test 配置 | 默认 OFF；Key 不存在仍可启动；非 OFF 时配置校验明确 |
| J1-2 | `SystemOneRequest/Response` DTO | 覆盖 Score、Noul、usage 和 model 字段 |
| J1-3 | `TypeSafeJevClient` | `RestClient` 调通固定响应；401/422/429/529/超时行为通过测试 |
| J1-4 | 响应校验与异常分类 | 缺问题、非法概率、类型错配均不能进入业务决策 |

### J2：领域判断闭环

依赖：J1、上游 P1 数据模型。

| 任务 | 内容 | 完成标准 |
|---|---|---|
| J2-1 | 新建 `KpPairJudge` 端口和领域模型 | 应用层不依赖 TypeSafe DTO |
| J2-2 | 实现 `JevKpPairJudge` | 输入一个候选对，输出完整 `KpPairJudgment` |
| J2-3 | state 规范化、哈希与缓存 | 相同输入命中缓存；任一字段/版本变化均失效 |
| J2-4 | `HardConflictChecker` | 学科、学段、拒绝映射、自环和图修订规则单测通过 |
| J2-5 | `KpDecisionPolicy v1` | 固定结果可重复地产生三段决策和原因码 |

### J3：SHADOW 接线

依赖：上游 P2 的候选召回和账本。

| 任务 | 内容 | 完成标准 |
|---|---|---|
| J3-1 | `GraphConstructedEvent` 改为 mention ID 载荷 | 同名来源可独立追踪，事件幂等 |
| J3-2 | `KpAlignmentService` 编排候选和 Jev 请求 | 每个候选状态、失败原因和证据可查询 |
| J3-3 | 模式开关 `OFF/SHADOW` | SHADOW 不写规范映射、不改变正式图 |
| J3-4 | 指标、运行摘要和成本记录 | 一次运行可核对请求数、token、缓存与延迟 |

### J4：中文标注集评测与校准

依赖：J3。

对同一候选集至少比较：

1. 当前同名 + 字符 Jaccard；
2. 上游五项确定性特征方案；
3. Jev `link_state`；
4. 硬规则 + Jev + confidence gate 的最终策略。

报告至少包含：

- 三分类混淆矩阵；
- `same` 的 precision/recall，尤其是自动合并 precision；
- 同名异义误合并数和异名同义漏合并数；
- 分学科、学段、上下文完整度的结果；
- confidence 分桶后的准确率与样本量；
- 重复调用的一致性；
- 平均/P95 延迟、请求数、token 和估算费用；
- 模型/问题版本变化前后的回归差异。

若中文样本的自动合并精确率未达到业务门槛，Jev 仍可停留在审核排序和解释用途，不进入 AUTO。

### J5：REVIEW 模式

依赖：J4、上游 P3 教师审核接口与页面。

| 任务 | 内容 | 完成标准 |
|---|---|---|
| J5-1 | REVIEW 决策持久化 | 中间态、低置信度和服务失败均进入明确队列 |
| J5-2 | 审核证据展示 | 展示两个知识点、硬规则、确定性特征、Jev 三档概率及 Noul 信号 |
| J5-3 | 并发和过期保护 | 审核期间图修订变化时返回冲突并要求刷新 |
| J5-4 | 教师反馈闭环 | 批准/拒绝进入不可覆盖的决策历史，可导出用于复评 |

### J6：LIMITED_AUTO

依赖：REVIEW 稳定运行、自动合并门槛通过、回滚闭环完成。

先按单一学科和学段灰度，只允许：上下文完整、无硬冲突、规范组一致、Jev 为 same 且达到已校准门槛的候选自动映射。观察期内持续监控教师撤回率、下游诊断变化和回滚成功率，再决定是否扩大学科范围。

任何以下条件触发自动降级回 REVIEW：

- Jev 模型版本、问题版本或状态字段发生未评测变化；
- 自动合并精确率或教师撤回率越过告警门槛；
- TypeSafe 错误率、超时或 429/529 持续异常；
- 图修订冲突或回滚失败；
- 数据分布显著变化，例如新学科、新学段或大量上下文缺失来源。

## 12. 测试计划

### 12.1 客户端契约测试

- 正常 Score + Noul 响应映射。
- 401、422 不重试；429、529 和瞬态 5xx 指数退避。
- `Retry-After` 存在时优先采用服务端建议。
- 连接超时、读取超时、空响应、非法 JSON、缺问题、重复/未知问题。
- score、confidence、Noul、probabilities 越界及概率和非法。
- 响应实际 model 与请求 alias/固定版本的审计记录。

测试使用 `MockRestServiceServer` 或等价的本地 HTTP stub，不要求 CI 访问真实 TypeSafe。

### 12.2 领域单元测试

- 同名同义、同名异义、异名同义、上下位、前后置、章节相邻但概念不同。
- 学科/学段硬冲突优先于模型结果。
- description、chapter、neighbor 缺失时不会自动合并。
- 低 confidence、Noul 接近 0.5 和不同概率分布的正确路由。
- 服务异常不回退到旧的同名/Jaccard 自动合并。
- 相同 stateHash 与版本命中缓存；版本变化重新调用。

### 12.3 编排与持久化测试

- 一个 mention 多候选时分别判断，不用单链接并查集合并整个组。
- 运行和候选创建幂等，重试不重复建单或重复扣用量记录。
- SHADOW 不改变 Neo4j 正式映射。
- REVIEW 批准/拒绝包含乐观锁或图修订检查。
- AUTO 应用前再次检查候选、决策和图修订仍有效。
- MySQL 成功但 Neo4j 失败、Neo4j 成功但 MySQL 完成态更新失败时可幂等恢复。

### 12.4 回归测试

- 教材删除不误删其他来源共享的规范知识点。
- 成绩 MASTERS 按 canonical ID 归属，不因别名或同名选错节点。
- 前置边重写后无坏端点、自环、重复边和方向反转。
- 问答剪枝、教学 Agent 和图可视化在规范 ID 切换后结果可解释。
- Jev OFF 时，系统仍能完成导入和候选记录，但不执行不安全的自动合并。

## 13. 验收门槛

### 13.1 工程门槛

- API Key 不泄露，客户端契约和错误分类测试通过。
- 所有远程调用位于数据库事务之外。
- 每个判断可追溯到 mention、candidate、stateHash、model、questionVersion 和完整概率。
- OFF、SHADOW、REVIEW、AUTO 四种模式行为与定义一致。
- Jev 不可用时不触发旧规则自动合并。
- 缓存、重试、并发上限、候选上限和 Micrometer 指标生效。

### 13.2 质量门槛

上线 REVIEW 前：

- 中文标注集版本固定，测试集未参与问题或门槛调优；
- 三分类、同类 precision/recall、confidence 分桶和错误案例已出报告；
- 证明 Jev 证据能降低教师判断成本或提升候选排序质量。

上线 LIMITED_AUTO 前：

- 自动合并 precision 达到项目预先批准的门槛；
- 同名异义和链式误合并关键集合无自动误合并；
- REVIEW 观察期的教师撤回率、回滚成功率和下游诊断回归达标；
- 模型、问题、阈值、特征和数据集版本均已锁定；
- 完成关闭 AUTO、切回 REVIEW 和按运行回滚的演练。

## 14. 风险与待确认事项

1. **中文准确率**：Jev 对 CJK 的效果必须由本项目数据证明；不能以英文 cookbook 结果外推。
2. **章节数据质量**：当前 KP 缺少可靠章节关联，P1 未完成前模型 state 不完整，自动合并不可开放。
3. **双重计分**：若确定性描述相似度与 Jev 同时读取相同文本，不能未经校准简单加权，避免重复放大同一证据。
4. **候选规模**：必须先召回小集合，禁止全学科两两调用 Jev。候选上限和截断应可观测。
5. **外部数据传输**：首版只发送知识点元数据，不发送学生姓名、学号、成绩、考试明细或完整教材；上线前完成数据合规确认。
6. **版本漂移**：生产固定模型 ID；升级模型或问题集时先跑 SHADOW 对照，不覆盖旧证据。
7. **成本与配额**：以真实 usage 统计为准；缓存、候选上限和并发控制是上线必要条件。
8. **人工审核能力**：若教师审核页面和流程尚未可用，服务失败或不确定候选必须保持独立，不能进入无处处理的隐式待审状态。

## 15. 推荐开工顺序

建议以两个独立闭环推进：

1. **先完成 J1 HTTP 契约闭环**：不依赖图改造，用固定中文候选对验证 Java DTO、RestClient、响应校验、重试和指标。
2. **并行完成上游 P1 来源模型**：消除同名预合并并建立 mention/canonical 身份。
3. **二者完成后实施 J2/J3**：把 Jev 接入候选评分 SHADOW，不写正式图。
4. **以 J4 标注集报告作为闸门**：报告通过后才进入 REVIEW；REVIEW 观察通过后才讨论 LIMITED_AUTO。

最小工程闭环是 J1；最小业务闭环是 J3；最小可上线闭环是 J5。J6 自动合并属于经过数据验证后的后续能力，不是首版交付条件。
