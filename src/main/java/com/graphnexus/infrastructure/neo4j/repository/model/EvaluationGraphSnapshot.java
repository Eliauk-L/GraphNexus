package com.graphnexus.infrastructure.neo4j.repository.model;

import java.time.LocalDateTime;
import java.util.List;

/** Infrastructure-neutral snapshot persisted as an isolated evaluation graph. */
public record EvaluationGraphSnapshot(
        String graphId,
        String datasetVersion,
        String datasetHash,
        String method,
        String status,
        LocalDateTime createdAt,
        MetricSnapshot metrics,
        List<TopicSnapshot> topics,
        List<DependencySnapshot> dependencies,
        List<String> warnings
) {
    public EvaluationGraphSnapshot {
        topics = List.copyOf(topics);
        dependencies = List.copyOf(dependencies);
        warnings = List.copyOf(warnings);
    }

    public record MetricSnapshot(
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
        public MetricSnapshot(int goldTopicCount, int candidateTopicCount, int matchedTopicCount,
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

    public record TopicSnapshot(String tempId, String name, String type, String domain, String description) {
    }

    public record DependencySnapshot(String prerequisiteTempId, String topicTempId, String strength) {
    }
}
