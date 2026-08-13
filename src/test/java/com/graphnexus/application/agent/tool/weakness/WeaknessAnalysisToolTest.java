package com.graphnexus.application.agent.tool.weakness;

import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.analysis.model.PrunedSubgraph.PruningMeta;
import com.graphnexus.application.analysis.strategy.StudentDiagnosisStrategy;
import com.graphnexus.application.graph.construction.model.GraphEdgeData;
import com.graphnexus.application.graph.construction.model.GraphNodeData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeaknessAnalysisToolTest {
    @Test
    void convertsPrunedGraphIntoWeakPointsAndRootCauses() {
        StudentDiagnosisStrategy strategy = mock(StudentDiagnosisStrategy.class);
        when(strategy.prune(any())).thenReturn(new PrunedSubgraph(
                List.of(node("pre", "对称轴", 0.4), node("weak", "顶点坐标", 0.2)),
                List.of(new GraphEdgeData("student", "weak", "MASTERS", 0.2, null),
                        new GraphEdgeData("pre", "weak", "PREREQUISITE_OF", 0.95, null)),
                new PruningMeta("STUDENT_DIAGNOSIS", true, 0.6, 2, 2, 2, false, List.of())));

        var result = new WeaknessAnalysisTool(strategy).execute(
                new WeaknessAnalysisInput("S001", "数学", 0.6, 2, 10), context(Set.of("S001")));

        assertTrue(result.success());
        assertEquals("顶点坐标", result.data().weakPoints().get(0).name());
        assertEquals("对称轴", result.data().rootCauses().get(0).name());
        assertEquals(1, result.data().prerequisiteEdges().size());
    }

    @Test
    void enforcesStudentScope() {
        var result = new WeaknessAnalysisTool(mock(StudentDiagnosisStrategy.class)).execute(
                new WeaknessAnalysisInput("S002", "数学", null, null, null), context(Set.of("S001")));
        assertFalse(result.success());
        assertEquals("STUDENT_SCOPE_DENIED", result.errorCode());
    }

    private GraphNodeData node(String id, String name, double weight) {
        return new GraphNodeData(id, "KnowledgePoint", name, null, null,
                new java.util.HashMap<>(Map.of("name", name, "weight", weight)));
    }

    private ToolExecutionContext context(Set<String> scope) {
        return new ToolExecutionContext("task", "teacher", Set.of("TEACHER"), scope, Instant.MAX, "trace");
    }
}
