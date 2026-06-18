package com.graphnexus.application.graph.core.service;

import com.graphnexus.application.graph.construction.service.ExtractionService;
import com.graphnexus.application.graph.core.service.impl.GraphServiceImpl;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mysql.file.FileDO;
import com.graphnexus.infrastructure.mysql.file.FileRepository;
import com.graphnexus.infrastructure.mysql.file.FileStatus;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * GraphService 单元测试（对应 AC-6 空文本/未就绪文档拒绝）。
 *
 * @author Jay
 * @date 2026/06/13
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GraphService 业务校验测试")
class GraphServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private ExtractionService extractionService;

    @Mock
    private GraphNodeRepository graphNodeRepository;

    @InjectMocks
    private GraphServiceImpl graphService;

    private FileDO completedDoc;
    private FileDO uploadedDoc;
    private FileDO emptyDoc;

    @BeforeEach
    void setUp() {
        completedDoc = new FileDO();
        completedDoc.setId(1L);
        completedDoc.setName("test.pdf");
        completedDoc.setSubject("数学");
        completedDoc.setPageCount(10);
        completedDoc.setStatus(FileStatus.COMPLETED);
        completedDoc.setTextContent("二次函数的定义是...");

        uploadedDoc = new FileDO();
        uploadedDoc.setId(2L);
        uploadedDoc.setName("pending.pdf");
        uploadedDoc.setStatus(FileStatus.UPLOADED);
        uploadedDoc.setTextContent("some text");

        emptyDoc = new FileDO();
        emptyDoc.setId(3L);
        emptyDoc.setName("empty.pdf");
        emptyDoc.setStatus(FileStatus.COMPLETED);
        emptyDoc.setTextContent("");
    }

    @Test
    @DisplayName("文档不存在 → BusinessException A0006")
    void testExtractDocumentNotFound_ShouldThrow() {
        when(fileRepository.findByIdAndIsDeletedFalse(99L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(99L));
        assertEquals("A0006", ex.getErrorCode());
    }

    @Test
    @DisplayName("文档状态为 UPLOADED（非 COMPLETED） → BusinessException A0009")
    void testExtractDocumentNotCompleted_ShouldThrow() {
        when(fileRepository.findByIdAndIsDeletedFalse(2L))
                .thenReturn(Optional.of(uploadedDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(2L));
        assertEquals("A0009", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("UPLOADED"));
    }

    @Test
    @DisplayName("textContent 为空字符串 → BusinessException A0008")
    void testExtractEmptyText_ShouldThrow() {
        when(fileRepository.findByIdAndIsDeletedFalse(3L))
                .thenReturn(Optional.of(emptyDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(3L));
        assertEquals("A0008", ex.getErrorCode());
    }

    @Test
    @DisplayName("textContent 为纯空白字符 → BusinessException A0008")
    void testExtractBlankText_ShouldThrow() {
        FileDO blankDoc = new FileDO();
        blankDoc.setId(4L);
        blankDoc.setStatus(FileStatus.COMPLETED);
        blankDoc.setTextContent("   \n  \t  ");
        when(fileRepository.findByIdAndIsDeletedFalse(4L))
                .thenReturn(Optional.of(blankDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(4L));
        assertEquals("A0008", ex.getErrorCode());
    }
}