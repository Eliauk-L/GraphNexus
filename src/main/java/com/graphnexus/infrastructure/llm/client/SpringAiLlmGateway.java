package com.graphnexus.infrastructure.llm.client;

import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LlmGateway 的 Spring AI 实现 — 调用 DeepSeek 云端 API。
 * 反序列化失败时输出 LLM 原始返回内容便于排查。
 *
 * @author Jay
 * @date 2026/06/14
 */
@Slf4j
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatModel chatModel;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    public SpringAiLlmGateway(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.info("调用 DeepSeek API，systemPrompt={}chars, userMessage={}chars",
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

            log.info("DeepSeek 调用成功，响应长度={}", response.length());
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 调用失败: {}", e.getMessage());
            // 用原始 HTTP 请求查看实际返回内容
            dumpRawResponse(systemPrompt, userMessage);
            throw new BusinessException(ErrorCode.C0001, "DeepSeek 调用失败: " + e.getMessage());
        }
    }

    /** 反序列化失败时，用原始 HTTP 请求抓取并打印 LLM 实际返回内容 */
    private void dumpRawResponse(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 4096
            );

            String url = baseUrl + "/v1/chat/completions";
            String raw = RestClient.builder().build()
                    .post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("DeepSeek 原始返回 (前2000字符): {}",
                    raw != null ? raw.substring(0, Math.min(2000, raw.length())) : "null");
        } catch (Exception ex) {
            log.error("查看原始返回也失败: {}", ex.getMessage());
        }
    }
}