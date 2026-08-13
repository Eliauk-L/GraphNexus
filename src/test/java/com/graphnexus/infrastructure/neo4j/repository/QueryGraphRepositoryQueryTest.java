package com.graphnexus.infrastructure.neo4j.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryGraphRepositoryQueryTest {

    @Test
    void upstreamQueryEndsAtWeakNodeAndReturnsRealEdges() {
        String cypher = QueryGraphRepository.buildPrerequisitesUpstreamCypher(2);

        assertTrue(cypher.contains(
                "(prerequisite:KnowledgePoint)-[:PREREQUISITE_OF*1..2]->(weak:KnowledgePoint)"));
        assertTrue(cypher.contains("WHERE weak.id IN $ids"));
        assertTrue(cypher.contains("UNWIND relationships(path) AS relation"));
        assertTrue(cypher.contains("startNode(relation) AS source"));
        assertTrue(cypher.contains("endNode(relation) AS target"));
        assertFalse(cypher.contains("(weak:KnowledgePoint)-[:PREREQUISITE_OF"));
    }

    @Test
    void upstreamQueryClampsHopCount() {
        assertTrue(QueryGraphRepository.buildPrerequisitesUpstreamCypher(0)
                .contains("PREREQUISITE_OF*1..1"));
        assertTrue(QueryGraphRepository.buildPrerequisitesUpstreamCypher(99)
                .contains("PREREQUISITE_OF*1..3"));
    }
}
