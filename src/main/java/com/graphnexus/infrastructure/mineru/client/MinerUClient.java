package com.graphnexus.infrastructure.mineru.client;

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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MinerU API 客户端（v1 上传 + v4 解析）。
 *
 * <p>上传使用 v1 Agent API（免 Token），解析使用 v4 精准解析 API。</p>
 * <p>调用链：v1 submitTask → PUT 文件 → v4 pollTask → 下载解压 Markdown</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Service
public class MinerUClient {

    /** v4 API 客户端（带 Bearer Token，用于查询和下载） */
    private final RestClient v4Client;

    /** v1 API 客户端（免 Token，仅用于提交上传任务） */
    private final RestClient v1Client;

    private final MinerUProperties properties;
    private final ObjectMapper objectMapper;

    public MinerUClient(RestClient.Builder restClientBuilder, MinerUProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        String token = properties.getApi().getToken();
        this.v4Client = restClientBuilder
                .baseUrl(properties.getApi().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + token)
                .build();

        // v1 Agent API 无需 Token
        this.v1Client = restClientBuilder
                .baseUrl(properties.getApi().getBaseUrl())
                .build();
    }

    /**
     * 提交文件上传任务（v1 Agent API）。
     *
     * <p>调用 {@code POST /api/v1/agent/parse/file}，无需 Token，
     * 返回 task_id + OSS 预签名上传 URL。</p>
     *
     * @param fileName 文件名（含扩展名）
     * @return 包含 taskId 和 fileUrl 的结果
     */
    public TaskSubmitResult submitTask(String fileName) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("file_name", fileName);
        requestBody.put("enable_formula", properties.getParse().isEnableFormula());
        requestBody.put("enable_table", properties.getParse().isEnableTable());
        requestBody.put("language", properties.getParse().getLanguage());

        log.info("MinerU v1 提交上传任务: fileName={}", fileName);

        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("MinerU v1 request body: {}", jsonBody);

            String response = v1Client.post()
                    .uri("/api/v1/agent/parse/file")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            log.debug("MinerU v1 response: {}", response);

            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("未知错误");
                throw new BusinessException(ErrorCode.C0001, "MinerU 提交任务失败: " + msg,
                        "MinerU v1 API 返回 code=" + code + ", msg=" + msg);
            }

            JsonNode data = root.path("data");
            String taskId = data.path("task_id").asText();
            String fileUrl = data.path("file_url").asText();

            log.info("MinerU v1 任务创建成功: taskId={}", taskId);
            return new TaskSubmitResult(taskId, fileUrl);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU v1 提交任务异常", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU API 调用失败: " + e.getMessage(),
                    "MinerU 任务提交网络异常，请稍后重试");
        }
    }

    /**
     * 上传 PDF 文件到 MinerU OSS 预签名 URL。
     *
     * <p>不设置任何请求头（Content-Type / Authorization），OSS 签名已包含完整认证。</p>
     *
     * @param fileUrl  MinerU 返回的预签名上传 URL
     * @param pdfBytes PDF 文件字节数组
     */
    public void uploadFile(String fileUrl, byte[] pdfBytes) {
        log.info("MinerU 开始上传文件: size={} bytes", pdfBytes.length);

        try {
            // 干净 RestClient：不设任何默认头，OSS 签名已包含认证
            RestClient uploadClient = RestClient.builder()
                    .requestInterceptor((req, body, exec) -> {
                        req.getHeaders().clear();
                        req.getHeaders().setContentLength(body.length);
                        return exec.execute(req, body);
                    })
                    .build();
            var putResponse = uploadClient.put()
                    .uri(URI.create(fileUrl))
                    .body(pdfBytes)
                    .retrieve()
                    .toBodilessEntity();

            log.info("MinerU 文件上传成功: httpStatus={}", putResponse.getStatusCode());

        } catch (Exception e) {
            log.error("MinerU 文件上传失败", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU 文件上传失败: " + e.getMessage(),
                    "文件上传至 MinerU OSS 异常，请稍后重试");
        }
    }

    /**
     * 轮询任务解析结果（v4 单任务查询），直到 state=done/failed 或超时。
     *
     * @param taskId 任务 ID
     * @return 解析结果（含 fullZipUrl）
     */
    public TaskPollResult pollTaskResult(String taskId) {
        Duration pollInterval = properties.getApi().getPollInterval();
        Duration pollTimeout = properties.getApi().getPollTimeout();
        long startTime = System.currentTimeMillis();
        long timeoutMs = pollTimeout.toMillis();

        log.info("MinerU v4 开始轮询解析结果: taskId={}, timeout={}s, interval={}s",
                taskId, pollTimeout.toSeconds(), pollInterval.toSeconds());

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                String response = v4Client.get()
                        .uri("/api/v4/extract/task/{taskId}", taskId)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(response);
                int code = root.path("code").asInt(-1);
                if (code != 0) {
                    String msg = root.path("msg").asText("未知错误");
                    throw new BusinessException(ErrorCode.C0001, "MinerU 查询结果失败: " + msg,
                            "MinerU v4 API 返回 code=" + code);
                }

                JsonNode data = root.path("data");
                String state = data.path("state").asText();

                log.debug("MinerU 轮询状态: taskId={}, state={}", taskId, state);

                switch (state) {
                    case "done":
                        String fullZipUrl = data.path("full_zip_url").asText();
                        log.info("MinerU 解析完成: taskId={}, fullZipUrl={}", taskId, fullZipUrl);
                        return new TaskPollResult("done", fullZipUrl, null);

                    case "failed":
                        String errMsg = data.path("err_msg").asText("解析失败");
                        log.warn("MinerU 解析失败: taskId={}, errMsg={}", taskId, errMsg);
                        return new TaskPollResult("failed", null, errMsg);

                    case "running":
                    case "pending":
                    case "converting":
                    case "waiting-file":
                    case "uploading":
                        // 输出进度
                        if (data.has("extract_progress")) {
                            JsonNode progress = data.path("extract_progress");
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
            RestClient downloadClient = v4Client;
            byte[] zipBytes = downloadClient.get()
                    .uri(URI.create(fullZipUrl))
                    .retrieve()
                    .body(byte[].class);

            if (zipBytes == null || zipBytes.length == 0) {
                throw new BusinessException(ErrorCode.C0001, "MinerU 下载结果为空",
                        "MinerU 解析结果 zip 文件为空");
            }

            log.info("MinerU 下载完成: size={} bytes", zipBytes.length);

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
     * 任务提交结果（v1）。
     */
    public record TaskSubmitResult(String taskId, String fileUrl) {}

    /**
     * 任务轮询结果（v4）。
     */
    public record TaskPollResult(String state, String fullZipUrl, String errMsg) {
        public boolean isDone() {
            return "done".equals(state);
        }

        public boolean isFailed() {
            return "failed".equals(state);
        }
    }
}