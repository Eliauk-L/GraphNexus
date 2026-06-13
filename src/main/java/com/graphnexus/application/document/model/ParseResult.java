package com.graphnexus.application.document.model;

import java.util.Collections;
import java.util.Map;

/**
 * PDF 解析结果（不可变 record）。
 *
 * <p>由 {@link DocumentParser} 的实现类产出，包含提取的文本、页数和元信息。</p>
 *
 * @param textContent 提取的全文文本
 * @param pageCount   总页数
 * @param metadata    PDF 元信息（标题/作者/创建日期等），可为空 Map
 * @author Jay
 * @date 2026/06/12
 */
public record ParseResult(
        String textContent,
        int pageCount,
        Map<String, String> metadata
) {

    /**
     * 创建仅含文本和页数的解析结果（无元信息）。
     */
    public ParseResult(String textContent, int pageCount) {
        this(textContent, pageCount, Collections.emptyMap());
    }
}