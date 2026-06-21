package com.graphnexus.application.analysis.fusion.model;

/**
 * 融合回滚结果 BO。
 *
 * @author Jay
 * @date 2026/06/15
 */
public record FusionRollbackResult(
        long fusionLogId,
        int restoredKpCount,
        int restoredEdgeCount
) {}