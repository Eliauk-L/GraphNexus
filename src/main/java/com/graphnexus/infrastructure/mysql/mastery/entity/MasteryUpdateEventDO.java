package com.graphnexus.infrastructure.mysql.mastery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 可重放的掌握度更新事件。 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mastery_update_event")
public class MasteryUpdateEventDO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false, unique = true)
    private String eventId;

    @Column(name = "student_no", length = 64, nullable = false)
    private String studentNo;

    @Column(name = "exam_no", length = 64, nullable = false)
    private String examNo;

    @Column(name = "subject", length = 32, nullable = false)
    private String subject;

    @Column(name = "knowledge_point_id", length = 64, nullable = false)
    private String knowledgePointId;

    @Column(name = "knowledge_point_name", length = 255, nullable = false)
    private String knowledgePointName;

    @Column(name = "score_rate", nullable = false)
    private Double scoreRate;

    @Column(name = "old_weight")
    private Double oldWeight;

    @Column(name = "new_weight", nullable = false)
    private Double newWeight;

    @Column(name = "alpha", nullable = false)
    private Double alpha;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "evidence_json", columnDefinition = "JSON")
    private String evidenceJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
