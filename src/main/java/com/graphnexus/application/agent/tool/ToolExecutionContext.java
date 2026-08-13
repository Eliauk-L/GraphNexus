package com.graphnexus.application.agent.tool;

import java.time.Instant;
import java.util.Set;

/** 由服务端生成的工具执行上下文，模型不能修改权限范围。 */
public record ToolExecutionContext(
        String taskId,
        String userId,
        Set<String> roles,
        Set<String> allowedStudentNos,
        Instant deadline,
        String traceId
) {
    public ToolExecutionContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        allowedStudentNos = allowedStudentNos == null ? Set.of() : Set.copyOf(allowedStudentNos);
    }

    public boolean canAccessStudent(String studentNo) {
        return roles.contains("ADMIN") || allowedStudentNos.contains(studentNo);
    }
}
