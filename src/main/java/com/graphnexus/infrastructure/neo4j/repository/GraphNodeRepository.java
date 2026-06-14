package com.graphnexus.infrastructure.neo4j.repository;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
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
    private static final String COMMON_LABEL = "GraphNode";

    /**
     * 保存任意 GraphNode 子类，委托 SDN 自动映射。
     */
    public <T extends GraphNode> T save(T node) {
        return neo4jTemplate.save(node);
    }

    /**
     * 批量保存节点 — 使用 SDN 批量 API 避免 N+1。
     */
    public <T extends GraphNode> List<T> saveAll(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(neo4jTemplate.saveAll(nodes));
    }

    /**
     * 保存一条边 — 通过 Cypher CREATE 创建关系。
     *
     * <p>使用 {@code :GraphNode} 标签确保走索引；edgeType 来自枚举，通过 {@code String.format} 注入安全。</p>
     */
    public void saveEdge(GraphEdge edge) {
        String cypher = String.format(
                "MATCH (a:%s {id: $sourceId}), (b:%s {id: $targetId}) " +
                "CREATE (a)-[r:%s]->(b) " +
                "SET r.createdAt = $createdAt, r.edgeType = $edgeType, r.weight = $weight, r.description = $description",
                COMMON_LABEL, COMMON_LABEL, edge.getEdgeType());

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
     * 批量保存边。
     */
    public void saveAllEdges(List<? extends GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        for (GraphEdge edge : edges) {
            saveEdge(edge);
        }
    }

    /**
     * 按 documentId 查询该文档关联的所有节点。
     *
     * <p>使用 {@code :GraphNode} 标签 + documentId 属性索引，走 INDEX SEEK 而非 AllNodesScan。</p>
     */
    public List<GraphNode> findByDocumentId(String documentId) {
        try {
            return new ArrayList<>(neo4jTemplate.findAll(
                    "MATCH (n:" + COMMON_LABEL + " {documentId: $docId}) RETURN n",
                    Map.of("docId", documentId),
                    GraphNode.class
            ));
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
                    "MATCH (a:" + COMMON_LABEL + ")-[r]->(b:" + COMMON_LABEL + ") " +
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
                "MATCH (n:" + COMMON_LABEL + " {documentId: $docId}) DETACH DELETE n"
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
}