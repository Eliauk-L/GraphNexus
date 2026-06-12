package com.graphnexus.api.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档更新请求体（L1 接收前端 JSON）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
public class UpdateDocumentRequest {

    /** 新文档名称（必填，不可为空） */
    @NotBlank(message = "文档名称不能为空")
    private String name;
}