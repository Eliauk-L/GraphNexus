package com.graphnexus.infrastructure.typesafe.dto;

import java.util.Map;

/** TypeSafe {@code POST /v1/systemone} 请求体。 */
public record SystemOneRequest(Object state, String model, Map<String, SystemOneQuestion> questions) {
}
