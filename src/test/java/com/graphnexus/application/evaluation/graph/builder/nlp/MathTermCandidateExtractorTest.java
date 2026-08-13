package com.graphnexus.application.evaluation.graph.builder.nlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MathTermCandidateExtractorTest {
    @Test
    void extractsDefinitionTarget() {
        SentenceSplitter splitter = new SentenceSplitter();
        var result = new MathTermCandidateExtractor().extract(splitter.split("含有未知数的等式叫做方程。"));
        assertThat(result).anyMatch(term -> term.term().equals("方程") && term.definitionHit());
    }
}
