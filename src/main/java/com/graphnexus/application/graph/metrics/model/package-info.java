/**
 * 图指标计算领域模型 —— 查询参数与结果 BO。
 *
 * <p>{@link com.graphnexus.application.graph.metrics.model.MetricsQuery} 为不可变查询参数，
 * {@link com.graphnexus.application.graph.metrics.model.MetricResultBO} 为不可变计算结果。
 * 全链路（L3 GDS → L2 Service → L1 API）统一使用这两个 record 传递数据。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
package com.graphnexus.application.graph.metrics.model;