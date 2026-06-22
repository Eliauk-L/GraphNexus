package com.graphnexus.application.query.chat.registry;

import com.graphnexus.application.analysis.strategy.SubgraphPruningStrategy;
import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PruningStrategyRegistry 单元测试 — 验证策略路由和异常处理。
 */
class PruningStrategyRegistryTest {

    @Test
    void shouldReturnStrategyForKnownIntent() {
        var mockStrategy = mock(SubgraphPruningStrategy.class);
        var registry = new PruningStrategyRegistry(Map.of("STUDENT_DIAGNOSIS", mockStrategy));

        var result = registry.get("STUDENT_DIAGNOSIS");
        assertSame(mockStrategy, result);
    }

    @Test
    void shouldThrowForUnknownIntent() {
        var mockStrategy = mock(SubgraphPruningStrategy.class);
        var registry = new PruningStrategyRegistry(Map.of("STUDENT_DIAGNOSIS", mockStrategy));

        var ex = assertThrows(BusinessException.class, () -> registry.get("UNKNOWN_INTENT"));
        assertTrue(ex.getMessage().contains("不支持的查询意图"));
    }
}