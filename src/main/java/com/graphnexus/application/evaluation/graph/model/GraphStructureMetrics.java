package com.graphnexus.application.evaluation.graph.model;

public record GraphStructureMetrics(
        int selfLoopCount,
        int duplicateNodeCount,
        int duplicateEdgeCount,
        int danglingEdgeCount,
        double isolatedNodeRate,
        int weakComponentCount,
        double largestComponentCoverage,
        int cycleNodeCount,
        boolean dagValid,
        double goldReachabilityRecall,
        double validityScore
) {
}
