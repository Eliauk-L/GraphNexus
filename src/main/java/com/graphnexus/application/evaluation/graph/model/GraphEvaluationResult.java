package com.graphnexus.application.evaluation.graph.model;

import java.time.LocalDateTime;
import java.util.List;

/** Completed manual evaluation and the isolated graph persisted in Neo4j. */
public record GraphEvaluationResult(
        String graphId,
        String datasetVersion,
        String datasetHash,
        String method,
        String status,
        LocalDateTime createdAt,
        EvaluationMetrics metrics,
        CandidateGraph graph,
        List<String> warnings
) {
    public GraphEvaluationResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
