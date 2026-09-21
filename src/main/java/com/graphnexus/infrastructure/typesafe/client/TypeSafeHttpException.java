package com.graphnexus.infrastructure.typesafe.client;

/** TypeSafe HTTP 或传输错误，保留是否可重试的分类供应用层处理。 */
public class TypeSafeHttpException extends RuntimeException {

    private final Integer statusCode;
    private final boolean retryable;

    public TypeSafeHttpException(String message, Integer statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
