package com.graphnexus.infrastructure.mineru.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mineru.config.MinerUProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * MinerUClient 单元测试 — v1 上传 + v4 解析。
 *
 * @author Jay
 * @date 2026/06/16
 */
@DisplayName("MinerUClient v1上传 + v4解析")
class MinerUClientTest {

    private MinerUClient minerUClient;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MinerUProperties properties = new MinerUProperties();
        properties.setEnabled(true);
        MinerUProperties.Api api = new MinerUProperties.Api();
        api.setBaseUrl("https://mineru.net");
        api.setToken("test-token");
        api.setModelVersion("vlm");
        api.setPollTimeout(Duration.ofSeconds(5));
        api.setPollInterval(Duration.ofMillis(100));
        properties.setApi(api);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        minerUClient = new MinerUClient(builder, properties, objectMapper);
    }

    @Test
    @DisplayName("submitTask 成功返回 taskId 和 fileUrl（v1，无需 Token）")
    void submitTaskShouldReturnTaskIdAndFileUrl() {
        mockServer.expect(requestTo("https://mineru.net/api/v1/agent/parse/file"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "msg": "ok",
                          "data": {
                            "task_id": "task-abc-123",
                            "file_url": "https://oss.example.com/upload/doc.pdf?sign=abc"
                          }
                        }""", MediaType.APPLICATION_JSON));

        MinerUClient.TaskSubmitResult result = minerUClient.submitTask("test.pdf");

        assertThat(result.taskId()).isEqualTo("task-abc-123");
        assertThat(result.fileUrl()).contains("oss.example.com");
        mockServer.verify();
    }

    @Test
    @DisplayName("submitTask API 返回错误码时抛 BusinessException")
    void submitTaskShouldThrowOnApiError() {
        mockServer.expect(requestTo("https://mineru.net/api/v1/agent/parse/file"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": -30001,
                          "msg": "文件大小超出轻量接口限制"
                        }""", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> minerUClient.submitTask("large.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MinerU 提交任务失败");
        mockServer.verify();
    }

    @Test
    @DisplayName("submitTask 网络异常时抛 BusinessException C0001")
    void submitTaskShouldThrowOnNetworkError() {
        mockServer.expect(requestTo("https://mineru.net/api/v1/agent/parse/file"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> minerUClient.submitTask("test.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MinerU API 调用失败");
    }

    @Test
    @DisplayName("pollTaskResult 轮询到 done 返回 fullZipUrl（v4）")
    void pollTaskResultShouldReturnFullZipUrlWhenDone() {
        // 第一次返回 running，第二次返回 done
        mockServer.expect(requestTo("https://mineru.net/api/v4/extract/task/task-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "task_id": "task-123",
                            "state": "running",
                            "extract_progress": {
                              "extracted_pages": 1,
                              "total_pages": 3
                            }
                          }
                        }""", MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://mineru.net/api/v4/extract/task/task-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "task_id": "task-123",
                            "state": "done",
                            "full_zip_url": "https://cdn.example.com/result.zip"
                          }
                        }""", MediaType.APPLICATION_JSON));

        MinerUClient.TaskPollResult result = minerUClient.pollTaskResult("task-123");

        assertThat(result.isDone()).isTrue();
        assertThat(result.fullZipUrl()).isEqualTo("https://cdn.example.com/result.zip");
    }

    @Test
    @DisplayName("pollTaskResult 返回 failed 时标记 isFailed")
    void pollTaskResultShouldReturnFailedState() {
        mockServer.expect(requestTo("https://mineru.net/api/v4/extract/task/task-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "task_id": "task-123",
                            "state": "failed",
                            "err_msg": "解析失败：文件损坏"
                          }
                        }""", MediaType.APPLICATION_JSON));

        MinerUClient.TaskPollResult result = minerUClient.pollTaskResult("task-123");

        assertThat(result.isFailed()).isTrue();
        assertThat(result.errMsg()).contains("文件损坏");
    }

    @Test
    @DisplayName("downloadAndExtractMarkdown 下载 zip 并提取 full.md")
    void downloadAndExtractMarkdownShouldExtractFullMd() throws Exception {
        byte[] zipBytes = createTestZip("full.md", "# Test Markdown\n\n$E=mc^2$");

        mockServer.expect(requestTo("https://cdn.example.com/result.zip"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(zipBytes, MediaType.APPLICATION_OCTET_STREAM));

        String markdown = minerUClient.downloadAndExtractMarkdown("https://cdn.example.com/result.zip");

        assertThat(markdown).contains("# Test Markdown");
        assertThat(markdown).contains("$E=mc^2$");
        mockServer.verify();
    }

    @Test
    @DisplayName("downloadAndExtractMarkdown zip 中无 full.md 时抛异常")
    void downloadAndExtractMarkdownShouldThrowWhenNoFullMd() throws Exception {
        byte[] zipBytes = createTestZip("other.txt", "not markdown");

        mockServer.expect(requestTo("https://cdn.example.com/result.zip"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(zipBytes, MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(() -> minerUClient.downloadAndExtractMarkdown("https://cdn.example.com/result.zip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("full.md");
    }

    // ======================== 工具方法 ========================

    private byte[] createTestZip(String entryName, String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}