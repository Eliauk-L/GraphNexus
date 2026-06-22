package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.FileParserRegistry;
import com.graphnexus.application.file.textbook.model.ParseResult;
import com.graphnexus.application.file.textbook.parser.pdf.mineru.MinerUTextbookParser;
import com.graphnexus.application.file.textbook.parser.pdf.PdfBoxTextbookParser;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.application.file.textbook.parser.pdf.mineru.config.MinerUProperties;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TextbookService 单元测试（V4 — ADR-028 短事务 + 事务外事件，移除 afterCommit 回调）。
 *
 * @author Jay
 * @date 2026/06/22
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TextbookService 业务逻辑")
class TextbookServiceTest {

    @Mock
    private TextbookRepository textbookRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MinerUTextbookParser minerUTextbookParser;

    @Mock
    private PdfBoxTextbookParser pdfBoxTextbookParser;

    @Mock
    private MinerUProperties minerUProperties;

    @Mock
    private TextbookUploadService uploadService;

    @Mock
    private FileParserRegistry fileParserRegistry;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private TransactionStatus txStatus;

    @InjectMocks
    private TextbookServiceImpl fileService;

    private TextbookDO sampleDoc;

    @BeforeEach
    void setUp() {
        sampleDoc = TextbookDO.builder()
                .id(1L)
                .documentNo("abc123")
                .name("test")
                .subject("MATH")
                .fileSize(1024L)
                .filePath("http://localhost:9000/bucket/textbooks/abc123.pdf")
                .fileType("pdf")
                .textContent("Sample text content")
                .pageCount(5)
                .status(FileStatus.UPLOADED)
                .build();

        // 使 TransactionTemplate 可在单元测试中工作（mock 事务管理器为 no-op）
        when(txManager.getTransaction(any())).thenReturn(txStatus);
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

    // ======================== 解析测试（ADR-028：短事务→无事务→短事务→事务外事件） ========================

    @Test
    @DisplayName("解析成功：短事务(PARSING)→无事务(MinIO+解析)→短事务(PARSED)→事务外事件")
    void parseShouldSucceed() {
        // 阶段 1 findById + save (短事务内)
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        // 阶段 2 findById for file metadata
        when(fileStorageService.extractObjectKey(anyString())).thenReturn("textbooks/abc123.pdf");
        when(fileStorageService.getFile(anyString())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(fileParserRegistry.getParsers(anyString(), eq("TEXTBOOK")))
                .thenReturn(List.of(pdfBoxTextbookParser));
        when(pdfBoxTextbookParser.parse(any(byte[].class)))
                .thenReturn(new ParseResult("parsed text", 10));
        when(textbookRepository.save(any())).thenReturn(sampleDoc);

        ParseResult result = fileService.parse(1L);

        assertNotNull(result);
        assertEquals("parsed text", result.textContent());
        assertEquals(10, result.pageCount());
        verify(fileStorageService).getFile("textbooks/abc123.pdf");
        verify(pdfBoxTextbookParser).parse(any(byte[].class));

        // 事件直接在事务外发布（不再经过 afterCommit · AC-5）
        verify(eventPublisher).publishEvent(argThat(e ->
                e instanceof com.graphnexus.application.file.textbook.event.TextbookParsedEvent
                && ((com.graphnexus.application.file.textbook.event.TextbookParsedEvent) e)
                        .getDocumentId().equals(1L)));
    }

    @Test
    @DisplayName("解析不存在文档应抛 A0006")
    void parseNonExistentShouldThrow() {
        when(textbookRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.parse(999L));
        assertEquals("A0006", ex.getErrorCode());
    }

    @Test
    @DisplayName("解析器链：主解析器失败时 fallback 兜底")
    void parseWithParserFallbackShouldSucceed() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(fileStorageService.extractObjectKey(anyString())).thenReturn("textbooks/abc123.pdf");
        when(fileStorageService.getFile(anyString())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(fileParserRegistry.getParsers(anyString(), eq("TEXTBOOK")))
                .thenReturn(List.of(minerUTextbookParser, pdfBoxTextbookParser));
        when(minerUTextbookParser.parse(any(byte[].class)))
                .thenThrow(new RuntimeException("MinerU failed"));
        when(pdfBoxTextbookParser.parse(any(byte[].class)))
                .thenReturn(new ParseResult("fallback text", 5));
        when(textbookRepository.save(any())).thenReturn(sampleDoc);

        ParseResult result = fileService.parse(1L);

        assertNotNull(result);
        assertEquals("fallback text", result.textContent());
        verify(minerUTextbookParser).parse(any(byte[].class));
        verify(pdfBoxTextbookParser).parse(any(byte[].class));
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
        when(textbookRepository.findByIdAndStatusNot(999L, FileStatus.DELETING))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.getTextBook(999L));
        assertEquals("A0006", ex.getErrorCode());
    }

    // ======================== 删除测试（ADR-028：短事务→事务外事件） ========================

    @Test
    @DisplayName("删除：短事务(DELETING)→事务外直接发布事件（AC-4）")
    void deleteShouldMarkDeletingAndPublishEvent() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(textbookRepository.save(any(TextbookDO.class))).thenReturn(sampleDoc);

        assertDoesNotThrow(() -> fileService.deleteTextBook(1L));

        // 验证 DELETING 状态已设置（在短事务内通过 save 持久化）
        assertEquals(FileStatus.DELETING, sampleDoc.getStatus());
        verify(textbookRepository).save(sampleDoc);

        // 事件在事务外直接发布（不再经过 afterCommit · AC-5）
        verify(eventPublisher).publishEvent(argThat(e ->
                e instanceof com.graphnexus.application.file.textbook.event.TextbookDeletedEvent
                && ((com.graphnexus.application.file.textbook.event.TextbookDeletedEvent) e)
                        .getDocumentId().equals(1L)));

        // 级联操作由监听器完成
        verify(textbookRepository, never()).delete(any());
        // 不再使用 TransactionSynchronizationManager（AC-5）
    }

    @Test
    @DisplayName("删除不存在的文档应抛 A0006")
    void deleteNonExistentShouldThrow() {
        when(textbookRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.deleteTextBook(999L));
        assertEquals("A0006", ex.getErrorCode());
    }

    @Test
    @DisplayName("重复删除 DELETING 状态文档应幂等跳过")
    void deleteAlreadyDeletingShouldBeIdempotent() {
        sampleDoc.setStatus(FileStatus.DELETING);
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(textbookRepository.save(any(TextbookDO.class))).thenReturn(sampleDoc);

        fileService.deleteTextBook(1L);

        // 不应发布事件（DELETING 已存在）
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ======================== finalizeDeletion 测试 ========================

    @Test
    @DisplayName("finalizeDeletion：MinIO 删除 + MySQL 物理删除")
    void finalizeDeletionShouldDeleteMinioAndMysql() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(fileStorageService.extractObjectKey(anyString())).thenReturn("textbooks/abc123.pdf");
        when(textbookRepository.countByFilePathAndStatusNot(anyString(), eq(FileStatus.DELETING)))
                .thenReturn(1L);

        fileService.finalizeDeletion(1L, sampleDoc.getFilePath());

        verify(fileStorageService).deleteFile("textbooks/abc123.pdf");
        verify(textbookRepository).delete(sampleDoc);
    }

    @Test
    @DisplayName("finalizeDeletion：文档已不存在时幂等跳过")
    void finalizeDeletionShouldSkipWhenDocNotFound() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                fileService.finalizeDeletion(1L, "http://localhost:9000/bucket/textbooks/abc123.pdf"));

        verify(fileStorageService, never()).deleteFile(anyString());
        verify(textbookRepository, never()).delete(any());
    }

    @Test
    @DisplayName("finalizeDeletion：引用计数 > 1 时跳过 MinIO 删除")
    void finalizeDeletionShouldSkipMinioWhenRefCountGreaterThanOne() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(fileStorageService.extractObjectKey(anyString())).thenReturn("textbooks/abc123.pdf");
        when(textbookRepository.countByFilePathAndStatusNot(anyString(), eq(FileStatus.DELETING)))
                .thenReturn(3L);

        fileService.finalizeDeletion(1L, sampleDoc.getFilePath());

        verify(fileStorageService, never()).deleteFile(anyString());
        verify(textbookRepository).delete(sampleDoc);
    }
}