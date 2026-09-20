# K12-KGraph 单册最小图谱质量评测与对照实验实施方案

状态：实施方案；本文未执行数据转换、模型调用或评测。

## 1. 目标、假设与范围

本方案假设已经取得两份相互对应的输入：

1. [K12-KGraph 的 `math_7a_rjb.json` 单册样例图](https://github.com/haolpku/K12-KGraph/blob/main/demo/kg/math_7a_rjb.json)，本轮**暂按参考金标**使用。
2. 同版次人教版七年级数学上册的完整解析文本，能作为 GraphNexus 的抽取输入。

[K12-KGraph demo 清单](https://github.com/haolpku/K12-KGraph/blob/main/demo/manifest.json)给出全图 157 个节点、285 条边。样例图同时含有概念、技能、习题、章节等节点和多种边；这两个全图数量**不是**本轮知识点、前置边指标的分母。

最小问题只有两个：GraphNexus 从该册文本中抽出了多少参考知识点？抽出的有向前置关系与参考图有多一致？本轮只评 `Concept`/`Skill` 节点与两端均属于这些节点的 `prerequisites_for` 边。定义、公式、习题、章节归属、证据质量、强度和其他关系类型留待后续评测。

这里的“金标”是一次可复现试验的**参考答案**，并不表示公开样例已被独立逐条裁决。结论应表述为“相对 K12-KGraph 样例图的一致性”，不能直接表述为教材图谱的绝对正确率。

## 2. 从原始数据到可复现输入

### 2.1 冻结两份原始数据

| 输入 | 必须记录 | 用途 |
|---|---|---|
| K12 原始 JSON | 上游仓库 commit、原文件 SHA-256、下载地址 | 金标转换的唯一来源；保留原件，不原地修改 |
| 教材解析文本 | 出版社、版次、册次、解析器版本、文本 SHA-256；有条件时保留页码/章节对应 | A、B 两组共同的抽取输入 |

先检查解析文本是否覆盖整册，章节顺序是否正确，是否混入目录、页眉页脚或其他册内容。若只有部分章节文本，必须按同一章节范围过滤参考图；**不能用部分输入对整册金标计算召回率**。金标名称、关系和答案不得进入任何抽取提示词。

建议把原始文件、转换文件和报告分开存放，并在每次报告中写入两份 SHA-256，避免后来文本或公开仓库更新造成不可复现的分数。

### 2.2 最小对象映射

| K12 原始图 | GraphNexus 候选图 | 本轮处理 |
|---|---|---|
| `nodes[].label == "Concept"` | `KnowledgePointNode` | 纳入知识点金标；保留原 `id`、`name`、定义 |
| `nodes[].label == "Skill"` | `KnowledgePointNode` | 纳入知识点金标；保留原 `id`、`name`、说明 |
| `edges[].type == "prerequisites_for"` | `PREREQUISITE_OF` | 仅两端都在上述节点集合中时纳入；方向均为前置知识 `source → target` |
| `Exercise`、`Chapter`、`Section` 等 | 习题、文件或其他节点 | 不纳入本轮分母 |
| `relates_to`、`is_a` 等 | 其他关系 | 不纳入本轮分母；不能当成前置关系误报 |

这个映射先解决“对象是不是同一种东西”，再讨论准确率。特别是不能将 K12 全部 285 条边作为 GraphNexus 前置边的召回分母。

## 3. 从原始图转换成最小参考金标

### 3.1 转换规则

用一个独立适配器读取原始 `nodes` 和 `edges`，按以下顺序处理：

1. 取 `Concept`、`Skill` 节点，形成 `goldNodeIds`；沿用原始节点 ID，不按数组顺序重新编号。
2. 检查节点 ID、名称非空，ID 唯一；规范化名称相同但 ID 不同的情况列为歧义，不能无提示地合并。
3. 仅保留 `type == "prerequisites_for"` 且 `source`、`target` 都在 `goldNodeIds` 中的边；边的方向保持原样。
4. 检查边的端点、自环、重复边和前置子图环路。若不满足现有金标加载器的要求，记录为转换失败或人工待裁决项，不能为了让分数可算而静默删除参考边。
5. 输出筛选后节点数、筛选后边数、排除原因统计；这两个筛选后数量才是知识点和前置边召回分母。

### 3.2 输出 GraphNexus 现有格式

[`GoldDatasetLoader`](../src/main/java/com/graphnexus/application/evaluation/graph/gold/GoldDatasetLoader.java)要求同一目录下有四个 JSON 文件，可设版本目录为 `k12-math-7a-v1/`：

```text
k12-math-7a-v1/
  topics.json
  dependencies.json
  clusters.json
  manifest.json
```

字段示意；占位字符串须在转换时替换为原始图中的真实值，数量由实际筛选结果生成。

`topics.json`：

```json
{"topics":[{"id":"math_7a_rjb_cpt1","name":"正数","type":"Concept","domain":"数学","description":"<从 properties.definition 复制>"}]}
```

`dependencies.json`：

```json
{"dependencies":[{"prerequisiteId":"原边 source","topicId":"原边 target","strength":""}]}
```

`clusters.json` 写为 `{"clusters":[]}`。`manifest.json` 至少写出转换后真实的 `typeDistribution`，其中 `Concept` 和 `Skill` 的值必须是程序统计出的整数；来源 commit、哈希、筛选规则版本可加在其他字段或旁路元数据中。`strength` 留空，因为参考图与生产抽取的强度口径不能直接比较。通过 [`EvaluationProperties`](../src/main/java/com/graphnexus/application/evaluation/graph/config/EvaluationProperties.java) 注册 `dataset-root` 和 `datasets.k12-math-7a-v1.directory`，不要依赖默认的八年级数据目录。

转换完成后，应能通过 `GoldDatasetLoader.load("k12-math-7a-v1")` 加载；同时核对加载得到的 `gold.topics().size()`、`gold.internalDependencies().size()` 与适配器统计一致。当前加载器会校验前置子图无环；若参考图不满足该条件，应先列出具体冲突边并决定裁决办法，保留原始文件和处理记录。

## 4. 用当前生产流程生成候选图

1. 用现有 [`EvaluationTextChunker`](../src/main/java/com/graphnexus/application/evaluation/graph/builder/llm/EvaluationTextChunker.java)对**完整**教材文本切片。初次沿用配置默认值：约 2500 字符一片、相邻片重叠 200 字符；记录实际片数、片 ID、参数与文本哈希。
2. 对每片调用生产 [`ExtractionService.extract(...)`](../src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java)，学科为“数学”，文档名与册次固定。该方法返回知识点和通过质量门禁后的边；最小评测只需内存结果，不需保存到 Neo4j。
3. 把每片 `knowledgePoints` 转为 `CandidateTopic`，把 `edgeType == "PREREQUISITE_OF"` 且两端都是知识点的边转为 `CandidateDependency`。临时 ID 加片 ID 前缀，边的两端使用相同前缀后的 ID。候选知识点没有与 K12 `Concept`/`Skill` 完全一致的类型字段，因此本轮不评价类型准确率。
4. 汇总全部片形成一个**未预先规范化**的 `CandidateGraph`，交给 [`StrictGraphEvaluator.evaluateReport`](../src/main/java/com/graphnexus/application/evaluation/graph/core/StrictGraphEvaluator.java)处理。评测器内部会规范化名称、合并重复节点与边；提前规范化可能使原始重复和坏边统计消失。
5. 任一切片抽取失败，就把整册运行标为失败；不能跳过切片后仍报告整册召回。记录调用次数、模型/提示词版本与耗时。

现有 [`GraphEvaluationServiceImpl.evaluateManual`](../src/main/java/com/graphnexus/application/evaluation/graph/service/impl/GraphEvaluationServiceImpl.java)会把结果写入 Neo4j，最小离线 runner 直接调用加载器和评测器即可。生产 `ExtractionService` 在返回前已经过滤一部分无效边，因此报告应把“门禁后候选图”标清；若后续需要精确分析门禁前坏边，可再增加结构化日志或原始结果导出。

逐片抽取通常无法直接提出**跨切片**的前置边，这会压低整册关系召回率。A、B 使用同样的切片时仍可做配对比较，但报告必须标注这一共同限制；如果已有章节或证据位置可定位参考边，还应单列跨切片金标边数，不把全部此类 FN 解释为提示词错误。

## 5. 最小质量指标与错误明细

对知识点与有向前置边分别给出 `TP`、`FP`、`FN`、`Precision`、`Recall`、`F1`，其中 `Precision = TP / (TP + FP)`，`Recall = TP / (TP + FN)`，`F1 = 2PR / (P + R)`；分母为零时在报告中标为不适用，不把 `0/0` 当作满分。

| 指标 | TP 判定 | 主要错误 |
|---|---|---|
| 知识点严格匹配 | 候选名称经现有 [`TopicNameNormalizer`](../src/main/java/com/graphnexus/application/evaluation/graph/core/TopicNameNormalizer.java)规范化后，与一个参考节点一对一相同 | 未匹配候选为 FP；未匹配参考节点为 FN |
| 有向前置边匹配 | 两端知识点都已**严格匹配**，且映射后的源 ID、目标 ID 与参考边同向一致 | 反向边、端点未匹配边、额外边、漏边分别列账 |

松弛名称匹配仅用于发现“同义名称导致的严格漏判”，不在看过结果后改动金标或主指标。报告应附上规范化前后节点/边数量及错误清单。现有 [`MetricCalculator`](../src/main/java/com/graphnexus/application/evaluation/graph/core/MetricCalculator.java)的 `qualityScore` 包含本轮不具可比性的类型准确率，不能作为 A/B 的唯一判断值。

最少人工复核：检查 A、B 有分歧的前置边，并抽查知识点 FP/FN 是否为别名、教材版本差异或参考图缺漏。复核意见单独保存，不回写本轮冻结金标。

## 6. A/B 对照实验：只改变前置边数量要求

### 6.1 两组配置

| 项目 | A：当前生产基线 | B：去数量要求版本 |
|---|---|---|
| 教材文本与切片 | 同一快照、同一片 ID 和顺序 | 与 A 完全相同 |
| 模型、参数、解析器、质量门禁 | 当前配置 | 与 A 完全相同 |
| 金标、候选图转换、评分器 | 同一冻结版本 | 与 A 完全相同 |
| 唯一有意变化 | 原样使用 [`extraction-system.md`](../src/main/resources/prompts/extraction-system.md) | 仅删除“每个知识点尽量有前置边”“至少 6 条”“覆盖率 60% 以上”等数量要求；无充分依据时允许输出空前置边数组 |

当前提示词的前置边数量要求位于“识别策略”和输出要求两处，必须一起处理，才真正完成这一个因素的消融。其他关系定义、few-shot、输出 JSON 结构均不修改。不要在一次运行中让同一个 `ExtractionPromptBuilder` 缓存实例切换两版模板；可用明确的模板版本参数或两个独立进程。A、B 各记录模板 SHA-256。

### 6.2 运行与配对

1. **实验登记先于运行**：固定实验 ID、要验证的假设（去掉数量要求是否减少无依据的前置边）、A/B 模板文件及逐行 diff、文本/金标/代码哈希、切片清单、模型与参数、主指标、保护指标和选用规则。金标不进入提示词。登记后的变更另起实验 ID。
2. 对每个相同切片分别运行 A 和 B，两组必须覆盖完全相同的片 ID 集合。若模型支持固定温度和随机种子，两组采用相同设置；即使种子相同，也仍按随机输出记录结果。重复试验可交替 A/B 调用顺序，降低服务状态随时间变化的影响。
3. 每组各自合并整册候选图、规范化、评分；**不对逐片 F1 求平均**。同一教材的一次 A/B 只能算一个配对样本，不能把大量切片当作彼此独立的教材样本。
4. 最小烟测每组运行一次。若用于选择生产提示词，建议每组至少重复 3 次，逐次保留结果，并报告中位数和范围；模型版本若在实验中变化，该次配对作废。重复运行不得在看到结果后调整提示词或金标。

### 6.3 实验记录：从提示词版本到每条差异边

建议每次实验产出以下四类机器可读文件：每个 `pair_id` 恰好关联一个 A 组 `run_id` 和一个 B 组 `run_id`，两组各自覆盖相同的切片清单。

| 文件 | 最少字段 | 作用 |
|---|---|---|
| `experiment.json` | `experiment_id`、假设、A/B 模板路径与 SHA-256、提示词 diff、代码 commit、原始图/转换金标/文本 SHA-256、切片参数和切片 ID 清单、模型版本与参数、预定判断规则 | 固定所有控制变量和唯一有意变化 |
| `chunks.jsonl` | `pair_id`、`run_id`、组别、切片 ID/哈希、开始时间、耗时、调用次数、解析状态、原始/门禁后知识点与前置边数量、拒绝边原因；有条件时保存原始模型响应 | 定位具体切片的生成、解析或质量门禁差异 |
| `candidate.json` 与 `metrics.json` | 去重前后候选图、金标计数、节点/边 TP/FP/FN 与 P/R/F1、反向边和端点未匹配边数量、运行失败原因 | 复算整册分数，防止只保留最终百分比 |
| `edge-delta.csv` | 规范化后的源/目标名称或金标 ID、A/B 是否输出、是否命中金标、错误类别、参考边证据、教材原文位置、人工复核结论 | 解释改动究竟删除了误报还是造成漏报 |

差异边要在**名称规范化并映射到金标 ID 后**比较；A、B 生成的随机节点 ID 不可直接比较。端点仍无法匹配金标的边，使用规范化名称成对展示，并标记 `UNMATCHED_ENDPOINT`。至少区分 `A_ONLY`、`B_ONLY`、`BOTH` 三类；对 `A_ONLY`、`B_ONLY` 逐条标明 TP/FP/FN 影响。若 `chunks.jsonl` 要包含原始响应或门禁前数量，需要给当前 `ExtractionService` 增加评测专用捕获点；现有返回值只有门禁后图，不能从它反推被拒边明细。

逐片记录用于排错；整册候选图和统一金标用于主分。任何切片失败，都将该组整册运行标记为失败，并保留失败片记录，不把缺片结果写成有效的 Recall。

### 6.4 对照报告与判读

| 指标 | A | B | `B - A` | 判读重点 |
|---|---:|---:|---:|---|
| 知识点 TP/FP/FN、严格 P/R/F1 |  |  |  | 提示词变化是否意外影响节点抽取 |
| 前置边 TP/FP/FN、有向 P/R/F1 |  |  |  | 主结果；精确率和召回率一起看 |
| 反向边、端点未匹配边、额外边 |  |  |  | 识别新增或消失的错误类型 |
| 候选知识点数、候选前置边数 |  |  |  | 防止“少输出边”造成单一精确率虚高 |
| 门禁拒绝边数及原因 |  |  |  | 判断关系质量变化是否被门禁掩盖；未捕获门禁前输出时标为“不可得” |
| 失败切片数、调用次数、耗时 |  |  |  | 完整性和成本 |

主表列出**每一次配对**的 A、B 和 `B - A`，再汇总重复运行的中位数与范围，不只展示最好的一次。重点核查“A 输出而 B 未输出”和“B 新增”的边：B 删除的是错误边还是金标真边？若 B 精确率提高、召回率下降，应列出丢失的具体参考边后再判断。只有同时查看原始计数、F1、错误类型和人工复核结果，才能解释分数变化。公开样例仍可能有错漏，因此报告保留“参考图争议”一栏。

### 6.5 预先写定的结论规则

在 `experiment.json` 中先声明**主指标**（建议有向前置边 F1，并同时展示 Precision/Recall）及**保护指标**（知识点 F1、失败切片数、候选边数量）。还应事先写明可接受的召回下降或节点质量下降范围；具体阈值由项目业务风险确定，不能按本次结果倒推。判读顺序为：

1. 两组输入、模型、切片和金标是否一致，是否均完成全部切片；不满足则不给出胜负结论。
2. 对比关系 TP/FP/FN、Precision/Recall/F1 与候选边数，识别“少输出边导致 Precision 上升”的情况。
3. 对照 `edge-delta.csv` 和教材原文，确认差异边中真实误报减少与真实漏报增加的数量。
4. 检查知识点 F1、失败率和重复运行波动；只有主指标改善且保护指标满足预设规则，才建议选用 B。一次整册运行只支持初步判断，不能据小幅差异宣称普遍改进。

如果需要多轮改提示词，先用预定章节做开发，另留未用于改写提示词的章节作最后一次验证；开发与验证均须把文本和金标限制在各自相同的章节范围，并单列跨范围参考边。每轮保存新的提示词版本和实验 ID，不用最终验证章节反复调参。

可选加入 [`NlpGraphBuilder`](../src/main/java/com/graphnexus/application/evaluation/graph/builder/nlp/NlpGraphBuilder.java)作为第三种方法基线，但它与 A/B 的差异不止提示词，须单独标为跨方法比较，不混入上述单因素结论。

## 7. 建议的最小实现清单与验收

建议实现一个 K12 原始图适配器、一个生产抽取结果适配器、一个仅运行本试验的离线 runner，以及 JSON/CSV 报告导出。离线 runner 复用已有 `GoldDatasetLoader`、`ExtractionService`、`StrictGraphEvaluator`，不复用会写库的服务入口。现有 [`TextbookMarkdownEvaluationTest`](../src/test/java/com/graphnexus/evaluation/TextbookMarkdownEvaluationTest.java)可参考切片和汇总方式，但必须处理整册，且评测器只对原始候选图规范化一次。

验收条件：

- 原始数据与转换数据的来源、哈希、数量及筛选规则可追溯；金标加载后的节点/边数等于适配器筛选数。
- A、B 输入的文本哈希、切片 ID 列表、模型参数及评分器版本相同，提示词差异有明确 diff 和哈希；`experiment.json` 在运行前写定主指标、保护指标与判定规则。
- 两组都完成全部切片；`TP + FN` 等于筛选后参考数量，`TP + FP` 等于规范化后候选数量。
- 报告包含每次配对的逐项计数、指标、差值、重复运行波动、`edge-delta.csv`、失败或门禁日志摘要，以及对有分歧前置边的原文复核记录。

完成这些交付物后，才能得到一次可复现的最小对照结论；扩展到定义、习题、证据、分类和教师裁决时，再按完整方案增加独立金标与指标。
