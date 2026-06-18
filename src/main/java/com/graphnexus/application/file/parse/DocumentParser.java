package com.graphnexus.application.file.parse;

import com.graphnexus.application.file.parse.FileParseRequest;
import com.graphnexus.application.file.parse.FileParseResult;
import com.graphnexus.application.file.parse.ParseResult;

/**
 * 文档解析器接口 — 扩展自 {@link FileParser}，专注于文档类文件（PDF 等）的内容抽取。
 *
 * <p>继承体系：</p>
 * <pre>
 * FileParser（通用文件解析）
 *   └── DocumentParser（本文档解析接口）
 *         ├── PdfBoxDocumentParser
 *         └── MinerUDocumentParser
 * </pre>
 *
 * <p>实现类需提供 {@link #supportedType()} + {@link #supportedExtensions()} +
 * {@link #parse(byte[])} 三个方法。
 * {@link #parse(FileParseRequest)} 由默认实现委托给 {@link #parse(byte[])}。</p>
 *
 * <p>见 ADR-001。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
public interface DocumentParser extends FileParser {

    /**
     * 解析文档字节数组，提取文本内容、页数和元信息。
     *
     * @param pdfBytes 文档文件的完整字节数组
     * @return 解析结果
     * @throws com.graphnexus.common.exception.BusinessException 解析失败时抛出 A0004
     */
    ParseResult parse(byte[] pdfBytes);

    /**
     * {@inheritDoc}
     *
     * <p>默认实现委托给 {@link #parse(byte[])}。</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    default <T> FileParseResult<T> parse(FileParseRequest request) {
        ParseResult result = parse(request.rawBytes());
        return (FileParseResult<T>) new FileParseResult<>(result, supportedType());
    }
}