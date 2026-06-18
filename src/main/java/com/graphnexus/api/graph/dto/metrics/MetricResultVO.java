package com.graphnexus.api.graph.dto.metrics;

import com.graphnexus.common.model.MetricResultBO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 图指标计算结果 VO —— L1 层返回给客户端的单条指标记录。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Data
@Schema(description = "图指标计算结果视图（PageRank / 度中心性共用结构）")
public class MetricResultVO {

    @Schema(description = "节点 ID（Neo4j elementId）", example = "4:abc123:0")
    private String nodeId;

    @Schema(description = "节点类型标签", example = "KnowledgePoint")
    private String nodeType;

    @Schema(description = "指标名称：PageRank / inDegree / outDegree", example = "PageRank")
    private String metricName;

    @Schema(description = "指标数值", example = "0.0234")
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