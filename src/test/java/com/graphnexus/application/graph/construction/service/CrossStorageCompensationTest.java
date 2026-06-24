package com.graphnexus.application.graph.construction.service;

import com.graphnexus.application.graph.construction.extract.ExtractionService;
import com.graphnexus.application.graph.construction.service.impl.ConstructionServiceImpl;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.neo4j.node.EntityNode;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AC-8: Neo4j 写入失败时 MySQL 状态回退补偿测试。
 *
 * <p>验证 ADR-029 跨存储补偿策略：当 Neo4j 图谱构建失败时，
 * MySQL 文档状态从 EXTRACTING 回退到 PARSED + failReason，
 * 确保系统进入可恢复的已知状态而非静默不一致。</p>
 *
 * <p><b>测试场景：</b></p>
 * <ol>
 *   <li>创建 PARSED 状态文档</li>
 *   <li>Mock ExtractionService 返回有效抽取结果</li>
 *   <li>Mock ConstructionGraphRepository.save() 抛出 RuntimeException</li>
 *   <li>调用 extract() → 断言 BusinessException 抛出</li>
 *   <li>验证文档状态回退到 PARSED + failReason 非空</li>
 * </ol>
 *
 * @author Jay
 * @date 2026/06/24
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AC-8: 跨存储补偿 — Neo4j 失败 → MySQL 状态回退")
class CrossStorageCompensationTest {

    @Autowired
    private ConstructionServiceImpl constructionService;

    @Autowired
    private TextbookRepository textbookRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    @MockBean
    private ConstructionGraphRepository constructionGraphRepository;

    @MockBean
    private ExtractionService extractionService;

    private static Long testDocumentId;

    /**
     * 准备测试文档（PARSED 状态，含文本内容）。
     */
    @Test
    @Order(1)
    @DisplayName("准备: 创建 PARSED 状态测试文档")
    void setUpTestDocument() {
        TextbookDO doc = TextbookDO.builder()
                .documentNo("AC8-TEST-" + System.currentTimeMillis())
                .name("跨存储补偿测试文档")
                .subject("测试学科")
                .fileType("pdf")
                .fileSize(1024L)
                .filePath("test/path/ac8-test.pdf")
                .textContent("这是用于测试跨存储补偿的文本内容。包含足够的信息供 LLM 抽取。")
                .pageCount(1)
                .status(FileStatus.PARSED)
                .uploadedBy(1L)
                .createTime(LocalDateTime.now())
                .build();
        doc = textbookRepository.save(doc);
        testDocumentId = doc.getId();
        assertThat(testDocumentId).isNotNull();
    }

    /**
     * AC-8 核心验证：Neo4j 写入失败 → 状态回退 PARSED + failReason。
     *
     * <p>模拟 extract() 流程：</p>
     * <ul>
     *   <li>阶段 1：短事务设 EXTRACTING → 成功 commit</li>
     *   <li>阶段 2：LLM 抽取（mock 返回有效结果）→ 成功</li>
     *   <li>阶段 3：Neo4j 写入（mock 抛异常）→ 触发 failExtraction 补偿</li>
     *   <li>补偿结果：status=PARSED + failReason 非空</li>
     * </ul>
     */
    @Test
    @Order(2)
    @DisplayName("AC-8: Neo4j save() 失败 → 状态回退 PARSED + failReason")
    void neo4jFailureShouldCompensateToParsed() {
        // Given: 文档在 PARSED 状态
        TextbookDO before = textbookRepository.findById(testDocumentId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(FileStatus.PARSED);

        // Mock LLM 抽取返回有效结果
        ExtractionService.ExtractionResult mockExtractionResult =
                new ExtractionService.ExtractionResult(
                        List.of(new EntityNode("CONCEPT", "测试实体", "测试描述", 1, "1", null)),
                        Collections.emptyList(),  // knowledgePoints
                        Collections.emptyList(),  // categories
                        Collections.emptyList(),  // edges
                        Collections.emptyList()   // extensionNodes
                );
        when(extractionService.extract(anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockExtractionResult);

        // Mock ConstructionGraphRepository.deleteByDocumentId（阶段 3 的第一步，可成功）
        doNothing().when(constructionGraphRepository).deleteByDocumentId(anyString());

        // Mock ConstructionGraphRepository.save() 抛出异常（阶段 3 的任一 save 失败）
        doThrow(new RuntimeException("Neo4j 写入失败: 模拟节点保存异常"))
                .when(constructionGraphRepository).save(any());

        // When: 调用 extract()
        assertThatThrownBy(() -> constructionService.extract(testDocumentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("图谱构建失败");

        // Then: 文档状态回退到 PARSED + failReason 非空
        TextbookDO after = textbookRepository.findById(testDocumentId).orElseThrow();
        assertThat(after.getStatus())
                .as("Neo4j 失败后状态应回退到 PARSED")
                .isEqualTo(FileStatus.PARSED);
        assertThat(after.getFailReason())
                .as("应记录失败原因")
                .isNotNull()
                .isNotEmpty()
                .contains("图谱构建失败");

        // 验证 mock 调用
        verify(extractionService, atLeastOnce())
                .extract(anyString(), anyString(), anyString(), anyInt(), anyString());
        verify(constructionGraphRepository, atLeastOnce()).deleteByDocumentId(anyString());
        verify(constructionGraphRepository, atLeastOnce()).save(any());
    }

    /**
     * AC-8 补偿自身失败场景：补偿事务中的状态回退即使失败也不影响异常传播。
     *
     * <p>验证补偿事务独立于主流程执行，补偿失败时仍向外抛出原始异常。</p>
     */
    @Test
    @Order(3)
    @DisplayName("AC-8: LLM 抽取失败也触发补偿回退")
    void llmFailureShouldAlsoCompensate() {
        // 恢复文档到 PARSED（上一步可能已修改）
        TransactionTemplate resetTx = new TransactionTemplate(txManager);
        resetTx.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            doc.setStatus(FileStatus.PARSED);
            doc.setFailReason(null);
            doc.setTextContent("恢复测试数据");
            textbookRepository.save(doc);
        });

        // Given: 文档在 PARSED 状态
        TextbookDO before = textbookRepository.findById(testDocumentId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(FileStatus.PARSED);

        // Mock LLM 抽取抛出异常（阶段 2 失败）
        when(extractionService.extract(anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("LLM API 调用超时"));

        // When: 调用 extract() → 应抛出 BusinessException
        assertThatThrownBy(() -> constructionService.extract(testDocumentId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LLM 抽取失败");

        // Then: 文档状态回退到 PARSED + failReason 包含 LLM 失败信息
        TextbookDO after = textbookRepository.findById(testDocumentId).orElseThrow();
        assertThat(after.getStatus())
                .as("LLM 失败后状态应回退到 PARSED")
                .isEqualTo(FileStatus.PARSED);
        assertThat(after.getFailReason())
                .as("应记录 LLM 失败原因")
                .isNotNull()
                .isNotEmpty()
                .contains("LLM抽取失败");

        verify(extractionService, atLeastOnce())
                .extract(anyString(), anyString(), anyString(), anyInt(), anyString());
    }

    /**
     * 清理测试数据。
     */
    @Test
    @Order(99)
    @DisplayName("清理: 删除测试文档")
    void cleanup() {
        if (testDocumentId != null) {
            textbookRepository.deleteById(testDocumentId);
        }
    }
}