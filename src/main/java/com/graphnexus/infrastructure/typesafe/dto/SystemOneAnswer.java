package com.graphnexus.infrastructure.typesafe.dto;

import java.util.Map;

/**
 * 三种 TypeSafe answer 的并集表示；字段由 {@code type} 决定。
 *
 * <p>使用一个 DTO 保持 HTTP 层简单，领域层会再按预期问题类型严格校验。</p>
 */
public record SystemOneAnswer(
        String type,
        Double noul,
        String choice,
        Double score,
        Map<String, Double> probabilities,
        Double confidence,
        Map<String, String> legend) {
}
