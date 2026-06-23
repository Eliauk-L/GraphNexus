package com.graphnexus.api.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志文件元数据 VO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日志文件元数据")
public class LogFileVO {

    @Schema(description = "文件名", example = "graphnexus-dev.log")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件大小（人类可读）", example = "1.6 MB")
    private String fileSizeFormatted;

    @Schema(description = "最后修改时间", example = "2026-06-23 11:09:00")
    private String lastModified;
}