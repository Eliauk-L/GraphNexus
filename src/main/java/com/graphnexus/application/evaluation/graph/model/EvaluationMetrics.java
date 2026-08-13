package com.graphnexus.application.evaluation.graph.model;

/** Core strict metrics used by the first vertical slice. */
public record EvaluationMetrics(
        int goldTopicCount,
        int candidateTopicCount,
        int matchedTopicCount,
        double topicStrictPrecision,
        double topicStrictRecall,
        double topicStrictF1,
        double topicRelaxedPrecision,
        double topicRelaxedRecall,
        double topicRelaxedF1,
        double typeAccuracy,
        double typeCoverage,
        double domainAccuracy,
        double domainCoverage,
        int goldInternalDependencyCount,
        int candidateDependencyCount,
        int matchedDependencyCount,
        double internalRelationPrecision,
        double internalRelationRecall,
        double internalRelationF1,
        double directionAccuracy,
        double strengthAccuracy,
        double structureValidity,
        double qualityScore
) {
    /** Compatibility constructor used by the first strict-only vertical slice. */
    public EvaluationMetrics(int goldTopicCount, int candidateTopicCount, int matchedTopicCount,
                             double topicStrictPrecision, double topicStrictRecall, double topicStrictF1,
                             int goldInternalDependencyCount, int candidateDependencyCount, int matchedDependencyCount,
                             double internalRelationPrecision, double internalRelationRecall, double internalRelationF1) {
        this(goldTopicCount, candidateTopicCount, matchedTopicCount,
                topicStrictPrecision, topicStrictRecall, topicStrictF1,
                topicStrictPrecision, topicStrictRecall, topicStrictF1,
                0, 0, 0, 0,
                goldInternalDependencyCount, candidateDependencyCount, matchedDependencyCount,
                internalRelationPrecision, internalRelationRecall, internalRelationF1,
                0, 0, 1, 0);
    }
}
