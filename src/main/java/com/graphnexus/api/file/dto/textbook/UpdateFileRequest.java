package com.graphnexus.api.file.dto.textbook;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档更新请求体（L1 接收前端 JSON）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Schema(description = "文档更新请求体")
public class UpdateFileRequest {

    /** 新文档名称（必填，不可为空） */
    @NotBlank(message = "文档名称不能为空")
    @Schema(description = "新文档名称", example = "初三数学二次函数讲义（修订版）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}