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
 * <p>新增节点/边子类无需修改本仓库代码。设计决策见 ADR-002。</p>
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

    /**
     * 保存任意 GraphNode 子类，委托 SDN 自动映射。
     */
    public <T extends GraphNode> T save(T node) {
        return neo4jTemplate.save(node);
    }

    /**
     * 批量保存节点。
     */
    public <T extends GraphNode> List<T> saveAll(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> saved = new ArrayList<>();
        for (T node : nodes) {
            saved.add(neo4jTemplate.save(node));
        }
        return saved;
    }

    /**
     * 保存一条边 — 通过 Cypher CREATE 创建关系。
     *
     * <p>SDN 7.x 中 @RelationshipProperties 边类无法直接通过 Neo4jTemplate.save() 保存，
     * 需用 Cypher 显式创建关系。</p>
     */
    public void saveEdge(GraphEdge edge) {
        String cypher = String.format(
                "MATCH (a {id: $sourceId}), (b {id: $targetId}) " +
                "CREATE (a)-[r:%s]->(b) SET r.createdAt = $createdAt, r.edgeType = $edgeType, r.weight = $weight, r.description = $description",
                edge.getEdgeType());

        neo4jClient.query(cypher)
                .bindAll(Map.of(
                        "sourceId", edge.getSourceNodeId(),
                        "targetId", edge.getTargetNodeId(),
                        "createdAt", edge.getCreatedAt().toString(),
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
     */
    public List<GraphNode> findByDocumentId(String documentId) {
        try {
            return new ArrayList<>(neo4jTemplate.findAll(
                    "MATCH (n) WHERE n.documentId = $docId RETURN n",
                    Map.of("docId", documentId),
                    GraphNode.class
            ));
        } catch (Exception e) {
            log.warn("按 documentId={} 查询节点失败: {}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按 documentId 查询该文档关联的所有边 — 返回边的 sourceNodeId/targetNodeId/edgeType。
     */
    public List<GraphEdge> findEdgesByDocumentId(String documentId) {
        try {
            Collection<Map<String, Object>> result = neo4jClient.query(
                    "MATCH (a)-[r]->(b) WHERE a.documentId = $docId OR b.documentId = $docId " +
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
     * 删除该文档关联的所有节点和边（DETACH DELETE 级联删除边）。
     * 在事务内调用以保证原子性。
     */
    public void deleteByDocumentId(String documentId) {
        neo4jClient.query(
                "MATCH (n {documentId: $docId}) DETACH DELETE n"
        ).bindAll(Map.of("docId", documentId)).run();
        log.debug("已删除 documentId={} 的所有节点和边", documentId);
    }

    /**
     * 匿名内部类创建 GraphEdge 实例（用于查询结果封装）。
     */
    private static class SimpleGraphEdge extends GraphEdge {
        SimpleGraphEdge() {
            super("");
        }
    }
}