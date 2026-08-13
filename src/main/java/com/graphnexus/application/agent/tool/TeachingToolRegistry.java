package com.graphnexus.application.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 启动时发现并校验所有 Agent Tool。 */
@Component
public class TeachingToolRegistry {
    private final Map<String, TeachingTool<?, ?>> tools;

    public TeachingToolRegistry(List<TeachingTool<?, ?>> discoveredTools) {
        Map<String, TeachingTool<?, ?>> registered = new LinkedHashMap<>();
        for (TeachingTool<?, ?> tool : discoveredTools) {
            if (tool.name() == null || tool.name().isBlank()) {
                throw new IllegalStateException("Tool 名称不能为空");
            }
            TeachingTool<?, ?> duplicate = registered.putIfAbsent(tool.name(), tool);
            if (duplicate != null) {
                throw new IllegalStateException("Tool 名称重复: " + tool.name());
            }
        }
        tools = Map.copyOf(registered);
    }

    public Optional<TeachingTool<?, ?>> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<TeachingTool<?, ?>> all() {
        return tools.values();
    }
}
