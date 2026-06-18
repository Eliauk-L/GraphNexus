package com.graphnexus.api.file.dto.core;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * 文档列表条件查询请求参数。
 *
 * @author Jay
 * @date 2026/06/18
 */
public record FileQueryRequest(
        @Parameter(description = "文件类型筛选（精确匹配，如 PDF、TXT）", example = "PDF")
        String fileType,

        @Parameter(description = "文件名模糊搜索", example = "二次函数")
        String name
) {}