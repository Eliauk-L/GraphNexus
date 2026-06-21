package com.graphnexus.infrastructure.mysql.file.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 教材文档元数据实体（JPA Entity），对应 MySQL {@code textbook} 表。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "textbook")
@EntityListeners(AuditingEntityListener.class)
public class TextbookDO {

    /** 技术主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MD5 内容指纹，用于去重 */
    @Column(name = "document_no", length = 32, nullable = false)
    private String documentNo;

    /** 文档名称 */
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /** 所属学科 MATH/PHYSICS/CHEMISTRY */
    @Column(name = "subject", length = 20, nullable = false)
    private String subject;

    /** 文件类型（扩展名，如 pdf/txt/csv） */
    @Column(name = "file_type", length = 20, nullable = false)
    private String fileType;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 完整文件访问路径 */
    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;

    /** 提取的文本内容（MEDIUMTEXT，最大 16MB） */
    @Lob
    @Column(name = "text_content", columnDefinition = "MEDIUMTEXT")
    private String textContent;

    /** 总页数 */
    @Column(name = "page_count")
    private Integer pageCount;

    /** 文档状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private FileStatus status = FileStatus.UPLOADED;

    /** 失败原因 */
    @Column(name = "fail_reason", length = 512)
    private String failReason;

    /** 上传人 ID（FK → user_account.id，可 NULL） */
    @Column(name = "uploaded_by")
    private Long uploadedBy;

    /** 创建时间（自动填充） */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}