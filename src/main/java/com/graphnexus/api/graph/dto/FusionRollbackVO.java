package com.graphnexus.api.graph.dto;

/**
 * 融合回滚结果 VO。
 *
 * @author Jay
 * @date 2026/06/15
 */
public record FusionRollbackVO(
        long fusionLogId,
        int restoredKpCount,
        int restoredEdgeCount
) {}