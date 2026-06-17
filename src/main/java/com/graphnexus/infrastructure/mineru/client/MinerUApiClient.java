package com.graphnexus.infrastructure.mineru.client;

/**
 * MinerU API 客户端策略接口。
 *
 * <p>v1 和 v4 解析器各自实现此接口，由 {@link MinerUClient} 根据
 * {@code mineru.api.version} 配置选择对应的实现。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
public interface MinerUApiClient {

    /**
     * 提交文件上传任务，获取 task_id 和 OSS 预签名上传 URL。
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
     * @param resultUrl 结果文件 URL（v1: markdown_url, v4: full_zip_url）
     * @return Markdown 文本内容
     */
    String downloadResult(String resultUrl);

    /**
     * 任务提交结果。
     */
    record TaskSubmitResult(String taskId, String fileUrl) {}

    /**
     * 任务轮询结果。
     */
    record TaskPollResult(String state, String resultUrl, String errMsg) {
        public boolean isDone() {
            return "done".equals(state);
        }

        public boolean isFailed() {
            return "failed".equals(state);
        }
    }
}