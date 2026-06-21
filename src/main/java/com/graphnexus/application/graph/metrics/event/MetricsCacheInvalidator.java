package com.graphnexus.application.graph.metrics.event;

import com.graphnexus.application.graph.metrics.service.MetricsService;
import com.graphnexus.common.event.GraphChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 图谱变更事件监听器 —— 自动清空指标缓存。
 *
 * <p>{@link EventListener} 默认同步执行，{@code clearCache()} 是 O(1) 操作不阻塞主流程。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsCacheInvalidator {

    private final MetricsService metricsService;

    /**
     * 监听图谱变更事件，全量清空指标缓存。
     *
     * <p>缓存清空后，下次查询自动触发 GDS 重算（Caffeine cache miss → GdsAdapter）。</p>
     */
    @EventListener
    public void onGraphChanged(GraphChangedEvent event) {
        metricsService.clearCache();
        log.debug("GraphChangedEvent 收到（source={}），指标缓存已清空",
                event.getSource().getClass().getSimpleName());
    }
}