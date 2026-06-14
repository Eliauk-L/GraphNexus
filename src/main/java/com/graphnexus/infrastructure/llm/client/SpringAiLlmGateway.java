package com.graphnexus.infrastructure.llm.client;

import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * LlmGateway 的 Spring AI 实现 — 通过 {@link ChatClient} 调用 DeepSeek API（OpenAI 兼容协议）。
 *
 * <p>读取 {@code spring.ai.openai.*} 配置（base-url/api-key/model），
 * 调用失败时包装为 {@link BusinessException}(ErrorCode.C0001)。</p>
 *
 * <p>设计决策见 ADR-004。接口在 L2，实现在 L3，隔离 Spring AI 具体 API。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatModel chatModel;

    /**
     * 发送 Prompt 到 LLM 并返回原始文本响应。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return LLM 原始文本响应
     * @throws BusinessException 调用失败时（网络超时/API 错误/空响应）
     */
    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.debug("调用 LLM API，systemPrompt 长度={}, userMessage 长度={}",
                systemPrompt.length(), userMessage.length());

        try {
            String response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                log.error("LLM 返回空响应");
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