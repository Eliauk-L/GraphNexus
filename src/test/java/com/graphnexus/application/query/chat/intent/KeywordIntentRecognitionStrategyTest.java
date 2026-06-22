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
    void shouldReturnNullForNullOrEmpty() {
        assertNull(strategy.recognize(null));
        assertNull(strategy.recognize(""));
        assertNull(strategy.recognize("   "));
    }
}