package com.graphnexus.api.document.dto;

import com.graphnexus.application.document.model.ParseResult;
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
public class ParseResultVO {

    private Long documentId;
    private String textContent;
    private int pageCount;
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