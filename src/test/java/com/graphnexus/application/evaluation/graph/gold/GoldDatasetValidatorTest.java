package com.graphnexus.application.evaluation.graph.gold;

import com.graphnexus.application.evaluation.graph.model.GoldDependency;
import com.graphnexus.application.evaluation.graph.model.GoldTopic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldDatasetValidatorTest {

    private final GoldDatasetValidator validator = new GoldDatasetValidator();

    @Test
    void duplicateIdIsBlocking() {
        assertThatThrownBy(() -> validator.validate(List.of(topic("a", "A"), topic("a", "B")), List.of(), Map.of()))
                .hasMessageContaining("ID 重复");
    }

    @Test
    void selfLoopAndCycleAreBlocking() {
        List<GoldTopic> topics = List.of(topic("a", "A"), topic("b", "B"));
        assertThatThrownBy(() -> validator.validate(topics,
                List.of(new GoldDependency("a", "a", "hard")), Map.of()))
                .hasMessageContaining("自环");
        assertThatThrownBy(() -> validator.validate(topics, List.of(
                new GoldDependency("a", "b", "hard"),
                new GoldDependency("b", "a", "hard")), Map.of()))
                .hasMessageContaining("环路");
    }

    @Test
    void incompleteFieldsExternalEndpointAndManifestMismatchAreWarnings() {
        GoldTopic incomplete = new GoldTopic("a", "A", "", "", "");
        List<String> warnings = validator.validate(List.of(incomplete),
                List.of(new GoldDependency("external", "a", "soft")), Map.of("CONCEPTUAL", 1L));

        assertThat(warnings).anyMatch(value -> value.contains("type 为空"));
        assertThat(warnings).anyMatch(value -> value.contains("domain 为空"));
        assertThat(warnings).anyMatch(value -> value.contains("description 为空"));
        assertThat(warnings).anyMatch(value -> value.contains("跨册依赖端点"));
        assertThat(warnings).anyMatch(value -> value.contains("manifest 类型统计"));
    }

    private GoldTopic topic(String id, String name) {
        return new GoldTopic(id, name, "CONCEPTUAL", "数学", "描述");
    }
}
