package com.graphnexus.application.analysis.fusion.model;

import java.time.LocalDateTime;

/**
 * 融合状态查询结果 BO。
 *
 * @author Jay
 * @date 2026/06/15
 */
public record FusionStatusResult(
        Long fusionLogId,
        String triggerType,
        String status,
        LocalDateTime executedAt,
        int mergedKpGroupCount,
        int mastersEdgeCount,
        boolean rolledBack,
        String fusionDetailJson,
        String mastersSnapshotJson
) {}