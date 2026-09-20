# GraphNexus NLP 模型接入实施方案

状态：实施设计；本文未训练模型、启动服务或修改现有生产构图流程。

## 1. 目标与实施决策

把 **BERT-BiLSTM-CRF 知识点识别**与 **CasRel 显式先修关系抽取**接入 GraphNexus，先生成可评测的 `CandidateGraph`，确认质量收益后再接入教材生产构图。训练和推理由独立 Python 模块负责；Java 通过内部 HTTP 批量请求模型，不在每个句子上启动 Python 进程。[Spring RestClient 文档](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)与[FastAPI 请求体文档](https://fastapi.tiangolo.com/tutorial/body/)分别给出两侧所需的基础接口能力。

本方案的第一交付是**离线可复现评测**，第二交付是**影子运行候选关系**。正式写入 Neo4j 需经过独立评测、证据核对、知识点 ID 映射和现有质量门禁。

当前项目的实际状态：

- [`NlpGraphBuilder`](../src/main/java/com/graphnexus/application/evaluation/graph/builder/nlp/NlpGraphBuilder.java) 只调用 `RuleBasedNerExtractor` 与 `RuleBasedRelationExtractor`，应保留为规则基线。
- [`GraphBuildMethod`](../src/main/java/com/graphnexus/application/evaluation/graph/model/GraphBuildMethod.java) 只有 `LLM`、`NLP_NER_RE`；[`GraphBuilderRegistry`](../src/main/java/com/graphnexus/application/evaluation/graph/builder/GraphBuilderRegistry.java) 已提供按方法注册构建器的扩展点。
- [`GraphEvaluationServiceImpl`](../src/main/java/com/graphnexus/application/evaluation/graph/service/impl/GraphEvaluationServiceImpl.java) 与公开控制器目前只处理手工候选图；自动调用各构建器并聚合结果的 runner 尚需新增。`evaluateManual` 会写入 Neo4j，离线比较不应复用其写入副作用。
- 项目已有 `spring-boot-starter-web` 和使用 `RestClient` 的 [`MinerUV4Client`](../src/main/java/com/graphnexus/application/file/textbook/parser/pdf/mineru/client/MinerUV4Client.java)，可以沿用 HTTP 客户端模式，无须为模型另引入一套 Java 推理框架。

## 2. 目标调用链

```text
同一份教材 textSnapshot / textHash
             │
             ▼
有原文位置的分句器 → 句子批次 → Python /v1/predict
                                  ├─ BERT-BiLSTM-CRF：知识点 span
                                  └─ CasRel：显式先修三元组
             │                            │
             └──────── Java 校验与映射 ◀───┘
                              │
                              ▼
                       raw CandidateGraph
                              │
                   StrictGraphEvaluator
                              │
                分项指标 + FP/FN + 证据文件
```

CasRel 在原文上直接预测主体、关系与客体，不要求先由 CRF 找出主体。两路结果在图谱映射阶段合并：知识点模型负责完整节点候选，CasRel 负责有句级证据的边；CasRel 找到但知识点模型未找到的端点，作为单独来源的候选节点补入并在报告中统计。隐含先修边不作为 CasRel 的训练正例。

## 3. 前置数据：模型服务需要什么

沿用[K12-KGraph 单册模型可行性方案](./K12-KGraph单册BERT-BiLSTM-CRF与CasRel最小可行性方案.md)的数据范围与章节划分：教材原文和 K12 参考图应冻结版本、哈希；另建句子级标注，包含知识点跨度、显式关系的主体/客体跨度、方向和原文证据。图谱节点与边本身不能直接充当 BERT-BiLSTM-CRF 或 CasRel 所需的原文跨度标签。测试章节在模型训练、名称规则调整和阈值选择前封存。

模型权重、训练配置、标注版本、预处理版本与随机种子应单独记录。服务启动时加载权重一次，推理时关闭训练行为；没有权重时健康检查应返回不可用，不能以空预测伪装成功。

## 4. Java ↔ Python HTTP 契约（v1）

### 4.1 请求

`POST /v1/predict`，`Content-Type: application/json`。`mode` 用于同一个模型服务支持“只做知识点”与“知识点加 CasRel”两种对照组。初期每批不超过 16 句；实际最大长度按模型 tokenizer 限制控制，超长句须切分或明确报错，不能静默截断后仍算完整输入。

```json
{
  "schemaVersion": 1,
  "runId": "run-001",
  "textHash": "<教材文本 SHA-256>",
  "mode": "NER_CASREL",
  "segments": [
    {
      "segmentId": "ch02-s145",
      "chapterId": "ch02",
      "text": "先掌握有理数运算，再学习一元一次方程。"
    }
  ]
}
```

### 4.2 响应

`start`、`end` 统一定义为**该句原文中的 Unicode 码点偏移**，左闭右开；返回的 `text` 必须等于对应原文切片。Python 字符串索引通常按码点处理，Java `String` 索引按 UTF-16 码元处理，因此 Java 适配器必须先通过 `offsetByCodePoints` 转换，再用 `substring` 核验，不可直接把模型偏移传给 Java `substring`。

```json
{
  "schemaVersion": 1,
  "nerModelVersion": "bert-bilstm-crf-v1",
  "relationModelVersion": "casrel-v1",
  "offsetUnit": "UNICODE_CODE_POINT",
  "segments": [
    {
      "segmentId": "ch02-s145",
      "entities": [
        {"text": "有理数运算", "start": 3, "end": 8, "score": 0.93},
        {"text": "一元一次方程", "start": 12, "end": 18, "score": 0.91}
      ],
      "triples": [
        {
          "subject": {"text": "有理数运算", "start": 3, "end": 8},
          "relation": "PREREQUISITE_OF",
          "object": {"text": "一元一次方程", "start": 12, "end": 18},
          "score": 0.88
        }
      ]
    }
  ]
}
```

`GET /health` 应报告服务是否已加载两套权重及其版本。响应不得缺句、重复 `segmentId`，不得返回未知关系类型或 `[0,1]` 外的置信度。Java 侧把这些情况当作契约错误；离线评测整次失败，影子运行则记录错误并继续原生产流程。`runId`、`textHash`、模型版本、输入句 ID 和响应摘要共同构成审计记录。

## 5. Java 侧具体改动

| 顺序 | 建议类或文件 | 职责与验收点 |
|---|---|---|
| 1 | `config/NlpModelProperties`、`application.yml` | 配置 `baseUrl`、启用开关、每批句数、连接和读取超时；默认关闭生产调用 |
| 2 | `builder/model/LocatedTextSegmenter` | 基于 `GraphBuildContext.textSnapshot()` 产生稳定 `segmentId`、章节与原文位置；现有 `SentenceSplitter` 只有序号/段落/文本，不能直接承担证据偏移任务 |
| 3 | `builder/model/dto/*`、`PythonModelClient` | Java `record` 对齐 v1 JSON；使用 `RestClient` 批量请求；响应逐句完整性、跨度、标签和分数校验 |
| 4 | `builder/model/ModelPredictionMapper` | 把跨度映射为 `CandidateTopic`，把 `主体 → 客体` 映射为 `CandidateDependency`；给每个候选稳定临时 ID，并另存证据与模型分数 |
| 5 | `GraphBuildMethod` 与两个新 `GraphBuilder` | 建议增加 `BERT_BILSTM_CRF_RULE_RE` 与 `BERT_BILSTM_CRF_CASREL`；前者模型识别知识点后调用现有规则关系器，后者使用 CasRel 边；保留原 `NLP_NER_RE` 不变 |
| 6 | `ModelGraphEvaluationRunner` | 对同一文本快照依次运行规则、模型知识点、完整模型，调用 `GoldDatasetLoader` 和 `StrictGraphEvaluator.evaluateReport()`，落盘原始候选与报告；初期为离线 CLI/内部任务，不增加公开运行接口 |

`GraphBuildResult.rawGraph()` 应返回**未预先归一化**的 `CandidateGraph`，由现有评测器统一处理，才能保留原始重复节点、重复边和坏端点统计。神经模型的 `promptChars`、`responseChars`、token 估计设为 0，`callCount` 记录实际 HTTP 批次数，`durationMs` 记录整次构图耗时；模型版本另写运行元数据。`CandidateDependency.strength` 是教学依赖强度字段，**不得填模型置信度**；模型分数只写旁路证据记录。

Java 客户端伪代码：

```java
PredictResponse response = restClient.post()
        .uri("/v1/predict")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(PredictResponse.class);
validateCompletenessAndOffsets(request, response);
return response;
```

连接超时、读取超时与批大小从配置读取。第一次离线实现可不自动重试；发生网络错误、模型 5xx、JSON 解析失败或缺失句子时，保存失败原因并停止该次评测。需要重试时仅重试整个幂等批次，并在报告中记录次数。

## 6. Python 侧具体改动

在独立 `nlp-model-service/` 模块中维护训练和推理代码，不把 Python 依赖放进 Maven 应用：

```text
nlp-model-service/
  app.py                 # FastAPI /health、/v1/predict
  schemas.py             # Pydantic 请求与响应类型
  inference.py           # 分批推理、位置映射、输出校验
  models/ner.py          # BERT-BiLSTM-CRF 加载与预测
  models/casrel.py       # CasRel 加载与预测
  train_ner.py           # 训练脚本
  train_casrel.py        # 训练脚本
  tests/test_contract.py # 契约与跨度测试
  Dockerfile             # 影子运行阶段使用
```

模型在应用启动阶段加载并保留于内存；`/v1/predict` 按 `mode` 执行 NER 或 NER+CasRel，逐句返回结果。输出跨度必须定位于未经改写的输入句；模型内部清洗、tokenizer 偏移和原句偏移之间需要显式映射。CasRel 仅输出本轮支持的 `PREREQUISITE_OF`，不得把共现或教材顺序当成先修。部署时服务只在内部网络暴露；若 Java 与 Python 运行在不同容器，`baseUrl` 使用容器服务名，不能使用 Java 容器自己的 `localhost`。

## 7. 评测接线与验收

沿用[单册最小评测方案](./K12-KGraph单册最小评测与对照实验实施方案.md)把 K12 样例图转换为 `topics.json`、`dependencies.json`、`clusters.json`、`manifest.json` 并注册版本。A/B/C 使用同一教材原文快照、测试章节、名称归一规则和评分器：

| 组别 | 知识点 | 前置边 | 验证点 |
|---|---|---|---|
| A | 当前规则 | 当前规则 | 既有基线 |
| B | BERT-BiLSTM-CRF | 当前规则 | 模型识别节点带来的变化 |
| C | BERT-BiLSTM-CRF | CasRel | 模型关系抽取带来的变化 |

至少输出：原文知识点严格跨度 P/R/F1；有证据显式三元组 P/R/F1；参考图知识点严格 P/R/F1；有向先修边 P/R/F1；无关系句误报、反向边、无证据边、坏跨度、悬空端点与运行耗时。显式三元组以人工句级标注为分母；整册参考图还包含可能没有单句证据的先修边，须单列，不能把二者混成一个 CasRel 分数。单册样例图属于参考答案，对图谱指标的表述应是“相对 K12-KGraph 的一致性”。

最少测试：

1. 用含中文、数学符号和补充平面字符的句子做 Java/Python 偏移契约测试。
2. 检查主体、客体方向以及 CasRel 端点未出现在 NER 结果时的补点逻辑。
3. 模拟超时、5xx、空响应、缺句、重复句和非法置信度；评测不得静默出分。
4. 用固定 JSON 响应跑完整 `GraphBuilder → CandidateGraph → StrictGraphEvaluator`，确保分数可重复。
5. 最后才接真实权重，对锁定测试章节运行一次正式比较，并保留 FP/FN 证据。

技术验收是端到端流程、数据版本和指标都可复现；质量验收由 A/B/C 在锁定测试范围上的实际结果决定。若节点或边质量没有优于规则组，应如实记录，不直接进入生产写图。

## 8. 生产接入顺序

离线评测确认收益后，在 [`ConstructionServiceImpl.extract()`](../src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java) 的无 Neo4j 事务阶段，或独立的解析后任务中运行影子模型。影子模式只保存候选、证据与分歧，当前 [`ExtractionService.extract()`](../src/main/java/com/graphnexus/application/graph/construction/extract/ExtractionService.java) 的 LLM 结果仍决定正式图谱；模型服务失败时记录原因，不改变现有构图状态。

进入审核与有限写入阶段时，只合并知识点及前置候选，不丢弃生产流程已有的实体、分类和其他边。先将模型表面词映射到规范知识点，再把临时边端点改为最终节点 ID；由于 [`ConstructionServiceImpl`](../src/main/java/com/graphnexus/application/graph/construction/service/impl/ConstructionServiceImpl.java) 可能复用既有知识点 ID，写入前须再次核对端点、重边和跨文档环路，并调用 [`GraphQualityValidator`](../src/main/java/com/graphnexus/application/graph/construction/validate/GraphQualityValidator.java)。审核通过的正式前置边应关联原文证据和模型版本；抽取置信度与 `PrerequisiteEdge.strength` 分开存储。

开关按 `OFF → SHADOW → REVIEW → LIMITED_WRITE` 推进。首个落地版本只实现 `OFF` 与离线评测；影子模式在契约、耗时和失败降级通过后接入，正式写图必须另经质量与业务验收。

## 9. 建议任务拆分与完成标准

| 里程碑 | 任务 | 完成标准 |
|---|---|---|
| M0：输入与标注 | 冻结教材文本和参考图；确定章节划分及句级标注 | 哈希、划分清单和标注规范可复查；测试章封存 |
| M1：服务契约 | Python 假模型返回固定 JSON；Java `RestClient`、DTO、校验与映射接通 | 契约、跨度、缺句和方向测试通过；无需真实模型即可生成候选图 |
| M2：模型推理 | 加载 NER 与 CasRel 权重，提供批量 `/v1/predict` 和 `/health` | 服务启动仅加载一次权重；模型版本、耗时与错误可观测 |
| M3：离线评测 | 新增两组 `GraphBuilder` 与批量 runner；运行 A/B/C | 同一测试输入生成原始图、证据、分项指标及 FP/FN 清单 |
| M4：影子运行 | 内部部署模型服务，按开关记录候选与分歧 | 服务故障不影响当前生产构图；无未经审核的模型边写入正式图谱 |

M1 是最小工程闭环，M3 是最小质量闭环。现有[四方案对照评测路线](./GraphNexus-四方案对照评测与接入实施路线.md)覆盖生产 LLM 与更广的模型比较；本文专注 BERT-BiLSTM-CRF、CasRel 两模型如何接入当前 Java 项目。
