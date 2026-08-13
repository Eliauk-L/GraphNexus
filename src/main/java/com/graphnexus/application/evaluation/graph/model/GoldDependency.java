package com.graphnexus.application.evaluation.graph.model;

/** Gold directed edge: prerequisiteId -> topicId. */
public record GoldDependency(
        String prerequisiteId,
        String topicId,
        String strength
) {
}
