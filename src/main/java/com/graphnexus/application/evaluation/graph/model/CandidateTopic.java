package com.graphnexus.application.evaluation.graph.model;

/** A topic produced by a graph construction method. */
public record CandidateTopic(
        String tempId,
        String name,
        String type,
        String domain,
        String description
) {
}
