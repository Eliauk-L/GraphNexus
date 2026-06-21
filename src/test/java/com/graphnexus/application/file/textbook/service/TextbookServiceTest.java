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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TextbookService 单元测试（V3 — 事件通过 TransactionSynchronization.afterCommit 发布）。
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

    // ======================== 解析测试 ========================

    @Test
    @DisplayName("解析成功：从 MinIO 读取 → 解析 → 入库，通过 TransactionSynchronization 发布事件")
    void parseShouldSucceed() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(fileStorageService.extractObjectKey(anyString())).thenReturn("textbooks/abc123.pdf");
        when(fileStorageService.getFile(anyString())).thenReturn(new ByteArrayInputStream("content".getBytes()));
        when(fileParserRegistry.getParsers(anyString(), eq("TEXTBOOK")))
                .thenReturn(List.of(pdfBoxTextbookParser));
        when(pdfBoxTextbookParser.parse(any(byte[].class)))
                .thenReturn(new ParseResult("parsed text", 10));
        when(textbookRepository.saveAndFlush(any())).thenReturn(sampleDoc);

        // Mock TransactionSynchronizationManager to simulate active transaction
        try (MockedStatic<TransactionSynchronizationManager> tsMock =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsMock.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

            ParseResult result = fileService.parse(1L);

            assertNotNull(result);
            assertEquals("parsed text", result.textContent());
            assertEquals(10, result.pageCount());
            verify(fileStorageService).getFile("textbooks/abc123.pdf");
            verify(pdfBoxTextbookParser).parse(any(byte[].class));

            // 验证 TransactionSynchronization 已注册
            tsMock.verify(() ->
                    TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));

            // 模拟 afterCommit → 事件应发布
            syncCaptor.getValue().afterCommit();
            verify(eventPublisher).publishEvent(argThat(e ->
                    e instanceof com.graphnexus.application.file.textbook.event.TextbookParsedEvent
                    && ((com.graphnexus.application.file.textbook.event.TextbookParsedEvent) e)
                            .getDocumentId().equals(1L)));
        }
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
        when(textbookRepository.saveAndFlush(any())).thenReturn(sampleDoc);

        try (MockedStatic<TransactionSynchronizationManager> tsMock =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsMock.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);

            ParseResult result = fileService.parse(1L);

            assertNotNull(result);
            assertEquals("fallback text", result.textContent());
            verify(minerUTextbookParser).parse(any(byte[].class));
            verify(pdfBoxTextbookParser).parse(any(byte[].class));
        }
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

    // ======================== 删除测试（事件通过 TransactionSynchronization 发布） ========================

    @Test
    @DisplayName("删除：进入 DELETING 状态并通过 TransactionSynchronization.afterCommit 发布事件")
    void deleteShouldMarkDeletingAndRegisterSynchronization() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(textbookRepository.saveAndFlush(any(TextbookDO.class))).thenReturn(sampleDoc);

        try (MockedStatic<TransactionSynchronizationManager> tsMock =
                     mockStatic(TransactionSynchronizationManager.class)) {
            tsMock.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

            assertDoesNotThrow(() -> fileService.deleteTextBook(1L));

            // 验证 DELETING 状态已设置
            assertEquals(FileStatus.DELETING, sampleDoc.getStatus());

            // 验证 TransactionSynchronization 已注册
            tsMock.verify(() ->
                    TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));

            // 模拟 afterCommit → 事件应发布
            syncCaptor.getValue().afterCommit();
            verify(eventPublisher).publishEvent(argThat(e ->
                    e instanceof com.graphnexus.application.file.textbook.event.TextbookDeletedEvent
                    && ((com.graphnexus.application.file.textbook.event.TextbookDeletedEvent) e)
                            .getDocumentId().equals(1L)));
        }

        // 级联操作（MinIO、Neo4j、物理删除）由监听器完成，service 层不再直接调用
        verify(textbookRepository, never()).delete(any());
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

        fileService.deleteTextBook(1L);

        // 不应再次 saveAndFlush
        verify(textbookRepository, never()).saveAndFlush(any());
        // 不应与 TransactionSynchronizationManager 交互（提前返回）
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

        // MinIO 文件已删除（最后引用）
        verify(fileStorageService).deleteFile("textbooks/abc123.pdf");
        // MySQL 物理删除
        verify(textbookRepository).delete(sampleDoc);
    }

    @Test
    @DisplayName("finalizeDeletion：文档已不存在时幂等跳过")
    void finalizeDeletionShouldSkipWhenDocNotFound() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> fileService.finalizeDeletion(1L, "http://localhost:9000/bucket/textbooks/abc123.pdf"));

        // 不应尝试删除 MinIO 文件或 MySQL 记录
        verify(fileStorageService, never()).deleteFile(anyString());
        verify(textbookRepository, never()).delete(any());
    }

    @Test
    @DisplayName("finalizeDeletion：引用计数 > 1 时跳过 MinIO 删除")
    void finalizeDeletionShouldSkipMinioWhenRefCountGreaterThanOne() {
        when(textbookRepository.findById(1L)).thenReturn(Optional.of(sampleDoc));
        when(fileStorageService.extractObjectKey(anyString())).thenReturn("textbooks/abc123.pdf");
        when(textbookRepository.countByFilePathAndStatusNot(anyString(), eq(FileStatus.DELETING)))
                .thenReturn(3L); // 包括当前记录还有 2 条其他引用

        fileService.finalizeDeletion(1L, sampleDoc.getFilePath());

        // 不应删除 MinIO 文件（有其他引用）
        verify(fileStorageService, never()).deleteFile(anyString());
        // 仍应执行 MySQL 物理删除
        verify(textbookRepository).delete(sampleDoc);
    }
}