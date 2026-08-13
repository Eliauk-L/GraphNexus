package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateGraphNormalizerTest {

    private final CandidateGraphNormalizer normalizer = new CandidateGraphNormalizer(new TopicNameNormalizer());

    @Test
    void cleansAndCountsAllRecoverableCandidateProblems() {
        CandidateGraph graph = new CandidateGraph(List.of(
                new CandidateTopic("a", " 定理A ", null, null, null),
                new CandidateTopic("a2", "定理Ａ", null, null, null),
                new CandidateTopic("empty", "  ", null, null, null),
                new CandidateTopic("b", "定理B", null, null, null)), List.of(
                new CandidateDependency("a", "b", "hard"),
                new CandidateDependency("a2", "b", "hard"),
                new CandidateDependency("a", "a2", "hard"),
                new CandidateDependency("missing", "b", "hard")));

        var result = normalizer.normalize(graph);

        assertThat(result.normalizedGraph().topics()).hasSize(2);
        assertThat(result.normalizedGraph().dependencies()).hasSize(1);
        assertThat(result.removedEmptyTopics()).isEqualTo(1);
        assertThat(result.mergedDuplicateTopics()).isEqualTo(1);
        assertThat(result.removedSelfLoops()).isEqualTo(1);
        assertThat(result.removedDuplicateEdges()).isEqualTo(1);
        assertThat(result.removedDanglingEdges()).isEqualTo(1);
    }
}
