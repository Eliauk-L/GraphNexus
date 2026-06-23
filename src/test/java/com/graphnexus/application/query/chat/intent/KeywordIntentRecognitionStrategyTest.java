package com.graphnexus.application.query.chat.intent;

import com.graphnexus.application.query.chat.model.QueryIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KeywordIntentRecognitionStrategy 单元测试 — 验证关键词匹配与 null 处理。
 */
class KeywordIntentRecognitionStrategyTest {

    private final KeywordIntentRecognitionStrategy strategy = new KeywordIntentRecognitionStrategy();

    @Test
    void shouldRecognizeStudentDiagnosisByKeyword() {
        assertEquals(QueryIntent.STUDENT_DIAGNOSIS,
                strategy.recognize("分析学生张三的数学薄弱点"));
        assertEquals(QueryIntent.STUDENT_DIAGNOSIS,
                strategy.recognize("加强数学学习"));
        assertEquals(QueryIntent.STUDENT_DIAGNOSIS,
                strategy.recognize("掌握情况分析"));
        assertEquals(QueryIntent.STUDENT_DIAGNOSIS,
                strategy.recognize("学生诊断"));
        assertEquals(QueryIntent.STUDENT_DIAGNOSIS,
                strategy.recognize("薄弱知识点"));
    }

    @Test
    void shouldReturnNullWhenNoKeywordMatches() {
        assertNull(strategy.recognize("帮我看看李四数学怎么样"));
        assertNull(strategy.recognize("你好"));
    }

    @Test
    void shouldRecognizeClassWeaknessOverviewByKeyword() {
        // 不含"薄弱""掌握""诊断""分析学生"等 STUDENT_DIAGNOSIS 关键词，仅含班级关键词
        assertEquals(QueryIntent.CLASS_WEAKNESS_OVERVIEW,
                strategy.recognize("初三(1)班全班情况"));
        assertEquals(QueryIntent.CLASS_WEAKNESS_OVERVIEW,
                strategy.recognize("某班数据分析"));
        assertEquals(QueryIntent.CLASS_WEAKNESS_OVERVIEW,
                strategy.recognize("看看三(1)班班级整体情况"));
    }

    @Test
    void shouldPrioritizeStudentDiagnosisOverClassOverview() {
        // "薄弱" 和 "分析学生" 在 LinkedHashMap 中先于 "班级"，应优先匹配 STUDENT_DIAGNOSIS
        assertEquals(QueryIntent.STUDENT_DIAGNOSIS,
                strategy.recognize("分析学生张三的数学薄弱点"));
        assertEquals(QueryIntent.STUDENT_DIAGNOSIS,
                strategy.recognize("分析初三(1)班全班数学薄弱点"));
    }

    @Test
    void shouldReturnNullForNullOrEmpty() {
        assertNull(strategy.recognize(null));
        assertNull(strategy.recognize(""));
        assertNull(strategy.recognize("   "));
    }
}