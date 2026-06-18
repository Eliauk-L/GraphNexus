package com.graphnexus.infrastructure.mysql.file.entity;

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
 * 文档元数据实体（JPA Entity），对应 MySQL {@code document} 表。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "document")
@EntityListeners(AuditingEntityListener.class)
public class FileDO {

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

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** MinIO 存储路径 */
    @Column(name = "minio_path", length = 500, nullable = false)
    private String minioPath;

    /** PDFBox 提取的文本内容（MEDIUMTEXT，最大 16MB） */
    @Lob
    @Column(name = "text_content", columnDefinition = "MEDIUMTEXT")
    private String textContent;

    /** 总页数 */
    @Column(name = "page_count")
    private Integer pageCount;

    /** PDF 元信息 JSON（标题/作者/创建日期等） */
    @Column(name = "metadata_json", length = 2000)
    private String metadataJson;

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

    /** 逻辑删除 0=否 1=是 */
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
     * 标记为逻辑删除。
     */
    public void markDeleted() {
        this.isDeleted = 1;
    }
}