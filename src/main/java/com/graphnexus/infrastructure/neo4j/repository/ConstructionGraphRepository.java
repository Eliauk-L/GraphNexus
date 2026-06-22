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
 * 图谱构建模块 Neo4j 数据访问 — 文档图谱 CRUD + 成绩事件图谱操作。
 *
 * <p>从原 {@code GraphNodeRepository} 拆分而来，专注于构建模块的 Neo4j 读写。
 * 所有文档子图（节点+边）的创建、查询、删除均通过此 Repository。
 * 面向 {@link GraphNode} / {@link GraphEdge} 抽象编程，不依赖具体子类。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ConstructionGraphRepository {

    private final Neo4jTemplate neo4jTemplate;
    private final Neo4jClient neo4jClient;

    // ======================== 节点 CRUD ========================

    /**
     * 保存任意 GraphNode 子类 — 通过 Cypher MERGE 创建。
     */
    public <T extends GraphNode> T save(T node) {
        String label = node.getNodeType();
        String cypher = String.format("MERGE (n:%s {id: $id}) SET n = $props", label);
        Map<String, Object> props = toNodeProps(node);
        Map<String, Object> params = new HashMap<>();
        params.put("id", node.getId());
        params.put("props", props);
        neo4jClient.query(cypher).bindAll(params).run();
        return node;
    }

    /**
     * 批量保存节点 — 逐条 MERGE。
     */
    public <T extends GraphNode> List<T> saveAll(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) return Collections.emptyList();
        for (T node : nodes) save(node);
        return nodes;
    }

    private Map<String, Object> toNodeProps(GraphNode node) {
        // 过滤 null 值，避免 SET n = $props 时用 null 覆盖已有属性
        // 场景：考试 KP（CSV_IMPORT）的 documentId/gradeLevel 为 null，
        // 若直接 SET 会覆盖文档 KP 已存储的正确值
        Map<String, Object> props = node.toProperties();
        Map<String, Object> filtered = new HashMap<>();
        for (var entry : props.entrySet()) {
            if (entry.getValue() != null) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    // ======================== 边 CRUD ========================

    /**
     * 保存一条边 — 通过 Cypher CREATE 创建关系。
     */
    public void saveEdge(GraphEdge edge) {
        String cypher = String.format(
                "MATCH (a {id: $sourceId}), (b {id: $targetId}) " +
                "CREATE (a)-[r:%s]->(b) " +
                "SET r.createdAt = $createdAt, r.edgeType = $edgeType, r.weight = $weight, r.description = $description",
                edge.getEdgeType());
        neo4jClient.query(cypher).bindAll(Map.of(
                "sourceId", edge.getSourceNodeId(),
                "targetId", edge.getTargetNodeId(),
                "createdAt", edge.getCreatedAt(),
                "edgeType", edge.getEdgeType(),
                "weight", edge.getWeight(),
                "description", edge.getDescription() != null ? edge.getDescription() : ""
        )).run();
    }

    /**
     * 批量保存边 — 按 edgeType 分组，每组一条 UNWIND Cypher 避免 N+1。
     */
    public void saveAllEdges(List<? extends GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) return;
        Map<String, List<GraphEdge>> byType = edges.stream()
                .collect(Collectors.groupingBy(GraphEdge::getEdgeType));
        for (var entry : byType.entrySet()) {
            String type = entry.getKey();
            List<GraphEdge> group = entry.getValue();
            List<Map<String, Object>> edgeParams = group.stream().map(e -> {
                Map<String, Object> m = new HashMap<>();
                m.put("sourceId", e.getSourceNodeId());
                m.put("targetId", e.getTargetNodeId());
                m.put("createdAt", e.getCreatedAt());
                m.put("weight", e.getWeight());
                m.put("description", e.getDescription() != null ? e.getDescription() : "");
                return m;
            }).collect(Collectors.toList());
            String cypher = "UNWIND $edges AS edge " +
                    "MATCH (a {id: edge.sourceId}), (b {id: edge.targetId}) " +
                    "CREATE (a)-[r:" + type + "]->(b) " +
                    "SET r.createdAt = edge.createdAt, r.edgeType = $type, " +
                    "r.weight = edge.weight, r.description = edge.description";
            neo4jClient.query(cypher).bindAll(Map.of("edges", edgeParams, "type", type)).run();
        }
        log.debug("批量保存 {} 条边完成，共 {} 种类型", edges.size(), byType.size());
    }

    // ======================== 文档子图查询 ========================

    /**
     * 按 documentId 查询该文档关联的所有节点。
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
                // 提取所有 Neo4j 节点属性（name, description 等）
                node.setProperties(new HashMap<>(n.asMap()));
                nodes.add(node);
            }
            return nodes;
        } catch (Exception e) {
            log.warn("按 documentId={} 查询节点失败: {}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按 documentId 查询该文档关联的所有边。
     */
    public List<GraphEdge> findEdgesByDocumentId(String documentId) {
        try {
            Collection<Map<String, Object>> result = neo4jClient.query(
                    "MATCH (a)-[r]->(b) " +
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
     * 删除该文档关联的所有节点和边 — DETACH DELETE。
     */
    public void deleteByDocumentId(String documentId) {
        neo4jClient.query(
                "MATCH (n {documentId: $docId}) DETACH DELETE n"
        ).bindAll(Map.of("docId", documentId)).run();
        log.debug("已删除 documentId={} 的所有节点和边", documentId);
    }

    // ======================== 成绩事件图谱操作 ========================

    /**
     * 按考试编号删除指定类型的边（幂等）。
     */
    public int deleteEdgesByExamNo(String examNo, String edgeType) {
        String cypher;
        if ("ATTENDED".equals(edgeType)) {
            cypher = "MATCH (:Student)-[r:ATTENDED]->(e:Exam {examNo: $examNo}) DELETE r";
        } else {
            cypher = "MATCH (e:Exam {examNo: $examNo})-[r:TESTED]->(:KnowledgePoint) DELETE r";
        }
        var summary = neo4jClient.query(cypher).bindAll(Map.of("examNo", examNo)).run();
        int deletedCount = summary.counters().relationshipsDeleted();
        log.debug("已删除 {} 边 {} 条（examNo={}）", edgeType, deletedCount, examNo);
        return deletedCount;
    }

    /**
     * 删除 Neo4j 中的 Exam 节点（幂等，DETACH DELETE 兜底清除残留边）。
     */
    public int deleteExamNode(String examNo) {
        var summary = neo4jClient.query(
                "MATCH (e:Exam {examNo: $examNo}) DETACH DELETE e"
        ).bindAll(Map.of("examNo", examNo)).run();
        int deletedCount = summary.counters().nodesDeleted();
        log.debug("已删除 Exam 节点 {} 个（examNo={}）", deletedCount, examNo);
        return deletedCount;
    }

    // ======================== 全量图谱查询 ========================

    /**
     * 查询全量图谱所有节点（排除 DELETING 状态的文档关联节点）。
     */
    public List<GraphNode> findAllNodes() {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (n) RETURN n ORDER BY n.nodeType"
            ).fetch().all();
            List<GraphNode> nodes = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                org.neo4j.driver.types.Node n = (org.neo4j.driver.types.Node) row.get("n");
                SimpleGraphNode node = new SimpleGraphNode();
                node.setId(n.get("id").asString());
                node.setNodeType(n.get("nodeType").asString());
                try { node.setDocumentId(n.get("documentId").asString()); } catch (Exception e) { node.setDocumentId(null); }
                try { node.setCreatedAt(java.time.LocalDateTime.parse(n.get("createdAt").asString())); }
                catch (Exception e) { node.setCreatedAt(null); }
                node.setProperties(new HashMap<>(n.asMap()));
                nodes.add(node);
            }
            return nodes;
        } catch (Exception e) {
            log.warn("查询全量图谱节点失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询全量图谱所有边。
     */
    public List<GraphEdge> findAllEdges() {
        try {
            Collection<Map<String, Object>> result = neo4jClient.query(
                    "MATCH (a)-[r]->(b) " +
                    "RETURN a.id AS sourceNodeId, b.id AS targetNodeId, type(r) AS edgeType"
            ).fetch().all();
            return result.stream().map(row -> {
                SimpleGraphEdge edge = new SimpleGraphEdge();
                edge.setSourceNodeId((String) row.get("sourceNodeId"));
                edge.setTargetNodeId((String) row.get("targetNodeId"));
                edge.setEdgeType((String) row.get("edgeType"));
                return edge;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询全量图谱边失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== Subject 辅助 ========================

    /**
     * 按名称查找已有 SubjectNode，若无则创建（幂等）。
     */
    public SubjectNode findOrCreateSubject(String name) {
        try {
            var rows = neo4jClient.query(
                    "MATCH (s:Subject {name: $name}) RETURN s.id AS id"
            ).bindAll(Map.of("name", name)).fetch().all();
            if (!rows.isEmpty()) {
                SubjectNode existing = new SubjectNode(name);
                existing.setId((String) rows.iterator().next().get("id"));
                return existing;
            }
        } catch (Exception e) {
            log.debug("查找 Subject 失败，将创建新节点: name={}", name);
        }
        return save(new SubjectNode(name));
    }

    // ======================== 内部类 ========================

    private static class SimpleGraphEdge extends GraphEdge {
        SimpleGraphEdge() { super(""); }
    }

    private static class SimpleGraphNode extends GraphNode {
        SimpleGraphNode() { super(""); }
    }
}