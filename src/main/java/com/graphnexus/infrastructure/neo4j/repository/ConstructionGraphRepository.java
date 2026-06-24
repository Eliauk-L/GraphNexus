package com.graphnexus.infrastructure.neo4j.repository;

import com.graphnexus.infrastructure.neo4j.edge.EdgeType;
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
     * 若 id 为 null，先自动生成 UUID 确保 MERGE 能精确匹配节点。
     */
    public <T extends GraphNode> T save(T node) {
        // 保证 id 非 null：SDN @GeneratedValue 仅在 SDN save 时触发，
        // 直接使用 neo4jClient.query 时需手动生成，否则 MERGE {id: null} 行为不可预测
        if (node.getId() == null) {
            node.setId(UUID.randomUUID().toString());
        }
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
        String displayName = EdgeType.fromType(edge.getEdgeType()).getDisplayName();
        String cypher = String.format(
                "MATCH (a {id: $sourceId}), (b {id: $targetId}) " +
                "CREATE (a)-[r:%s]->(b) " +
                "SET r.createdAt = $createdAt, r.edgeType = $edgeType, r.weight = $weight, r.description = $description, r.displayName = $displayName",
                edge.getEdgeType());
        neo4jClient.query(cypher).bindAll(Map.of(
                "sourceId", edge.getSourceNodeId(),
                "targetId", edge.getTargetNodeId(),
                "createdAt", edge.getCreatedAt(),
                "edgeType", edge.getEdgeType(),
                "weight", edge.getWeight(),
                "description", edge.getDescription() != null ? edge.getDescription() : "",
                "displayName", displayName != null ? displayName : ""
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
                m.put("displayName", EdgeType.fromType(type).getDisplayName());
                return m;
            }).collect(Collectors.toList());
            String cypher = "UNWIND $edges AS edge " +
                    "MATCH (a {id: edge.sourceId}), (b {id: edge.targetId}) " +
                    "CREATE (a)-[r:" + type + "]->(b) " +
                    "SET r.createdAt = edge.createdAt, r.edgeType = $type, " +
                    "r.weight = edge.weight, r.description = edge.description, r.displayName = edge.displayName";
            neo4jClient.query(cypher).bindAll(Map.of("edges", edgeParams, "type", type)).run();
        }
        log.debug("批量保存 {} 条边完成，共 {} 种类型", edges.size(), byType.size());
    }

    // ======================== 文档子图查询 ========================

    /**
     * 从文档节点出发做图遍历，查询关联的 Entity 和 KnowledgePoint 节点。
     */
    public List<GraphNode> findByDocumentId(String documentId) {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (d {documentId: $docId}) " +
                    "OPTIONAL MATCH (d)-[:EXTRACTS]->(e:Entity) " +
                    "OPTIONAL MATCH (e)-[:ALIGNED_TO]->(kp:KnowledgePoint) " +
                    "OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp:KnowledgePoint) " +
                    "OPTIONAL MATCH (kp)-[:CHILD_OF]->(cat:KnowledgeCategory) " +
                    "OPTIONAL MATCH (cat)-[:CHILD_OF]->(parentCat:KnowledgeCategory) " +
                    "WITH collect(d) + collect(e) + collect(kp) + collect(nextKp) + collect(cat) + collect(parentCat) AS allNodes " +
                    "UNWIND allNodes AS n " +
                    "WITH DISTINCT n WHERE n IS NOT NULL " +
                    "RETURN n ORDER BY labels(n)[0]"
            ).bindAll(Map.of("docId", documentId)).fetch().all();
            List<GraphNode> nodes = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                org.neo4j.driver.types.Node n = (org.neo4j.driver.types.Node) row.get("n");
                SimpleGraphNode node = new SimpleGraphNode();
                node.setId(n.get("id").asString());
                node.setNodeType(n.labels().iterator().next());
                node.setDocumentId(n.containsKey("documentId") ? n.get("documentId").asString() : documentId);
                try { node.setCreatedAt(java.time.LocalDateTime.parse(n.get("createdAt").asString())); }
                catch (Exception e) { node.setCreatedAt(null); }
                // 提取所有 Neo4j 节点属性（name, description 等）
                node.setProperties(new HashMap<>(n.asMap()));
                nodes.add(node);
            }
            return dedupeById(nodes);
        } catch (Exception e) {
            log.warn("按 documentId={} 查询节点失败: {}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询图遍历结果中的边：文档→实体→知识点之间的所有关系。
     */
    public List<GraphEdge> findEdgesByDocumentId(String documentId) {
        try {
            Collection<Map<String, Object>> result = neo4jClient.query(
                    "MATCH (d {documentId: $docId}) " +
                    "OPTIONAL MATCH (d)-[r1:EXTRACTS]->(e:Entity) " +
                    "OPTIONAL MATCH (e)-[r2:ALIGNED_TO]->(kp:KnowledgePoint) " +
                    "OPTIONAL MATCH (kp)-[r3:PREREQUISITE_OF]->(nextKp:KnowledgePoint) " +
                    "OPTIONAL MATCH (kp)-[r4:CHILD_OF]->(cat:KnowledgeCategory) " +
                    "OPTIONAL MATCH (cat)-[r5:CHILD_OF]->(parentCat:KnowledgeCategory) " +
                    "WITH collect(r1) + collect(r2) + collect(r3) + collect(r4) + collect(r5) AS allRels " +
                    "UNWIND allRels AS r " +
                    "WITH DISTINCT r WHERE r IS NOT NULL " +
                    "RETURN startNode(r).id AS sourceNodeId, endNode(r).id AS targetNodeId, type(r) AS edgeType"
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

    /**
     * 删除孤点 StudentNode — 仅当该学生没有任何 ATTENDED 关系时才删除。
     *
     * <p>用于考试级联删除后清理：某学生所有考试都被删 → MySQL 无记录 → 删除 Neo4j 孤点。</p>
     *
     * @param studentNo 学号
     * @return 删除的节点数（0 表示仍有关系未删除）
     */
    public int deleteOrphanStudent(String studentNo) {
        var summary = neo4jClient.query(
                "MATCH (s:Student {studentNo: $studentNo}) "
              + "WHERE NOT (s)-[:ATTENDED]->(:Exam) "
              + "DETACH DELETE s"
        ).bindAll(Map.of("studentNo", studentNo)).run();
        int deletedCount = summary.counters().nodesDeleted();
        if (deletedCount > 0) {
            log.debug("已删除孤点 StudentNode: studentNo={}", studentNo);
        }
        return deletedCount;
    }

    /**
     * 查询考试直接关联的知识点名称列表（考前取出，用于清理后孤点检查）。
     */
    public List<String> findKpNamesByExamNo(String examNo) {
        return neo4jClient.query(
                "MATCH (e:Exam {examNo: $examNo})-[:TESTED]->(kp:KnowledgePoint) RETURN kp.name"
        ).bindAll(Map.of("examNo", examNo)).fetch().all().stream()
                .map(row -> (String) row.get("kp.name"))
                .toList();
    }

    /**
     * 删除孤点 KnowledgePoint — 仅当没有任何考试或文档实体引用时才删除。
     *
     * <p>检查条件：无 TESTED 关系（来自 Exam）+ 无 ALIGNED_TO 关系（来自 Entity/文档抽取）。
     * 同时满足两个条件才执行 DETACH DELETE，防止误删文档图谱引用的知识点。</p>
     *
     * @param kpName 知识点名称
     * @return 删除的节点数（0 表示仍有引用未删除）
     */
    public int deleteOrphanKnowledgePoint(String kpName) {
        var summary = neo4jClient.query(
                "MATCH (kp:KnowledgePoint {name: $kpName}) "
              + "WHERE NOT (kp)<-[:TESTED]-(:Exam) "
              + "  AND NOT (kp)<-[:ALIGNED_TO]-(:Entity) "
              + "DETACH DELETE kp"
        ).bindAll(Map.of("kpName", kpName)).run();
        int deletedCount = summary.counters().nodesDeleted();
        if (deletedCount > 0) {
            log.debug("已删除孤点 KnowledgePoint: name={}", kpName);
        }
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

    /**
     * 批量查询节点的所属学科名（按 BELONGS_TO_SUBJECT 边）。
     *
     * @param nodeIds 节点 ID 集合
     * @return nodeId → subjectName 映射
     */
    public Map<String, String> findSubjectByNodeIds(Set<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) return Collections.emptyMap();
        try {
            return neo4jClient.query(
                    "MATCH (n)-[:BELONGS_TO_SUBJECT]->(s:Subject) WHERE n.id IN $ids " +
                    "RETURN n.id AS nodeId, s.name AS subject"
            ).bindAll(Map.of("ids", nodeIds)).fetch().all().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row.get("nodeId"),
                            row -> (String) row.get("subject"),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量查询节点学科失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ======================== 学科全景图查询 ========================

    /**
     * 按学科查询知识图谱子图 — 返回该学科下 KnowledgePoint + Entity + Document 及依赖关系。
     *
     * <p>从 Subject 节点出发，沿 BELONGS_TO_SUBJECT 收集 KP，再扩展：
     * PREREQUISITE_OF（KP 依赖链）、CHILD_OF（分类层级）、
     * ALIGNED_TO（实体对齐）、EXTRACTS（文档来源）。</p>
     */
    public List<GraphNode> findBySubject(String subjectName) {
        try {
            // 两段收集：先拿所有 KP（含 prerequisite 目标），再为所有 KP 展开 Entity/Document/Category
            // 这样 nextKp 也不会成为孤立节点，确保全图连通
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    // 段1：收集学科 KP + prerequisite 目标 KP + Subject 节点
                    "MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name: $name}) " +
                    "OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp:KnowledgePoint) " +
                    "WITH s, collect(DISTINCT kp) + collect(DISTINCT nextKp) AS scopeKps " +
                    "UNWIND scopeKps AS sk " +
                    "WITH DISTINCT s, sk WHERE sk IS NOT NULL " +
                    // 段2：对每个范围 KP 展开 Entity、Document、Category
                    "OPTIONAL MATCH (sk)<-[:ALIGNED_TO]-(entity:Entity) " +
                    "OPTIONAL MATCH (entity)<-[:EXTRACTS]-(doc) " +
                    "OPTIONAL MATCH (sk)-[:CHILD_OF]->(cat:KnowledgeCategory) " +
                    "OPTIONAL MATCH (cat)-[:CHILD_OF]->(parentCat:KnowledgeCategory) " +
                    "WITH collect(DISTINCT s) + collect(DISTINCT sk) + collect(DISTINCT entity) + collect(DISTINCT doc) " +
                    "   + collect(DISTINCT cat) + collect(DISTINCT parentCat) AS allNodes " +
                    "UNWIND allNodes AS n " +
                    "WITH DISTINCT n WHERE n IS NOT NULL " +
                    "RETURN n ORDER BY labels(n)[0] " +
                    "LIMIT 1000"
            ).bindAll(Map.of("name", subjectName)).fetch().all();
            List<GraphNode> nodes = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                org.neo4j.driver.types.Node n = (org.neo4j.driver.types.Node) row.get("n");
                SimpleGraphNode node = new SimpleGraphNode();
                node.setId(n.get("id").asString());
                node.setNodeType(n.labels().iterator().next());
                try { node.setDocumentId(n.get("documentId").asString()); }
                catch (Exception e) { node.setDocumentId(null); }
                try { node.setCreatedAt(java.time.LocalDateTime.parse(n.get("createdAt").asString())); }
                catch (Exception e) { node.setCreatedAt(null); }
                node.setProperties(new HashMap<>(n.asMap()));
                nodes.add(node);
            }
            // 按 ID 去重（Cypher collect(DISTINCT) 跨列表拼接时同一节点可能出现在 kp 和 nextKp 中）
            return dedupeById(nodes);
        } catch (Exception e) {
            log.warn("按 subjectName={} 查询学科全景图节点失败: {}", subjectName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询学科全景图的边 — PREREQUISITE_OF + CHILD_OF + ALIGNED_TO + EXTRACTS。
     */
    public List<GraphEdge> findEdgesBySubject(String subjectName) {
        try {
            // 两段收集：先确定 KP 范围，再为该范围内所有 KP 收集边
            Collection<Map<String, Object>> result = neo4jClient.query(
                    // 段1：收集学科 KP + prerequisite 目标 KP + BELONGS_TO_SUBJECT 边
                    "MATCH (kp:KnowledgePoint)-[rb:BELONGS_TO_SUBJECT]->(s:Subject {name: $name}) " +
                    "OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp:KnowledgePoint) " +
                    "WITH collect(DISTINCT rb) AS belongRels, " +
                    "     collect(DISTINCT kp) + collect(DISTINCT nextKp) AS scopeKps " +
                    "UNWIND scopeKps AS sk " +
                    "WITH belongRels, sk WHERE sk IS NOT NULL " +
                    // 段2：对每个范围 KP 收集边
                    "OPTIONAL MATCH (sk)-[r:PREREQUISITE_OF]->(:KnowledgePoint) " +
                    "OPTIONAL MATCH (sk)-[:CHILD_OF]->(cat:KnowledgeCategory) " +
                    "OPTIONAL MATCH (cat)-[rc:CHILD_OF]->(:KnowledgeCategory) " +
                    "OPTIONAL MATCH (sk)<-[ra:ALIGNED_TO]-(:Entity) " +
                    "WITH belongRels, " +
                    "     collect(DISTINCT r) + collect(DISTINCT rc) + collect(DISTINCT ra) AS kpRels, " +
                    "     collect(DISTINCT sk) AS scopeKps2 " +
                    // 段3：EXTRACTS 边
                    "UNWIND scopeKps2 AS sk2 " +
                    "OPTIONAL MATCH (sk2)<-[:ALIGNED_TO]-(:Entity)<-[re:EXTRACTS]-() " +
                    "WITH belongRels, kpRels, collect(DISTINCT re) AS extraRels " +
                    "UNWIND belongRels + kpRels + extraRels AS r " +
                    "WITH DISTINCT r WHERE r IS NOT NULL " +
                    "RETURN startNode(r).id AS sourceNodeId, endNode(r).id AS targetNodeId, type(r) AS edgeType"
            ).bindAll(Map.of("name", subjectName)).fetch().all();
            return result.stream().map(row -> {
                SimpleGraphEdge edge = new SimpleGraphEdge();
                edge.setSourceNodeId((String) row.get("sourceNodeId"));
                edge.setTargetNodeId((String) row.get("targetNodeId"));
                edge.setEdgeType((String) row.get("edgeType"));
                return edge;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("按 subjectName={} 查询学科全景图边失败: {}", subjectName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 轻量查询 — 仅返回指定学科下所有 KnowledgePoint 的 id 集合。
     *
     * <p>用于 MetricsService 指标结果后置过滤（ADR-033），避免额外全图扫描。</p>
     */
    public Set<String> findKpIdsBySubject(String subjectName) {
        try {
            return neo4jClient.query(
                    "MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name: $name}) " +
                    "RETURN kp.id AS id"
            ).bindAll(Map.of("name", subjectName)).fetch().all().stream()
                    .map(row -> (String) row.get("id"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("按 subjectName={} 查询 KP ID 集合失败: {}", subjectName, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 轻量查询 — 仅返回指定文档关联的所有 KnowledgePoint 的 id 集合。
     *
     * <p>路径：Document → EXTRACTS → Entity → ALIGNED_TO → KnowledgePoint。
     * 用于 MetricsService 按文档过滤指标结果。</p>
     */
    public Set<String> findKpIdsByDocumentId(String documentId) {
        try {
            return neo4jClient.query(
                    "MATCH (d {documentId: $docId})-[:EXTRACTS]->(:Entity)-[:ALIGNED_TO]->(kp:KnowledgePoint) " +
                    "RETURN DISTINCT kp.id AS id"
            ).bindAll(Map.of("docId", documentId)).fetch().all().stream()
                    .map(row -> (String) row.get("id"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("按 documentId={} 查询 KP ID 集合失败: {}", documentId, e.getMessage());
            return Collections.emptySet();
        }
    }

    // ======================== 考试频次查询 ========================

    /**
     * 按学科查询每个知识点的考试频次（含 frequency=0 的 KP，确保覆盖图上所有知识点）。
     */
    public Map<String, Integer> queryExamFrequencyBySubject(String subjectName) {
        try {
            return neo4jClient.query(
                    "MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name: $name}) " +
                    "OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp:KnowledgePoint) " +
                    "WITH collect(DISTINCT kp) + collect(DISTINCT nextKp) AS scopeKps " +
                    "UNWIND scopeKps AS sk " +
                    "WITH DISTINCT sk WHERE sk IS NOT NULL " +
                    "OPTIONAL MATCH (e:Exam)-[:TESTED]->(sk) " +
                    "RETURN sk.id AS nodeId, count(e) AS frequency"
            ).bindAll(Map.of("name", subjectName)).fetch().all().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row.get("nodeId"),
                            row -> ((Number) row.get("frequency")).intValue(),
                            (a, b) -> a  // 去重保留首次
                    ));
        } catch (Exception e) {
            log.warn("按 subjectName={} 查询考试频次失败: {}", subjectName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 按文档查询每个知识点的考试频次（含 frequency=0 的 KP + prerequisite 目标）。
     */
    public Map<String, Integer> queryExamFrequencyByDocumentId(String documentId) {
        try {
            return neo4jClient.query(
                    "MATCH (d {documentId: $docId})-[:EXTRACTS]->(:Entity)-[:ALIGNED_TO]->(kp:KnowledgePoint) " +
                    "OPTIONAL MATCH (kp)-[:PREREQUISITE_OF]->(nextKp:KnowledgePoint) " +
                    "WITH collect(DISTINCT kp) + collect(DISTINCT nextKp) AS scopeKps " +
                    "UNWIND scopeKps AS sk " +
                    "WITH DISTINCT sk WHERE sk IS NOT NULL " +
                    "OPTIONAL MATCH (e:Exam)-[:TESTED]->(sk) " +
                    "RETURN sk.id AS nodeId, count(e) AS frequency"
            ).bindAll(Map.of("docId", documentId)).fetch().all().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row.get("nodeId"),
                            row -> ((Number) row.get("frequency")).intValue(),
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.warn("按 documentId={} 查询考试频次失败: {}", documentId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 查询文档关联的知识点是否已被考试引用。
     *
     * <p>通过 {@code EXTRACTS → ALIGNED_TO → TESTED} 路径，
     * 查找所有与文档知识点关联的考试。用于删除前的考试关联校验。</p>
     *
     * @param documentId 文档 ID
     * @return 关联的考试编号和名称列表（空列表表示无关联）
     */
    public List<Map<String, Object>> findExamsLinkedToDocument(String documentId) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (d {documentId: $docId})-[:EXTRACTS]->(:Entity)-[:ALIGNED_TO]->(kp:KnowledgePoint) " +
                    "MATCH (e:Exam)-[:TESTED]->(kp) " +
                    "RETURN DISTINCT e.examNo AS examNo, e.name AS examName"
            ).bindAll(Map.of("docId", documentId)).fetch().all());
        } catch (Exception e) {
            log.warn("查询文档关联考试失败: documentId={}, {}", documentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== Subject 辅助 ========================

    /**
     * 查找已有 SubjectNode，若无则创建（幂等）。
     */
    public SubjectNode findOrCreateSubject(String name) {
        String id = UUID.nameUUIDFromBytes(("SUBJECT:" + name).getBytes()).toString();

        String cypher = "MERGE (s:Subject {name: $name}) "
                + "ON CREATE SET s.id = $id, s.nodeType = 'Subject' "
                + "RETURN s.id AS id";

        try {
            var rows = neo4jClient.query(cypher).bindAll(Map.of(
                    "name", name, "id", id
            )).fetch().all();
            if (!rows.isEmpty()) {
                SubjectNode node = new SubjectNode(name);
                node.setId((String) rows.iterator().next().get("id"));
                return node;
            }
        } catch (Exception e) {
            log.warn("MERGE Subject 失败: name={}", name, e);
        }
        // 兜底：直接 save（极低概率）
        return save(new SubjectNode(name));
    }

    /**
     * 按学号查找已有 StudentNode，若无则创建（幂等）。
     *
     * <p>MERGE 键为 {@code studentNo}（学号），确保同一学生跨多次考试上传复用同一节点。
     * 已存在时更新 name/className/grade（以最新上传为准）。</p>
     */
    public StudentNode findOrCreateStudent(String studentNo, String name, String className, String grade) {
        String id = UUID.nameUUIDFromBytes(("STUDENT:" + studentNo).getBytes()).toString();

        String cypher = "MERGE (s:Student {studentNo: $studentNo}) "
                + "ON CREATE SET s.id = $id, s.name = $name, s.className = $className, "
                + "s.grade = $grade, s.nodeType = 'Student' "
                + "ON MATCH SET s.name = $name, s.className = $className, s.grade = $grade "
                + "RETURN s.id AS id";

        var rows = neo4jClient.query(cypher).bindAll(Map.of(
                "studentNo", studentNo,
                "id", id,
                "name", name,
                "className", className != null ? className : "",
                "grade", grade != null ? grade : ""
        )).fetch().all();

        StudentNode node = new StudentNode(studentNo, name, className, grade);
        if (!rows.isEmpty()) {
            node.setId((String) rows.iterator().next().get("id"));
        }
        return node;
    }

    /**
     * 按知识点名称 + Subject 节点查找已有 KnowledgePointNode，若无则创建（考试版本）。
     *
     * <p>MERGE 键为 {@code (name, BELONGS_TO_SUBJECT)} 组合——同一 Subject 下同名 KP 复用同一节点。
     * 不同 Subject 下同名 KP 各自独立。解决多次考试成绩上传产生重复 KP 节点的问题。</p>
     * <p>已存在时追加 CSV_IMPORT 来源标记。</p>
     */
    public KnowledgePointNode findOrCreateKnowledgePoint(String kpName, String subjectNodeId) {
        String id = UUID.nameUUIDFromBytes(("KP:" + kpName + ":" + subjectNodeId).getBytes()).toString();

        String cypher = "MATCH (s:Subject {id: $subjectNodeId}) "
                + "MERGE (kp:KnowledgePoint {name: $name})-[:BELONGS_TO_SUBJECT]->(s) "
                + "ON CREATE SET kp.id = $id, kp.fusionSource = $fusionSource, "
                + "kp.nodeType = 'KnowledgePoint', kp.description = '', kp.gradeLevel = '', kp.documentId = '' "
                + "ON MATCH SET kp.fusionSource = "
                + "CASE WHEN kp.fusionSource IS NULL OR kp.fusionSource = '' THEN $fusionSource "
                + "     WHEN kp.fusionSource CONTAINS $fusionSource THEN kp.fusionSource "
                + "     ELSE kp.fusionSource + ',' + $fusionSource END "
                + "RETURN kp.id AS id";

        var rows = neo4jClient.query(cypher).bindAll(Map.of(
                "subjectNodeId", subjectNodeId,
                "name", kpName,
                "id", id,
                "fusionSource", "CSV_IMPORT"
        )).fetch().all();

        KnowledgePointNode node = new KnowledgePointNode(kpName);
        if (!rows.isEmpty()) {
            node.setId((String) rows.iterator().next().get("id"));
        }
        return node;
    }

    /**
     * 按知识点名称 + Subject 节点查找已有 KnowledgePointNode（文档版本）。
     *
     * <p>MERGE 键为 {@code (name, BELONGS_TO_SUBJECT)} 组合——同一 Subject 下同名 KP 复用同一节点。
     * 文档 KP 携带完整属性（description/gradeLevel/documentId），ON MATCH 时覆盖（文档数据质量更高）。
     * 已存在时追加 DOCUMENT 来源标记。</p>
     */
    public KnowledgePointNode findOrCreateDocumentKnowledgePoint(String kpName, String description,
                                                                  String gradeLevel, String documentId,
                                                                  String subjectNodeId) {
        String id = UUID.nameUUIDFromBytes(("KP:" + kpName + ":" + subjectNodeId).getBytes()).toString();

        String cypher = "MATCH (s:Subject {id: $subjectNodeId}) "
                + "MERGE (kp:KnowledgePoint {name: $name})-[:BELONGS_TO_SUBJECT]->(s) "
                + "ON CREATE SET kp.id = $id, kp.description = $description, "
                + "kp.gradeLevel = $gradeLevel, kp.documentId = $documentId, "
                + "kp.fusionSource = $fusionSource, kp.nodeType = 'KnowledgePoint' "
                + "ON MATCH SET kp.description = $description, kp.gradeLevel = $gradeLevel, "
                + "kp.documentId = $documentId, "
                + "kp.fusionSource = CASE WHEN kp.fusionSource IS NULL OR kp.fusionSource = '' THEN $fusionSource "
                + "     WHEN kp.fusionSource CONTAINS $fusionSource THEN kp.fusionSource "
                + "     ELSE kp.fusionSource + ',' + $fusionSource END "
                + "RETURN kp.id AS id";

        var rows = neo4jClient.query(cypher).bindAll(Map.of(
                "subjectNodeId", subjectNodeId,
                "name", kpName,
                "id", id,
                "description", description != null ? description : "",
                "gradeLevel", gradeLevel != null ? gradeLevel : "",
                "documentId", documentId != null ? documentId : "",
                "fusionSource", "DOCUMENT"
        )).fetch().all();

        KnowledgePointNode node = new KnowledgePointNode(kpName, description, gradeLevel, documentId, "DOCUMENT");
        if (!rows.isEmpty()) {
            node.setId((String) rows.iterator().next().get("id"));
        }
        return node;
    }

    /**
     * 按知识点名称 + Subject 查找已有 KnowledgePointNode（只读查询，不创建）。
     *
     * <p>用于文档路径在创建 KP 前检查是否已有考试 KP 存在，复用其 id 避免重复节点。</p>
     *
     * @return 已有 KP 节点，若无则返回 null
     */
    public KnowledgePointNode findExistingKnowledgePoint(String kpName, String subjectNodeId) {
        try {
            var rows = neo4jClient.query(
                    "MATCH (kp:KnowledgePoint {name: $name})-[:BELONGS_TO_SUBJECT]->(s:Subject {id: $subjectNodeId}) "
                    + "RETURN kp.id AS id"
            ).bindAll(Map.of("name", kpName, "subjectNodeId", subjectNodeId)).fetch().all();
            if (!rows.isEmpty()) {
                KnowledgePointNode node = new KnowledgePointNode(kpName);
                node.setId((String) rows.iterator().next().get("id"));
                return node;
            }
        } catch (Exception e) {
            log.debug("查找已有 KnowledgePoint 失败: name={}", kpName, e);
        }
        return null;
    }

    // ======================== 内部类 ========================

    /**
     * 按节点 ID 去重，保留首次出现。
     */
    private static List<GraphNode> dedupeById(List<GraphNode> nodes) {
        java.util.LinkedHashMap<String, GraphNode> map = new java.util.LinkedHashMap<>();
        for (GraphNode n : nodes) {
            map.putIfAbsent(n.getId(), n);
        }
        return new ArrayList<>(map.values());
    }

    private static class SimpleGraphEdge extends GraphEdge {
        SimpleGraphEdge() { super(""); }
    }

    private static class SimpleGraphNode extends GraphNode {
        SimpleGraphNode() { super(""); }
    }
}