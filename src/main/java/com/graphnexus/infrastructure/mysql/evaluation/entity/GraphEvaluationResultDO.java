package com.graphnexus.infrastructure.mysql.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "graph_evaluation_result")
@EntityListeners(AuditingEntityListener.class)
public class GraphEvaluationResultDO {

    private static final Map<GraphEvaluationStatus, EnumSet<GraphEvaluationStatus>> TRANSITIONS = Map.of(
            GraphEvaluationStatus.PENDING, EnumSet.of(GraphEvaluationStatus.RUNNING, GraphEvaluationStatus.FAILED),
            GraphEvaluationStatus.RUNNING, EnumSet.of(GraphEvaluationStatus.COMPLETED, GraphEvaluationStatus.FAILED),
            GraphEvaluationStatus.COMPLETED, EnumSet.of(GraphEvaluationStatus.PROMOTED),
            GraphEvaluationStatus.FAILED, EnumSet.noneOf(GraphEvaluationStatus.class),
            GraphEvaluationStatus.PROMOTED, EnumSet.noneOf(GraphEvaluationStatus.class));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", length = 36, nullable = false)
    private String groupId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "dataset_version", length = 64, nullable = false)
    private String datasetVersion;

    @Column(name = "dataset_hash", length = 64, nullable = false)
    private String datasetHash;

    @Column(name = "text_hash", length = 64, nullable = false)
    private String textHash;

    @Column(name = "method", length = 32, nullable = false)
    private String method;

    @Column(name = "repeat_index", nullable = false)
    @Builder.Default
    private Integer repeatIndex = 1;

    @Column(name = "graph_id", length = 64)
    private String graphId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private GraphEvaluationStatus status = GraphEvaluationStatus.PENDING;

    @Column(name = "metrics_json", columnDefinition = "MEDIUMTEXT")
    private String metricsJson;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "estimated_tokens")
    private Long estimatedTokens;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    public void transitionTo(GraphEvaluationStatus target) {
        if (target == status) return;
        if (status == null || target == null || !TRANSITIONS.get(status).contains(target)) {
            throw new IllegalStateException("非法评测状态流转: " + status + " -> " + target);
        }
        this.status = target;
    }

    public void complete(String graphId, String metricsJson, long durationMs, long estimatedTokens) {
        transitionTo(GraphEvaluationStatus.COMPLETED);
        this.graphId = graphId;
        this.metricsJson = metricsJson;
        this.durationMs = durationMs;
        this.estimatedTokens = estimatedTokens;
        this.errorMessage = null;
    }

    public void fail(String message) {
        transitionTo(GraphEvaluationStatus.FAILED);
        this.errorMessage = abbreviate(message, 512);
    }

    private String abbreviate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
