package com.graphnexus.infrastructure.mysql.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agent_task")
public class AgentTaskDO {
    @Id @Column(name = "task_id", length = 36) private String taskId;
    @Column(name = "user_id", length = 128, nullable = false) private String userId;
    @Column(name = "status", length = 20, nullable = false) private String status;
    @Column(name = "answer_text", columnDefinition = "MEDIUMTEXT") private String answerText;
    @Column(name = "fallback_reason", length = 64) private String fallbackReason;
    @Column(name = "create_time", nullable = false) private LocalDateTime createTime;
}
