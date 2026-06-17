package com.graphnexus.infrastructure.neo4j.gds;

import com.graphnexus.application.graph.metrics.config.MetricsProperties;
import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.model.MetricsQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j GDS 图算法调用适配器（L3）— 通过 Cypher {@code CALL gds.*.stream} 执行 PageRank 与度中心性计算。
 *
 * <p>使用 transient 命名图模式（计算后 {@code gds.graph.drop} 释放内存），
 * 见 ADR-013 §1。并发请求通过 UUID 随机化命名图名称隔离。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GdsAdapter {

    private final Neo4jClient neo4jClient;
    private final MetricsProperties metricsProperties;

    /** 边类型 → GDS orientation 映射（见 DESIGN §2.3） */
    private static final Map<String, String> ORIENTATION_MAP = Map.ofEntries(
            Map.entry("ALIGNED_TO", "UNDIRECTED"),
            Map.entry("MASTERS", "UNDIRECTED")
    );

    private static final String DEFAULT_ORIENTATION = "NATURAL";

    /**
     * 根据指标类型调度到对应算法。
     */
    public List<MetricResultBO> calculate(MetricsQuery query) {
        return switch (query.metricName()) {
            case "pagerank" -> calculatePageRank(query);
            case "inDegree", "outDegree" -> calculateDegree(query);
            default -> throw new IllegalArgumentException("不支持的指标类型: " + query.metricName());
        };
    }

    /**
     * PageRank 计算 —— transient 命名图投影 + gds.pageRank.stream。
     */
    public List<MetricResultBO> calculatePageRank(MetricsQuery query) {
        String graphName = generateGraphName();
        try {
            projectGraph(graphName, query.nodeTypes(), query.edgeTypes());
            return runPageRank(graphName);
        } finally {
            dropGraph(graphName);
        }
    }

    /**
     * 度中心性计算 —— transient 命名图投影 + gds.degree.stream。
     *
     * <p>NATURAL 方向给出 outDegree，REVERSE 方向给出 inDegree。</p>
     */
    public List<MetricResultBO> calculateDegree(MetricsQuery query) {
        String graphName = generateGraphName();
        try {
            projectGraph(graphName, query.nodeTypes(), query.edgeTypes());

            List<MetricResultBO> results = new ArrayList<>();

            // outDegree（NATURAL orientation）
            results.addAll(runDegreeStream(graphName, "NATURAL", "outDegree"));

            // inDegree（REVERSE orientation on a fresh projection of same graph）
            // GDS allows streaming with different orientations on the same named graph via config
            results.addAll(runDegreeStream(graphName, "REVERSE", "inDegree"));

            return results;
        } finally {
            dropGraph(graphName);
        }
    }

    // ======================== GDS 过程调用 ========================

    /**
     * 构建 transient 命名图投影。
     *
     * <p>Cypher 示例：
     * <pre>{@code
     * CALL gds.graph.project(
     *   'metrics-temp-abc123',
     *   ['KnowledgePoint', 'Entity'],
     *   {PREREQUISITE_OF: {orientation: 'NATURAL'}, ALIGNED_TO: {orientation: 'UNDIRECTED'}}
     * )
     * }</pre>
     */
    private void projectGraph(String graphName, Set<String> nodeTypes, Set<String> edgeTypes) {
        String nodeProj = buildNodeProjection(nodeTypes);
        String relProj = buildRelationshipProjection(edgeTypes);

        String cypher = String.format(
                "CALL gds.graph.project('%s', %s, %s)",
                graphName, nodeProj, relProj);

        log.debug("GDS 图投影: graphName={}, nodeTypes={}, edgeTypes={}", graphName, nodeTypes, edgeTypes);
        neo4jClient.query(cypher).run();
    }

    /**
     * 执行 PageRank 并映射结果。
     */
    private List<MetricResultBO> runPageRank(String graphName) {
        int maxIterations = metricsProperties.getPageRank().maxIterations();
        double dampingFactor = metricsProperties.getPageRank().dampingFactor();

        String cypher = String.format(
                "CALL gds.pageRank.stream('%s', {maxIterations: %d, dampingFactor: %.4f}) " +
                "YIELD nodeId, score " +
                "RETURN gds.util.asNode(nodeId).id AS nodeId, " +
                "labels(gds.util.asNode(nodeId))[0] AS nodeType, " +
                "score",
                graphName, maxIterations, dampingFactor);

        return fetchResults(cypher, "pagerank");
    }

    /**
     * 执行度中心性流式查询。
     *
     * @param orientation NATURAL（outDegree）或 REVERSE（inDegree）
     * @param metricName  输出 metricName
     */
    private List<MetricResultBO> runDegreeStream(String graphName, String orientation, String metricName) {
        // GDS degree.stream orientation 通过 procedure 参数指定
        // NATURAL → 定向出度；REVERSE → 定向入度
        String cypher = String.format(
                "CALL gds.degree.stream('%s', {orientation: '%s'}) " +
                "YIELD nodeId, score " +
                "RETURN gds.util.asNode(nodeId).id AS nodeId, " +
                "labels(gds.util.asNode(nodeId))[0] AS nodeType, " +
                "score",
                graphName, orientation);

        return fetchResults(cypher, metricName);
    }

    /**
     * 释放命名图。
     */
    private void dropGraph(String graphName) {
        try {
            neo4jClient.query("CALL gds.graph.drop('" + graphName + "')").run();
            log.debug("GDS 命名图已释放: {}", graphName);
        } catch (Exception e) {
            log.warn("释放 GDS 命名图 {} 失败（可能已被释放）: {}", graphName, e.getMessage());
        }
    }

    // ======================== 工具方法 ========================

    /**
     * 构建 GDS 节点投影列表。
     *
     * <p>空集合 → 通配符 {@code ['*']}（全标签）。否则用 Neo4j label 列表。</p>
     */
    private String buildNodeProjection(Set<String> nodeTypes) {
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            return "['*']";
        }
        String labels = nodeTypes.stream()
                .sorted()
                .map(t -> "'" + t + "'")
                .collect(Collectors.joining(", "));
        return "[" + labels + "]";
    }

    /**
     * 构建 GDS 关系投影。
     *
     * <p>空集合 → 通配符字符串 {@code '*'}（GDS 2.x 不支持 Map 中的 '*' key）。
     * 否则每条边类型按 DESIGN §2.3 映射 orientation（ALIGNED_TO/MASTERS → UNDIRECTED，其他 → NATURAL）。</p>
     */
    private String buildRelationshipProjection(Set<String> edgeTypes) {
        if (edgeTypes == null || edgeTypes.isEmpty()) {
            return "'*'";
        }
        String entries = edgeTypes.stream()
                .sorted()
                .map(t -> {
                    String orientation = ORIENTATION_MAP.getOrDefault(t.toUpperCase(), DEFAULT_ORIENTATION);
                    return t + ": {orientation: '" + orientation + "'}";
                })
                .collect(Collectors.joining(", "));
        return "{" + entries + "}";
    }

    /**
     * 执行 Cypher 并映射结果为 MetricResultBO 列表。
     */
    private List<MetricResultBO> fetchResults(String cypher, String metricName) {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher).fetch().all();
            return rows.stream()
                    .map(row -> new MetricResultBO(
                            (String) row.get("nodeId"),
                            (String) row.get("nodeType"),
                            metricName,
                            ((Number) row.get("score")).doubleValue()
                    ))
                    .sorted(Comparator.comparingDouble(MetricResultBO::metricValue).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("GDS 算法执行失败（metric={}）: {}", metricName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 生成唯一命名图名称。
     */
    private String generateGraphName() {
        return "metrics-temp-" + UUID.randomUUID().toString().replace("-", "");
    }
}