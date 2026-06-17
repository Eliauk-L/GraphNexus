package com.graphnexus.application.graph.metrics.event;

import org.springframework.context.ApplicationEvent;

/**
 * 图谱变更通知事件 —— 任何修改 Neo4j 图数据的操作完成后发布。
 *
 * <p>无载荷信号事件（仅 source），消费者自行决定响应逻辑。
 * 当前消费者：{@link MetricsCacheInvalidator} 清空指标缓存。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
public class GraphChangedEvent extends ApplicationEvent {

    public GraphChangedEvent(Object source) {
        super(source);
    }
}