package com.graphnexus.api.analysis.dto.fusion;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 融合执行结果 VO。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Schema(description = "融合执行结果视图")
public record FusionExecuteVO(
        @Schema(description = "融合日志 ID（可用于回滚查询）", example = "1") long fusionLogId,
        @Schema(description = "本次融合合并的 KP 组数", example = "5") int mergedKpGroupCount,
        @Schema(description = "重算的 MASTERS 边数", example = "230") int mastersEdgeCount
) {}