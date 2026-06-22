package com.graphnexus.application.graph.construction.service;

import com.graphnexus.application.graph.construction.extract.ExtractionService;
import com.graphnexus.application.graph.construction.service.impl.ConstructionServiceImpl;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
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
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ConstructionService 业务校验单元测试。
 *
 * @author Jay
 * @date 2026/06/20
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConstructionService 业务校验测试")
class ConstructionServiceTest {

    @Mock
    private TextbookRepository textbookRepository;

    @Mock
    private ExtractionService extractionService;

    @Mock
    private ConstructionGraphRepository constructionGraphRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private Neo4jTransactionManager neo4jTransactionManager;

    @Mock
    private TransactionStatus txStatus;

    @InjectMocks
    private ConstructionServiceImpl constructionService;

    private TextbookDO completedDoc;
    private TextbookDO uploadedDoc;
    private TextbookDO emptyDoc;

    @BeforeEach
    void setUp() {
        // 使 TransactionTemplate 可在单元测试中工作（mock 事务管理器为 no-op · ADR-028）
        when(txManager.getTransaction(any())).thenReturn(txStatus);

        completedDoc = new TextbookDO();
        completedDoc.setId(1L);
        completedDoc.setName("test.pdf");
        completedDoc.setSubject("数学");
        completedDoc.setPageCount(10);
        completedDoc.setStatus(FileStatus.COMPLETED);
        completedDoc.setTextContent("二次函数的定义是...");

        uploadedDoc = new TextbookDO();
        uploadedDoc.setId(2L);
        uploadedDoc.setName("pending.pdf");
        uploadedDoc.setStatus(FileStatus.UPLOADED);
        uploadedDoc.setTextContent("some text");

        emptyDoc = new TextbookDO();
        emptyDoc.setId(3L);
        emptyDoc.setName("empty.pdf");
        emptyDoc.setStatus(FileStatus.COMPLETED);
        emptyDoc.setTextContent("");
    }

    @Test
    @DisplayName("文档不存在 → BusinessException A0006")
    void testExtractDocumentNotFound_ShouldThrow() {
        when(textbookRepository.findByIdAndStatusNot(99L, FileStatus.DELETING))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> constructionService.extract(99L));
        assertEquals("A0006", ex.getErrorCode());
    }

    @Test
    @DisplayName("文档状态为 UPLOADED → BusinessException A0009")
    void testExtractDocumentNotCompleted_ShouldThrow() {
        when(textbookRepository.findByIdAndStatusNot(2L, FileStatus.DELETING))
                .thenReturn(Optional.of(uploadedDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> constructionService.extract(2L));
        assertEquals("A0009", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("UPLOADED"));
    }

    @Test
    @DisplayName("textContent 为空 → BusinessException A0008")
    void testExtractEmptyText_ShouldThrow() {
        when(textbookRepository.findByIdAndStatusNot(3L, FileStatus.DELETING))
                .thenReturn(Optional.of(emptyDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> constructionService.extract(3L));
        assertEquals("A0008", ex.getErrorCode());
    }

    @Test
    @DisplayName("textContent 为纯空白 → BusinessException A0008")
    void testExtractBlankText_ShouldThrow() {
        TextbookDO blankDoc = new TextbookDO();
        blankDoc.setId(4L);
        blankDoc.setStatus(FileStatus.COMPLETED);
        blankDoc.setTextContent("   \n  \t  ");
        when(textbookRepository.findByIdAndStatusNot(4L, FileStatus.DELETING))
                .thenReturn(Optional.of(blankDoc));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> constructionService.extract(4L));
        assertEquals("A0008", ex.getErrorCode());
    }
}