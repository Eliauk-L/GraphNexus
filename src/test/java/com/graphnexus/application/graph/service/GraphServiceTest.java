package com.graphnexus.application.graph.service;

import com.graphnexus.application.graph.extraction.ExtractionService;
import com.graphnexus.application.graph.service.impl.GraphServiceImpl;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mysql.document.DocumentDO;
import com.graphnexus.infrastructure.mysql.document.DocumentRepository;
import com.graphnexus.infrastructure.mysql.document.DocumentStatus;
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
    private DocumentRepository documentRepository;

    @Mock
    private ExtractionService extractionService;

    @Mock
    private GraphNodeRepository graphNodeRepository;

    @InjectMocks
    private GraphServiceImpl graphService;

    private DocumentDO completedDoc;
    private DocumentDO uploadedDoc;
    private DocumentDO emptyDoc;

    @BeforeEach
    void setUp() {
        completedDoc = new DocumentDO();
        completedDoc.setId(1L);
        completedDoc.setName("test.pdf");
        completedDoc.setSubject("数学");
        completedDoc.setPageCount(10);
        completedDoc.setStatus(DocumentStatus.COMPLETED);
        completedDoc.setTextContent("二次函数的定义是...");

        uploadedDoc = new DocumentDO();
        uploadedDoc.setId(2L);
        uploadedDoc.setName("pending.pdf");
        uploadedDoc.setStatus(DocumentStatus.UPLOADED);
        uploadedDoc.setTextContent("some text");

        emptyDoc = new DocumentDO();
        emptyDoc.setId(3L);
        emptyDoc.setName("empty.pdf");
        emptyDoc.setStatus(DocumentStatus.COMPLETED);
        emptyDoc.setTextContent("");
    }

    @Test
    @DisplayName("文档不存在 → BusinessException A0006")
    void testExtractDocumentNotFound_ShouldThrow() {
        when(documentRepository.findByIdAndIsDeletedFalse(99L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(99L));
        assertEquals("A0006", ex.getErrorCode());
    }

    @Test
    @DisplayName("文档状态为 UPLOADED（非 COMPLETED） → BusinessException A0009")
    void testExtractDocumentNotCompleted_ShouldThrow() {
        when(documentRepository.findByIdAndIsDeletedFalse(2L))
                .thenReturn(Optional.of(uploadedDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(2L));
        assertEquals("A0009", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("UPLOADED"));
    }

    @Test
    @DisplayName("textContent 为空字符串 → BusinessException A0008")
    void testExtractEmptyText_ShouldThrow() {
        when(documentRepository.findByIdAndIsDeletedFalse(3L))
                .thenReturn(Optional.of(emptyDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(3L));
        assertEquals("A0008", ex.getErrorCode());
    }

    @Test
    @DisplayName("textContent 为纯空白字符 → BusinessException A0008")
    void testExtractBlankText_ShouldThrow() {
        DocumentDO blankDoc = new DocumentDO();
        blankDoc.setId(4L);
        blankDoc.setStatus(DocumentStatus.COMPLETED);
        blankDoc.setTextContent("   \n  \t  ");
        when(documentRepository.findByIdAndIsDeletedFalse(4L))
                .thenReturn(Optional.of(blankDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> graphService.extract(4L));
        assertEquals("A0008", ex.getErrorCode());
    }
}