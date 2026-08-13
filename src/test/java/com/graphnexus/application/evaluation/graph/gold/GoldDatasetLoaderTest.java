package com.graphnexus.application.evaluation.graph.gold;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("8-down 黄金集加载")
class GoldDatasetLoaderTest {

    private GoldDatasetLoader loader;

    @BeforeEach
    void setUp() {
        EvaluationProperties properties = new EvaluationProperties();
        properties.setDatasetRoot("pep-math-taxonomy/data");
        EvaluationProperties.DatasetConfig dataset = new EvaluationProperties.DatasetConfig();
        dataset.setDirectory("8-down");
        properties.getDatasets().put("pep-math-8down-v1", dataset);
        loader = new GoldDatasetLoader(new DatasetRegistry(properties), new ObjectMapper(), new GoldDatasetValidator());
    }

    @Test
    @DisplayName("加载真实结构化标注并正确拆分内部与跨册关系")
    void loadsExpectedDatasetShape() {
        GoldDataset result = loader.load("pep-math-8down-v1");

        assertThat(result.topics()).hasSize(19);
        assertThat(result.dependencies()).hasSize(27);
        assertThat(result.internalDependencies()).hasSize(17);
        assertThat(result.crossBookDependencies()).hasSize(10);
        assertThat(result.externalTopicIds()).hasSize(7);
        assertThat(result.datasetHash()).hasSize(64);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("manifest 类型统计"));
    }

    @Test
    @DisplayName("依赖方向保持 prerequisiteId 指向 topicId")
    void keepsPrerequisiteDirection() {
        GoldDataset result = loader.load("pep-math-8down-v1");

        assertThat(result.internalDependencies())
                .allSatisfy(edge -> {
                    assertThat(edge.prerequisiteId()).isNotBlank();
                    assertThat(edge.topicId()).isNotBlank();
                });
    }
}
