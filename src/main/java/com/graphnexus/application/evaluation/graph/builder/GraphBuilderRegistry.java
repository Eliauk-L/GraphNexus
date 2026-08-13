package com.graphnexus.application.evaluation.graph.builder;

import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Routes a build method to exactly one Spring-managed graph builder. */
@Slf4j
@Component
public class GraphBuilderRegistry {

    private final Map<GraphBuildMethod, GraphBuilder> builders;

    public GraphBuilderRegistry(List<GraphBuilder> graphBuilders) {
        EnumMap<GraphBuildMethod, GraphBuilder> registered = new EnumMap<>(GraphBuildMethod.class);
        for (GraphBuilder builder : graphBuilders) {
            if (builder == null || builder.method() == null) {
                throw new IllegalStateException("GraphBuilder 及其 method 不能为空");
            }
            GraphBuilder previous = registered.putIfAbsent(builder.method(), builder);
            if (previous != null) {
                throw new IllegalStateException("GraphBuilder 方法重复注册: " + builder.method()
                        + " (" + previous.getClass().getName() + ", " + builder.getClass().getName() + ")");
            }
        }
        this.builders = Collections.unmodifiableMap(registered);
        log.info("图谱评测构建器注册表初始化完成，共 {} 个构建器: {}", builders.size(), builders.keySet());
    }

    public GraphBuilder get(GraphBuildMethod method) {
        if (method == null) {
            throw new BusinessException(ErrorCode.A0002, "图谱构建方法不能为空");
        }
        GraphBuilder builder = builders.get(method);
        if (builder == null) {
            throw new BusinessException(ErrorCode.A0002, "图谱构建方法尚未实现: " + method);
        }
        return builder;
    }

    public Set<GraphBuildMethod> availableMethods() {
        return Set.copyOf(builders.keySet());
    }
}
