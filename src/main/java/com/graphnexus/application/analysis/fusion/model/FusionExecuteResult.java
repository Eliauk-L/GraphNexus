package com.graphnexus.application.analysis.fusion.model;

/**
 * 融合执行结果 BO。
 *
 * @author Jay
 * @date 2026/06/15
 */
public record FusionExecuteResult(
        Long fusionLogId,
        int mergedKpGroupCount,
        int mastersEdgeCount
) {}