package com.graphnexus.application.evaluation.graph.model;

import java.util.List;

public record TopicMatchResult(
        List<TopicMatch> strictMatches,
        List<TopicMatch> relaxedMatches
) {
    public TopicMatchResult {
        strictMatches = List.copyOf(strictMatches);
        relaxedMatches = List.copyOf(relaxedMatches);
    }
}
