package com.graphnexus.application.query.chat.intent;

import com.graphnexus.application.query.chat.model.QueryIntent;
import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IntentRecognitionService 单元测试 — 验证策略链编排逻辑。
 */
@ExtendWith(MockitoExtension.class)
class IntentRecognitionServiceTest {

    @Test
    void shouldReturnFirstNonNullStrategy() {
        var s1 = mock(IntentRecognitionStrategy.class);
        when(s1.priority()).thenReturn(10);
        when(s1.recognize("test")).thenReturn(QueryIntent.STUDENT_DIAGNOSIS);
        var s2 = mock(IntentRecognitionStrategy.class);
        when(s2.priority()).thenReturn(20);

        var service = new IntentRecognitionService(List.of(s1, s2));
        var result = service.recognize("test");

        assertEquals(QueryIntent.STUDENT_DIAGNOSIS, result);
        verify(s2, never()).recognize(any()); // s2 不应被调用
    }

    @Test
    void shouldFallbackToNextStrategyWhenFirstReturnsNull() {
        var s1 = mock(IntentRecognitionStrategy.class);
        when(s1.priority()).thenReturn(10);
        when(s1.recognize("test")).thenReturn(null);
        var s2 = mock(IntentRecognitionStrategy.class);
        when(s2.priority()).thenReturn(20);
        when(s2.recognize("test")).thenReturn(QueryIntent.STUDENT_DIAGNOSIS);

        var service = new IntentRecognitionService(List.of(s1, s2));
        var result = service.recognize("test");

        assertEquals(QueryIntent.STUDENT_DIAGNOSIS, result);
        verify(s1).recognize("test");
        verify(s2).recognize("test");
    }

    @Test
    void shouldThrowWhenAllStrategiesReturnNull() {
        var s1 = mock(IntentRecognitionStrategy.class);
        when(s1.priority()).thenReturn(10);
        when(s1.recognize("test")).thenReturn(null);
        var s2 = mock(IntentRecognitionStrategy.class);
        when(s2.priority()).thenReturn(20);
        when(s2.recognize("test")).thenReturn(null);

        var service = new IntentRecognitionService(List.of(s1, s2));

        var ex = assertThrows(BusinessException.class, () -> service.recognize("test"));
        assertTrue(ex.getMessage().contains("无法识别查询意图"));
    }

    @Test
    void shouldThrowWhenNoStrategiesRegistered() {
        var service = new IntentRecognitionService(Collections.emptyList());

        var ex = assertThrows(BusinessException.class, () -> service.recognize("test"));
        assertTrue(ex.getMessage().contains("无法识别查询意图"));
    }
}