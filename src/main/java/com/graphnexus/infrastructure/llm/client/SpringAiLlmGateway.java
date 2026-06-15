package com.graphnexus.infrastructure.llm.client;

import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * LlmGateway 的 Spring AI 实现 — 调用 DeepSeek 云端 API。
 *
 * @author Jay
 * @date 2026/06/14
 */
@Slf4j
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatModel chatModel;

    public SpringAiLlmGateway(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.debug("调用 DeepSeek API，systemPrompt={}chars, userMessage={}chars",
                systemPrompt.length(), userMessage.length());

        try {
            String response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                throw new BusinessException(ErrorCode.C0001, "DeepSeek 返回空响应");
            }

            log.debug("DeepSeek 响应长度={}", response.length());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.C0001, "DeepSeek 调用失败: " + e.getMessage());
        }
    }
}