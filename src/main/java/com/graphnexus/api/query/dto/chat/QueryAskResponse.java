package com.graphnexus.api.query.dto.chat;

import com.graphnexus.application.query.chat.model.QueryResultBO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 同步问答响应 VO。
 */
@Schema(description = "同步问答/智能对话响应视图")
public record QueryAskResponse(
        @Schema(description = "任务 ID（UUID）", example = "550e8400-e29b-41d4-a716-446655440000")
        String taskId,

        @Schema(description = "用户原始问题", example = "分析张三的数学薄弱点")
        String question,

        @Schema(description = "识别的查询意图", example = "STUDENT_DIAGNOSIS")
        String intent,

        @Schema(description = "LLM 分析结论（Markdown 格式）", example = "## 张三数学薄弱点分析\n\n### 薄弱知识点\n- **二次函数顶点坐标**（掌握度 0.42）...")
        String answer,

        @Schema(description = "任务状态", example = "COMPLETED")
        String status,

        @Schema(description = "Token 用量统计")
        TokenUsageVO tokenUsage
) {
    @Schema(description = "Token 用量统计")
    public record TokenUsageVO(
            @Schema(description = "剪枝后子图节点数", example = "15") int prunedNodes,
            @Schema(description = "剪枝后子图边数", example = "22") int prunedEdges,
            @Schema(description = "估算的 LLM 输入 token 数", example = "3200") int estimatedTokens
    ) {}

    public static QueryAskResponse from(QueryResultBO bo) {
        return new QueryAskResponse(bo.taskId(), bo.question(), bo.intent(),
                bo.answer(), bo.status(),
                bo.tokenUsage() != null
                        ? new TokenUsageVO(bo.tokenUsage().prunedNodes(), bo.tokenUsage().prunedEdges(), bo.tokenUsage().estimatedTokens())
                        : null);
    }
}