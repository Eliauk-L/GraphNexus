package com.graphnexus.infrastructure.typesafe.dto;

import java.util.Map;

/** TypeSafe {@code POST /v1/systemone} 响应体。 */
public record SystemOneResponse(String model, Map<String, SystemOneAnswer> answers, SystemOneUsage usage) {
}
