package com.graphnexus.application.auth.service;

import com.graphnexus.application.auth.model.TokenPair;
import com.graphnexus.common.security.UserPrincipal;

import java.util.List;

/**
 * Token 管理服务接口。
 *
 * @author Jay
 * @date 2026/06/22
 */
public interface TokenService {

    TokenPair generateTokenPair(UserPrincipal principal);

    TokenPair refreshAccessToken(String refreshToken);

    void revokeRefreshToken(String refreshToken);

    String getCachedUser(Long userId);

    void cacheUser(Long userId, String json, long ttlMillis);

    void evictUserCache(Long userId);

    String getUserStatus(Long userId);

    List<String> getUserRoles(Long userId);
}