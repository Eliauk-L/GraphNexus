package com.graphnexus.application.analysis.fusion.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.graphnexus.infrastructure.typesafe.client.TypeSafeHttpException;
import com.graphnexus.infrastructure.typesafe.client.TypeSafeJevClient;
import com.graphnexus.infrastructure.typesafe.config.TypeSafeProperties;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneAnswer;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneQuestion;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneRequest;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jev 对知识点候选对的 Score + Noul 判断实现。
 *
 * <p>该组件尚不接入现有自动融合流程。来源记录、候选账本和审核闭环完成后，
 * 由未来的 KpAlignmentService 在事务外调用它。</p>
 */
@Slf4j
@Component
public class JevKpPairJudge implements KpPairJudge {

    private static final double PROBABILITY_TOLERANCE = 0.0001;

    private final TypeSafeJevClient client;
    private final TypeSafeProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Cache<String, KpPairJudgment> cache;

    public JevKpPairJudge(TypeSafeJevClient client, TypeSafeProperties properties,
                           ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, properties.getCacheMaxEntries()))
                .build();
    }

    @Override
    public KpPairJudgment judge(KpPairState pair) {
        if (!properties.isActive()) {
            throw new IllegalStateException("TypeSafe Jev 当前为 OFF 或未启用");
        }
        Map<String, Object> state = buildState(pair);
        Map<String, SystemOneQuestion> questions = buildQuestions();
        String stateHash = hash(state, properties.getModel(), properties.getQuestionVersion(), questions);
        KpPairJudgment cached = cache.getIfPresent(stateHash);
        if (cached != null) {
            meterRegistry.counter("graphnexus.typesafe.cache.hits").increment();
            return cached;
        }

        long startedAt = System.nanoTime();
        SystemOneResponse response = client.evaluate(new SystemOneRequest(
                state, properties.getModel(), questions));
        KpPairJudgment judgment = mapResponse(response, stateHash,
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        cache.put(stateHash, judgment);
        log.debug("Jev 知识点候选判断完成: model={}, questionVersion={}, outcome={}, confidence={}",
                judgment.model(), judgment.questionVersion(), judgment.outcome(), judgment.confidence());
        return judgment;
    }

    private Map<String, Object> buildState(KpPairState pair) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("mention", descriptor(pair.mention(), false));
        state.put("candidate", descriptor(pair.candidate(), true));
        return state;
    }

    private Map<String, Object> descriptor(KnowledgePointDescriptor value, boolean includeAliases) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", blankToEmpty(value.name()));
        result.put("description", blankToEmpty(value.description()));
        result.put("subject", blankToEmpty(value.subject()));
        result.put("stage", blankToEmpty(value.stage()));
        result.put("grade", blankToEmpty(value.grade()));
        result.put("chapterPath", value.chapterPath());
        result.put("prerequisites", value.prerequisites());
        if (includeAliases) {
            result.put("aliases", value.aliases());
        }
        return result;
    }

    private Map<String, SystemOneQuestion> buildQuestions() {
        Map<String, SystemOneQuestion> questions = new LinkedHashMap<>();
        questions.put("link_state", new SystemOneQuestion("score", Map.of(
                "question", "`mention` 与 `candidate` 在当前教学语境中是否表示同一个可共享的规范知识点？",
                "focus", "判断概念身份；不要因为名称相似、同属章节或存在前置关系就视为同一知识点。"), List.of(
                Map.of("outcome", "different", "meaning", "表示不同知识点，应保持独立。"),
                Map.of("outcome", "review", "meaning", "语义相关但描述、范围或上下文不足，需要教师审核。"),
                Map.of("outcome", "same", "meaning", "表示同一个教学概念，仅名称或描述详略不同。"))));
        questions.put("same_core_concept", new SystemOneQuestion("noul",
                "`mention` 与 `candidate` 是否教授相同的核心概念，而不只是主题相关？", Map.of(
                "true", "定义对象、教学范围和学习目标一致。",
                "false", "只是相关、上下位、前后置，或核心对象不同。")));
        questions.put("descriptions_consistent", new SystemOneQuestion("noul",
                "`mention.description` 与 `candidate.description` 是否不存在实质语义冲突？", null));
        questions.put("neighborhood_consistent", new SystemOneQuestion("noul",
                "`mention.prerequisites` 与 `candidate.prerequisites` 是否支持两者为同一知识点？", null));
        return questions;
    }

    private KpPairJudgment mapResponse(SystemOneResponse response, String stateHash, long durationMs) {
        if (response.model() == null || response.model().isBlank()) {
            throw contractError("TypeSafe 响应缺少 model");
        }
        SystemOneAnswer link = requireAnswer(response, "link_state", "score");
        double score = requiredScore(link.score());
        double confidence = requiredProbability(link.confidence(), "link_state.confidence");
        Map<Integer, Double> probabilities = parseLinkProbabilities(link.probabilities());
        double probabilitySum = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(probabilitySum - 1.0) > PROBABILITY_TOLERANCE) {
            throw contractError("link_state.probabilities 之和必须为 1");
        }
        int inputTokens = response.usage() == null || response.usage().input_tokens() == null
                ? -1 : response.usage().input_tokens();
        int outputTokens = response.usage() == null || response.usage().output_tokens() == null
                ? -1 : response.usage().output_tokens();
        return new KpPairJudgment("typesafe", response.model(), properties.getQuestionVersion(), score,
                probabilities, confidence,
                noul(response, "same_core_concept"),
                noul(response, "descriptions_consistent"),
                noul(response, "neighborhood_consistent"),
                inputTokens, outputTokens, stateHash, durationMs);
    }

    private SystemOneAnswer requireAnswer(SystemOneResponse response, String questionId, String expectedType) {
        if (response.answers() == null) {
            throw contractError("TypeSafe 响应缺少 answers");
        }
        SystemOneAnswer answer = response.answers().get(questionId);
        if (answer == null || !expectedType.equals(answer.type())) {
            throw contractError("问题 " + questionId + " 缺失或类型不是 " + expectedType);
        }
        return answer;
    }

    private double noul(SystemOneResponse response, String questionId) {
        return requiredProbability(requireAnswer(response, questionId, "noul").noul(), questionId + ".noul");
    }

    private Map<Integer, Double> parseLinkProbabilities(Map<String, Double> values) {
        if (values == null || values.size() != 3) {
            throw contractError("link_state.probabilities 必须包含三个等级");
        }
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (int level = 0; level <= 2; level++) {
            Double value = values.get(String.valueOf(level));
            result.put(level, requiredProbability(value, "link_state.probabilities." + level));
        }
        return result;
    }

    private double requiredProbability(Double value, String field) {
        if (value == null || value < 0 || value > 1) {
            throw contractError(field + " 必须位于 [0,1]");
        }
        return value;
    }

    private double requiredScore(Double value) {
        if (value == null || value < 0 || value > 2) {
            throw contractError("link_state.score 必须位于 [0,2]");
        }
        return value;
    }

    private TypeSafeHttpException contractError(String message) {
        return new TypeSafeHttpException("TypeSafe 响应契约错误: " + message, null, false, null);
    }

    private String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(objectMapper.writeValueAsBytes(value));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 TypeSafe stateHash", exception);
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
