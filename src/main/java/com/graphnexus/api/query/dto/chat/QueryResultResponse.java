package com.graphnexus.api.query.dto.chat;

import com.graphnexus.application.query.chat.model.QueryResultBO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 轮询结果响应 VO。 */
@Schema(description = "异步问答轮询结果视图")
public record QueryResultResponse(
        @Schema(description = "任务 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        String taskId,

        @Schema(description = "任务状态：PENDING/PROCESSING/COMPLETED/FAILED", example = "COMPLETED")
        String status,

        @Schema(description = "用户原始问题")
        String question,

        @Schema(description = "识别的查询意图", example = "STUDENT_DIAGNOSIS")
        String intent,

        @Schema(description = "LLM 分析结论（COMPLETED 时有值，格式由 outputFormat 字段指示）")
        String answer,

        @Schema(description = "输出格式: html-svg | markdown", example = "markdown")
        String outputFormat,

        @Schema(description = "Token 用量统计")
        QueryAskResponse.TokenUsageVO tokenUsage,

        @Schema(description = "失败原因（FAILED 时有值）", example = "LLM API 调用超时")
        String errorMessage,

        @Schema(description = "任务创建时间")
        LocalDateTime createdAt,

        @Schema(description = "最后更新时间")
        LocalDateTime updatedAt
) {
    public static QueryResultResponse from(QueryResultBO bo) {
        return new QueryResultResponse(bo.taskId(), bo.status(), bo.question(), bo.intent(),
                bo.answer(), bo.outputFormat(),
                bo.tokenUsage() != null ? new QueryAskResponse.TokenUsageVO(bo.tokenUsage().prunedNodes(), bo.tokenUsage().prunedEdges(), bo.tokenUsage().estimatedTokens()) : null,
                bo.errorMessage(), bo.createdAt(), bo.updatedAt());
    }
}