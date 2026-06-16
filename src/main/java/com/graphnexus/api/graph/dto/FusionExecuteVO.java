package com.graphnexus.api.graph.dto;

/**
 * 融合执行结果 VO。
 *
 * @author Jay
 * @date 2026/06/15
 */
public record FusionExecuteVO(
        long fusionLogId,
        int mergedKpGroupCount,
        int mastersEdgeCount
) {}