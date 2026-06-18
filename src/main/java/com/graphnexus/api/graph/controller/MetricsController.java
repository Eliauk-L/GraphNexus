package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.metrics.MetricResultVO;
import com.graphnexus.api.graph.dto.metrics.MetricsQueryRequest;
import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.service.MetricsService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图指标查询 API 控制器（L1）— PageRank 与度中心性端点。
 *
 * @author Jay
 * @date 2026/06/17
 */
@RestController
@RequestMapping("/api/v1/graph/metrics")
@RequiredArgsConstructor
@Tag(name = "图指标", description = "Neo4j GDS 图算法 — PageRank 节点重要性与度中心性查询")
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * 查询 PageRank 值。
     */
    @Operation(summary = "查询 PageRank", description = "返回图中各节点的 PageRank 值（重要性排序）。可选按节点/边类型过滤投影范围。不传参 = 全图默认（所有节点+边类型）。GDS transient 命名图模式，计算后自动释放。结果 Caffeine 本地缓存（TTL 5 分钟），图谱变更后事件驱动自动清空缓存")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PageRank 结果列表"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / Neo4j GDS 计算失败")
    })
    @GetMapping("/pagerank")
    public ApiResult<List<MetricResultVO>> getPageRank(
            @Parameter(description = "节点类型标签列表（逗号分隔），如 KnowledgePoint,Student。空 = 全类型", example = "KnowledgePoint,Student")
            @RequestParam(required = false) List<String> nodeTypes,
            @Parameter(description = "边类型列表（逗号分隔），如 PREREQUISITE_OF,MASTERS。空 = 全类型", example = "PREREQUISITE_OF,MASTERS")
            @RequestParam(required = false) List<String> edgeTypes) {

        MetricsQueryRequest request = new MetricsQueryRequest();
        if (nodeTypes != null) request.setNodeTypes(nodeTypes);
        if (edgeTypes != null) request.setEdgeTypes(edgeTypes);

        List<MetricResultBO> results = metricsService.queryPageRank(
                request.nodeTypeSet(), request.edgeTypeSet());
        List<MetricResultVO> vos = results.stream()
                .map(MetricResultVO::from)
                .toList();
        return ApiResult.success(vos);
    }

    /**
     * 查询度中心性（含 inDegree + outDegree）。
     */
    @Operation(summary = "查询度中心性", description = "返回各节点的入度（inDegree）和出度（outDegree）值。可选过滤投影范围。理解：高入度节点 = 被大量边指向（如被大量实体 ALIGNED_TO 的 KP），高出度节点 = 广泛连接到其他节点。缓存策略同 PageRank")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "度中心性结果列表"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / Neo4j GDS 计算失败")
    })
    @GetMapping("/degree")
    public ApiResult<List<MetricResultVO>> getDegree(
            @Parameter(description = "节点类型标签列表（逗号分隔），如 KnowledgePoint。空 = 全类型", example = "KnowledgePoint")
            @RequestParam(required = false) List<String> nodeTypes,
            @Parameter(description = "边类型列表（逗号分隔）。空 = 全类型", example = "ALIGNED_TO,TESTED")
            @RequestParam(required = false) List<String> edgeTypes) {

        MetricsQueryRequest request = new MetricsQueryRequest();
        if (nodeTypes != null) request.setNodeTypes(nodeTypes);
        if (edgeTypes != null) request.setEdgeTypes(edgeTypes);

        List<MetricResultBO> results = metricsService.queryDegree(
                request.nodeTypeSet(), request.edgeTypeSet());
        List<MetricResultVO> vos = results.stream()
                .map(MetricResultVO::from)
                .toList();
        return ApiResult.success(vos);
    }
}