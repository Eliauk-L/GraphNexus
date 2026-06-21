package com.graphnexus.application.analysis.model;

import com.graphnexus.application.graph.construction.model.GraphEdgeData;
import com.graphnexus.application.graph.construction.model.GraphNodeData;

import java.util.List;

/**
 * 剪枝后的子图 BO — 包含节点列表（L2 GraphNodeData）、边列表（L2 GraphEdgeData）及剪枝元信息。
 *
 * <p>由 {@link com.graphnexus.application.analysis.strategy.SubgraphPruningStrategy#prune(PruningRequest)} 产出，
 * 用于序列化为 LLM Prompt 文本或通过 API 返回前端渲染。</p>
 *
 * @param nodes 子图节点列表（L2 GraphNodeData，按优先级排序）
 * @param edges 子图边列表（L2 GraphEdgeData）
 * @param meta  剪枝元信息
 * @author Jay
 * @date 2026/06/17
 */
public record PrunedSubgraph(
        List<GraphNodeData> nodes,
        List<GraphEdgeData> edges,
        PrunedSubgraph.PruningMeta meta
) {

    /**
     * 剪枝元信息 — 记录本次剪枝的策略名、参数、结果统计。
     *
     * @param strategy           剪枝策略名称（如 "STUDENT_DIAGNOSIS"）
     * @param mastersAvailable   MASTERS 边是否可用（false 表示走了降级路径）
     * @param weakThreshold      弱掌握度阈值
     * @param maxHops            PREREQUISITE_OF 最大遍历跳数
     * @param totalNodes         子图节点总数
     * @param totalEdges         子图边总数
     * @param truncated          是否因 Token 预算触发截断
     * @param truncatedNodeNames 被截断省略的节点名称列表
     */
    public record PruningMeta(
            String strategy,
            boolean mastersAvailable,
            double weakThreshold,
            int maxHops,
            int totalNodes,
            int totalEdges,
            boolean truncated,
            List<String> truncatedNodeNames
    ) {}
}