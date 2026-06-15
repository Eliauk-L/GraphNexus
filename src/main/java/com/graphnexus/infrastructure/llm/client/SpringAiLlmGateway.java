package com.graphnexus.infrastructure.llm.client;

import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * LlmGateway 的双模型降级实现 — DeepSeek 云端为主，Ollama 本地兜底。
 *
 * @author Jay
 * @date 2026/06/14
 */
@Slf4j
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatModel primaryModel;
    private final ChatModel fallbackModel;

    public SpringAiLlmGateway(
            @Qualifier("deepseekChatModel") ChatModel primaryModel,
            @Qualifier("ollamaChatModel") ChatModel fallbackModel) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        // 先调 DeepSeek 主模型
        try {
            return callModel(primaryModel, "DeepSeek", systemPrompt, userMessage);
        } catch (Exception e) {
            log.warn("DeepSeek 主模型失败，降级到 Ollama 本地: {}", e.getMessage());
        }

        // 兜底调 Ollama 本地
        try {
            return callModel(fallbackModel, "Ollama", systemPrompt, userMessage);
        } catch (Exception e) {
            log.error("Ollama 兜底也失败了: {}", e.getMessage());
            throw new BusinessException(ErrorCode.C0001, "LLM 双模型全部调用失败: " + e.getMessage());
        }
    }

    private String callModel(ChatModel model, String name, String systemPrompt, String userMessage) {
        log.debug("调用 {} API，systemPrompt 长度={}, userMessage 长度={}",
                name, systemPrompt.length(), userMessage.length());

        String response = ChatClient.builder(model).build()
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        if (response == null || response.isBlank()) {
            throw new BusinessException(ErrorCode.C0001, name + " 返回空响应");
        }

        log.debug("{} 响应长度={}", name, response.length());
        return response;
    }
}