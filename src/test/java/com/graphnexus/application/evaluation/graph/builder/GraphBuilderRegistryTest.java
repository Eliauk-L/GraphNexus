package com.graphnexus.application.evaluation.graph.builder;

import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import com.graphnexus.application.evaluation.graph.model.GraphBuildResult;
import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphBuilderRegistryTest {

    @Test
    void resolvesLlmAndNlpBuildersByMethod() {
        GraphBuilder llm = builder(GraphBuildMethod.LLM);
        GraphBuilder nlp = builder(GraphBuildMethod.NLP_NER_RE);
        GraphBuilderRegistry registry = new GraphBuilderRegistry(List.of(llm, nlp));

        assertThat(registry.get(GraphBuildMethod.LLM)).isSameAs(llm);
        assertThat(registry.get(GraphBuildMethod.NLP_NER_RE)).isSameAs(nlp);
        assertThat(registry.availableMethods()).containsExactlyInAnyOrder(
                GraphBuildMethod.LLM, GraphBuildMethod.NLP_NER_RE);
    }

    @Test
    void rejectsDuplicateMethodRegistration() {
        assertThatThrownBy(() -> new GraphBuilderRegistry(List.of(
                builder(GraphBuildMethod.LLM), builder(GraphBuildMethod.LLM))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复注册")
                .hasMessageContaining("LLM");
    }

    @Test
    void allowsApplicationToStartBeforeConcreteBuildersExist() {
        GraphBuilderRegistry registry = new GraphBuilderRegistry(List.of());

        assertThat(registry.availableMethods()).isEmpty();
        assertThatThrownBy(() -> registry.get(GraphBuildMethod.LLM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未实现");
    }

    private GraphBuilder builder(GraphBuildMethod method) {
        return new GraphBuilder() {
            @Override
            public GraphBuildMethod method() {
                return method;
            }

            @Override
            public GraphBuildResult build(GraphBuildContext context) {
                return new GraphBuildResult(new CandidateGraph(List.of(), List.of()),
                        0, 0, 0, 0, 0, 0, List.of());
            }
        };
    }
}
