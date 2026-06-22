package com.graphnexus.api.auth.controller;

import com.graphnexus.api.auth.dto.*;
import com.graphnexus.application.auth.model.TokenPair;
import com.graphnexus.application.auth.service.AuthService;
import com.graphnexus.application.auth.service.TokenService;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证 API 控制器。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("用户登录请求 username={}", request.getUsername());
        return ApiResult.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResult<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair tokenPair = tokenService.refreshAccessToken(request.getRefreshToken());
        LoginResponse response = new LoginResponse(
                tokenPair.getAccessToken(), tokenPair.getRefreshToken(),
                tokenPair.getExpiresIn(), null);
        return ApiResult.success(response);
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revokeRefreshToken(request.getRefreshToken());
        return ApiResult.success(null);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PageResult<UserVO>> getUsers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResult.success(authService.getUsers(pageNum, pageSize));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserVO> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("管理员创建用户 username={}", request.getUsername());
        return authService.createUser(request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserVO> updateUser(@PathVariable Long id,
                                        @Valid @RequestBody UpdateUserRequest request) {
        log.info("管理员更新用户 id={}", id);
        return authService.updateUser(id, request);
    }

    @GetMapping("/roles")
    public ApiResult<List<RoleVO>> getRoles() {
        return ApiResult.success(authService.getRoles());
    }
}