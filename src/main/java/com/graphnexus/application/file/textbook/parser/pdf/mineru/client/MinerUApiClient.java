package com.graphnexus.application.file.textbook.parser.pdf.mineru.client;

/**
 * MinerU API 客户端策略接口。
 *
 * <p>每种 MinerU API 版本（v1/v4/自部署）各自实现此接口并注册为 Spring Bean。
 * {@link MinerUClient} 根据 {@code mineru.api.version} 自动发现匹配的实现。</p>
 *
 * <p>扩展方式：新增 {@code @Component class MyMinerUCustomClient implements MinerUApiClient}
 * 返回 {@code "custom"}，配置 {@code mineru.api.version=custom} 即可。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
public interface MinerUApiClient {

    /**
     * 返回此实现的版本标识，与 {@code mineru.api.version} 配置项匹配。
     *
     * @return 版本标识（如 "v1"、"v4"、"custom"）
     */
    String getVersion();

    /**
     * 提交文件上传任务，获取任务 ID 和 OSS 预签名上传 URL。
     *
     * @param fileName 文件名（含扩展名）
     * @return 包含 taskId 和 fileUrl 的结果
     */
    TaskSubmitResult submitTask(String fileName);

    /**
     * 轮询解析结果，直到 state=done/failed 或超时。
     *
     * @param taskId 任务 ID
     * @return 解析结果
     */
    TaskPollResult pollTaskResult(String taskId);

    /**
     * 下载解析结果文本。
     *
     * @param resultUrl 结果文件 URL（v1: markdown_url、v4: full_zip_url）
     * @return Markdown 文本内容
     */
    String downloadResult(String resultUrl);

    // ======================== 共享数据类 ========================

    record TaskSubmitResult(String taskId, String fileUrl) {}

    record TaskPollResult(String state, String resultUrl, String errMsg) {
        public boolean isDone() { return "done".equals(state); }
        public boolean isFailed() { return "failed".equals(state); }
    }
}