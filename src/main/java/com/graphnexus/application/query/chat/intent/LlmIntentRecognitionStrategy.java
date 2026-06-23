package com.graphnexus.application.query.chat.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.query.chat.model.QueryIntent;
import com.graphnexus.application.query.prompt.service.PromptTemplateService;
import com.graphnexus.common.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 意图分类策略 — 调用 LLM 将用户自然语言问题分类为 {@link QueryIntent}。
 *
 * <p>Priority=10（最高优先级），链中第一个执行。
 * 调用失败时：永久性错误（API Key/Auth）直接抛出，瞬态错误返回 null 降级。</p>
 *
 * @author Jay
 * @date 2026/06/22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmIntentRecognitionStrategy implements IntentRecognitionStrategy {

    private final LlmGateway llmGateway;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public QueryIntent recognize(String question) {
        try {
            String intentList = promptTemplateService.buildIntentList();
            String systemPrompt = promptTemplateService.loadTemplate("intent-classification-system")
                    .replace("{{intentList}}", intentList);
            String response = llmGateway.chat(systemPrompt, question);
            return parseClassificationResponse(response);
        } catch (BusinessException e) {
            // 永久性错误（API Key/Auth/权限）→ 直接抛出让用户看到，不降级
            if (isPermanentError(e.getMessage())) {
                throw e;
            }
            log.warn("LLM 意图分类失败（可降级），交由下一策略: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("LLM 意图分类失败，交由下一策略: {}", e.getMessage());
            return null;
        }
    }

    /** 判断是否为永久性配置错误（需透传给用户，不应降级） */
    private boolean isPermanentError(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("api key") || lower.contains("apikey")
                || lower.contains("unauthorized") || lower.contains("401")
                || lower.contains("forbidden") || lower.contains("403")
                || lower.contains("authentication") || lower.contains("auth failed")
                || lower.contains("未配置") || lower.contains("无效")
                || lower.contains("权限");
    }

    /**
     * 解析 LLM 分类响应 JSON，含容错处理。
     * 任何解析异常 → 返回 null。
     */
    private QueryIntent parseClassificationResponse(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) return null;
        try {
            String json = llmResponse.trim();
            // 移除可能存在的 markdown code fence
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?\\s*", "")
                           .replaceAll("```\\s*$", "").trim();
            }
            JsonNode node = objectMapper.readTree(json);
            String intentName = node.has("intent") && !node.get("intent").isNull()
                    ? node.get("intent").asText() : null;
            if (intentName == null) return null;
            try {
                return QueryIntent.valueOf(intentName);
            } catch (IllegalArgumentException e) {
                log.warn("LLM 返回未知意图: {}", intentName);
                return null;
            }
        } catch (Exception e) {
            log.warn("LLM 意图分类 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }
}