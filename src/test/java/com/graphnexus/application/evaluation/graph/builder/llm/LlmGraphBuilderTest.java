package com.graphnexus.application.evaluation.graph.builder.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.core.CandidateGraphNormalizer;
import com.graphnexus.application.evaluation.graph.core.TopicNameNormalizer;
import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import com.graphnexus.common.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmGraphBuilderTest {

    @Test
    void retriesInvalidJsonAndCollectsPerformance() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.chat(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("invalid")
                .thenReturn("""
                        {"knowledgePoints":[{"tempId":"a","name":"概念A"},{"tempId":"b","name":"方法B"}],
                         "prerequisites":[{"prerequisiteTempId":"a","topicTempId":"b","strength":"hard"}]}
                        """);
        LlmGraphBuilder builder = builder(gateway, 5000);

        var result = builder.build(context("一段测试数学文本"));

        assertThat(builder.method()).isEqualTo(GraphBuildMethod.LLM);
        assertThat(result.rawGraph().topics()).hasSize(2);
        assertThat(result.rawGraph().dependencies()).hasSize(1);
        assertThat(result.callCount()).isEqualTo(2);
        assertThat(result.promptChars()).isPositive();
        assertThat(result.responseChars()).isPositive();
        assertThat(result.estimatedInputTokens()).isPositive();
        assertThat(result.warnings()).anyMatch(value -> value.contains("重试次数"));
    }

    @Test
    void multipleChunksMergeSameNamesAndEdges() {
        LlmGateway gateway = mock(LlmGateway.class);
        String response = """
                {"knowledgePoints":[{"tempId":"a","name":"概念A"},{"tempId":"b","name":"方法B"}],
                 "prerequisites":[{"prerequisiteTempId":"a","topicTempId":"b"}]}
                """;
        when(gateway.chat(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(response);
        LlmGraphBuilder builder = builder(gateway, 15);

        var result = builder.build(context("第一段数学内容很长。\n\n第二段数学内容也很长。"));

        assertThat(result.callCount()).isGreaterThan(1);
        assertThat(result.rawGraph().topics()).hasSize(2);
        assertThat(result.rawGraph().dependencies()).hasSize(1);
    }

    private LlmGraphBuilder builder(LlmGateway gateway, int chunkSize) {
        EvaluationProperties properties = new EvaluationProperties();
        properties.setChunkSize(chunkSize);
        properties.setChunkOverlap(Math.min(3, chunkSize - 1));
        TopicNameNormalizer names = new TopicNameNormalizer();
        return new LlmGraphBuilder(new EvaluationTextChunker(properties),
                new EvaluationPromptBuilder(new DefaultResourceLoader()),
                new EvaluationLlmResponseParser(new ObjectMapper()), new CandidateGraphNormalizer(names), gateway);
    }

    private GraphBuildContext context(String text) {
        return new GraphBuildContext(1L, "测试", "数学", "八年级", text,
                "hash", "v1", "run-1");
    }
}
