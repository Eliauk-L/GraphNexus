package com.graphnexus.api.analysis.controller;

import com.graphnexus.api.analysis.dto.SubgraphResponse;
import com.graphnexus.api.analysis.dto.SubgraphResponse.*;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.query.chat.service.QueryService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "图分析", description = "剪枝子图可视化数据查询（含节点、边、剪枝元信息）")
public class AnalysisController {

    private final QueryService queryService;

    /**
     * 查询剪枝子图数据。
     */
    @Operation(summary = "查询剪枝子图", description = "返回指定问答任务剪枝出的子图完整数据：节点列表（id/type/properties）+ 边列表（src→dst/type/weight）+ 剪枝元信息（策略名/阈值/跳数/是否截断）。供前端图可视化渲染，不含 LLM 分析结论。与智能问答端点（/query/*）配合使用：先问答获取结论，再用此端点获取图结构数据渲染")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "子图数据（节点 + 边 + 剪枝元信息）"),
            @ApiResponse(responseCode = "404", description = "A0021 任务不存在或已过期"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/subgraph/{taskId}")
    public ApiResult<SubgraphResponse> getSubgraph(
            @Parameter(description = "问答任务 ID（UUID 格式）", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String taskId) {
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

        return ApiResult.success(new SubgraphResponse(taskId, "OK", nodes, edges, meta));
    }
}