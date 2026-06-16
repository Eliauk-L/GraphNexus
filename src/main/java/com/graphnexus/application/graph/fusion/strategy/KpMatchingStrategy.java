package com.graphnexus.application.graph.fusion.strategy;

import com.graphnexus.application.graph.fusion.model.KpCandidate;

/**
 * KP 匹配策略接口 — 判断两个知识点是否指向同一概念。
 *
 * <p>实现类通过 Spring Bean 注册，由 {@code FusionService} 按 yml 配置
 * {@code fusion.kp-matching.strategy} 值选择。见 ADR-006。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface KpMatchingStrategy {

    /**
     * 计算两个 KP 候选的相似度。
     *
     * @param a 候选 A
     * @param b 候选 B
     * @return 0~1 相似度，1 = 完全等价，0 = 完全不相关
     */
    double match(KpCandidate a, KpCandidate b);

    /**
     * 策略标识，对应 yml 中 {@code fusion.kp-matching.strategy} 配置值。
     *
     * @return 策略名称（如 "fuzzy", "exact"）
     */
    String getName();
}