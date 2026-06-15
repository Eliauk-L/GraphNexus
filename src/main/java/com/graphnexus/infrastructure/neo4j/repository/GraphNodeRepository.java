package com.graphnexus.infrastructure.neo4j.repository;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通用图节点/边仓库 — 面向 {@link GraphNode} / {@link GraphEdge} 抽象编程，不依赖具体子类。
 *
 * <p>所有图节点共享公共 label {@code :GraphNode}，查询时通过此 label 启用索引扫描，
 * 避免 AllNodesScan。索引见 {@code Neo4jIndexConfig}。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GraphNodeRepository {

    private final Neo4jTemplate neo4jTemplate;
    private final Neo4jClient neo4jClient;

    /** 所有节点共享的公共 label，用于索引扫描 */
    private static final String COMMON_LABEL = "";

    /** 构建带标签+属性的节点模式 */
    private static String node(String alias, String props) {
        String labelPart = COMMON_LABEL.isEmpty() ? "" : ":" + COMMON_LABEL;
        if (props.isEmpty()) {
            return "(" + alias + labelPart + ")";
        }
        return "(" + alias + labelPart + " " + props + ")";
    }

    /**
     * 保存任意 GraphNode 子类 — 通过 Cypher MERGE 创建。
     */
    public <T extends GraphNode> T save(T node) {
        String label = node.getNodeType();
        String cypher = String.format(
                "MERGE (n:%s {id: $id}) SET n = $props",
                label);
        Map<String, Object> props = toNodeProps(node);
        Map<String, Object> params = new HashMap<>();
        params.put("id", node.getId());
        params.put("props", props);
        neo4jClient.query(cypher)
                .bindAll(params)
                .run();
        return node;
    }

    /**
     * 批量保存节点 — 逐条 MERGE（节点数量通常不多）。
     */
    public <T extends GraphNode> List<T> saveAll(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        for (T node : nodes) {
            save(node);
        }
        return nodes;
    }

    /** 将 GraphNode 的属性转为 Cypher 参数 Map（多态分发，见 DESIGN D11） */
    private Map<String, Object> toNodeProps(GraphNode node) {
        return node.toProperties();
    }

    /**
     * 保存一条边 — 通过 Cypher CREATE 创建关系。
     *
     * <p>使用 {@code :GraphNode} 标签确保走索引；edgeType 来自枚举，通过 {@code String.format} 注入安全。</p>
     */
    public void saveEdge(GraphEdge edge) {
        String labelPart = COMMON_LABEL.isEmpty() ? "" : ":" + COMMON_LABEL;
        String cypher = String.format(
                "MATCH (a%s {id: $sourceId}), (b%s {id: $targetId}) " +
                "CREATE (a)-[r:%s]->(b) " +
                "SET r.createdAt = $createdAt, r.edgeType = $edgeType, r.weight = $weight, r.description = $description",
                labelPart, labelPart, edge.getEdgeType());

        neo4jClient.query(cypher)
                .bindAll(Map.of(
                        "sourceId", edge.getSourceNodeId(),
                        "targetId", edge.getTargetNodeId(),
                        "createdAt", edge.getCreatedAt(),
                        "edgeType", edge.getEdgeType(),
                        "weight", edge.getWeight(),
                        "description", edge.getDescription() != null ? edge.getDescription() : ""
                ))
                .run();
    }

    /**
     * 批量保存边 — 按 edgeType 分组，每组一条 UNWIND Cypher 避免 N+1。
     */
    public void saveAllEdges(List<? extends GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        // 按 edgeType 分组
        Map<String, List<GraphEdge>> byType = edges.stream()
                .collect(Collectors.groupingBy(GraphEdge::getEdgeType));

        for (var entry : byType.entrySet()) {
            String type = entry.getKey();
            List<GraphEdge> group = entry.getValue();

            // 构建每条边的参数 Map
            List<Map<String, Object>> edgeParams = group.stream()
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("sourceId", e.getSourceNodeId());
                        m.put("targetId", e.getTargetNodeId());
                        m.put("createdAt", e.getCreatedAt());
                        m.put("weight", e.getWeight());
                        m.put("description", e.getDescription() != null ? e.getDescription() : "");
                        return m;
                    })
                    .collect(Collectors.toList());

            String cypher = "UNWIND $edges AS edge " +
                    "MATCH " + node("a", "{id: edge.sourceId}") + ", " + node("b", "{id: edge.targetId}") + " " +
                    "CREATE (a)-[r:" + type + "]->(b) " +
                    "SET r.createdAt = edge.createdAt, r.edgeType = $type, " +
                    "r.weight = edge.weight, r.description = edge.description";

            neo4jClient.query(cypher)
                    .bindAll(Map.of("edges", edgeParams, "type", type))
                    .run();
        }
        log.debug("批量保存 {} 条边完成，共 {} 种类型", edges.size(), byType.size());
    }

    /**
     * 按 documentId 查询该文档关联的所有节点。
     * 使用 Neo4jClient 直接查询，绕过 SDN 的抽象类映射限制。
     */
    public List<GraphNode> findByDocumentId(String documentId) {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (n {documentId: $docId}) RETURN n ORDER BY n.nodeType"
            ).bindAll(Map.of("docId", documentId)).fetch().all();

            List<GraphNode> nodes = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                org.neo4j.driver.types.Node n = (org.neo4j.driver.types.Node) row.get("n");
                SimpleGraphNode node = new SimpleGraphNode();
                node.setId(n.get("id").asString());
                node.setNodeType(n.get("nodeType").asString());
                node.setDocumentId(n.get("documentId").asString());
                try { node.setCreatedAt(java.time.LocalDateTime.parse(n.get("createdAt").asString())); }
                catch (Exception e) { node.setCreatedAt(null); }
                node.setProperties(new HashMap<>());
                nodes.add(node);
            }
            return nodes;
        } catch (Exception e) {
            log.warn("按 documentId={} 查询节点失败: {}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按 documentId 查询该文档关联的所有边 — 返回 sourceNodeId/targetNodeId/edgeType。
     */
    public List<GraphEdge> findEdgesByDocumentId(String documentId) {
        try {
            Collection<Map<String, Object>> result = neo4jClient.query(
                    "MATCH " + node("a", "") + "-[r]->" + node("b", "") + " " +
                    "WHERE a.documentId = $docId OR b.documentId = $docId " +
                    "RETURN a.id AS sourceNodeId, b.id AS targetNodeId, type(r) AS edgeType"
            ).bindAll(Map.of("docId", documentId)).fetch().all();

            return result.stream().map(row -> {
                SimpleGraphEdge edge = new SimpleGraphEdge();
                edge.setSourceNodeId((String) row.get("sourceNodeId"));
                edge.setTargetNodeId((String) row.get("targetNodeId"));
                edge.setEdgeType((String) row.get("edgeType"));
                return edge;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("按 documentId={} 查询边失败: {}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 删除该文档关联的所有节点和边 — DETACH DELETE 级联删除边，走标签索引。
     */
    public void deleteByDocumentId(String documentId) {
        neo4jClient.query(
                "MATCH " + node("n", "{documentId: $docId}") + " DETACH DELETE n"
        ).bindAll(Map.of("docId", documentId)).run();
        log.debug("已删除 documentId={} 的所有节点和边", documentId);
    }

    /**
     * 内部类，用于查询结果的 GraphEdge 实例。
     */
    private static class SimpleGraphEdge extends GraphEdge {
        SimpleGraphEdge() {
            super("");
        }
    }

    /** 内部类，用于查询结果的 GraphNode 实例。 */
    private static class SimpleGraphNode extends GraphNode {
        SimpleGraphNode() {
            super("");
        }
    }
}