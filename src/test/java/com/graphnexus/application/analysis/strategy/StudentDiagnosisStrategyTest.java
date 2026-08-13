package com.graphnexus.application.analysis.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDiagnosisStrategyTest {

    @Mock
    private QueryGraphRepository queryGraphRepository;

    @Mock
    private ExamRecordRepository examRecordRepository;

    private StudentDiagnosisStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new StudentDiagnosisStrategy(
                queryGraphRepository, examRecordRepository, new ObjectMapper());
    }

    @Test
    void buildsRealPrerequisiteEdgesTowardWeakKnowledgePoint() {
        when(queryGraphRepository.findStudentByNo("S001")).thenReturn(Optional.of(mapOf(
                "id", "student-1", "studentNo", "S001", "name", "张三",
                "className", "九年级一班", "grade", "九年级")));
        when(queryGraphRepository.findMastersByStudentAndSubject("student-1", "数学"))
                .thenReturn(List.of(mapOf(
                        "kpId", "vertex", "kpName", "顶点坐标", "weight", 0.2)));
        when(queryGraphRepository.findPrerequisitesUpstream(List.of("vertex"), 2))
                .thenReturn(List.of(
                        edge("general", "二次函数一般式", "axis", "对称轴", 0.9),
                        edge("axis", "对称轴", "vertex", "顶点坐标", 0.95)));
        when(queryGraphRepository.findMastersByStudentAndKpIds(
                "student-1", List.of("general", "axis", "vertex")))
                .thenReturn(List.of());

        PrunedSubgraph result = strategy.prune(new PruningRequest(
                "STUDENT_DIAGNOSIS", "S001", "数学",
                Map.of("weakThreshold", 0.6, "maxHops", 2.0)));

        assertTrue(result.nodes().stream().anyMatch(node -> "general".equals(node.id())));
        assertTrue(result.nodes().stream().anyMatch(node -> "axis".equals(node.id())));
        assertTrue(result.nodes().stream().anyMatch(node -> "vertex".equals(node.id())));
        assertTrue(result.edges().stream().anyMatch(edge ->
                "general".equals(edge.sourceNodeId()) && "axis".equals(edge.targetNodeId())
                        && edge.weight() == 0.9));
        assertTrue(result.edges().stream().anyMatch(edge ->
                "axis".equals(edge.sourceNodeId()) && "vertex".equals(edge.targetNodeId())
                        && edge.weight() == 0.95));
        assertFalse(result.edges().stream().anyMatch(edge ->
                "vertex".equals(edge.sourceNodeId()) && "axis".equals(edge.targetNodeId())));
        assertEquals(2, result.edges().stream()
                .filter(edge -> "PREREQUISITE_OF".equals(edge.edgeType())).count());
    }

    @Test
    void selectsWeakestKnowledgePointsWithStableTopK() {
        when(queryGraphRepository.findStudentByNo("S001")).thenReturn(Optional.of(mapOf(
                "id", "student-1", "studentNo", "S001", "name", "张三",
                "className", "九年级一班", "grade", "九年级")));
        when(queryGraphRepository.findMastersByStudentAndSubject("student-1", "数学"))
                .thenReturn(List.of(
                        mapOf("kpId", "kp-c", "kpName", "C", "weight", 0.4),
                        mapOf("kpId", "kp-b", "kpName", "B", "weight", 0.2),
                        mapOf("kpId", "kp-a", "kpName", "A", "weight", 0.2)));
        when(queryGraphRepository.findPrerequisitesUpstream(List.of("kp-a", "kp-b"), 2))
                .thenReturn(List.of());

        PrunedSubgraph result = strategy.prune(new PruningRequest(
                "STUDENT_DIAGNOSIS", "S001", "数学",
                Map.of("weakThreshold", 0.6, "maxHops", 2.0, "topK", 2.0)));

        List<String> masteryTargets = result.edges().stream()
                .filter(edge -> "MASTERS".equals(edge.edgeType()))
                .map(edge -> edge.targetNodeId())
                .toList();
        assertEquals(List.of("kp-a", "kp-b"), masteryTargets);
        assertFalse(result.nodes().stream().anyMatch(node -> "kp-c".equals(node.id())));
    }

    private Map<String, Object> edge(String sourceId, String sourceName,
                                     String targetId, String targetName, double strength) {
        return mapOf("sourceKpId", sourceId, "sourceKpName", sourceName,
                "targetKpId", targetId, "targetKpName", targetName,
                "strength", strength);
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> values = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return values;
    }
}
