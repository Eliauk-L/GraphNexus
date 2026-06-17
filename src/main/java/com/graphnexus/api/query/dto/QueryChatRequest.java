package com.graphnexus.api.query.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 智能对话请求 VO — 仅需提供原始问题，系统自动提取学生姓名、学科等信息。
 *
 * @author Jay
 * @date 2026/06/17
 */
public record QueryChatRequest(
        @NotBlank String question
) {}