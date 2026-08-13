package com.graphnexus.application.evaluation.graph.builder.llm;

import com.graphnexus.application.evaluation.graph.core.CandidateGraphNormalizer;
import com.graphnexus.application.evaluation.graph.builder.GraphBuilder;
import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import com.graphnexus.application.evaluation.graph.model.GraphBuildResult;
import com.graphnexus.common.LlmGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LlmGraphBuilder implements GraphBuilder {

    private static final int MAX_PARSE_RETRIES = 2;
    private final EvaluationTextChunker chunker;
    private final EvaluationPromptBuilder promptBuilder;
    private final EvaluationLlmResponseParser responseParser;
    private final CandidateGraphNormalizer graphNormalizer;
    private final LlmGateway llmGateway;

    @Override
    public GraphBuildMethod method() { return GraphBuildMethod.LLM; }

    @Override
    public GraphBuildResult build(GraphBuildContext context) {
        long started = System.nanoTime();
        List<CandidateTopic> topics = new ArrayList<>();
        List<CandidateDependency> dependencies = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long promptChars = 0, responseChars = 0;
        int calls = 0;
        List<EvaluationTextChunker.TextChunk> chunks = chunker.chunk(
                context.textSnapshot(), context.textHash(), context.chunkConfigVersion());
        for (var chunk : chunks) {
            var prompt = promptBuilder.build(context, chunk);
            EvaluationLlmResponseParser.ParseResult parsed = null;
            RuntimeException last = null;
            for (int attempt = 0; attempt <= MAX_PARSE_RETRIES; attempt++) {
                promptChars += prompt.systemPrompt().length() + prompt.userMessage().length();
                String response = llmGateway.chat(prompt.systemPrompt(), prompt.userMessage());
                calls++;
                responseChars += response == null ? 0 : response.length();
                try {
                    parsed = responseParser.parse(response);
                    if (attempt > 0) warnings.add(chunk.chunkId() + " JSON 解析重试次数: " + attempt);
                    break;
                } catch (RuntimeException ex) {
                    last = ex;
                }
            }
            if (parsed == null) throw last == null ? new IllegalStateException("LLM 解析失败") : last;
            prefixAndMerge(chunk.chunkId(), parsed.graph(), topics, dependencies);
            warnings.addAll(parsed.warnings());
        }
        var normalized = graphNormalizer.normalize(new CandidateGraph(topics, dependencies));
        warnings.addAll(normalized.warnings());
        long duration = (System.nanoTime() - started) / 1_000_000;
        return new GraphBuildResult(normalized.normalizedGraph(), duration, promptChars, responseChars,
                estimate(promptChars), estimate(responseChars), calls, warnings);
    }

    private void prefixAndMerge(String chunkId, CandidateGraph graph, List<CandidateTopic> topics,
                                List<CandidateDependency> dependencies) {
        graph.topics().forEach(topic -> topics.add(new CandidateTopic(chunkId + ":" + topic.tempId(),
                topic.name(), topic.type(), topic.domain(), topic.description())));
        graph.dependencies().forEach(edge -> dependencies.add(new CandidateDependency(
                chunkId + ":" + edge.prerequisiteTempId(), chunkId + ":" + edge.topicTempId(), edge.strength())));
    }

    private long estimate(long chars) { return (chars + 2) / 3; }
}
