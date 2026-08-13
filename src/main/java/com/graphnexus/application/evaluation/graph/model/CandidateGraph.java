package com.graphnexus.application.evaluation.graph.model;

import java.util.List;

/** Minimal graph representation shared by manual, LLM and NLP builders. */
public record CandidateGraph(
        List<CandidateTopic> topics,
        List<CandidateDependency> dependencies
) {
    public CandidateGraph {
        topics = topics == null ? List.of() : List.copyOf(topics);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
