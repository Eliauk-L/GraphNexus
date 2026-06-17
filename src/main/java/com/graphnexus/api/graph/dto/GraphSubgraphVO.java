package com.graphnexus.api.graph.dto;

import com.graphnexus.application.graph.model.GraphSubgraphBO;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 子图查询响应 VO — 含 nodes 和 edges 两个数组。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Schema(description = "文档子图视图")
public class GraphSubgraphVO {

    @Schema(description = "节点列表")
    private List<GraphNodeVO> nodes;

    @Schema(description = "边列表")
    private List<GraphEdgeVO> edges;

    public static GraphSubgraphVO from(GraphSubgraphBO bo) {
        GraphSubgraphVO vo = new GraphSubgraphVO();
        vo.setNodes(bo.getNodes().stream().map(GraphNodeVO::from).collect(Collectors.toList()));
        vo.setEdges(bo.getEdges().stream().map(GraphEdgeVO::from).collect(Collectors.toList()));
        return vo;
    }

    @Data
    @Schema(description = "图节点简略视图")
    public static class GraphNodeVO {
        @Schema(description = "节点 Neo4j elementId", example = "4:abc123:0")
        private String id;

        @Schema(description = "节点类型标签", example = "KnowledgePoint")
        private String nodeType;

        @Schema(description = "关联文档 ID（EntityNode 专属）", example = "1")
        private String documentId;

        @Schema(description = "节点创建时间", example = "2026-06-17T10:30:00")
        private String createdAt;

        public static GraphNodeVO from(GraphNode node) {
            GraphNodeVO vo = new GraphNodeVO();
            vo.setId(node.getId());
            vo.setNodeType(node.getNodeType());
            vo.setDocumentId(node.getDocumentId());
            vo.setCreatedAt(node.getCreatedAt() != null ? node.getCreatedAt().toString() : null);
            return vo;
        }
    }

    @Data
    @Schema(description = "图边简略视图")
    public static class GraphEdgeVO {
        @Schema(description = "源节点 elementId", example = "4:abc123:0")
        private String sourceNodeId;

        @Schema(description = "目标节点 elementId", example = "4:def456:1")
        private String targetNodeId;

        @Schema(description = "边类型", example = "PREREQUISITE_OF")
        private String edgeType;

        @Schema(description = "边创建时间", example = "2026-06-17T10:30:00")
        private String createdAt;

        public static GraphEdgeVO from(GraphEdge edge) {
            GraphEdgeVO vo = new GraphEdgeVO();
            vo.setSourceNodeId(edge.getSourceNodeId());
            vo.setTargetNodeId(edge.getTargetNodeId());
            vo.setEdgeType(edge.getEdgeType());
            vo.setCreatedAt(edge.getCreatedAt() != null ? edge.getCreatedAt().toString() : null);
            return vo;
        }
    }
}