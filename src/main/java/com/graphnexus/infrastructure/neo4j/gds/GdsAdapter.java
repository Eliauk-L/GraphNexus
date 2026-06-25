package com.graphnexus.infrastructure.neo4j.gds;

import com.graphnexus.infrastructure.neo4j.gds.config.MetricsProperties;
import com.graphnexus.infrastructure.neo4j.gds.model.GdsResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j GDS 图算法适配器（L3）— 仅负责原始 Cypher 调用。
 *
 * <p>路由、编排、结果转换等业务逻辑由 L2 {@code MetricsService} 负责。
 * 见 ADR-013。</p>
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

    // ======================== 公开 API ========================

    /**
     * 构建 transient 命名图投影，返回图名称供后续操作。
     */
    public String projectGraph(Set<String> nodeTypes, Set<String> edgeTypes) {
        String graphName = generateGraphName();
        String nodeProj = buildNodeProjection(nodeTypes);
        String relProj = buildRelationshipProjection(edgeTypes);

        String cypher = String.format(
                "CALL gds.graph.project('%s', %s, %s)",
                graphName, nodeProj, relProj);

        log.debug("GDS 图投影: graphName={}, nodeTypes={}, edgeTypes={}", graphName, nodeTypes, edgeTypes);
        try {
            neo4jClient.query(cypher).run();
            return graphName;
        } catch (Exception e) {
            log.warn("GDS 图投影失败(可能部分边类型不存在): {}", e.getMessage());
            // 回退：尝试仅用 '*' 投影全部关系类型
            if (!"'*'".equals(relProj)) {
                try {
                    String fallbackCypher = String.format(
                            "CALL gds.graph.project('%s', %s, '*')",
                            graphName, nodeProj);
                    log.info("GDS 回退投影: graphName={}, relProj=*", graphName);
                    neo4jClient.query(fallbackCypher).run();
                    return graphName;
                } catch (Exception e2) {
                    log.warn("GDS 回退投影也失败: {}", e2.getMessage());
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * 执行 PageRank 流式计算。
     */
    public List<GdsResult> runPageRank(String graphName) {
        try {
            int maxIterations = metricsProperties.getPageRank().maxIterations();
            double dampingFactor = metricsProperties.getPageRank().dampingFactor();

            String cypher = String.format(
                    "CALL gds.pageRank.stream('%s', {maxIterations: %d, dampingFactor: %.4f}) " +
                    "YIELD nodeId, score " +
                    "RETURN gds.util.asNode(nodeId).id AS nodeId, " +
                    "labels(gds.util.asNode(nodeId))[0] AS nodeType, " +
                    "coalesce(gds.util.asNode(nodeId).name, gds.util.asNode(nodeId).id) AS nodeName, " +
                    "'' AS subject, " +
                    "coalesce(gds.util.asNode(nodeId).className, '') AS className, " +
                    "score",
                    graphName, maxIterations, dampingFactor);

            return fetchResults(cypher);
        } catch (Exception e) {
            log.warn("PageRank 计算失败(可能无边或无节点): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 执行度中心性流式计算。
     *
     * @param orientation NATURAL（outDegree）或 REVERSE（inDegree）
     */
    public List<GdsResult> runDegreeStream(String graphName, String orientation) {
        try {
            String cypher = String.format(
                    "CALL gds.degree.stream('%s', {orientation: '%s'}) " +
                    "YIELD nodeId, score " +
                    "RETURN gds.util.asNode(nodeId).id AS nodeId, " +
                    "labels(gds.util.asNode(nodeId))[0] AS nodeType, " +
                    "coalesce(gds.util.asNode(nodeId).name, gds.util.asNode(nodeId).id) AS nodeName, " +
                    "'' AS subject, " +
                    "coalesce(gds.util.asNode(nodeId).className, '') AS className, " +
                    "score",
                    graphName, orientation);

            return fetchResults(cypher);
        } catch (Exception e) {
            log.warn("度中心性计算失败(orientation={}, 可能无边): {}", orientation, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 释放命名图。
     */
    public void dropGraph(String graphName) {
        try {
            neo4jClient.query("CALL gds.graph.drop('" + graphName + "')").run();
            log.debug("GDS 命名图已释放: {}", graphName);
        } catch (Exception e) {
            log.warn("释放 GDS 命名图 {} 失败（可能已被释放）: {}", graphName, e.getMessage());
        }
    }

    // ======================== 私有工具方法 ========================

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

    private List<GdsResult> fetchResults(String cypher) {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher).fetch().all();
            return rows.stream()
                    .map(row -> new GdsResult(
                            (String) row.get("nodeId"),
                            (String) row.get("nodeType"),
                            (String) row.get("nodeName"),
                            (String) row.get("subject"),
                            (String) row.get("className"),
                            ((Number) row.get("score")).doubleValue()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("GDS 算法执行失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String generateGraphName() {
        return "metrics-temp-" + UUID.randomUUID().toString().replace("-", "");
    }
}