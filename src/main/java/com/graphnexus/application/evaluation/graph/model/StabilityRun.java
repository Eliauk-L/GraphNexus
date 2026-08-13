package com.graphnexus.application.evaluation.graph.model;

public record StabilityRun(CandidateGraph graph, EvaluationMetrics metrics, long durationMs) {
}
