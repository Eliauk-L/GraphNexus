package com.graphnexus.application.evaluation.graph.builder.llm;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationTextChunkerTest {

    @Test
    void chunkIdsAndContentsAreDeterministic() {
        EvaluationProperties properties = new EvaluationProperties();
        properties.setChunkSize(30);
        properties.setChunkOverlap(5);
        EvaluationTextChunker chunker = new EvaluationTextChunker(properties);
        String text = "第一章 测试\n\n这是第一段较长的数学内容。\n\n1.1 小节\n\n这是第二段数学内容。";

        var first = chunker.chunk(text, "hash", "v1");
        var second = chunker.chunk(text, "hash", "v1");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSizeGreaterThan(1);
        assertThat(first).allSatisfy(chunk -> assertThat(chunk.chunkId()).startsWith("chunk-"));
    }
}
