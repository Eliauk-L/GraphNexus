package com.graphnexus.infrastructure.mineru.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mineru.config.MinerUProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU v4 API 客户端。
 *
 * <p>封装 MinerU v4 精准解析 API 的完整调用链：申请上传链接 → PUT 文件 → 轮询结果 → 下载并解压 Markdown。
 * 使用 Spring 6.1 {@link RestClient}（同步 HTTP 客户端，无需额外依赖）。</p>
 *
 * <p>v4 API 文档：docs/mineru-api.md</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Service
public class MinerUClient {

    private final RestClient restClient;
    private final RestClient.Builder restClientBuilder;
    private final MinerUProperties properties;
    private final ObjectMapper objectMapper;

    public MinerUClient(RestClient.Builder restClientBuilder, MinerUProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
        this.restClient = restClientBuilder
                .baseUrl(properties.getApi().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApi().getToken())
                .build();
    }

    /**
     * 提交单个文件的批量上传申请。
     *
     * <p>调用 {@code POST /api/v4/file-urls/batch}，MinerU 返回预签名上传 URL，
     * 上传完成后系统自动提交解析任务。</p>
     *
     * @param fileName 文件名（含扩展名，如 demo.pdf）
     * @return 包含 batchId 和 fileUrl 的结果
     */
    public BatchSubmitResult submitBatch(String fileName) {
        Map<String, Object> requestBody = new HashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("name", fileName);
        files.add(fileInfo);
        requestBody.put("files", files);
        requestBody.put("model_version", properties.getApi().getModelVersion());
        requestBody.put("enable_formula", properties.getParse().isEnableFormula());
        requestBody.put("enable_table", properties.getParse().isEnableTable());
        requestBody.put("language", properties.getParse().getLanguage());

        log.info("MinerU 提交批量上传申请: fileName={}, model={}", fileName, properties.getApi().getModelVersion());

        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("MinerU batch request body: {}", jsonBody);
            String response = restClient.post()
                    .uri("/api/v4/file-urls/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("未知错误");
                throw new BusinessException(ErrorCode.C0001, "MinerU 批量申请失败: " + msg,
                        "MinerU API 返回 code=" + code + ", msg=" + msg);
            }

            JsonNode data = root.path("data");
            String batchId = data.path("batch_id").asText();
            String fileUrl = data.path("file_urls").get(0).asText();

            log.info("MinerU 批量申请成功: batchId={}", batchId);
            return new BatchSubmitResult(batchId, fileUrl);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU 批量申请异常", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU API 调用失败: " + e.getMessage(),
                    "MinerU 批量上传申请网络异常，请稍后重试");
        }
    }

    /**
     * 上传 PDF 文件到 MinerU OSS 预签名 URL。
     *
     * @param fileUrl  MinerU 返回的预签名上传 URL
     * @param pdfBytes PDF 文件字节数组
     */
    public void uploadFile(String fileUrl, byte[] pdfBytes) {
        log.info("MinerU 开始上传文件: size={} bytes", pdfBytes.length);

        try {
            // 使用独立的 RestClient（不带 Authorization 头，OSS 签名已包含认证；
            // 不设置 Content-Type，OSS 签名校验时 Content-Type 必须为空或完全匹配签名时的值）
            RestClient uploadClient = RestClient.create();
            String putResponse = uploadClient.put()
                    .uri(fileUrl)
                    .body(pdfBytes)
                    .retrieve()
                    .body(String.class);

            log.info("MinerU 文件上传成功: responseLength={}", putResponse != null ? putResponse.length() : 0);

        } catch (Exception e) {
            log.error("MinerU 文件上传失败", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU 文件上传失败: " + e.getMessage(),
                    "文件上传至 MinerU OSS 异常，请稍后重试");
        }
    }

    /**
     * 轮询批量解析结果，直到 {@code state=done} 或 {@code state=failed} 或超时。
     *
     * @param batchId 批量任务 ID
     * @return 解析结果（含 fullZipUrl）
     */
    public BatchPollResult pollBatchResult(String batchId) {
        Duration pollInterval = properties.getApi().getPollInterval();
        Duration pollTimeout = properties.getApi().getPollTimeout();
        long startTime = System.currentTimeMillis();
        long timeoutMs = pollTimeout.toMillis();

        log.info("MinerU 开始轮询解析结果: batchId={}, timeout={}s, interval={}s",
                batchId, pollTimeout.toSeconds(), pollInterval.toSeconds());

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                String response = restClient.get()
                        .uri("/api/v4/extract-results/batch/{batchId}", batchId)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(response);
                int code = root.path("code").asInt(-1);
                if (code != 0) {
                    String msg = root.path("msg").asText("未知错误");
                    throw new BusinessException(ErrorCode.C0001, "MinerU 查询结果失败: " + msg,
                            "MinerU API 返回 code=" + code);
                }

                JsonNode data = root.path("data");
                if (!data.has("extract_result") || data.path("extract_result").isEmpty()) {
                    log.debug("MinerU 批量结果为空，等待中...");
                    Thread.sleep(pollInterval.toMillis());
                    continue;
                }

                JsonNode result = data.path("extract_result").get(0);
                String state = result.path("state").asText();
                String fileName = result.path("file_name").asText();

                log.debug("MinerU 轮询状态: fileName={}, state={}", fileName, state);

                switch (state) {
                    case "done":
                        String fullZipUrl = result.path("full_zip_url").asText();
                        log.info("MinerU 解析完成: batchId={}, fullZipUrl={}", batchId, fullZipUrl);
                        return new BatchPollResult(fileName, "done", fullZipUrl, null);

                    case "failed":
                        String errMsg = result.path("err_msg").asText("解析失败");
                        log.warn("MinerU 解析失败: batchId={}, errMsg={}", batchId, errMsg);
                        return new BatchPollResult(fileName, "failed", null, errMsg);

                    case "running":
                    case "pending":
                    case "waiting-file":
                    case "converting":
                        // 继续轮询
                        if (result.has("extract_progress")) {
                            JsonNode progress = result.path("extract_progress");
                            log.debug("MinerU 解析进度: {}/{} pages",
                                    progress.path("extracted_pages").asInt(),
                                    progress.path("total_pages").asInt());
                        }
                        break;

                    default:
                        log.warn("MinerU 未知状态: {}", state);
                        break;
                }

                Thread.sleep(pollInterval.toMillis());

            } catch (BusinessException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.C0001, "MinerU 轮询被中断",
                        "解析任务轮询被中断，请重试");
            } catch (Exception e) {
                log.error("MinerU 轮询异常", e);
                throw new BusinessException(ErrorCode.C0001, "MinerU 轮询失败: " + e.getMessage(),
                        "MinerU 解析结果查询网络异常，请稍后重试");
            }
        }

        throw new BusinessException(ErrorCode.C0001, "MinerU 轮询超时: " + pollTimeout.toSeconds() + "s",
                "MinerU 解析任务超时，请稍后重试或检查文档大小");
    }

    /**
     * 下载解析结果 zip 并解压提取 full.md。
     *
     * @param fullZipUrl 解析结果 zip 文件的 CDN 链接
     * @return Markdown 文本内容
     */
    public String downloadAndExtractMarkdown(String fullZipUrl) {
        log.info("MinerU 开始下载解析结果: url={}", fullZipUrl);

        try {
            // 下载 zip（无须 Token，CDN 公开链接）
            RestClient downloadClient = restClientBuilder.baseUrl("").build();
            byte[] zipBytes = downloadClient.get()
                    .uri(fullZipUrl)
                    .retrieve()
                    .body(byte[].class);

            if (zipBytes == null || zipBytes.length == 0) {
                throw new BusinessException(ErrorCode.C0001, "MinerU 下载结果为空",
                        "MinerU 解析结果 zip 文件为空");
            }

            log.info("MinerU 下载完成: size={} bytes", zipBytes.length);

            // 解压提取 full.md
            String markdown = extractFullMd(zipBytes);
            log.info("MinerU Markdown 提取完成: length={} chars", markdown.length());
            return markdown;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU 下载或解压失败", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU 结果下载失败: " + e.getMessage(),
                    "MinerU 解析结果下载或解压异常，请稍后重试");
        }
    }

    /**
     * 从 zip 字节数组中提取 full.md 文件内容。
     */
    private String extractFullMd(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith("full.md") || "full.md".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toString(StandardCharsets.UTF_8);
                }
            }
        }
        throw new BusinessException(ErrorCode.C0001, "MinerU zip 中未找到 full.md",
                "MinerU 解析结果格式异常，zip 包中缺少 full.md 文件");
    }

    // ======================== 内部结果类 ========================

    /**
     * 批量提交结果。
     */
    public record BatchSubmitResult(String batchId, String fileUrl) {}

    /**
     * 批量轮询结果。
     */
    public record BatchPollResult(String fileName, String state, String fullZipUrl, String errMsg) {
        public boolean isDone() {
            return "done".equals(state);
        }

        public boolean isFailed() {
            return "failed".equals(state);
        }
    }
}