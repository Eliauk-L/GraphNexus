package com.graphnexus.common.security;

import com.graphnexus.common.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT Token 工具类 —— 生成/解析/验证 Access Token。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Access Token（HS256）。roles 存为逗号分隔字符串，避免 JSON 数组反序列化问题。
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getAccessTokenTtl().toMillis());

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", String.join(",", roles))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 Access Token，返回 Claims。
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 签名 + 有效期。
     */
    public boolean validateToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    public String getUsername(Claims claims) {
        return claims.getSubject();
    }

    /**
     * 从 Claims 提取 roles。roles 以逗号分隔字符串存储，直接按逗号 split。
     */
    public List<String> getRoles(Claims claims) {
        String rolesStr = claims.get("roles", String.class);
        if (rolesStr == null || rolesStr.isBlank()) {
            return List.of();
        }
        return Arrays.asList(rolesStr.split(","));
    }

    public long getAccessTokenTtlMillis() {
        return jwtProperties.getAccessTokenTtl().toMillis();
    }

    public long getRefreshTokenTtlMillis() {
        return jwtProperties.getRefreshTokenTtl().toMillis();
    }
}