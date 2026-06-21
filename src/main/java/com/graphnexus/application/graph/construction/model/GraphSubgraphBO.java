package com.graphnexus.application.graph.construction.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 子图查询结果 BO — 包含文档关联的所有节点和边（L2 数据记录格式）。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Builder
public class GraphSubgraphBO {

    /** 节点列表（L2 GraphNodeData，已从 L3 GraphNode 转换） */
    private List<GraphNodeData> nodes;

    /** 边列表（L2 GraphEdgeData，已从 L3 GraphEdge 转换） */
    private List<GraphEdgeData> edges;
}