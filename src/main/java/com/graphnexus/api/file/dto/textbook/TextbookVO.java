package com.graphnexus.api.file.dto.textbook;

import com.graphnexus.application.file.textbook.model.TextbookBO;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "文档元数据视图")
public class TextbookVO {

    @Schema(description = "文档 ID", example = "1")
    private Long documentId;

    @Schema(description = "文档内容指纹（MD5）", example = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")
    private String documentNo;

    @Schema(description = "文档名称", example = "初三数学二次函数讲义")
    private String name;

    @Schema(description = "学科", example = "数学")
    private String subject;

    @Schema(description = "文件大小（字节）", example = "2048576")
    private Long fileSize;

    @Schema(description = "完整文件访问路径", example = "documents/2026/06/math_quadratic.pdf")
    private String filePath;

    @Schema(description = "PDF 页数", example = "12")
    private Integer pageCount;

    @Schema(description = "文档状态：UPLOADED → PARSING → PARSED → EXTRACTING → EXTRACTED → FUSING → COMPLETED（成功）或 FAILED（异常）", example = "COMPLETED")
    private String status;

    @Schema(description = "文件类型（PDF/TXT）", example = "PDF")
    private String fileType;

    @Schema(description = "失败原因（处理失败时填充）", example = "LLM抽取失败: read timeout")
    private String failReason;

    @Schema(description = "创建时间", example = "2026-06-17T10:30:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-06-17T10:35:00")    /**
     * 从 BO 构造 VO。
     */
    public static TextbookVO from(TextbookBO bo) {
        return TextbookVO.builder()
                .documentId(bo.getId())
                .documentNo(bo.getDocumentNo())
                .name(bo.getName())
                .subject(bo.getSubject())
                .fileSize(bo.getFileSize())
                .filePath(bo.getFilePath())
                .pageCount(bo.getPageCount())
                .status(bo.getStatus())
                .fileType(bo.getFileType())
                .failReason(bo.getFailReason())
                .createTime(bo.getCreateTime()).build();
    }
}