package com.graphnexus.application.analysis.fusion.strategy;

import com.graphnexus.application.analysis.fusion.model.KpCandidate;
import org.springframework.stereotype.Component;

/**
 * 模糊匹配策略 v1 — 字符 Jaccard 相似度。
 *
 * <p>仅使用字符集交集/并集比作为相似度度量，对中文 KP 名称最为有效。
 * 精确名称匹配和子序列匹配由 {@code FusionGroupBuilder} 前置 pass 兜底。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Component("fuzzy")
public class FuzzyMatchStrategy implements KpMatchingStrategy {

    @Override
    public double match(KpCandidate a, KpCandidate b) {
        if (a == null || b == null) {
            return 0.0;
        }
        // 前置过滤：跨学科不匹配
        if (!a.subject().equals(b.subject())) {
            return 0.0;
        }

        // 名称归一化：全角转半角、trim、小写
        String na = normalize(a.name());
        String nb = normalize(b.name());

        if (na.isEmpty() || nb.isEmpty()) {
            return 0.0;
        }

        // 仅使用字符 Jaccard 相似度（中文 KP 名称最有效的单一度量）
        return charJaccard(na, nb);
    }

    @Override
    public String getName() {
        return "fuzzy";
    }

    // ======================== 归一化 ========================

    /**
     * 名称归一化：全角转半角、去标点、去多余空格、小写。
     */
    private String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            // 全角字母数字转半角
            if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            }
            // 全角空格转半角
            if (c == '　') {
                c = ' ';
            }
            // 跳过标点符号
            if (Character.isLetterOrDigit(c) || Character.isIdeographic(c) || c == ' ') {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().trim().replaceAll("\\s+", "");
    }

    // ======================== 字符 Jaccard ========================

    private double charJaccard(String a, String b) {
        java.util.Set<Integer> setA = a.chars().boxed().collect(java.util.stream.Collectors.toSet());
        java.util.Set<Integer> setB = b.chars().boxed().collect(java.util.stream.Collectors.toSet());

        java.util.Set<Integer> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);

        java.util.Set<Integer> union = new java.util.HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}