# TASK: API 接口文档 — SpringDoc OpenAPI 3.0 自动生成

- **Change ID**: `api-documentation`
- **关联**: `@.specs/api-documentation/CHANGE.md`

---

## 波次划分

```
Wave 1:            T01                    SpringDoc 全局配置 + 通用 Schema
Wave 2 (parallel): T02[P] ~ T07[P]        6 个 Controller + 各自 DTO/VO 注解补齐（互不冲突）
Wave 3:            T08                    集成验证（Swagger UI + openapi.json）
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。T02–T07 操作文件完全不重叠。

---

## 任务清单

```xml
<task id="T01" parallel="false" status="done">
  <name>SpringDoc 全局配置 + 通用响应 Schema 注解</name>
  <read_files>
    src/main/resources/application-dev.yml
    src/main/java/com/graphnexus/common/ApiResponse.java
    src/main/java/com/graphnexus/common/PageResult.java
    src/main/java/com/graphnexus/common/exception/ErrorResponse.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
    pom.xml
  </read_files>
  <write_files>
    src/main/resources/application-dev.yml
    src/main/java/com/graphnexus/common/ApiResponse.java
    src/main/java/com/graphnexus/common/exception/ErrorResponse.java
  </write_files>
  <action>
    1. application-dev.yml 新增 springdoc 配置段：
       - springdoc.api-docs.path=/v3/api-docs
       - springdoc.swagger-ui.path=/swagger-ui.html
       - springdoc.swagger-ui.tagsSorter=alpha
       - springdoc.swagger-ui.operationsSorter=alpha
       - API 元信息通过 @OpenAPIDefinition 注解在启动类或独立配置类中声明（标题"GraphNexus API"、版本"1.0.0"、描述含模块概览和错误码体系说明）
    2. ApiResponse record 添加 @Schema 注解（description="统一API响应体"，各字段说明：code=HTTP状态码、message=提示信息、data=业务数据、traceId=追踪ID、timestamp=时间戳毫秒）
    3. ErrorResponse record 添加 @Schema 注解（description="统一错误响应体"，各字段说明）
    4. 不需要为 PageResult 单独加 @Schema——它作为泛型嵌套在 ApiResponse 的 data 中，SpringDoc 2.6 自动解析
    5. 创建一个 OpenApiConfig @Configuration 类（api/gateway/config/ 包下），定义 OpenAPI bean 含标题/描述/版本/错误码表概要，并声明 GroupedOpenApi 覆盖所有业务 Controller 包路径
  </action>
  <verify>./mvnw compile -q 2>&1 | tail -5</verify>
  <done>编译通过；application-dev.yml 含 springdoc 配置段；OpenApiConfig bean 存在且正确配置</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>DocumentController + 6 个 DTO/VO OpenAPI 注解补齐</name>
  <read_files>
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/java/com/graphnexus/api/document/dto/DocumentVO.java
    src/main/java/com/graphnexus/api/document/dto/ParseResultVO.java
    src/main/java/com/graphnexus/api/document/dto/UpdateDocumentRequest.java
    src/main/java/com/graphnexus/api/document/dto/GradeUploadResultVO.java
    src/main/java/com/graphnexus/api/document/dto/GradeRecordVO.java
    src/main/java/com/graphnexus/api/document/dto/DeleteResultVO.java
    src/main/java/com/graphnexus/common/ApiResponse.java
    src/main/java/com/graphnexus/common/PageResult.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/document/controller/DocumentController.java
    src/main/java/com/graphnexus/api/document/dto/DocumentVO.java
    src/main/java/com/graphnexus/api/document/dto/ParseResultVO.java
    src/main/java/com/graphnexus/api/document/dto/UpdateDocumentRequest.java
    src/main/java/com/graphnexus/api/document/dto/GradeUploadResultVO.java
    src/main/java/com/graphnexus/api/document/dto/GradeRecordVO.java
    src/main/java/com/graphnexus/api/document/dto/DeleteResultVO.java
  </write_files>
  <action>
    Controller（@Tag(name="文档处理", description="PDF教辅上传解析 + CSV成绩导入管理")）：
    - POST /upload：@Operation(summary="上传文件", description="PDF/CSV统一入口。PDF→DocumentVO，CSV→GradeUploadResultVO")；@ApiResponse 声明 200 OK（两种 schema：DocumentVO + GradeUploadResultVO，用 @Content 显式指定）、400（A0002/A0004/A0011/A0012）、409（A0007 内容重复）、500（B0001）；@RequestParam file 加 @Parameter(description="上传文件 PDF/CSV"，required=true)
    - POST /{id}/process：@Operation(summary="触发文档解析")；@ApiResponse 200/400(A0008/A0009)/404(A0006)/500
    - GET /：@Operation(summary="分页查询文档列表")；@Parameter pageNum/pageSize 加 description；@ApiResponse 200(Generic→PageResult<DocumentVO>)/500
    - GET /{id}：@Operation(summary="查询文档详情")；@ApiResponse 200/404(A0006)/500
    - PUT /{id}：@Operation(summary="更新文档名称")；@ApiResponse 200/400(A0002 校验失败)/404(A0006)/500
    - DELETE /{id}：@Operation(summary="删除文档", description="级联删除MinIO文件+Neo4j子图")；@ApiResponse 200/404(A0006)/500
    - GET /grade/exam/{examNo}：@Operation(summary="按考试编号查成绩")；@ApiResponse 200/404(A0014)/500
    - DELETE /grade/exam/{examNo}：@Operation(summary="级联删除成绩", description="删MySQL记录+MinIO文件+Neo4j边")；@ApiResponse 200/404(A0015)/500

    DTO/VO（@Schema description + example + requiredMode）：
    - UpdateDocumentRequest.name：@Schema(description="新文档名称", example="初三数学二次函数讲义", requiredMode=REQUIRED)
    - DocumentVO：9个字段每个加 @Schema(description="...", example="...")
    - ParseResultVO：4个字段，textContent example="## 第1章..."
    - GradeUploadResultVO：8个字段，examNo example="E20200041"
    - GradeRecordVO：9个字段，scoreDetails description="JSON格式各题成绩明细"
    - DeleteResultVO：4个字段
  </action>
  <verify>./mvnw compile -q 2>&1 | tail -5</verify>
  <done>编译通过；DocumentController 含 @Tag/@Operation/@ApiResponse/@Parameter；6 个 DTO/VO 含 @Schema</done>
  <depends_on>T01</depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>GraphController + ExtractionResultVO / GraphSubgraphVO OpenAPI 注解补齐</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/api/graph/dto/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/GraphSubgraphVO.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/GraphNode.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/GraphEdge.java
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/api/graph/dto/ExtractionResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/GraphSubgraphVO.java
  </write_files>
  <action>
    Controller（@Tag(name="知识图谱", description="文档知识图谱抽取与子图查询")）：
    - POST /extract/{documentId}：@Operation(summary="触发知识图谱抽取", description="对已解析完成的文档执行LLM抽取，生成Entity/KP/Category节点及关系边。重复抽取全量覆盖旧数据")；@PathVariable documentId 加 @Parameter(description="文档ID", required=true, example="1")；@ApiResponse 200(ExtractionResultVO)/400(A0008/A0009)/404(A0006)/500
    - GET /document/{documentId}：@Operation(summary="查询文档子图", description="返回指定文档关联的所有Entity/KP/Category节点及关系边")；@ApiResponse 200(GraphSubgraphVO)/404(A0006)/500

    DTO/VO：
    - ExtractionResultVO：@Schema 5个字段加 description+example（documentId/entityCount/knowledgePointCount/categoryCount/edgeCount）
    - GraphSubgraphVO：@Schema 含嵌套 GraphNodeVO（4字段：id/nodeType/documentId/createdAt）+ GraphEdgeVO（4字段：sourceNodeId/targetNodeId/edgeType/createdAt）
  </action>
  <verify>./mvnw compile -q 2>&1 | tail -5</verify>
  <done>编译通过；GraphController 含 @Tag/@Operation/@ApiResponse/@Parameter；ExtractionResultVO 和 GraphSubgraphVO 含 @Schema</done>
  <depends_on>T01</depends_on>
</task>

<task id="T04" parallel="true" status="done">
  <name>FusionController + 3 个 VO OpenAPI 注解补齐</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/FusionController.java
    src/main/java/com/graphnexus/api/graph/dto/FusionExecuteVO.java
    src/main/java/com/graphnexus/api/graph/dto/FusionStatusVO.java
    src/main/java/com/graphnexus/api/graph/dto/FusionRollbackVO.java
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/FusionController.java
    src/main/java/com/graphnexus/api/graph/dto/FusionExecuteVO.java
    src/main/java/com/graphnexus/api/graph/dto/FusionStatusVO.java
    src/main/java/com/graphnexus/api/graph/dto/FusionRollbackVO.java
  </write_files>
  <action>
    Controller（@Tag(name="宽图谱融合", description="知识图谱KP融合与MASTERS掌握度聚合")）：
    - POST /execute：@Operation(summary="手动全量融合", description="对全图所有同名/相似KP执行融合合并，重算MASTERS边权重。返回融合日志ID")；@ApiResponse 200/409(A0017 融合进行中)/500
    - GET /status：@Operation(summary="查询融合状态", description="返回最近一次融合操作的完整状态（含融合明细JSON + MASTERS变更快照）")；@ApiResponse 200(可null)/500
    - POST /rollback/{fusionLogId}：@Operation(summary="回滚融合", description="按融合日志记录逆向恢复Neo4j图状态。不支持跨多次融合的部分回滚")；@PathVariable fusionLogId 加 @Parameter(description="融合日志ID", required=true, example="1")；@ApiResponse 200/404(A0016)/409(A0018 图已变更)/500

    VO：
    - FusionExecuteVO：@Schema 3个字段（fusionLogId/mergedKpGroupCount/mastersEdgeCount）
    - FusionStatusVO：@Schema 8个字段，fusionDetailJson/mastersSnapshotJson description="JSON格式明细/快照"
    - FusionRollbackVO：@Schema 3个字段（fusionLogId/restoredKpCount/restoredEdgeCount）
  </action>
  <verify>./mvnw compile -q 2>&1 | tail -5</verify>
  <done>编译通过；FusionController 含 @Tag/@Operation/@ApiResponse/@Parameter；3 个 VO 含 @Schema</done>
  <depends_on>T01</depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>MetricsController + MetricResultVO / MetricsQueryRequest OpenAPI 注解补齐</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
    src/main/java/com/graphnexus/api/graph/dto/MetricResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
    src/main/java/com/graphnexus/api/graph/dto/MetricResultVO.java
  </write_files>
  <action>
    Controller（@Tag(name="图指标", description="Neo4j GDS图算法——PageRank与度中心性查询")）：
    - GET /pagerank：@Operation(summary="查询PageRank", description="返回图中各节点的PageRank值。可选按节点/边类型过滤投影范围。GDS transient模式，结果Caffeine缓存5分钟")；@RequestParam nodeTypes/edgeTypes 加 @Parameter(description="节点/边类型标签列表 逗号分隔，空=全类型", required=false, example="KnowledgePoint,Student")；@ApiResponse 200(List<MetricResultVO>)/500
    - GET /degree：@Operation(summary="查询度中心性", description="返回各节点的入度和出度。可选过滤投影范围")；@Parameter 同上；@ApiResponse 200(List<MetricResultVO>)/500

    VO：
    - MetricResultVO：@Schema 4个字段（nodeId example="kp_123"、nodeType example="KnowledgePoint"、metricName example="PageRank"、metricValue example="0.023"）
    - MetricsQueryRequest 不加 @Schema 注解（仅用于 Controller 内部参数绑定，不暴露为 OpenAPI schema）
  </action>
  <verify>./mvnw compile -q 2>&1 | tail -5</verify>
  <done>编译通过；MetricsController 含 @Tag/@Operation/@ApiResponse/@Parameter；MetricResultVO 含 @Schema</done>
  <depends_on>T01</depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>QueryController + 5 个 DTO OpenAPI 注解补齐</name>
  <read_files>
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
    src/main/java/com/graphnexus/api/query/dto/QueryAskRequest.java
    src/main/java/com/graphnexus/api/query/dto/QueryChatRequest.java
    src/main/java/com/graphnexus/api/query/dto/QueryAskResponse.java
    src/main/java/com/graphnexus/api/query/dto/QueryAsyncResponse.java
    src/main/java/com/graphnexus/api/query/dto/QueryResultResponse.java
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/query/controller/QueryController.java
    src/main/java/com/graphnexus/api/query/dto/QueryAskRequest.java
    src/main/java/com/graphnexus/api/query/dto/QueryChatRequest.java
    src/main/java/com/graphnexus/api/query/dto/QueryAskResponse.java
    src/main/java/com/graphnexus/api/query/dto/QueryAsyncResponse.java
    src/main/java/com/graphnexus/api/query/dto/QueryResultResponse.java
  </write_files>
  <action>
    Controller（@Tag(name="智能问答", description="自然语言问答——图剪枝驱动LLM分析诊断")）：
    - POST /ask：@Operation(summary="同步问答", description="提交问题后同步等待LLM分析结果（≤30s）。需显式指定studentName/studentNo/subject")；@RequestBody 描述；@ApiResponse 200/400(A0002/A0019)/409(A0020 同名student)/500
    - POST /ask-async：@Operation(summary="异步问答", description="提交复杂问题，立即返回taskId，轮询GET /result/{taskId}获取结果")；@ApiResponse 200/400/409/500
    - POST /chat：@Operation(summary="智能对话", description="仅需提供自然语言问题，系统自动提取studentName/studentNo/subject等实体（LLM-first + 正则fallback），然后执行诊断分析")；@ApiResponse 200/400(A0019)/500
    - GET /result/{taskId}：@Operation(summary="查询异步结果", description="轮询异步问答任务的执行状态和结果。status=PENDING/PROCESSING/COMPLETED/FAILED")；@PathVariable taskId 加 @Parameter(description="任务ID UUID", required=true, example="uuid")；@ApiResponse 200/404(A0021)/500

    DTO（@Schema description + example + requiredMode）：
    - QueryAskRequest：question example="分析张三的数学薄弱点"、studentName、studentNo、subject required
    - QueryChatRequest：question example="分析一下张三最近数学怎么样"、仅1字段
    - QueryAskResponse：taskId/intent/answer(status+answer Markdown)/tokenUsage(嵌套PrunedNodes/PrunedEdges/EstimatedTokens)
    - QueryAsyncResponse：taskId/status(固定"PENDING")/createdAt
    - QueryResultResponse：9字段，含可空字段 errorMessage（FAILED时有值）、answer（COMPLETED时有值）
  </action>
  <verify>./mvnw compile -q 2>&1 | tail -5</verify>
  <done>编译通过；QueryController 含 @Tag/@Operation/@ApiResponse/@Parameter；5 个 DTO 含 @Schema</done>
  <depends_on>T01</depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>AnalysisController + SubgraphResponse OpenAPI 注解补齐</name>
  <read_files>
    src/main/java/com/graphnexus/api/analysis/controller/AnalysisController.java
    src/main/java/com/graphnexus/api/analysis/dto/SubgraphResponse.java
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/analysis/controller/AnalysisController.java
    src/main/java/com/graphnexus/api/analysis/dto/SubgraphResponse.java
  </write_files>
  <action>
    Controller（@Tag(name="图分析", description="剪枝子图可视化数据查询")）：
    - GET /subgraph/{taskId}：@Operation(summary="查询剪枝子图", description="返回指定问答任务剪枝出的子图节点/边数据+剪枝元信息，供前端图可视化渲染。不含LLM分析结论")；@PathVariable taskId 加 @Parameter(description="问答任务ID UUID", required=true, example="uuid")；@ApiResponse 200(SubgraphResponse)/404(A0021)/500

    VO：
    - SubgraphResponse：@Schema，含嵌套 NodeVO（id/nodeType/properties=Map）、EdgeVO（sourceNodeId/targetNodeId/edgeType/weight）、PruningMetaVO（strategy/mastersAvailable/weakThreshold/maxHops/totalNodes/totalEdges/truncated/truncatedNodeNames）
    - 嵌套 record 不加独立 @Schema，SpringDoc 自动解析
  </action>
  <verify>./mvnw compile -q 2>&1 | tail -5</verify>
  <done>编译通过；AnalysisController 含 @Tag/@Operation/@ApiResponse/@Parameter；SubgraphResponse 含 @Schema</done>
  <depends_on>T01</depends_on>
</task>

<task id="T08" parallel="false" status="done">
  <name>集成验证：启动应用 + Swagger UI 可访问 + openapi.json 完整性检查</name>
  <read_files>
    src/main/resources/application-dev.yml
  </read_files>
  <write_files>
    <!-- 本任务不写代码，仅验证 -->
  </write_files>
  <action>
    1. 启动应用（dev profile）：./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
    2. 等待启动完成后验证：
       - curl -s http://localhost:8080/swagger-ui.html | head -20（确认 Swagger UI 页面可加载）
       - curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool | head -50（确认 openapi.json 可获取）
       - 检查 openapi.json 的 paths 段包含所有 6 个 Controller 的分组（/api/v1/document, /api/v1/graph, /api/v1/graph/fusion, /api/v1/graph/metrics, /api/v1/query, /api/v1/analysis）
       - 确认 /api/v1/llm 不在 paths 中
       - 检查 components/schemas 包含所有 VO/DTO
    3. 关闭应用
  </action>
  <verify>curl -s http://localhost:8080/v3/api-docs | python3 -c "import json,sys; d=json.load(sys.stdin); paths=list(d.get('paths',{}).keys()); assert len(paths)>0, 'No paths'; print(f'OK: {len(paths)} endpoints, {len(d.get(\"components\",{}).get(\"schemas\",{}))} schemas')"</verify>
  <done>Swagger UI 可访问；openapi.json 含 20 个业务端点 + 全部 schema；LlmController 路径不在文档中</done>
  <depends_on>T02, T03, T04, T05, T06, T07</depends_on>
</task>
```

---

## 状态字段说明

- `status="done"` — 未开始
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