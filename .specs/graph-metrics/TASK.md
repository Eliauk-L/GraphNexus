# TASK: 图谱节点指标度量（PageRank + 度中心性）

- **Change ID**: graph-metrics
- **关联**: `@.specs/graph-metrics/REQUIREMENT.md`、`@.specs/graph-metrics/DESIGN.md`

---

## 波次划分

```
Wave 1 (parallel): T01[P], T02[P]
Wave 2 (parallel): T03[P], T06[P]          (depends on T01)
Wave 3:            T04                      (depends on T01, T02, T03)
Wave 4 (parallel): T05[P], T07[P]          (T05 depends on T04; T07 depends on T04, T06)
Wave 5:            T08                      (depends on T05)
```

> 同 wave = 可并行；跨 wave = 必须顺序执行。

---

## 任务清单

```xml
<task id="T01" parallel="true" status="done">
  <name>创建 MetricsQuery 和 MetricResultBO 领域模型</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/model/*
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/metrics/model/MetricsQuery.java
    src/main/java/com/graphnexus/application/graph/metrics/model/MetricResultBO.java
    src/main/java/com/graphnexus/application/graph/metrics/model/package-info.java
  </write_files>
  <action>
    1. MetricResultBO：不可变 record（nodeId: String, nodeType: String, metricName: String, metricValue: Double），供 GdsAdapter → MetricsService → Controller 全链路传递。构造时 `nodeType` 取 Neo4j labels() 的第一个 label。
    2. MetricsQuery：不可变 record（nodeTypes: Set&lt;String&gt;, edgeTypes: Set&lt;String&gt;, metricName: String），含方法：
       - `toCacheKey(): String` — 拼接 `metricName + nodeTypes排序 + edgeTypes排序` 后 MD5 hash
       - `static builder()` 模式（用静态工厂 + 链式调用，风格与既有 BO 一致）
    3. 数据方向：L1 Query 参数 → MetricsQuery → GdsAdapter 使用 nodeTypes/edgeTypes 构建投影 Cypher → GDS 结果映射为 List&lt;MetricResultBO&gt;
  </action>
  <verify>mvn compile -pl . 确认两个 record 编译通过，import 无异常</verify>
  <done>MetricsQuery 和 MetricResultBO 编译通过，toCacheKey() 对相同参数的两次调用返回相同 MD5 值（AC-1/AC-7 数据模型基础）</done>
  <depends_on></depends_on>
</task>

<task id="T02" parallel="true" status="done">
  <name>创建 MetricsProperties 配置类</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/fusion/config/FusionProperties.java
    src/main/resources/application-dev.yml
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/metrics/config/MetricsProperties.java
    src/main/java/com/graphnexus/application/graph/metrics/config/package-info.java
  </write_files>
  <action>
    1. `@ConfigurationProperties(prefix = "graph.metrics")` 类，字段：
       - `Cache cache`（record：ttlMinutes=5, maxSize=50）
       - `PageRank pageRank`（record：maxIterations=20, dampingFactor=0.85）
    2. 在 `application-dev.yml` 中追加默认值（加注释 `# graph-metrics 配置`）：
       ```yaml
       graph:
         metrics:
           cache:
             ttl-minutes: 5
             max-size: 50
           page-rank:
             max-iterations: 20
             damping-factor: 0.85
       ```
    3. 使用 Java 17 record 定义内部配置（cache/pageRank），风格与 FusionProperties 一致
    4. 在 Spring Boot 主类或配置类上已有 `@EnableConfigurationProperties` 或通过 `@ConfigurationPropertiesScan`，无需新增启用注解（SDN 7.x 已启用）
  </action>
  <verify>mvn test -Dtest="*MetricsProperties*" -pl . 确认 YAML 值绑定到 MetricsProperties 实例正确</verify>
  <done>MetricsProperties 编译通过，测试确认 cache.ttl-minutes=5, page-rank.max-iterations=20 从 YAML 正确绑定（AC-非功能性-缓存配置）</done>
  <depends_on></depends_on>
</task>

<task id="T03" parallel="true" status="done">
  <name>创建 GdsAdapter — GDS Cypher 调用适配器（L3）</name>
  <read_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/repository/GraphNodeRepository.java
    src/main/java/com/graphnexus/application/graph/metrics/model/MetricsQuery.java
    src/main/java/com/graphnexus/application/graph/metrics/model/MetricResultBO.java
    src/main/java/com/graphnexus/application/graph/metrics/config/MetricsProperties.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java
    src/main/java/com/graphnexus/infrastructure/neo4j/gds/package-info.java
  </write_files>
  <action>
    1. `@Component`，构造器注入 `Neo4jClient` + `MetricsProperties`（沿用例注入 `@RequiredArgsConstructor`）
    2. 核心方法：
       - `List&lt;MetricResultBO&gt; calculate(MetricsQuery query)`：调度到对应的算法方法
       - `calculatePageRank(MetricsQuery query)`：见 DESIGN §2.1 流程（project → stream → drop）
       - `calculateDegree(MetricsQuery query)`：`CALL gds.degree.stream(...)`，返回 inDegree/outDegree 两条 MetricResultBO（见 AC-2）
    3. 命名图管理（见 ADR-013 §1）：
       - 命名图名称：`graphName = "metrics-temp-" + UUID.randomUUID().toString().replace("-", "")`
       - try-finally 模式：finally 块 `CALL gds.graph.drop(graphName)` 确保释放
    4. 方向映射（见 DESIGN §2.3）：
       - 内置静态 Map：EdgeType → GDS orientation（NATURAL/UNDIRECTED）
       - ALIGNED_TO 和 MASTERS → `UNDIRECTED`；其余 → `NATURAL`
    5. 空图保护（R4 缓解）：
       - 投影前 COUNT 节点数，0 节点/0 边 → 直接返回 `Collections.emptyList()`，不调 GDS
    6. PageRank Cypher 返回：`nodeId, labels(gds.util.asNode(nodeId))[0] AS nodeType, score`
    7. Degree Cypher 返回：`nodeId, labels(gds.util.asNode(nodeId))[0] AS nodeType, score`（两条 record：one for inDegree, one for outDegree）
  </action>
  <verify>mvn test -Dtest="*GdsAdapter*" -pl . 确认单元测试覆盖 PageRank + degree 两种算法的 Cypher 构建逻辑（Mock Neo4jClient 验证生成的 Cypher 字符串）</verify>
  <done>GdsAdapter 编译通过，测试覆盖 PageRank/degree 两种算法 + 空图保护 + 命名图 drop 逻辑（AC-1/AC-2/AC-5 的 L3 层基础）</done>
  <depends_on>T01</depends_on>
</task>

<task id="T04" status="done">
  <name>创建 MetricsService 接口与实现（L2）+ Caffeine 缓存</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/metrics/model/MetricsQuery.java
    src/main/java/com/graphnexus/application/graph/metrics/model/MetricResultBO.java
    src/main/java/com/graphnexus/application/graph/metrics/config/MetricsProperties.java
    src/main/java/com/graphnexus/infrastructure/neo4j/gds/GdsAdapter.java
    src/main/java/com/graphnexus/infrastructure/neo4j/node/NodeType.java
    src/main/java/com/graphnexus/infrastructure/neo4j/edge/EdgeType.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/metrics/service/MetricsService.java
    src/main/java/com/graphnexus/application/graph/metrics/service/impl/MetricsServiceImpl.java
    src/main/java/com/graphnexus/application/graph/metrics/service/package-info.java
    src/main/java/com/graphnexus/application/graph/metrics/service/impl/package-info.java
  </write_files>
  <action>
    1. `MetricsService` 接口（见 DESIGN §1 D4）：
       - `List&lt;MetricResultBO&gt; queryPageRank(Set&lt;String&gt; nodeTypes, Set&lt;String&gt; edgeTypes)`
       - `List&lt;MetricResultBO&gt; queryDegree(Set&lt;String&gt; nodeTypes, Set&lt;String&gt; edgeTypes)`
       - `void clearCache()`（供事件监听器调用）
    2. `MetricsServiceImpl`：
       - 构造器注入 `GdsAdapter` + `MetricsProperties`
       - 本地 `Cache&lt;String, List&lt;MetricResultBO&gt;&gt;`，用 Caffeine.newBuilder() 构建（ttlMinutes + maxSize 来自配置）
       - `queryPageRank()` 流程：① 参数校验（NodeType.fromLabel / EdgeType.fromType）→ ② 构建 MetricsQuery → ③ `cache.get(cacheKey, k -> gdsAdapter.calculate(query))`（利用 Caffeine 的 `computeIfAbsent` 语义防并发重复计算，见 R5 缓解）
       - `queryDegree()` 同上
       - `clearCache()` → `cache.invalidateAll()`
    3. 缓存 key 策略：`query.toCacheKey()`（T01 已实现），DESIGN D3
  </action>
  <verify>mvn test -Dtest="*MetricsService*" -pl . 确认缓存命中/未命中/清空逻辑 + 参数校验（非法 nodeType → BusinessException）</verify>
  <done>MetricsService 编译通过，测试覆盖 PageRank + degree 查询路径（含缓存命中 + 校验失败场景），关联 AC-1/AC-2/AC-7/AC-8</done>
  <depends_on>T01, T02, T03</depends_on>
</task>

<task id="T05" parallel="true" status="done">
  <name>创建 GraphChangedEvent + MetricsCacheInvalidator（事件机制）</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/metrics/service/MetricsService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/metrics/event/GraphChangedEvent.java
    src/main/java/com/graphnexus/application/graph/metrics/event/MetricsCacheInvalidator.java
    src/main/java/com/graphnexus/application/graph/metrics/event/package-info.java
  </write_files>
  <action>
    1. `GraphChangedEvent`：extends `ApplicationEvent`，无自定义载荷字段（仅 `source` 属性，同 DESIGN §2.2 设计）
    2. `MetricsCacheInvalidator`：`@Component`，构造器注入 `MetricsService`
       - `@EventListener` 方法 `onGraphChanged(GraphChangedEvent event)` 调用 `metricsService.clearCache()`
       - 加 `log.debug("GraphChangedEvent received, metrics cache cleared")`
    3. 此事件为项目首次 Spring 事件引入（见 DESIGN D4 + ADR-013），为后续复用打基础
  </action>
  <verify>mvn test -Dtest="*MetricsCacheInvalidator*" -pl . 确认发布 GraphChangedEvent 后 cache.clear() 被调用（Spring 集成测试或 Mock）</verify>
  <done>事件类 + 监听器编译通过，测试确认事件→清空缓存的端到端链路（AC-6 事件基础设施）</done>
  <depends_on>T04</depends_on>
</task>

<task id="T06" parallel="true" status="done">
  <name>创建 MetricResultVO 和 MetricsQueryRequest DTO（L1）</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/dto/ExtractionResultVO.java
    src/main/java/com/graphnexus/application/graph/metrics/model/MetricResultBO.java
    src/main/java/com/graphnexus/common/ApiResponse.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/dto/MetricResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
  </write_files>
  <action>
    1. `MetricResultVO`：不可变 record（nodeId, nodeType, metricName, metricValue），含 `static MetricResultVO from(MetricResultBO bo)` 工厂方法
    2. `MetricsQueryRequest`：不可变 record（nodeTypes: List&lt;String&gt; 默认空列表, edgeTypes: List&lt;String&gt; 默认空列表），用作 Controller 方法的 `@RequestParam` 参数绑定
       - 含便捷方法 `nodeTypeSet()` / `edgeTypeSet()` 转为 Set（自动 trim + toUpperCase + 空列表→空 Set）
       - 空列表 = 全图默认（Controller 调用 MetricsService 时不传 filter 或传空 Set）
    3. 风格对齐 ExtractionResultVO/GraphSubgraphVO 的 record + from() 模式
  </action>
  <verify>mvn compile -pl . 确认 VO + QueryRequest 编译通过，from() 映射正确</verify>
  <done>DTO 编译通过，MetricResultVO.from(bo) 映射正确，空列表→空 Set 逻辑正常（AC-1/AC-2 L1 数据载体）</done>
  <depends_on>T01</depends_on>
</task>

<task id="T07" parallel="true" status="done">
  <name>创建 MetricsController（L1 API 端点）</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/controller/GraphController.java
    src/main/java/com/graphnexus/api/graph/controller/FusionController.java
    src/main/java/com/graphnexus/api/graph/dto/MetricResultVO.java
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
    src/main/java/com/graphnexus/application/graph/metrics/service/MetricsService.java
    src/main/java/com/graphnexus/common/ApiResponse.java
    src/main/java/com/graphnexus/common/exception/ErrorCode.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/controller/MetricsController.java
  </write_files>
  <action>
    1. `@RestController` + `@RequestMapping("/api/v1/graph/metrics")` + `@RequiredArgsConstructor`，注入 `MetricsService`
    2. 端点 1：`GET /pagerank`
       - `@RequestParam(required = false) List&lt;String&gt; nodeTypes`
       - `@RequestParam(required = false) List&lt;String&gt; edgeTypes`
       - → `metricsService.queryPageRank(nodeTypeSet, edgeTypeSet)` → `ApiResponse.success(vos)`
    3. 端点 2：`GET /degree`
       - 相同参数结构，调用 `metricsService.queryDegree(...)`
       - 返回 `ApiResponse.success(vos)`（每个节点两条：inDegree + outDegree，见 AC-2）
    4. 节点类型参数不为空时校验：调用 `NodeType.fromLabel()` 逐个检查，无效类型 → 抛 `BusinessException(A0001, "无效的节点类型: " + type + "，有效值: " + NodeType.values())`（见 AC-8）
    5. 边类型参数同理校验：`EdgeType.fromType()`
    6. 风格对齐 GraphController（`@RequiredArgsConstructor` + `ApiResponse` 包装）
  </action>
  <verify>mvn test -Dtest="*MetricsController*" -pl . 确认 MockMvc 测试覆盖：① PageRank 200 + JSON 结构正确 ② degree 200 ③ 无效 nodeType → 400 ④ 空结果 → 200 + []（覆盖 AC-1/AC-2/AC-5/AC-8）</verify>
  <done>MetricsController 编译通过，测试覆盖 4 种场景（PageRank 正常 / degree 正常 / 参数校验 400 / 空结果 200），关联 AC-1/AC-2/AC-5/AC-8</done>
  <depends_on>T04, T06</depends_on>
</task>

<task id="T08" status="done">
  <name>在既有 Service 中添加 GraphChangedEvent 发布钩子</name>
  <read_files>
    src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/application/document/service/impl/GradeServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java
    src/main/java/com/graphnexus/application/graph/metrics/event/GraphChangedEvent.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionRollbackService.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/application/graph/service/impl/GraphServiceImpl.java
    src/main/java/com/graphnexus/application/document/service/impl/GradeServiceImpl.java
    src/main/java/com/graphnexus/application/graph/fusion/service/impl/FusionServiceImpl.java
  </write_files>
  <action>
    按 ADR-013 §4 + DESIGN §2.2 在 3 个既有 Service 中添加事件发布：

    1. **GraphServiceImpl.extract()**：在既有 `fusionService.fuseIncremental(...)` try-catch 之后、`return result` 之前加 1 行：
       `eventPublisher.publishEvent(new GraphChangedEvent(this));`
       注入 `ApplicationEventPublisher eventPublisher`（构造器参数+1）

    2. **GradeServiceImpl.uploadGradeCsv()**：在既有 `fusionService.fuseIncremental(...)` try-catch 块之后加同上 1 行

    3. **FusionServiceImpl**：在 `fuseFull()` 和 `fuseIncremental()` 两个方法的 `return` 之前各加 1 行
       - `fuseFull()` 在 `log.info("全量融合完成...")` 之后、`return result` 之前
       - `fuseIncremental()` 同理

    4. 每个文件改动范围：构造器新增 1 个 `ApplicationEventPublisher` 字段 + 末尾新增 1 行 `publishEvent` 调用
    5. 不改既有 try-catch 结构、不改变现有逻辑——event 发布在 try-catch 块外部，即使融合失败也会发布事件（cache 清空无害）
  </action>
  <verify>mvn compile -pl . 确认 GraphServiceImpl/GradeServiceImpl/FusionServiceImpl 编译通过；mvn test -Dtest="*ServiceImpl*" -pl . 确认既有测试不受影响；grep "publishEvent\|GraphChangedEvent" 确认 3 个文件各含至少 1 处事件发布</verify>
  <done>3 个既有 Service 编译通过，既有测试全部通过，确认 GraphChangedEvent 在抽取/CSV导入/全量融合/增量融合完成后均被发布（AC-6 自动重算链路）</done>
  <depends_on>T05</depends_on>
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

```xml
<task id="T-FIX-01" status="done">
  <name>修复 MetricsQueryRequest 注释：移除 "转为大写" 描述</name>
  <read_files>
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
  </read_files>
  <write_files>
    src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java
  </write_files>
  <action>
    MetricsQueryRequest.java 中两处 JavaDoc 注释 (line 28, line 40) 仍描述 "转为大写去重的 Set"，
    但代码已移除 toUpperCase()。更新注释为 "去重的 Set（保留原始大小写，由 MetricsServiceImpl
    归一化为规范标签）"。
  </action>
  <verify>grep -n "大写" src/main/java/com/graphnexus/api/graph/dto/MetricsQueryRequest.java → 0 命中</verify>
  <done>✅ 0 命中 "大写"</done>
  <depends_on></depends_on>
</task>
```