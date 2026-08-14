package com.graphnexus.application.agent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.agent.model.AgentResponse;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.agent.entity.AgentTaskDO;
import com.graphnexus.infrastructure.mysql.agent.entity.AgentToolCallDO;
import com.graphnexus.infrastructure.mysql.agent.repository.AgentTaskRepository;
import com.graphnexus.infrastructure.mysql.agent.repository.AgentToolCallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentTraceService {
    private final AgentTaskRepository taskRepository;
    private final AgentToolCallRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(AgentResponse response, String userId) {
        LocalDateTime now = LocalDateTime.now();
        taskRepository.save(AgentTaskDO.builder().taskId(response.taskId()).userId(userId)
                .status(response.status()).answerText(response.answer())
                .fallbackReason(response.fallbackReason()).createTime(now).build());
        List<AgentToolCallDO> rows = response.trace().stream().map(call -> AgentToolCallDO.builder()
                .taskId(response.taskId()).roundNo(call.round()).toolName(call.toolName())
                .argumentsJson(call.argumentsJson())
                .observationJson(json(call.observation().data()))
                .decisionSummary(call.decisionSummary())
                .status(call.observation().success() ? "COMPLETED" : "FAILED")
                .elapsedMs(call.observation().metrics().elapsedMs())
                .errorMessage(call.observation().success() ? null : String.join(";", call.observation().warnings()))
                .createTime(now).build()).toList();
        repository.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<AgentToolCallView> get(String taskId, String userId, boolean admin) {
        requireOwner(taskId, userId, admin);
        return repository.findByTaskIdOrderByRoundNoAsc(taskId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<AgentTaskView> recent(String userId, boolean admin) {
        List<AgentTaskDO> rows = admin ? taskRepository.findTop50ByOrderByCreateTimeDesc()
                : taskRepository.findTop50ByUserIdOrderByCreateTimeDesc(userId);
        return rows.stream().map(this::toView).toList();
    }

    private void requireOwner(String taskId, String userId, boolean admin) {
        AgentTaskDO task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0001, "Agent 任务不存在"));
        if (!admin && !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.A0003);
        }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { return "null"; }
    }

    private AgentTaskView toView(AgentTaskDO row) {
        return new AgentTaskView(row.getTaskId(), row.getUserId(), row.getStatus(), row.getAnswerText(),
                row.getFallbackReason(), row.getCreateTime());
    }

    private AgentToolCallView toView(AgentToolCallDO row) {
        return new AgentToolCallView(row.getId(), row.getTaskId(), row.getRoundNo(), row.getToolName(),
                row.getArgumentsJson(), row.getObservationJson(), row.getDecisionSummary(), row.getStatus(),
                row.getElapsedMs(), row.getErrorMessage(), row.getCreateTime());
    }
}
