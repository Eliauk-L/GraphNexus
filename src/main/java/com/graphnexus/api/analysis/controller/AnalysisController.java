package com.graphnexus.api.analysis.controller;

import com.graphnexus.api.analysis.dto.SubgraphResponse;
import com.graphnexus.api.analysis.dto.SubgraphResponse.*;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.query.service.QueryService;
import com.graphnexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * 图分析 REST API 控制器 — 子图查询等分析端点。
 *
 * @author Jay
 * @date 2026/06/17
 */
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final QueryService queryService;

    /**
     * 查询剪枝子图数据。
     */
    @GetMapping("/subgraph/{taskId}")
    public ApiResponse<SubgraphResponse> getSubgraph(@PathVariable String taskId) {
        PrunedSubgraph subgraph = queryService.getSubgraph(taskId);

        var nodes = subgraph.nodes().stream()
                .map(n -> new NodeVO(n.getId(), n.getNodeType(), n.toProperties()))
                .collect(Collectors.toList());
        var edges = subgraph.edges().stream()
                .map(e -> new EdgeVO(e.getSourceNodeId(), e.getTargetNodeId(), e.getEdgeType(), e.getWeight()))
                .collect(Collectors.toList());
        var meta = new PruningMetaVO(
                subgraph.meta().strategy(), subgraph.meta().mastersAvailable(),
                subgraph.meta().weakThreshold(), subgraph.meta().maxHops(),
                subgraph.meta().totalNodes(), subgraph.meta().totalEdges(),
                subgraph.meta().truncated(), subgraph.meta().truncatedNodeNames()
        );

        return ApiResponse.success(new SubgraphResponse(taskId, "OK", nodes, edges, meta));
    }
}