package com.graphnexus.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求 DTO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank
    @Schema(description = "用户名", example = "admin")
    private String username;

    @NotBlank
    @Schema(description = "密码", example = "Pass@123")
    private String password;
}