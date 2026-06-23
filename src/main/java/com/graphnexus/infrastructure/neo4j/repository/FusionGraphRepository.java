package com.graphnexus.infrastructure.neo4j.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 融合模块 Neo4j 数据访问 — KP 融合 + MASTERS 掌握度 + 回滚辅助。
 *
 * <p>从原 {@code GraphNodeRepository} 拆分而来，专注于融合和 MASTERS 的 Neo4j 读写。
 * 含全量/增量 KP 查询、边重定向、MASTERS 批量 upsert、回滚辅助方法。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FusionGraphRepository {

    private final Neo4jClient neo4jClient;

    // ======================== KP 查询（融合用） ========================

    /**
     * 查询所有 KnowledgePoint 节点（全量融合用）。
     */
    public List<Map<String, Object>> findAllKnowledgePoints() {
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject) " +
                    "RETURN kp.id AS id, kp.name AS name, s.id AS subjectNodeId, s.name AS subject, " +
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
     * 按名称列表 + 学科 SubjectNode 查询 KP（增量融合 / MASTERS 重算用）。
     */
    public List<Map<String, Object>> findKnowledgePointsByNamesAndSubject(List<String> names, String subjectName) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        try {
            Collection<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(s:Subject {name: $subjectName}) " +
                    "WHERE kp.name IN $names " +
                    "RETURN kp.id AS id, kp.name AS name, s.id AS subjectNodeId, s.name AS subject, " +
                    "kp.documentId AS documentId, kp.fusionSource AS fusionSource, " +
                    "kp.description AS description, kp.gradeLevel AS gradeLevel"
            ).bindAll(Map.of("names", names, "subjectName", subjectName)).fetch().all();
            return new ArrayList<>(rows);
        } catch (Exception e) {
            log.warn("按名称+学科查询 KP 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== 边重定向 + KP 删除（融合用） ========================

    /**
     * 边重定向：将源 KP 节点的所有边迁移到目标 KP 节点（MASTERS 除外）。
     */
    public void redirectEdges(String fromKpId, String toKpId) {
        List<String> inTypes = queryRelationshipTypes(
                "MATCH (a)-[r]->(b:KnowledgePoint {id: $id}) WHERE type(r) <> 'MASTERS' RETURN DISTINCT type(r)", fromKpId);
        List<String> outTypes = queryRelationshipTypes(
                "MATCH (a:KnowledgePoint {id: $id})-[r]->(b) RETURN DISTINCT type(r)", fromKpId);
        for (String type : inTypes) {
            try {
                neo4jClient.query(
                        "MATCH (a)-[r:" + type + "]->(b:KnowledgePoint {id: $fromId}) " +
                        "CREATE (a)-[r2:" + type + "]->(c:KnowledgePoint {id: $toId}) " +
                        "SET r2 = properties(r) DELETE r"
                ).bindAll(Map.of("fromId", fromKpId, "toId", toKpId)).run();
            } catch (Exception e) { log.debug("重定向入边 {} 失败: {}", type, e.getMessage()); }
        }
        for (String type : outTypes) {
            try {
                neo4jClient.query(
                        "MATCH (a:KnowledgePoint {id: $fromId})-[r:" + type + "]->(b) " +
                        "CREATE (c:KnowledgePoint {id: $toId})-[r2:" + type + "]->(b) " +
                        "SET r2 = properties(r) DELETE r"
                ).bindAll(Map.of("fromId", fromKpId, "toId", toKpId)).run();
            } catch (Exception e) { log.debug("重定向出边 {} 失败: {}", type, e.getMessage()); }
        }
        log.debug("边重定向完成: {} → {} (入边={}, 出边={})", fromKpId, toKpId, inTypes, outTypes);
    }

    private List<String> queryRelationshipTypes(String cypher, String nodeId) {
        try {
            return neo4jClient.query(cypher).bindAll(Map.of("id", nodeId))
                    .fetch().all().stream()
                    .map(row -> (String) row.get("type(r)"))
                    .filter(Objects::nonNull).collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    /**
     * 删除指定的 KnowledgePoint 节点（DETACH DELETE 兜底，幂等）。
     *
     * <p>优先使用 DETACH DELETE 确保即使 redirectEdges 遗漏边也能删除。
     * 记录实际删除节点数以辅助排查融合后 KP 未删除问题。</p>
     */
    public void deleteKnowledgePoints(List<String> kpIds) {
        if (kpIds == null || kpIds.isEmpty()) return;
        try {
            var summary = neo4jClient.query(
                    "MATCH (kp:KnowledgePoint) WHERE kp.id IN $ids DETACH DELETE kp"
            ).bindAll(Map.of("ids", kpIds)).run();
            int deleted = summary.counters().nodesDeleted();
            if (deleted > 0) {
                log.info("已删除 {} 个冗余 KP 节点（预期 {} 个）", deleted, kpIds.size());
            }
            if (deleted < kpIds.size()) {
                log.warn("KP 删除数量不足：实际删除 {} 个，预期 {} 个，ids={}", deleted, kpIds.size(), kpIds);
            }
        } catch (Exception e) {
            log.error("删除冗余 KP 节点失败: ids={}, error={}", kpIds, e.getMessage(), e);
        }
    }

    // ======================== Student 去重融合 ========================

    /**
     * 查询所有 Student 节点（含 id + studentNo + name）。
     */
    public List<Map<String, Object>> findAllStudents() {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student) RETURN s.id AS id, s.studentNo AS studentNo, s.name AS name " +
                    "ORDER BY s.id"
            ).fetch().all());
        } catch (Exception e) {
            log.warn("查询全部 Student 节点失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 合并重复 Student 节点（同 studentNo 不同 id）：边重定向到规范节点 + 删除冗余节点。
     *
     * <p>规范节点选取规则：同 studentNo 的第一个（按 id 升序，最旧节点存活）。
     * 需重定向的边类型：ATTENDED、MASTERS。共享节点不级联删除。</p>
     *
     * @return 合并的重复节点数（被删除的节点数）
     */
    public int mergeDuplicateStudents() {
        List<Map<String, Object>> allStudents = findAllStudents();

        // 按 studentNo 分组
        Map<String, List<Map<String, Object>>> byStudentNo = new LinkedHashMap<>();
        for (Map<String, Object> s : allStudents) {
            String sno = (String) s.get("studentNo");
            if (sno != null) {
                byStudentNo.computeIfAbsent(sno, k -> new ArrayList<>()).add(s);
            }
        }

        int deletedCount = 0;
        for (var entry : byStudentNo.entrySet()) {
            List<Map<String, Object>> nodes = entry.getValue();
            if (nodes.size() < 2) continue; // 无重复

            // 选规范节点（第一个，按 id 排序）
            String canonicalId = (String) nodes.get(0).get("id");
            for (int i = 1; i < nodes.size(); i++) {
                String dupId = (String) nodes.get(i).get("id");
                // 重定向 ATTENDED 边：(:Student {id: dupId})-[r:ATTENDED]->(e:Exam)
                // → 删除旧边，创建新边
                redirectStudentEdges(dupId, canonicalId, "ATTENDED");
                // 重定向 MASTERS 边
                redirectStudentEdges(dupId, canonicalId, "MASTERS");
                // 删除冗余 Student 节点
                deleteStudentNode(dupId);
                deletedCount++;
                log.debug("Student 融合: {} → {} (studentNo={})", dupId, canonicalId, entry.getKey());
            }
        }
        if (deletedCount > 0) {
            log.info("Student 融合完成: 合并 {} 个重复节点", deletedCount);
        }
        return deletedCount;
    }

    /** 将冗余 Student 节点的边重定向到规范节点（先创建新边，再删除旧边） */
    private void redirectStudentEdges(String fromStudentId, String toStudentId, String edgeType) {
        try {
            neo4jClient.query(
                    "MATCH (a:Student {id: $fromId})-[r:" + edgeType + "]->(b) "
                    + "MATCH (c:Student {id: $toId}) "
                    + "CREATE (c)-[r2:" + edgeType + "]->(b) "
                    + "SET r2 = properties(r) "
                    + "DELETE r"
            ).bindAll(Map.of("fromId", fromStudentId, "toId", toStudentId)).run();
        } catch (Exception e) {
            log.debug("重定向 Student {} 边 {} 失败（可能无边）: {}", edgeType, fromStudentId, e.getMessage());
        }
    }

    /** 删除指定 Student 节点（DETACH DELETE 兜底，幂等） */
    private void deleteStudentNode(String studentId) {
        try {
            neo4jClient.query("MATCH (s:Student {id: $id}) DETACH DELETE s")
                    .bindAll(Map.of("id", studentId)).run();
        } catch (Exception e) {
            log.warn("删除冗余 Student 节点失败: {}", e.getMessage());
        }
    }

    // ======================== MASTERS 批量操作 ========================

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

    /** MASTERS 批量写入的边数据 record */
    public record MastersEdgeData(String studentId, String kpId, double weight, String description) {}

    // ======================== Student 查询（MASTERS 重算/回滚用） ========================

    /**
     * 查询某学科下所有有考试记录的学生。
     */
    public List<Map<String, Object>> findAllStudentsBySubject(String subjectName) {
        try {
            String cypher = "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(sub:Subject {name: $name}) " +
                    "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId";
            return new ArrayList<>(neo4jClient.query(cypher)
                    .bindAll(Map.of("name", subjectName)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 subject={} 的学生失败: {}", subjectName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按知识点名称 + 学科查找受影响的 Student（增量融合用）。
     */
    public List<Map<String, Object>> findStudentsByKpNamesAndSubject(List<String> kpNames, String subjectName) {
        if (kpNames == null || kpNames.isEmpty()) return Collections.emptyList();
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(sub:Subject {name: $name}) " +
                    "WHERE kp.name IN $names " +
                    "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId"
            ).bindAll(Map.of("names", kpNames, "name", subjectName)).fetch().all());
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
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student)-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint) " +
                    "WHERE kp.name IN $names " +
                    "RETURN DISTINCT s.studentNo AS studentNo, s.id AS studentNodeId"
            ).bindAll(Map.of("names", kpNames)).fetch().all());
        } catch (Exception e) {
            log.warn("按 KP 名称查找 Student 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== MASTERS 查询（回滚用） ========================

    public List<Map<String, Object>> findMastersEdgesByStudent(String studentNodeId) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student {id: $id})-[r:MASTERS]->(kp:KnowledgePoint) " +
                    "RETURN kp.name AS kpName, r.weight AS weight"
            ).bindAll(Map.of("id", studentNodeId)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 MASTERS 边失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== 回滚辅助方法 ========================

    public void deleteIncomingEdges(String kpId, String edgeType) {
        neo4jClient.query(
                "MATCH (a)-[r:" + edgeType + "]->(b:KnowledgePoint {id: $kpId}) DELETE r"
        ).bindAll(Map.of("kpId", kpId)).run();
    }

    public void updateNodeProperties(String label, Map<String, Object> props) {
        String cypher = String.format("MATCH (n:%s {id: $id}) SET n = $props", label);
        neo4jClient.query(cypher).bindAll(Map.of("id", props.get("id"), "props", props)).run();
    }

    public void createNode(String label, Map<String, Object> props) {
        String cypher = String.format("CREATE (n:%s) SET n = $props", label);
        neo4jClient.query(cypher).bindAll(Map.of("props", props)).run();
    }

    public void deleteMastersEdge(String studentNodeId, String kpName) {
        neo4jClient.query(
                "MATCH (s:Student {id: $sid})-[r:MASTERS]->(kp:KnowledgePoint {name: $kpName}) DELETE r"
        ).bindAll(Map.of("sid", studentNodeId, "kpName", kpName)).run();
    }

    public void updateMastersWeight(String studentNodeId, String kpName, double weight, String description) {
        neo4jClient.query(
                "MATCH (s:Student {id: $sid})-[r:MASTERS]->(kp:KnowledgePoint {name: $kpName}) " +
                "SET r.weight = $weight, r.description = $description"
        ).bindAll(Map.of("sid", studentNodeId, "kpName", kpName, "weight", weight, "description", description != null ? description : "")).run();
    }
}