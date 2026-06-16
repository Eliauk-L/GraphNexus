package com.graphnexus.api.graph.dto;

import java.time.LocalDateTime;

/**
 * 融合状态查询 VO。
 *
 * @author Jay
 * @date 2026/06/15
 */
public record FusionStatusVO(
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