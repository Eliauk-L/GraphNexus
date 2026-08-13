package com.graphnexus.application.agent.tool;

import java.util.List;

/** Tool 的结构化结果，不使用异常表达可预期的业务失败。 */
public record ToolResult<T>(
        boolean success,
        T data,
        List<EvidenceRef> evidence,
        List<String> warnings,
        ToolMetrics metrics,
        String errorCode
) {
    public ToolResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static <T> ToolResult<T> success(T data, List<EvidenceRef> evidence,
                                            long elapsedMs, int resultCount) {
        return new ToolResult<>(true, data, evidence, List.of(),
                new ToolMetrics(elapsedMs, resultCount, false), null);
    }

    public static <T> ToolResult<T> failure(String errorCode, String warning, long elapsedMs) {
        return new ToolResult<>(false, null, List.of(), List.of(warning),
                new ToolMetrics(elapsedMs, 0, false), errorCode);
    }
}
