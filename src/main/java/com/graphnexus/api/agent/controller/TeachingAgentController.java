package com.graphnexus.api.agent.controller;

import com.graphnexus.api.agent.dto.AgentChatRequest;
import com.graphnexus.application.agent.model.AgentRequest;
import com.graphnexus.application.agent.model.AgentResponse;
import com.graphnexus.application.agent.service.TeachingAgentService;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.trace.AgentTraceService;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.agent.entity.AgentTaskDO;
import com.graphnexus.infrastructure.mysql.agent.entity.AgentToolCallDO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class TeachingAgentController {
    private final TeachingAgentService agentService;
    private final AgentTraceService traceService;

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ApiResult<AgentResponse> chat(@RequestBody @Valid AgentChatRequest request,
                                         Authentication authentication) {
        Set<String> roles = authentication == null ? Set.of() : authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .collect(Collectors.toSet());
        String user = authentication == null ? "anonymous" : authentication.getName();
        if (roles.contains("STUDENT") && !user.equals(request.studentNo())) {
            throw new BusinessException(ErrorCode.A0003);
        }
        // 教师班级授权表尚未落库；当前由角色权限允许教师指定一个学生作为本次执行范围。
        Set<String> studentScope = roles.contains("ADMIN") ? Set.of() : Set.of(request.studentNo());
        ToolExecutionContext context = new ToolExecutionContext(UUID.randomUUID().toString(), user,
                roles, studentScope, Instant.MAX, MDC.get("traceId"));
        AgentResponse response = agentService.execute(new AgentRequest(
                request.question(), request.studentNo(), request.subject(),
                request.dailyMinutes(), request.days()), context);
        return ApiResult.success(response);
    }

    @GetMapping("/result/{taskId}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ApiResult<List<AgentToolCallDO>> trace(@PathVariable String taskId) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return ApiResult.success(traceService.get(taskId, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    public ApiResult<List<AgentTaskDO>> tasks(Authentication authentication) {
        return ApiResult.success(traceService.recent(authentication.getName(), isAdmin(authentication)));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
