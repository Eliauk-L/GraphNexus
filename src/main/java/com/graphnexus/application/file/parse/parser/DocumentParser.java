package com.graphnexus.application.file.parse.parser;

import com.graphnexus.application.file.parse.model.ParseResult;

/**
 * 可扩展的文档解析接口（策略模式）。
 *
 * <p>L2 业务层定义接口契约，具体实现（PDFBox / MinerU / Tika）通过依赖注入切换。
 * 调用方 {@code DocumentService} 只依赖本接口，不依赖具体实现类。</p>
 *
 * <p>见 ADR-001: DocumentParser 接口归属 L2 层。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@FunctionalInterface
public interface DocumentParser {

    /**
     * 解析 PDF 字节数组，提取文本内容、页数和元信息。
     *
     * @param pdfBytes PDF 文件的完整字节数组
     * @return 解析结果
     * @throws com.graphnexus.common.exception.BusinessException 解析失败时抛出 A0004
     */
    ParseResult parse(byte[] pdfBytes);
}