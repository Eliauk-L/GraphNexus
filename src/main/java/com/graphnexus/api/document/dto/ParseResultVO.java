package com.graphnexus.api.document.dto;

import com.graphnexus.application.document.model.ParseResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 解析结果视图对象（L1 返回前端）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档解析结果视图")
public class ParseResultVO {

    @Schema(description = "文档 ID", example = "1")
    private Long documentId;

    @Schema(description = "解析后的文本内容（Markdown 格式，含 LaTeX 公式）", example = "## 第一章 二次函数\n\n二次函数的标准形式为 $y = ax^2 + bx + c$...")
    private String textContent;

    @Schema(description = "PDF 页数", example = "12")
    private int pageCount;

    @Schema(description = "解析元数据（解析器名称、模型版本、耗时等）", example = "{\"parser\": \"mineru-v4\", \"model\": \"vlm\", \"duration_ms\": \"15200\"}")
    private Map<String, String> metadata;

    /**
     * 从 ParseResult 构造 VO。
     */
    public static ParseResultVO from(Long documentId, ParseResult result) {
        return ParseResultVO.builder()
                .documentId(documentId)
                .textContent(result.textContent())
                .pageCount(result.pageCount())
                .metadata(result.metadata())
                .build();
    }
}