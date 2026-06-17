package com.graphnexus.api.graph.dto;

import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import lombok.Data;

/**
 * 图指标计算结果 VO —— L1 层返回给客户端的单条指标记录。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Data
public class MetricResultVO {

    private String nodeId;
    private String nodeType;
    private String metricName;
    private Double metricValue;

    /**
     * 从领域 BO 构造 VO。
     */
    public static MetricResultVO from(MetricResultBO bo) {
        MetricResultVO vo = new MetricResultVO();
        vo.setNodeId(bo.nodeId());
        vo.setNodeType(bo.nodeType());
        vo.setMetricName(bo.metricName());
        vo.setMetricValue(bo.metricValue());
        return vo;
    }
}