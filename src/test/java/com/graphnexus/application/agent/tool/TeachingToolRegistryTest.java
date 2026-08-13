package com.graphnexus.application.agent.tool;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeachingToolRegistryTest {
    @Test
    void registersToolsByStableName() {
        TeachingToolRegistry registry = new TeachingToolRegistry(List.of(tool("student_profile")));
        assertTrue(registry.find("student_profile").isPresent());
        assertEquals(1, registry.all().size());
    }

    @Test
    void rejectsDuplicateNames() {
        assertThrows(IllegalStateException.class,
                () -> new TeachingToolRegistry(List.of(tool("duplicate"), tool("duplicate"))));
    }

    @Test
    void executionContextEnforcesStudentScope() {
        ToolExecutionContext teacher = new ToolExecutionContext(
                "task", "teacher", Set.of("TEACHER"), Set.of("S001"), Instant.MAX, "trace");
        ToolExecutionContext admin = new ToolExecutionContext(
                "task", "admin", Set.of("ADMIN"), Set.of(), Instant.MAX, "trace");
        assertTrue(teacher.canAccessStudent("S001"));
        assertTrue(admin.canAccessStudent("S999"));
    }

    private TeachingTool<String, String> tool(String name) {
        return new TeachingTool<>() {
            public String name() { return name; }
            public String description() { return name; }
            public Class<String> inputType() { return String.class; }
            public ToolResult<String> execute(String input, ToolExecutionContext context) {
                return ToolResult.success(input, List.of(), 0, 1);
            }
        };
    }
}
