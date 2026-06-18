# DESIGN: 细化项目包结构，建立统一分层子包模板

- **Change ID**: `refine-package-structure`
- **关联**: `@.specs/refine-package-structure/CHANGE.md`、`@.specs/CONTEXT.md`
- **作者**: AI（Architect 角色）+ 人工 review

---

## 0. 技术栈选定

> 由 CONTEXT.md「已锁技术决策」锁定，不重选。

- **选定**：Java 17 + Spring Boot 3.3 + Maven 单模块（已锁）
- **理由**：纯包结构重构，不涉及技术栈变更
- **明确排除**：不引入任何新依赖、新框架

---

## 0.5 既有架构对齐（brownfield）

### 0.5.1 本次 change 触碰的既有模块

```
触碰模块（主代码 · grep import 验证）：
- src/main/java/com/graphnexus/api/document/controller/DocumentController.java   （import 路径需更新）
- src/main/java/com/graphnexus/api/document/dto/DocumentVO.java                  （import 路径需更新）
- src/main/java/com/graphnexus/api/document/dto/ParseResultVO.java               （import 路径需更新）
- src/main/java/com/graphnexus/api/document/dto/UpdateDocumentRequest.java        （不动 · 无内部 import）
- src/main/java/com/graphnexus/application/document/service/DocumentService.java     （移动 · 留在 service/）
- src/main/java/com/graphnexus/application/document/service/DocumentServiceImpl.java （移动 · service/ → service/impl/）
- src/main/java/com/graphnexus/application/document/service/DocumentBO.java          （移动 · service/ → model/）
- src/main/java/com/graphnexus/application/document/service/UpdateDocumentBO.java    （移动 · service/ → model/）
- src/main/java/com/graphnexus/application/document/service/ParseResult.java         （移动 · service/ → model/）
- src/main/java/com/graphnexus/application/document/service/DocumentParser.java      （移动 · service/ → parser/）
- src/main/java/com/graphnexus/application/document/service/PdfBoxDocumentParser.java（移动 · service/ → parser/）
- src/main/java/com/graphnexus/infrastructure/mysql/JpaAuditConfig.java              （移动 · mysql/ → mysql/config/）
- src/main/java/com/graphnexus/infrastructure/storage/MinioConfig.java               （移动 · storage/ → storage/config/）
- src/main/java/com/graphnexus/infrastructure/storage/MinioProperties.java           （移动 · storage/ → storage/config/）
- src/main/java/com/graphnexus/infrastructure/storage/FileStorageService.java        （不动 · 留在 storage/）
- src/main/java/com/graphnexus/infrastructure/mysql/document/                        （不动 · DO/Repository/enum 维持现状）
- src/main/java/com/graphnexus/common/                                               （不动 · 禁动）

新增空包骨架：
- L1: api/gateway/config/, api/{graph,analysis,query,basic}/dto/
- L2: application/{graph,analysis,query,basic,llmgateway}/service/impl/, application/{graph,analysis,query,basic,llmgateway}/model/
- L3: infrastructure/mysql/config/, infrastructure/storage/config/,
      infrastructure/neo4j/{config,node,edge,repository}/,
      infrastructure/redis/config/,
      infrastructure/mq/{config,queue,exchange}/,
      infrastructure/llm/{config,client}/

测试触碰（跟随 main 移动）：
- src/test/java/com/graphnexus/application/document/service/DocumentServiceTest.java           （移动 · service/）
- src/test/java/com/graphnexus/application/document/service/DocumentProcessingIntegrationTest.java（移动 · service/）
- src/test/java/com/graphnexus/application/document/service/PdfBoxDocumentParserTest.java      （移动 · service/ → parser/）
- src/test/java/com/graphnexus/application/document/service/DocumentStatusTest.java            （移动 · 对应 L3 新路径）
- src/test/java/com/graphnexus/architecture/LayeredArchitectureTest.java                       （不动 · 使用 `..` 通配符，子包自动匹配）

禁动清单（与本次无关，AI 不许"顺手"碰）：
- pom.xml                                                           （禁动 · 依赖全量已配置）
- docs/项目规范.md                                                   （禁动 · 规范基准）
- docs/tech-stack-java.md                                           （禁动 · 技术决策基准）
- src/main/java/com/graphnexus/common/*                             （禁动 · 结构已合理）
- src/main/java/com/graphnexus/GraphNexusApplication.java           （禁动 · 启动类）
- src/main/resources/                                               （禁动 · 配置文件）
- src/main/java/com/graphnexus/infrastructure/mysql/document/*      （禁动内容 · DO/Repository/enum 仅 import 路径可能被外部引用更新）
```

### 0.5.2 既有抽象沿用对照表

| 本次需要 | 既有有没有？路径 | 决定 |
|---|---|---|
| 分层架构定义 | `docs/项目规范.md` §1.4.2 + §2.2 | **沿用**四层架构，本次只细化子包 |
| L2 接口/实现分离 | CONTEXT §1.4.5 依赖倒置原则 | **沿用**，本次通过 `service/` vs `service/impl/` 物理分离落实 |
| POJO 命名后缀 | CONTEXT DO/DTO/BO/VO/Query | **沿用**，BO/Query 统一进 `model/` |
| 包占位方式 | `package-info.java`（来自 init-platform DESIGN § D2） | **沿用**，所有新骨架用 package-info.java 占位 |
| ArchUnit 分层测试 | `LayeredArchitectureTest.java`（使用 `..` 通配符） | **沿用**，子包变化自动匹配，无需改规则 |
| DocumentParser 策略模式 | ADR-001（预留 parser/ 子包） | **沿用** ADR-001，本次正式落地产出 `parser/` |

### 0.5.3 沿用模式 vs 引入新模式

```
- 包占位：**沿用** package-info.java 模式（init-platform DESIGN § D2）
- L2 接口/实现组织：**引入新模式** service/ + service/impl/ 物理分离
  → 理由：当前 service/ 摊大饼（7 文件混放），项目规范 §1.4.5 要求依赖倒置（接口与 Impl 分离），本次从包结构层面落实
- model/ 子包：**引入新模式** 统一收纳 BO + Query + 领域值对象
  → 理由：当前 BO 和 ParseResult 与接口/实现混放，分离后职责清晰
- parser/ 子包：**引入新模式**（但 ADR-001 已预见，不算新模式算既定设计的落地）
  → 理由：ADR-001 Consequences 原文："如果未来需要多种解析器...可以通过子包 parser/ 组织"
- L3 config/ 子包：**引入新模式** 配置类与技术服务类分离
  → 理由：mysql/ 下 JpaAuditConfig 和 document/ 同级不合理，storage/ 下 MinioConfig 和 FileStorageService 混放也不合理
```

---

## 1. 决策清单

| # | 决策 | 备选 | 选择理由 | 取舍代价 |
|---|---|---|---|---|
| D1 | **L2 标准子包模板**：每个模块含 `service/`（接口）+ `service/impl/`（实现）+ `model/`（BO/Query/领域模型） | A: 保持当前扁平 service/ | 选 D1。项目规范 §1.4.5 要求接口与 Impl 分离（依赖倒置），当前 service/ 混放 7 个文件已违反此原则。物理分子包比命名后缀 `XxxImpl` 更直观，且 IDE 按包折叠即可看清模块全貌 | import 路径多一层（`..service.impl..`），但这是有意义的层级信息 |
| D2 | **`parser/` 为文档模块扩展，不进基础模板** | A: parser/ 进基础模板所有模块都有 | 选 D2。ADR-001 已明确 parser 是文档模块特有概念。graph 模块未来可能有 `strategy/`、query 可能有 `builder/`——各自按需扩展，不强求统一 | 模块间子包不完全对称，但"按需扩展"比"空壳统一"更诚实 |
| D3 | **L3 配置类归入 `config/` 子包**：`mysql/config/`、`storage/config/` 等 | A: 配置类放模块根路径（当前做法） | 选 D3。`JpaAuditConfig`（配置）与 `document/DocumentDO`（数据对象）层级不应平级；`MinioConfig`/`MinioProperties`（配置）与 `FileStorageService`（技术服务）职责不同 | import 路径多一层，但只有 3 个文件受影响 |
| D4 | **L3 数据模块保持扁平**：`mysql/document/` 内 DO + Repository + enum 不拆子包 | A: 拆为 `entity/` + `repository/` | 选 D4。当前 document/ 仅 3 个文件，拆 2 个子包导致每包 1~2 个文件，过度工程。未来一个模块的 DO 超过 5 个时再拆（YAGNI） | 未来文件增多时需再次重构——但触发阈值明确（>5 个 DO），届时成本可控 |
| D5 | **L3 预留模块骨架按规范注释创建**：neo4j→`config/` `node/` `edge/` `repository/`、redis→`config/`、mq→`config/` `queue/` `exchange/`、llm→`config/` `client/` | A: 预留模块只放一个空的 package-info.java | 选 D5。`docs/项目规范.md` §1.4.2 已用注释说明了每个基础设施模块的内容（如"Neo4j：配置、节点、边、Repository"），把注释落地为子包目录，新同学不需要 grep 规范文档就能"一眼看出代码放哪" | 创建 10+ 个空目录和 package-info.java，但每个文件只有 3~5 行，一次性成本 |
| D6 | **test 目录跟随 main 对齐**：测试类路径 = 被测试类路径镜像 | A: test 保持当前扁平，不跟随移动 | 选 D6。Maven 约定：test 包路径应镜像 main。当前 `DocumentStatusTest` 放在 `application/document/service/` 但测的是 `infrastructure/mysql/document/DocumentStatus`——路径不一致 | 4 个测试文件需移动 + 更新 package 声明 |

---

## 2. 架构图 · 目标包结构

```
com.graphnexus/
├── GraphNexusApplication.java
│
├── api/                                      # L1 API 层
│   ├── gateway/
│   │   ├── config/                           #   [新] 安全配置子包
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── document/
│   │   ├── controller/
│   │   │   └── DocumentController.java       #   import 路径更新
│   │   ├── dto/
│   │   │   ├── DocumentVO.java               #   import 路径更新
│   │   │   ├── ParseResultVO.java            #   import 路径更新
│   │   │   └── UpdateDocumentRequest.java
│   │   └── package-info.java
│   ├── graph/
│   │   ├── controller/
│   │   ├── dto/                              #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── analysis/
│   │   ├── controller/
│   │   ├── dto/                              #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── query/
│   │   ├── controller/
│   │   ├── dto/                              #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   └── basic/
│       ├── controller/
│       ├── dto/                              #   [新] 骨架
│       │   └── package-info.java
│       └── package-info.java
│
├── application/                              # L2 应用层
│   ├── document/
│   │   ├── service/                          #   接口（基础模板）
│   │   │   └── DocumentService.java
│   │   ├── service/impl/                     #   实现（基础模板）
│   │   │   └── DocumentServiceImpl.java
│   │   ├── model/                            #   BO/Query/领域模型（基础模板）
│   │   │   ├── DocumentBO.java
│   │   │   ├── UpdateDocumentBO.java
│   │   │   └── ParseResult.java
│   │   ├── parser/                           #   文档模块扩展（非基础模板）
│   │   │   ├── DocumentParser.java
│   │   │   └── PdfBoxDocumentParser.java
│   │   └── package-info.java
│   ├── graph/
│   │   ├── service/
│   │   ├── service/impl/                     #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── model/                            #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── analysis/
│   │   ├── service/
│   │   ├── service/impl/                     #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── model/                            #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── query/
│   │   ├── service/
│   │   ├── service/impl/                     #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── model/                            #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── basic/
│   │   ├── service/                          #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── service/impl/                     #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── model/                            #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   └── llmgateway/
│       ├── service/
│       ├── service/impl/                     #   [新] 骨架
│       │   └── package-info.java
│       ├── model/                            #   [新] 骨架
│       │   └── package-info.java
│       └── package-info.java
│
├── infrastructure/                           # L3 基础设施层
│   ├── mysql/
│   │   ├── config/                           #   [新] 配置子包
│   │   │   └── JpaAuditConfig.java
│   │   ├── document/                         #   保持扁平（仅 3 文件）
│   │   │   ├── DocumentDO.java
│   │   │   ├── DocumentRepository.java
│   │   │   └── DocumentStatus.java
│   │   └── package-info.java
│   ├── storage/
│   │   ├── config/                           #   [新] 配置子包
│   │   │   ├── MinioConfig.java
│   │   │   └── MinioProperties.java
│   │   ├── FileStorageService.java
│   │   └── package-info.java
│   ├── neo4j/
│   │   ├── config/                           #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── node/                             #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── edge/                             #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── repository/                       #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── redis/
│   │   ├── config/                           #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   ├── mq/
│   │   ├── config/                           #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── queue/                            #   [新] 骨架
│   │   │   └── package-info.java
│   │   ├── exchange/                         #   [新] 骨架
│   │   │   └── package-info.java
│   │   └── package-info.java
│   └── llm/
│       ├── config/                           #   [新] 骨架
│       │   └── package-info.java
│       ├── client/                           #   [新] 骨架
│       │   └── package-info.java
│       └── package-info.java
│
└── common/                                   # 公共模块（本次不动）
    ├── ApiResponse.java
    ├── PageResult.java
    ├── config/
    ├── exception/
    ├── logging/
    └── monitoring/
```

### 数据流（调用链不变，仅 import 路径更新）

```
L1  DocumentController
    │  import: api.document.dto.*           ← 路径不变
    │          application.document.service.DocumentService    ← 路径不变（接口留在 service/）
    │          application.document.model.DocumentBO           ← 更新（原 ..service.DocumentBO）
    │          application.document.model.UpdateDocumentBO     ← 更新（原 ..service.UpdateDocumentBO）
    │          application.document.model.ParseResult          ← 更新（原 ..service.ParseResult）
    │
    ▼
L2  DocumentServiceImpl
    │  import: application.document.service.DocumentService   ← 同包接口，import 不变
    │          application.document.parser.DocumentParser      ← 更新（原 ..service.DocumentParser）
    │          application.document.parser.PdfBoxDocumentParser← 自身移动，package 声明更新
    │          application.document.model.DocumentBO           ← 更新（原 ..service.DocumentBO）
    │          application.document.model.UpdateDocumentBO     ← 更新（原 ..service.UpdateDocumentBO）
    │          application.document.model.ParseResult          ← 更新（原 ..service.ParseResult）
    │          infrastructure.mysql.document.*                 ← 不变
    │          infrastructure.storage.FileStorageService       ← 不变
    │
    ▼
L3  DocumentDO / DocumentRepository / FileStorageService       ← 路径不变
    MinioConfig / MinioProperties                             ← 更新（storage/ → storage/config/）
    JpaAuditConfig                                             ← 更新（mysql/ → mysql/config/）
```

---

## 3. 关键状态机

不适用。本次不涉及任何状态变更。

---

## 4. ADR 索引

本次无需新增 ADR。关键决策 **D2（parser/ 子包）** 直接延续 ADR-001 的预见，不构成需要推翻或新增 ADR 的变更。

---

## 5. 风险

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | **Import 修正遗漏**：15+ 文件的 package/import 需手动同步，遗漏导致编译失败 | 编译不过，阻塞后续 TASK | 高 | 编译阶段 100% 可检测。DEV 每个子任务完成后立即 `mvn compile` 验证，不攒到最后 |
| R2 | **ArchUnit 规则意外匹配**：`..service.impl..` 路径可能被 ArchUnit 误判为 L3 访问（如果规则写死了具体包名而非通配符） | 分层测试误报 | 低 | 已验证：当前规则使用 `com.graphnexus.api..` 等通配符，`service/impl/` 仍在 `application..` 下，不会误判。DEV 阶段跑一次 ArchUnit 确认 |
| R3 | **Git 历史断裂**：移动文件后 `git log --follow` 可能无法追踪完整历史 | 代码考古变困难 | 中 | 使用 `git mv` 保留 rename 追踪（同一 commit 内先 mv 再改内容）。接受部分历史断裂——结构健康优先 |
| R4 | **IDE 缓存混乱**：IntelliJ IDEA 可能缓存旧包路径导致红色波浪线 | 开发体验暂时变差 | 中 | DEV 完成后提示用户 `File → Invalidate Caches`。Maven 命令行编译不依赖 IDE 缓存 |
| R5 | **未来模块扩展子包命名不一致**：各模块按需添加扩展子包时，可能出现 `strategy/` vs `strategies/` 等命名分歧 | 包结构逐步退化 | 低 | DESIGN.md § 2 架构图即为规范源。未来新增扩展子包需在对应 change 的 DESIGN 中声明并更新 `docs/package-structure-spec.md`（本次产出） |

---

## 6. 不在范围

- ❌ **不产出 ADR**：本次决策均不满足 ADR 门槛（不可逆性低，未来推翻成本仅为一次 import 重构）
- ❌ **不修改 ArchUnit 规则**：当前规则已用通配符，自动适应子包变化
- ❌ **不修改 `docs/项目规范.md`**：该文件在禁动清单。本次产出独立的 `docs/package-structure-spec.md`
- ❌ **不调整 Spring 组件扫描**：`@SpringBootApplication` 默认扫描 `com.graphnexus` 及子包，包路径变更不影响
- ❌ **不处理 application/llmgateway/ 的命名**：当前包名 `llmgateway` 不符合规范（应为 `llmgateway` 单独词或 `llm-gateway`？）。这是命名规范问题，不属于本次"细化子包"范围，应走独立 change

---

## 9. 架构沉淀建议

### 9.1 新增的可复用抽象

| 路径（预留） | 能力 | 触发场景 | 复用建议 |
|---|---|---|---|
| `application/<module>/service/` | 业务接口定义层 | 任何新业务模块 | 所有 L2 模块的 Service 接口放此处 |
| `application/<module>/service/impl/` | 业务实现层 | Service 接口的实现类 | 所有 Service 实现类放此处，命名 `XxxServiceImpl` |
| `application/<module>/model/` | BO + Query + 领域值对象 | 业务数据传递 | 所有 L2 模块的 BO/Query/领域模型放此处 |
| `application/document/parser/` | 文档解析器策略（模块扩展示例） | PDF/Office 等文档解析 | 仅 document 模块。接口 + 实现同包，切换实现时只加文件不改接口 |

### 9.2 新增 / 改变的项目级技术决策

| 决策 | 取值 | 影响范围 | 推翻代价 |
|---|---|---|---|
| L2 子包模板 | `service/` + `service/impl/` + `model/` | 所有 L2 业务模块 | 低——推翻只需一次 import 重构，与本次成本相当 |
| L3 配置分离 | `config/` 子包收纳所有配置类 | 所有 L3 基础设施模块 | 低 |

### 9.3 新增 / 修改的跨模块契约

不适用。本次不改变任何 API/Schema/事件。

### 9.4 新增 / 升级的依赖

不适用。本次不新增或升级依赖。

### 9.5 禁动清单变化

```
新增禁动：无。本次产出的 docs/package-structure-spec.md 建议作为后续 DEV 的参考文档，但不设禁动
解禁：无
```

---

> 本文件不含代码实现。下一步进入 `TASK.md` 拆解原子任务。