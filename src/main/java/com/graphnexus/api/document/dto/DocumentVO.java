package com.graphnexus.api.document.dto;

import com.graphnexus.application.document.service.DocumentBO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档视图对象（L1 返回前端）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVO {

    private Long documentId;
    private String documentNo;
    private String name;
    private String subject;
    private Long fileSize;
    private String minioPath;
    private Integer pageCount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 从 BO 构造 VO。
     */
    public static DocumentVO from(DocumentBO bo) {
        return DocumentVO.builder()
                .documentId(bo.getId())
                .documentNo(bo.getDocumentNo())
                .name(bo.getName())
                .subject(bo.getSubject())
                .fileSize(bo.getFileSize())
                .minioPath(bo.getMinioPath())
                .pageCount(bo.getPageCount())
                .status(bo.getStatus())
                .createTime(bo.getCreateTime())
                .updateTime(bo.getUpdateTime())
                .build();
    }
}