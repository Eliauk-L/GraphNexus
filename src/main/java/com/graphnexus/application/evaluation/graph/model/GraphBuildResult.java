package com.graphnexus.application.evaluation.graph.model;

import java.util.List;

/** Builder output plus comparable runtime and cost measurements. */
public record GraphBuildResult(
        CandidateGraph rawGraph,
        long durationMs,
        long promptChars,
        long responseChars,
        long estimatedInputTokens,
        long estimatedOutputTokens,
        int callCount,
        List<String> warnings
) {
    public GraphBuildResult {
        rawGraph = rawGraph == null ? new CandidateGraph(List.of(), List.of()) : rawGraph;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        requireNonNegative(durationMs, "durationMs");
        requireNonNegative(promptChars, "promptChars");
        requireNonNegative(responseChars, "responseChars");
        requireNonNegative(estimatedInputTokens, "estimatedInputTokens");
        requireNonNegative(estimatedOutputTokens, "estimatedOutputTokens");
        requireNonNegative(callCount, "callCount");
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " 不能为负数");
    }
}
