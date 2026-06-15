package com.graphnexus.infrastructure.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * LlmGateway 实现 — 绕过 Spring AI 反序列化，直接通过 HTTP 调用 DeepSeek API。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SpringAiLlmGateway(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model}") String model,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        String url = baseUrl + "/v1/chat/completions";
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "temperature", 0.3,
                "max_tokens", 4096
        );

        log.debug("调用 DeepSeek API: url={}, model={}, prompt={}/{}chars",
                url, model, systemPrompt.length(), userMessage.length());

        try {
            String raw = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw new BusinessException(ErrorCode.C0001, "DeepSeek 返回空响应");
            }

            JsonNode root = objectMapper.readTree(raw);

            // 检查是否有 error
            if (root.has("error")) {
                String errMsg = root.get("error").toPrettyString();
                log.error("DeepSeek API 返回错误: {}", errMsg);
                throw new BusinessException(ErrorCode.C0001, "DeepSeek API 错误: " + errMsg);
            }

            // 提取 content：choices[0].message.content
            JsonNode content = root.at("/choices/0/message/content");
            if (content.isMissingNode()) {
                log.error("DeepSeek 响应缺少 choices[0].message.content，原始: {}", raw.substring(0, Math.min(500, raw.length())));
                throw new BusinessException(ErrorCode.C0001, "DeepSeek 响应格式错误：缺少 content 字段");
            }

            String text = content.asText();
            if (text.isBlank()) {
                throw new BusinessException(ErrorCode.C0001, "DeepSeek 返回空 content");
            }

            log.debug("DeepSeek 调用成功，响应长度={}", text.length());
            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 调用失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.C0001, "DeepSeek 调用失败: " + e.getMessage());
        }
    }
}