package com.graphnexus.api.llm.dto;

import lombok.Data;

/**
 * LLM 调试请求。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
public class LlmDebugRequest {

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户消息 */
    private String userMessage;
}