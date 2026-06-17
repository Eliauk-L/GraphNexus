package com.graphnexus.infrastructure.mineru.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mineru.config.MinerUProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * MinerU 统一客户端入口。
 *
 * <p>根据 {@code mineru.api.version} 配置选择 v1 或 v4 实现策略。
 * 文件上传（PUT 到 OSS 签名 URL）为 v1/v4 共用逻辑。</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Service
public class MinerUClient {

    private final MinerUApiClient apiClient;

    public MinerUClient(RestClient.Builder restClientBuilder, MinerUProperties properties, ObjectMapper objectMapper) {
        String version = properties.getApi().getVersion();
        log.info("MinerU 初始化: version={}", version);

        this.apiClient = switch (version) {
            case "v4" -> new MinerUV4Client(restClientBuilder, properties, objectMapper);
            default -> new MinerUV1Client(restClientBuilder, properties, objectMapper);
        };
    }

    /**
     * 提交文件上传任务。
     *
     * @param fileName 文件名（含扩展名）
     * @return 包含 taskId 和 fileUrl 的结果
     */
    public MinerUApiClient.TaskSubmitResult submitTask(String fileName) {
        return apiClient.submitTask(fileName);
    }

    /**
     * 上传 PDF 文件到 MinerU OSS 预签名 URL（v1/v4 共用）。
     *
     * @param fileUrl  OSS 预签名上传 URL
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
     * 轮询任务解析结果。
     *
     * @param taskId 任务 ID
     * @return 解析结果
     */
    public MinerUApiClient.TaskPollResult pollTaskResult(String taskId) {
        return apiClient.pollTaskResult(taskId);
    }

    /**
     * 下载解析结果 Markdown 文本。
     *
     * @param resultUrl 结果文件 URL
     * @return Markdown 文本内容
     */
    public String downloadMarkdown(String resultUrl) {
        return apiClient.downloadResult(resultUrl);
    }
}