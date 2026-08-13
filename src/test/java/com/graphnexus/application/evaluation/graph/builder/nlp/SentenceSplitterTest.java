package com.graphnexus.application.evaluation.graph.builder.nlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceSplitterTest {
    @Test
    void keepsStableSentenceAndParagraphIndexes() {
        var result = new SentenceSplitter().split("第一句。第二句！\n\n第三句。" );
        assertThat(result).hasSize(3);
        assertThat(result).extracting(SentenceSplitter.Sentence::paragraphIndex).containsExactly(0, 0, 1);
    }
}
