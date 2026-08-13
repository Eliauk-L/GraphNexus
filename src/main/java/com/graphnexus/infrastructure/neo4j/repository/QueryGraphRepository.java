package com.graphnexus.infrastructure.neo4j.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能问答 + 指标模块 Neo4j 只读查询 — Student / KP / MASTERS / 前置依赖链 / 学科列表。
 *
 * <p>从原 {@code GraphNodeRepository} 拆分而来。本 Repository 仅含只读查询，
 * 无写操作。服务于智能问答（QueryService）和指标计算（MetricsService）。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class QueryGraphRepository {

    private final Neo4jClient neo4jClient;

    // ======================== Student 查询 ========================

    /**
     * 按姓名精确匹配 Student 节点。
     */
    public List<Map<String, Object>> findStudentByName(String name) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student) WHERE s.name = $name " +
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
     */
    public Optional<Map<String, Object>> findStudentByNo(String studentNo) {
        try {
            var rows = neo4jClient.query(
                    "MATCH (s:Student {studentNo: $studentNo}) " +
                    "RETURN s.id AS id, s.studentNo AS studentNo, s.name AS name, " +
                    "s.className AS className, s.grade AS grade"
            ).bindAll(Map.of("studentNo", studentNo)).fetch().all();
            if (rows.isEmpty()) return Optional.empty();
            return Optional.of(new HashMap<>(rows.iterator().next()));
        } catch (Exception e) {
            log.warn("按学号查询 Student 失败: studentNo={}, {}", studentNo, e.getMessage());
            return Optional.empty();
        }
    }

    // ======================== MASTERS / TESTED 查询 ========================

    /**
     * 查询学生指定学科的所有 MASTERS 边。
     */
    public List<Map<String, Object>> findMastersByStudentAndSubject(String studentNodeId, String subjectName) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student {id: $sid})-[m:MASTERS]->(kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(sub:Subject {name: $name}) " +
                    "RETURN kp.id AS kpId, COALESCE(kp.name, '未命名知识点') AS kpName, kp.description AS kpDescription, " +
                    "kp.gradeLevel AS kpGradeLevel, m.weight AS weight, m.description AS description"
            ).bindAll(Map.of("sid", studentNodeId, "name", subjectName)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 MASTERS 边失败: sid={}, subject={}, {}", studentNodeId, subjectName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询学生指定学科下所有考试覆盖的知识点（MASTERS 降级用）。
     */
    public List<Map<String, Object>> findTestedKpsByStudentAndSubject(String studentNo, String subjectName) {
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student {studentNo: $studentNo})-[:ATTENDED]->(:Exam)-[:TESTED]->(kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(sub:Subject {name: $name}) " +
                    "RETURN DISTINCT kp.id AS kpId, COALESCE(kp.name, '未命名知识点') AS kpName"
            ).bindAll(Map.of("studentNo", studentNo, "name", subjectName)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 TESTED 路径失败: studentNo={}, subject={}, {}", studentNo, subjectName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询指定 Student 对一批 KP 的 MASTERS 边。
     */
    public List<Map<String, Object>> findMastersByStudentAndKpIds(String studentNodeId, List<String> kpIds) {
        if (kpIds == null || kpIds.isEmpty()) return Collections.emptyList();
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student {id: $sid})-[m:MASTERS]->(kp:KnowledgePoint) " +
                    "WHERE kp.id IN $kpIds " +
                    "RETURN kp.id AS kpId, COALESCE(kp.name, '未命名知识点') AS kpName, m.weight AS weight, m.description AS description"
            ).bindAll(Map.of("sid", studentNodeId, "kpIds", kpIds)).fetch().all());
        } catch (Exception e) {
            log.warn("查询指定 KP 的 MASTERS 边失败: sid={}, {}", studentNodeId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 批量查询多个学生对指定学科的 MASTERS 边（班级概览用，一次 Cypher 替代 N 次逐生查询）。
     */
    public List<Map<String, Object>> findMastersByStudentNos(List<String> studentNos, String subjectName) {
        if (studentNos == null || studentNos.isEmpty()) return Collections.emptyList();
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (s:Student)-[m:MASTERS]->(kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(sub:Subject {name: $name}) " +
                    "WHERE s.studentNo IN $studentNos " +
                    "RETURN s.studentNo AS studentNo, s.name AS studentName, kp.id AS kpId, " +
                    "COALESCE(kp.name, '未命名知识点') AS kpName, m.weight AS weight, m.description AS description"
            ).bindAll(Map.of("studentNos", studentNos, "name", subjectName)).fetch().all());
        } catch (Exception e) {
            log.warn("批量查询 MASTERS 边失败: studentNos.size={}, subject={}, {}", studentNos.size(), subjectName, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== 前置依赖链查询 ========================

    /** 按名称和描述检索知识点，并返回一跳依赖摘要。 */
    public List<Map<String, Object>> searchKnowledgePoints(String query, String subject, int topK) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        int limit = Math.max(1, Math.min(topK, 20));
        try {
            return new ArrayList<>(neo4jClient.query(
                    "MATCH (kp:KnowledgePoint)-[:BELONGS_TO_SUBJECT]->(:Subject {name: $subject}) " +
                    "WHERE toLower(coalesce(kp.name, '')) CONTAINS toLower($query) " +
                    "   OR toLower(coalesce(kp.description, '')) CONTAINS toLower($query) " +
                    "OPTIONAL MATCH (pre:KnowledgePoint)-[pr:PREREQUISITE_OF]->(kp) " +
                    "OPTIONAL MATCH (kp)-[nr:PREREQUISITE_OF]->(next:KnowledgePoint) " +
                    "RETURN kp.id AS kpId, kp.name AS kpName, kp.description AS description, " +
                    "kp.documentId AS documentId, collect(DISTINCT pre.name) AS prerequisites, " +
                    "collect(DISTINCT next.name) AS dependents, " +
                    "CASE WHEN toLower(kp.name) = toLower($query) THEN 1.0 " +
                    "     WHEN toLower(kp.name) CONTAINS toLower($query) THEN 0.8 ELSE 0.5 END AS score " +
                    "ORDER BY score DESC, kp.name ASC LIMIT $limit")
                    .bindAll(Map.of("query", query.trim(), "subject", subject, "limit", limit))
                    .fetch().all());
        } catch (Exception exception) {
            log.warn("检索知识点失败: query={}, subject={}, error={}", query, subject, exception.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 展开前置依赖链（上游方向），最多 maxHops 跳。
     *
     * <p>关系语义固定为 {@code prerequisite-[:PREREQUISITE_OF]->dependent}。
     * 因此查询必须以薄弱知识点为路径终点，沿入边反向找到前置知识。
     * 返回结果会展开为路径中的真实逐边关系，避免把多跳路径压缩成一条不存在的边。</p>
     */
    public List<Map<String, Object>> findPrerequisitesUpstream(List<String> kpIds, int maxHops) {
        if (kpIds == null || kpIds.isEmpty()) return Collections.emptyList();
        int hops = Math.max(1, Math.min(maxHops, 3));
        try {
            String cypher = buildPrerequisitesUpstreamCypher(hops);
            return new ArrayList<>(neo4jClient.query(cypher).bindAll(Map.of("ids", kpIds)).fetch().all());
        } catch (Exception e) {
            log.warn("查询 PREREQUISITE_OF 链失败: kpIds={}, maxHops={}, {}", kpIds, maxHops, e.getMessage());
            return Collections.emptyList();
        }
    }

    static String buildPrerequisitesUpstreamCypher(int maxHops) {
        int hops = Math.max(1, Math.min(maxHops, 3));
        return String.format(
                "MATCH path = (prerequisite:KnowledgePoint)-[:PREREQUISITE_OF*1..%d]->(weak:KnowledgePoint) " +
                "WHERE weak.id IN $ids " +
                "UNWIND relationships(path) AS relation " +
                "WITH DISTINCT startNode(relation) AS source, endNode(relation) AS target, relation " +
                "RETURN source.id AS sourceKpId, COALESCE(source.name, '未命名知识点') AS sourceKpName, " +
                "target.id AS targetKpId, COALESCE(target.name, '未命名知识点') AS targetKpName, " +
                "COALESCE(relation.strength, relation.weight, 1.0) AS strength " +
                "ORDER BY sourceKpId, targetKpId " +
                "LIMIT 200", hops);
    }

    // ======================== 学科列表查询 ========================

    /**
     * 查询图谱中所有不重复的学科（从 SubjectNode.name 聚合）。
     */
    public List<String> findDistinctSubjects() {
        try {
            return neo4jClient.query(
                    "MATCH (s:Subject) WHERE s.name IS NOT NULL " +
                    "RETURN DISTINCT s.name AS subject ORDER BY subject"
            ).fetch().all().stream()
                    .map(row -> (String) row.get("subject"))
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询学科列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
