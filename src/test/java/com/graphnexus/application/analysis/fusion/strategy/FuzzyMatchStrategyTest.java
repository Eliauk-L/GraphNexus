package com.graphnexus.application.analysis.fusion.strategy;

import com.graphnexus.application.analysis.fusion.config.FuzzyMatchProperties;
import com.graphnexus.application.analysis.fusion.model.KpCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FuzzyMatchStrategy 单元测试 — AC-2/3/11。
 *
 * @author Jay
 * @date 2026/06/15
 */
@DisplayName("FuzzyMatchStrategy 模糊匹配")
class FuzzyMatchStrategyTest {

    private FuzzyMatchStrategy strategy;

    @BeforeEach
    void setUp() {
        FuzzyMatchProperties props = new FuzzyMatchProperties();
        props.setAlpha(0.3);
        props.setBeta(0.5);
        props.setGamma(0.2);
        strategy = new FuzzyMatchStrategy(props);
    }

    @Test
    @DisplayName("完全相同 KP 返回 1.0")
    void identicalKpsReturnOne() {
        KpCandidate a = new KpCandidate("二次函数顶点坐标", "数学", "1", "DOCUMENT");
        KpCandidate b = new KpCandidate("二次函数顶点坐标", "数学", null, "CSV_IMPORT");
        assertEquals(1.0, strategy.match(a, b), 0.001);
    }

    @Test
    @DisplayName("跨学科 KP 不匹配（前置过滤返回 0）")
    void differentSubjectReturnsZero() {
        KpCandidate a = new KpCandidate("函数定义", "数学", "1", "DOCUMENT");
        KpCandidate b = new KpCandidate("函数定义", "物理", null, "CSV_IMPORT");
        assertEquals(0.0, strategy.match(a, b), 0.001);
    }

    @Test
    @DisplayName("AC-3: 名称部分重叠但不同知识点应低于阈值")
    void similarButDifferentKpsBelowThreshold() {
        KpCandidate a = new KpCandidate("二次函数图像", "数学", "1", "DOCUMENT");
        KpCandidate b = new KpCandidate("一次函数图像", "数学", null, "CSV_IMPORT");
        double score = strategy.match(a, b);
        // 共享"函数图像"，但 Jaccard ≈ 0.714，应 < 0.85
        assertTrue(score < 0.85, "Expected score < 0.85 but got " + score);
    }

    @Test
    @DisplayName("完全不同 KP 得分很低")
    void completelyDifferentKpsScoreLow() {
        KpCandidate a = new KpCandidate("二次函数顶点坐标", "数学", "1", "DOCUMENT");
        KpCandidate b = new KpCandidate("一元二次方程求根公式", "数学", null, "CSV_IMPORT");
        double score = strategy.match(a, b);
        assertTrue(score < 0.3, "Expected score < 0.3 but got " + score);
    }

    @Test
    @DisplayName("null 候选返回 0")
    void nullCandidateReturnsZero() {
        KpCandidate a = new KpCandidate("测试", "数学", null, null);
        assertEquals(0.0, strategy.match(a, null), 0.001);
        assertEquals(0.0, strategy.match(null, a), 0.001);
    }

    @Test
    @DisplayName("getName 返回 fuzzy")
    void getNameReturnsFuzzy() {
        assertEquals("fuzzy", strategy.getName());
    }
}