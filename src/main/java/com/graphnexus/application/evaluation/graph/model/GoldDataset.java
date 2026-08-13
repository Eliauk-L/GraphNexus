package com.graphnexus.application.evaluation.graph.model;

import java.util.List;
import java.util.Set;

/** Validated gold dataset and its internal/cross-book split. */
public record GoldDataset(
        String version,
        String datasetHash,
        List<GoldTopic> topics,
        List<GoldDependency> dependencies,
        List<GoldDependency> internalDependencies,
        List<GoldDependency> crossBookDependencies,
        Set<String> externalTopicIds,
        List<String> warnings
) {
    public GoldDataset {
        topics = List.copyOf(topics);
        dependencies = List.copyOf(dependencies);
        internalDependencies = List.copyOf(internalDependencies);
        crossBookDependencies = List.copyOf(crossBookDependencies);
        externalTopicIds = Set.copyOf(externalTopicIds);
        warnings = List.copyOf(warnings);
    }
}
