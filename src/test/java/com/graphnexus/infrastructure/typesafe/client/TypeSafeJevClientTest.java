package com.graphnexus.infrastructure.typesafe.client;

import com.graphnexus.infrastructure.typesafe.config.TypeSafeProperties;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneQuestion;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("TypeSafeJevClient HTTP 契约")
class TypeSafeJevClientTest {

    @Test
    @DisplayName("发送 Bearer 请求并解析结构化响应")
    void sendsBearerRequestAndParsesResponse() {
        TypeSafeProperties properties = activeProperties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        TypeSafeJevClient client = new TypeSafeJevClient(builder.build(), properties, new SimpleMeterRegistry());
        var response = client.evaluate(request());

        assertEquals("jev-1.13.0", response.model());
        assertEquals(0.9, response.answers().get("same").noul(), 0.0001);
        server.verify();
    }

    @Test
    @DisplayName("503 后按配置重试并返回后续成功响应")
    void retriesTransientServerFailure() {
        TypeSafeProperties properties = activeProperties();
        properties.setInitialBackoffMs(0);
        properties.setMaxRetries(1);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        TypeSafeJevClient client = new TypeSafeJevClient(builder.build(), properties, new SimpleMeterRegistry());

        assertDoesNotThrow(() -> client.evaluate(request()));
        server.verify();
    }

    @Test
    @DisplayName("401 不重试并保留不可重试分类")
    void doesNotRetryUnauthorized() {
        TypeSafeProperties properties = activeProperties();
        properties.setMaxRetries(3);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        TypeSafeJevClient client = new TypeSafeJevClient(builder.build(), properties, new SimpleMeterRegistry());
        TypeSafeHttpException exception = assertThrows(TypeSafeHttpException.class,
                () -> client.evaluate(request()));

        assertEquals(401, exception.getStatusCode());
        assertFalse(exception.isRetryable());
        server.verify();
    }

    @Test
    @DisplayName("缺少 API 约定的 usage 时拒绝响应")
    void rejectsResponseWithoutUsage() {
        TypeSafeProperties properties = activeProperties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess("""
                        {"model":"jev-1.13.0","answers":{"same":{"type":"noul","noul":0.9}}}
                        """, MediaType.APPLICATION_JSON));

        TypeSafeJevClient client = new TypeSafeJevClient(builder.build(), properties, new SimpleMeterRegistry());
        TypeSafeHttpException exception = assertThrows(TypeSafeHttpException.class,
                () -> client.evaluate(request()));

        assertNull(exception.getStatusCode());
        assertFalse(exception.isRetryable());
        server.verify();
    }

    private TypeSafeProperties activeProperties() {
        TypeSafeProperties properties = new TypeSafeProperties();
        properties.setEnabled(true);
        properties.setMode(TypeSafeProperties.Mode.SHADOW);
        properties.setApiKey("test-key");
        properties.setInitialBackoffMs(0);
        return properties;
    }

    private SystemOneRequest request() {
        return new SystemOneRequest(Map.of("text", "测试"), "jev-1.13.0", Map.of(
                "same", new SystemOneQuestion("noul", "是否相同？", null)));
    }

    private String validResponse() {
        return """
                {
                  "model":"jev-1.13.0",
                  "answers":{"same":{"type":"noul","noul":0.9}},
                  "usage":{"input_tokens":12,"output_tokens":2}
                }
                """;
    }
}
