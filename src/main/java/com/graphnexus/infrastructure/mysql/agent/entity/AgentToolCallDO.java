package com.graphnexus.infrastructure.mysql.agent.entity;

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

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agent_tool_call")
public class AgentToolCallDO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", length = 36, nullable = false) private String taskId;
    @Column(name = "round_no", nullable = false) private Integer roundNo;
    @Column(name = "tool_name", length = 64, nullable = false) private String toolName;
    @Column(name = "arguments_json", columnDefinition = "JSON", nullable = false) private String argumentsJson;
    @Column(name = "observation_json", columnDefinition = "MEDIUMTEXT") private String observationJson;
    @Column(name = "decision_summary", length = 512) private String decisionSummary;
    @Column(name = "status", length = 20, nullable = false) private String status;
    @Column(name = "elapsed_ms") private Long elapsedMs;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "create_time", nullable = false) private LocalDateTime createTime;
}
