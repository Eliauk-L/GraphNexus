package com.graphnexus.application.agent.tool;

/** 教学 Agent 可调用工具的统一契约。 */
public interface TeachingTool<I, O> {
    String name();

    String description();

    Class<I> inputType();

    ToolResult<O> execute(I input, ToolExecutionContext context);
}
