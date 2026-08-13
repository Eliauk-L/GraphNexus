package com.graphnexus.application.evaluation.graph.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicNameNormalizerTest {

    private final TopicNameNormalizer normalizer = new TopicNameNormalizer();

    @Test
    void normalizesWidthWhitespaceCaseAndBoundaryPunctuation() {
        assertThat(normalizer.normalize("  【ＡBC　 定理。】 ")).isEqualTo("abc 定理");
    }

    @Test
    void preservesMathSymbols() {
        assertThat(normalizer.normalize("  a²+b²=c²  ")).isEqualTo("a2+b2=c2");
    }
}
