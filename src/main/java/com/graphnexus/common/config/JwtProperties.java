package com.graphnexus.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 配置属性。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** JWT 签名密钥（HS256） */
    private String secret;

    /** Access Token 有效期（默认 30 分钟） */
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    /** Refresh Token 有效期（默认 7 天） */
    private Duration refreshTokenTtl = Duration.ofDays(7);
}