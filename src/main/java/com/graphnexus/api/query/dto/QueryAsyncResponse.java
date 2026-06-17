package com.graphnexus.api.query.dto;

import java.time.LocalDateTime;

/** 异步提交响应 VO。 */
public record QueryAsyncResponse(String taskId, String status, LocalDateTime createdAt) {}