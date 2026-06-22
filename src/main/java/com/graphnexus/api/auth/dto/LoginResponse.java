package com.graphnexus.api.auth.dto;

import com.graphnexus.application.auth.model.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 VO。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    @Schema(description = "Access Token（JWT）")
    private String accessToken;

    @Schema(description = "Refresh Token（UUID）")
    private String refreshToken;

    @Schema(description = "Access Token 有效期（秒）")
    private long expiresIn;

    @Schema(description = "用户基本信息")
    private UserInfo userInfo;
}