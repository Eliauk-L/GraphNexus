package com.graphnexus.api.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequest(
        @NotBlank String question,
        @NotBlank String studentNo,
        @NotBlank String subject,
        Integer dailyMinutes,
        Integer days
) {}
