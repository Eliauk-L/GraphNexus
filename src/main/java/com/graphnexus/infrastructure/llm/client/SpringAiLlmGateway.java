package com.graphnexus.infrastructure.llm.client;

import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LlmGateway 的双模型降级实现 — DeepSeek 云端为主，Ollama 本地兜底。
 * 反序列化失败时输出 LLM 原始返回内容。
 *
 * @author Jay
 * @date 2026/06/14
 */
@Slf4j
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatModel primaryModel;
    private final ChatModel fallbackModel;

    @Value("${spring.ai.openai.base-url}")
    private String deepseekBaseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String deepseekModel;

    @Value("${spring.ai.openai.api-key}")
    private String deepseekApiKey;

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
            return callWithChatClient(primaryModel, "DeepSeek", systemPrompt, userMessage);
        } catch (Exception e) {
            log.warn("DeepSeek 主模型失败，降级到 Ollama: {}", e.getMessage());
            // 用原始 HTTP 查看 DeepSeek 实际返回了什么
            dumpRawDeepSeekResponse(systemPrompt, userMessage);
        }

        // 兜底 Ollama
        return callWithChatClient(fallbackModel, "Ollama", systemPrompt, userMessage);
    }

    private String callWithChatClient(ChatModel model, String name, String systemPrompt, String userMessage) {
        log.info("调用 {} API，systemPrompt={}chars, userMessage={}chars",
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

        log.info("{} 调用成功，响应长度={}", name, response.length());
        return response;
    }

    /** 用原始 HTTP 请求查看 DeepSeek 返回的原始内容（用于排查反序列化问题） */
    private void dumpRawDeepSeekResponse(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> body = Map.of(
                    "model", deepseekModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 4096
            );

            String url = deepseekBaseUrl + "/v1/chat/completions";
            String raw = RestClient.builder().build()
                    .post()
                    .uri(url)
                    .header("Authorization", "Bearer " + deepseekApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .body(String.class);

            log.info("DeepSeek 原始返回 (前2000字符): {}", raw != null ? raw.substring(0, Math.min(2000, raw.length())) : "null");
        } catch (Exception ex) {
            log.error("查看原始返回也失败: {}", ex.getMessage());
        }
    }
}