package com.graphnexus.api.graph.dto;

import com.graphnexus.application.graph.model.GraphSubgraphBO;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
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
public class GraphSubgraphVO {

    private List<GraphNodeVO> nodes;
    private List<GraphEdgeVO> edges;

    public static GraphSubgraphVO from(GraphSubgraphBO bo) {
        GraphSubgraphVO vo = new GraphSubgraphVO();
        vo.setNodes(bo.getNodes().stream().map(GraphNodeVO::from).collect(Collectors.toList()));
        vo.setEdges(bo.getEdges().stream().map(GraphEdgeVO::from).collect(Collectors.toList()));
        return vo;
    }

    @Data
    public static class GraphNodeVO {
        private String id;
        private String nodeType;
        private String documentId;
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
    public static class GraphEdgeVO {
        private String sourceNodeId;
        private String targetNodeId;
        private String edgeType;
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