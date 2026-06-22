package com.graphnexus.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新 Token 请求 DTO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    @NotBlank
    @Schema(description = "Refresh Token（UUID）")
    private String refreshToken;
}