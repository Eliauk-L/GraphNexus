package com.graphnexus.infrastructure.typesafe.dto;

/** TypeSafe 请求 token 用量。 */
public record SystemOneUsage(Integer input_tokens, Integer output_tokens) {
}
