package com.graphnexus.application.query.chat.model;

import java.time.LocalDateTime;

/**
 * 问答结果 BO — L2 层返回给 L1 的查询结果。
 *
 * @author Jay
 * @date 2026/06/17
 */
public record QueryResultBO(
        /** 任务唯一标识 */
        String taskId,
        /** 任务状态 */
        String status,
        /** 用户原始问题 */
        String question,
        /** 识别到的意图 */
        String intent,
        /** LLM 生成的 Markdown 答案（COMPLETED 时有值） */
        String answer,
        /** Token 用量信息 */
        TokenUsage tokenUsage,
        /** 错误信息（FAILED 时有值） */
        String errorMessage,
        /** 任务创建时间 */
        LocalDateTime createdAt,
        /** 任务更新时间 */
        LocalDateTime updatedAt
) {
    public record TokenUsage(
            int prunedNodes,
            int prunedEdges,
            int estimatedTokens,
            Integer promptTokens,
            Integer completionTokens
    ) {}
}