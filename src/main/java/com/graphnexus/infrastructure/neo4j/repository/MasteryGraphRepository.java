package com.graphnexus.infrastructure.neo4j.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** Neo4j 中 MASTERS 最新状态的读写仓库。 */
@Repository
@RequiredArgsConstructor
public class MasteryGraphRepository {
    private final Neo4jClient neo4jClient;

    public Optional<String> findStudentNodeId(String studentNo) {
        return neo4jClient.query(
                "MATCH (s:Student {studentNo: $studentNo}) RETURN s.id AS id")
                .bindAll(Map.of("studentNo", studentNo)).fetch().all().stream()
                .map(row -> (String) row.get("id")).findFirst();
    }

    public Optional<String> findKnowledgePointId(String name, String subject) {
        return neo4jClient.query(
                "MATCH (kp:KnowledgePoint {name: $name})-[:BELONGS_TO_SUBJECT]->(:Subject {name: $subject}) "
                        + "RETURN kp.id AS id ORDER BY kp.id LIMIT 1")
                .bindAll(Map.of("name", name, "subject", subject)).fetch().all().stream()
                .map(row -> (String) row.get("id")).findFirst();
    }

    public void upsert(String studentNodeId, String knowledgePointId,
                       double weight, int sampleCount, double confidence,
                       String examNo, LocalDate examDate,
                       String description) {
        neo4jClient.query(
                "MATCH (s:Student {id: $studentId}), (kp:KnowledgePoint {id: $kpId}) "
                        + "MERGE (s)-[r:MASTERS]->(kp) "
                        + "SET r.weight = $weight, r.sampleCount = $sampleCount, "
                        + "r.confidence = $confidence, r.lastExamNo = $examNo, "
                        + "r.lastExamDate = $examDate, r.updatedAt = datetime(), "
                        + "r.version = coalesce(r.version, 0) + 1, r.description = $description, "
                        + "r.edgeType = 'MASTERS'")
                .bindAll(Map.of(
                        "studentId", studentNodeId,
                        "kpId", knowledgePointId,
                        "weight", weight,
                        "sampleCount", sampleCount,
                        "confidence", confidence,
                        "examNo", examNo,
                        "examDate", examDate.toString(),
                        "description", description))
                .run();
    }

    public void delete(String studentNodeId, String knowledgePointId) {
        neo4jClient.query(
                "MATCH (s:Student {id: $studentId})-[r:MASTERS]->(kp:KnowledgePoint {id: $kpId}) DELETE r")
                .bindAll(Map.of("studentId", studentNodeId, "kpId", knowledgePointId)).run();
    }
}
