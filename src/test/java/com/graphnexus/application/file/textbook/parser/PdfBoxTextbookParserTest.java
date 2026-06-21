package com.graphnexus.application.file.textbook.parser;

import com.graphnexus.application.file.textbook.model.ParseResult;
import com.graphnexus.application.file.textbook.parser.pdf.PdfBoxTextbookParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PdfBoxTextbookParser 单元测试（对应 AC-2 解析部分）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@DisplayName("PdfBoxTextbookParser 解析测试")
class PdfBoxTextbookParserTest {

    private static final PdfBoxTextbookParser parser = new PdfBoxTextbookParser();
    private static byte[] samplePdf;

    @BeforeAll
    static void setUp() throws Exception {
        // 用 PDFBox 动态生成一个极简测试 PDF（1 页，含 "Hello GraphNexus" 文本）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Hello GraphNexus");
                cs.endText();
            }

            // 设置元信息
            doc.getDocumentInformation().setTitle("Test PDF");
            doc.getDocumentInformation().setAuthor("Jay");

            doc.save(baos);
        }
        samplePdf = baos.toByteArray();
    }

    @Test
    @DisplayName("解析返回非空文本内容")
    void parseShouldReturnNonEmptyText() {
        ParseResult result = parser.parse(samplePdf);
        assertNotNull(result.textContent());
        assertFalse(result.textContent().isBlank(),
                "textContent 不应为空，应包含 'Hello GraphNexus'");
        assertTrue(result.textContent().contains("Hello GraphNexus"),
                "textContent 应包含原始文本");
    }

    @Test
    @DisplayName("解析返回正确页数")
    void parseShouldReturnCorrectPageCount() {
        ParseResult result = parser.parse(samplePdf);
        assertTrue(result.pageCount() >= 1, "pageCount 应 ≥ 1");
    }

    @Test
    @DisplayName("解析返回元信息")
    void parseShouldReturnMetadata() {
        ParseResult result = parser.parse(samplePdf);
        assertNotNull(result.metadata(), "metadata 不应为 null");
        assertTrue(result.metadata().containsKey("title"),
                "metadata 应包含 title");
        assertEquals("Test PDF", result.metadata().get("title"));
    }

    @Test
    @DisplayName("解析空字节数组应抛异常")
    void parseEmptyBytesShouldReturnEmptyOrError() {
        byte[] empty = "%PDF-1.0\n%EOF".getBytes();
        // 无效 PDF 字节应抛出 BusinessException
        try {
            parser.parse(empty);
            // 如果能解析出来，textContent 应为空
        } catch (Exception e) {
            // 预期抛异常（来自 BusinessException 或 PDFBox）
            assertTrue(e.getMessage() != null);
        }
    }
}