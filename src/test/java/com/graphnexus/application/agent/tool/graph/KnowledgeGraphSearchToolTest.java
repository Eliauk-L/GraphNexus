package com.graphnexus.application.agent.tool.graph;

import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeGraphSearchToolTest {
    @Test
    void returnsStructuredKnowledgeGraphEvidence() {
        QueryGraphRepository repository = mock(QueryGraphRepository.class);
        HashMap<String, Object> row = new HashMap<>();
        row.put("kpId", "kp-vertex"); row.put("kpName", "顶点坐标");
        row.put("description", "二次函数顶点的位置"); row.put("documentId", 12L);
        row.put("score", 1.0); row.put("prerequisites", List.of("对称轴"));
        row.put("dependents", List.of("综合应用"));
        when(repository.searchKnowledgePoints("顶点坐标", "数学", 5)).thenReturn(List.of(row));

        var result = new KnowledgeGraphSearchTool(repository).execute(
                new KnowledgeGraphSearchInput("顶点坐标", "数学", 5, 2), context());

        assertTrue(result.success());
        assertEquals("顶点坐标", result.data().get(0).name());
        assertEquals(List.of("对称轴"), result.data().get(0).prerequisites());
        assertEquals("KNOWLEDGE_POINT", result.evidence().get(0).type());
    }

    @Test
    void rejectsMissingQuery() {
        var result = new KnowledgeGraphSearchTool(mock(QueryGraphRepository.class)).execute(
                new KnowledgeGraphSearchInput("", "数学", 5, 2), context());
        assertFalse(result.success());
        assertEquals("INVALID_ARGUMENT", result.errorCode());
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("task", "teacher", Set.of("TEACHER"), Set.of(), Instant.MAX, "trace");
    }
}
