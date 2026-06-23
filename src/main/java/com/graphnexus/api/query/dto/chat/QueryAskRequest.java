package com.graphnexus.api.query.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 智能问答请求 VO。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Schema(description = "同步/异步问答请求体（需显式指定学生和学科）")
public record QueryAskRequest(
        @NotBlank
        @Schema(description = "自然语言问题", example = "分析张三的数学薄弱点", requiredMode = Schema.RequiredMode.REQUIRED)
        String question,

        @Schema(description = "学生姓名（模糊匹配）", example = "张三")
        String studentName,

        @Schema(description = "学号（精确匹配，存在同名时必填）", example = "20240001")
        String studentNo,

        @NotBlank
        @Schema(description = "学科", example = "数学", requiredMode = Schema.RequiredMode.REQUIRED)
        String subject,

        @Schema(description = "班级名称（班级概览时使用，与 studentName/studentNo 互斥）", example = "初三(1)班")
        String className
) {}