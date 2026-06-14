package com.graphnexus.infrastructure.llm.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * LLM 手动配置 — Ollama 本地部署，使用 OpenAI 兼容端点 /v1/chat/completions。
 *
 * @author Jay
 * @date 2026/06/14
 */
@Configuration
public class LlmConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String model;

    @Value("${spring.ai.ollama.chat.options.temperature:0.3}")
    private Double temperature;

    @Value("${spring.ai.ollama.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    @Bean
    public OpenAiChatModel chatModel() {
        // 加大超时：Ollama 首次推理需加载模型到内存，大模型可能耗时 30-120s
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofMinutes(10));

        var restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        OpenAiApi openAiApi = new OpenAiApi(
                baseUrl, "ollama",
                "/v1/chat/completions", "/v1/embeddings",
                restClientBuilder,
                org.springframework.web.reactive.function.client.WebClient.builder(),
                org.springframework.ai.retry.RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER
        );

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();
        return new OpenAiChatModel(openAiApi, options);
    }
}