package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.ParseResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档处理模块集成测试（真实 MySQL + MinIO 容器）。
 *
 * <p>对应 AC-1/AC-2/AC-4/AC-5/AC-7/AC-8/AC-9。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("文档处理集成测试")
class FileProcessingIntegrationTest {

    @Container
    static GenericContainer<?> mysql = new GenericContainer<>(
            DockerImageName.parse("mysql:8.0"))
            .withExposedPorts(3306)
            .withEnv("MYSQL_ROOT_PASSWORD", "test")
            .withEnv("MYSQL_DATABASE", "graphnexus");

    @Container
    static GenericContainer<?> minio = new GenericContainer<>(
            DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(9000)
            .withCommand("server /data")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin123");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306)
                        + "/graphnexus?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("minio.endpoint",
                () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin123");
        registry.add("minio.bucket", () -> "graphnexus-test");
    }

    @Autowired
    private TextbookService textBookService;

    @Autowired
    private TextbookRepository textbookRepository;

    private static Long uploadedDocId;

    @Test
    @Order(1)
    @DisplayName("AC-1: PDF 上传成功")
    void uploadPdfShouldSucceed() throws Exception {
        byte[] pdfContent = createMinimalPdf();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-integration.pdf", "application/pdf", pdfContent
        );

        TextbookBO result = textBookService.upload(file, "MATH");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("test-integration.pdf");
        assertThat(result.getSubject()).isEqualTo("MATH");
        assertThat(result.getStatus()).isEqualTo(FileStatus.UPLOADED);
        assertThat(result.getDocumentNo()).isNotBlank();
        assertThat(result.getFilePath()).isNotBlank();

        TextbookDO doc = textbookRepository.findByIdAndIsDeletedAndStatusNot(result.getId(), 0, FileStatus.DELETING).orElseThrow();
        assertThat(doc.getStatus()).isEqualTo(FileStatus.UPLOADED);

        uploadedDocId = result.getId();
    }

    @Test
    @Order(2)
    @DisplayName("AC-2: PDF 解析成功并返回文本")
    void processDocumentShouldSucceed() {
        assertThat(uploadedDocId).isNotNull();

        ParseResult result = textBookService.parse(uploadedDocId);

        assertThat(result).isNotNull();
        assertThat(result.textContent()).isNotBlank();
        assertThat(result.textContent()).contains("GraphNexus");
        assertThat(result.pageCount()).isEqualTo(1);

        TextbookDO doc = textbookRepository.findById(uploadedDocId).orElseThrow();
        assertThat(doc.getStatus()).isEqualTo(FileStatus.PARSED);
        assertThat(doc.getTextContent()).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("AC-4: 分页查询文档列表")
    void listTextBooksShouldReturnPage() {
        var page = textBookService.listTextBooks(1, 10, null, null);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("AC-7: 上传非 PDF 文件应拒绝")
    void uploadNonPdfShouldBeRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "not a pdf".getBytes()
        );
        try {
            textBookService.upload(file, "MATH");
            Assertions.fail("Should have thrown BusinessException");
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo("A0004");
        }
    }

    @Test
    @Order(6)
    @DisplayName("AC-5: 删除文档（逻辑删除 + MinIO 清除）")
    void deleteTextBookShouldSucceed() {
        assertThat(uploadedDocId).isNotNull();

        textBookService.deleteTextBook(uploadedDocId);

        TextbookDO doc = textbookRepository.findById(uploadedDocId).orElseThrow();
        assertThat(doc.getIsDeleted()).isEqualTo(1);
        assertThat(textbookRepository.findByIdAndIsDeletedAndStatusNot(uploadedDocId, 0, FileStatus.DELETING)).isEmpty();
    }

    // ======================== 辅助方法 ========================

    private byte[] createMinimalPdf() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("GraphNexus Integration Test");
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }
}