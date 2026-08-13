package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MetricCalculatorTest {

    private final MetricCalculator calculator = new MetricCalculator();

    @Test
    void perfectPredictionProducesOneForCoreMetrics() {
        GoldDataset gold = gold();
        CandidateGraph candidate = candidate();
        TopicMatch matchA = new TopicMatch("a", "ga", MatchType.STRICT, 1, true, true);
        TopicMatch matchB = new TopicMatch("b", "gb", MatchType.STRICT, 1, true, true);
        TopicMatchResult topics = new TopicMatchResult(List.of(matchA, matchB), List.of(matchA, matchB));
        DependencyMatchResult dependencies = new DependencyMatchResult(1, 1, 1, 1, 1, List.of());

        EvaluationMetrics result = calculator.calculate(gold, candidate, topics, dependencies, 1.0);

        assertThat(result.topicStrictF1()).isEqualTo(1.0);
        assertThat(result.topicRelaxedF1()).isEqualTo(1.0);
        assertThat(result.internalRelationF1()).isEqualTo(1.0);
        assertThat(result.typeAccuracy()).isEqualTo(1.0);
        assertThat(result.domainAccuracy()).isEqualTo(1.0);
        assertThat(result.directionAccuracy()).isEqualTo(1.0);
        assertThat(result.strengthAccuracy()).isEqualTo(1.0);
        assertThat(result.qualityScore()).isEqualTo(1.0);
    }

    @Test
    void emptyPredictionNeverProducesNan() {
        EvaluationMetrics result = calculator.calculate(gold(), new CandidateGraph(List.of(), List.of()),
                new TopicMatchResult(List.of(), List.of()),
                new DependencyMatchResult(0, 0, 0, 0, 0, List.of()), 1.0);
        assertThat(result.topicStrictF1()).isZero();
        assertThat(result.internalRelationF1()).isZero();
        assertThat(Double.isNaN(result.typeAccuracy())).isFalse();
        assertThat(Double.isNaN(result.directionAccuracy())).isFalse();
    }

    private CandidateGraph candidate() {
        return new CandidateGraph(List.of(
                new CandidateTopic("a", "A", "CONCEPTUAL", "数学", null),
                new CandidateTopic("b", "B", "PROCEDURAL", "数学", null)),
                List.of(new CandidateDependency("a", "b", "hard")));
    }

    private GoldDataset gold() {
        List<GoldTopic> topics = List.of(
                new GoldTopic("ga", "A", "CONCEPTUAL", "数学", null),
                new GoldTopic("gb", "B", "PROCEDURAL", "数学", null));
        List<GoldDependency> edges = List.of(new GoldDependency("ga", "gb", "hard"));
        return new GoldDataset("v", "h", topics, edges, edges, List.of(), Set.of(), List.of());
    }
}
