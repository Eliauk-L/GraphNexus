package com.graphnexus.infrastructure.llm.client;

import com.graphnexus.common.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * LlmGateway 的 LangChain4j 实现。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Component
public class Langchain4jLlmGateway implements LlmGateway {

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    private volatile OpenAiChatModel chatModel;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("LLM API Key 未设置，LlmGateway 将不可用。请设置环境变量 LLM_API_KEY");
            this.chatModel = null;
            return;
        }
        this.chatModel = buildChatModel(model, temperature, maxTokens);
    }

    /**
     * 热重建 ChatModel — 供 ConfigService.apply() 在 LLM 参数变更时调用。
     *
     * <p>创建新实例成功后原子替换 chatModel（volatile 保证并发可见性）。
     * 创建失败时保留旧实例，记录 ERROR 日志并抛异常。</p>
     */
    public void reinitialize(String newModel, Double newTemperature, Integer newMaxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.C0001,
                    "LLM 网关不可用：API Key 未配置，无法重建 ChatModel");
        }
        OpenAiChatModel newChatModel = buildChatModel(newModel, newTemperature, newMaxTokens);
        this.model = newModel;
        this.temperature = newTemperature;
        this.maxTokens = newMaxTokens;
        this.chatModel = newChatModel;
        log.info("ChatModel 已重建: baseUrl={}, model={}, temperature={}, maxTokens={}",
                baseUrl, newModel, newTemperature, newMaxTokens);
    }

    public String getCurrentModelName() { return model; }
    public Double getCurrentTemperature() { return temperature; }
    public Integer getCurrentMaxTokens() { return maxTokens; }
    public String getCurrentBaseUrl() { return baseUrl; }
    public String getCurrentApiKey() { return apiKey; }

    // ── Setters for ConfigService dynamic management ──
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setModel(String model) { this.model = model; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    private OpenAiChatModel buildChatModel(String modelName, Double temp, Integer tokens) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl + "/v1")
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temp)
                .maxTokens(tokens)
                .timeout(Duration.ofMinutes(5))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        if (chatModel == null) {
            throw new BusinessException(ErrorCode.C0001,
                    "LLM 网关不可用：API Key 未配置，请设置环境变量 LLM_API_KEY");
        }
        log.debug("调用 LLM 模型 {}，systemPrompt={}chars, userMessage={}chars", model,
                systemPrompt.length(), userMessage.length());

        try {
            String response = chatModel.chat(
                    List.of(SystemMessage.from(systemPrompt), UserMessage.from(userMessage))
            ).aiMessage().text();

            if (response == null || response.isBlank()) {
                throw new BusinessException(ErrorCode.C0001, "LLM 返回空响应");
            }

            log.debug("LLM 响应长度={}", response.length());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.C0001, toFriendlyError(e));
        }
    }

    /**
     * 将 LLM 服务商原始异常转换为用户友好提示。
     */
    private String toFriendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "大模型服务暂时不可用，请稍后重试";
        String lower = msg.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized")
                || lower.contains("invalid api key") || lower.contains("incorrect api key")
                || lower.contains("authentication") || lower.contains("auth failed")) {
            return "大模型 API Key 无效或已过期，请联系管理员检查 LLM_API_KEY 配置";
        }
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "大模型服务拒绝访问，可能未开通该模型权限，请联系管理员";
        }
        if (lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests")) {
            return "大模型调用过于频繁，请稍后重试";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "大模型响应超时，请稍后重试";
        }
        if (lower.contains("500") || lower.contains("502") || lower.contains("503")
                || lower.contains("internal server error") || lower.contains("service unavailable")) {
            return "大模型服务暂时不可用，请稍后重试";
        }
        return "大模型服务调用失败，请稍后重试";
    }
}