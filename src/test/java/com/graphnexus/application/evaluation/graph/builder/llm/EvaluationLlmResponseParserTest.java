package com.graphnexus.application.evaluation.graph.builder.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationLlmResponseParserTest {

    private final EvaluationLlmResponseParser parser = new EvaluationLlmResponseParser(new ObjectMapper());

    @Test
    void parsesFenceAndKeepsPrerequisiteDirection() {
        var result = parser.parse("""
                ```json
                {"knowledgePoints":[{"tempId":"a","name":"概念A"},{"tempId":"b","name":"方法B"}],
                 "prerequisites":[{"prerequisiteTempId":"a","topicTempId":"b","strength":"hard"}]}
                ```
                """);
        assertThat(result.graph().topics()).hasSize(2);
        assertThat(result.graph().dependencies()).singleElement().satisfies(edge -> {
            assertThat(edge.prerequisiteTempId()).isEqualTo("a");
            assertThat(edge.topicTempId()).isEqualTo("b");
        });
    }

    @Test
    void supportsIndexReferencesAndFiltersMissingEndpoints() {
        var result = parser.parse("""
                {"knowledgePoints":[{"name":"A"},{"name":"B"}],
                 "prerequisites":[{"sourceKnowledgePointIndex":0,"targetKnowledgePointIndex":1},
                                  {"sourceKnowledgePointIndex":9,"targetKnowledgePointIndex":1}]}
                """);
        assertThat(result.graph().dependencies()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
    }

    @Test
    void rejectsEmptyAndInvalidResponses() {
        assertThatThrownBy(() -> parser.parse(" ")).hasMessageContaining("空响应");
        assertThatThrownBy(() -> parser.parse("not-json")).hasMessageContaining("JSON");
    }
}
