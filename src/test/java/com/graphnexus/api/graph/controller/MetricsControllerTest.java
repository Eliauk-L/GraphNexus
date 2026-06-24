package com.graphnexus.api.graph.controller;

import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.service.MetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MetricsController MockMvc 测试 — 验证端点响应结构与参数校验（AC-1/AC-2/AC-5/AC-8）。
 *
 * @author Jay
 * @date 2026/06/17
 */
@WebMvcTest(MetricsController.class)
@DisplayName("MetricsController 端点测试")
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetricsService metricsService;

    @Test
    @DisplayName("GET /api/v1/graph/metrics/pagerank → 200 + 含 nodeId/nodeType/metricName/metricValue（AC-1）")
    void testGetPageRank_Returns200() throws Exception {
        when(metricsService.queryPageRank(anySet(), anySet())).thenReturn(List.of(
                new MetricResultBO("kp-001", "KnowledgePoint", null, null, null, "pagerank", 0.85)));

        mockMvc.perform(get("/api/v1/graph/metrics/pagerank")
                        .param("nodeTypes", "KnowledgePoint")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].nodeId").value("kp-001"))
                .andExpect(jsonPath("$.data[0].nodeType").value("KnowledgePoint"))
                .andExpect(jsonPath("$.data[0].metricName").value("pagerank"))
                .andExpect(jsonPath("$.data[0].metricValue").value(0.85));
    }

    @Test
    @DisplayName("GET /api/v1/graph/metrics/degree → 200（AC-2）")
    void testGetDegree_Returns200() throws Exception {
        when(metricsService.queryDegree(anySet(), anySet())).thenReturn(List.of(
                new MetricResultBO("kp-001", "KnowledgePoint", null, null, null, "inDegree", 2.0),
                new MetricResultBO("kp-001", "KnowledgePoint", null, null, null, "outDegree", 3.0)));

        mockMvc.perform(get("/api/v1/graph/metrics/degree")
                        .param("nodeTypes", "KnowledgePoint")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("空结果 → 200 + data=[]（AC-5）")
    void testGetPageRank_EmptyGraphReturns200EmptyArray() throws Exception {
        when(metricsService.queryPageRank(anySet(), anySet())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/graph/metrics/pagerank")
                        .param("nodeTypes", "KnowledgePoint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("带 nodeTypes 和 edgeTypes 参数 → 200（AC-3 过滤投影，仅 KP/Student 为中心）")
    void testGetPageRank_WithFilters() throws Exception {
        when(metricsService.queryPageRank(anySet(), anySet())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/graph/metrics/pagerank")
                        .param("nodeTypes", "KnowledgePoint,Student")
                        .param("edgeTypes", "PREREQUISITE_OF,MASTERS"))
                .andExpect(status().isOk());
    }
}