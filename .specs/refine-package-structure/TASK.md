# TASK: 细化项目包结构，建立统一分层子包模板

- **Change ID**: `refine-package-structure`
- **关联**: `@.specs/refine-package-structure/DESIGN.md`、`@.specs/CONTEXT.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P], T03[P]
Wave 2:            T04              (depends on T01, T02)
Wave 3 (parallel): T05[P], T06[P]   (depends on T04)
Wave 4:            T07              (depends on T04, T05, T06)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>创建预留模块骨架子包 + package-info.java（L1/L2/L3）</name>
  <read_files>
    .specs/refine-package-structure/DESIGN.md
    src/main/java/com/graphnexus/api/gateway/package-info.java
    src/main/java/com/graphnexus/application/document/package-info.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/gateway/config/package-info.java
    src/main/java/com/graphnexus/api/graph/dto/package-info.java
    src/main/java/com/graphnexus/api/analysis/dto/package-info.java
    src/main/java/com/graphnexus/api/query/dto/package-info.java
    src/main/java/com/graphnexus/api/basic/dto/package-info.java
    src/main/java/com/graphnexus/application/graph/service/impl/package-info.java
    src/main/java/com/graphnexus/application/graph/model/package-info.java
    src/main/java/com/graphnexus/application/analysis/service/impl/package-info.java
    src/main/java/com/graphnexus/application/analysis/model/package-info.java
    src/main/java/com/graphnexus/application/query/service/impl/package-info.java
    src/main/java/com/graphnexus/application/query/model/package-info.java
    src/main/java/com/graphnexus/application/basic/service/package-info.java
    src/main/java/com/graphnexus/application/basic/service/impl/package-info.java
    src/main/java/com/graphnexus/application/basic/model/package-info.java
    src/main/java/com/graphnexus/application/llmgateway/service/impl/package-info.java
    src/main/java/com/graphnexus/application/llmgateway/model/package-info.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/package-info.java
    src/main/java/com/graphnexus/infrastructure/storage/config/package-info.java
    src/main/java/com/graphnexus/infrastructure/neo4j/config/package-info.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/package-info.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/package-info.java
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/package-info.java
    src/main/java/com/graphnexus/infrastructure/redis/config/package-info.java
    src/main/java/com/graphnexus/infrastructure/mq/config/package-info.java
    src/main/java/com/graphnexus/infrastructure/mq/queue/package-info.java
    src/main/java/com/graphnexus/infrastructure/mq/exchange/package-info.java
    src/main/java/com/graphnexus/infrastructure/llm/config/package-info.java
    src/main/java/com/graphnexus/infrastructure/llm/client/package-info.java
  </write_files>
  <action>
    按 DESIGN.md §2 架构图创建所有 [新] 标记的空子包目录及 package-info.java。
    每个 package-info.java 包含：
    ① 包级 Javadoc（一句话描述该子包职责，如 "Neo4j 图节点实体定义"）
    ② @NonNullApi 注解（org.springframework.lang.NonNullApi）
    参照已有 package-info.java 的风格（如 api/gateway/package-info.java）。
    共创建 28 个子包。
  </action>
  <verify>find src/main/java -type d | wc -l && mvn compile -q 2>&1 | head -5</verify>
  <done>28 个新子包目录已创建，每个含 package-info.java；mvn compile 无新增错误（新增空包不影响编译）</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>移动 L3 配置类到 config/ 子包</name>
  <read_files>
    .specs/refine-package-structure/DESIGN.md
    src/main/java/com/graphnexus/infrastructure/mysql/JpaAuditConfig.java
    src/main/java/com/graphnexus/infrastructure/storage/MinioConfig.java
    src/main/java/com/graphnexus/infrastructure/storage/MinioProperties.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mysql/JpaAuditConfig.java
    src/main/java/com/graphnexus/infrastructure/mysql/config/JpaAuditConfig.java
    src/main/java/com/graphnexus/infrastructure/storage/MinioConfig.java
    src/main/java/com/graphnexus/infrastructure/storage/config/MinioConfig.java
    src/main/java/com/graphnexus/infrastructure/storage/MinioProperties.java
    src/main/java/com/graphnexus/infrastructure/storage/config/MinioProperties.java
    src/main/java/com/graphnexus/infrastructure/storage/FileStorageService.java
  </write_files>
  <action>
    将 3 个 L3 配置类移动到 config/ 子包（见 DESIGN § D3）：
    1. JpaAuditConfig.java: infrastructure.mysql → infrastructure.mysql.config（git mv）
    2. MinioConfig.java: infrastructure.storage → infrastructure.storage.config（git mv）
    3. MinioProperties.java: infrastructure.storage → infrastructure.storage.config（git mv）
    
    每个文件只改第 1 行 package 声明。经 grep 验证：这 3 个类均为 Spring @Configuration/@ConfigurationProperties，
    由组件扫描自动发现，无其他文件显式 import 它们，因此无需修改任何其他文件的 import 语句。
    
    移动后删除旧路径下的原文件（git rm）。
  </action>
  <verify>mvn compile -q 2>&1</verify>
  <done>3 个配置类已移动到 config/ 子包；mvn compile 零错误通过</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>产出包结构规范文档 docs/package-structure-spec.md</name>
  <read_files>
    .specs/refine-package-structure/DESIGN.md
    docs/项目规范.md
    .specs/CONTEXT.md
  </read_files>
  <write_files>
    docs/package-structure-spec.md
  </write_files>
  <action>
    基于 DESIGN.md §2 的目标包架构图，撰写独立的包结构规范文档。
    内容包括：
    1. 四层架构总览（L1→L2→L3→common 调用规则图）
    2. 每层的标准子包模板（如 L2：service/ + service/impl/ + model/）
    3. 模块扩展规则（如 document 模块的 parser/，说明何时可加扩展子包）
    4. POJO 放置规则（DTO/VO→L1 dto/，BO/Query→L2 model/，DO→L3 mysql/<module>/）
    5. 预留模块骨架子包一览（neo4j/redis/mq/llm 的预期子包）
    6. 反例：列出当前已消除的结构模式（"不要把 BO 和 Service 接口混放"）
    
    文档面向后续 DEV 使用，目标是"新同学不 grep 也能知道代码放哪"。
  </action>
  <verify>test -f docs/package-structure-spec.md && wc -l docs/package-structure-spec.md</verify>
  <done>docs/package-structure-spec.md 已创建，覆盖四层全部模块的子包规范</done>
  <depends_on></depends_on>
</task>

<task id="T04" parallel="false" status="done">
  <name>拆分 L2 document 模块：移动文件到 model/ parser/ service/impl/ + 修正全部 import</name>
  <read_files>
    .specs/refine-package-structure/DESIGN.md
    src/main/java/com/graphnexus/application/document/service/DocumentService.java
    src/main/java/com/graphnexus/application/document/service/DocumentServiceImpl.java
    src/main/java/com/graphnexus/application/document/service/DocumentBO.java
    src/main/java/com/graphnexus/application/document/service/UpdateDocumentBO.java
    src/main/java/com/graphnexus/application/document/service/ParseResult.java
    src/main/java/com/graphnexus/application/document/service/DocumentParser.java
    src/main/java/com/graphnexus/application/document/service/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/java/com/graphnexus/api/document/dto/DocumentVO.java
    src/main/java/com/graphnexus/api/document/dto/ParseResultVO.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/document/service/DocumentService.java
    src/main/java/com/graphnexus/application/document/service/DocumentServiceImpl.java
    src/main/java/com/graphnexus/application/document/service/impl/DocumentServiceImpl.java
    src/main/java/com/graphnexus/application/document/service/DocumentBO.java
    src/main/java/com/graphnexus/application/document/model/DocumentBO.java
    src/main/java/com/graphnexus/application/document/service/UpdateDocumentBO.java
    src/main/java/com/graphnexus/application/document/model/UpdateDocumentBO.java
    src/main/java/com/graphnexus/application/document/service/ParseResult.java
    src/main/java/com/graphnexus/application/document/model/ParseResult.java
    src/main/java/com/graphnexus/application/document/service/DocumentParser.java
    src/main/java/com/graphnexus/application/document/parser/DocumentParser.java
    src/main/java/com/graphnexus/application/document/service/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/application/document/parser/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/java/com/graphnexus/api/document/dto/DocumentVO.java
    src/main/java/com/graphnexus/api/document/dto/ParseResultVO.java
  </write_files>
  <action>
    按 DESIGN § D1/D2 拆分 L2 document 模块的大饼包。具体步骤：

    **第1步 · git mv 移动文件**：
    - DocumentBO.java → model/DocumentBO.java
    - UpdateDocumentBO.java → model/UpdateDocumentBO.java
    - ParseResult.java → model/ParseResult.java
    - DocumentParser.java → parser/DocumentParser.java
    - PdfBoxDocumentParser.java → parser/PdfBoxDocumentParser.java
    - DocumentServiceImpl.java → service/impl/DocumentServiceImpl.java
    - DocumentService.java 留在 service/（不动）

    **第2步 · 更新被移动文件的 package 声明**（6 个文件）：
    - model/*: package com.graphnexus.application.document.model;
    - parser/*: package com.graphnexus.application.document.parser;
    - service/impl/*: package com.graphnexus.application.document.service.impl;

    **第3步 · 修正 L2 内部新增的 import**（同包引用变跨包子包引用）：
    - DocumentServiceImpl.java（现位于 service/impl/）需新增 import：
      - import com.graphnexus.application.document.model.DocumentBO;
      - import com.graphnexus.application.document.model.UpdateDocumentBO;
      - import com.graphnexus.application.document.model.ParseResult;
      - import com.graphnexus.application.document.parser.DocumentParser;
      - import com.graphnexus.application.document.service.DocumentService;
    - PdfBoxDocumentParser.java（现位于 parser/）需新增 import：
      - import com.graphnexus.application.document.model.ParseResult;

    **第4步 · 修正 L1 对 L2 的 import 路径**：
    - DocumentController.java：将 ..service.DocumentBO → ..model.DocumentBO（1处）
      将 ..service.UpdateDocumentBO → ..model.UpdateDocumentBO（1处）
      将 ..service.ParseResult → ..model.ParseResult（1处）
      （DocumentService 路径不变，无需改）
    - DocumentVO.java：将 ..service.DocumentBO → ..model.DocumentBO（1处）
    - ParseResultVO.java：将 ..service.ParseResult → ..model.ParseResult（1处）

    注意：此任务不涉及 DocumentStatus、DocumentDO、DocumentRepository——它们在 L3，路径不变。
  </action>
  <verify>mvn compile -q 2>&1</verify>
  <done>L2 document 模块已拆分为 service/ + service/impl/ + model/ + parser/；mvn compile 零错误通过</done>
  <depends_on>T01, T02</depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>移动测试文件到正确包路径 + 修正 import</name>
  <read_files>
    .specs/refine-package-structure/DESIGN.md
    src/test/java/com/graphnexus/application/document/service/PdfBoxDocumentParserTest.java
    src/test/java/com/graphnexus/application/document/service/DocumentStatusTest.java
    src/main/java/com/graphnexus/application/document/parser/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/infrastructure/mysql/document/DocumentStatus.java
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/document/service/PdfBoxDocumentParserTest.java
    src/test/java/com/graphnexus/application/document/parser/PdfBoxDocumentParserTest.java
    src/test/java/com/graphnexus/application/document/service/DocumentStatusTest.java
    src/test/java/com/graphnexus/infrastructure/mysql/document/DocumentStatusTest.java
    src/test/java/com/graphnexus/application/document/service/DocumentServiceTest.java
    src/test/java/com/graphnexus/application/document/service/DocumentProcessingIntegrationTest.java
  </write_files>
  <action>
    按 DESIGN § D6 将测试文件对齐到正确包路径：

    1. PdfBoxDocumentParserTest.java：
       git mv → application/document/parser/PdfBoxDocumentParserTest.java
       更新 package 声明：com.graphnexus.application.document.service → com.graphnexus.application.document.parser
       检查 import：PdfBoxDocumentParser 已在同包（parser/），无需 import；若引用了 ParseResult（在 model/），需新增 import com.graphnexus.application.document.model.ParseResult

    2. DocumentStatusTest.java：
       git mv → infrastructure/mysql/document/DocumentStatusTest.java
       更新 package 声明：com.graphnexus.application.document.service → com.graphnexus.infrastructure.mysql.document
       这是纠错——该测试测的是 L3 的 DocumentStatus，却放在 L2 的 test 路径下。
       DocumentStatus 在同包，无需单独 import。
  </action>
  <verify>mvn test-compile -q 2>&1</verify>
  <done>2 个测试文件已移动到正确包路径；mvn test-compile 零错误通过</done>
  <depends_on>T04</depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>验证 ArchUnit 分层架构测试通过</name>
  <read_files>
    src/test/java/com/graphnexus/architecture/LayeredArchitectureTest.java
    .specs/refine-package-structure/DESIGN.md
  </read_files>
  <write_files>
  </write_files>
  <action>
    运行 ArchUnit 分层架构测试，确认子包变更未破坏分层规则。

    当前 ArchUnit 规则使用通配符 `com.graphnexus.api..` / `com.graphnexus.application..` 等，
    新增的 `service.impl`、`model`、`parser` 子包仍在 `application..` 下，理论上自动匹配。
    但需实际运行确认无意外。

    如果失败：分析失败原因，确认是规则需微调还是包移动出错。
    如果是规则需微调（如 `service.impl` 被误判），更新 LayeredArchitectureTest.java 并记录变更。
  </action>
  <verify>mvn test -Dtest="LayeredArchitectureTest" -pl . 2>&1</verify>
  <done>ArchUnit LayeredArchitectureTest 通过，四层依赖方向校验无误</done>
  <depends_on>T04</depends_on>
</task>

<task id="T07" parallel="false" status="done">
  <name>全量验证：编译 + 全部测试通过</name>
  <read_files>
  </read_files>
  <write_files>
  </write_files>
  <action>
    运行完整验证链条：
    1. mvn compile —— 确认所有 package/import 正确
    2. mvn test —— 运行全部测试（含 ArchUnit + 23 个单元测试）

    预期：
    - compile: BUILD SUCCESS
    - test: 23+ tests passed, 0 failures, 0 errors (T10 集成测试可能需要 Docker，若环境无 Docker 则跳过)
    
    如果失败：根据失败信息回溯对应 task 修复。
  </action>
  <verify>mvn clean compile && mvn test -Dtest="!DocumentProcessingIntegrationTest" 2>&1 | tail -20</verify>
  <done>mvn compile 零错误 + mvn test 全部通过（23 tests, 0 failures）；包结构细化完成</done>
  <depends_on>T04, T05, T06</depends_on>
</task>
```

---

## 状态字段说明

- `status="pending"` — 未开始
- `status="in_progress"` — 进行中（同时只允许一个非 [P] 任务为此状态）
- `status="done"` — 已完成（verify 通过）
- `status="blocked"` — 阻塞（必须在文件末尾「阻塞日志」记录）

---

## 阻塞日志

| 任务 | 阻塞原因 | 待人工决策项 | 时间 |
|---|---|---|---|
|  |  |  |  |

---

## Fix 任务（来自 REVIEW / INTEGRATION）

> 此区域由 review/integration 阶段自动追加，编号 `T-FIX-XX`。

```xml
<!-- 占位 -->
```