package com.graphnexus.application.file.textbook.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教材文档业务对象（L2 内部使用）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextbookBO {

    private Long id;
    private String documentNo;
    private String name;
    private String subject;
    private Long fileSize;
    private String filePath;
    private Integer pageCount;
    private String textContent;
    private String status;
    private String failReason;
    private String fileType;
    private Long uploadedBy;
    private LocalDateTime createTime;
}