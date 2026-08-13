package com.graphnexus.application.evaluation.graph.builder.llm;

import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationPromptBuilderTest {

    @Test
    void loadsDedicatedTemplatesAndReplacesVariables() {
        EvaluationPromptBuilder builder = new EvaluationPromptBuilder(new DefaultResourceLoader());
        GraphBuildContext context = new GraphBuildContext(1L, "测试教材", "数学", "八年级",
                "正文", "hash", "v1", "run");
        var pair = builder.build(context, new EvaluationTextChunker.TextChunk("chunk-1", 0, "待抽取内容"));
        assertThat(pair.systemPrompt()).contains("微粒度知识点");
        assertThat(pair.userMessage()).contains("测试教材", "数学", "八年级", "chunk-1", "待抽取内容");
        assertThat(pair.userMessage()).doesNotContain("{{");
    }
}
