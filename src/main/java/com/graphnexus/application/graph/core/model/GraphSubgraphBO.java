package com.graphnexus.application.graph.core.model;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 子图查询结果 BO — 包含文档关联的所有节点和边。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Builder
public class GraphSubgraphBO {

    /** 节点列表（各类型混合，通过 nodeType 区分） */
    private List<GraphNode> nodes;

    /** 边列表 */
    private List<GraphEdge> edges;
}