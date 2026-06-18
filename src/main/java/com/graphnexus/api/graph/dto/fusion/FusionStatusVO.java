package com.graphnexus.api.graph.dto.fusion;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 融合状态查询 VO。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Schema(description = "融合状态查询视图")
public record FusionStatusVO(
        @Schema(description = "融合日志 ID", example = "1") Long fusionLogId,
        @Schema(description = "触发方式：MANUAL（手动）或 AUTO_INCREMENTAL（自动增量）", example = "MANUAL") String triggerType,
        @Schema(description = "融合状态", example = "COMPLETED") String status,
        @Schema(description = "融合执行时间", example = "2026-06-17T10:30:00") LocalDateTime executedAt,
        @Schema(description = "合并的 KP 组数", example = "5") int mergedKpGroupCount,
        @Schema(description = "重算的 MASTERS 边数", example = "230") int mastersEdgeCount,
        @Schema(description = "是否已被回滚", example = "false") boolean rolledBack,
        @Schema(description = "融合明细 JSON（源KP→目标KP映射+边重定向清单）", example = "[{\"sourceKpId\":\"kp_old1\",\"targetKpId\":\"kp_new1\",\"redirectedEdges\":5}]") String fusionDetailJson,
        @Schema(description = "MASTERS 变更快照 JSON（oldWeight→newWeight）", example = "[{\"studentNo\":\"20240001\",\"kpId\":\"kp_new1\",\"oldWeight\":0.72,\"newWeight\":0.68}]") String mastersSnapshotJson
) {}