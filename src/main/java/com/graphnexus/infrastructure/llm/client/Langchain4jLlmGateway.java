package com.graphnexus.infrastructure.llm.client;

import com.graphnexus.application.llmgateway.service.LlmGateway;
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

    private OpenAiChatModel chatModel;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("LLM API Key 未设置，LlmGateway 将不可用。请设置环境变量 LLM_API_KEY");
            this.chatModel = null;
            return;
        }
        this.chatModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl + "/v1")
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofMinutes(5))
                .logRequests(true)
                .logResponses(true)
                .build();
        log.info("LangChain4j OpenAiChatModel 初始化: baseUrl={}, model={}", baseUrl, model);
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
            throw new BusinessException(ErrorCode.C0001, "LLM 调用失败: " + e.getMessage());
        }
    }
}