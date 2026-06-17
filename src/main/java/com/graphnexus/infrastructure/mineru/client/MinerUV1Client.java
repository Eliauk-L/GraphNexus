package com.graphnexus.infrastructure.mineru.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mineru.config.MinerUProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * MinerU v1 Agent API 客户端（免 Token，IP 限频，≤10MB / ≤20 页）。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Component
class MinerUV1Client implements MinerUApiClient {

    private final RestClient client;
    private final MinerUProperties properties;
    private final ObjectMapper objectMapper;

    MinerUV1Client(RestClient.Builder builder, MinerUProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = builder.baseUrl(properties.getApi().getBaseUrl()).build();
    }

    @Override
    public String getVersion() {
        return "v1";
    }

    @Override
    public TaskSubmitResult submitTask(String fileName) {
        Map<String, Object> body = new HashMap<>();
        body.put("file_name", fileName);
        body.put("enable_formula", properties.getParse().isEnableFormula());
        body.put("enable_table", properties.getParse().isEnableTable());
        body.put("language", properties.getParse().getLanguage());

        log.info("MinerU v1 提交上传任务: fileName={}", fileName);

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("MinerU v1 request: {}", jsonBody);

            String response = client.post()
                    .uri(properties.getApi().getSubmitPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BusinessException(ErrorCode.C0001,
                        "MinerU 提交任务失败: " + root.path("msg").asText("未知错误"),
                        "MinerU v1 API 返回 code=" + root.path("code").asInt());
            }

            JsonNode data = root.path("data");
            return new TaskSubmitResult(data.path("task_id").asText(), data.path("file_url").asText());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU v1 提交任务异常", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU API 调用失败: " + e.getMessage(),
                    "MinerU 任务提交网络异常，请稍后重试");
        }
    }

    @Override
    public TaskPollResult pollTaskResult(String taskId) {
        Duration pollInterval = properties.getApi().getPollInterval();
        Duration pollTimeout = properties.getApi().getPollTimeout();
        long startTime = System.currentTimeMillis();
        String pollUri = properties.getApi().getPollPathTemplate().replace("{taskId}", taskId);

        log.info("MinerU v1 开始轮询: taskId={}, timeout={}s", taskId, pollTimeout.toSeconds());

        while (System.currentTimeMillis() - startTime < pollTimeout.toMillis()) {
            try {
                String response = client.get().uri(pollUri).retrieve().body(String.class);
                JsonNode data = objectMapper.readTree(response).path("data");
                String state = data.path("state").asText();
                log.debug("MinerU v1 state: {}", state);

                switch (state) {
                    case "done":
                        return new TaskPollResult("done", data.path("markdown_url").asText(), null);
                    case "failed":
                        return new TaskPollResult("failed", null, data.path("err_msg").asText("解析失败"));
                    case "running":
                    case "pending":
                    case "waiting-file":
                    case "uploading":
                        break;
                    default:
                        log.warn("MinerU v1 未知状态: {}", state);
                }

                Thread.sleep(pollInterval.toMillis());

            } catch (BusinessException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.C0001, "MinerU 轮询被中断", "");
            } catch (Exception e) {
                log.error("MinerU v1 轮询异常", e);
                throw new BusinessException(ErrorCode.C0001, "MinerU 轮询失败: " + e.getMessage(), "");
            }
        }

        throw new BusinessException(ErrorCode.C0001, "MinerU 轮询超时: " + pollTimeout.toSeconds() + "s", "");
    }

    @Override
    public String downloadResult(String resultUrl) {
        log.info("MinerU v1 下载结果: url={}", resultUrl);
        try {
            String markdown = client.get().uri(resultUrl).retrieve().body(String.class);
            if (markdown == null || markdown.isEmpty()) {
                throw new BusinessException(ErrorCode.C0001, "MinerU 下载结果为空", "");
            }
            log.info("MinerU v1 下载完成: length={}", markdown.length());
            return markdown;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MinerU v1 下载失败", e);
            throw new BusinessException(ErrorCode.C0001, "MinerU 下载失败: " + e.getMessage(), "");
        }
    }
}