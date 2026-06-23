package com.graphnexus.api.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 日志分页内容 VO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日志分页内容")
public class LogContentVO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "当前页日志行")
    private List<String> lines;

    @Schema(description = "当前页码，从 1 开始")
    private Integer currentPage;

    @Schema(description = "总页数")
    private Integer totalPages;

    @Schema(description = "文件总行数")
    private Long totalLines;

    @Schema(description = "每页行数")
    private Integer pageSize;
}