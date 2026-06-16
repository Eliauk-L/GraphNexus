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
     * 按考试编号删除指定类型的边（幂等）。
     *
     * <p>ATTENDED 方向：{@code MATCH (:Student)-[r:ATTENDED]->(e:Exam {examNo}) DELETE r}<br>
     * TESTED 方向：{@code MATCH (e:Exam {examNo})-[r:TESTED]->(:KnowledgePoint) DELETE r}</p>
     *
     * <p>见全局删除约束 C2（幂等）+ C4（删除顺序：先边后节点）+ C5（共享节点保留）。</p>
     *
     * @param examNo   考试编号
     * @param edgeType 边类型（ATTENDED / TESTED）
     * @return 删除的边数（0 表示已无此边，幂等）
     */
    public int deleteEdgesByExamNo(String examNo, String edgeType) {
        String cypher;
        if ("ATTENDED".equals(edgeType)) {
            cypher = "MATCH (:Student)-[r:ATTENDED]->(e:Exam {examNo: $examNo}) DELETE r";
        } else {
            cypher = "MATCH (e:Exam {examNo: $examNo})-[r:TESTED]->(:KnowledgePoint) DELETE r";
        }
        var summary = neo4jClient.query(cypher)
                .bindAll(Map.of("examNo", examNo))
                .run();
        int deletedCount = summary.counters().relationshipsDeleted();
        log.debug("已删除 {} 边 {} 条（examNo={}）", edgeType, deletedCount, examNo);
        return deletedCount;
    }

    /**
     * 删除 Neo4j 中的 Exam 节点（幂等）。
     *
     * <p>使用 DETACH DELETE 兜底清除残留边。若节点不存在则不报错。
     * Student 和 KnowledgePoint 节点不删除（全局约束 C5）。</p>
     *
     * @param examNo 考试编号
     * @return 删除的节点数（0 表示已无此节点，幂等）
     */
    public int deleteExamNode(String examNo) {
        var summary = neo4jClient.query(
                "MATCH (e:Exam {examNo: $examNo}) DETACH DELETE e"
        ).bindAll(Map.of("examNo", examNo)).run();
        int deletedCount = summary.counters().nodesDeleted();
        log.debug("已删除 Exam 节点 {} 个（examNo={}）", deletedCount, examNo);
        return deletedCount;
    }

    // ======================== 宽图谱融合方法（见 DESIGN T10） ========================

    /**
     * 查询所有 KnowledgePoint 节点（全量融合用）。
     */
    public List<Map<String, Object>> findAllKnowledgePoints() {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (kp:KnowledgePoint) " +
                    "RETURN kp.id AS id, kp.name AS name, kp.subject AS subject, " +
                    "kp.documentId AS documentId, kp.fusionSource AS fusionSource, " +
                    "kp.description AS description, kp.gradeLevel AS gradeLevel"
            ).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("查询全部 KnowledgePoint 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按名称列表查询指定 subject 的 KP（增量融合用）。
     */
    public List<Map<String, Object>> findKnowledgePointsByNames(List<String> names, String subject) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        try {
            String cypher;
            Map<String, Object> params;
            if (subject == null) {
                cypher = "MATCH (kp:KnowledgePoint) WHERE kp.name IN $names " +
                        "RETURN kp.id AS id, kp.name AS name, kp.subject AS subject, " +
                        "kp.documentId AS documentId, kp.fusionSource AS fusionSource, " +
                        "kp.description AS description, kp.gradeLevel AS gradeLevel";
                params = Map.of("names", names);
            } else {
                cypher = "MATCH (kp:KnowledgePoint) WHERE kp.name IN $names AND kp.subject = $subject " +
                        "RETURN kp.id AS id, kp.name AS name, kp.subject AS subject, " +
                        "kp.documentId AS documentId, kp.fusionSource AS fusionSource, " +
                        "kp.description AS description, kp.gradeLevel AS gradeLevel";
                params = Map.of("names", names, "subject", subject);
            }
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(params).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("按名称查询 KP 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 边重定向：将源 KP 节点的边迁移到目标 KP 节点。
     *
     * <p>分别处理入边（a→fromKpId 改为 a→toKpId）和出边（fromKpId→b 改为 toKpId→b）。
     * 未匹配到边的类型静默跳过（可能不存在该类型边）。</p>
     */
    public void redirectEdges(String fromKpId, String toKpId) {
        // 入边模板 + 出边模板：{type, cypherSnippet}
        record EdgeRedirect(String type, String cypher) {}
        EdgeRedirect[] redirects = {
                // 入边：其他节点 → fromKpId → 改为 → toKpId
                new EdgeRedirect("ALIGNED_TO",
                        "MATCH (a)-[r:ALIGNED_TO]->(b:KnowledgePoint {id: $fromId}) " +
                        "CREATE (a)-[r2:ALIGNED_TO]->(c:KnowledgePoint {id: $toId}) SET r2 = properties(r) DELETE r"),
                new EdgeRedirect("TESTED",
                        "MATCH (a)-[r:TESTED]->(b:KnowledgePoint {id: $fromId}) " +
                        "CREATE (a)-[r2:TESTED]->(c:KnowledgePoint {id: $toId}) SET r2 = properties(r) DELETE r"),
                new EdgeRedirect("PREREQUISITE_OF",
                        "MATCH (a)-[r:PREREQUISITE_OF]->(b:KnowledgePoint {id: $fromId}) " +
                        "CREATE (a)-[r2:PREREQUISITE_OF]->(c:KnowledgePoint {id: $toId}) SET r2 = properties(r) DELETE r"),
                new EdgeRedirect("CHILD_OF",
                        "MATCH (a)-[r:CHILD_OF]->(b:KnowledgePoint {id: $fromId}) " +
                        "CREATE (a)-[r2:CHILD_OF]->(c:KnowledgePoint {id: $toId}) SET r2 = properties(r) DELETE r"),
                // 出边：fromKpId → 其他节点 → 改为 toKpId → 其他节点
                new EdgeRedirect("BELONGS_TO-out",
                        "MATCH (a:KnowledgePoint {id: $fromId})-[r:BELONGS_TO]->(b) " +
                        "CREATE (c:KnowledgePoint {id: $toId})-[r2:BELONGS_TO]->(b) SET r2 = properties(r) DELETE r"),
                new EdgeRedirect("PREREQUISITE_OF-out",
                        "MATCH (a:KnowledgePoint {id: $fromId})-[r:PREREQUISITE_OF]->(b) " +
                        "CREATE (c:KnowledgePoint {id: $toId})-[r2:PREREQUISITE_OF]->(b) SET r2 = properties(r) DELETE r"),
                new EdgeRedirect("CHILD_OF-out",
                        "MATCH (a:KnowledgePoint {id: $fromId})-[r:CHILD_OF]->(b) " +
                        "CREATE (c:KnowledgePoint {id: $toId})-[r2:CHILD_OF]->(b) SET r2 = properties(r) DELETE r"),
        };

        for (EdgeRedirect redir : redirects) {
            try {
                neo4jClient.query(redir.cypher())
                        .bindAll(Map.of("fromId", fromKpId, "toId", toKpId)).run();
            } catch (Exception e) {
                log.debug("重定向边 {} 时无匹配: {}", redir.type(), e.getMessage());
            }
        }
        log.debug("边重定向完成: {} → {}", fromKpId, toKpId);
    }

    /**
     * 删除指定的 KnowledgePoint 节点（DETACH DELETE，幂等）。
     */
    public void deleteKnowledgePoints(List<String> kpIds) {
        if (kpIds == null || kpIds.isEmpty()) return;
        neo4jClient.query(
                "MATCH (kp:KnowledgePoint) WHERE kp.id IN $ids DETACH DELETE kp"
        ).bindAll(Map.of("ids", kpIds)).run();
        log.debug("已删除 {} 个冗余 KP 节点", kpIds.size());
    }

    /**
     * 按 Student 批量 upsert MASTERS 边。
     */
    public void batchUpsertMastersEdges(String studentNodeId, List<MastersEdgeData> edges) {
        if (edges == null || edges.isEmpty()) return;
        List<Map<String, Object>> edgeParams = edges.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", e.studentId());
            m.put("kpId", e.kpId());
            m.put("weight", e.weight());
            m.put("description", e.description() != null ? e.description() : "");
            return m;
        }).collect(Collectors.toList());

        neo4jClient.query(
                "UNWIND $edges AS edge " +
                "MATCH (s:Student {id: edge.studentId}), (kp:KnowledgePoint {id: edge.kpId}) " +
                "MERGE (s)-[r:MASTERS]->(kp) " +
                "SET r.weight = edge.weight, r.description = edge.description, " +
                "r.edgeType = 'MASTERS', r.createdAt = datetime()"
        ).bindAll(Map.of("edges", edgeParams)).run();
        log.debug("批量 upsert {} 条 MASTERS 边完成（student={}）", edges.size(), studentNodeId);
    }

    /**
     * 查找受影响的 Student（通过 TESTED 边关联到指定 KP 名称列表）。
     */
    public List<Map<String, Object>> findStudentsByKnowledgePointNames(List<String> kpNames, String subject) {
        if (kpNames == null || kpNames.isEmpty()) return Collections.emptyList();
        try {
            String cypher;
            Map<String, Object> params;
            if (subject == null) {
                cypher = "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint) " +
                        "WHERE kp.name IN $names " +
                        "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId";
                params = Map.of("names", kpNames);
            } else {
                cypher = "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint) " +
                        "WHERE kp.name IN $names AND kp.subject = $subject " +
                        "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId";
                params = Map.of("names", kpNames, "subject", subject);
            }
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(params).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("查找受影响 Student 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询某 Student 的所有 MASTERS 边（回滚校验用）。
     */
    public List<Map<String, Object>> findMastersEdgesByStudent(String studentNodeId) {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (s:Student {id: $id})-[r:MASTERS]->(kp:KnowledgePoint) " +
                    "RETURN kp.name AS kpName, r.weight AS weight"
            ).bindAll(Map.of("id", studentNodeId)).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("查询 MASTERS 边失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 删除指定 KP 的指定类型入边（回滚用）。
     */
    public void deleteIncomingEdges(String kpId, String edgeType) {
        neo4jClient.query(
                "MATCH (a)-[r:" + edgeType + "]->(b:KnowledgePoint {id: $kpId}) DELETE r"
        ).bindAll(Map.of("kpId", kpId)).run();
    }

    /**
     * 重建节点（回滚用）。
     */
    public void createNodeWithProperties(String label, Map<String, Object> props) {
        String cypher = String.format("CREATE (n:%s) SET n = $props", label);
        neo4jClient.query(cypher).bindAll(Map.of("props", props)).run();
    }

    /**
     * 删除 MASTERS 边（回滚用）。
     */
    public void deleteMastersEdge(String studentNodeId, String kpName) {
        neo4jClient.query(
                "MATCH (s:Student {id: $sid})-[r:MASTERS]->(kp:KnowledgePoint {name: $kpName}) DELETE r"
        ).bindAll(Map.of("sid", studentNodeId, "kpName", kpName)).run();
    }

    /**
     * 更新 MASTERS 边权重（回滚用）。
     */
    public void updateMastersWeight(String studentNodeId, String kpName, double weight, String description) {
        neo4jClient.query(
                "MATCH (s:Student {id: $sid})-[r:MASTERS]->(kp:KnowledgePoint {name: $kpName}) " +
                "SET r.weight = $weight, r.description = $description"
        ).bindAll(Map.of("sid", studentNodeId, "kpName", kpName, "weight", weight, "description", description != null ? description : "")).run();
    }

    /** MASTERS 批量写入的边数据 record */
    public record MastersEdgeData(String studentId, String kpId, double weight, String description) {}

    // ======================== 内部类 ========================
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