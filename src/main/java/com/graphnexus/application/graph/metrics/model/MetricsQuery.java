package com.graphnexus.application.graph.metrics.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;

/**
 * 图指标查询参数对象 —— 不可变 record，供 MetricsService → GdsAdapter 传递投影参数。
 *
 * <p>{@link #toCacheKey()} 对相同参数的两次调用返回相同 MD5 值，用于 Caffeine 缓存 key。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
public record MetricsQuery(
        Set<String> nodeTypes,
        Set<String> edgeTypes,
        String metricName
) {

    /**
     * 生成缓存 key —— 拼接 {@code metricName|sortedNodeTypes|sortedEdgeTypes} 后 MD5 hash。
     *
     * <p>使用 {@link TreeSet} 确保排序一致（去重 + 字母序）。</p>
     */
    public String toCacheKey() {
        String joined = metricName + "|"
                + String.join(",", new TreeSet<>(nodeTypes)) + "|"
                + String.join(",", new TreeSet<>(edgeTypes));
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed to be available on all JVMs
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}