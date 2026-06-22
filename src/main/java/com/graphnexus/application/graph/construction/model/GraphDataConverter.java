package com.graphnexus.application.graph.construction.model;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;

import java.util.Map;

/**
 * 图数据转换器 — 将 L3 Neo4j 实体转换为 L2 数据记录。
 *
 * @author Jay
 * @date 2026/06/18
 */
public final class GraphDataConverter {

    private GraphDataConverter() {}

    public static GraphNodeData toNodeData(GraphNode node) {
        Map<String, Object> properties = node.getProperties() != null
                ? node.getProperties() : node.toProperties();
        String label = extractLabel(node, properties);
        return new GraphNodeData(
                node.getId(),
                node.getNodeType(),
                label,
                node.getDocumentId(),
                node.getCreatedAt(),
                properties
        );
    }

    /**
     * 从节点属性和类型中提取人类可读的显示名称。
     * 优先级：name > label > title > 类型名+id截断
     */
    private static String extractLabel(GraphNode node, Map<String, Object> properties) {
        // 通用属性名尝试
        for (String key : new String[]{"name", "label", "title"}) {
            Object val = properties.get(key);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        // 兜底：类型名 + id 前 8 位
        String id = node.getId();
        String idPart = id != null && id.length() > 8 ? id.substring(0, 8) : (id != null ? id : "");
        return node.getNodeType() + (idPart.isEmpty() ? "" : "-" + idPart);
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