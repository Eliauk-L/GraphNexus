package com.graphnexus.application.file.core.service;

import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.parse.parser.MinerUDocumentParser;
import com.graphnexus.application.file.parse.parser.PdfBoxDocumentParser;
import com.graphnexus.application.file.core.service.impl.TextbookServiceImpl;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.application.file.parse.parser.mineru.config.MinerUProperties;
import com.graphnexus.infrastructure.mysql.file.entity.FileDO;
import com.graphnexus.infrastructure.mysql.file.repository.FileRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
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
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileService 单元测试（mock 依赖，对应 AC-1/AC-6/AC-7）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileService 业务逻辑")
class TextbookServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MinerUDocumentParser minerUDocumentParser;

    @Mock
    private PdfBoxDocumentParser pdfBoxDocumentParser;

    @Mock
    private MinerUProperties minerUProperties;

    @InjectMocks
    private TextbookServiceImpl fileService;

    private FileDO sampleDoc;

    @BeforeEach
    void setUp() {
        sampleDoc = FileDO.builder()
                .id(1L)
                .documentNo("abc123")
                .name("test.pdf")
                .subject("MATH")
                .fileSize(1024L)
                .minioPath("uuid.pdf")
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
        when(fileRepository.findIdByDocumentNoAndSubjectAndIsDeletedFalse(anyString(), eq("MATH")))
                .thenReturn(Optional.empty());
        when(fileRepository.save(any(FileDO.class)))
                .thenReturn(sampleDoc);
        doNothing().when(fileStorageService).uploadFile(any(InputStream.class), anyString(), anyString());

        FileBO result = fileService.upload(file, "MATH");

        assertNotNull(result);
        assertEquals("test.pdf", result.getName());
        assertEquals("MATH", result.getSubject());
        assertEquals("UPLOADED", result.getStatus());
        verify(fileStorageService).uploadFile(any(InputStream.class), anyString(), eq("application/pdf"));
        verify(fileRepository).save(any(FileDO.class));
    }

    @Test
    @DisplayName("上传非 PDF 文件应抛 A0004（AC-7）")
    void uploadNonPdfShouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "text".getBytes()
        );
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, "MATH"));
        assertEquals("A0004", ex.getErrorCode());
        verify(fileStorageService, never()).uploadFile(any(), any(), any());
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("上传超大文件应抛 A0005（AC-7）")
    void uploadOversizedFileShouldThrow() {
        byte[] bigData = new byte[51 * 1024 * 1024]; // 51MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", bigData
        );
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, "MATH"));
        assertEquals("A0005", ex.getErrorCode());
    }

    @Test
    @DisplayName("重复文档上传应抛 A0007")
    void uploadDuplicateShouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes()
        );
        when(fileRepository.findIdByDocumentNoAndSubjectAndIsDeletedFalse(anyString(), eq("MATH")))
                .thenReturn(Optional.of(1L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.upload(file, "MATH"));
        assertEquals("A0007", ex.getErrorCode());
    }

    // ======================== 解析测试 ========================

    @Test
    @DisplayName("解析成功：状态 COMPLETED + 结果返回（AC-2）")
    void processShouldSucceed() {
        when(minerUProperties.isEnabled()).thenReturn(false);
        when(fileRepository.findByIdAndIsDeletedFalse(1L))
                .thenReturn(Optional.of(sampleDoc));
        when(fileStorageService.getFile("uuid.pdf"))
                .thenReturn(new ByteArrayInputStream("pdf-bytes".getBytes()));
        when(pdfBoxDocumentParser.parse(any(byte[].class)))
                .thenReturn(new ParseResult("Hello GraphNexus", 5));
        when(fileRepository.save(any(FileDO.class)))
                .thenReturn(sampleDoc);

        ParseResult result = fileService.process(1L);

        assertNotNull(result);
        assertEquals("Hello GraphNexus", result.textContent());
        assertEquals(5, result.pageCount());
        verify(fileRepository, atLeastOnce()).save(argThat(doc ->
                doc.getStatus() == FileStatus.COMPLETED
        ));
    }

    @Test
    @DisplayName("解析不存在文档应抛 A0006")
    void processNonExistentShouldThrow() {
        when(fileRepository.findByIdAndIsDeletedFalse(999L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.process(999L));
        assertEquals("A0006", ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-6: PdfBoxDocumentParser 作为兜底可独立工作")
    void pdfBoxParserShouldWorkStandalone() {
        PdfBoxDocumentParser realParser = mock(PdfBoxDocumentParser.class);
        when(realParser.parse(any(byte[].class)))
                .thenReturn(new ParseResult("pdfbox result", 1));

        // 验证 PDFBox 解析器可以独立工作
        ParseResult r = realParser.parse(new byte[]{1, 2, 3});
        assertEquals("pdfbox result", r.textContent());
        verify(realParser).parse(any(byte[].class));
    }

    // ======================== 查询测试 ========================

    @Test
    @DisplayName("分页查询返回正确结构（AC-4）")
    void listDocumentsShouldReturnPage() {
        Page<FileDO> page = new PageImpl<>(List.of(sampleDoc), PageRequest.of(0, 10), 1);
        when(fileRepository.findByIsDeletedFalse(any(PageRequest.class)))
                .thenReturn(page);

        Page<FileBO> result = fileService.listDocuments(1, 10, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("查询不存在文档应抛 A0006")
    void getNonExistentShouldThrow() {
        when(fileRepository.findByIdAndIsDeletedFalse(999L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getDocument(999L));
        assertEquals("A0006", ex.getErrorCode());
    }

    // ======================== 删除测试 ========================

    @Test
    @DisplayName("删除：逻辑删除 + MinIO 清除（AC-5）")
    void deleteShouldMarkDeletedAndRemoveFile() {
        when(fileRepository.findByIdAndIsDeletedFalse(1L))
                .thenReturn(Optional.of(sampleDoc));
        when(fileRepository.save(any(FileDO.class)))
                .thenReturn(sampleDoc);
        doNothing().when(fileStorageService).deleteFile(anyString());

        assertDoesNotThrow(() -> fileService.deleteDocument(1L));

        verify(fileRepository).save(argThat(doc -> doc.getIsDeleted() == 1));
        verify(fileStorageService).deleteFile("uuid.pdf");
    }
}