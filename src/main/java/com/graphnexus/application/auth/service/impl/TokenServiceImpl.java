package com.graphnexus.application.auth.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.auth.model.TokenPair;
import com.graphnexus.application.auth.model.UserInfo;
import com.graphnexus.application.auth.service.TokenService;
import com.graphnexus.common.config.JwtProperties;
import com.graphnexus.common.security.JwtTokenProvider;
import com.graphnexus.common.security.UserPrincipal;
import com.graphnexus.infrastructure.mysql.auth.entity.UserAccountDO;
import com.graphnexus.infrastructure.mysql.auth.repository.RoleRepository;
import com.graphnexus.infrastructure.mysql.auth.repository.UserAccountRepository;
import com.graphnexus.infrastructure.mysql.auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Token 管理服务实现。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String USER_CACHE_PREFIX = "user:auth:";

    @Override
    public TokenPair generateTokenPair(UserPrincipal principal) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                principal.getUserId(), principal.getUsername(), principal.getRoles());
        String refreshToken = UUID.randomUUID().toString();

        // 存 Refresh Token → Redis
        try {
            redisTemplate.opsForValue().set(
                    REFRESH_KEY_PREFIX + refreshToken,
                    String.valueOf(principal.getUserId()),
                    Duration.ofMillis(jwtProperties.getRefreshTokenTtl().toMillis()));
        } catch (Exception e) {
            log.warn("Redis 写入 Refresh Token 失败: {}", e.getMessage());
        }

        return new TokenPair(accessToken, refreshToken,
                jwtTokenProvider.getAccessTokenTtlMillis() / 1000);
    }

    @Override
    public TokenPair refreshAccessToken(String refreshToken) {
        String key = REFRESH_KEY_PREFIX + refreshToken;
        String userIdStr;
        try {
            userIdStr = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis Refresh Token 校验失败: {}", e.getMessage());
            throw new RuntimeException("Refresh token validation failed");
        }

        if (userIdStr == null) {
            throw new RuntimeException("Refresh token not found or expired");
        }

        // 删除旧 Refresh Token（滚动刷新）
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 删除旧 Refresh Token 失败: {}", e.getMessage());
        }

        Long userId = Long.valueOf(userIdStr);
        UserAccountDO user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<String> roles = getUserRoles(userId);

        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getUsername(), user.getPassword(),
                user.getRealName(), user.getStatus().name(), roles);

        return generateTokenPair(principal);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        try {
            redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
        } catch (Exception e) {
            log.warn("Redis 删除 Refresh Token 失败（登出）: {}", e.getMessage());
        }
    }

    @Override
    public String getCachedUser(Long userId) {
        try {
            return redisTemplate.opsForValue().get(USER_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis 读取用户缓存失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void cacheUser(Long userId, String json, long ttlMillis) {
        try {
            redisTemplate.opsForValue().set(
                    USER_CACHE_PREFIX + userId, json,
                    Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.warn("Redis 写入用户缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public void evictUserCache(Long userId) {
        try {
            redisTemplate.delete(USER_CACHE_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis 删除用户缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public String getUserStatus(Long userId) {
        return userAccountRepository.findById(userId)
                .map(u -> u.getStatus().name())
                .orElse("DISABLED");
    }

    @Override
    public List<String> getUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.getRoleId())
                        .map(r -> r.getCode())
                        .orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }
}