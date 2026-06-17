package com.graphnexus.application.graph.metrics.event;

import com.graphnexus.application.graph.metrics.service.MetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * MetricsCacheInvalidator 单元测试 — 验证事件 → 缓存清空链路（AC-6）。
 *
 * @author Jay
 * @date 2026/06/17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsCacheInvalidator 事件监听测试")
class MetricsCacheInvalidatorTest {

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private MetricsCacheInvalidator invalidator;

    @Test
    @DisplayName("收到 GraphChangedEvent → 调用 metricsService.clearCache()（AC-6）")
    void testOnGraphChanged_ClearsCache() {
        GraphChangedEvent event = new GraphChangedEvent(this);
        invalidator.onGraphChanged(event);

        verify(metricsService, times(1)).clearCache();
    }

    @Test
    @DisplayName("多次事件 → 多次清空")
    void testMultipleEvents_ClearsEachTime() {
        invalidator.onGraphChanged(new GraphChangedEvent(this));
        invalidator.onGraphChanged(new GraphChangedEvent(this));

        verify(metricsService, times(2)).clearCache();
    }

    @Test
    @DisplayName("GraphChangedEvent 构造正确（source 不为 null）")
    void testEvent_HasSource() {
        GraphChangedEvent event = new GraphChangedEvent("test-source");
        assert event.getSource().equals("test-source");
    }
}