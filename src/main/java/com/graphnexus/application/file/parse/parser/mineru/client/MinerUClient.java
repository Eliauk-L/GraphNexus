package com.graphnexus.application.file.parse.parser.mineru.client;

import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.application.file.parse.parser.mineru.config.MinerUProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MinerU 统一客户端入口。
 *
 * <p>自动发现所有 {@link MinerUApiClient} Bean，根据 {@code mineru.api.version}
 * 匹配对应的实现。新增 API 版本只需实现接口并注册为 Spring Bean，零代码改动。</p>
 *
 * <p>扩展方式：</p>
 * <pre>{@code
 * @Component
 * class MinerUCustomClient implements MinerUApiClient {
 *     public String getVersion() { return "custom"; }
 *     public TaskSubmitResult submitTask(String fileName) { ... }
 *     public TaskPollResult pollTaskResult(String taskId) { ... }
 *     public String downloadResult(String resultUrl) { ... }
 * }
 * }</pre>
 * <p>然后在 {@code application.yml} 中配置：</p>
 * <pre>
 * mineru:
 *   api:
 *     version: custom
 *     base-url: https://my-mineru.example.com
 *     submit-path: /api/v2/parse
 *     poll-path-template: /api/v2/parse/{taskId}
 * </pre>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Service
public class MinerUClient {

    private final MinerUApiClient apiClient;

    public MinerUClient(MinerUProperties properties, List<MinerUApiClient> allClients) {
        String version = properties.getApi().getVersion();

        Map<String, MinerUApiClient> clientMap = allClients.stream()
                .collect(Collectors.toMap(MinerUApiClient::getVersion, Function.identity()));

        this.apiClient = clientMap.get(version);
        if (this.apiClient == null) {
            log.error("MinerU 版本 '{}' 无对应实现，可用版本: {}", version, clientMap.keySet());
            throw new IllegalStateException(
                    "未找到 MinerU 版本 '" + version + "' 的实现，请检查 mineru.api.version 配置。" +
                    "可用版本: " + clientMap.keySet());
        }

        log.info("MinerU 初始化: version={}, client={}", version, apiClient.getClass().getSimpleName());
    }

    public MinerUApiClient.TaskSubmitResult submitTask(String fileName) {
        return apiClient.submitTask(fileName);
    }

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

    public MinerUApiClient.TaskPollResult pollTaskResult(String taskId) {
        return apiClient.pollTaskResult(taskId);
    }

    public String downloadMarkdown(String resultUrl) {
        return apiClient.downloadResult(resultUrl);
    }
}