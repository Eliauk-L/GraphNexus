package com.graphnexus.application.evaluation.graph.builder.llm;

import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EvaluationPromptBuilder {

    private final ResourceLoader resourceLoader;
    private volatile String systemTemplate;
    private volatile String userTemplate;

    public PromptPair build(GraphBuildContext context, EvaluationTextChunker.TextChunk chunk) {
        String user = template(false);
        Map<String, String> values = Map.of(
                "{{documentName}}", value(context.documentName()),
                "{{subject}}", value(context.subject()),
                "{{grade}}", value(context.grade()),
                "{{chunkId}}", chunk.chunkId(),
                "{{textContent}}", chunk.content());
        for (var entry : values.entrySet()) user = user.replace(entry.getKey(), entry.getValue());
        return new PromptPair(template(true), user);
    }

    private synchronized String template(boolean system) {
        String cached = system ? systemTemplate : userTemplate;
        if (cached != null) return cached;
        String name = system ? "evaluation-extraction-system.md" : "evaluation-extraction-user.md";
        try {
            var resource = resourceLoader.getResource("classpath:/prompts/" + name);
            if (!resource.exists()) throw new BusinessException(ErrorCode.C0001, "评测 Prompt 不存在: " + name);
            cached = resource.getContentAsString(StandardCharsets.UTF_8);
            if (system) systemTemplate = cached; else userTemplate = cached;
            return cached;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.C0001, "评测 Prompt 加载失败: " + name);
        }
    }

    private String value(String value) { return value == null ? "" : value; }
    public record PromptPair(String systemPrompt, String userMessage) { }
}
