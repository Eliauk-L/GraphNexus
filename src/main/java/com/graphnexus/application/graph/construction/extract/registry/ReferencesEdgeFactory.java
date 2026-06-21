package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.edge.ReferencesEdge;
import org.springframework.stereotype.Component;

/**
 * REFERENCES 关系边工厂（D3）。
 *
 * @author Jay
 * @date 2026/06/21
 */
@Component
public class ReferencesEdgeFactory implements ExtractionEdgeFactory {

    @Override
    public EntityRelationType relationType() {
        return EntityRelationType.REFERENCES;
    }

    @Override
    public GraphEdge create(String sourceNodeId, String targetNodeId, String description) {
        return new ReferencesEdge(sourceNodeId, targetNodeId, description);
    }
}
