# GraphNexus：四方案对照评测与生产接入实施路线

日期：2026-09-13　　性质：实施方案；尚未改动 GraphNexus 仓库

## 先定比较对象

四组候选为：①当前**生产** LLM 教材抽取；②现有规则 `NLP_NER_RE`；③ BERT-BiLSTM-CRF 知识点识别＋有向关系分类器；④ CasRel 三元组抽取。统一输入一份教材的相同文本快照，统一输出 `CandidateGraph(topics, dependencies)`，其中依赖方向均为 `前置知识点 → 后续知识点`。

须把“**完整图谱效果**”和“**关系模型效果**”分别报告。BiLSTM+CRF 只做序列标注，必须另接关系分类器；CasRel 只找到参与关系的实体，不能单独保证发现所有孤立知识点。完整图谱对比时为各方案明确补足这一步，并在报告中标明组件；关系阶段另用人工金标实体作条件化实验（CasRel 以金标主体 span 运行关系特定客体标注），分析上游漏识别的影响。条件化实验是诊断指标，正式选型仍看端到端结果。[BiLSTM-CRF 论文](https://arxiv.org/abs/1508.01991) · [CasRel 论文](https://aclanthology.org/2020.acl-main.136/)

## 1. 做一份可训练、可复核的教材金标

1. 选取覆盖不同章节、年级、版式的真实教材；保留文档 ID、学科、年级、教材版本、文档哈希、页码、段落 ID、原文与字符偏移。PDF 解析后的文本须固化为不可变快照，四组方案都读取这份快照。
2. 教师/标注员标出知识点提及 span、规范知识点 ID、知识点类型，以及有向前置边和支持该边的原文句子。把“原文明确支持”和“基于教学经验推断”分成两个标签；首轮模型只比较前者。
3. 金标包含没有知识点或没有前置关系的句子、多个关系共享知识点的句子、公式跨行、标题、OCR 错字和否定句。没有关系的句子不可过滤，否则会低估误报。[关系抽取评测研究](https://aclanthology.org/2023.genbench-1.1/)
4. 按**教材或章节分组**划分训练、验证、锁定测试集，不能把同一段落的改写或相邻切片分到训练和测试两边。两名标注员独立标注争议样本，由学科教师裁决；记录标注规范和数据版本。
5. 现有 `GoldDatasetLoader` 读取 `topics.json`、`dependencies.json` 等结构金标，可用于图谱指标；另外增加句子级标注（推荐 JSONL）供 NER/CasRel 训练与证据核对。仓库默认金标根目录为 `pep-math-taxonomy/data`，当前结算前须核实实际数据位置与注册配置。[加载器](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/gold/GoldDatasetLoader.java:30) · [配置](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/config/EvaluationProperties.java:17)

句子级记录可以采用以下形态；`start/end` 是原文左闭右开字符区间，标注工具应校验 `text[start:end]` 与标注词一致。

```json
{
  "documentId": "book-01",
  "segmentId": "p3-s8",
  "subject": "数学",
  "text": "学习二次函数顶点坐标前，先掌握配方法。",
  "mentions": [
    {"start": 2, "end": 10, "kpId": "kp-vertex"},
    {"start": 15, "end": 18, "kpId": "kp-square-completion"}
  ],
  "relations": [{
    "sourceKpId": "kp-square-completion",
    "type": "PREREQUISITE_OF",
    "targetKpId": "kp-vertex",
    "evidenceSegmentId": "p3-s8",
    "evidenceKind": "EXPLICIT"
  }]
}
```

## 2. 固定共同输入、输出和运行条件

- 复用 [`GraphBuildContext`](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/model/GraphBuildContext.java:5) 的 `textSnapshot/textHash/runId`。金标与模型共享同一原文和位置映射；为保留真实生产基线，生产 LLM 沿用现有切分方式，其余模型可以按页分句，但要记录预处理版本并在报告中披露差异。
- 四组分别实现 [`GraphBuilder`](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/builder/GraphBuilder.java:8)，扩展 `GraphBuildMethod` 为 `PRODUCTION_LLM`、`RULE_NER_RE`、`BERT_BILSTM_CRF_RE`、`CASREL`。模型版本、提示词哈希、预处理版本、训练数据版本和随机种子写进运行记录。
- 输出统一经知识点规范化：同一学科内做名称/别名到金标规范 ID 的映射；歧义映射单独计错，不借人工测试答案给模型。关系候选统一转成 `(sourceKpId, PREREQUISITE_OF, targetKpId)`。
- LLM 固定模型、提示词、参数和调用重试规则；记录实际用量与耗时。神经模型只用训练集拟合，验证集调阈值，测试集只运行一次正式比较。所有方法保留原始输出，便于复查错误。

**生产 LLM 基线不能误用评测专用 LLM 构建器。**生产 [`ExtractionService`](D:/GraphNexus/src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java:87) 使用全量抽取提示词和 `ExtractionRawResult`；现有 [`LlmGraphBuilder`](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/builder/llm/LlmGraphBuilder.java:27) 使用另一份仅含知识点与依赖的评测提示词。应为生产抽取结果做一个**只读适配器**转换为 `CandidateGraph`；评测专用 LLM 可另列第五组，但不能冒充“现有生产 LLM”。基线运行后冻结结果，再单独比较去除关系数量硬要求的改进提示词。[生产提示词](D:/GraphNexus/src/main/resources/prompts/extraction-system.md:34) · [评测提示词](D:/GraphNexus/src/main/resources/prompts/evaluation-extraction-system.md:1)

## 3. 实现四个构建器

| 组别 | 具体实现 | 需要说明的能力边界 |
|---|---|---|
| 生产 LLM | 调用当前 `ExtractionService.extract()`，把 `KnowledgePointNode` 和 `PrerequisiteEdge` 转换为 `CandidateGraph`；离线评测不触发 `ConstructionServiceImpl` 入图 | 它还产出分类与对齐，但这次只计知识点和前置边 |
| 现有规则 | 直接使用当前 `NlpGraphBuilder`、`RuleBasedNerExtractor` 和 `RuleBasedRelationExtractor` | 这是规则基线，不称为训练式 NER；保留其零 API 成本优势 |
| BERT-BiLSTM-CRF＋RE | 中文 BERT 编码，BiLSTM+CRF 输出 BIO/BIOES 知识点 span；同句候选实体对交给 BERT 有向关系分类器，标签至少有 `PREREQUISITE_OF`、反向与 `NONE`；再做知识点规范化 | NER 漏掉实体会传导到 RE；需分别报告 NER、给定金标实体的 RE 和端到端结果 |
| CasRel | 中文编码器＋CasRel 主体/关系特定客体标注，输出有向三元组；与共同的知识点候选发现组件合并，保证孤立知识点也可进入完整图谱对比 | CasRel 的知识点分数必须标注“CasRel＋共同发现组件”；其自身只评三元组端点和关系，不冒充全量知识点识别器 |

神经模型可由独立 Python 推理服务提供 HTTP 接口，Java 侧建薄适配器转换为 `GraphBuildResult`；推理服务与训练脚本保存同一模型版本号。先离线跑通，不在生产教材构建时同步依赖新服务。

## 4. 接通 GraphNexus 现有评测能力

目前 [`GraphBuilderRegistry`](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/builder/GraphBuilderRegistry.java:18) 能路由构建器，但 [`GraphEvaluationService`](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/service/GraphEvaluationService.java:7) 和公开接口只有手工候选图评测。增加一个离线批量 runner：按固定数据版本遍历文档及四种 builder，保存每份 `GraphBuildResult`，调用 [`StrictGraphEvaluator`](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/core/StrictGraphEvaluator.java:33)，最终聚合每方法的逐文档结果。初期用 CLI/受控内部任务即可，不急于暴露公网运行接口。

至少输出两张成绩单：

| 成绩单 | 计算内容 |
|---|---|
| 完整图谱 | 规范知识点严格 Precision/Recall/F1；有向前置三元组 Precision/Recall/F1；方向准确率；结构有效性；每份教材耗时与成本 |
| 分阶段诊断 | 实体 span F1、规范化准确率；给定金标知识点时的关系 F1；无关系句误报率；重叠三元组 F1；有证据边比例 |

主决策指标是**端到端前置关系精确率与下游诊断正确性**，不能只按 [`MetricCalculator`](D:/GraphNexus/src/main/java/com/graphnexus/application/evaluation/graph/core/MetricCalculator.java:17) 的加权 `qualityScore` 排名：错误前置边比少量漏边更可能误导薄弱点归因。用按教材聚类的 bootstrap 置信区间比较四组差异；展示逐章错误实例，不仅展示平均分。

## 5. 根据结果决定接入方式

不要预先规定“一个模型接管整套图谱”。按模块选型：知识点识别胜者负责候选发现，关系抽取胜者负责前置边；若生产 LLM 在某项继续最好，就保留该项。入生产前需要把两项输出转换回完整 `ExtractionRawResult` 或引入更稳定的中间表示，以兼容现有分类、对齐和扩展节点。

接入顺序为 **OFF → SHADOW → REVIEW → LIMITED_WRITE**：影子运行只比对不写图；审核模式保存候选、来源句和模型版本；有限写入只对经过验证的学科/文档启用。所有正式边在**知识点规范化与端点 ID 重映射之后**通过 `GraphQualityValidator`，并核对跨文档环路。推理服务故障时回退原 LLM 路径；关闭开关即可停止新路径。[写入流程](D:/GraphNexus/src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java:160) · [质量门禁](D:/GraphNexus/src/main/java/com/graphnexus/application/graph/construction/validate/GraphQualityValidator.java:18)

正式放量前再用教师审阅的真实问答集检验“薄弱点根因”和“学习路径顺序”，确认关系指标改善确实转化为问答改善。模型的抽取置信度与 `PrerequisiteEdge.strength` 的教学依赖强度必须分开存储，不得相互替代。[现有边定义](D:/GraphNexus/src/main/java/com/graphnexus/infrastructure/neo4j/edge/PrerequisiteEdge.java:17)

## 可执行任务顺序

1. 固化教材文本快照、制定标注规范、建句子级和结构级金标，并冻结文档级测试集。
2. 为生产 LLM 建只读 `GraphBuilder` 适配器，接通四方法批量评测 runner，先取得 LLM/规则真实基线。
3. 训练 BERT-BiLSTM-CRF＋RE 和 CasRel，接入同一输入/输出契约，验证偏移与知识点规范化。
4. 运行完整图谱及关系条件化两套评测，锁定模型与阈值，分析无关系句、重叠句和方向错误。
5. 决定模块胜者，扩展证据与候选审核数据结构，影子运行；无回归后有限写入并保留回滚开关。

首个最小里程碑是**同一批测试教材上得到可信的“生产 LLM vs 规则”基线**。这一步不需要先训练新模型，却能验证金标、适配器与评测统计是否可靠。
