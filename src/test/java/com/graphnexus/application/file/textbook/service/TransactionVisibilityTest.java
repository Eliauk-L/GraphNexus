package com.graphnexus.application.file.textbook.service;

import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-1: 文档状态中间态对前端轮询可见 — 事务可见性测试。
 *
 * <p>验证短事务 commit 后，状态变更对另一数据库连接/事务立即可见，
 * 确保前端轮询能观察到 PARSING / EXTRACTING 等中间态。</p>
 *
 * <p>测试策略（ADR-028 规则 1）：</p>
 * <ul>
 *   <li>在当前线程的一个 TransactionTemplate 中更新状态并 commit</li>
 *   <li>在另一个 TransactionTemplate（新连接/事务）中立即查询，断言新状态已可见</li>
 *   <li>MySQL InnoDB REPEATABLE_READ 下，已提交的数据对新事务可见</li>
 * </ul>
 *
 * @author Jay
 * @date 2026/06/24
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AC-1: 文档状态中间态事务可见性")
class TransactionVisibilityTest {

    @Autowired
    private TextbookRepository textbookRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private static Long testDocumentId;

    /**
     * 准备测试文档（UPLOADED 状态）。
     */
    @Test
    @Order(1)
    @DisplayName("准备: 创建 UPLOADED 状态测试文档")
    void setUpTestDocument() {
        TextbookDO doc = TextbookDO.builder()
                .documentNo("AC1-TEST-" + System.currentTimeMillis())
                .name("事务可见性测试文档")
                .subject("测试学科")
                .fileType("pdf")
                .fileSize(1024L)
                .filePath("test/path/ac1-test.pdf")
                .status(FileStatus.UPLOADED)
                .uploadedBy(1L)
                .createTime(LocalDateTime.now())
                .build();
        doc = textbookRepository.save(doc);
        testDocumentId = doc.getId();
        assertThat(testDocumentId).isNotNull();
    }

    /**
     * AC-1 核心验证：TransactionTemplate 短事务提交后，新事务中立即可见。
     *
     * <p>模拟 parse() 阶段 1 的行为：在 TransactionTemplate 中设 PARSING 并 commit，
     * 然后在新 TransactionTemplate 中查询，断言 PARSING 可见。</p>
     */
    @Test
    @Order(2)
    @DisplayName("AC-1: 短事务 commit 后状态在新事务中立即可见（≤1s）")
    void shortTxCommitVisibleInNewTransaction() {
        // Given: 文档状态为 UPLOADED
        TextbookDO before = textbookRepository.findById(testDocumentId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(FileStatus.UPLOADED);

        // When: 模拟 parse() 阶段 1 — 短事务内设 PARSING 并 commit
        TransactionTemplate writeTx = new TransactionTemplate(txManager);
        writeTx.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            doc.setStatus(FileStatus.PARSING);
            doc.setFailReason(null);
            textbookRepository.save(doc);
        });
        // PARSING 已提交

        // Then: 在新事务中立即查询，断言 PARSING 可见
        long start = System.currentTimeMillis();
        TransactionTemplate readTx = new TransactionTemplate(txManager);
        readTx.setReadOnly(true);
        FileStatus observedStatus = readTx.execute(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            return doc.getStatus();
        });
        long elapsed = System.currentTimeMillis() - start;

        assertThat(observedStatus).isEqualTo(FileStatus.PARSING);
        assertThat(elapsed).isLessThan(1000); // ≤1s（AC-1 要求）
    }

    /**
     * AC-1 轮询模拟：在状态变更后，另一个"连接"的查询能观察到中间态。
     *
     * <p>使用独立 TransactionTemplate（模拟独立 HTTP 请求/DB 连接），
     * 验证前端 2s 轮询间隔下状态变更必定可见。</p>
     */
    @Test
    @Order(3)
    @DisplayName("AC-1: 轮询模拟 — 独立事务可见中间态")
    void pollingSimulationSeesIntermediateStatus() {
        // 先恢复到 UPLOADED
        TransactionTemplate resetTx = new TransactionTemplate(txManager);
        resetTx.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            doc.setStatus(FileStatus.UPLOADED);
            textbookRepository.save(doc);
        });

        // 验证 UPLOADED 在新事务中可见
        TransactionTemplate readTx1 = new TransactionTemplate(txManager);
        readTx1.setReadOnly(true);
        FileStatus status1 = readTx1.execute(status ->
                textbookRepository.findById(testDocumentId).orElseThrow().getStatus());
        assertThat(status1).isEqualTo(FileStatus.UPLOADED);

        // 变更到 PARSING（模拟 parse() 阶段 1）
        TransactionTemplate writeTx = new TransactionTemplate(txManager);
        writeTx.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            doc.setStatus(FileStatus.PARSING);
            textbookRepository.save(doc);
        });

        // 立即在新事务中查询（模拟前端 GET /list 轮询）
        TransactionTemplate readTx2 = new TransactionTemplate(txManager);
        readTx2.setReadOnly(true);
        FileStatus status2 = readTx2.execute(status ->
                textbookRepository.findById(testDocumentId).orElseThrow().getStatus());
        assertThat(status2).isEqualTo(FileStatus.PARSING);

        // 变更到 PARSED（模拟 parse() 阶段 3）
        TransactionTemplate writeTx2 = new TransactionTemplate(txManager);
        writeTx2.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            doc.setStatus(FileStatus.PARSED);
            doc.setTextContent("test content");
            textbookRepository.save(doc);
        });

        // 立即在新事务中查询
        TransactionTemplate readTx3 = new TransactionTemplate(txManager);
        readTx3.setReadOnly(true);
        FileStatus status3 = readTx3.execute(status ->
                textbookRepository.findById(testDocumentId).orElseThrow().getStatus());
        assertThat(status3).isEqualTo(FileStatus.PARSED);
    }

    /**
     * 验证状态回退补偿（UPLOADED→PARSING→UPLOADED）在新事务中可见。
     */
    @Test
    @Order(4)
    @DisplayName("AC-1 补偿: 状态回退在新事务中可见")
    void compensationRollbackVisibleInNewTransaction() {
        // 设 PARSING
        TransactionTemplate tx1 = new TransactionTemplate(txManager);
        tx1.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            doc.setStatus(FileStatus.PARSING);
            textbookRepository.save(doc);
        });

        // 模拟解析失败 → 回退 UPLOADED
        TransactionTemplate tx2 = new TransactionTemplate(txManager);
        tx2.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            doc.setStatus(FileStatus.UPLOADED);
            doc.setFailReason("事务可见性测试: 模拟解析失败回退");
            textbookRepository.save(doc);
        });

        // 新事务中验证回退结果
        TransactionTemplate readTx = new TransactionTemplate(txManager);
        readTx.setReadOnly(true);
        readTx.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(testDocumentId).orElseThrow();
            assertThat(doc.getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(doc.getFailReason()).contains("模拟解析失败回退");
        });
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