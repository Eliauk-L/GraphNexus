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

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * MinerU v1 Agent API 客户端。
 *
 * <p>免 Token，IP 限频。上传 + 轮询 + 下载全走 v1。</p>
 * <p>调用链：submitTask → PUT 文件 → pollTaskResult → downloadMarkdown</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Service
public class MinerUClient {

    /** v1 API 客户端（免 Token） */
    private final RestClient v1Client;

    private final MinerUProperties properties;
    private final ObjectMapper objectMapper;

    public MinerUClient(RestClient.Builder restClientBuilder, MinerUProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        this.v1Client = restClientBuilder
                .baseUrl(properties.getApi().getBaseUrl())
                .build();
    }

    /**
     * 提交文件上传任务。
     *
     * <p>调用 {@code POST /api/v1/agent/parse/file}，返回 task_id + OSS 预签名上传 URL。</p>
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
     * <p>不设置任何请求头，OSS 签名已包含完整认证。</p>
     *
     * @param fileUrl  MinerU 返回的预签名上传 URL
     * @param pdfBytes PDF 文件字节数组
     */
    public void uploadFile(String fileUrl, byte[] pdfBytes) {
        log.info("MinerU 开始上传文件: size={} bytes", pdfBytes.length);

        try {
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
     * 轮询任务解析结果（v1），直到 state=done/failed 或超时。
     *
     * @param taskId 任务 ID
     * @return 解析结果（含 markdownUrl）
     */
    public TaskPollResult pollTaskResult(String taskId) {
        Duration pollInterval = properties.getApi().getPollInterval();
        Duration pollTimeout = properties.getApi().getPollTimeout();
        long startTime = System.currentTimeMillis();
        long timeoutMs = pollTimeout.toMillis();

        log.info("MinerU v1 开始轮询: taskId={}, timeout={}s, interval={}s",
                taskId, pollTimeout.toSeconds(), pollInterval.toSeconds());

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                String response = v1Client.get()
                        .uri("/api/v1/agent/parse/{taskId}", taskId)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(response);
                int code = root.path("code").asInt(-1);
                if (code != 0) {
                    String msg = root.path("msg").asText("未知错误");
                    throw new BusinessException(ErrorCode.C0001, "MinerU 查询结果失败: " + msg,
                            "MinerU v1 API 返回 code=" + code);
                }

                JsonNode data = root.path("data");
                String state = data.path("state").asText();

                log.debug("MinerU 轮询状态: taskId={}, state={}", taskId, state);

                switch (state) {
                    case "done":
                        String markdownUrl = data.path("markdown_url").asText();
                        log.info("MinerU 解析完成: taskId={}, markdownUrl={}", taskId, markdownUrl);
                        return new TaskPollResult("done", markdownUrl, null);

                    case "failed":
                        String errMsg = data.path("err_msg").asText("解析失败");
                        log.warn("MinerU 解析失败: taskId={}, errMsg={}", taskId, errMsg);
                        return new TaskPollResult("failed", null, errMsg);

                    case "running":
                    case "pending":
                    case "waiting-file":
                    case "uploading":
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
                throw new BusinessException(ErrorCode.C0001, "MinerU 轮询被中断", "解析任务轮询被中断，请重试");
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
     * 下载 Markdown 解析结果（v1 返回的直接是 .md 文本，非 zip）。
     *
     * @param markdownUrl Markdown 文件的 CDN 链接
     * @return Markdown 文本内容
     */
    public String downloadMarkdown(String markdownUrl) {
        log.info("MinerU 开始下载解析结果: url={}", markdownUrl);

        try {
            RestClient downloadClient = v1Client;
            String markdown = downloadClient.get()
                    .uri(URI.create(markdownUrl))
                    .retrieve()
                    .body(String.class);

            if (markdown == null || markdown.isEmpty()) {
                throw new BusinessException(ErrorCode.C0001, "MinerU 下载结果为空",
                        "MinerU 解析结果 Markdown 文件为空");
            }

            log.info("MinerU Markdown 下载完成: length={} chars", markdown.length());
            return markdown;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU 下载失败", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU 结果下载失败: " + e.getMessage(),
                    "MinerU 解析结果下载异常，请稍后重试");
        }
    }

    // ======================== 内部结果类 ========================

    public record TaskSubmitResult(String taskId, String fileUrl) {}

    public record TaskPollResult(String state, String markdownUrl, String errMsg) {
        public boolean isDone() {
            return "done".equals(state);
        }

        public boolean isFailed() {
            return "failed".equals(state);
        }
    }
}