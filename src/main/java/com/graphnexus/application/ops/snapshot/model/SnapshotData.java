package com.graphnexus.application.ops.snapshot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统计快照数据 BO，对应 stats_snapshot.snapshot_data JSON 列。
 * 三层嵌套结构：usage / documents / graph + 按学科明细。
 * 见 ADR-052 JSON Schema。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotData {

    private UsageData usage;
    private DocumentData documents;
    private GraphData graph;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageData {
        private long activeUsers;
        private long loginCount;
        private long documentUploadCount;
        private long documentProcessCount;
        private long qaAskCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentData {
        private long total;
        private Map<String, Long> byStatus;
        private Map<String, Long> bySubject;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphData {
        private Map<String, Long> nodesByType;
        private Map<String, Long> edgesByType;
        private Map<String, SubjectGraphData> bySubject;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectGraphData {
        private Map<String, Long> nodes;
        private Map<String, Long> edges;
    }
}