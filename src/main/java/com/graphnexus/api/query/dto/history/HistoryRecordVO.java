package com.graphnexus.api.query.dto.history;

import com.graphnexus.api.query.dto.chat.QueryAskResponse;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskDO;

import java.time.LocalDateTime;

/**
 * 历史诊断记录列表项 VO。
 *
 * <p>不含 answer 字段——列表不返回大文本，详情通过 GET /result/{taskId} 按需加载。</p>
 *
 * @param taskId      任务 UUID
 * @param question    用户原始问题
 * @param studentName 学生姓名
 * @param studentNo   学号
 * @param subject     学科
 * @param status      任务状态
 * @param intent      识别意图
 * @param tokenUsage  Token 用量（prunedNodes/prunedEdges/estimatedTokens）
 * @param elapsedMs   耗时（毫秒）
 * @param errorMessage 失败原因（仅 FAILED 状态有值）
 * @param createTime  创建时间
 * @author Jay
 * @date 2026/06/22
 */
public record HistoryRecordVO(
        String taskId,
        String question,
        String studentName,
        String studentNo,
        String subject,
        String status,
        String intent,
        QueryAskResponse.TokenUsageVO tokenUsage,
        Long elapsedMs,
        String errorMessage,
        LocalDateTime createTime
) {

    /** 从 QueryTaskDO 构造 */
    public static HistoryRecordVO from(QueryTaskDO task) {
        return new HistoryRecordVO(
                task.getTaskId(),
                task.getQuestion(),
                task.getStudentName(),
                task.getStudentNo(),
                task.getSubject(),
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getIntent(),
                parseTokenUsage(task.getTokenUsageJson()),
                task.getElapsedMs(),
                task.getErrorMessage(),
                task.getCreateTime()
        );
    }

    private static QueryAskResponse.TokenUsageVO parseTokenUsage(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, QueryAskResponse.TokenUsageVO.class);
        } catch (Exception e) {
            return null;
        }
    }
}