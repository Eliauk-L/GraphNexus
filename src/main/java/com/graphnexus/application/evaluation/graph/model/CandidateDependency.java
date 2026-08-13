package com.graphnexus.application.evaluation.graph.model;

/** Directed edge: prerequisiteTempId -> topicTempId. */
public record CandidateDependency(
        String prerequisiteTempId,
        String topicTempId,
        String strength
) {
}
