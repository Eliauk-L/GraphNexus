package com.graphnexus.application.file.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档业务对象（L2 内部使用）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentBO {

    private Long id;
    private String documentNo;
    private String name;
    private String subject;
    private Long fileSize;
    private String minioPath;
    private Integer pageCount;
    private String textContent;
    private String metadataJson;
    private String status;
    private String failReason;
    private Long uploadedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}