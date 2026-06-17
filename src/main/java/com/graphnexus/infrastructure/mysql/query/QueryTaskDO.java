package com.graphnexus.infrastructure.mysql.query;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 问答任务记录实体（JPA Entity），对应 MySQL {@code query_task} 表。
 *
 * <p>日志类表，不设 is_deleted。记录每次问答任务的完整生命周期，
 * 含 LLM 答案、剪枝子图 JSON、Token 用量、错误信息等。
 * 见 DESIGN D9 + ADR-012。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "query_task")
@EntityListeners(AuditingEntityListener.class)
public class QueryTaskDO {

    /** 技术主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务唯一标识（UUID） */
    @Column(name = "task_id", length = 36, nullable = false, unique = true)
    private String taskId;

    /** 用户原始问题 */
    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    /** 目标学生姓名 */
    @Column(name = "student_name", length = 128)
    private String studentName;

    /** 目标学生学号 */
    @Column(name = "student_no", length = 64)
    private String studentNo;

    /** 学科 */
    @Column(name = "subject", length = 32)
    private String subject;

    /** 识别到的意图类型（如 STUDENT_DIAGNOSIS） */
    @Column(name = "intent", length = 32)
    private String intent;

    /** 任务状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QueryTaskStatus status;

    /** LLM 生成的分析答案（Markdown 格式） */
    @Column(name = "answer", columnDefinition = "MEDIUMTEXT")
    private String answer;

    /** 剪枝子图 JSON（PrunedSubgraph 序列化） */
    @Column(name = "subgraph_json", columnDefinition = "MEDIUMTEXT")
    private String subgraphJson;

    /** Token 用量 JSON（prunedNodes/prunedEdges/estimatedTokens/promptTokens/completionTokens） */
    @Column(name = "token_usage_json", columnDefinition = "JSON")
    private String tokenUsageJson;

    /** 失败时的错误信息 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** LLM 调用重试次数 */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /** 任务总耗时（毫秒） */
    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /** 创建时间（自动填充） */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间（自动填充） */
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}