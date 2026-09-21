package com.graphnexus.infrastructure.typesafe.client;

import com.graphnexus.infrastructure.typesafe.config.TypeSafeProperties;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneRequest;
import com.graphnexus.infrastructure.typesafe.dto.SystemOneResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Objects;

/**
 * TypeSafe Jev HTTP 客户端。
 *
 * <p>仅封装认证、HTTP 调用、有限退避重试和通用响应完整性校验；候选对问题和
 * AUTO/REVIEW 决策保留在应用层。客户端不会记录 API Key 或请求 state。</p>
 */
@Slf4j
@Component
public class TypeSafeJevClient {

    private static final String REQUEST_METRIC = "graphnexus.typesafe.requests";
    private static final String DURATION_METRIC = "graphnexus.typesafe.duration";
    private static final String RETRY_METRIC = "graphnexus.typesafe.retries";
    private static final String FAILURE_METRIC = "graphnexus.typesafe.failures";
    private static final String TOKEN_METRIC = "graphnexus.typesafe.tokens";

    private final RestClient client;
    private final TypeSafeProperties properties;
    private final MeterRegistry meterRegistry;

    @Autowired
    public TypeSafeJevClient(RestClient.Builder builder, TypeSafeProperties properties,
                             MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.client = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    /** 供本地 HTTP 契约测试或专用 RestClient 配置使用。 */
    public TypeSafeJevClient(RestClient client, TypeSafeProperties properties,
                             MeterRegistry meterRegistry) {
        this.client = client;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public SystemOneResponse evaluate(SystemOneRequest request) {
        requireConfigured();
        Objects.requireNonNull(request, "TypeSafe request 不能为空");
        if (request.state() == null || request.model() == null || request.model().isBlank()
                || request.questions() == null || request.questions().isEmpty()) {
            throw new IllegalArgumentException("TypeSafe request 必须包含 state、model 和 questions");
        }

        long startedAt = System.nanoTime();
        int attempts = Math.max(0, properties.getMaxRetries()) + 1;
        TypeSafeHttpException lastException = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                SystemOneResponse response = client.post()
                        .uri("/v1/systemone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .body(request)
                        .retrieve()
                        .body(SystemOneResponse.class);
                validateResponse(response);
                recordSuccess(response, startedAt);
                return response;
            } catch (RestClientResponseException exception) {
                TypeSafeHttpException classified = classifyResponseException(exception);
                if (!classified.isRetryable() || attempt == attempts - 1) {
                    recordFailure(classified, startedAt);
                    throw classified;
                }
                retry(attempt, classified.getStatusCode());
                lastException = classified;
            } catch (ResourceAccessException exception) {
                TypeSafeHttpException classified = new TypeSafeHttpException(
                        "TypeSafe 网络调用失败", null, true, exception);
                if (attempt == attempts - 1) {
                    recordFailure(classified, startedAt);
                    throw classified;
                }
                retry(attempt, null);
                lastException = classified;
            } catch (TypeSafeHttpException | IllegalArgumentException exception) {
                recordFailure(exception instanceof TypeSafeHttpException typed ? typed
                        : new TypeSafeHttpException(exception.getMessage(), null, false, exception), startedAt);
                throw exception;
            } catch (Exception exception) {
                TypeSafeHttpException classified = new TypeSafeHttpException(
                        "TypeSafe 调用失败", null, false, exception);
                recordFailure(classified, startedAt);
                throw classified;
            }
        }
        throw lastException == null
                ? new IllegalStateException("TypeSafe 调用未产生响应")
                : lastException;
    }

    private void requireConfigured() {
        if (!properties.isActive()) {
            throw new IllegalStateException("TypeSafe Jev 当前为 OFF 或未启用");
        }
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("TypeSafe Jev 已启用但 TYPESAFE_API_KEY 未配置");
        }
    }

    private void validateResponse(SystemOneResponse response) {
        if (response == null || response.model() == null || response.model().isBlank()
                || response.answers() == null || response.answers().isEmpty()) {
            throw new TypeSafeHttpException("TypeSafe 返回的 model 或 answers 缺失", null, false, null);
        }
        if (response.usage() == null || response.usage().input_tokens() == null
                || response.usage().input_tokens() < 0 || response.usage().output_tokens() == null
                || response.usage().output_tokens() < 0) {
            throw new TypeSafeHttpException("TypeSafe 返回的 usage 缺失或非法", null, false, null);
        }
    }

    private TypeSafeHttpException classifyResponseException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        boolean retryable = status == 429 || status == 529 || status >= 500;
        return new TypeSafeHttpException("TypeSafe HTTP " + status, status, retryable, exception);
    }

    private void retry(int attempt, Integer statusCode) {
        meterRegistry.counter(RETRY_METRIC, "status", statusCode == null ? "network" : statusCode.toString()).increment();
        long delay = exponentialBackoff(attempt);
        log.warn("TypeSafe 请求失败，将在 {}ms 后重试: attempt={}, status={}",
                delay, attempt + 1, statusCode);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TypeSafeHttpException("TypeSafe 重试被中断", statusCode, false, exception);
        }
    }

    private long exponentialBackoff(int attempt) {
        if (properties.getInitialBackoffMs() <= 0) {
            return 0;
        }
        int exponent = Math.min(attempt, 20);
        return Math.min(properties.getInitialBackoffMs() * (1L << exponent), 30_000L);
    }

    private void recordSuccess(SystemOneResponse response, long startedAt) {
        String model = response.model();
        meterRegistry.counter(REQUEST_METRIC, "model", model, "outcome", "success").increment();
        meterRegistry.timer(DURATION_METRIC, "model", model).record(System.nanoTime() - startedAt,
                java.util.concurrent.TimeUnit.NANOSECONDS);
        if (response.usage() != null) {
            if (response.usage().input_tokens() != null) {
                meterRegistry.counter(TOKEN_METRIC, "direction", "input").increment(response.usage().input_tokens());
            }
            if (response.usage().output_tokens() != null) {
                meterRegistry.counter(TOKEN_METRIC, "direction", "output").increment(response.usage().output_tokens());
            }
        }
    }

    private void recordFailure(TypeSafeHttpException exception, long startedAt) {
        String reason = exception.getStatusCode() == null ? "transport_or_contract"
                : exception.getStatusCode().toString();
        meterRegistry.counter(FAILURE_METRIC, "reason", reason).increment();
        meterRegistry.timer(DURATION_METRIC, "model", properties.getModel()).record(System.nanoTime() - startedAt,
                java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private org.springframework.http.client.ClientHttpRequestFactory requestFactory(TypeSafeProperties props) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(1, props.getConnectTimeoutMs())));
        factory.setReadTimeout(Duration.ofMillis(Math.max(1, props.getReadTimeoutMs())));
        return factory;
    }
}
