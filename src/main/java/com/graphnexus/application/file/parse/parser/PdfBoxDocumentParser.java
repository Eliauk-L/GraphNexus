package com.graphnexus.application.file.parse.parser;

import com.graphnexus.application.file.parse.model.FileParseType;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Apache PDFBox 文档解析器实现。
 *
 * <p>实现 {@link DocumentParser} 接口，使用 PDFBox 3.x 提取 PDF 文本、页数和元信息。
 * 后续可替换为 {@code MinerUDocumentParser}，调用方无需修改。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@Slf4j
@Service
public class PdfBoxDocumentParser implements DocumentParser {

    /**
     * 解析 PDF 字节数组。
     *
     * @param pdfBytes PDF 文件的完整字节数组
     * @return 解析结果（文本内容 + 页数 + 元信息）
     * @throws BusinessException 解析失败时抛出 A0004
     */
    @Override
    public ParseResult parse(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String textContent = extractText(document);
            int pageCount = document.getNumberOfPages();
            Map<String, String> metadata = extractMetadata(document);

            log.debug("PDF 解析完成: pages={}, textLength={}", pageCount,
                    textContent != null ? textContent.length() : 0);
            return new ParseResult(textContent, pageCount, metadata);

        } catch (IOException e) {
            log.error("PDF 解析失败", e);
            throw new BusinessException(
                    ErrorCode.A0004,
                    "PDF 文件解析失败: " + e.getMessage(),
                    "该 PDF 文件可能已损坏或格式不兼容"
            );
        }
    }

    /**
     * 提取全文文本。
     */
    private String extractText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        // 按页排序
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

    /**
     * 提取 PDF 元信息（标题/作者/主题/创建日期等）。
     */
    private Map<String, String> extractMetadata(PDDocument document) {
        PDDocumentInformation info = document.getDocumentInformation();
        Map<String, String> metadata = new HashMap<>();

        putIfNotNull(metadata, "title", info.getTitle());
        putIfNotNull(metadata, "author", info.getAuthor());
        putIfNotNull(metadata, "subject", info.getSubject());
        putIfNotNull(metadata, "creator", info.getCreator());
        putIfNotNull(metadata, "producer", info.getProducer());
        putIfNotNull(metadata, "creationDate", info.getCreationDate() != null
                ? info.getCreationDate().toString() : null);

        return metadata;
    }

    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    @Override
    public FileParseType supportedType() {
        return FileParseType.DOCUMENT;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".pdf");
    }
}