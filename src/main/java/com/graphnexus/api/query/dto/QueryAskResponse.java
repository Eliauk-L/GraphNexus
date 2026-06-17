package com.graphnexus.api.query.dto;

import com.graphnexus.application.query.model.QueryResultBO;

/**
 * 同步问答响应 VO。
 */
public record QueryAskResponse(
        String taskId,
        String question,
        String intent,
        String answer,
        String status,
        TokenUsageVO tokenUsage
) {
    public record TokenUsageVO(int prunedNodes, int prunedEdges, int estimatedTokens) {}

    public static QueryAskResponse from(QueryResultBO bo) {
        return new QueryAskResponse(bo.taskId(), bo.question(), bo.intent(),
                bo.answer(), bo.status(),
                bo.tokenUsage() != null
                        ? new TokenUsageVO(bo.tokenUsage().prunedNodes(), bo.tokenUsage().prunedEdges(), bo.tokenUsage().estimatedTokens())
                        : null);
    }
}