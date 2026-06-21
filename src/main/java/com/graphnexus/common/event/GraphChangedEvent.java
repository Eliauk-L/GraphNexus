package com.graphnexus.common.event;

import org.springframework.context.ApplicationEvent;

/**
 * 图谱变更通知事件 —— 任何修改 Neo4j 图数据的操作完成后发布。
 *
 * <p>无载荷信号事件（仅 source），消费者自行决定响应逻辑。
 * 当前消费者：{@code MetricsCacheInvalidator} 清空指标缓存。</p>
 *
 * <p>发布场景：</p>
 * <ol>
 *   <li>图谱构建完成（阶段一 Neo4j 写入后）</li>
 *   <li>全量融合完成（KP 合并 + MASTERS 重算后）</li>
 *   <li>增量融合完成（文档抽取后自动触发）</li>
 *   <li>成绩上传 → 图谱节点创建完成</li>
 *   <li>成绩删除 → 图谱节点清理完成</li>
 * </ol>
 *
 * <p>位于 common 包中，供多个业务模块（graph/construction、analysis/fusion、
 * file/textbook、file/grade）共用，避免跨模块耦合。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
public class GraphChangedEvent extends ApplicationEvent {

    public GraphChangedEvent(Object source) {
        super(source);
    }
}