package com.graphnexus.application.agent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.agent.model.AgentResponse;
import com.graphnexus.application.agent.model.AgentToolCall;
import com.graphnexus.application.agent.tool.ToolResult;
import com.graphnexus.infrastructure.mysql.agent.repository.AgentToolCallRepository;
import com.graphnexus.infrastructure.mysql.agent.repository.AgentTaskRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgentTraceServiceTest {
    @Test
    void savesPublicDecisionAndObservationWithoutHiddenThought() {
        AgentToolCallRepository repository = mock(AgentToolCallRepository.class);
        AgentTaskRepository taskRepository = mock(AgentTaskRepository.class);
        AgentTraceService service = new AgentTraceService(taskRepository, repository, new ObjectMapper());
        AgentToolCall call = new AgentToolCall(1, "student_profile", "{}", "读取学生画像",
                ToolResult.success("profile", List.of(), 12, 1));

        service.save(new AgentResponse("task", "COMPLETED", "answer", List.of("student_profile"),
                List.of(), List.of(call), List.of(), null), "teacher-1");

        ArgumentCaptor<List<com.graphnexus.infrastructure.mysql.agent.entity.AgentToolCallDO>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertEquals("读取学生画像", captor.getValue().get(0).getDecisionSummary());
        assertEquals("\"profile\"", captor.getValue().get(0).getObservationJson());
    }
}
