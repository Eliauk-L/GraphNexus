package com.graphnexus.api.llm.controller;

import com.graphnexus.api.llm.dto.LlmDebugRequest;
import com.graphnexus.application.llmgateway.service.LlmGateway;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * LLM 调试接口 — 直接调用 LlmGateway 返回原始响应，用于排查 LLM 连接问题。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmGateway llmGateway;

    /** 简单 Ping：发 "hi" 看 LLM 是否连通 */
    @PostMapping("/ping")
    public String ping() {
        log.info("LLM ping 测试");
        return llmGateway.chat("你是一个助手", "hi，请回复 hello");
    }

    /** 完整调试：自定义 system + user prompt */
    @PostMapping("/debug")
    public String debug(@RequestBody LlmDebugRequest request) {
        log.info("LLM debug: systemPrompt={}chars, userMessage={}chars",
                request.getSystemPrompt().length(), request.getUserMessage().length());
        return llmGateway.chat(request.getSystemPrompt(), request.getUserMessage());
    }
}