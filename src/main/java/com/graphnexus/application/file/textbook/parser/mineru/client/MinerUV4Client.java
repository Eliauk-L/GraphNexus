package com.graphnexus.application.file.textbook.parser.mineru.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.application.file.textbook.parser.mineru.config.MinerUProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
 * MinerU v4 精准解析 API 客户端（需 Token，≤200MB / ≤200 页，vlm 模型）。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Component
class MinerUV4Client implements MinerUApiClient {

    private final RestClient client;
    private final MinerUProperties properties;
    private final ObjectMapper objectMapper;

    MinerUV4Client(RestClient.Builder builder, MinerUProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = builder
                .baseUrl(properties.getApi().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApi().getToken())
                .build();
    }

    @Override
    public String getVersion() {
        return "v4";
    }

    @Override
    public TaskSubmitResult submitTask(String fileName) {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("name", fileName);
        files.add(fileInfo);
        body.put("files", files);
        body.put("model_version", properties.getApi().getModelVersion());
        body.put("enable_formula", properties.getParse().isEnableFormula());
        body.put("enable_table", properties.getParse().isEnableTable());
        body.put("language", properties.getParse().getLanguage());

        log.info("MinerU v4 提交批量上传: fileName={}, model={}", fileName, properties.getApi().getModelVersion());

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("MinerU v4 request: {}", jsonBody);

            String response = client.post()
                    .uri(properties.getApi().getSubmitPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ErrorCode.C0001,
                        "MinerU 批量申请失败: " + root.path("msg").asText("未知错误"),
                        "MinerU v4 API 返回 code=" + root.path("code").asInt());
            }

            JsonNode data = root.path("data");
            return new TaskSubmitResult(
                    data.path("batch_id").asText(),
                    data.path("file_urls").get(0).asText());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU v4 提交任务异常", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU API 调用失败: " + e.getMessage(),
                    "MinerU 任务提交网络异常，请稍后重试");
        }
    }

    @Override
    public TaskPollResult pollTaskResult(String taskId) {
        Duration pollInterval = properties.getApi().getPollInterval();
        Duration pollTimeout = properties.getApi().getPollTimeout();
        long startTime = System.currentTimeMillis();
        String pollUri = properties.getApi().getPollPathTemplate().replace("{batch_id}", taskId);

        log.info("MinerU v4 开始轮询: batchId={}, timeout={}s", taskId, pollTimeout.toSeconds());

        while (System.currentTimeMillis() - startTime < pollTimeout.toMillis()) {
            try {
                String response = client.get().uri(pollUri).retrieve().body(String.class);
                JsonNode root = objectMapper.readTree(response);

                if (root.path("code").asInt(-1) != 0) {
                    throw new BusinessException(ErrorCode.C0001,
                            "MinerU 查询失败: " + root.path("msg").asText("未知错误"), "");
                }

                JsonNode results = root.path("data").path("extract_result");
                if (results.isEmpty()) {
                    Thread.sleep(pollInterval.toMillis());
                    continue;
                }

                JsonNode result = results.get(0);
                String state = result.path("state").asText();
                log.debug("MinerU v4 state: {}", state);

                switch (state) {
                    case "done":
                        return new TaskPollResult("done", result.path("full_zip_url").asText(), null);
                    case "failed":
                        return new TaskPollResult("failed", null, result.path("err_msg").asText("解析失败"));
                    case "running":
                    case "pending":
                    case "converting":
                        break;
                    default:
                        log.warn("MinerU v4 未知状态: {}", state);
                }

                Thread.sleep(pollInterval.toMillis());

            } catch (BusinessException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.C0001, "MinerU 轮询被中断", "");
            } catch (Exception e) {
                log.error("MinerU v4 轮询异常", e);
                throw new BusinessException(ErrorCode.C0001, "MinerU 轮询失败: " + e.getMessage(), "");
            }
        }

        throw new BusinessException(ErrorCode.C0001, "MinerU 轮询超时: " + pollTimeout.toSeconds() + "s", "");
    }

    @Override
    public String downloadResult(String resultUrl) {
        log.info("MinerU v4 下载结果: url={}", resultUrl);
        try {
            byte[] zipBytes = client.get().uri(resultUrl).retrieve().body(byte[].class);
            if (zipBytes == null || zipBytes.length == 0) {
                throw new BusinessException(ErrorCode.C0001, "MinerU 下载结果为空", "");
            }
            log.info("MinerU v4 下载完成: size={}", zipBytes.length);

            String markdown = extractFullMd(zipBytes);
            log.info("MinerU v4 解压完成: length={}", markdown.length());
            return markdown;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU v4 下载或解压失败", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU 下载失败: " + e.getMessage(), "");
        }
    }

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
        throw new BusinessException(ErrorCode.C0001, "MinerU zip 中未找到 full.md", "");
    }
}