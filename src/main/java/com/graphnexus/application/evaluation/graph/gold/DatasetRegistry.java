package com.graphnexus.application.evaluation.graph.gold;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Resolves only configured dataset versions and prevents path traversal. */
@Component
@RequiredArgsConstructor
public class DatasetRegistry {

    private final EvaluationProperties properties;

    public Path resolve(String datasetVersion) {
        EvaluationProperties.DatasetConfig config = properties.getDatasets().get(datasetVersion);
        if (config == null || config.getDirectory() == null || config.getDirectory().isBlank()) {
            throw new BusinessException(ErrorCode.A0002, "未注册的黄金集版本: " + datasetVersion);
        }
        Path root = Path.of(properties.getDatasetRoot()).toAbsolutePath().normalize();
        Path dataset = root.resolve(config.getDirectory()).normalize();
        if (!dataset.startsWith(root)) {
            throw new BusinessException(ErrorCode.A0002, "黄金集目录越界: " + datasetVersion);
        }
        return dataset;
    }
}
