package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.infrastructure.neo4j.edge.ContainsEdge;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import org.springframework.stereotype.Component;

/**
 * CONTAINS 关系边工厂（D3）。
 *
 * @author Jay
 * @date 2026/06/21
 */
@Component
public class ContainsEdgeFactory implements ExtractionEdgeFactory {

    @Override
    public EntityRelationType relationType() {
        return EntityRelationType.CONTAINS;
    }

    @Override
    public GraphEdge create(String sourceNodeId, String targetNodeId, String description) {
        return new ContainsEdge(sourceNodeId, targetNodeId, description);
    }
}
