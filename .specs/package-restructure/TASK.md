# TASK: 规范化重构项目包结构布局

- **Change ID**: `package-restructure`
- **关联**: `@.specs/package-restructure/DESIGN.md`、`@.specs/package-restructure/CHANGE.md`
- **路径**: 中等（跳 REQUIREMENT，DESIGN → TASK → DEV → TEST → REVIEW → INTEGRATION）

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P]
Wave 2:            T03              (depends on Wave 1)
Wave 3 (parallel): T04[P], T05[P], T06[P], T07[P]  (depends on Wave 2)
Wave 4 (parallel): T08[P], T09[P], T10[P], T11[P], T12[P]  (depends on Wave 3)
Wave 5:            T13              (depends on T04)
Wave 6:            T14              (depends on Wave 4, Wave 5)
Wave 7:            T15              (depends on Wave 6)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。Wave 3 先做 L2（application），Wave 4 再做 L1（api），保证 L1 import 的 L2 目标包已存在。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="pending">
  <name>删除全部 package-info.java（~67 个文件）</name>
  <read_files>
    src/main/java/com/GraphNexus/**/package-info.java
    src/test/java/com/GraphNexus/**/package-info.java
  </read_files>
  <write_files>
    src/main/java/com/GraphNexus/**/package-info.java
    src/test/java/com/GraphNexus/**/package-info.java
  </write_files>
  <action>
    全项目搜索并删除所有 package-info.java 文件。
    使用 find + git rm：
      find src/ -name "package-info.java" -exec git rm {} \;
    
    预期删除 ~67 个文件（66 main + 1 test）。
    删除后检查是否有空目录残留——空目录 git 不跟踪，无需处理。
  </action>
  <verify>find src/ -name "package-info.java" | wc -l | xargs test 0 -eq</verify>
  <done>零个 package-info.java 文件残留</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="pending">
  <name>更新 ArchUnit 分层架构测试规则</name>
  <read_files>
    src/test/java/com/GraphNexus/architecture/LayeredArchitectureTest.java
    .specs/package-restructure/DESIGN.md
    .specs/CONTEXT.md
  </read_files>
  <write_files>
    src/test/java/com/GraphNexus/architecture/LayeredArchitectureTest.java
  </write_files>
  <action>
    更新 LayeredArchitectureTest.java 中的包名匹配模式：
    1. 所有 `com.GraphNexus` → `com.graphnexus`（根包重命名）
    2. `com.graphnexus.application.document..` → `com.graphnexus.application.file..`（document 重命名为 file）
    3. `com.graphnexus.application.basic..` → `com.graphnexus.application.system..`（basic 重命名为 system）
    4. `com.graphnexus.api.document..` → `com.graphnexus.api.file..`
    5. `com.graphnexus.api.basic..` → `com.graphnexus.api.system..`
    
    注意：当前规则使用通配符 `com.graphnexus.api..` / `com.graphnexus.application..` /
    `com.graphnexus.infrastructure..` / `com.graphnexus.common..`，
    新增的子模块（如 application.file.parse..）自动被 `application..` 通配符覆盖。
    只需修正模块名引用即可。
  </action>
  <verify>grep -r "com\.GraphNexus\|com\.graphnexus\.application\.document\|com\.graphnexus\.application\.basic\|com\.graphnexus\.api\.document\|com\.graphnexus\.api\.basic" src/test/java/com/GraphNexus/architecture/LayeredArchitectureTest.java; echo "检查完毕：旧包名引用应为零"</verify>
  <done>ArchUnit 规则中无旧包名引用</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="false" status="pending">
  <name>根包重命名：com.GraphNexus → com.graphnexus（全项目）</name>
  <read_files>
    src/main/java/com/GraphNexus/**/*.java
    src/test/java/com/GraphNexus/**/*.java
    src/main/resources/**/*.yml
    src/main/resources/**/*.properties
  </read_files>
  <write_files>
    src/main/java/com/GraphNexus/**/*.java
    src/test/java/com/GraphNexus/**/*.java
    src/main/resources/**/*.yml
  </write_files>
  <action>
    将全部 Java 文件 + 配置文件中的 `com.GraphNexus` 替换为 `com.graphnexus`。

    **步骤 1 · 目录重命名（git mv）**：
    在 macOS 大小写不敏感的文件系统上，需要两步 git mv：
      git mv src/main/java/com/GraphNexus src/main/java/com/graphnexus_tmp
      git mv src/main/java/com/graphnexus_tmp src/main/java/com/graphnexus
      git mv src/test/java/com/GraphNexus src/test/java/com/graphnexus_tmp
      git mv src/test/java/com/graphnexus_tmp src/test/java/com/graphnexus

    **步骤 2 · 文件内容替换（sed）**：
      find src/ -name "*.java" -exec sed -i '' 's/com\.GraphNexus/com.graphnexus/g' {} +
      find src/ -name "*.yml" -o -name "*.properties" | xargs sed -i '' 's/com\.GraphNexus/com.graphnexus/g'

    **步骤 3 · 检查遗漏**：
      grep -r "com\.GraphNexus" src/  应返回零结果
    
    注意：`application.yml` 中可能有 `server.servlet.context-path`、
    `spring.application.name` 等包含 `graphnexus` 字符串的配置项，
    sed 替换 `com.GraphNexus`→`com.graphnexus` 精确匹配点号分隔，不会误伤配置值。
  </action>
  <verify>grep -r "com\.GraphNexus" src/ | wc -l | xargs test 0 -eq && echo "零旧包名残留"</verify>
  <done>grep -r "com\.GraphNexus" src/ 返回零结果；所有文件 package 声明和 import 均使用 com.graphnexus</done>
  <depends_on>T01, T02</depends_on>
</task>

<task id="T04" parallel="true" status="pending">
  <name>L2 application/document/ → application/file/{upload,parse,core}</name>
  <read_files>
    src/main/java/com/graphnexus/application/document/**/*.java
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/document/**/*.java
    src/main/java/com/graphnexus/application/file/upload/model/GradeRecordBO.java
    src/main/java/com/graphnexus/application/file/upload/model/GradeUploadResultBO.java
    src/main/java/com/graphnexus/application/file/upload/service/GradeService.java
    src/main/java/com/graphnexus/application/file/upload/service/impl/GradeServiceImpl.java
    src/main/java/com/graphnexus/application/file/parse/model/ParseResult.java
    src/main/java/com/graphnexus/application/file/parse/model/FileParseRequest.java
    src/main/java/com/graphnexus/application/file/parse/model/FileParseResult.java
    src/main/java/com/graphnexus/application/file/parse/model/FileParseType.java
    src/main/java/com/graphnexus/application/file/parse/parser/DocumentParser.java
    src/main/java/com/graphnexus/application/file/parse/parser/PdfBoxDocumentParser.java
    src/main/java/com/graphnexus/application/file/parse/parser/MinerUDocumentParser.java
    src/main/java/com/graphnexus/application/file/parse/parser/FileParser.java
    src/main/java/com/graphnexus/application/file/parse/parser/FileParserRegistry.java
    src/main/java/com/graphnexus/application/file/parse/parser/CsvGradeParser.java
    src/main/java/com/graphnexus/application/file/core/model/DocumentBO.java
    src/main/java/com/graphnexus/application/file/core/model/UpdateDocumentBO.java
    src/main/java/com/graphnexus/application/file/core/model/DeleteResultBO.java
    src/main/java/com/graphnexus/application/file/core/service/DocumentService.java
    src/main/java/com/graphnexus/application/file/core/service/impl/DocumentServiceImpl.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.9 映射表，将 application/document/ 下 19 个文件拆入 file/{upload,parse,core}：

    **upload/（成绩上传）**：
    - model/GradeRecordBO.java, GradeUploadResultBO.java
    - service/GradeService.java → service/impl/GradeServiceImpl.java

    **parse/（文档解析）**：
    - model/ParseResult.java + FileParseRequest/Result/Type（从 parser/ 移入 model/）
    - parser/ 下 6 个解析器（DocumentParser, PdfBox, MinerU, FileParser, FileParserRegistry, CsvGradeParser）
    - 注意：FileParseRequest/Result/Type 当前在 parser/ 包，移入 model/ 包（它们是模型对象不是解析器）

    **core/（文档管理）**：
    - model/DocumentBO.java, UpdateDocumentBO.java, DeleteResultBO.java
    - service/DocumentService.java → service/impl/DocumentServiceImpl.java

    每个文件操作：
    1. git mv 到新路径
    2. 更新 package 声明（如 ..application.document.model → ..application.file.core.model）
    3. 更新本文件内的 import 语句（同模块其他类的新路径）
    
    旧 application/document/ 目录清理（git rm 残留文件，如有）。
  </action>
  <verify>test ! -d src/main/java/com/graphnexus/application/document && test -d src/main/java/com/graphnexus/application/file/upload && test -d src/main/java/com/graphnexus/application/file/parse/parser && test -d src/main/java/com/graphnexus/application/file/core/service/impl && echo "结构正确"</verify>
  <done>application/document/ 目录不存在；file/{upload,parse,core} 三个子模块各含正确文件</done>
  <depends_on>T03</depends_on>
</task>

<task id="T05" parallel="true" status="pending">
  <name>L2 application/graph/ → core/construction/fusion/metrics</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/**/*.java
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/model/ExtractionResultBO.java
    src/main/java/com/graphnexus/application/graph/model/GraphSubgraphBO.java
    src/main/java/com/graphnexus/application/graph/service/GraphService.java
    src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/application/graph/extraction/**/*.java
    src/main/java/com/graphnexus/application/graph/core/model/GraphSubgraphBO.java
    src/main/java/com/graphnexus/application/graph/core/model/ExtractionResultBO.java
    src/main/java/com/graphnexus/application/graph/core/service/GraphService.java
    src/main/java/com/graphnexus/application/graph/core/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/application/graph/construction/model/ExtractionRawResult.java
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionService.java
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionJsonParser.java
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionPromptBuilder.java
    src/main/java/com/graphnexus/application/graph/construction/service/ExtractionValidator.java
    src/main/java/com/graphnexus/application/graph/fusion/**/*.java
    src/main/java/com/graphnexus/application/graph/metrics/**/*.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.10 映射表重组 graph/ 模块：

    **core/（图管理：查询、删除）**：
    - 原 graph/model/GraphSubgraphBO.java, ExtractionResultBO.java → core/model/
    - 原 graph/service/GraphService.java → core/service/
    - 原 graph/service/impl/GraphServiceImpl.java → core/service/impl/

    **construction/（图谱构建，原 extraction/）**：
    - 原 extraction/ExtractionService → construction/service/
    - 原 extraction/ExtractionJsonParser, ExtractionPromptBuilder, ExtractionValidator → construction/service/（这些是服务内部组件，保留在 service 包）
    - 原 extraction/ExtractionRawResult → construction/model/

    **fusion/（图谱融合）**：
    - 全部文件路径不变（config/model/service/strategy 已在正确位置）
    - 仅更新 package 声明（..application.graph.fusion.xxx 保持不变，因父包 graph 未变）

    **metrics/（图指标）**：
    - 全部文件路径不变（config/event/model/service 已在正确位置）
    - 仅更新 package 声明（不变）

    每个文件：git mv → 更新 package 声明 → 更新本文件 import。
    清理旧 application/graph/model/ 和 application/graph/service/ 和 application/graph/extraction/。
  </action>
  <verify>test -d src/main/java/com/graphnexus/application/graph/core && test -d src/main/java/com/graphnexus/application/graph/construction && test ! -d src/main/java/com/graphnexus/application/graph/extraction && test ! -d src/main/java/com/graphnexus/application/graph/model && test ! -d src/main/java/com/graphnexus/application/graph/service && echo "graph 结构正确"</verify>
  <done>graph/core/, construction/, fusion/, metrics/ 四个子模块各含正确文件；旧 extraction/, model/, service/ 已清除</done>
  <depends_on>T03</depends_on>
</task>

<task id="T06" parallel="true" status="pending">
  <name>L2 application/query/ → chat/prompt/conversation</name>
  <read_files>
    src/main/java/com/graphnexus/application/query/**/*.java
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/query/config/AsyncConfig.java
    src/main/java/com/graphnexus/application/query/config/QueryProperties.java
    src/main/java/com/graphnexus/application/query/model/QueryIntent.java
    src/main/java/com/graphnexus/application/query/model/QueryResultBO.java
    src/main/java/com/graphnexus/application/query/service/QueryService.java
    src/main/java/com/graphnexus/application/query/service/impl/QueryServiceImpl.java
    src/main/java/com/graphnexus/application/query/prompt/PromptTemplateService.java
    src/main/java/com/graphnexus/application/query/chat/config/AsyncConfig.java
    src/main/java/com/graphnexus/application/query/chat/config/QueryProperties.java
    src/main/java/com/graphnexus/application/query/chat/model/QueryIntent.java
    src/main/java/com/graphnexus/application/query/chat/model/QueryResultBO.java
    src/main/java/com/graphnexus/application/query/chat/service/QueryService.java
    src/main/java/com/graphnexus/application/query/chat/service/impl/QueryServiceImpl.java
    src/main/java/com/graphnexus/application/query/prompt/service/PromptTemplateService.java
    src/main/java/com/graphnexus/application/query/conversation/service/package-info.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.11 映射表拆分 query/ 模块：

    **chat/（智能问答）**：
    - 原 service/QueryService → chat/service/
    - 原 service/impl/QueryServiceImpl → chat/service/impl/
    - 原 model/QueryIntent, QueryResultBO → chat/model/
    - 原 config/AsyncConfig, QueryProperties → chat/config/

    **prompt/（Prompt 模板）**：
    - 原 prompt/PromptTemplateService → prompt/service/
    - prompt/ 下建 model/ 空骨架 + service/impl/ 空骨架

    **conversation/（会话管理，预留）**：
    - 新建 conversation/service/ + conversation/service/impl/ + conversation/model/ 空骨架
    - 无现有文件迁入

    每个文件：git mv → 更新 package 声明 → 更新 import。
    清理旧 application/query/service/, config/, model/, prompt/ 目录。
  </action>
  <verify>test -d src/main/java/com/graphnexus/application/query/chat && test -d src/main/java/com/graphnexus/application/query/prompt && test -d src/main/java/com/graphnexus/application/query/conversation && test ! -d src/main/java/com/graphnexus/application/query/service && test ! -d src/main/java/com/graphnexus/application/query/config && test ! -d src/main/java/com/graphnexus/application/query/model && echo "query 结构正确"</verify>
  <done>query/chat/, prompt/, conversation/ 三个子模块各含正确文件；旧 service/, config/, model/, prompt/ 已清除</done>
  <depends_on>T03</depends_on>
</task>

<task id="T07" parallel="true" status="pending">
  <name>L2 application/basic/ → application/system/ 重命名</name>
  <read_files>
    src/main/java/com/graphnexus/application/basic/**/*.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/basic/**/*.java
    src/main/java/com/graphnexus/application/system/**/*.java
  </write_files>
  <action>
    将 application/basic/ 目录重命名为 application/system/：
    1. git mv application/basic application/system
    2. 更新所有文件内的 package 声明：..application.basic → ..application.system
    3. 更新所有 import 语句中的旧包名
    
    注意：basic/ 当前可能仅有空目录（无 .java 文件），若是则只需目录重命名。
    若有 .java 文件，按上述步骤处理。
  </action>
  <verify>test -d src/main/java/com/graphnexus/application/system && test ! -d src/main/java/com/graphnexus/application/basic && echo "system 重命名成功"</verify>
  <done>application/system/ 存在，application/basic/ 不存在</done>
  <depends_on>T03</depends_on>
</task>

<task id="T08" parallel="true" status="pending">
  <name>L1 api/gateway/ 移除 + OpenApiConfig → common/config/</name>
  <read_files>
    src/main/java/com/graphnexus/api/gateway/config/OpenApiConfig.java
    src/main/java/com/graphnexus/common/config/
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/gateway/config/OpenApiConfig.java
    src/main/java/com/graphnexus/common/config/OpenApiConfig.java
  </write_files>
  <action>
    1. git mv api/gateway/config/OpenApiConfig.java → common/config/OpenApiConfig.java
    2. 更新 package 声明：..api.gateway.config → ..common.config
    3. 清理 import（移除旧包自引用）
    4. git rm -r api/gateway/（删除空目录）
    
    注意：OpenApiConfig 是 SpringDoc/Swagger 配置类，移到 common/config/
    后仍然由 @ComponentScan 自动发现，无需额外配置。
  </action>
  <verify>test -f src/main/java/com/graphnexus/common/config/OpenApiConfig.java && test ! -d src/main/java/com/graphnexus/api/gateway && echo "gateway 移除成功"</verify>
  <done>OpenApiConfig 位于 common/config/；api/gateway/ 目录不存在</done>
  <depends_on>T04, T05, T06, T07</depends_on>
</task>

<task id="T09" parallel="true" status="pending">
  <name>L1 api/document/ → api/file/ + dto 子包化</name>
  <read_files>
    src/main/java/com/graphnexus/api/document/**/*.java
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/java/com/graphnexus/api/document/dto/*.java
    src/main/java/com/graphnexus/api/file/controller/DocumentController.java
    src/main/java/com/graphnexus/api/file/dto/core/DocumentVO.java
    src/main/java/com/graphnexus/api/file/dto/core/DeleteResultVO.java
    src/main/java/com/graphnexus/api/file/dto/core/UpdateDocumentRequest.java
    src/main/java/com/graphnexus/api/file/dto/parse/ParseResultVO.java
    src/main/java/com/graphnexus/api/file/dto/upload/GradeRecordVO.java
    src/main/java/com/graphnexus/api/file/dto/upload/GradeUploadResultVO.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.3 映射表：
    
    1. git mv api/document → api/file_tmp → api/file（处理大小写无关的文件系统）
    2. controller/DocumentController.java 保持在 controller/ 下
    3. dto 拆分：
       - dto/core/：DocumentVO, DeleteResultVO, UpdateDocumentRequest（文档管理）
       - dto/parse/：ParseResultVO（解析相关）
       - dto/upload/：GradeRecordVO, GradeUploadResultVO（上传相关）
    
    每个文件：git mv → 更新 package 声明 → 更新 import。
    注意 DocumentController 引用的 L2 类已在本 wave 之前（Wave 3）完成重命名，
    需更新 import：..application.document.xxx → ..application.file.{core,parse,upload}.xxx
  </action>
  <verify>test -d src/main/java/com/graphnexus/api/file/dto/core && test -d src/main/java/com/graphnexus/api/file/dto/parse && test -d src/main/java/com/graphnexus/api/file/dto/upload && test ! -d src/main/java/com/graphnexus/api/document && echo "file 结构正确"</verify>
  <done>api/file/ 含 controller + dto/{core,parse,upload}；api/document/ 不存在</done>
  <depends_on>T04, T05, T06, T07</depends_on>
</task>

<task id="T10" parallel="true" status="pending">
  <name>L1 api/graph/dto/ → dto/{graph,fusion,metrics} 子包化</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/dto/*.java
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/dto/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/GraphSubgraphVO.java
    src/main/java/com/graphnexus/api/graph/dto/FusionExecuteVO.java
    src/main/java/com/graphnexus/api/graph/dto/FusionRollbackVO.java
    src/main/java/com/graphnexus/api/graph/dto/FusionStatusVO.java
    src/main/java/com/graphnexus/api/graph/dto/MetricResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
    src/main/java/com/graphnexus/api/graph/dto/graph/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/graph/GraphSubgraphVO.java
    src/main/java/com/graphnexus/api/graph/dto/fusion/FusionExecuteVO.java
    src/main/java/com/graphnexus/api/graph/dto/fusion/FusionRollbackVO.java
    src/main/java/com/graphnexus/api/graph/dto/fusion/FusionStatusVO.java
    src/main/java/com/graphnexus/api/graph/dto/metrics/MetricResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/metrics/MetricsQueryRequest.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.4 映射表，将 api/graph/dto/ 下 7 个类拆入三个子包：

    - dto/graph/：ExtractionResultVO, GraphSubgraphVO（对应 GraphController）
    - dto/fusion/：FusionExecuteVO, FusionRollbackVO, FusionStatusVO（对应 FusionController）
    - dto/metrics/：MetricResultVO, MetricsQueryRequest（对应 MetricsController）

    每个文件：git mv → 更新 package 声明 → 更新 import。
    注意 controller 中引用了这些 DTO，需更新 controller 的 import 语句
    （GraphController, FusionController, MetricsController）。
  </action>
  <verify>test -d src/main/java/com/graphnexus/api/graph/dto/graph && test -d src/main/java/com/graphnexus/api/graph/dto/fusion && test -d src/main/java/com/graphnexus/api/graph/dto/metrics && test ! -f src/main/java/com/graphnexus/api/graph/dto/ExtractionResultVO.java && echo "graph dto 子包化成功"</verify>
  <done>dto/{graph,fusion,metrics} 三个子包各含对应 DTO；dto/ 根下无残留 .java 文件</done>
  <depends_on>T04, T05, T06, T07</depends_on>
</task>

<task id="T11" parallel="true" status="pending">
  <name>L1 api/query/dto/ → dto/{chat,prompt,conversation} 子包化</name>
  <read_files>
    src/main/java/com/graphnexus/api/query/dto/*.java
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/query/dto/QueryAskRequest.java
    src/main/java/com/graphnexus/api/query/dto/QueryAskResponse.java
    src/main/java/com/graphnexus/api/query/dto/QueryAsyncResponse.java
    src/main/java/com/graphnexus/api/query/dto/QueryChatRequest.java
    src/main/java/com/graphnexus/api/query/dto/QueryResultResponse.java
    src/main/java/com/graphnexus/api/query/dto/chat/QueryAskRequest.java
    src/main/java/com/graphnexus/api/query/dto/chat/QueryAskResponse.java
    src/main/java/com/graphnexus/api/query/dto/chat/QueryAsyncResponse.java
    src/main/java/com/graphnexus/api/query/dto/chat/QueryChatRequest.java
    src/main/java/com/graphnexus/api/query/dto/chat/QueryResultResponse.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.5 映射表，将 api/query/dto/ 下 5 个类拆入子包：

    - dto/chat/：QueryAskRequest, QueryAskResponse, QueryAsyncResponse, QueryChatRequest, QueryResultResponse（全部现有 DTO 均为 chat 相关）
    - dto/prompt/：预留空骨架，当前无文件
    - dto/conversation/：预留空骨架，当前无文件

    每个文件：git mv → 更新 package 声明。
    注意 QueryController 引用了这些 DTO，需更新 controller 的 import 语句。
  </action>
  <verify>test -d src/main/java/com/graphnexus/api/query/dto/chat && test -d src/main/java/com/graphnexus/api/query/dto/prompt && test -d src/main/java/com/graphnexus/api/query/dto/conversation && test ! -f src/main/java/com/graphnexus/api/query/dto/QueryAskRequest.java && echo "query dto 子包化成功"</verify>
  <done>dto/{chat,prompt,conversation} 三个子包各含对应 DTO；dto/ 根下无残留 .java 文件</done>
  <depends_on>T04, T05, T06, T07</depends_on>
</task>

<task id="T12" parallel="true" status="pending">
  <name>L1 api/basic/ → api/system/ 重命名</name>
  <read_files>
    src/main/java/com/graphnexus/api/basic/**/*.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/basic/**/*.java
    src/main/java/com/graphnexus/api/system/**/*.java
  </write_files>
  <action>
    将 api/basic/ 目录重命名为 api/system/：
    1. git mv api/basic api/system
    2. 更新所有文件内的 package 声明：..api.basic → ..api.system
    3. 更新所有 import 语句中的旧包名
    
    注意：basic/ 当前可能仅有空目录，若是则只需目录重命名。
  </action>
  <verify>test -d src/main/java/com/graphnexus/api/system && test ! -d src/main/java/com/graphnexus/api/basic && echo "system 重命名成功"</verify>
  <done>api/system/ 存在，api/basic/ 不存在</done>
  <depends_on>T04, T05, T06, T07</depends_on>
</task>

<task id="T13" parallel="false" status="pending">
  <name>infrastructure/mineru/ → application/file/parse/parser/mineru/</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/mineru/**/*.java
    src/main/java/com/graphnexus/application/file/parse/parser/
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUClient.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUApiClient.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUV1Client.java
    src/main/java/com/graphnexus/infrastructure/mineru/client/MinerUV4Client.java
    src/main/java/com/graphnexus/infrastructure/mineru/config/MinerUProperties.java
    src/main/java/com/graphnexus/application/file/parse/parser/mineru/client/MinerUClient.java
    src/main/java/com/graphnexus/application/file/parse/parser/mineru/client/MinerUApiClient.java
    src/main/java/com/graphnexus/application/file/parse/parser/mineru/client/MinerUV1Client.java
    src/main/java/com/graphnexus/application/file/parse/parser/mineru/client/MinerUV4Client.java
    src/main/java/com/graphnexus/application/file/parse/parser/mineru/config/MinerUProperties.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.9「MinerU 迁入」段，将 infrastructure/mineru/ 整体迁入 L2：

    1. 在 application/file/parse/parser/ 下创建 mineru/ 子目录
    2. git mv infrastructure/mineru/client/*.java → application/file/parse/parser/mineru/client/
    3. git mv infrastructure/mineru/config/MinerUProperties.java → application/file/parse/parser/mineru/config/
    4. 删除 infrastructure/mineru/ 空目录
    
    每个文件：
    - package 声明：..infrastructure.mineru.xxx → ..application.file.parse.parser.mineru.xxx
    - import 更新：自引用 + 对外引用（MinerUProperties 可能被其他模块引用，需全局更新）
    
    注意：MinerUProperties 在 yml 中可能有 `mineru.*` 配置绑定（@ConfigurationProperties），
    其全限定类名变更后，Spring 通过注解扫描自动发现，无需改 yml。
  </action>
  <verify>test -d src/main/java/com/graphnexus/application/file/parse/parser/mineru/client && test -f src/main/java/com/graphnexus/application/file/parse/parser/mineru/config/MinerUProperties.java && test ! -d src/main/java/com/graphnexus/infrastructure/mineru && echo "MinerU 迁移成功"</verify>
  <done>infrastructure/mineru/ 不存在；5 个文件位于 application/file/parse/parser/mineru/ 下</done>
  <depends_on>T04</depends_on>
</task>

<task id="T14" parallel="false" status="pending">
  <name>测试文件移动对齐 main 侧新结构</name>
  <read_files>
    src/test/java/com/graphnexus/**/*.java
    .specs/package-restructure/DESIGN.md
  </read_files>
  <write_files>
    src/test/java/com/graphnexus/application/document/**/*.java
    src/test/java/com/graphnexus/application/graph/extraction/**/*.java
    src/test/java/com/graphnexus/application/graph/service/GraphServiceTest.java
    src/test/java/com/graphnexus/application/query/config/QueryPropertiesTest.java
    src/test/java/com/graphnexus/application/query/prompt/PromptTemplateServiceTest.java
    src/test/java/com/graphnexus/infrastructure/mineru/client/MinerUClientTest.java
    src/test/java/com/graphnexus/application/file/parse/parser/CsvGradeParserTest.java
    src/test/java/com/graphnexus/application/file/parse/parser/MinerUDocumentParserTest.java
    src/test/java/com/graphnexus/application/file/parse/parser/PdfBoxDocumentParserTest.java
    src/test/java/com/graphnexus/application/file/core/service/DocumentProcessingIntegrationTest.java
    src/test/java/com/graphnexus/application/file/core/service/DocumentServiceTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/ExtractionJsonParserTest.java
    src/test/java/com/graphnexus/application/graph/construction/service/ExtractionValidatorTest.java
    src/test/java/com/graphnexus/application/graph/core/service/GraphServiceTest.java
    src/test/java/com/graphnexus/application/query/chat/config/QueryPropertiesTest.java
    src/test/java/com/graphnexus/application/query/prompt/service/PromptTemplateServiceTest.java
    src/test/java/com/graphnexus/application/file/parse/parser/mineru/client/MinerUClientTest.java
  </write_files>
  <action>
    按 DESIGN.md §2.2.13 测试文件映射表，将 12 个测试文件移动到与 main 侧对齐的新路径。

    **需移动的测试文件**（原路径 → 新路径）：
    - application/document/parser/*Test.java → application/file/parse/parser/*Test.java（3 个）
    - application/document/service/*Test.java → application/file/core/service/*Test.java（2 个）
    - application/graph/extraction/*Test.java → application/graph/construction/service/*Test.java（2 个）
    - application/graph/service/GraphServiceTest.java → application/graph/core/service/GraphServiceTest.java
    - application/query/config/QueryPropertiesTest.java → application/query/chat/config/QueryPropertiesTest.java
    - application/query/prompt/PromptTemplateServiceTest.java → application/query/prompt/service/PromptTemplateServiceTest.java
    - infrastructure/mineru/client/MinerUClientTest.java → application/file/parse/parser/mineru/client/MinerUClientTest.java

    **不需移动的测试**（仅 package/import 已在 Wave 2 中更新）：
    - api/graph/controller/*Test.java（3 个）
    - api/query/controller/*Test.java（1 个）
    - application/graph/fusion/strategy/*Test.java（2 个）
    - application/graph/metrics/**Test.java（3 个）
    - architecture/LayeredArchitectureTest.java
    - common/util/Md5UtilsTest.java
    - infrastructure/mysql/document/DocumentStatusTest.java
    - infrastructure/neo4j/gds/GdsAdapterTest.java
    - infrastructure/neo4j/node/GraphNodeAbstractionTest.java

    每个文件：git mv → 更新 package 声明 → 更新 import（被测类的新路径）。
  </action>
  <verify>mvn test-compile -q 2>&1 | tail -5</verify>
  <done>mvn test-compile 零错误通过；test 侧包路径与 main 侧完全镜像</done>
  <depends_on>T08, T09, T10, T11, T12, T13</depends_on>
</task>

<task id="T15" parallel="false" status="pending">
  <name>全量验证：编译 + 全部测试通过 + 结构巡检</name>
  <read_files>
  </read_files>
  <write_files>
  </write_files>
  <action>
    运行完整验证链条：

    1. `mvn clean compile` — 确认所有 package/import 正确，无编译错误
    2. `mvn test` — 运行全部单元测试 + 集成测试 + ArchUnit 分层测试
    3. 结构巡检：
       - `grep -r "com\.GraphNexus" src/` 确认零结果
       - `find src/ -name "package-info.java"` 确认零结果
       - `test ! -d src/main/java/com/graphnexus/api/gateway` 确认 gateway 已删除
       - `test ! -d src/main/java/com/graphnexus/api/document` 确认 document 已重命名
       - `test ! -d src/main/java/com/graphnexus/api/basic` 确认 basic 已重命名
       - `test ! -d src/main/java/com/graphnexus/application/document` 确认 document 已重命名
       - `test ! -d src/main/java/com/graphnexus/application/basic` 确认 basic 已重命名
       - `test ! -d src/main/java/com/graphnexus/application/graph/extraction` 确认 extraction 已重命名
       - `test ! -d src/main/java/com/graphnexus/infrastructure/mineru` 确认 MinerU 已迁移

    预期：
    - compile: BUILD SUCCESS
    - test: 全部通过，0 failures，0 errors
    - 结构巡检：全部通过
  </action>
  <verify>mvn clean compile -q 2>&1 && echo "=== compile OK ===" && mvn test 2>&1 | tail -30</verify>
  <done>mvn compile 零错误 + mvn test 全部通过 + 结构巡检全部通过；包结构规范化重构完成</done>
  <depends_on>T14</depends_on>
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

## 追加任务（DEV 阶段扩展，2026-06-18）

### 追加波次 8: DocumentParser 合并 + 单向依赖修复

```xml
<task id="T16" parallel="true" status="done">
  <name>DocumentParser extends FileParser 继承合并</name>
  <depends_on>T15</depends_on>
  <action>
    DocumentParser 改为 extends FileParser，添加默认 parse(FileParseRequest) 桥接方法。
    PdfBoxDocumentParser + MinerUDocumentParser 实现 supportedType()/supportedExtensions()。
    CsvGradeParser 保持直接实现 FileParser。
  </action>
  <verify>mvn compile -q</verify>
  <done>编译通过；FileParser 继承体系建立</done>
</task>

<task id="T17" parallel="true" status="done">
  <name>修复 analysis↔query 循环依赖</name>
  <depends_on>T15</depends_on>
  <action>
    PruningRequest.intent 从 QueryIntent 枚举改为 String。
    StudentDiagnosisStrategy 用字符串比较代替枚举比较。
    QueryServiceImpl 传 intent.name() 而非枚举值。
  </action>
  <verify>grep -r "import com\.graphnexus\.application\.query\." src/main/java/.../analysis/ | wc -l | xargs test 0 -eq</verify>
  <done>analysis→query 依赖为零；单向依赖修复</done>
</task>

<task id="T18" parallel="true" status="done">
  <name>修复 file→graph 依赖：GradeUploadedEvent 解耦</name>
  <depends_on>T15</depends_on>
  <action>
    新增 GradeUploadedEvent（file/upload/event/）。
    GradeServiceImpl 移除 FusionService/GraphChangedEvent 依赖，改为发布 GradeUploadedEvent。
    新增 GradeUploadedEventListener（graph/fusion/event/）监听事件触发融合。
  </action>
  <verify>grep -r "import com\.graphnexus\.application\.graph\." src/main/java/.../file/ | wc -l | xargs test 0 -eq</verify>
  <done>file→graph 依赖为零；事件驱动解耦完成</done>
</task>
```

### 追加波次 9: 系统级 Document → File 重命名

```xml
<task id="T19" parallel="false" status="done">
  <name>全系统 Document* 类名 → File* 重命名</name>
  <depends_on>T18</depends_on>
  <action>
    L1: DocumentController→FileController, DocumentVO→FileVO, UpdateDocumentRequest→UpdateFileRequest
    L2: DocumentService→FileService, DocumentBO→FileBO, UpdateDocumentBO→UpdateFileBO
    L3: DocumentDO→FileDO, DocumentRepository→FileRepository, DocumentStatus→FileStatus, DocumentNode→FileNode
    API: /api/v1/document → /api/v1/file/document
    SQL: CREATE TABLE document → CREATE TABLE file
    包: mysql/document/ → mysql/file/
  </action>
  <verify>grep -rn "\bDocumentVO\b\|\bDocumentBO\b\|\bDocumentDO\b\|\bDocumentService\b\|\bDocumentRepository\b" src/ | grep -v "MinerUDocument\|PdfBoxDocument\|DocumentParser" | wc -l | xargs test 0 -eq</verify>
  <done>36 files changed; 全系统 Document→File 重命名完成</done>
</task>
```

### 追加波次 10: MySQL entity/repository 子包拆分

```xml
<task id="T20" parallel="false" status="done">
  <name>MySQL 业务模块拆分为 entity/ + repository/ 子包</name>
  <depends_on>T19</depends_on>
  <action>
    infrastructure/mysql/file/ → entity/(FileDO+FileStatus+ExamRecordDO) + repository/(FileRepository+ExamRecordRepository)
    infrastructure/mysql/fusion/ → entity/(FusionLogDO) + repository/(FusionLogRepository)
    infrastructure/mysql/query/ → entity/(QueryTaskDO+QueryTaskStatus) + repository/(QueryTaskRepository)
    包名使用 entity 而非 do（do 是 Java 关键字）。
  </action>
  <verify>find src/main/java/com/graphnexus/infrastructure/mysql -name "*.java" | sort | grep -E "entity|repository"</verify>
  <done>11 files changed; 三个模块均含 entity/ + repository/ 子包</done>
</task>
```

### 追加波次 11: 消除分层架构违规（2026-06-18）

```xml
<task id="T21" parallel="false" status="done">
  <name>消除 86 处 LayeredArchitectureTest 分层违规</name>
  <depends_on>T20</depends_on>
  <action>
    L1→L3 修复：
    - 创建 GraphNodeData/GraphEdgeData 记录 + GraphDataConverter (application/graph/core/model/)
    - PrunedSubgraph/GraphSubgraphBO 改为持有 L2 类型
    - GraphSubgraphVO.from() 使用 GraphNodeData/GraphEdgeData
    - AnalysisController 使用 record 字段而非 L3 getter
    - StudentDiagnosisStrategy buildResult 产出 GraphNodeData/GraphEdgeData
    - QueryServiceImpl 适配新类型

    L3→L2 修复：
    - LlmGateway 移至 common/
    - MetricsQuery/MetricResultBO 移至 common/model/
    - MetricsProperties 移至 infrastructure/neo4j/gds/config/
  </action>
  <verify>mvn test -Dtest="LayeredArchitectureTest" 2>&1 | grep "BUILD SUCCESS"</verify>
  <done>ArchUnit 86 违例 → 0；BUILD SUCCESS；分层架构约束全部通过</done>
</task>
```