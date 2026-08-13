package com.graphnexus.application.evaluation.graph.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateGraphTest {

    @Test
    void defensivelyCopiesTopicAndDependencyLists() {
        List<CandidateTopic> topics = new ArrayList<>();
        topics.add(new CandidateTopic("t1", "A", null, null, null));
        List<CandidateDependency> dependencies = new ArrayList<>();
        CandidateGraph graph = new CandidateGraph(topics, dependencies);

        topics.clear();
        dependencies.add(new CandidateDependency("t1", "t2", "hard"));

        assertThat(graph.topics()).hasSize(1);
        assertThat(graph.dependencies()).isEmpty();
        assertThatThrownBy(() -> graph.topics().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void convertsNullCollectionsToEmptyImmutableLists() {
        CandidateGraph graph = new CandidateGraph(null, null);

        assertThat(graph.topics()).isEmpty();
        assertThat(graph.dependencies()).isEmpty();
    }

    @Test
    void buildResultCopiesWarningsAndRejectsNegativeMeasurements() {
        List<String> warnings = new ArrayList<>(List.of("warning"));
        GraphBuildResult result = new GraphBuildResult(null, 1, 2, 3, 4, 5, 1, warnings);
        warnings.clear();

        assertThat(result.rawGraph().topics()).isEmpty();
        assertThat(result.warnings()).containsExactly("warning");
        assertThatThrownBy(() -> new GraphBuildResult(null, -1, 0, 0, 0, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMs");
    }
}
