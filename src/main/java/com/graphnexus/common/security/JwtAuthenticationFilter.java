package com.graphnexus.common.security;

import com.graphnexus.application.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 —— 每次请求提取/验证 JWT → 注入 SecurityContext。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        System.out.println(token);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 1. 验证 JWT 签名 + 有效期
            if (!jwtTokenProvider.validateToken(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 2. 解析 payload
            var claims = jwtTokenProvider.parseAccessToken(token);
            Long userId = jwtTokenProvider.getUserId(claims);
            String username = jwtTokenProvider.getUsername(claims);
            List<String> roles = jwtTokenProvider.getRoles(claims);
            if (roles == null || roles.isEmpty()) {
                roles = tokenService.getUserRoles(userId);
            }

            // 3. 查缓存或 MySQL 获取用户状态
            String status;
            try {
                String cached = tokenService.getCachedUser(userId);
                if (cached != null) {
                    // 从缓存 JSON 中提取 status（简化解析）
                    status = extractStatusFromCache(cached);
                } else {
                    // 缓存未命中 → 查 MySQL（由 TokenService 负责）
                    status = tokenService.getUserStatus(userId);
                }
            } catch (Exception e) {
                // Redis 不可用 → 降级查 MySQL
                log.warn("Redis 不可用，降级查 MySQL: {}", e.getMessage());
                status = tokenService.getUserStatus(userId);
            }

            // 4. 校验用户状态
            if ("DISABLED".equals(status)) {
                log.debug("用户 {} 已被禁用，拒绝访问", username);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "账号已被禁用");
                return;
            }

            // 5. 构建认证主体
            UserPrincipal principal = new UserPrincipal(userId, username, "", "", status, roles);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            log.debug("JWT 处理异常: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 从缓存 JSON 中简单提取 status 字段（避免引入 Jackson 解析依赖）。
     */
    private String extractStatusFromCache(String cachedJson) {
        if (cachedJson == null) return null;
        int start = cachedJson.indexOf("\"status\":\"");
        if (start < 0) return null;
        start += 10;
        int end = cachedJson.indexOf("\"", start);
        return end > start ? cachedJson.substring(start, end) : null;
    }
}