package com.graphnexus.application.evaluation.graph.config;

import lombok.Data;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Knowledge graph evaluation dataset configuration. */
@Data
@Component
@ConfigurationProperties(prefix = "graphnexus.evaluation")
public class EvaluationProperties {

    /** Root containing all registered gold datasets. */
    private String datasetRoot = "pep-math-taxonomy/data";

    /** Public dataset version to directory mapping. */
    private Map<String, DatasetConfig> datasets = new LinkedHashMap<>();

    /** Maximum text characters sent to one builder chunk. */
    private int chunkSize = 2500;

    /** Characters shared by adjacent chunks. */
    private int chunkOverlap = 200;

    /** Minimum score accepted by relaxed topic matching. */
    private double relaxedMatchThreshold = 0.85;

    /** Minimum confidence retained by the NLP topic extractor. */
    private double nlpTopicThreshold = 0.55;

    /** Minimum confidence retained by the NLP relation extractor. */
    private double nlpRelationThreshold = 0.60;

    private double nlpDefinitionWeight = 0.45;
    private double nlpMathPatternWeight = 0.25;
    private double nlpFrequencyWeight = 0.10;
    private double nlpCrossParagraphWeight = 0.15;
    private double nlpTitlePenalty = 0.50;
    private double nlpExplicitRelationScore = 0.85;

    /** Default number of repeated runs used for stability evaluation. */
    private int defaultRepeatCount = 3;

    @PostConstruct
    void validate() {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("graphnexus.evaluation.chunk-size 必须大于 0");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("graphnexus.evaluation.chunk-overlap 必须在 [0, chunk-size) 范围内");
        }
        requireProbability(relaxedMatchThreshold, "relaxed-match-threshold");
        requireProbability(nlpTopicThreshold, "nlp-topic-threshold");
        requireProbability(nlpRelationThreshold, "nlp-relation-threshold");
        requireProbability(nlpDefinitionWeight, "nlp-definition-weight");
        requireProbability(nlpMathPatternWeight, "nlp-math-pattern-weight");
        requireProbability(nlpFrequencyWeight, "nlp-frequency-weight");
        requireProbability(nlpCrossParagraphWeight, "nlp-cross-paragraph-weight");
        requireProbability(nlpTitlePenalty, "nlp-title-penalty");
        requireProbability(nlpExplicitRelationScore, "nlp-explicit-relation-score");
        if (defaultRepeatCount <= 0) {
            throw new IllegalArgumentException("graphnexus.evaluation.default-repeat-count 必须大于 0");
        }
    }

    private void requireProbability(double value, String property) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("graphnexus.evaluation." + property + " 必须在 [0, 1] 范围内");
        }
    }

    @Data
    public static class DatasetConfig {
        private String directory;
    }
}
