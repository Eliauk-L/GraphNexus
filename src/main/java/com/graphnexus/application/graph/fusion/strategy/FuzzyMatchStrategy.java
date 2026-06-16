package com.graphnexus.application.graph.fusion.strategy;

import com.graphnexus.application.graph.fusion.config.FuzzyMatchProperties;
import com.graphnexus.application.graph.fusion.model.KpCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模糊匹配策略 v1 — 多度量组合：字符 Jaccard + Bigram Jaccard + 归一化编辑距离。
 *
 * <p>见 ADR-006 + DESIGN D1。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Component
@RequiredArgsConstructor
public class FuzzyMatchStrategy implements KpMatchingStrategy {

    private final FuzzyMatchProperties fuzzyProperties;

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

        double alpha = fuzzyProperties.getAlpha();
        double beta = fuzzyProperties.getBeta();
        double gamma = fuzzyProperties.getGamma();

        double charJaccard = charJaccard(na, nb);
        double bigramJaccard = bigramJaccard(na, nb);
        double normLevenshtein = 1.0 - normalizedLevenshtein(na, nb);

        return alpha * charJaccard + beta * bigramJaccard + gamma * normLevenshtein;
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

    // ======================== Bigram Jaccard ========================

    private double bigramJaccard(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            // 短于 2 字符降级为字符 Jaccard
            return charJaccard(a, b);
        }
        java.util.Set<String> bigramsA = new java.util.HashSet<>();
        for (int i = 0; i < a.length() - 1; i++) {
            bigramsA.add(a.substring(i, i + 2));
        }
        java.util.Set<String> bigramsB = new java.util.HashSet<>();
        for (int i = 0; i < b.length() - 1; i++) {
            bigramsB.add(b.substring(i, i + 2));
        }

        java.util.Set<String> intersection = new java.util.HashSet<>(bigramsA);
        intersection.retainAll(bigramsB);

        java.util.Set<String> union = new java.util.HashSet<>(bigramsA);
        union.addAll(bigramsB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    // ======================== 归一化编辑距离 ========================

    private double normalizedLevenshtein(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 0.0;
        int distance = levenshteinDistance(a, b);
        return (double) distance / maxLen;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1,
                        Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
            }
        }
        return dp[a.length()][b.length()];
    }
}