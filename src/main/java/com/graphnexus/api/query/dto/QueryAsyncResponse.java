package com.graphnexus.api.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 异步提交响应 VO。 */
@Schema(description = "异步问答提交响应视图")
public record QueryAsyncResponse(
        @Schema(description = "任务 ID（UUID），用于轮询 GET /result/{taskId}", example = "550e8400-e29b-41d4-a716-446655440000")
        String taskId,

        @Schema(description = "任务状态（提交后固定为 PENDING）", example = "PENDING")
        String status,

        @Schema(description = "任务创建时间", example = "2026-06-17T10:30:00")
        LocalDateTime createdAt
) {}