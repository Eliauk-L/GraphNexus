package com.graphnexus.application.evaluation.graph.builder.nlp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopicTypeClassifierTest {
    private final TopicTypeClassifier classifier = new TopicTypeClassifier();
    @Test void classifiesProceduralAndRepresentationalTerms() {
        assertThat(classifier.classify("二次根式运算", "")).isEqualTo("PROCEDURAL");
        assertThat(classifier.classify("函数图象", "")).isEqualTo("REPRESENTATIONAL");
        assertThat(classifier.classify("未知短语", "")).isNull();
    }
}
