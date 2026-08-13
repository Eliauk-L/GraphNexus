package com.graphnexus.api.evaluation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GraphEvaluationResult;
import com.graphnexus.application.evaluation.graph.service.GraphEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GraphEvaluationControllerTest {

    private MockMvc mockMvc;
    private GraphEvaluationService service;

    @BeforeEach
    void setUp() {
        service = mock(GraphEvaluationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GraphEvaluationController(service)).build();
    }

    @Test
    void postsManualCandidateAndReturnsMetrics() throws Exception {
        when(service.evaluateManual(eq("pep-math-8down-v1"), any(CandidateGraph.class)))
                .thenReturn(result("graph-1"));

        mockMvc.perform(post("/api/v1/evaluations/graph/manual-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion":"pep-math-8down-v1",
                                  "topics":[{"tempId":"t1","name":"勾股定理"}],
                                  "dependencies":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphId").value("graph-1"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.metrics.goldTopicCount").value(19));
    }

    @Test
    void getsPersistedEvaluationGraph() throws Exception {
        when(service.getGraph("graph-1")).thenReturn(result("graph-1"));

        mockMvc.perform(get("/api/v1/evaluations/graph/graphs/graph-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphId").value("graph-1"))
                .andExpect(jsonPath("$.data.method").value("MANUAL"));
    }

    @Test
    void rejectsInvalidManualRequest() throws Exception {
        mockMvc.perform(post("/api/v1/evaluations/graph/manual-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(java.util.Map.of(
                                "datasetVersion", "", "topics", List.of(), "dependencies", List.of()))))
                .andExpect(status().isBadRequest());
    }

    private GraphEvaluationResult result(String graphId) {
        EvaluationMetrics metrics = new EvaluationMetrics(19, 1, 1,
                1, 1.0 / 19, 0.1, 17, 0, 0, 0, 0, 0);
        return new GraphEvaluationResult(graphId, "pep-math-8down-v1", "hash", "MANUAL", "COMPLETED",
                LocalDateTime.now(), metrics, new CandidateGraph(List.of(), List.of()), List.of());
    }
}
