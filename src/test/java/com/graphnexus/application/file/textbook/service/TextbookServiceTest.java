package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.ParseResult;
import com.graphnexus.application.file.textbook.parser.MinerUTextbookParser;
import com.graphnexus.application.file.textbook.parser.PdfBoxTextbookParser;
import com.graphnexus.application.file.textbook.pipeline.TextbookProcessingPipeline;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.application.file.textbook.parser.mineru.config.MinerUProperties;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TextbookService 单元测试（V2 — upload/pipeline 解耦，mock 依赖）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TextbookService 业务逻辑")
class TextbookServiceTest {

    @Mock
    private TextbookRepository textbookRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private GraphNodeRepository graphNodeRepository;

    @Mock
    private MinerUTextbookParser minerUTextbookParser;

    @Mock
    private PdfBoxTextbookParser pdfBoxTextbookParser;

    @Mock
    private MinerUProperties minerUProperties;

    @Mock
    private TextbookUploadService uploadService;

    @Mock
    private TextbookProcessingPipeline textbookProcessingPipeline;

    @InjectMocks
    private TextbookServiceImpl fileService;

    private TextbookDO sampleDoc;

    @BeforeEach
    void setUp() {
        sampleDoc = TextbookDO.builder()
                .id(1L)
                .documentNo("abc123")
                .name("test.pdf")
                .subject("MATH")
                .fileSize(1024L)
                .filePath("uuid.pdf")
                .fileType("pdf")
                .textContent("Sample text content")
                .pageCount(5)
                .status(FileStatus.UPLOADED)
                .build();
    }

    // ======================== 上传测试 ========================

    @Test
    @DisplayName("上传 PDF 成功（AC-1）")
    void uploadPdfShouldSucceed() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "pdf-content".getBytes()
        );

        TextbookBO expectedBO = TextbookBO.builder()
                .id(1L)
                .name("test.pdf")
                .subject("MATH")
                .status("UPLOADED")
                .build();
        when(uploadService.upload(file, "MATH")).thenReturn(expectedBO);

        TextbookBO result = fileService.upload(file, "MATH");

        assertNotNull(result);
        assertEquals("test.pdf", result.getName());
        assertEquals("MATH", result.getSubject());
        assertEquals("UPLOADED", result.getStatus());
        verify(uploadService).upload(file, "MATH");
    }

    @Test
    @DisplayName("上传不支持的文件类型应抛 A0004（AC-7）")
    void uploadUnsupportedTypeShouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xyz", "application/octet-stream", "data".getBytes()
        );
        when(uploadService.upload(file, "MATH"))
                .thenThrow(new BusinessException(com.graphnexus.common.exception.ErrorCode.A0004,
                        "不支持的文件类型: test.xyz"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, "MATH"));
        assertEquals("A0004", ex.getErrorCode());
    }

    @Test
    @DisplayName("重复文档上传应抛 A0007")
    void uploadDuplicateShouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes()
        );
        when(uploadService.upload(file, "MATH"))
                .thenThrow(new BusinessException(com.graphnexus.common.exception.ErrorCode.A0007,
                        "文档内容重复"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, "MATH"));
        assertEquals("A0007", ex.getErrorCode());
    }

    // ======================== 解析测试 ========================

    @Test
    @DisplayName("解析成功：Pipeline 处理后返回解析结果（AC-2）")
    void processShouldSucceed() {
        when(textbookProcessingPipeline.processStored(1L)).thenReturn(null);
        when(textbookRepository.findById(1L))
                .thenReturn(Optional.of(sampleDoc));

        ParseResult result = fileService.process(1L);

        assertNotNull(result);
        assertEquals("Sample text content", result.textContent());
        assertEquals(5, result.pageCount());
        verify(textbookProcessingPipeline).processStored(1L);
        verify(textbookRepository).findById(1L);
    }

    @Test
    @DisplayName("解析不存在文档应抛 A0006")
    void processNonExistentShouldThrow() {
        when(textbookProcessingPipeline.processStored(999L)).thenReturn(null);
        when(textbookRepository.findById(999L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.process(999L));
        assertEquals("A0006", ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-6: PdfBoxTextbookParser 作为兜底可独立工作")
    void pdfBoxParserShouldWorkStandalone() {
        PdfBoxTextbookParser realParser = mock(PdfBoxTextbookParser.class);
        when(realParser.parse(any(byte[].class)))
                .thenReturn(new ParseResult("pdfbox result", 1));

        ParseResult r = realParser.parse(new byte[]{1, 2, 3});
        assertEquals("pdfbox result", r.textContent());
        verify(realParser).parse(any(byte[].class));
    }

    // ======================== 查询测试 ========================

    @Test
    @DisplayName("分页查询返回正确结构（AC-4）")
    void listTextBooksShouldReturnPage() {
        Page<TextbookDO> page = new PageImpl<>(List.of(sampleDoc), PageRequest.of(0, 10), 1);
        when(textbookRepository.findByConditions(null, null, PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<TextbookBO> result = fileService.listTextBooks(1, 10, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("查询不存在文档应抛 A0006")
    void getNonExistentShouldThrow() {
        when(textbookRepository.findByIdAndIsDeletedFalse(999L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getTextBook(999L));
        assertEquals("A0006", ex.getErrorCode());
    }

    // ======================== 删除测试 ========================

    @Test
    @DisplayName("删除：逻辑删除 + MinIO 清除（AC-5）")
    void deleteShouldMarkDeletedAndRemoveFile() {
        when(textbookRepository.findById(1L))
                .thenReturn(Optional.of(sampleDoc));
        when(textbookRepository.save(any(TextbookDO.class)))
                .thenReturn(sampleDoc);
        doNothing().when(fileStorageService).deleteFile(anyString());
        doNothing().when(graphNodeRepository).deleteByDocumentId(anyString());
        when(fileStorageService.extractObjectKey(anyString())).thenReturn("uuid.pdf");

        assertDoesNotThrow(() -> fileService.deleteTextBook(1L));

        verify(textbookRepository).save(argThat(doc -> doc.getIsDeleted() == 1));
        verify(fileStorageService).deleteFile(anyString());
        verify(graphNodeRepository).deleteByDocumentId("1");
    }
}
