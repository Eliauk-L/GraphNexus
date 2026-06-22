package com.graphnexus.application.graph.metrics.service.impl;

import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.service.MetricsService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.neo4j.gds.GdsAdapter;
import com.graphnexus.infrastructure.neo4j.gds.config.MetricsProperties;
import com.graphnexus.infrastructure.neo4j.gds.model.GdsResult;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MetricsService 单元测试 — 中心节点约束 + 边类型收敛 + 缓存（Mock GdsAdapter）。
 *
 * @author Jay
 * @date 2026/06/21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService 测试")
class MetricsServiceTest {

    @Mock
    private GdsAdapter gdsAdapter;

    @Mock
    private ConstructionGraphRepository constructionGraphRepository;

    private MetricsService service;

    @BeforeEach
    void setUp() {
        MetricsProperties props = new MetricsProperties();
        props.setCache(new MetricsProperties.Cache(5, 50));
        props.setPageRank(new MetricsProperties.PageRank(20, 0.85));
        service = new MetricsServiceImpl(gdsAdapter, props, constructionGraphRepository);
        ((MetricsServiceImpl) service).initCache();
    }

    // ======================== 中心节点约束（核心新增） ========================

    @Test
    @DisplayName("nodeTypes 为空 → 拒绝（必须指定中心）")
    void testEmptyNodeTypes_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.queryPageRank(Set.of(), Set.of()));
        assertEquals("A0002", ex.getErrorCode());
    }

    @Test
    @DisplayName("nodeTypes 含非中心类型（Entity）→ 拒绝")
    void testNonCenterNodeType_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.queryPageRank(Set.of("Entity"), Set.of()));
        assertEquals("A0002", ex.getErrorCode());
    }

    @Test
    @DisplayName("nodeTypes 仅 Student（无 KP）→ 拒绝（MASTERS 边需 KP）")
    void testStudentOnly_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.queryPageRank(Set.of("Student"), Set.of()));
        assertEquals("A0002", ex.getErrorCode());
    }

    @Test
    @DisplayName("nodeTypes=KnowledgePoint → 通过，边自动收敛为 PREREQUISITE_OF")
    void testKnowledgePointCenter_Allowed() {
        when(gdsAdapter.projectGraph(any(), any())).thenReturn("g1");
        when(gdsAdapter.runPageRank("g1")).thenReturn(List.of(
                new GdsResult("kp-1", "KnowledgePoint", 0.5)));

        List<MetricResultBO> results = service.queryPageRank(Set.of("KnowledgePoint"), Set.of());
        assertEquals(1, results.size());

        // 验证投影时传入的边类型被收敛为 PREREQUISITE_OF
        verify(gdsAdapter).projectGraph(eq(Set.of("KnowledgePoint")), eq(Set.of("PREREQUISITE_OF")));
    }

    @Test
    @DisplayName("nodeTypes=KnowledgePoint+Student → 通过，边含 PREREQUISITE_OF+MASTERS")
    void testKpAndStudentCenter_Allowed() {
        when(gdsAdapter.projectGraph(any(), any())).thenReturn("g1");
        when(gdsAdapter.runPageRank("g1")).thenReturn(List.of());

        service.queryPageRank(Set.of("KnowledgePoint", "Student"), Set.of());
        verify(gdsAdapter).projectGraph(any(),
                argThat(edges -> edges.contains("PREREQUISITE_OF") && edges.contains("MASTERS")));
    }

    // ======================== 边类型收敛 ========================

    @Test
    @DisplayName("调用方传入的 edgeTypes 与允许集合取交集")
    void testEdgeTypeIntersection() {
        when(gdsAdapter.projectGraph(any(), any())).thenReturn("g1");
        when(gdsAdapter.runPageRank("g1")).thenReturn(List.of());

        // 调用方传 PREREQUISITE_OF + ALIGNED_TO，仅 PREREQUISITE_OF 保留
        service.queryPageRank(Set.of("KnowledgePoint"),
                Set.of("PREREQUISITE_OF", "ALIGNED_TO"));
        verify(gdsAdapter).projectGraph(eq(Set.of("KnowledgePoint")), eq(Set.of("PREREQUISITE_OF")));
    }

    @Test
    @DisplayName("调用方传入的 edgeTypes 与允许集合无交集 → 拒绝")
    void testEdgeTypeNoIntersection_Rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.queryPageRank(Set.of("KnowledgePoint"), Set.of("MASTERS")));
        assertEquals("A0002", ex.getErrorCode());
    }

    // ======================== 缓存 ========================

    @Test
    @DisplayName("同参数两次查询仅调用一次 GdsAdapter（缓存命中）")
    void testCache_Hit() {
        when(gdsAdapter.projectGraph(any(), any())).thenReturn("g1");
        when(gdsAdapter.runPageRank("g1")).thenReturn(List.of(
                new GdsResult("kp-1", "KnowledgePoint", 0.5)));

        Set<String> nodes = Set.of("KnowledgePoint");
        Set<String> edges = Set.of("PREREQUISITE_OF");

        service.queryPageRank(nodes, edges);
        service.queryPageRank(nodes, edges);

        verify(gdsAdapter, times(1)).projectGraph(any(), any());
    }

    @Test
    @DisplayName("clearCache 后重新触发 GDS 计算")
    void testClearCache_TriggersRecompute() {
        when(gdsAdapter.projectGraph(any(), any())).thenReturn("g1");
        when(gdsAdapter.runPageRank("g1")).thenReturn(List.of(
                new GdsResult("kp-1", "KnowledgePoint", 0.5)));

        Set<String> nodes = Set.of("KnowledgePoint");
        Set<String> edges = Set.of("PREREQUISITE_OF");

        service.queryPageRank(nodes, edges);
        service.clearCache();
        service.queryPageRank(nodes, edges);

        verify(gdsAdapter, times(2)).projectGraph(any(), any());
    }

    // ======================== Degree ========================

    @Test
    @DisplayName("queryDegree 返回 inDegree + outDegree 结果")
    void testQueryDegree_ReturnsBothDirections() {
        when(gdsAdapter.projectGraph(any(), any())).thenReturn("g1");
        when(gdsAdapter.runDegreeStream("g1", "NATURAL")).thenReturn(List.of(
                new GdsResult("kp-1", "KnowledgePoint", 3.0)));
        when(gdsAdapter.runDegreeStream("g1", "REVERSE")).thenReturn(List.of(
                new GdsResult("kp-1", "KnowledgePoint", 2.0)));

        List<MetricResultBO> results = service.queryDegree(Set.of("KnowledgePoint"), Set.of());
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> "outDegree".equals(r.metricName())));
        assertTrue(results.stream().anyMatch(r -> "inDegree".equals(r.metricName())));
    }
}