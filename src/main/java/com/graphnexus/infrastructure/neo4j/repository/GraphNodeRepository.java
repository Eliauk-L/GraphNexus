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
     * 按名称列表 + 学科查询 KP（增量融合 / MASTERS 重算用）。
     */
    public List<Map<String, Object>> findKnowledgePointsByNamesAndSubject(List<String> names, String subject) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (kp:KnowledgePoint) WHERE kp.name IN $names AND kp.subject = $subject " +
                    "RETURN kp.id AS id, kp.name AS name, kp.subject AS subject, " +
                    "kp.documentId AS documentId, kp.fusionSource AS fusionSource, " +
                    "kp.description AS description, kp.gradeLevel AS gradeLevel"
            ).bindAll(Map.of("names", names, "subject", subject)).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("按名称+学科查询 KP 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 边重定向：将源 KP 节点的所有边迁移到目标 KP 节点。
     *
     * <p><b>边类型从 Neo4j 动态发现</b>——新增边类型（如未来 RELATES_TO）无需修改此方法。
     * MASTERS 边除外（由 MastersRecalculationService 独立管理）。</p>
     */
    public void redirectEdges(String fromKpId, String toKpId) {
        // 1. 动态发现入边类型
        List<String> inTypes = queryRelationshipTypes(
                "MATCH (a)-[r]->(b:KnowledgePoint {id: $id}) WHERE type(r) <> 'MASTERS' RETURN DISTINCT type(r)", fromKpId);
        // 2. 动态发现出边类型
        List<String> outTypes = queryRelationshipTypes(
                "MATCH (a:KnowledgePoint {id: $id})-[r]->(b) RETURN DISTINCT type(r)", fromKpId);

        // 3. 重定向入边
        for (String type : inTypes) {
            try {
                neo4jClient.query(
                        "MATCH (a)-[r:" + type + "]->(b:KnowledgePoint {id: $fromId}) " +
                        "CREATE (a)-[r2:" + type + "]->(c:KnowledgePoint {id: $toId}) " +
                        "SET r2 = properties(r) DELETE r"
                ).bindAll(Map.of("fromId", fromKpId, "toId", toKpId)).run();
            } catch (Exception e) {
                log.debug("重定向入边 {} 失败: {}", type, e.getMessage());
            }
        }

        // 4. 重定向出边
        for (String type : outTypes) {
            try {
                neo4jClient.query(
                        "MATCH (a:KnowledgePoint {id: $fromId})-[r:" + type + "]->(b) " +
                        "CREATE (c:KnowledgePoint {id: $toId})-[r2:" + type + "]->(b) " +
                        "SET r2 = properties(r) DELETE r"
                ).bindAll(Map.of("fromId", fromKpId, "toId", toKpId)).run();
            } catch (Exception e) {
                log.debug("重定向出边 {} 失败: {}", type, e.getMessage());
            }
        }
        log.debug("边重定向完成: {} → {} (入边={}, 出边={})", fromKpId, toKpId, inTypes, outTypes);
    }

    /** 查询节点的关系类型列表 */
    private List<String> queryRelationshipTypes(String cypher, String nodeId) {
        try {
            return neo4jClient.query(cypher)
                    .bindAll(Map.of("id", nodeId))
                    .fetch().all().stream()
                    .map(row -> (String) row.get("type(r)"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("查询关系类型失败: {}", e.getMessage());
            return Collections.emptyList();
        }
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
     * 查询某学科下所有有考试记录的学生（全量 MASTERS 重算用）。
     */
    public List<Map<String, Object>> findAllStudentsBySubject(String subject) {
        try {
            String cypher = "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint {subject: $subject}) " +
                    "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId";
            Collection<Map<String, Object>> rows = neo4jClient.query(cypher)
                    .bindAll(Map.of("subject", subject)).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("查询 subject={} 的学生失败: {}", subject, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按知识点名称 + 学科查找受影响的 Student（增量融合用）。
     */
    public List<Map<String, Object>> findStudentsByKpNamesAndSubject(List<String> kpNames, String subject) {
        if (kpNames == null || kpNames.isEmpty()) return Collections.emptyList();
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint) " +
                    "WHERE kp.name IN $names AND kp.subject = $subject " +
                    "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId"
            ).bindAll(Map.of("names", kpNames, "subject", subject)).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("按 KP 名称+学科查找 Student 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按知识点名称查找受影响的 Student（回滚用，不限学科）。
     */
    public List<Map<String, Object>> findStudentsByKpNames(List<String> kpNames) {
        if (kpNames == null || kpNames.isEmpty()) return Collections.emptyList();
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint) " +
                    "WHERE kp.name IN $names " +
                    "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId"
            ).bindAll(Map.of("names", kpNames)).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("按 KP 名称查找 Student 失败: {}", e.getMessage());
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
     * 更新节点属性（按 id 匹配现有节点）。
     */
    public void updateNodeProperties(String label, Map<String, Object> props) {
        String cypher = String.format("MATCH (n:%s {id: $id}) SET n = $props", label);
        neo4jClient.query(cypher).bindAll(Map.of("id", props.get("id"), "props", props)).run();
    }

    /**
     * 创建新节点（回滚用，重建被删除的 KP）。
     */
    public void createNode(String label, Map<String, Object> props) {
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

    // ======================== 智能问答只读查询（intelligent-qa T07） ========================

    /**
     * 按姓名模糊匹配 Student 节点。
     *
     * @param name 学生姓名（支持部分匹配）
     * @return 匹配的学生列表（含 id/studentNo/name/className/grade）
     */
    public List<Map<String, Object>> findStudentByName(String name) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student) WHERE s.name CONTAINS $name " +
                    "RETURN s.id AS id, s.studentNo AS studentNo, s.name AS name, " +
                    "s.className AS className, s.grade AS grade"
            ).bindAll(Map.of("name", name)).fetch().all());
        } catch (Exception e) {
            log.warn("按姓名查询 Student 失败: name={}, {}", name, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按学号精确查找 Student 节点。
     *
     * @param studentNo 学号（唯一标识）
     * @return Optional 包裹的学生属性 Map（id/studentNo/name/className/grade）
     */
    public Optional<Map<String, Object>> findStudentByNo(String studentNo) {
        try {
            var rows = neo4jClient.query(
                    "MATCH (s:Student {studentNo: $studentNo}) " +
                    "RETURN s.id AS id, s.studentNo AS studentNo, s.name AS name, " +
                    "s.className AS className, s.grade AS grade"
            ).bindAll(Map.of("studentNo", studentNo)).fetch().all();
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new HashMap<>(rows.iterator().next()));
        } catch (Exception e) {
            log.warn("按学号查询 Student 失败: studentNo={}, {}", studentNo, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 查询学生指定学科的所有 MASTERS 边。
     *
     * @param studentNodeId Student 节点的 Neo4j id
     * @param subject       学科
     * @return KP 列表（含 kp.id/kp.name/kp.description/kp.gradeLevel/m.weight/m.description）
     */
    public List<Map<String, Object>> findMastersByStudentAndSubject(String studentNodeId, String subject) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student {id: $sid})-[m:MASTERS]->(kp:KnowledgePoint {subject: $subject}) " +
                    "RETURN kp.id AS kpId, kp.name AS kpName, kp.description AS kpDescription, " +
                    "kp.gradeLevel AS kpGradeLevel, m.weight AS weight, m.description AS description"
            ).bindAll(Map.of("sid", studentNodeId, "subject", subject)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 MASTERS 边失败: sid={}, subject={}, {}", studentNodeId, subject, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询学生指定学科下所有考试覆盖的知识点（MASTERS 降级用）。
     *
     * <p>当 MASTERS 边不存在时（融合未执行），通过 TESTED 路径获取原始成绩关联。</p>
     *
     * @param studentNo 学号
     * @param subject   学科
     * @return KP 列表（含 kpName）
     */
    public List<Map<String, Object>> findTestedKpsByStudentAndSubject(String studentNo, String subject) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student {studentNo: $studentNo})-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint {subject: $subject}) " +
                    "RETURN DISTINCT kp.id AS kpId, kp.name AS kpName"
            ).bindAll(Map.of("studentNo", studentNo, "subject", subject)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 TESTED 路径失败: studentNo={}, subject={}, {}", studentNo, subject, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 展开前置依赖链（上游方向）。
     *
     * <p>从指定 KP 出发，沿 PREREQUISITE_OF 边向上游遍历（当前 KP 依赖的前置 KP），最多 maxHops 跳。</p>
     *
     * @param kpIds   起始 KnowledgePoint 的 Neo4j id 列表
     * @param maxHops 最大遍历跳数（1~3）
     * @return 前置依赖关系列表（含 fromKpId/toKpId/toKpName/hops）
     */
    public List<Map<String, Object>> findPrerequisitesUpstream(List<String> kpIds, int maxHops) {
        if (kpIds == null || kpIds.isEmpty()) return Collections.emptyList();
        int hops = Math.max(1, Math.min(maxHops, 3));
        try {
            String cypher = String.format(
                    "MATCH (kp:KnowledgePoint)-[:PREREQUISITE_OF*1..%d]->(pre:KnowledgePoint) " +
                    "WHERE kp.id IN $ids " +
                    "RETURN DISTINCT kp.id AS fromKpId, pre.id AS toKpId, pre.name AS toKpName, " +
                    "length(path) AS hops " +
                    "LIMIT 200", hops);
            return new ArrayList<>(neo4jClient.query(cypher)
                    .bindAll(Map.of("ids", kpIds)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 PREREQUISITE_OF 链失败: kpIds={}, maxHops={}, {}", kpIds, maxHops, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询指定 Student 对一批 KP 的 MASTERS 边。
     *
     * @param studentNodeId Student 节点的 Neo4j id
     * @param kpIds         KnowledgePoint 的 Neo4j id 列表
     * @return MASTERS 边列表（含 kp.id/kp.name/m.weight）
     */
    public List<Map<String, Object>> findMastersByStudentAndKpIds(String studentNodeId, List<String> kpIds) {
        if (kpIds == null || kpIds.isEmpty()) return Collections.emptyList();
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student {id: $sid})-[m:MASTERS]->(kp:KnowledgePoint) " +
                    "WHERE kp.id IN $kpIds " +
                    "RETURN kp.id AS kpId, kp.name AS kpName, m.weight AS weight"
            ).bindAll(Map.of("sid", studentNodeId, "kpIds", kpIds)).fetch().all());
        } catch (Exception e) {
            log.warn("查询指定 KP 的 MASTERS 边失败: sid={}, {}", studentNodeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询图谱中所有不重复的学科（从 KnowledgePoint.subject 聚合）。
     *
     * @return 学科名称列表（如 ["数学", "物理", "英语"]）
     */
    public List<String> findDistinctSubjects() {
        try {
            return neo4jClient.query(
                    "MATCH (kp:KnowledgePoint) WHERE kp.subject IS NOT NULL " +
                    "RETURN DISTINCT kp.subject AS subject ORDER BY subject"
            ).fetch().all().stream()
                    .map(row -> (String) row.get("subject"))
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询学科列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

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