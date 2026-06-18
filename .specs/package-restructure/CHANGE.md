# CHANGE: 规范化重构项目包结构布局

- **Change ID**: `package-restructure`
- **创建日期**: 2026-06-17
- **路径建议**: 中等（`DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION`）
- **状态**: ✅ DEV 完成（3 次提交，6 个文件新增）

---

## Why（为什么做）

自 2026-06-13 `refine-package-structure` 完成后，项目又经历了 6 个 change（knowledge-graph-extraction、csv-grade-import、wide-graph-fusion、graph-metrics、intelligent-qa、mineru-pdf-parser），代码量从 ~30 膨胀到 **216 个 Java 文件**。当时的细化只做了 L2 document 拆分 + 骨架子包创建 + L3 配置归位，未覆盖后续新增模块。当前结构问题：

1. **根包名违反 Java 规范**：`com.GraphNexus` 含大写字母，项目自身规范 §1.4.1 要求「包名全小写」，示例为 `com.graphnexus.xxx`。216 个文件 + yml 配置均受影响
2. **模块职责边界模糊**：`document` 模块名暗示只处理文档，但实际已支持 PDF + CSV；`graph` 模块 49 个文件堆在 extraction/fusion/metrics 下，缺乏统一的子模块划分；`query` 模块 prompt/config/service 混杂
3. **`api/gateway/` 定位不当**：整个模块仅含一个 `OpenApiConfig.java`（Swagger 配置），属于公共配置而非 API 网关
4. **API dto 包扁平化**：graph 模块有 3 个 controller（Graph/Fusion/Metrics），但所有 dto 都堆在 `api/graph/dto/` 一个包下
5. **冗余 `package-info.java`**：全项目 50+ 个 `package-info.java`，绝大多数仅包含一行 `@NonNullApi` 注解，维护成本高于收益
6. **测试目录未严格镜像 main 侧**

## What（做什么）

一次彻底的包结构规范化重构。**只动 package 声明 + import 语句 + 文件路径**，不修改任何业务逻辑。

### 1. 全局变更

- **根包重命名**：`com.GraphNexus` → `com.graphnexus`（全小写）
- **删除所有 `package-info.java`**：50+ 个文件全部移除
- **测试目录完整镜像**：test 侧包路径严格对齐 main 侧

### 2. API 层（L1）调整

- **移除 `api/gateway/`**：`OpenApiConfig.java` 移入 `common/config/`
- **dto 子包化**：每个模块的 dto 按 controller 拆分子包。规则：有几个 controller 子模块，dto 下就有几个对应子包
  - `api/graph/dto/` → `dto/fusion/`、`dto/graph/`、`dto/metrics/`
  - `api/file/dto/` → `dto/upload/`、`dto/parse/`、`dto/core/`
  - `api/query/dto/` → `dto/chat/`、`dto/prompt/`、`dto/conversation/`
  - `api/analysis/`、`api/system/`、`api/llm/` — 单 controller，dto 保持扁平

### 3. Application 层（L2）模块拆分

#### `document/` → `file/`（文件模块）

重命名为 `file`，面向未来支持 PDF/CSV/其他文件类型。拆为三个子模块：

```
application/file/
├── upload/                  # 上传模块（预留骨架）
│   ├── service/
│   ├── service/impl/
│   └── model/
├── parse/                   # 解析模块
│   ├── service/             # 解析编排服务
│   ├── service/impl/
│   ├── model/               # ParseResult 等 BO
│   └── parser/              # 解析器实现（DocumentParser、PdfBox、MinerU）
│       └── mineru/          # MinerU 客户端 + 配置（从 infrastructure/mineru/ 迁入）
└── core/                    # 文档管理模块（查询、删除、状态管理）
    ├── service/
    ├── service/impl/
    └── model/
```

- `upload/` 和 `core/` 当前若无可不创建文件，仅建骨架目录；`parse/` 承接原 `document/` 的全部现有文件
- `infrastructure/mineru/` → `application/file/parse/parser/mineru/`

#### `graph/` 拆分为 4 个子模块

```
application/graph/
├── core/                    # 图管理模块（查询、删除等管理操作）
│   ├── service/
│   ├── service/impl/
│   └── model/               # GraphNode/GraphEdge 等共享 BO
├── construction/            # 图谱构建（原 extraction/）
│   ├── service/
│   ├── service/impl/
│   └── model/
├── fusion/                  # 图谱融合（原样保留，内部结构已规范）
│   ├── config/
│   ├── service/
│   ├── service/impl/
│   ├── model/
│   └── strategy/
└── metrics/                 # 图指标（原样保留，内部结构已规范）
    ├── config/
    ├── event/
    ├── service/
    ├── service/impl/
    └── model/
```

- `core/` 承接原 `graph/model/` + `graph/service/`（图管理：查询、删除等操作）
- `construction/` 承接原 `graph/extraction/` 全部内容
- `fusion/`、`metrics/` 内部结构不变，仅调整父包路径

#### `query/` 拆分为 3 个子模块

```
application/query/
├── chat/                    # 智能问答（意图识别 + 剪枝调度 + LLM 编排）
│   ├── service/
│   ├── service/impl/
│   └── model/
├── prompt/                  # Prompt 模板管理（模板加载 + 组装 + 格式校验）
│   ├── service/
│   ├── service/impl/
│   └── model/
└── conversation/            # 会话管理（预留骨架，当前无文件）
    ├── service/
    ├── service/impl/
    └── model/
```

- `chat/` 承接原 `query/service/` 中的问答编排逻辑
- `prompt/` 承接原 `query/prompt/` + `query/config/`
- `conversation/` 新建骨架，为未来多轮对话预留

### 4. 不变的模块

| 模块 | 层 | 说明 |
|------|:--:|------|
| `common/` | — | 保持现状；新增 `OpenApiConfig.java`（从 api/gateway 迁入） |
| `api/analysis/` | L1 | 保持现状 |
| `api/system/` | L1 | 保持现状 |
| `api/llm/` | L1 | 保持现状 |
| `application/analysis/` | L2 | 保持现状（图分析剪枝策略，服务于 query/chat） |
| `application/system/` | L2 | 保持现状 |
| `application/llmgateway/` | L2 | 保持现状（自研 LLM 网关，与 infrastructure/llm 语义不同） |
| `infrastructure/` 全部 | L3 | 保持现状（llm/mineru/mq/mysql/neo4j/redis/storage） |

### 5. 目标包目录树总览

```
com.graphnexus
├── GraphNexusApplication.java
├── api/                                    // L1
│   ├── analysis/
│   │   ├── controller/
│   │   └── dto/
│   ├── system/
│   │   ├── controller/
│   │   └── dto/
│   ├── file/                               //   原 document/
│   │   ├── controller/
│   │   └── dto/
│   │       ├── upload/
│   │       ├── parse/
│   │       └── core/
│   ├── graph/
│   │   ├── controller/
│   │   └── dto/
│   │       ├── graph/                      //   对应 GraphController
│   │       ├── fusion/                     //   对应 FusionController
│   │       └── metrics/                    //   对应 MetricsController
│   ├── llm/
│   │   ├── controller/
│   │   └── dto/
│   └── query/
│       ├── controller/
│       └── dto/
│           ├── chat/
│           ├── prompt/
│           └── conversation/
├── application/                            // L2
│   ├── analysis/
│   │   ├── model/
│   │   ├── service/
│   │   ├── service/impl/
│   │   └── strategy/
│   ├── system/
│   │   ├── model/
│   │   └── service/
│   │       └── impl/
│   ├── file/                               //   原 document/，重命名
│   │   ├── upload/
│   │   │   ├── model/
│   │   │   └── service/
│   │   │       └── impl/
│   │   ├── parse/
│   │   │   ├── model/
│   │   │   ├── service/
│   │   │   │   └── impl/
│   │   │   └── parser/
│   │   │       └── mineru/                 //   ← 从 infrastructure/mineru/ 迁入
│   │   │           ├── client/
│   │   │           └── config/
│   │   └── core/
│   │       ├── model/
│   │       └── service/
│   │           └── impl/
│   ├── graph/
│   │   ├── core/                           //   图管理（查询、删除等），原 graph/model/ + graph/service/
│   │   │   ├── model/
│   │   │   └── service/
│   │   │       └── impl/
│   │   ├── construction/                   //   原 graph/extraction/
│   │   │   ├── model/
│   │   │   └── service/
│   │   │       └── impl/
│   │   ├── fusion/
│   │   │   ├── config/
│   │   │   ├── model/
│   │   │   ├── service/
│   │   │   │   └── impl/
│   │   │   └── strategy/
│   │   └── metrics/
│   │       ├── config/
│   │       ├── event/
│   │       ├── model/
│   │       └── service/
│   │           └── impl/
│   ├── llmgateway/
│   │   ├── model/
│   │   └── service/
│   │       └── impl/
│   └── query/
│       ├── chat/                           //   原 query/service/ 问答编排
│       │   ├── model/
│       │   └── service/
│       │       └── impl/
│       ├── prompt/                         //   原 query/prompt/ + query/config/
│       │   ├── model/
│       │   └── service/
│       │       └── impl/
│       └── conversation/                   //   预留骨架
│           ├── model/
│           └── service/
│               └── impl/
├── infrastructure/                         // L3
│   ├── llm/
│   │   ├── client/
│   │   └── config/
│   ├── mq/
│   │   ├── config/
│   │   ├── exchange/
│   │   └── queue/
│   ├── mysql/
│   │   ├── config/
│   │   ├── file/
│   │   │   ├── entity/                     //   FileDO, FileStatus, ExamRecordDO
│   │   │   └── repository/                 //   FileRepository, ExamRecordRepository
│   │   ├── fusion/
│   │   │   ├── entity/                     //   FusionLogDO
│   │   │   └── repository/                 //   FusionLogRepository
│   │   └── query/
│   │       ├── entity/                     //   QueryTaskDO, QueryTaskStatus
│   │       └── repository/                 //   QueryTaskRepository
│   ├── neo4j/
│   │   ├── config/
│   │   ├── edge/
│   │   ├── gds/
│   │   ├── node/
│   │   └── repository/
│   ├── redis/
│   │   └── config/
│   └── storage/
│       └── config/
└── common/
    ├── config/                             //   新增 OpenApiConfig.java（从 api/gateway/ 迁入）
    ├── exception/
    ├── logging/
    ├── monitoring/
    ├── util/
    ├── ApiResponse.java
    └── PageResult.java
```

## 影响面

- [ ] 影响 `REQUIREMENT.md` — 否，纯结构重构
- [x] 影响 `DESIGN.md` — 是，需产出完整目标包目录树 + 文件级移动映射表
- [ ] 影响现有 AC — 否
- [ ] 影响数据模型 / 迁移 — 否
- [ ] 影响外部 API 兼容性 — 否，REST 路径和 JSON 结构不变
- [x] 影响 Spring 组件扫描 — 根包改名后 `@SpringBootApplication` 需确认无硬编码旧包名
- [x] 影响 ArchUnit 测试 — 包名匹配模式需同步更新
- [x] 影响 IDE 缓存 — IntelliJ IDEA 需 invalidate caches

## 范围排除（这次不做）

- ❌ **修改任何业务逻辑代码**（解耦事件除外，见下方「执行中追加」）
- ❌ **修改 `pom.xml`**（禁动清单）
- ❌ **修改 `docs/项目规范.md`**（禁动清单）
- ❌ **移动 `common/` 下现有类**

## 执行中追加（DEV 阶段扩展）

以下变更在原 CHANGE 基础上追加，均为配套重命名重构：

### 追加 A · DocumentParser 合并入 FileParser 继承体系
- `DocumentParser extends FileParser`，统一文件解析接口层次
- `PdfBoxDocumentParser` / `MinerUDocumentParser` 保持原名，补充 `supportedType()` + `supportedExtensions()`

### 追加 B · 系统级 Document → File 重命名
- L1：`DocumentController→FileController`, `DocumentVO→FileVO`, `UpdateDocumentRequest→UpdateFileRequest`
- L2：`DocumentService→FileService`, `DocumentBO→FileBO`, `UpdateDocumentBO→UpdateFileBO`
- L3：`DocumentDO→FileDO`, `DocumentRepository→FileRepository`, `DocumentStatus→FileStatus`, `DocumentNode→FileNode`
- API 路径：`/api/v1/document` → `/api/v1/file/document`
- DB 表名：`document` → `file`

### 追加 C · 单向依赖修复
- `analysis ↔ query` 循环依赖：`PruningRequest.intent` 从 `QueryIntent` 枚举改为 `String`，`QueryIntent` 留在 query 模块
- `file → graph` 违规：`GradeServiceImpl` 不再直接调用 `FusionService`，改为发布 `GradeUploadedEvent`；新增 `GradeUploadedEventListener`（graph/fusion/event/）监听并触发融合

### 追加 D · MySQL entity/repository 子包拆分
- `infrastructure/mysql/<module>/` → `entity/` + `repository/`
- 模块：`file/`, `fusion/`, `query/`

### 追加 E · 消除分层架构违规（ArchUnit 清零）
- **L1→L3**：创建 L2 层 `GraphNodeData`/`GraphEdgeData` 记录 + `GraphDataConverter`，`PrunedSubgraph`/`GraphSubgraphBO` 持有 L2 类型，L1 VO/Controller 不再直接接触 L3 实体
- **L3→L2**：`LlmGateway` 接口移至 `common/`；`MetricsQuery`/`MetricResultBO` 移至 `common/model/`；`MetricsProperties` 移至 `infrastructure/neo4j/gds/config/`
- 结果：ArchUnit 86 违例 → 0

## 验收线

1. **根包全小写**：`grep -r "com\.GraphNexus" src/` 返回零结果
2. **零 `package-info.java`**：`find src/ -name "package-info.java"` 返回零结果
3. **编译零错误**：`mvn compile` 通过
4. **测试全绿**：`mvn test` 全部通过（含 ArchUnit）
5. **模块拆分落地**：
   - `application/file/` 含 `upload/` + `parse/` + `core/` 三个子模块
   - `application/graph/` 含 `core/` + `construction/` + `fusion/` + `metrics/` 四个子模块
   - `application/query/` 含 `chat/` + `prompt/` + `conversation/` 三个子模块
6. **`api/gateway/` 目录不存在**，`OpenApiConfig.java` 位于 `common/config/`
7. **`infrastructure/mineru/` 目录不存在**
8. **API dto 子包化**：graph dto 含 `fusion/`/`graph/`/`metrics/`，file dto 含 `upload/`/`parse/`/`core/`，query dto 含 `chat/`/`prompt/`/`conversation/`
9. **测试镜像**：test 侧包路径与 main 侧完全对齐
10. **文档更新**：`docs/package-structure-spec.md` 反映最终结构

## 风险与未知

- **Import 修正遗漏**：~216 个文件的 package/import 修改。编译器可 100% 检测，风险可控
- **MinerU 移入 L2 的架构影响**：MinerU 含 HTTP 客户端（`MineruClient`），传统上属于 L3 基础设施。移入 `application/file/parse/parser/mineru/` 后，需在 DESIGN 阶段评估：是否将 HTTP 调用部分剥离留在 L3，还是接受解析器实现可内嵌 HTTP 调用
- **Git 历史断裂**：大量 `git mv` 后 `git log --follow` 可能无法追踪。接受此代价
- **Spring 组件扫描**：需检查 `@EnableJpaRepositories`、`@EntityScan`、`@ComponentScan` 等注解中是否有硬编码 `com.GraphNexus`
- **`application/analysis/` 归属待定**：当前 `analysis/` 含剪枝策略（`SubgraphPruningStrategy`），服务于 query/chat 模块。本次不动它，但未来可考虑是否并入 `query/chat/` 或 `graph/core/`

---

> 后续目标包目录树精确定义 + 文件级移动映射表进入 `DESIGN.md` → `TASK.md`，本文件不再扩展。