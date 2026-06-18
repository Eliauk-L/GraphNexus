package com.graphnexus.application.graph.core.model;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;

/**
 * 图数据转换器 — 将 L3 Neo4j 实体转换为 L2 数据记录。
 *
 * <p>L2 服务和策略在返回数据给 L1 前，使用此工具将 L3 类型转换为 L2 类型，
 * 确保 L1 不直接依赖 L3。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
public final class GraphDataConverter {

    private GraphDataConverter() {}

    public static GraphNodeData toNodeData(GraphNode node) {
        return new GraphNodeData(
                node.getId(),
                node.getNodeType(),
                node.getDocumentId(),
                node.getCreatedAt(),
                node.toProperties()
        );
    }

    public static GraphEdgeData toEdgeData(GraphEdge edge) {
        return new GraphEdgeData(
                edge.getSourceNodeId(),
                edge.getTargetNodeId(),
                edge.getEdgeType(),
                edge.getWeight(),
                edge.getCreatedAt()
        );
    }
}