package com.graphnexus.api.query.dto;

import com.graphnexus.application.query.model.QueryResultBO;
import java.time.LocalDateTime;

/** 轮询结果响应 VO。 */
public record QueryResultResponse(
        String taskId, String status, String question, String intent,
        String answer, QueryAskResponse.TokenUsageVO tokenUsage,
        String errorMessage, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static QueryResultResponse from(QueryResultBO bo) {
        return new QueryResultResponse(bo.taskId(), bo.status(), bo.question(), bo.intent(),
                bo.answer(),
                bo.tokenUsage() != null ? new QueryAskResponse.TokenUsageVO(bo.tokenUsage().prunedNodes(), bo.tokenUsage().prunedEdges(), bo.tokenUsage().estimatedTokens()) : null,
                bo.errorMessage(), bo.createdAt(), bo.updatedAt());
    }
}