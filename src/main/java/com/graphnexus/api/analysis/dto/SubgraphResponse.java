package com.graphnexus.api.analysis.dto;

import java.util.List;
import java.util.Map;

/**
 * 剪枝子图响应 VO — 包含节点、边及剪枝元信息，供前端图可视化渲染。
 *
 * @author Jay
 * @date 2026/06/17
 */
public record SubgraphResponse(
        String taskId,
        String status,
        List<NodeVO> nodes,
        List<EdgeVO> edges,
        PruningMetaVO pruningMeta
) {
    public record NodeVO(String id, String nodeType, Map<String, Object> properties) {}
    public record EdgeVO(String sourceNodeId, String targetNodeId, String edgeType, Double weight) {}
    public record PruningMetaVO(
            String strategy, boolean mastersAvailable, double weakThreshold,
            int maxHops, int totalNodes, int totalEdges,
            boolean truncated, List<String> truncatedNodeNames
    ) {}
}