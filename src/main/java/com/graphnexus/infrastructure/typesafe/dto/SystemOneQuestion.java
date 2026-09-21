package com.graphnexus.infrastructure.typesafe.dto;

/** TypeSafe System One 问题的通用 JSON 形状。 */
public record SystemOneQuestion(String type, Object instructions, Object criteria) {
}
