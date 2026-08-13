package com.graphnexus.application.agent.tool.recommendation;

import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import com.graphnexus.application.agent.tool.weakness.WeaknessAnalysisResult;
import com.graphnexus.application.agent.tool.weakness.WeaknessAnalysisTool;
import com.graphnexus.application.graph.construction.model.GraphEdgeData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningPathRecommendationToolTest {
    @Test
    void ordersPrerequisitesBeforeWeakTarget() {
        WeaknessAnalysisTool weakness = mock(WeaknessAnalysisTool.class);
        when(weakness.execute(any(), any())).thenReturn(ToolResult.success(
                analysis(List.of(edge("definition", "axis"), edge("axis", "vertex"))),
                List.of(), 1, 3));

        var result = new LearningPathRecommendationTool(weakness).execute(
                new LearningPathInput("S001", "数学", List.of("vertex"), 30, 3), context());

        assertTrue(result.success());
        assertEquals(List.of("definition", "axis", "vertex"), result.data().steps().stream()
                .map(step -> step.knowledgePointId()).toList());
        assertEquals(90, result.data().totalMinutes());
    }

    @Test
    void rejectsCyclicPrerequisiteGraph() {
        WeaknessAnalysisTool weakness = mock(WeaknessAnalysisTool.class);
        when(weakness.execute(any(), any())).thenReturn(ToolResult.success(
                analysis(List.of(edge("axis", "vertex"), edge("vertex", "axis"))),
                List.of(), 1, 2));

        var result = new LearningPathRecommendationTool(weakness).execute(
                new LearningPathInput("S001", "数学", List.of(), 30, 2), context());
        assertFalse(result.success());
        assertEquals("PREREQUISITE_CYCLE", result.errorCode());
    }

    private WeaknessAnalysisResult analysis(List<GraphEdgeData> edges) {
        return new WeaknessAnalysisResult(
                List.of(new WeaknessAnalysisResult.WeakPoint("vertex", "顶点坐标", 0.2, 0.8)),
                List.of(new WeaknessAnalysisResult.RootCause("definition", "二次函数定义", 0.7, 1),
                        new WeaknessAnalysisResult.RootCause("axis", "对称轴", 0.4, 1)),
                edges, true);
    }

    private GraphEdgeData edge(String source, String target) {
        return new GraphEdgeData(source, target, "PREREQUISITE_OF", 0.9, null);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("task", "teacher", Set.of("TEACHER"),
                Set.of("S001"), Instant.MAX, "trace");
    }
}
