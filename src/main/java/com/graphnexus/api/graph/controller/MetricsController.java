package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.MetricResultVO;
import com.graphnexus.api.graph.dto.MetricsQueryRequest;
import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.service.MetricsService;
import com.graphnexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图指标查询 API 控制器（L1）— PageRank 与度中心性端点。
 *
 * <p>端点映射（见 DESIGN §2）：</p>
 * <pre>
 *   GET /api/v1/graph/metrics/pagerank?nodeTypes=...&edgeTypes=...
 *   GET /api/v1/graph/metrics/degree?nodeTypes=...&edgeTypes=...
 * </pre>
 *
 * @author Jay
 * @date 2026/06/17
 */
@RestController
@RequestMapping("/api/v1/graph/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * 查询 PageRank 值。
     *
     * <p>不传参数 = 全图默认（所有节点类型 + 所有边类型）。</p>
     */
    @GetMapping("/pagerank")
    public ApiResponse<List<MetricResultVO>> getPageRank(
            @RequestParam(required = false) List<String> nodeTypes,
            @RequestParam(required = false) List<String> edgeTypes) {

        MetricsQueryRequest request = new MetricsQueryRequest();
        if (nodeTypes != null) request.setNodeTypes(nodeTypes);
        if (edgeTypes != null) request.setEdgeTypes(edgeTypes);

        List<MetricResultBO> results = metricsService.queryPageRank(
                request.nodeTypeSet(), request.edgeTypeSet());
        List<MetricResultVO> vos = results.stream()
                .map(MetricResultVO::from)
                .toList();
        return ApiResponse.success(vos);
    }

    /**
     * 查询度中心性（含 inDegree + outDegree）。
     *
     * <p>不传参数 = 全图默认。</p>
     */
    @GetMapping("/degree")
    public ApiResponse<List<MetricResultVO>> getDegree(
            @RequestParam(required = false) List<String> nodeTypes,
            @RequestParam(required = false) List<String> edgeTypes) {

        MetricsQueryRequest request = new MetricsQueryRequest();
        if (nodeTypes != null) request.setNodeTypes(nodeTypes);
        if (edgeTypes != null) request.setEdgeTypes(edgeTypes);

        List<MetricResultBO> results = metricsService.queryDegree(
                request.nodeTypeSet(), request.edgeTypeSet());
        List<MetricResultVO> vos = results.stream()
                .map(MetricResultVO::from)
                .toList();
        return ApiResponse.success(vos);
    }
}