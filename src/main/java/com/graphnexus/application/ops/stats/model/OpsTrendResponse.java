package com.graphnexus.application.ops.stats.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 运营仪表盘历史趋势响应 BO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsTrendResponse {

    private String metric;
    private String granularity;
    private List<TrendPoint> dataPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private double value;
    }
}