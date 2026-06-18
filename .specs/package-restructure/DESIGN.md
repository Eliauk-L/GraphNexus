# DESIGN: 规范化重构项目包结构布局

- **Change ID**: `package-restructure`
- **关联**: `@.specs/package-restructure/CHANGE.md`、`@.specs/CONTEXT.md`
- **角色**: Architect
- **日期**: 2026-06-17

---

## 0. 技术栈选定

**锁定为现有技术栈**（来源：`@.specs/CONTEXT.md`「已锁技术决策」）。本次纯结构重构，不引入新技术、不修改依赖。

---

## 0.5 既有架构对齐（Brownfield）

### 0.5.1 本次 change 触碰的模块

```
本次 change 会触碰：
- src/main/java/com/GraphNexus/ 下全部 216 个 .java 文件（根包重命名）
- src/test/java/com/GraphNexus/ 下全部 26 个 .java 文件（测试镜像）
- api/gateway/ → 移除，OpenApiConfig.java 迁入 common/config/
- api/document/ → api/file/（重命名 + 内部重组）
- api/graph/dto/ → 拆分为 fusion/graph/metrics 子包
- api/query/dto/ → 拆分为 chat/prompt/conversation 子包
- application/document/ → application/file/（重命名 + 拆为 upload/parse/core）
- application/graph/ → 拆为 core/construction/fusion/metrics
- application/query/ → 拆为 chat/prompt/conversation
- application/basic/ → application/system/（重命名）
- api/basic/ → api/system/（重命名）
- infrastructure/mineru/ → application/file/parse/parser/mineru/（迁入 L2）
- 全项目 ~67 个 package-info.java → 删除

不应触碰的：
- pom.xml（禁动清单）
- docs/项目规范.md（禁动清单）
- application.yml 中的业务配置（仅修正包名引用）
- common/ 下现有类（仅新增 OpenApiConfig.java）
- infrastructure/ 除 mineru/ 外的全部（llm/mq/mysql/neo4j/redis/storage 结构不动）
- application/analysis/ 内部结构（仅根包名 + 父包路径变更）
- application/llmgateway/ 内部结构（仅根包名变更）
```

### 0.5.2 沿用模式 vs 引入新模式

| 本次需要 | 既有有没有？ | 决定 |
|---------|------------|------|
| 四层分层架构 | `docs/项目规范.md` §1.4.2 定义 | **沿用** L1→L2→L3→common |
| L2 子包模板 | `docs/package-structure-spec.md` §3.1 | **沿用** service/ + service/impl/ + model/ |
| L3 子包模板 | `docs/package-structure-spec.md` §4.1 | **沿用** config/ + domain子包 |
| 模块扩展子包 | `docs/package-structure-spec.md` §3.2 | **沿用** parser/、strategy/、event/ 等按需扩展 |
| API dto 子包化 | 无既有模式 | **引入新模式**：dto 子包与 controller 对齐（规则：有几个 controller 子模块，dto 下就有几个对应子包） |
| L2 子模块拆分 | 无既有模式（refine-package-structure 仅做 internal 拆分） | **引入新模式**：大模块拆为 3-4 个功能子模块，每个含标准骨架 |
| 根包全小写 | `docs/项目规范.md` §1.4.1 规定但未落实 | **修正**：`com.GraphNexus` → `com.graphnexus` |
| package-info.java | 既有 67 个，维护成本 > 收益 | **移除**：删除全部，用关键类的 package-level Javadoc 替代 |

### 0.5.3 禁动清单（本 DESIGN 执行期）

- `pom.xml`
- `docs/项目规范.md`
- 任何 `.yml` 配置文件中的业务配置值
- `common/` 下除新增 OpenApiConfig 外的所有现有文件
- 所有业务逻辑代码（方法体、字段、注释不修改）

---

## 1. 技术决策

### D1 · 根包命名

- **决策**：`com.graphnexus`（全小写）
- **备选**：`com.graphnexus` 已经符合规范，无备选
- **理由**：对齐 `docs/项目规范.md` §1.4.1「包名全小写，点分隔」+ Java 社区惯例
- **代价**：全部 216 个 Java 文件的 package + import 声明需修改；Git 历史 `--follow` 可能断裂

### D2 · 子模块拆分粒度

- **决策**：每个 L2 业务域拆为 3-4 个功能子模块，每个含 `service/` + `service/impl/` + `model/` 标准骨架 + 按需扩展包
- **备选**：保持扁平结构（如 extraction/ 当前）
- **理由**：216 个文件若继续扁平堆放，单个包将超 50 个类；子模块拆分后每个子包 ≤ 15 个类，边界清晰
- **代价**：深层包路径（如 `application.file.parse.parser.mineru.client`）；跨子模块 import 增多

### D3 · API dto 子包化规则

- **决策**：dto 子包与 controller 一一对齐。controller 有几个子模块，dto 下就有几个对应子包
- **备选**：保持 dto 扁平
- **理由**：当前 graph dto 包 7 个类混放（fusion/graph/metrics 的 VO 全在一起），随功能增长会更混乱
- **代价**：单 controller 模块的 dto 仍有 1 层额外嵌套（如 `dto/chat/QueryAskRequest.java` vs `dto/QueryAskRequest.java`）

### D4 · MinerU 归属 L2

- **决策**：`infrastructure/mineru/` → `application/file/parse/parser/mineru/`
- **备选**：拆分为 L2 接口实现 + L3 HTTP client 两部分
- **理由**：MinerU 是 `DocumentParser` 接口的实现，与 `PdfBoxDocumentParser` 同属解析器族。当前 `PdfBoxDocumentParser` 已在 L2，统一放置降低理解成本。HTTP 调用细节随实现类同包，不强制分离
- **代价**：L2 出现 HTTP 调用逻辑，模糊了 L2/L3 边界。若未来 MinerU client 被其他模块复用，需再抽出到 L3

### D5 · 删除 package-info.java

- **决策**：全项目删除全部 `package-info.java`（~67 个文件）
- **备选**：保留或缩减为仅顶层包保留
- **理由**：绝大多数仅含一行 `@NonNullApi` 注解，实际未强制非空检查；维护负担（新增子包必须建一个）高于收益
- **代价**：失去包级 Javadoc 位置；若未来需要包级注解（如 `@NonNullApi`），需在关键类上加或重新评估

### D6 · 模块命名

- **决策**：各层模块名保持一致。L1 `api/file/` ↔ L2 `application/file/`；`document` 重命名为 `file` 以面向未来更多文件类型
- **备选**：`document` 保持原名
- **理由**：当前已支持 PDF + CSV，未来可能扩展图片/视频，`file` 更通用；各层命名一致便于定位
- **代价**：URL 路径 `/api/v1/document` 如需修改需额外评估（本次不动 REST 路径）

---

## 2. 目标包目录树与文件移动映射

### 2.1 移动规则速查

| 当前路径模式 | 目标路径模式 |
|------------|------------|
| `com.GraphNexus` | `com.graphnexus` |
| `api/gateway/**` | **删除**（OpenApiConfig → `common/config/`） |
| `api/document/**` | `api/file/**` |
| `api/basic/**` | `api/system/**` |
| `api/graph/dto/*.java` | 按 controller 拆入 `api/graph/dto/{graph,fusion,metrics}/` |
| `api/query/dto/*.java` | 按功能拆入 `api/query/dto/{chat,prompt,conversation}/` |
| `application/basic/**` | `application/system/**` |
| `application/document/**` | `application/file/{upload,parse,core}/**` |
| `application/graph/extraction/**` | `application/graph/construction/**` |
| `application/graph/model/**` | `application/graph/core/model/**` |
| `application/graph/service/**` | `application/graph/core/service/**` |
| `application/query/service/**` | `application/query/chat/service/**` |
| `application/query/config/**` | `application/query/chat/config/**` |
| `application/query/model/**` | `application/query/chat/model/**` |
| `application/query/prompt/**` | `application/query/prompt/service/**` |
| `infrastructure/mineru/**` | `application/file/parse/parser/mineru/**` |
| `**/package-info.java` | **删除** |

### 2.2 文件级移动映射表

#### 2.2.0 全局：根包重命名（全部文件）

> 以下每个文件的第 1 行 `package com.GraphNexus...` → `package com.graphnexus...`，
> 以及所有 `import com.GraphNexus...` → `import com.graphnexus...`。
> 此变更覆盖全部 main + test 下的 216 个 `.java` 文件，不逐条列出。

#### 2.2.1 删除：全部 package-info.java（~68 个）

```
删除 src/main/java/com/graphnexus/**/package-info.java  (~67 个)
删除 src/test/java/com/graphnexus/architecture/package-info.java
```

#### 2.2.2 api/gateway/ 移除 + OpenApiConfig 迁移

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| 1 | `api/gateway/config/OpenApiConfig.java` | `common/config/OpenApiConfig.java` |

> `api/gateway/` 目录整体删除（含空 config/ 子包）

#### 2.2.3 api/document/ → api/file/ + dto 子包化

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| 2 | `api/document/controller/DocumentController.java` | `api/file/controller/DocumentController.java` |
| 3 | `api/document/dto/DocumentVO.java` | `api/file/dto/core/DocumentVO.java` |
| 4 | `api/document/dto/DeleteResultVO.java` | `api/file/dto/core/DeleteResultVO.java` |
| 5 | `api/document/dto/UpdateDocumentRequest.java` | `api/file/dto/core/UpdateDocumentRequest.java` |
| 6 | `api/document/dto/ParseResultVO.java` | `api/file/dto/parse/ParseResultVO.java` |
| 7 | `api/document/dto/GradeRecordVO.java` | `api/file/dto/upload/GradeRecordVO.java` |
| 8 | `api/document/dto/GradeUploadResultVO.java` | `api/file/dto/upload/GradeUploadResultVO.java` |

#### 2.2.4 api/graph/dto/ 子包化

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| 9 | `api/graph/controller/GraphController.java` | `api/graph/controller/GraphController.java`（不移动） |
| 10 | `api/graph/controller/FusionController.java` | `api/graph/controller/FusionController.java`（不移动） |
| 11 | `api/graph/controller/MetricsController.java` | `api/graph/controller/MetricsController.java`（不移动） |
| 12 | `api/graph/dto/ExtractionResultVO.java` | `api/graph/dto/graph/ExtractionResultVO.java` |
| 13 | `api/graph/dto/GraphSubgraphVO.java` | `api/graph/dto/graph/GraphSubgraphVO.java` |
| 14 | `api/graph/dto/FusionExecuteVO.java` | `api/graph/dto/fusion/FusionExecuteVO.java` |
| 15 | `api/graph/dto/FusionRollbackVO.java` | `api/graph/dto/fusion/FusionRollbackVO.java` |
| 16 | `api/graph/dto/FusionStatusVO.java` | `api/graph/dto/fusion/FusionStatusVO.java` |
| 17 | `api/graph/dto/MetricResultVO.java` | `api/graph/dto/metrics/MetricResultVO.java` |
| 18 | `api/graph/dto/MetricsQueryRequest.java` | `api/graph/dto/metrics/MetricsQueryRequest.java` |

#### 2.2.5 api/query/dto/ 子包化

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| 19 | `api/query/controller/QueryController.java` | `api/query/controller/QueryController.java`（不移动） |
| 20 | `api/query/dto/QueryAskRequest.java` | `api/query/dto/chat/QueryAskRequest.java` |
| 21 | `api/query/dto/QueryAskResponse.java` | `api/query/dto/chat/QueryAskResponse.java` |
| 22 | `api/query/dto/QueryAsyncResponse.java` | `api/query/dto/chat/QueryAsyncResponse.java` |
| 23 | `api/query/dto/QueryChatRequest.java` | `api/query/dto/chat/QueryChatRequest.java` |
| 24 | `api/query/dto/QueryResultResponse.java` | `api/query/dto/chat/QueryResultResponse.java` |

> `api/query/dto/prompt/` 和 `api/query/dto/conversation/` 预留空骨架，当前无文件

#### 2.2.6 api/analysis/ → 不移动

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| 25 | `api/analysis/controller/AnalysisController.java` | 不移动（仅 package 改名） |
| 26 | `api/analysis/dto/SubgraphResponse.java` | 不移动（仅 package 改名） |

#### 2.2.7 api/llm/ → 不移动

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| 27 | `api/llm/controller/LlmController.java` | 不移动（仅 package 改名） |
| 28 | `api/llm/dto/LlmDebugRequest.java` | 不移动（仅 package 改名） |

#### 2.2.8 api/basic/ → api/system/

> basic/ 目录下当前无 .java 文件（仅有空目录 + package-info.java），直接重命名目录即可。

#### 2.2.9 application/document/ → application/file/{upload,parse,core}/

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| **core/（文档管理：查询、删除）** | | |
| 29 | `application/document/model/DocumentBO.java` | `application/file/core/model/DocumentBO.java` |
| 30 | `application/document/model/UpdateDocumentBO.java` | `application/file/core/model/UpdateDocumentBO.java` |
| 31 | `application/document/model/DeleteResultBO.java` | `application/file/core/model/DeleteResultBO.java` |
| 32 | `application/document/service/DocumentService.java` | `application/file/core/service/DocumentService.java` |
| 33 | `application/document/service/impl/DocumentServiceImpl.java` | `application/file/core/service/impl/DocumentServiceImpl.java` |
| **parse/（文档解析）** | | |
| 34 | `application/document/model/ParseResult.java` | `application/file/parse/model/ParseResult.java` |
| 35 | `application/document/parser/DocumentParser.java` | `application/file/parse/parser/DocumentParser.java` |
| 36 | `application/document/parser/PdfBoxDocumentParser.java` | `application/file/parse/parser/PdfBoxDocumentParser.java` |
| 37 | `application/document/parser/MinerUDocumentParser.java` | `application/file/parse/parser/MinerUDocumentParser.java` |
| 38 | `application/document/parser/FileParser.java` | `application/file/parse/parser/FileParser.java` |
| 39 | `application/document/parser/FileParserRegistry.java` | `application/file/parse/parser/FileParserRegistry.java` |
| 40 | `application/document/parser/FileParseRequest.java` | `application/file/parse/model/FileParseRequest.java` |
| 41 | `application/document/parser/FileParseResult.java` | `application/file/parse/model/FileParseResult.java` |
| 42 | `application/document/parser/FileParseType.java` | `application/file/parse/model/FileParseType.java` |
| 43 | `application/document/parser/CsvGradeParser.java` | `application/file/parse/parser/CsvGradeParser.java` |
| **upload/（成绩上传）** | | |
| 44 | `application/document/model/GradeRecordBO.java` | `application/file/upload/model/GradeRecordBO.java` |
| 45 | `application/document/model/GradeUploadResultBO.java` | `application/file/upload/model/GradeUploadResultBO.java` |
| 46 | `application/document/service/GradeService.java` | `application/file/upload/service/GradeService.java` |
| 47 | `application/document/service/impl/GradeServiceImpl.java` | `application/file/upload/service/impl/GradeServiceImpl.java` |
| **MinerU 迁入（从 infrastructure/mineru/）** | | |
| 48 | `infrastructure/mineru/client/MinerUClient.java` | `application/file/parse/parser/mineru/client/MinerUClient.java` |
| 49 | `infrastructure/mineru/client/MinerUApiClient.java` | `application/file/parse/parser/mineru/client/MinerUApiClient.java` |
| 50 | `infrastructure/mineru/client/MinerUV1Client.java` | `application/file/parse/parser/mineru/client/MinerUV1Client.java` |
| 51 | `infrastructure/mineru/client/MinerUV4Client.java` | `application/file/parse/parser/mineru/client/MinerUV4Client.java` |
| 52 | `infrastructure/mineru/config/MinerUProperties.java` | `application/file/parse/parser/mineru/config/MinerUProperties.java` |

#### 2.2.10 application/graph/ → core/construction/fusion/metrics

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| **core/（图管理：查询、删除）** | | |
| 53 | `application/graph/model/GraphSubgraphBO.java` | `application/graph/core/model/GraphSubgraphBO.java` |
| 54 | `application/graph/model/ExtractionResultBO.java` | `application/graph/core/model/ExtractionResultBO.java` |
| 55 | `application/graph/service/GraphService.java` | `application/graph/core/service/GraphService.java` |
| 56 | `application/graph/service/impl/GraphServiceImpl.java` | `application/graph/core/service/impl/GraphServiceImpl.java` |
| **construction/（图谱构建，原 extraction/）** | | |
| 57 | `application/graph/extraction/ExtractionService.java` | `application/graph/construction/service/ExtractionService.java` |
| 58 | `application/graph/extraction/ExtractionJsonParser.java` | `application/graph/construction/service/ExtractionJsonParser.java` |
| 59 | `application/graph/extraction/ExtractionPromptBuilder.java` | `application/graph/construction/service/ExtractionPromptBuilder.java` |
| 60 | `application/graph/extraction/ExtractionValidator.java` | `application/graph/construction/service/ExtractionValidator.java` |
| 61 | `application/graph/extraction/ExtractionRawResult.java` | `application/graph/construction/model/ExtractionRawResult.java` |
| **fusion/（图谱融合，原样保留内部结构）** | | |
| 62 | `application/graph/fusion/config/FusionProperties.java` | `application/graph/fusion/config/FusionProperties.java`（不移动） |
| 63 | `application/graph/fusion/config/FuzzyMatchProperties.java` | `application/graph/fusion/config/FuzzyMatchProperties.java`（不移动） |
| 64 | `application/graph/fusion/config/TimeDecayProperties.java` | `application/graph/fusion/config/TimeDecayProperties.java`（不移动） |
| 65 | `application/graph/fusion/model/FusionExecuteResult.java` | `application/graph/fusion/model/FusionExecuteResult.java` |
| 66 | `application/graph/fusion/model/FusionGroup.java` | `application/graph/fusion/model/FusionGroup.java` |
| 67 | `application/graph/fusion/model/FusionRollbackResult.java` | `application/graph/fusion/model/FusionRollbackResult.java` |
| 68 | `application/graph/fusion/model/FusionStatusResult.java` | `application/graph/fusion/model/FusionStatusResult.java` |
| 69 | `application/graph/fusion/model/KpCandidate.java` | `application/graph/fusion/model/KpCandidate.java` |
| 70 | `application/graph/fusion/model/TestedRecord.java` | `application/graph/fusion/model/TestedRecord.java` |
| 71 | `application/graph/fusion/model/WeightResult.java` | `application/graph/fusion/model/WeightResult.java` |
| 72 | `application/graph/fusion/service/FusionService.java` | `application/graph/fusion/service/FusionService.java` |
| 73 | `application/graph/fusion/service/impl/FusionServiceImpl.java` | `application/graph/fusion/service/impl/FusionServiceImpl.java` |
| 74 | `application/graph/fusion/service/impl/FusionGroupBuilder.java` | `application/graph/fusion/service/impl/FusionGroupBuilder.java` |
| 75 | `application/graph/fusion/service/impl/FusionRollbackService.java` | `application/graph/fusion/service/impl/FusionRollbackService.java` |
| 76 | `application/graph/fusion/service/impl/MastersRecalculationService.java` | `application/graph/fusion/service/impl/MastersRecalculationService.java` |
| 77 | `application/graph/fusion/strategy/KpMatchingStrategy.java` | `application/graph/fusion/strategy/KpMatchingStrategy.java` |
| 78 | `application/graph/fusion/strategy/FuzzyMatchStrategy.java` | `application/graph/fusion/strategy/FuzzyMatchStrategy.java` |
| 79 | `application/graph/fusion/strategy/ExactMatchStrategy.java` | `application/graph/fusion/strategy/ExactMatchStrategy.java` |
| 80 | `application/graph/fusion/strategy/WeightCalculationStrategy.java` | `application/graph/fusion/strategy/WeightCalculationStrategy.java` |
| 81 | `application/graph/fusion/strategy/TimeDecayStrategy.java` | `application/graph/fusion/strategy/TimeDecayStrategy.java` |
| 82 | `application/graph/fusion/strategy/SimpleAverageStrategy.java` | `application/graph/fusion/strategy/SimpleAverageStrategy.java` |
| **metrics/（图指标，原样保留内部结构）** | | |
| 83 | `application/graph/metrics/config/MetricsProperties.java` | `application/graph/metrics/config/MetricsProperties.java` |
| 84 | `application/graph/metrics/event/GraphChangedEvent.java` | `application/graph/metrics/event/GraphChangedEvent.java` |
| 85 | `application/graph/metrics/event/MetricsCacheInvalidator.java` | `application/graph/metrics/event/MetricsCacheInvalidator.java` |
| 86 | `application/graph/metrics/model/MetricResultBO.java` | `application/graph/metrics/model/MetricResultBO.java` |
| 87 | `application/graph/metrics/model/MetricsQuery.java` | `application/graph/metrics/model/MetricsQuery.java` |
| 88 | `application/graph/metrics/service/MetricsService.java` | `application/graph/metrics/service/MetricsService.java` |
| 89 | `application/graph/metrics/service/impl/MetricsServiceImpl.java` | `application/graph/metrics/service/impl/MetricsServiceImpl.java` |

#### 2.2.11 application/query/ → chat/prompt/conversation

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| **chat/（智能问答）** | | |
| 90 | `application/query/service/QueryService.java` | `application/query/chat/service/QueryService.java` |
| 91 | `application/query/service/impl/QueryServiceImpl.java` | `application/query/chat/service/impl/QueryServiceImpl.java` |
| 92 | `application/query/model/QueryIntent.java` | `application/query/chat/model/QueryIntent.java` |
| 93 | `application/query/model/QueryResultBO.java` | `application/query/chat/model/QueryResultBO.java` |
| 94 | `application/query/config/QueryProperties.java` | `application/query/chat/config/QueryProperties.java` |
| 95 | `application/query/config/AsyncConfig.java` | `application/query/chat/config/AsyncConfig.java` |
| **prompt/（Prompt 模板管理）** | | |
| 96 | `application/query/prompt/PromptTemplateService.java` | `application/query/prompt/service/PromptTemplateService.java` |
| **conversation/（会话管理）** | | |
| 97 | （无现有文件） | 预留 `service/` + `service/impl/` + `model/` 骨架 |

#### 2.2.12 不移动的模块（仅根包名变更）

**application/analysis/**（6 个文件，不移动）：
| # | 文件 |
|:--|-----|
| 98 | `application/analysis/model/PrunedSubgraph.java` |
| 99 | `application/analysis/model/PruningRequest.java` |
| 100 | `application/analysis/service/SubgraphAnalysisService.java`（如有） |
| 101 | `application/analysis/strategy/StudentDiagnosisStrategy.java` |
| 102 | `application/analysis/strategy/SubgraphPruningStrategy.java` |

**application/llmgateway/**（不移动）：
| # | 文件 |
|:--|-----|
| 103 | `application/llmgateway/model/` 下文件 |
| 104 | `application/llmgateway/service/LlmGateway.java` |
| 105 | `application/llmgateway/service/impl/` 下文件 |

**application/system/**（原 basic/，仅目录重命名）：
> 当前无 .java 文件，仅空目录重命名

**common/**（不移动，新增 1 个）：
| # | 文件 |
|:--|-----|
| 106 | `common/ApiResponse.java`（注：实际文件名为 `ApiResult.java`，待 DESIGN 确认是否改名） |
| 107 | `common/PageResult.java` |
| 108 | `common/exception/BusinessException.java` |
| 109 | `common/exception/ErrorCode.java` |
| 110 | `common/exception/ErrorResponse.java` |
| 111 | `common/exception/GlobalExceptionHandler.java` |
| 112 | `common/logging/TraceIdFilter.java` |
| 113 | `common/util/Md5Utils.java` |
| +1 | `common/config/OpenApiConfig.java`（新增，从 api/gateway/ 迁入） |

**infrastructure/**（除 mineru/ 外全部不移动，仅根包名变更）：
| # | 子域 | 文件数 |
|:--|------|:--:|
| 114-117 | `infrastructure/llm/` | 2 client + 1 config |
| 118-121 | `infrastructure/mq/` | config/exchange/queue |
| 122-129 | `infrastructure/mysql/` | config + document/fusion/query 的 DO/Repository |
| 130-145 | `infrastructure/neo4j/` | config + node(8) + edge(13) + gds(1) + repository(1) |
| 146-147 | `infrastructure/redis/` | config |
| 148-149 | `infrastructure/storage/` | FileStorageService + config |

**根目录**：
| # | 文件 |
|:--|-----|
| 150 | `GraphNexusApplication.java`（不移动，仅 package 改名） |

#### 2.2.13 测试文件移动映射

| # | 当前路径 | 目标路径 |
|:--|---------|---------|
| T1 | `test/.../api/graph/controller/FusionControllerIntegrationTest.java` | 不移动 |
| T2 | `test/.../api/graph/controller/GraphControllerIntegrationTest.java` | 不移动 |
| T3 | `test/.../api/graph/controller/MetricsControllerTest.java` | 不移动 |
| T4 | `test/.../api/query/controller/QueryControllerIntegrationTest.java` | 不移动 |
| T5 | `test/.../application/document/parser/CsvGradeParserTest.java` | `test/.../application/file/parse/parser/CsvGradeParserTest.java` |
| T6 | `test/.../application/document/parser/MinerUDocumentParserTest.java` | `test/.../application/file/parse/parser/MinerUDocumentParserTest.java` |
| T7 | `test/.../application/document/parser/PdfBoxDocumentParserTest.java` | `test/.../application/file/parse/parser/PdfBoxDocumentParserTest.java` |
| T8 | `test/.../application/document/service/DocumentProcessingIntegrationTest.java` | `test/.../application/file/core/service/DocumentProcessingIntegrationTest.java` |
| T9 | `test/.../application/document/service/DocumentServiceTest.java` | `test/.../application/file/core/service/DocumentServiceTest.java` |
| T10 | `test/.../application/graph/extraction/ExtractionJsonParserTest.java` | `test/.../application/graph/construction/service/ExtractionJsonParserTest.java` |
| T11 | `test/.../application/graph/extraction/ExtractionValidatorTest.java` | `test/.../application/graph/construction/service/ExtractionValidatorTest.java` |
| T12 | `test/.../application/graph/fusion/strategy/FuzzyMatchStrategyTest.java` | 不移动 |
| T13 | `test/.../application/graph/fusion/strategy/TimeDecayStrategyTest.java` | 不移动 |
| T14 | `test/.../application/graph/metrics/event/MetricsCacheInvalidatorTest.java` | 不移动 |
| T15 | `test/.../application/graph/metrics/model/MetricsQueryTest.java` | 不移动 |
| T16 | `test/.../application/graph/metrics/service/impl/MetricsServiceTest.java` | 不移动 |
| T17 | `test/.../application/graph/service/GraphServiceTest.java` | `test/.../application/graph/core/service/GraphServiceTest.java` |
| T18 | `test/.../application/query/config/QueryPropertiesTest.java` | `test/.../application/query/chat/config/QueryPropertiesTest.java` |
| T19 | `test/.../application/query/prompt/PromptTemplateServiceTest.java` | `test/.../application/query/prompt/service/PromptTemplateServiceTest.java` |
| T20 | `test/.../architecture/LayeredArchitectureTest.java` | 不移动（需更新包名模式） |
| T21 | `test/.../common/util/Md5UtilsTest.java` | 不移动 |
| T22 | `test/.../infrastructure/mineru/client/MinerUClientTest.java` | `test/.../application/file/parse/parser/mineru/client/MinerUClientTest.java` |
| T23 | `test/.../infrastructure/mysql/document/DocumentStatusTest.java` | 不移动 |
| T24 | `test/.../infrastructure/neo4j/gds/GdsAdapterTest.java` | 不移动 |
| T25 | `test/.../infrastructure/neo4j/node/GraphNodeAbstractionTest.java` | 不移动 |

---

## 3. ADR

### ADR-010: 根包全小写命名

- **Context**: 项目当前根包名为 `com.GraphNexus`（含大写 G/N），违反 `docs/项目规范.md` §1.4.1 和 Java 社区惯例
- **Decision**: 强制根包名为 `com.graphnexus`（全小写），所有子包继承此命名
- **Consequences**: 
  - 全部 Java 文件的 package/import 声明需修改
  - Spring 组件扫描、JPA Repository 扫描、ArchUnit 测试规则需同步更新包名引用
  - 配置文件（yml）中如有硬编码包名需同步修改
  - Git 历史 `--follow` 追踪可能断裂

### ADR-011: L2 模块子域拆分模式

- **Context**: 随代码量增长，`graph/`（49 文件）、`document/`（20 文件）、`query/`（11 文件）等 L2 模块内部类堆叠，职责边界模糊
- **Decision**: 大模块按功能子域拆分为 3-4 个子模块，每个子模块含标准骨架（`service/` + `service/impl/` + `model/`）+ 按需扩展包。具体：
  - `file/` → `upload/` + `parse/` + `core/`
  - `graph/` → `core/` + `construction/` + `fusion/` + `metrics/`
  - `query/` → `chat/` + `prompt/` + `conversation/`
- **Consequences**:
  - 包路径更深（如 `application.file.parse.parser.mineru.client`）
  - 跨子模块 import 增多
  - 新增业务子域时需按此模式创建骨架
  - 未来新增模块遵循同样拆分逻辑

### ADR-012: 删除 package-info.java

- **Context**: 项目当前有 ~67 个 `package-info.java`，绝大多数仅含 `@NonNullApi` 注解，实际未启用 null-safety 检查。每次新增子包都需创建，维护成本高于收益
- **Decision**: 全项目删除 `package-info.java`，不再要求每个包必须有此文件
- **Consequences**:
  - 失去包级 Javadoc 和注解位置
  - 如需包级注解（`@NonNullApi`、`@ParametersAreNonnullByDefault` 等），改为在关键类或 `common/` 配置类上声明
  - `docs/package-structure-spec.md` 需移除「每个包必须含 package-info.java」的反模式条目

---

## 4. 风险

| # | 风险 | 类型 | 严重度 | 缓解方案 |
|:--|------|------|:--:|------|
| R1 | **Import 修正遗漏**：216 个文件的 package/import 修改可能遗漏个别跨模块引用 | 实现 | 中 | 编译器 100% 检测；`mvn compile` 作为第一道门，逐 wave 验证；IDE 自动 import 优化作为辅助 |
| R2 | **ArchUnit 规则断裂**：`LayeredArchitectureTest` 中硬编码了 `com.GraphNexus` 包名模式，重构后测试失败 | 实现 | 中 | TASK 中专设一个任务更新 ArchUnit 规则；运行 `mvn test -Dtest=LayeredArchitectureTest` 验证 |
| R3 | **Spring 组件扫描遗漏**：`@EnableJpaRepositories`、`@EntityScan`、`@ComponentScan` 等注解中如有硬编码旧包名，重构后 Bean 无法加载 | 上线 | 高 | DESIGN 阶段 grep 全项目搜索 `basePackages`、`basePackageClasses`、`@ComponentScan` 等注解参数；TASK 中专设检查步骤 |
| R4 | **IDE 缓存混乱**：macOS APFS 默认大小写不敏感，`com/GraphNexus` → `com/graphnexus` 的 git mv 可能导致 IDE 看到两个相同目录 | 实现 | 中 | 重构后执行 `git clean -fd` + IntelliJ `File > Invalidate Caches`；在大小写敏感的 CI 环境（Linux）编译验证 |
| R5 | **MinerU L2 归属争议**：HTTP client 代码在 L2 可能引发架构讨论 | 长期 | 低 | 当前 `PdfBoxDocumentParser` 已在 L2，MinerU 对齐即可。若未来 MinerU client 被其他模块复用，再抽出到 L3 作为独立基础设施 |
| R6 | **测试包路径不匹配**：部分测试类被测类移动后 import 路径错误，但编译期可检测 | 实现 | 低 | 逐 wave 运行 `mvn test-compile` 验证 |

---

## 5. 不在范围内

- ❌ 修改 REST API URL 路径（如 `/api/v1/document` → `/api/v1/file`）——本次不动，留给后续 change
- ❌ 合并 `api/analysis/` 到 `api/graph/` 或 `api/query/`
- ❌ 移动 `application/analysis/` 的剪枝策略到 `application/query/chat/`
- ❌ 引入 JPMS `module-info.java`（Java 模块系统）
- ❌ 拆分 Maven 单模块为多模块项目
- ❌ 修改任何 `.yml` 配置文件的业务配置值（仅修正包名引用）

---

## 6. 目标包目录树（最终态）

```
com.graphnexus
├── GraphNexusApplication.java
├── api/
│   ├── analysis/
│   │   ├── controller/AnalysisController.java
│   │   └── dto/SubgraphResponse.java
│   ├── system/                              // ← basic 重命名
│   │   ├── controller/
│   │   └── dto/
│   ├── file/                                // ← document 重命名
│   │   ├── controller/DocumentController.java
│   │   └── dto/
│   │       ├── upload/                      //   GradeRecordVO, GradeUploadResultVO
│   │       ├── parse/                       //   ParseResultVO
│   │       └── core/                        //   DocumentVO, DeleteResultVO, UpdateDocumentRequest
│   ├── graph/
│   │   ├── controller/
│   │   │   ├── GraphController.java
│   │   │   ├── FusionController.java
│   │   │   └── MetricsController.java
│   │   └── dto/
│   │       ├── graph/                       //   ExtractionResultVO, GraphSubgraphVO
│   │       ├── fusion/                      //   FusionExecuteVO, FusionRollbackVO, FusionStatusVO
│   │       └── metrics/                     //   MetricResultVO, MetricsQueryRequest
│   ├── llm/
│   │   ├── controller/LlmController.java
│   │   └── dto/LlmDebugRequest.java
│   └── query/
│       ├── controller/QueryController.java
│       └── dto/
│           ├── chat/                        //   QueryAskRequest/Response, QueryAsyncResponse, QueryChatRequest, QueryResultResponse
│           ├── prompt/                      //   预留
│           └── conversation/                //   预留
├── application/
│   ├── analysis/
│   │   ├── model/                           //   PrunedSubgraph, PruningRequest
│   │   ├── service/
│   │   ├── service/impl/
│   │   └── strategy/                        //   SubgraphPruningStrategy, StudentDiagnosisStrategy
│   ├── system/                              // ← basic 重命名
│   │   ├── model/
│   │   ├── service/
│   │   └── service/impl/
│   ├── file/                                // ← document 重命名，拆为 3 子模块
│   │   ├── upload/                          //   上传模块
│   │   │   ├── model/                       //     GradeRecordBO, GradeUploadResultBO
│   │   │   └── service/                     //     GradeService
│   │   │       └── impl/                    //       GradeServiceImpl
│   │   ├── parse/                           //   解析模块
│   │   │   ├── model/                       //     ParseResult, FileParseRequest, FileParseResult, FileParseType
│   │   │   ├── service/
│   │   │   ├── service/impl/
│   │   │   └── parser/                      //     DocumentParser, PdfBoxDocumentParser, MinerUDocumentParser, FileParser, FileParserRegistry, CsvGradeParser
│   │   │       └── mineru/                  //       ← 从 infrastructure/mineru/ 迁入
│   │   │           ├── client/              //         MinerUClient, MinerUApiClient, MinerUV1Client, MinerUV4Client
│   │   │           └── config/              //         MinerUProperties
│   │   └── core/                            //   文档管理模块
│   │       ├── model/                       //     DocumentBO, UpdateDocumentBO, DeleteResultBO
│   │       └── service/                     //     DocumentService
│   │           └── impl/                    //       DocumentServiceImpl
│   ├── graph/
│   │   ├── core/                            //   图管理（查询、删除）
│   │   │   ├── model/                       //     GraphSubgraphBO, ExtractionResultBO
│   │   │   └── service/                     //     GraphService
│   │   │       └── impl/                    //       GraphServiceImpl
│   │   ├── construction/                    //   图谱构建（原 extraction）
│   │   │   ├── model/                       //     ExtractionRawResult
│   │   │   └── service/                     //     ExtractionService, ExtractionJsonParser, ExtractionPromptBuilder, ExtractionValidator
│   │   │       └── impl/
│   │   ├── fusion/
│   │   │   ├── config/                      //     FusionProperties, FuzzyMatchProperties, TimeDecayProperties
│   │   │   ├── model/                       //     FusionExecuteResult, FusionGroup, FusionRollbackResult, FusionStatusResult, KpCandidate, TestedRecord, WeightResult
│   │   │   ├── service/                     //     FusionService
│   │   │   │   └── impl/                    //       FusionServiceImpl, FusionGroupBuilder, FusionRollbackService, MastersRecalculationService
│   │   │   └── strategy/                    //     KpMatchingStrategy, FuzzyMatchStrategy, ExactMatchStrategy, WeightCalculationStrategy, TimeDecayStrategy, SimpleAverageStrategy
│   │   └── metrics/
│   │       ├── config/                      //     MetricsProperties
│   │       ├── event/                       //     GraphChangedEvent, MetricsCacheInvalidator
│   │       ├── model/                       //     MetricResultBO, MetricsQuery
│   │       └── service/                     //     MetricsService
│   │           └── impl/                    //       MetricsServiceImpl
│   ├── llmgateway/
│   │   ├── model/
│   │   ├── service/LlmGateway.java
│   │   └── service/impl/
│   └── query/
│       ├── chat/                            //   智能问答
│       │   ├── config/                      //     QueryProperties, AsyncConfig
│       │   ├── model/                       //     QueryIntent, QueryResultBO
│       │   └── service/                     //     QueryService
│       │       └── impl/                    //       QueryServiceImpl
│       ├── prompt/                          //   Prompt 模板管理
│       │   ├── model/
│       │   └── service/                     //     PromptTemplateService
│       │       └── impl/
│       └── conversation/                    //   会话管理（预留）
│           ├── model/
│           └── service/
│               └── impl/
├── infrastructure/
│   ├── llm/                                 //   不变
│   │   ├── client/
│   │   └── config/
│   ├── mq/                                  //   不变
│   │   ├── config/
│   │   ├── exchange/
│   │   └── queue/
│   ├── mysql/                               //   不变
│   │   ├── config/JpaAuditConfig.java
│   │   ├── document/                        //     DocumentDO, DocumentRepository, DocumentStatus, ExamRecordDO, ExamRecordRepository
│   │   ├── fusion/                          //     FusionLogDO, FusionLogRepository
│   │   └── query/                           //     QueryTaskDO, QueryTaskRepository, QueryTaskStatus
│   ├── neo4j/                               //   不变
│   │   ├── config/Neo4jIndexConfig.java
│   │   ├── node/                            //     GraphNode, DocumentNode, EntityNode, ExamNode, KnowledgeCategoryNode, KnowledgePointNode, StudentNode, NodeType, EntityType
│   │   ├── edge/                            //     GraphEdge, AlignedToEdge, AttendedEdge, BelongsToEdge, ChildOfEdge, ContainsEdge, DerivesEdge, ExtractsEdge, MastersEdge, PrerequisiteEdge, ReferencesEdge, TestedEdge, EdgeType
│   │   ├── gds/GdsAdapter.java
│   │   └── repository/GraphNodeRepository.java
│   ├── redis/config/                        //   不变
│   └── storage/                             //   不变
│       ├── FileStorageService.java
│       └── config/                          //     MinioConfig, MinioProperties
└── common/
    ├── config/OpenApiConfig.java            //   ← 从 api/gateway/ 迁入
    ├── exception/                           //   BusinessException, ErrorCode, ErrorResponse, GlobalExceptionHandler
    ├── logging/TraceIdFilter.java
    ├── util/Md5Utils.java
    ├── ApiResult.java
    └── PageResult.java
```

---

## 9. 架构沉淀建议

### 9.1 新增可复用抽象

| 抽象 | 路径 | 复用场景 |
|------|------|---------|
| L2 子模块拆分模板 | `application/<module>/{sub1,sub2,sub3}/` 模式 | 未来新增 L2 模块时按此模板创建子模块骨架 |

### 9.2 项目级技术决策

| 决策 | 说明 |
|------|------|
| 根包全小写（ADR-010） | 所有 Java 包名全小写，对齐 `docs/项目规范.md` §1.4.1 |
| 子模块拆分模式（ADR-011） | L2 大模块按功能子域拆 3-4 子模块 |
| 无 package-info.java（ADR-012） | 不再维护包级注解文件 |
| API dto 子包化 | dto 子包与 controller 一一对齐 |

### 9.3 跨模块契约

N/A（本次无 API/Schema/事件总线变更）

### 9.4 依赖变动

N/A（pom.xml 不变，禁动清单）

### 9.5 禁动清单变动

| 变动 | 路径 | 说明 |
|------|------|------|
| **新增禁动** | 无 | |
| **解禁** | `docs/package-structure-spec.md` | 需同步更新以反映新结构（移除 package-info.java 要求、更新模块清单、添加子模块拆分模板） |

---

> 下一步进入 `@flow-kit/prompts/3-task.md` 拆解原子任务。

## DEV 执行追加（2026-06-18）

以下在 DEV 阶段根据需求扩展追加，与上方 DESIGN §1-§9 共同构成完整设计。

### 追加 D7 · DocumentParser 合并入 FileParser 继承体系

- **决策**：`DocumentParser extends FileParser`，形成统一文件解析接口层次
- **理由**：消除两个独立接口的认知负担；`FileParser` 为通用文件解析，`DocumentParser` 为文档解析子接口，`FileParserRegistry` 可统一路由
- **层次**：
  ```
  FileParser（通用文件解析）
    ├── DocumentParser（文档解析，如 PDF）
    │     ├── PdfBoxDocumentParser
    │     └── MinerUDocumentParser
    └── CsvGradeParser（直接实现 FileParser）
  ```
- **代价**：`DocumentParser.parse(byte[])` 与 `FileParser.<T>parse(FileParseRequest)` 共存，需 `@SuppressWarnings("unchecked")` 默认桥接

### 追加 D8 · 系统级 Document → File 重命名

- **决策**：全系统将 `Document*` 类/接口/方法/变量名重命名为 `File*`
- **范围**：
  - L1：`DocumentController→FileController`, `DocumentVO→FileVO`, `UpdateDocumentRequest→UpdateFileRequest`
  - L2：`DocumentService→FileService`, `DocumentBO→FileBO`, `UpdateDocumentBO→UpdateFileBO`
  - L3：`DocumentDO→FileDO`, `DocumentRepository→FileRepository`, `DocumentStatus→FileStatus`, `DocumentNode→FileNode`
  - API 路径：`/api/v1/document` → `/api/v1/file/document`
  - DB 表：`document` → `file`
  - 包：`infrastructure/mysql/document/` → `infrastructure/mysql/file/`
- **排除**：`MinerUDocumentParser` / `PdfBoxDocumentParser` 保留原名（属 parser 子域，不随模块名改）
- **理由**：模块已重命名为 `file`，相关类名需保持一致，避免「file 模块里有 DocumentController」的认知错位

### 追加 D9 · 单向依赖修复

- **违规 #1** `file → graph`：`GradeServiceImpl` 不再注入 `FusionService`，改为发布 `GradeUploadedEvent`（file/upload/event/）；新增 `GradeUploadedEventListener`（graph/fusion/event/）监听并触发增量融合 + `GraphChangedEvent`
- **违规 #2** `analysis ↔ query`：`PruningRequest.intent` 从 `QueryIntent` 枚举改为 `String`；`QueryIntent` 留在 query/chat/model/，analysis 不再反向依赖 query
- **结果**：`file → graph → analysis → query` 单向依赖链成立

### 追加 D10 · MySQL entity/repository 子包拆分

- **决策**：`infrastructure/mysql/<module>/` 拆分为 `entity/` + `repository/`
- **理由**：DO 类与 Repository 接口职责分离，对齐 `docs/项目规范.md` §2.2 的「Entity(DO) / Repository」分层
- **目标结构**：
  ```
  mysql/file/entity/     → FileDO, FileStatus, ExamRecordDO
  mysql/file/repository/ → FileRepository, ExamRecordRepository
  mysql/fusion/entity/   → FusionLogDO
  mysql/fusion/repository/ → FusionLogRepository
  mysql/query/entity/    → QueryTaskDO, QueryTaskStatus
  mysql/query/repository/ → QueryTaskRepository
  ```
- **注意**：包名不能使用 `do`（Java 关键字），使用 `entity` 代替

### 追加 D11 · 消除分层架构违规（ArchUnit 清零）

- **问题**：`LayeredArchitectureTest` 检测到 86 处分层违规
  1. **L1→L3**（~50 处）：`AnalysisController`、`GraphSubgraphVO` 直接访问 `GraphNode`/`GraphEdge`（L3 neo4j）
  2. **L3→L2**（~36 处）：`Langchain4jLlmGateway` 实现 L2 接口 `LlmGateway`；`GdsAdapter` 依赖 L2 的 `MetricsProperties`/`MetricsQuery`/`MetricResultBO`
- **修复**：
  1. 创建 L2 层 `GraphNodeData`/`GraphEdgeData` 记录 + `GraphDataConverter` 工具类（`application/graph/core/model/`）
  2. `PrunedSubgraph`（analysis/model/）和 `GraphSubgraphBO`（graph/core/model/）改为持有 `GraphNodeData`/`GraphEdgeData`，不再直接暴露 L3 类型
  3. `GraphSubgraphVO.from()` 和 `AnalysisController` 改为使用 L2 记录类型
  4. `LlmGateway` 接口移至 `common/`（各层均可访问的合约接口）
  5. `MetricsQuery`/`MetricResultBO` 移至 `common/model/`（跨层共享数据对象）
  6. `MetricsProperties` 移至 `infrastructure/neo4j/gds/config/`（GDS 专属配置，属于 L3）
- **结果**：ArchUnit 86 违例 → 0，`LayeredArchitectureTest` BUILD SUCCESS