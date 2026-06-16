package com.graphnexus.application.graph.fusion.strategy;

import com.graphnexus.application.graph.fusion.model.KpCandidate;
import org.springframework.stereotype.Component;

/**
 * 精确名称匹配策略 — 测试桩，用于验证策略可替换性（AC-11）。
 *
 * <p>仅当两个 KP 的 name（忽略大小写）和 subject 完全相同时返回 1.0，否则 0.0。
 * 无模糊容差，适合严格去重场景。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Component
public class ExactMatchStrategy implements KpMatchingStrategy {

    @Override
    public double match(KpCandidate a, KpCandidate b) {
        if (a == null || b == null) {
            return 0.0;
        }
        // subject 不同直接返回 0
        if (!a.subject().equals(b.subject())) {
            return 0.0;
        }
        return a.name().equalsIgnoreCase(b.name()) ? 1.0 : 0.0;
    }

    @Override
    public String getName() {
        return "exact";
    }
}