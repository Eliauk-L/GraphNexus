package com.graphnexus.api.config.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Apply 操作响应 VO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyResultResponse {

    private int reloadedCount;
    private String reloadedAt;
}