package com.graphnexus.application.ops.stats.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 运营仪表盘摘要响应 BO。
 * 含三大统计维度：系统使用量、文档处理量、图谱分布。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsSummaryResponse {

    private UsageSummary usage;
    private DocumentSummary documents;
    private GraphSummary graph;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageSummary {
        private long activeUsers;
        private long loginCount;
        private long documentUploadCount;
        private long documentProcessCount;
        private long qaAskCount;
        private List<OperationCount> operationDistribution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationCount {
        private String type;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSummary {
        private long total;
        private Map<String, Long> byStatus;
        private Map<String, Long> bySubject;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphSummary {
        private Map<String, Long> nodesByType;
        private Map<String, Long> edgesByType;
        private Map<String, SubjectGraphDistribution> bySubject;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectGraphDistribution {
        private Map<String, Long> nodes;
        private Map<String, Long> edges;
    }
}