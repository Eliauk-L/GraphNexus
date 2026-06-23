package com.graphnexus.application.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.api.auth.dto.*;
import com.graphnexus.application.auth.model.TokenPair;
import com.graphnexus.application.auth.model.UserInfo;
import com.graphnexus.application.auth.service.AuthService;
import com.graphnexus.application.auth.service.TokenService;
import com.graphnexus.application.ops.audit.service.AuditLogService;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.common.security.UserPrincipal;
import com.graphnexus.infrastructure.mysql.auth.entity.RoleDO;
import com.graphnexus.infrastructure.mysql.auth.entity.UserAccountDO;
import com.graphnexus.infrastructure.mysql.auth.entity.UserRoleDO;
import com.graphnexus.infrastructure.mysql.auth.repository.RoleRepository;
import com.graphnexus.infrastructure.mysql.auth.repository.UserAccountRepository;
import com.graphnexus.infrastructure.mysql.auth.repository.UserRoleRepository;
import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证业务实现。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LoginResponse login(LoginRequest request) {
        UserAccountDO user = userAccountRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.A0023));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.A0023);
        }

        if (user.getStatus() == UserAccountDO.AccountStatus.DISABLED) {
            throw new BusinessException(ErrorCode.A0024);
        }

        List<String> roles = getUserRoleCodes(user.getId());

        UserPrincipal principal = new UserPrincipal(
                user.getId(), user.getUsername(), user.getPassword(),
                user.getRealName(), user.getStatus().name(), roles);

        // 缓存用户信息到 Redis
        cacheUserInfo(principal);

        // 生成 Token
        TokenPair tokenPair = tokenService.generateTokenPair(principal);

        UserInfo userInfo = UserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .status(user.getStatus().name())
                .roles(roles)
                .build();

        auditLogService.record(user.getId(), OperationType.LOGIN);

        return new LoginResponse(tokenPair.getAccessToken(), tokenPair.getRefreshToken(),
                tokenPair.getExpiresIn(), userInfo);
    }

    @Override
    @Transactional
    public ApiResult<UserVO> createUser(CreateUserRequest request) {
        if (userAccountRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.A0028);
        }

        UserAccountDO user = new UserAccountDO();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setStatus(UserAccountDO.AccountStatus.ENABLED);
        userAccountRepository.save(user);

        // 分配角色
        assignRoles(user.getId(), request.getRoles());

        List<String> roleCodes = request.getRoles();

        UserVO vo = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roleCodes)
                .status(user.getStatus().name())
                .createTime(user.getCreateTime())
                .build();

        return ApiResult.success(vo);
    }

    @Override
    @Transactional
    public ApiResult<UserVO> updateUser(Long id, UpdateUserRequest request) {
        UserAccountDO user = userAccountRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0029));

        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getStatus() != null) {
            user.setStatus(UserAccountDO.AccountStatus.valueOf(request.getStatus()));
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userAccountRepository.save(user);

        // 角色变更 → 删旧关联 + 加新关联
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            userRoleRepository.deleteByUserId(id);
            assignRoles(id, request.getRoles());
        }

        // 清除 Redis 缓存
        tokenService.evictUserCache(id);

        List<String> roles = getUserRoleCodes(id);

        UserVO vo = UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roles)
                .status(user.getStatus().name())
                .createTime(user.getCreateTime())
                .build();

        return ApiResult.success(vo);
    }

    @Override
    public PageResult<UserVO> getUsers(int pageNum, int pageSize) {
        Page<UserAccountDO> page = userAccountRepository.findAll(
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));

        return PageResult.of(page.map(u -> {
                    List<String> roles = getUserRoleCodes(u.getId());
                    return UserVO.builder()
                            .id(u.getId())
                            .username(u.getUsername())
                            .realName(u.getRealName())
                            .roles(roles)
                            .status(u.getStatus().name())
                            .createTime(u.getCreateTime())
                            .build();
                }));
    }

    @Override
    public List<RoleVO> getRoles() {
        return roleRepository.findAll().stream()
                .map(r -> new RoleVO(r.getCode(), r.getName()))
                .collect(Collectors.toList());
    }

    // ── private helpers ──

    private List<String> getUserRoleCodes(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.getRoleId())
                        .map(RoleDO::getCode)
                        .orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    private void assignRoles(Long userId, List<String> roleCodes) {
        for (String code : roleCodes) {
            RoleDO role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.A0001, "角色不存在: " + code));
            UserRoleDO ur = new UserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(role.getId());
            userRoleRepository.save(ur);
        }
    }

    private void cacheUserInfo(UserPrincipal principal) {
        try {
            UserInfo info = UserInfo.builder()
                    .userId(principal.getUserId())
                    .username(principal.getUsername())
                    .realName(principal.getRealName())
                    .status(principal.getStatus())
                    .roles(principal.getRoles())
                    .build();
            String json = objectMapper.writeValueAsString(info);
            tokenService.cacheUser(principal.getUserId(), json, 30 * 60 * 1000L);
        } catch (Exception e) {
            log.warn("缓存用户信息到 Redis 失败: {}", e.getMessage());
        }
    }
}