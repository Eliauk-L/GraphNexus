package com.graphnexus.api.evaluation.controller;

import com.graphnexus.application.auth.service.TokenService;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GraphEvaluationResult;
import com.graphnexus.application.evaluation.graph.service.GraphEvaluationService;
import com.graphnexus.common.config.SecurityConfig;
import com.graphnexus.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GraphEvaluationController.class)
@Import(SecurityConfig.class)
class GraphEvaluationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GraphEvaluationService graphEvaluationService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private TokenService tokenService;

    @Test
    void manualEvaluationAllowsRequestWithoutJwt() throws Exception {
        EvaluationMetrics metrics = new EvaluationMetrics(19, 0, 0, 0, 0, 0,
                17, 0, 0, 0, 0, 0);
        when(graphEvaluationService.evaluateManual(eq("pep-math-8down-v1"), any(CandidateGraph.class)))
                .thenReturn(new GraphEvaluationResult("graph-public", "pep-math-8down-v1", "hash",
                        "MANUAL", "COMPLETED", LocalDateTime.now(), metrics,
                        new CandidateGraph(List.of(), List.of()), List.of()));

        mockMvc.perform(post("/api/v1/evaluations/graph/manual-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion":"pep-math-8down-v1",
                                  "topics":[],
                                  "dependencies":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphId").value("graph-public"));
    }
}
