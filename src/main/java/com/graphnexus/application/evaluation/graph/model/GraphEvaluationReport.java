package com.graphnexus.application.evaluation.graph.model;

import java.util.List;

/** Full offline evaluation output before persistence/orchestration. */
public record GraphEvaluationReport(
        CandidateGraphNormalizationResult normalization,
        TopicMatchResult topicMatches,
        DependencyMatchResult dependencyMatches,
        GraphStructureMetrics structure,
        EvaluationMetrics metrics,
        List<EvaluationErrorDetail> errors
) {
    public GraphEvaluationReport {
        errors = List.copyOf(errors);
    }
}
