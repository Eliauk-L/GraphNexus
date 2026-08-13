package com.graphnexus.application.evaluation.graph.model;

import java.util.List;

public record DependencyMatchResult(
        int truePositiveCount,
        int comparablePairCount,
        int correctlyDirectedPairCount,
        int strengthComparableCount,
        int correctStrengthCount,
        List<EvaluationErrorDetail> errors
) {
    public DependencyMatchResult {
        errors = List.copyOf(errors);
    }
}
