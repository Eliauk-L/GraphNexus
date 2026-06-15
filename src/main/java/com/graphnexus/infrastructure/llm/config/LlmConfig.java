package com.graphnexus.infrastructure.llm.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * LLM 双模型配置 — DeepSeek 云端 API 为主，Ollama 本地为兜底。
 *
 * @author Jay
 * @date 2026/06/14
 */
@Configuration
public class LlmConfig {

    // ============ DeepSeek 云端（主） ============

    @Value("${spring.ai.openai.base-url}")
    private String deepseekBaseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String deepseekApiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String deepseekModel;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double deepseekTemperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer deepseekMaxTokens;

    // ============ Ollama 本地（兜底） ============

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String ollamaModel;

    @Value("${spring.ai.ollama.chat.options.temperature:0.3}")
    private Double ollamaTemperature;

    @Value("${spring.ai.ollama.chat.options.max-tokens:4096}")
    private Integer ollamaMaxTokens;

    @Bean
    @Primary
    public OpenAiChatModel deepseekChatModel() {
        return buildChatModel(deepseekBaseUrl, deepseekApiKey, deepseekModel,
                deepseekTemperature, deepseekMaxTokens, Duration.ofSeconds(60));
    }

    @Bean
    public OpenAiChatModel ollamaChatModel() {
        // Ollama 首次推理需加载模型到内存，加大超时
        return buildChatModel(ollamaBaseUrl, ollamaModel,
                ollamaTemperature, ollamaMaxTokens, Duration.ofMinutes(10));
    }

    private OpenAiChatModel buildChatModel(String baseUrl, String apiKey, String model,
                                           Double temperature, Integer maxTokens, Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(readTimeout);

        var restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        OpenAiApi openAiApi = new OpenAiApi(
                baseUrl, apiKey,
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

    private OpenAiChatModel buildChatModel(String baseUrl, String model,
                                            Double temperature, Integer maxTokens, Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(readTimeout);

        var restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        OpenAiApi openAiApi = new OpenAiApi(
                baseUrl, "",
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