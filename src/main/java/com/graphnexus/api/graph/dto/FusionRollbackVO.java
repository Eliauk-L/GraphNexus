package com.graphnexus.api.graph.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 融合回滚结果 VO。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Schema(description = "融合回滚结果视图")
public record FusionRollbackVO(
        @Schema(description = "融合日志 ID", example = "1") long fusionLogId,
        @Schema(description = "恢复的 KnowledgePoint 节点数", example = "8") int restoredKpCount,
        @Schema(description = "恢复和重定向的边数", example = "45") int restoredEdgeCount
) {}