package com.graphnexus.application.auth.service;

import com.graphnexus.api.auth.dto.*;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.auth.entity.RoleDO;
import com.graphnexus.infrastructure.mysql.auth.entity.UserAccountDO;
import com.graphnexus.infrastructure.mysql.auth.entity.UserRoleDO;
import com.graphnexus.infrastructure.mysql.auth.repository.RoleRepository;
import com.graphnexus.infrastructure.mysql.auth.repository.UserAccountRepository;
import com.graphnexus.infrastructure.mysql.auth.repository.UserRoleRepository;
import com.graphnexus.application.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 业务逻辑单元测试。
 *
 * @author Jay
 * @date 2026/06/22
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService 业务逻辑测试")
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private final String username = "test_admin";
    private final String rawPassword = "Pass@123";
    private final String encodedPassword = "$2a$10$encoded";

    @BeforeEach
    void setUp() {
        UserAccountDO user = new UserAccountDO();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setRealName("Test Admin");
        user.setStatus(UserAccountDO.AccountStatus.ENABLED);

        when(userAccountRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn(encodedPassword);
        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("AC-1: 正确密码登录成功 → 返回 TokenPair")
    void loginSuccess() {
        LoginRequest request = new LoginRequest(username, rawPassword);
        when(tokenService.generateTokenPair(any())).thenReturn(
                new com.graphnexus.application.auth.model.TokenPair("at", "rt", 1800));

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("at", response.getAccessToken());
        assertEquals("rt", response.getRefreshToken());
        assertEquals(1800, response.getExpiresIn());
        assertNotNull(response.getUserInfo());
        assertEquals(username, response.getUserInfo().getUsername());
        verify(tokenService, times(1)).generateTokenPair(any());
    }

    @Test
    @DisplayName("AC-2: 密码错误 → BusinessException(A0023)")
    void loginFailWrongPassword() {
        LoginRequest request = new LoginRequest(username, "WrongPass");
        when(passwordEncoder.matches("WrongPass", encodedPassword)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.A0023.getErrorCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-3: 已禁用用户登录 → BusinessException(A0024)")
    void loginFailDisabled() {
        UserAccountDO disabledUser = new UserAccountDO();
        disabledUser.setId(2L);
        disabledUser.setUsername("disabled_user");
        disabledUser.setPassword(encodedPassword);
        disabledUser.setStatus(UserAccountDO.AccountStatus.DISABLED);
        when(userAccountRepository.findByUsername("disabled_user")).thenReturn(Optional.of(disabledUser));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        LoginRequest request = new LoginRequest("disabled_user", rawPassword);
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.A0024.getErrorCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-11: 创建用户成功")
    void createUserSuccess() {
        when(userAccountRepository.existsByUsername("new_user")).thenReturn(false);
        when(roleRepository.findByCode("TEACHER")).thenReturn(
                Optional.of(new RoleDO(2L, "TEACHER", "教师", "")));
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateUserRequest request = new CreateUserRequest("new_user", "Pass@123", "张老师", List.of("TEACHER"));
        ApiResult<UserVO> result = authService.createUser(request);

        assertNotNull(result.data());
        assertEquals("new_user", result.data().getUsername());
        verify(userAccountRepository, times(1)).save(any(UserAccountDO.class));
        verify(userRoleRepository, times(1)).save(any(UserRoleDO.class));
    }

    @Test
    @DisplayName("AC-12: 重复用户名 → BusinessException(A0028)")
    void createUserDuplicate() {
        when(userAccountRepository.existsByUsername("existing")).thenReturn(true);

        CreateUserRequest request = new CreateUserRequest("existing", "Pass@123", "重复", List.of("TEACHER"));
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.createUser(request));
        assertEquals(ErrorCode.A0028.getErrorCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-13: 更新用户角色后缓存失效")
    void updateUserRoleThenCacheEvicted() {
        UserAccountDO target = new UserAccountDO();
        target.setId(5L);
        target.setUsername("target_user");
        target.setPassword(encodedPassword);
        target.setRealName("目标");
        target.setStatus(UserAccountDO.AccountStatus.ENABLED);
        when(userAccountRepository.findById(5L)).thenReturn(Optional.of(target));
        when(userRoleRepository.findByUserId(5L)).thenReturn(List.of());

        UpdateUserRequest request = new UpdateUserRequest();
        request.setRoles(List.of("TEACHER", "OPS_MANAGER"));
        when(roleRepository.findByCode("TEACHER")).thenReturn(
                Optional.of(new RoleDO(2L, "TEACHER", "教师", "")));
        when(roleRepository.findByCode("OPS_MANAGER")).thenReturn(
                Optional.of(new RoleDO(5L, "OPS_MANAGER", "运营人员", "")));

        authService.updateUser(5L, request);

        verify(userRoleRepository, times(1)).deleteByUserId(5L);
        verify(userRoleRepository, times(2)).save(any(UserRoleDO.class));
        verify(tokenService, times(1)).evictUserCache(5L);
    }

    @Test
    @DisplayName("AC-15: 获取用户列表成功")
    void getUsersSuccess() {
        UserAccountDO user = new UserAccountDO();
        user.setId(1L);
        user.setUsername("admin");
        user.setRealName("管理员");
        user.setStatus(UserAccountDO.AccountStatus.ENABLED);
        user.setCreateTime(java.time.LocalDateTime.now());
        user.setUpdateTime(java.time.LocalDateTime.now());
        when(userAccountRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(user)));

        PageResult<UserVO> result = authService.getUsers(1, 10);

        assertNotNull(result);
        assertEquals(1, result.total());
        assertEquals("admin", result.list().get(0).getUsername());
    }
}