package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StabilityCalculatorTest {

    private final StabilityCalculator calculator = new StabilityCalculator(new TopicNameNormalizer());

    @Test
    void identicalRunsHavePerfectJaccardAndZeroDeviation() {
        CandidateGraph graph = graph("a", "b");
        EvaluationMetrics metrics = metrics(1);
        StabilityMetrics result = calculator.calculate(List.of(
                new StabilityRun(graph, metrics, 10), new StabilityRun(graph, metrics, 20)));
        assertThat(result.topicJaccard()).isEqualTo(1.0);
        assertThat(result.dependencyJaccard()).isEqualTo(1.0);
        assertThat(result.metricStandardDeviation().get("topicStrictF1")).isZero();
        assertThat(result.durationMin()).isEqualTo(10);
        assertThat(result.durationMean()).isEqualTo(15);
        assertThat(result.durationMax()).isEqualTo(20);
    }

    @Test
    void disjointRunsHaveZeroJaccard() {
        StabilityMetrics result = calculator.calculate(List.of(
                new StabilityRun(graph("a", "b"), metrics(1), 1),
                new StabilityRun(graph("x", "y"), metrics(0), 1)));
        assertThat(result.topicJaccard()).isZero();
        assertThat(result.dependencyJaccard()).isZero();
        assertThat(result.metricStandardDeviation().get("topicStrictF1")).isGreaterThan(0);
    }

    private CandidateGraph graph(String source, String target) {
        return new CandidateGraph(List.of(
                new CandidateTopic(source, source, null, null, null),
                new CandidateTopic(target, target, null, null, null)),
                List.of(new CandidateDependency(source, target, "hard")));
    }

    private EvaluationMetrics metrics(double f1) {
        return new EvaluationMetrics(1, 1, 1, f1, f1, f1, 1, 1, 1, f1, f1, f1);
    }
}
