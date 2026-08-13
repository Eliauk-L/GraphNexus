package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyMatcherTest {

    private final DependencyMatcher matcher = new DependencyMatcher();

    @Test
    void detectsCorrectReversedStrengthAndUnmatchedRelations() {
        GoldDataset gold = gold();
        CandidateGraph candidate = new CandidateGraph(List.of(
                new CandidateTopic("a", "A", null, null, null),
                new CandidateTopic("b", "B", null, null, null),
                new CandidateTopic("x", "X", null, null, null)), List.of(
                new CandidateDependency("a", "b", "soft"),
                new CandidateDependency("b", "a", "hard"),
                new CandidateDependency("x", "a", "hard")));
        List<TopicMatch> matches = List.of(
                new TopicMatch("a", "ga", MatchType.STRICT, 1, true, true),
                new TopicMatch("b", "gb", MatchType.STRICT, 1, true, true));

        DependencyMatchResult result = matcher.match(candidate, gold, matches);

        assertThat(result.truePositiveCount()).isEqualTo(1);
        assertThat(result.errors()).extracting(EvaluationErrorDetail::type)
                .contains(EvaluationErrorType.WRONG_DEPENDENCY_STRENGTH,
                        EvaluationErrorType.REVERSED_DEPENDENCY,
                        EvaluationErrorType.UNMATCHED_DEPENDENCY_ENDPOINT);
    }

    private GoldDataset gold() {
        List<GoldTopic> topics = List.of(
                new GoldTopic("ga", "A", null, null, null),
                new GoldTopic("gb", "B", null, null, null));
        List<GoldDependency> edges = List.of(new GoldDependency("ga", "gb", "hard"));
        return new GoldDataset("v", "h", topics, edges, edges, List.of(), Set.of(), List.of());
    }
}
