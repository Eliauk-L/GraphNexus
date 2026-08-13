package com.graphnexus.application.evaluation.graph.model;

import java.util.Map;

public record StabilityMetrics(
        double topicJaccard,
        double dependencyJaccard,
        Map<String, Double> metricMean,
        Map<String, Double> metricStandardDeviation,
        long durationMin,
        double durationMean,
        long durationMax
) {
    public StabilityMetrics {
        metricMean = Map.copyOf(metricMean);
        metricStandardDeviation = Map.copyOf(metricStandardDeviation);
    }
}
