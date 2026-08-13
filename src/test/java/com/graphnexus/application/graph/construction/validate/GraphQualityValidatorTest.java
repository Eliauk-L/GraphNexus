package com.graphnexus.application.graph.construction.validate;

import com.graphnexus.infrastructure.neo4j.edge.PrerequisiteEdge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphQualityValidatorTest {
    private final GraphQualityValidator validator = new GraphQualityValidator();

    @Test
    void isolatesSelfLoopDuplicateInvalidEndpointWeightAndCycle() {
        var result = validator.validate(List.of(
                edge("a", "b", 0.9),
                edge("b", "c", 0.8),
                edge("a", "b", 0.9),
                edge("c", "a", 0.7),
                edge("a", "a", 0.5),
                edge("a", "missing", 0.5),
                edge("a", "c", 1.2)
        ), Set.of("a", "b", "c"));

        assertEquals(2, result.accepted().size());
        assertEquals(List.of("DUPLICATE_EDGE", "PREREQUISITE_CYCLE", "SELF_LOOP",
                        "INVALID_ENDPOINT", "WEIGHT_OUT_OF_RANGE"),
                result.rejected().stream().map(GraphQualityValidator.RejectedEdge::reason).toList());
    }

    @Test
    void preservesAcyclicPrerequisiteOrder() {
        var result = validator.validate(List.of(edge("general", "axis", 0.9),
                edge("axis", "vertex", 0.95)), Set.of("general", "axis", "vertex"));

        assertEquals(2, result.accepted().size());
        assertEquals(0, result.rejected().size());
    }

    private PrerequisiteEdge edge(String source, String target, double strength) {
        return new PrerequisiteEdge(source, target, strength, "test");
    }
}
