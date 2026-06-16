package com.graphnexus.application.graph.fusion.model;

/**
 * 融合执行结果 BO。
 *
 * @author Jay
 * @date 2026/06/15
 */
public record FusionExecuteResult(
        long fusionLogId,
        int mergedKpGroupCount,
        int mastersEdgeCount
) {}