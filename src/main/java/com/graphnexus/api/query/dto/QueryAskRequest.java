package com.graphnexus.api.query.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 智能问答请求 VO。
 *
 * @author Jay
 * @date 2026/06/17
 */
public record QueryAskRequest(
        @NotBlank String question,
        String studentName,
        String studentNo,
        @NotBlank String subject
) {}