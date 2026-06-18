package com.graphnexus.api.query.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 智能对话请求 VO — 仅需提供原始问题，系统自动提取学生姓名、学科等信息。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Schema(description = "智能对话请求体（仅需自然语言问题，系统自动提取实体）")
public record QueryChatRequest(
        @NotBlank
        @Schema(description = "自然语言问题（系统自动从中提取学生姓名、学科等实体）", example = "分析一下张三最近数学怎么样", requiredMode = Schema.RequiredMode.REQUIRED)
        String question
) {}