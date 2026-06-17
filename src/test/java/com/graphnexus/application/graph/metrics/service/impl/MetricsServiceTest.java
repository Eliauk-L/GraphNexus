package com.graphnexus.application.graph.metrics.service.impl;

import com.graphnexus.application.graph.metrics.config.MetricsProperties;
import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.model.MetricsQuery;
import com.graphnexus.application.graph.metrics.service.MetricsService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.neo4j.gds.GdsAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MetricsService 单元测试 — 缓存 + 参数校验 + 调度（Mock GdsAdapter）。
 *
 * @author Jay
 * @date 2026/06/17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsService 测试")
class MetricsServiceTest {

    @Mock
    private GdsAdapter gdsAdapter;

    private MetricsService service;

    @BeforeEach
    void setUp() {
        MetricsProperties props = new MetricsProperties();
        // 确保默认值就绪
        MetricsProperties.Cache cache = new MetricsProperties.Cache(5, 50);
        props.setCache(cache);
        props.setPageRank(new MetricsProperties.PageRank(20, 0.85));

        service = new MetricsServiceImpl(gdsAdapter, props);
        ((MetricsServiceImpl) service).initCache();
    }

    // ======================== 缓存 ========================

    @Test
    @DisplayName("同参数两次查询仅调用一次 GdsAdapter（AC-7 幂等性 / 缓存命中）")
    void testCache_Hit() {
        when(gdsAdapter.calculate(any())).thenReturn(List.of(
                new MetricResultBO("kp-1", "KnowledgePoint", "pagerank", 0.5)));

        Set<String> nodeTypes = Set.of("KnowledgePoint");
        Set<String> edgeTypes = Set.of();

        List<MetricResultBO> r1 = service.queryPageRank(nodeTypes, edgeTypes);
        List<MetricResultBO> r2 = service.queryPageRank(nodeTypes, edgeTypes);

        assertEquals(r1.size(), r2.size());
        assertEquals(r1.get(0).metricValue(), r2.get(0).metricValue());
        verify(gdsAdapter, times(1)).calculate(any());
    }

    @Test
    @DisplayName("clearCache 后重新触发 GDS 计算（AC-6 缓存失效）")
    void testClearCache_TriggersRecompute() {
        when(gdsAdapter.calculate(any())).thenReturn(List.of(
                new MetricResultBO("kp-1", "KnowledgePoint", "pagerank", 0.5)));

        Set<String> nodeTypes = Set.of("KnowledgePoint");
        Set<String> edgeTypes = Set.of();

        service.queryPageRank(nodeTypes, edgeTypes);
        service.clearCache();
        service.queryPageRank(nodeTypes, edgeTypes);

        // 两次：第一次 miss + clear 后第二次 miss
        verify(gdsAdapter, times(2)).calculate(any());
    }

    @Test
    @DisplayName("不同参数产生不同缓存 key（AC-4 多边组合）")
    void testCache_DifferentKeys() {
        when(gdsAdapter.calculate(any())).thenReturn(Collections.emptyList());

        service.queryPageRank(Set.of("KnowledgePoint"), Set.of("PREREQUISITE_OF"));
        service.queryPageRank(Set.of("KnowledgePoint"), Set.of("ALIGNED_TO"));

        verify(gdsAdapter, times(2)).calculate(any());
    }

    // ======================== 参数校验 ========================

    @Test
    @DisplayName("无效 nodeType → BusinessException（AC-8）")
    void testValidate_InvalidNodeType() {
        assertThrows(BusinessException.class, () ->
                service.queryPageRank(Set.of("InvalidType"), Set.of()));
    }

    @Test
    @DisplayName("无效 edgeType → BusinessException（AC-8）")
    void testValidate_InvalidEdgeType() {
        assertThrows(BusinessException.class, () ->
                service.queryPageRank(Set.of("KnowledgePoint"), Set.of("INVALID_EDGE")));
    }

    @Test
    @DisplayName("空参数不抛异常（全图默认）")
    void testValidate_EmptyParamsOk() {
        when(gdsAdapter.calculate(any())).thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> service.queryPageRank(Collections.emptySet(), Collections.emptySet()));
    }

    // ======================== Degree ========================

    @Test
    @DisplayName("queryDegree 返回 inDegree + outDegree 结果（AC-2）")
    void testQueryDegree_ReturnsBothDirections() {
        when(gdsAdapter.calculate(any())).thenReturn(List.of(
                new MetricResultBO("kp-1", "KnowledgePoint", "outDegree", 3.0)));

        List<MetricResultBO> results = service.queryDegree(Set.of(), Set.of());
        // queryDegree 调用了 calculate 两次（inDegree + outDegree），
        // mock 返回相同结果，总共 2 条（2 × 1 record each）
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> "outDegree".equals(r.metricName())));
    }
}