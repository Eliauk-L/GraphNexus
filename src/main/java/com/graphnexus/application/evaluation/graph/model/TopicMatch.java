package com.graphnexus.application.evaluation.graph.model;

/** One-to-one alignment from a candidate temp ID to a gold topic ID. */
public record TopicMatch(
        String candidateTempId,
        String goldTopicId,
        MatchType matchType,
        double score,
        boolean typeMatched,
        boolean domainMatched
) {
}
