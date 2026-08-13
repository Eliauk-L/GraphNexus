package com.graphnexus.application.evaluation.graph.model;

import java.util.List;

/** Candidate graph after common cleanup, with auditable difference counters. */
public record CandidateGraphNormalizationResult(
        CandidateGraph normalizedGraph,
        int removedEmptyTopics,
        int mergedDuplicateTopics,
        int removedSelfLoops,
        int removedDuplicateEdges,
        int removedDanglingEdges,
        List<String> warnings
) {
    public CandidateGraphNormalizationResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
