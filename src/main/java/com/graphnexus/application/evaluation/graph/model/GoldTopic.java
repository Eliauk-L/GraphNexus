package com.graphnexus.application.evaluation.graph.model;

/** Gold topic loaded from topics.json. */
public record GoldTopic(
        String id,
        String name,
        String type,
        String domain,
        String description
) {
}
