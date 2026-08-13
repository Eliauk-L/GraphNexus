package com.graphnexus.application.evaluation.graph.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsAllEvaluationProperties() {
        contextRunner.withPropertyValues(
                        "graphnexus.evaluation.dataset-root=taxonomy-data",
                        "graphnexus.evaluation.datasets.v1.directory=8-down",
                        "graphnexus.evaluation.chunk-size=3000",
                        "graphnexus.evaluation.chunk-overlap=300",
                        "graphnexus.evaluation.relaxed-match-threshold=0.9",
                        "graphnexus.evaluation.nlp-topic-threshold=0.6",
                        "graphnexus.evaluation.nlp-relation-threshold=0.7",
                        "graphnexus.evaluation.default-repeat-count=5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EvaluationProperties properties = context.getBean(EvaluationProperties.class);
                    assertThat(properties.getDatasetRoot()).isEqualTo("taxonomy-data");
                    assertThat(properties.getDatasets().get("v1").getDirectory()).isEqualTo("8-down");
                    assertThat(properties.getChunkSize()).isEqualTo(3000);
                    assertThat(properties.getChunkOverlap()).isEqualTo(300);
                    assertThat(properties.getRelaxedMatchThreshold()).isEqualTo(0.9);
                    assertThat(properties.getNlpTopicThreshold()).isEqualTo(0.6);
                    assertThat(properties.getNlpRelationThreshold()).isEqualTo(0.7);
                    assertThat(properties.getDefaultRepeatCount()).isEqualTo(5);
                });
    }

    @Test
    void rejectsOverlapNotSmallerThanChunkSize() {
        contextRunner.withPropertyValues(
                        "graphnexus.evaluation.chunk-size=100",
                        "graphnexus.evaluation.chunk-overlap=100")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EvaluationProperties.class)
    static class PropertiesConfiguration {
    }
}
