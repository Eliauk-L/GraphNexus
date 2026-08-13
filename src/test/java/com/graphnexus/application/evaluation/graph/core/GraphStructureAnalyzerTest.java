package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GraphStructureAnalyzerTest {

    private final GraphStructureAnalyzer analyzer = new GraphStructureAnalyzer();

    @Test
    void emptyAndSingleNodeGraphsAreSafe() {
        GraphStructureMetrics empty = analyzer.analyze(normalized(new CandidateGraph(List.of(), List.of())), gold(), List.of());
        GraphStructureMetrics single = analyzer.analyze(normalized(new CandidateGraph(
                List.of(new CandidateTopic("a", "A", null, null, null)), List.of())), gold(), List.of());
        assertThat(empty.dagValid()).isTrue();
        assertThat(empty.isolatedNodeRate()).isZero();
        assertThat(single.isolatedNodeRate()).isEqualTo(1.0);
        assertThat(single.weakComponentCount()).isEqualTo(1);
    }

    @Test
    void dagComponentsAndGoldReachabilityAreCalculated() {
        CandidateGraph graph = new CandidateGraph(List.of(
                topic("a"), topic("b"), topic("c")),
                List.of(new CandidateDependency("a", "b", "hard")));
        List<TopicMatch> matches = List.of(
                new TopicMatch("a", "ga", MatchType.STRICT, 1, true, true),
                new TopicMatch("b", "gb", MatchType.STRICT, 1, true, true));
        GraphStructureMetrics result = analyzer.analyze(normalized(graph), gold(), matches);
        assertThat(result.dagValid()).isTrue();
        assertThat(result.weakComponentCount()).isEqualTo(2);
        assertThat(result.largestComponentCoverage()).isEqualTo(2.0 / 3.0);
        assertThat(result.goldReachabilityRecall()).isEqualTo(1.0);
    }

    @Test
    void directedCycleIsDetected() {
        CandidateGraph graph = new CandidateGraph(List.of(topic("a"), topic("b")), List.of(
                new CandidateDependency("a", "b", "hard"),
                new CandidateDependency("b", "a", "hard")));
        GraphStructureMetrics result = analyzer.analyze(normalized(graph), gold(), List.of());
        assertThat(result.dagValid()).isFalse();
        assertThat(result.cycleNodeCount()).isEqualTo(2);
        assertThat(result.validityScore()).isZero();
    }

    private CandidateGraphNormalizationResult normalized(CandidateGraph graph) {
        return new CandidateGraphNormalizationResult(graph, 0, 0, 0, 0, 0, List.of());
    }

    private CandidateTopic topic(String id) { return new CandidateTopic(id, id, null, null, null); }

    private GoldDataset gold() {
        List<GoldTopic> topics = List.of(new GoldTopic("ga", "A", null, null, null),
                new GoldTopic("gb", "B", null, null, null));
        List<GoldDependency> edges = List.of(new GoldDependency("ga", "gb", "hard"));
        return new GoldDataset("v", "h", topics, edges, edges, List.of(), Set.of(), List.of());
    }
}
