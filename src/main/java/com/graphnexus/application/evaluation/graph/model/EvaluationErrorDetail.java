package com.graphnexus.application.evaluation.graph.model;

/** Machine-readable evaluation error plus the involved candidate/gold identifiers. */
public record EvaluationErrorDetail(
        EvaluationErrorType type,
        String candidateId,
        String goldId,
        String message
) {
}
