package com.graphnexus.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MetricsQuery 单元测试 — 验证缓存 key 生成与不可变性。
 *
 * @author Jay
 * @date 2026/06/17
 */
@DisplayName("MetricsQuery 缓存 key 测试")
class MetricsQueryTest {

    @Test
    @DisplayName("相同参数两次调用 toCacheKey 返回相同值（AC-4/AC-7 基础）")
    void testToCacheKey_Deterministic() {
        Set<String> nodeTypes = Set.of("KnowledgePoint", "Entity");
        Set<String> edgeTypes = Set.of("PREREQUISITE_OF");

        MetricsQuery q1 = new MetricsQuery(nodeTypes, edgeTypes, "pagerank");
        MetricsQuery q2 = new MetricsQuery(nodeTypes, edgeTypes, "pagerank");

        assertEquals(q1.toCacheKey(), q2.toCacheKey(),
                "相同参数应产生相同 MD5 key");
    }

    @Test
    @DisplayName("不同 nodeTypes 产生不同 key")
    void testToCacheKey_DifferentNodeTypes() {
        MetricsQuery q1 = new MetricsQuery(Set.of("KnowledgePoint"), Set.of(), "pagerank");
        MetricsQuery q2 = new MetricsQuery(Set.of("Student"), Set.of(), "pagerank");

        assertNotEquals(q1.toCacheKey(), q2.toCacheKey());
    }

    @Test
    @DisplayName("不同 metricName 产生不同 key（PageRank vs inDegree vs outDegree）")
    void testToCacheKey_DifferentMetricNames() {
        MetricsQuery q1 = new MetricsQuery(Set.of(), Set.of(), "pagerank");
        MetricsQuery q2 = new MetricsQuery(Set.of(), Set.of(), "inDegree");

        assertNotEquals(q1.toCacheKey(), q2.toCacheKey());
    }

    @Test
    @DisplayName("空集合生成全图 key 且确定性")
    void testToCacheKey_EmptySets() {
        MetricsQuery q1 = new MetricsQuery(Collections.emptySet(), Collections.emptySet(), "pagerank");
        MetricsQuery q2 = new MetricsQuery(Collections.emptySet(), Collections.emptySet(), "pagerank");

        assertEquals(q1.toCacheKey(), q2.toCacheKey());
    }

    @Test
    @DisplayName("nodeTypes 乱序但相同集合产生相同 key（TreeSet 排序保证）")
    void testToCacheKey_OrderIndependent() {
        // TreeSet 对 Set.of 的迭代顺序可能不同但最终 sorted -> 一致
        MetricsQuery q1 = new MetricsQuery(Set.of("Student", "KnowledgePoint"), Set.of(), "pagerank");
        MetricsQuery q2 = new MetricsQuery(Set.of("KnowledgePoint", "Student"), Set.of(), "pagerank");

        assertEquals(q1.toCacheKey(), q2.toCacheKey(),
                "排序后的 Set 应产生相同 key");
    }

    @Test
    @DisplayName("toCacheKey 返回非空 32 位 MD5 hex")
    void testToCacheKey_ValidMd5Format() {
        MetricsQuery q = new MetricsQuery(Set.of("KnowledgePoint"), Set.of(), "pagerank");
        String key = q.toCacheKey();

        assertNotNull(key);
        assertEquals(32, key.length(), "MD5 hex 应为 32 字符");
        assertTrue(key.matches("[0-9a-f]{32}"), "应为纯小写 hex");
    }
}