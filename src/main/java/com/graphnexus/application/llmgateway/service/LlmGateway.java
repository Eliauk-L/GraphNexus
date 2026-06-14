package com.graphnexus.application.llmgateway.service;

/**
 * LLM 调用网关 — v1 最小契约接口。
 *
 * <p>所有 LLM 调用必须通过此接口，禁止在业务代码中直接使用 Spring AI {@code ChatClient}。
 * 隔离 Spring AI 具体 API，为后续模型路由/配额/降级/审计预留扩展点。</p>
 *
 * <p>设计决策见 ADR-004。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
public interface LlmGateway {

    /**
     * 发送 Prompt 到 LLM 并返回原始文本响应。
     *
     * @param systemPrompt 系统提示词（角色设定、输出格式约束）
     * @param userMessage  用户消息（待处理的文本内容）
     * @return LLM 的原始文本响应（不解析、不加工）
     * @throws com.graphnexus.common.exception.BusinessException 错误码 C0001：LLM 调用失败
     */
    String chat(String systemPrompt, String userMessage);
}