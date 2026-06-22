package com.graphnexus.application.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Token 对（Access Token + Refresh Token）。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@AllArgsConstructor
public class TokenPair {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
}