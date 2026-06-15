package com.graphnexus.infrastructure.mysql.document;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考试成绩记录实体（JPA Entity），对应 MySQL {@code exam_record} 表。
 *
 * <p>设计决策：见 DESIGN D3（JSON 列存 score_details）+ D7（分数不存 Neo4j）+ D10（is_deleted 中间状态）。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "exam_record")
@EntityListeners(AuditingEntityListener.class)
public class ExamRecordDO {

    /** 技术主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学号 */
    @Column(name = "student_no", length = 64, nullable = false)
    private String studentNo;

    /** 学生姓名 */
    @Column(name = "name", length = 128)
    private String name;

    /** 班级 */
    @Column(name = "class_name", length = 128)
    private String className;

    /** 考试编号（CSV 提供） */
    @Column(name = "exam_no", length = 64, nullable = false)
    private String examNo;

    /** 考试名称 */
    @Column(name = "exam_name", length = 255)
    private String examName;

    /** 考试日期 */
    @Column(name = "exam_date")
    private LocalDate examDate;

    /** 学科 */
    @Column(name = "subject", length = 20)
    private String subject;

    /** 总分 */
    @Column(name = "total_score")
    private Integer totalScore;

    /** 班级排名 */
    @Column(name = "class_rank")
    private Integer classRank;

    /** 成绩明细 JSON 数组：每元素含 questionLabel/kpNames/rawScore/maxScore */
    @Column(name = "score_details", columnDefinition = "JSON")
    private String scoreDetails;

    /** CSV 文件的 MinIO 存储路径 */
    @Column(name = "csv_file_path", length = 500)
    private String csvFilePath;

    /** CSV 文件 MD5 内容指纹（用于上传判重） */
    @Column(name = "csv_md5", length = 32)
    private String csvMd5;

    /** 逻辑删除标记：0=正常 1=已删除（中间状态，见全局删除约束 C3） */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Integer isDeleted = 0;

    /** 创建时间（自动填充） */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间（自动填充） */
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 标记为删除中间状态（is_deleted = 1）。
     * 见 DESIGN D10 + CONTEXT.md 全局删除约束 C3。
     */
    public void markDeleted() {
        this.isDeleted = 1;
    }
}