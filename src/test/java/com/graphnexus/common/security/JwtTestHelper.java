package com.graphnexus.common.security;

import com.graphnexus.common.config.JwtProperties;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import io.jsonwebtoken.Jwts;

/**
 * 测试用 JWT 工具类 —— 生成指定角色的 Token。
 *
 * @author Jay
 * @date 2026/06/22
 */
public final class JwtTestHelper {

    private static final String TEST_SECRET = "test-jwt-secret-key-for-unit-tests-only";
    private static final long TEST_TTL_MILLIS = 30 * 60 * 1000L; // 30 min

    private JwtTestHelper() {}

    /**
     * 生成测试用 Access Token。
     */
    public static String generateTestToken(String username, String... roles) {
        return generateTestToken(1L, username, List.of(roles));
    }

    /**
     * 生成测试用 Access Token（指定 userId）。
     */
    public static String generateTestToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + TEST_TTL_MILLIS);

        SecretKeySpec key = new SecretKeySpec(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 返回 "Bearer <token>" 格式的 Authorization header 值。
     */
    public static String createAuthHeader(String username, String... roles) {
        return "Bearer " + generateTestToken(username, roles);
    }
}