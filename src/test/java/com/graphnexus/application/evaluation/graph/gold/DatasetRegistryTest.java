package com.graphnexus.application.evaluation.graph.gold;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetRegistryTest {

    @Test
    void rejectsUnregisteredVersion() {
        DatasetRegistry registry = new DatasetRegistry(new EvaluationProperties());

        assertThatThrownBy(() -> registry.resolve("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    void rejectsDirectoryOutsideRoot() {
        EvaluationProperties properties = new EvaluationProperties();
        EvaluationProperties.DatasetConfig config = new EvaluationProperties.DatasetConfig();
        config.setDirectory("../outside");
        properties.getDatasets().put("bad", config);

        assertThatThrownBy(() -> new DatasetRegistry(properties).resolve("bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("越界");
    }
}
