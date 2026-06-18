# ADR-013: GDS 图指标计算引擎 — transient 命名图 + 事件驱动缓存

- **日期**: 2026-06-17
- **状态**: proposed
- **来源**: `graph-metrics` DESIGN

---

## Context

需要在 Neo4j 宽图谱上支持 PageRank 和度中心性（Degree Centrality）两种图指标的计算。指标结果仅通过 API 返回（不持久化到节点属性），支持按节点类型和边类型过滤投影范围，并需要在图谱数据变更后自动刷新结果。

技术约束：
- 项目已有 Neo4j 5.26-community + podman-compose 声明 `NEO4J_PLUGINS=["graph-data-science"]`
- 项目使用 `Neo4jClient` 执行 Cypher（不走 SDN Template），需要决定 GDS 调用方式
- 项目无事件机制，需评估是否引入
- Caffeine 已在 pom.xml 作为本地缓存

## Decision

### 1. GDS 调用方式：Neo4jClient + Cypher `CALL gds.*.stream`

```java
// GdsAdapter 伪代码（L3）
public List<MetricResultBO> calculatePageRank(MetricsQuery query) {
    String graphName = "metrics-temp-" + UUID.randomUUID();

    try {
        // 1. 构建图投影
        String projectCypher = buildProjectCypher(graphName, query);
        neo4jClient.query(projectCypher).run();

        // 2. 执行 PageRank
        String algoCypher = String.format(
            "CALL gds.pageRank.stream('%s', {maxIterations: %d, dampingFactor: %.2f}) " +
            "YIELD nodeId, score RETURN gds.util.asNode(nodeId).id AS nodeId, " +
            "labels(gds.util.asNode(nodeId))[0] AS nodeType, score",
            graphName, metricsProperties.pageRank().maxIterations(),
            metricsProperties.pageRank().dampingFactor()
        );
        return neo4jClient.query(algoCypher).fetch().all()
            .stream().map(this::toMetricResult).toList();

    } finally {
        // 3. 释放命名图
        neo4jClient.query("CALL gds.graph.drop('" + graphName + "')").run();
    }
}
```

### 2. 图投影模型：transient 命名图 + 可配置方向

GDS 5.x 的 `gds.graph.project` 支持 3 种边方向：

- **NATURAL**：保持 Neo4j 原图方向
- **REVERSE**：反转方向
- **UNDIRECTED**：双向

各边类型的方向由 DESIGN § 2.3 表决定。**UNDIRECTED 是关键**：ALIGNED_TO（Entity ↔ KP）和 MASTERS（Student ↔ KP）使用 UNDIRECTED，因为这些关系的语义是双向的——"某 KP 被很多 Entity 对齐"和"某 Entity 对齐到某 KP"在重要性度量中应相互传递。

投影参数由 API 的 `nodeTypes` / `edgeTypes` 过滤生成。未指定 = 全图所有类型。

### 3. 缓存策略：Caffeine Cache + 全量清空

```java
// MetricsServiceImpl (L2)
private final Cache<String, List<MetricResultBO>> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(metricsProperties.cache().ttlMinutes()))
    .maximumSize(50)
    .build();

public List<MetricResultBO> queryPageRank(MetricsQuery query) {
    String cacheKey = query.toCacheKey(); // MD5 hash of nodeTypes + edgeTypes
    return cache.get(cacheKey, key -> gdsAdapter.calculatePageRank(query));
}

public void clearCache() {
    cache.invalidateAll();
    log.debug("指标缓存已清空（图谱变更后自动触发）");
}
```

使用 `Cache.get(key, loader)` 确保并发场景下同一 key 仅触发一次 GDS 计算（Caffeine 内置的 `computeIfAbsent` 语义）——缓解 R5。

### 4. 事件触发：Spring ApplicationEventPublisher + @EventListener

**发布方**（在 3 个既有钩子处各加 1 行，不改既有 try-catch 结构）：

```java
// GraphServiceImpl.extract() 末尾（融合钩子之后）
eventPublisher.publishEvent(new GraphChangedEvent(this));

// GradeServiceImpl.uploadGradeCsv() 末尾（融合钩子之后）
eventPublisher.publishEvent(new GraphChangedEvent(this));

// FusionServiceImpl.fuseFull() 末尾 和 fuseIncremental() 末尾
eventPublisher.publishEvent(new GraphChangedEvent(this));
```

**消费方**（新增单文件）：

```java
@Component
public class MetricsCacheInvalidator {
    @EventListener
    public void onGraphChanged(GraphChangedEvent event) {
        metricsService.clearCache();
    }
}
```

事件为无载荷信号事件（`new GraphChangedEvent(source)`），消费者只需知道"图变了"即可全量清空缓存。

**选择 @EventListener 而非 @TransactionalEventListener**：指标缓存失效不需要等待事务提交——即使事务回滚，缓存被清空也无害（下次查询重新计算即可）。`@EventListener` 默认同步执行，但 `clearCache()` 是 O(1) 操作，不阻塞主流程。

## Consequences

### 优点

- **零新增依赖**：GDS 走 Cypher（Neo4jClient 已有），事件走 Spring 内置，缓存走 Caffeine（已在 pom.xml）
- **transient 模式零残留**：`finally` 块保证命名图释放，服务重启/异常都不会泄漏 GDS 内存图
- **并发安全**：Caffeine `Cache.get(key, loader)` 天然防 duplicate 计算
- **事件解耦**：发布方只需 1 行代码，未来增加消费者无需修改发布方
- **缓存 TTL 兜底**：即使事件遗漏，5 分钟后缓存自动过期拿到最新数据

### 缺点

- **GDS Cypher 拼接**：不像 Java API 有类型安全，投影参数拼接靠字符串，需测试覆盖
- **命名图名称随机化**：UUID 防止并发冲突但不可读——debug 时需从 Neo4j 日志推断哪个命名图对应哪个请求
- **缓存全清过于粗粒度**：任何图变更都清空所有投影组合的缓存，但当前组合数少（几十个），可接受
- **事件遗漏风险**：若新增图谱写操作忘记发布事件，缓存最多过期 5 分钟（TLL 兜底）

### 升级路径

- v2：`@EventListener` → `@Async` + `@EnableAsync`，异步非阻塞重算
- v2：Caffeine → Redis 集中缓存（多实例部署时）
- v2：缓存全清 → 精准失效（追踪哪些投影参数组合受本次变更影响）
- v2：transient 命名图 → 定时 Cron 预计算（大图场景下避免首次查询等待）

---

> 本 ADR 与 ADR-009（自动增量融合钩子）配合：图指标缓存失效事件复用同样的钩子位置，在融合调用之后发布，形成"变更 → 融合 → 指标刷新"的完整链条。
